package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

// Web MVC配置：注册两个拦截器
// 顺序很关键：RefreshTokenInterceptor(order=0)先执行，负责把用户放进ThreadLocal；
//            LoginInterceptor(order=1)后执行，负责拦截未登录请求
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    // Redis操作模板，传给刷新登录态的拦截器（它要查Redis）
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 注册拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录拦截器：排除不用登录就能访问的路径（查商铺、看优惠券、发验证码、登录、热门笔记等）
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);
        // 刷新登录态拦截器：拦截所有请求，负责"取token + 续期 + 写ThreadLocal"
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
