package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

// 用户模块控制器（/user）：发验证码、登录、查个人信息、签到
// 所有接口只做"接参 -> 调Service -> 返回Result"，不含业务逻辑
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    // 用户服务：登录、注册、签到
    @Resource
    private IUserService userService;

    // 用户资料服务：查用户详情资料
    @Resource
    private IUserInfoService userInfoService;

    // 发送验证码：手机号从请求参数里来
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    // 登录：前端传手机号+验证码（或密码）
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm, session);
    }

    // 登出（演示代码未实现）
    @PostMapping("/logout")
    public Result logout(){
        // TODO 登出功能尚未实现
        return Result.fail("功能未完成");
    }

    // 查当前登录用户：从ThreadLocal直接取，不查库
    @GetMapping("/me")
    public Result me(){
        return Result.ok(UserHolder.getUser());
    }

    // 查用户详情资料（脱敏后返回，隐藏时间字段）
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查用户资料
        UserInfo info = userInfoService.getById(userId);
        // 查不到就返回空
        if (info == null) {
            return Result.ok();
        }
        // 脱敏：不返回创建/更新时间
        info.setCreateTime(null);
        info.setUpdateTime(null);
        return Result.ok(info);
    }

    // 查用户基本信息：脱敏成UserDTO返回（不含密码）
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 按id查用户
        User user = userService.getById(userId);
        // 查不到就返回空
        if (user == null) {
            return Result.ok();
        }
        // 拷贝成脱敏DTO返回
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }

    // 当日签到（Redis Bitmap实现）
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    // 查连续签到天数
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}
