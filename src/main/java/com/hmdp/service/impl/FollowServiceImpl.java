package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 关注关系业务：用Redis Set维护"某人关注了谁"（key=follows:用户id，value=被关注者id集合）
// 取关要"删DB记录 + 删Redis集合元素"双写保持一致；共同关注用两个Set求交集
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    // Redis操作模板：维护关注集合
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 用户服务：查共同关注的用户信息
    @Resource
    private IUserService userService;

    // 关注/取关：isFollow=true关注，false取关；数据库和Redis都要同步改
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 我的关注集合的redis key：follows:用户id
        String key = "follows:" + userId;

        // 关注操作
        if (isFollow) {
            // 构造关注记录：我关注了 followUserId
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            // 先写数据库
            if (save(follow)) {
                // 再把被关注者id加进我的关注集合（SADD）
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 取关操作：先删数据库的关注记录
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            // 再从我的关注集合移除（SREM）
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }
        return Result.ok();
    }

    // 判断我是否已关注某用户（关注按钮高亮用）
    @Override
    public Result isFollow(Long followUserId) {
        // 当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 查数据库里有没有这条关注记录
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    // 查我和某用户的共同关注：两个关注集合做交集（SINTER）
    @Override
    public Result followCommons(Long id) {
        // 当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // SINTER求交集：我在关注的人 ∩ 对方关注的人 = 共同关注
        Set<String> intersect = stringRedisTemplate.opsForSet()
                .intersect("follows:" + userId, "follows:" + id);
        // 没有共同关注，返回空列表
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 把交集里的id转成Long集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 按id批量查用户，转成脱敏UserDTO返回
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
