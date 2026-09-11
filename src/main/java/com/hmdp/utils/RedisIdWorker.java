package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

//交给Spring管理，自动创建RedisIdWorker实例存入容器
@Component
public class RedisIdWorker {
    // ID生成的起始基准时间戳（固定时间，用来缩短时间戳数值）
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    // 序列号占用二进制的位数，这里分配32位用来存放自增序号
    private static final int COUNT_BITS = 32;

    // Redis操作工具，用来执行redis自增命令生成序列号
    private StringRedisTemplate stringRedisTemplate;

    // 构造方法，接收Redis操作模板，给本类的成员变量赋值
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 生成全局唯一ID，keyPrefix作为业务前缀区分不同业务的id
    public long nextId(String keyPrefix) {
        // 1.计算相对时间戳：当前时间减去基准起始时间
        LocalDateTime now = LocalDateTime.now();
        // 将当前时间转为UTC时区下的秒数
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        // 得到相对于基准时间的时间差，作为ID的高位部分
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号，利用redis的increment做每日自增
        // 获取当前日期，格式yyyy:MM:dd，按天做key，实现序列号每日重置
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // redis key拼接：icr:业务前缀:日期，执行自增拿到序列号
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.位运算拼接ID：时间戳左移32位放到高位，或运算拼接低位序列号
        return timestamp << COUNT_BITS | count;
    }
}
