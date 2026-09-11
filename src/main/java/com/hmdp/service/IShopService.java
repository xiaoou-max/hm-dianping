package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 商铺业务层接口（实现在 ShopServiceImpl）。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /** 按 id 查商铺详情（重点：带 Redis 缓存 + 缓存击穿时互斥锁重建） */
    Result queryById(Long id);

    /** 更新商铺（先更新数据库，再删缓存，保证缓存与库一致） */
    Result update(Shop shop);

    /** 按分类分页查商铺；带上 x/y 经纬度时按距离由近到远排序 */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
