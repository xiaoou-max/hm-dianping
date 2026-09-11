package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

// 登录用户上下文工具：用ThreadLocal在同一个请求线程内"传递"当前用户
// ThreadLocal是"线程私有变量"：每个线程一份独立副本，A线程set的值B线程拿不到。
// Web请求是一请求一线程，所以拦截器里saveUser后，本请求内任何Service/Controller都能getUser到
public class UserHolder {
    // ThreadLocal对象，存放当前线程的用户信息
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    // 把用户存进当前线程
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    // 取出当前线程的用户
    public static UserDTO getUser(){
        return tl.get();
    }

    // 移除当前线程的用户（请求结束调用，防止线程复用导致串号）
    public static void removeUser(){
        tl.remove();
    }
}
