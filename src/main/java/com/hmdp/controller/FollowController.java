package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

// 关注关系控制器（/follow）：关注/取关、是否关注、共同关注
@RestController
@RequestMapping("/follow")
public class FollowController {

    // 关注服务：Redis Set维护关注关系
    @Resource
    private IFollowService followService;

    // 关注/取关：isFollow=true关注，false取关
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    // 判断我是否已关注某人（关注按钮高亮用）
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    // 查我和某用户的共同关注（Redis Set交集）
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }
}
