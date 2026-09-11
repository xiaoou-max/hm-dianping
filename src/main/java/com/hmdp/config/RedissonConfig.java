package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Redisson配置：创建一个RedissonClient交给Spring管理，业务里直接注入用
// Redisson是成熟的分布式锁客户端，比手写锁（SimpleRedisLock）强在：可重入、看门狗自动续期、原子释放
@Configuration
public class RedissonConfig {

    // 创建RedissonClient，交给Spring容器管理
    @Bean
    public RedissonClient redissonClient(){
        // 创建Redisson配置对象
        Config config = new Config();
        // 指定Redis单节点地址和密码（生产可换集群：useClusterServers）
        config.useSingleServer().setAddress("redis://192.168.150.101:6379").setPassword("123321");
        // 用配置创建Redisson客户端并返回
        return Redisson.create(config);
    }
}
