package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

// 登录态刷新拦截器（第一道）：所有请求先经过它，负责"从Redis取登录态 + 写入ThreadLocal + 滑动续期"
// 注意：它不拦截任何人；真正拦截未登录请求的是 LoginInterceptor
public class RefreshTokenInterceptor implements HandlerInterceptor {

    // Redis操作模板：查登录态、续期
    private StringRedisTemplate stringRedisTemplate;

    // 构造方法：这个拦截器是手动new的（不是Spring Bean），需要把Redis模板传进来
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 请求进入时执行：有token就查Redis、刷新有效期、把用户放进ThreadLocal
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 从请求头里取token（前端登录后每次请求都带）
        String token = request.getHeader("authorization");
        // 没带token：不处理，放行（是否拦截交给LoginInterceptor判断）
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 组装登录态的redis key：login:token:xxx
        String key  = LOGIN_USER_KEY + token;
        // 从Redis Hash取出用户信息（登录时存进去的）
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // Redis里没有这个token对应的用户（过期或被清掉）：放行，交给LoginInterceptor拦
        if (userMap.isEmpty()) {
            return true;
        }
        // 把Hash读出的用户Map反序列化成UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 把用户放进ThreadLocal，本次请求内任何Service/Controller都能取到
        UserHolder.saveUser(userDTO);
        // 滑动续期：每次访问都重置token有效期（活跃用户永不掉线）
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    // 请求结束执行：清理ThreadLocal，防止线程复用导致"串号"（上一个请求的用户被下一个请求拿到）
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
