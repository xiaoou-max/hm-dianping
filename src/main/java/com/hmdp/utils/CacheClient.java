package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

// 缓存工具类：封装缓存读写 + 缓存穿透/击穿的三种解决方案，业务层直接调用（见ShopServiceImpl.queryById）
@Slf4j
@Component
public class CacheClient {

    // Redis操作模板，所有缓存读写都靠它
    private final StringRedisTemplate stringRedisTemplate;

    // 逻辑过期方案专用的异步线程池：缓存过期后由它后台重建缓存，不阻塞用户请求
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 构造方法：Spring自动注入Redis模板
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 普通写缓存：对象转JSON存进Redis，并设置过期时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 逻辑过期方案专用写入：把"业务数据+过期时间"打包成RedisData存进去；key本身不设过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        // 存真实业务数据（比如商铺对象）
        redisData.setData(value);
        // 计算并存入逻辑过期时间 = 当前时间 + 缓存时长
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 整个RedisData转JSON后写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // ========== 方案一：缓存穿透（查缓存→查库→回填；查不到缓存空值） ==========
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        // 拼接完整的redis key
        String key = keyPrefix + id;

        // 第一步：查缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 缓存命中（有非空数据），直接返回，不查数据库
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 缓存里存的是空串（说明之前查过DB也没数据），直接返回null，不再查库
        if (json != null) {
            return null;
        }

        // 缓存没有：去数据库查（dbFallback是业务传入的"查库回调"，这里实际是getById）
        R r = dbFallback.apply(id);

        // 数据库也没有：把空串写进缓存（短过期），防止同一个不存在的id反复打库
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 数据库有：回填缓存并带过期时间，下次请求直接命中缓存
        this.set(key, r, time, unit);
        return r;
    }

    // ========== 方案二：缓存击穿-逻辑过期（缓存永不消失，过期靠逻辑时间；后台异步重建） ==========
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 第一步：查缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        // 缓存里没数据（比如还没预热），直接返回null
        if (StrUtil.isBlank(json)) {
            return null;
        }

        // 先反序列化外层包装RedisData，再取出里面的真实业务数据
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 取出逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();

        // 还没到过期时间，缓存有效，直接返回
        if(expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        // 已过期：尝试抢互斥锁（防止多个请求同时去重建缓存）
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 抢到锁：提交到线程池异步重建缓存，重建完释放锁
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查数据库，重新写入带新过期时间的缓存
                    R newR = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }

        // 没抢到锁（或重建还没完成）：先返回旧数据，保证用户有响应
        return r;
    }

    // ========== 方案三：缓存击穿-互斥锁（缓存没有时加锁查库，防缓存失效瞬间大量请求打库） ==========
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 第一步：查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 缓存命中，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, type);
        }

        // 缓存是空串，说明DB也没数据，直接返回null
        if (shopJson != null) {
            return null;
        }

        // 缓存没有：尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);

            // 没抢到锁：说明别人正在重建缓存，等50ms后重试（递归），直到抢到或缓存被重建好
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            // 抢到锁：去数据库查
            r = dbFallback.apply(id);

            // 数据库也没有：写空值防穿透
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 数据库有：回填缓存
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            // 释放锁（放finally里，业务异常也会执行，避免死锁）
            unlock(lockKey);
        }
        return r;
    }

    // 简单互斥锁：setnx + 10秒过期（兜底防死锁）
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁（简化版直接删，没比对标识；生产建议用Lua，见SimpleRedisLock.unlock）
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
