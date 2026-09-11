package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 关注关系业务层接口（实现在 FollowServiceImpl）。重点演示 Redis Set 运算。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /** 关注/取关某用户（isFollow=true 关注，false 取关）；同时维护 Redis 关注集合 */
    Result follow(Long followUserId, Boolean isFollow);

    /** 判断“我”是否已关注某用户（关注按钮高亮用） */
    Result isFollow(Long followUserId);

    /** 查“我”和某用户的共同关注（Redis Set 交集 sinter） */
    Result followCommons(Long id);
}
