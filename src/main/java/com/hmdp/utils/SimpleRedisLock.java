package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

// 手写版Redis分布式锁：靠setnx实现互斥，加锁时带过期时间防死锁，锁值存线程标识防止误删别人的锁
// 缺陷：不可重入、不会自动续期，所以正式下单场景改用Redisson（见RedissonConfig）
public class SimpleRedisLock implements ILock {

    // 锁对应的业务名称，比如"order:123"，用来拼接redis的锁key
    private String name;
    // Redis操作模板，用来执行setnx、lua等redis命令
    private StringRedisTemplate stringRedisTemplate;

    // 构造方法：接收业务名和Redis模板
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // redis中锁key的前缀，最终key = lock:业务名，比如 lock:order:123
    private static final String KEY_PREFIX = "lock:";
    // 线程唯一标识前缀 = UUID（防止多台JVM之间重复）+ "-"；再拼上线程id，就代表"这把锁是谁上的"
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    // 释放锁的Lua脚本对象（类加载时初始化一次，避免每次释放都重新读lua文件）
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    // 静态代码块：类第一次被使用时执行，用来初始化上面的Lua脚本
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // 指定lua脚本位置：classpath下的unlock.lua
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        // 指定脚本返回类型：Redis执行Lua返回的是Long
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    // 尝试获取锁；timeoutSec是锁的过期秒数，要大于业务耗时，否则业务没跑完锁就过期了
    @Override
    public boolean tryLock(long timeoutSec) {
        // 生成当前线程的标识：UUID前缀-线程id，用来证明"这把锁是我的"
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 执行setnx：key不存在才设置成功（保证互斥）；同时带过期时间（线程崩了锁也能自动释放，防死锁）
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        // Redis返回的Boolean可能为null，用Boolean.TRUE.equals统一转成基本类型（true=拿到锁）
        return Boolean.TRUE.equals(success);
    }

    // 释放锁
    @Override
    public void unlock() {
        // 执行unlock.lua：先比对"锁里存的标识"和"我的标识"，一致才删除，防止锁过期后误删别人的锁
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                // Lua脚本的参数KEYS[1]：锁的redis key
                Collections.singletonList(KEY_PREFIX + name),
                // Lua脚本的参数ARGV[1]：当前线程的标识
                ID_PREFIX + Thread.currentThread().getId());
    }

    /* 错误示范（被unlock.lua取代）：先get再del是两步操作、不是原子的。
       竞态：线程A的锁过期被清掉，线程B抢到同一把锁，此时线程A才执行del，就把线程B的锁误删了。
       unlock.lua把"比对+删除"合成一步，从根源杜绝，保留此块便于对比学习。 */
}
