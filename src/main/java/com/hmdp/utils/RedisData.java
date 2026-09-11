package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

// 逻辑过期方案的数据包装：Redis里实际存的是它（key本身不设过期，是否过期靠这里面的时间判断）
@Data
public class RedisData {
    // 逻辑过期时间：超过这个时间，说明缓存该后台重建了
    private LocalDateTime expireTime;
    // 真正的业务数据（比如商铺对象）
    private Object data;
}
