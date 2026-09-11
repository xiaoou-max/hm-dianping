package com.hmdp.utils;

// 分布式锁的接口：规定一把锁必须能"加锁"和"解锁"这两件事
// 为什么需要分布式锁：多台服务器部署后，synchronized只锁单台JVM内的对象，管不住别的机器上的线程
public interface ILock {

    // 尝试获取锁；timeoutSec是锁的过期秒数，要大于业务耗时，否则业务没跑完锁就提前过期了
    boolean tryLock(long timeoutSec);

    // 释放锁；释放时必须只删自己上的锁（靠唯一标识比对，见unlock.lua）
    void unlock();
}
