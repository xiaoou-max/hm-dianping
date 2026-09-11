package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// 登录拦截器（第二道）：只做一件事——ThreadLocal里没有用户就拦截（未登录）
// 依赖前面的RefreshTokenInterceptor已经把登录用户放进了ThreadLocal
public class LoginInterceptor implements HandlerInterceptor {

    // 请求进入时执行：检查ThreadLocal里有没有用户
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // ThreadLocal里没有用户 = 没登录，返回401状态码拦截
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        // 有用户，放行
        return true;
    }
}
