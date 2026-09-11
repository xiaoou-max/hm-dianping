package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
class RedissonTest {

    @Resource
    private RedissonClient redissonClient;

    private RLock lock;

    @BeforeEach
    void setUp() {
        // 拿一把名为 "order" 的锁对象（Redisson 的 RLock，可重入锁）
        lock = redissonClient.getLock("order");
    }

    /**
     * 这个测试专门演示 Redisson 锁的“可重入性（Reentrant）”。
     *
     * 可重入意味着：同一个线程已经持有这把锁后，再次 tryLock 不会死锁，而是把“重入计数 +1”。
     * 对比手写 SimpleRedisLock：它用 setIfAbsent 上锁，key 已存在就直接失败，无法重入。
     *
     * 执行流程：
     *   method1() 先 tryLock 拿到锁（重入计数=1） -> 调用 method2()
     *   method2() 再 tryLock 同一把锁（重入计数=2，不会失败！） -> 业务 -> unlock（计数-1）
     *   method1()  finally 里 unlock（计数归零，真正释放）
     * 如果锁不可重入，method2 的 tryLock 就会失败并打印“获取锁失败 .... 2”。
     */
    @Test
    void method1() throws InterruptedException {
        boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
        if (!isLock) {
            log.error("获取锁失败 .... 1");
            return;
        }
        try {
            log.info("获取锁成功 .... 1");
            method2();
            log.info("开始执行业务 ... 1");
        } finally {
            log.warn("准备释放锁 .... 1");
            lock.unlock();
        }
    }
    void method2() {
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败 .... 2");
            return;
        }
        try {
            log.info("获取锁成功 .... 2");
            log.info("开始执行业务 ... 2");
        } finally {
            log.warn("准备释放锁 .... 2");
            lock.unlock();
        }
    }
}
