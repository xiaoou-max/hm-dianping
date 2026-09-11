package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

// 探店笔记业务。Redis的两个核心用法：
//   点赞：ZSet，score=点赞时间戳，既能去重又能排"点赞排行榜"
//   关注Feed流：用ZSet做每个用户的"收件箱"，按时间戳倒序推送笔记，滚动分页
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    // 用户服务：查作者信息
    @Resource
    private IUserService userService;

    // Redis操作模板：点赞ZSet、Feed流ZSet
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 关注服务：发笔记时查作者有哪些粉丝
    @Resource
    private IFollowService followService;

    // 查询热门笔记：按点赞数倒序分页，并补上作者信息和"我是否点过赞"
    @Override
    public Result queryHotBlog(Integer current) {
        // 按liked字段倒序分页查笔记
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = page.getRecords();
        // 每条笔记都要补作者昵称/头像，以及当前登录用户是否点过赞
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    // 查询单条笔记详情
    @Override
    public Result queryBlogById(Long id) {
        // 按id查笔记
        Blog blog = getById(id);
        // 笔记不存在
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 补作者信息和点赞状态
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    // 判断当前登录用户是否给这篇笔记点过赞：查ZSet里有没有"用户id"这个成员
    private void isBlogLiked(Blog blog) {
        // 取当前登录用户（可能没登录）
        UserDTO user = UserHolder.getUser();
        // 没登录就不查，直接返回
        if (user == null) {
            return;
        }
        // ZSCORE：查用户在点赞集合里的分数；分数存在=点过赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), user.getId().toString());
        blog.setIsLike(score != null);
    }

    // 点赞/取消点赞：DB维护点赞数，Redis ZSet维护"谁点过赞"（score=时间戳，可排序）
    @Override
    public Result likeBlog(Long id) {
        // 当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 这篇笔记的点赞集合key
        String key = BLOG_LIKED_KEY + id;
        // ZSCORE：查这个用户是否已经在集合里（有分数=点过赞）
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        // 情况一：没点过赞 -> 点赞
        if (score == null) {
            // DB的点赞数+1
            if (update().setSql("liked = liked + 1").eq("id", id).update()) {
                // 把用户加进ZSet，score=当前时间戳（用来排点赞顺序）
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 情况二：点过赞 -> 取消点赞
            // DB的点赞数-1
            if (update().setSql("liked = liked - 1").eq("id", id).update()) {
                // 把用户从ZSet移除
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    // 查询点赞排行榜前5名用户：ZSet按score(时间戳)升序取前5个，再查用户信息
    @Override
    public Result queryBlogLikes(Long id) {
        // 点赞集合key
        String key = BLOG_LIKED_KEY + id;
        // ZRANGE：按score从小到大取前5个（点赞早的排前面）
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 没人点赞，返回空
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 把点赞用户id字符串转成Long
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 拼成sql用的id串，如 "1,2,3"
        String idStr = StrUtil.join(",", ids);
        // 按id批量查用户；ORDER BY FIELD让DB按传入id的顺序返回，保持"排行榜"顺序
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    // 发布笔记：写数据库，再把笔记id推送给作者的所有粉丝（写进各自的Feed流ZSet）
    @Override
    public Result saveBlog(Blog blog) {
        // 取当前登录用户
        UserDTO user = UserHolder.getUser();
        // 笔记的作者就是当前用户
        blog.setUserId(user.getId());
        // 保存笔记到数据库
        boolean isSuccess = save(blog);
        // 保存失败
        if(!isSuccess){
            return Result.fail("新增笔记失败!");
        }
        // 查出作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 把这篇笔记id写进每个粉丝的收件箱ZSet，score=当前时间戳（用来倒序排Feed流）
        for (Follow follow : follows) {
            stringRedisTemplate.opsForZSet().add(FEED_KEY + follow.getUserId(), blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    // 查询我关注的人的笔记（Feed流）：用ZSet滚动分页，不重复不漏查
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 我的收件箱key
        String key = FEED_KEY + userId;
        // ZREVRANGEBYSCORE：按score(时间戳)倒序，取[0, max]区间，从offset条之后开始取2条
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 没有数据了，返回空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        // 收集本页笔记id，并记录"最小的score"和"该score出现了几次"（给下一页当游标）
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;   // 与minTime相同的时间戳数量，翻页时跳过这些避免重复
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 笔记id
            ids.add(Long.valueOf(tuple.getValue()));
            // 这条笔记的时间戳（score）
            long time = tuple.getScore().longValue();
            // 如果时间戳和当前最小值相同，同分值数量+1（下页要跳过）
            if (time == minTime) {
                os++;
            } else {
                // 遇到更小的，记录为新的最小值，同分值数量从1开始
                minTime = time;
                os = 1;
            }
        }

        // 按id批量查笔记；ORDER BY FIELD保持与Feed流时间倒序一致
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 每条笔记补作者信息和点赞状态
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        // 组装滚动分页结果：list=本页数据，minTime/offset作为下一页的游标
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

    // 给笔记补上作者信息（昵称+头像），前端展示用
    private void queryBlogUser(Blog blog) {
        // 按笔记的作者id查用户
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
