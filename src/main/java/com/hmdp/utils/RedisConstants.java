package com.hmdp.utils;

// 集中管理Redis所有key前缀和过期时间（TTL），避免key散落各处拼错
public class RedisConstants {

    // ===== 登录模块 =====
    // 验证码key前缀：login:code:手机号
    public static final String LOGIN_CODE_KEY = "login:code:";
    // 验证码有效期：2分钟
    public static final Long LOGIN_CODE_TTL = 2L;
    // 登录态key前缀：login:token:token值
    public static final String LOGIN_USER_KEY = "login:token:";
    // 登录态有效期：36000分钟（教学值偏大）
    public static final Long LOGIN_USER_TTL = 36000L;

    // ===== 缓存模块 =====
    // 缓存空值有效期：2分钟（防缓存穿透）
    public static final Long CACHE_NULL_TTL = 2L;
    // 商铺缓存有效期：30分钟
    public static final Long CACHE_SHOP_TTL = 30L;
    // 商铺缓存key前缀：cache:shop:id
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    // 商铺重建缓存用的互斥锁：lock:shop:id
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    // 互斥锁超时：10秒
    public static final Long LOCK_SHOP_TTL = 10L;

    // ===== 秒杀模块 =====
    // 秒杀库存key前缀：seckill:stock:券id
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    // ===== 社交模块 =====
    // 笔记点赞集合：blog:liked:笔记id
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    // 关注收件箱（Feed流）：feed:用户id
    public static final String FEED_KEY = "feed:";
    // 商铺坐标集合：shop:geo:分类id
    public static final String SHOP_GEO_KEY = "shop:geo:";
    // 签到位图：sign:用户id:年月
    public static final String USER_SIGN_KEY = "sign:";
}
