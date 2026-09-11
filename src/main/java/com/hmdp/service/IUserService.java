package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * 用户业务层接口（定义“能做什么”，实现在 UserServiceImpl）。
 *
 * 继承 MP 的 IService<User>：直接拥有 getById/save/list 等通用方法；
 * 下面 4 个方法是额外定义的业务签名，具体逻辑在 UserServiceImpl。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /** 发送登录验证码：校验手机号 -> 生成验证码 -> 存 Redis（2分钟）-> 模拟发送 */
    Result sendCode(String phone, HttpSession session);

    /** 登录/注册：校验验证码 -> 查/建用户 -> 生成 token -> 用户信息存 Redis -> 返回 token */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /** 当日签到：用 Redis Bitmap 记录“今天签到了” */
    Result sign();

    /** 统计连续签到天数：读 Bitmap 并用位运算从今天往前数连续的 1 */
    Result signCount();

}
