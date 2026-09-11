package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 探店笔记业务层接口（实现在 BlogServiceImpl）。涉及大量 Redis 高级用法。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /** 查询热门笔记（按点赞数分页） */
    Result queryHotBlog(Integer current);

    /** 查单条笔记详情 */
    Result queryBlogById(Long id);

    /** 给笔记点赞：用 Redis Set 去重并记录点赞用户，支持滚动排行 */
    Result likeBlog(Long id);

    /** 查某笔记的点赞用户列表（Top5） */
    Result queryBlogLikes(Long id);

    /** 发布笔记（写库 + 推送给粉丝 Feed 流） */
    Result saveBlog(Blog blog);

    /** 查“我关注的人”的笔记（关注 Feed 流，滚动分页：max=上次最后id，offset=偏移） */
    Result queryBlogOfFollow(Long max, Integer offset);

}
