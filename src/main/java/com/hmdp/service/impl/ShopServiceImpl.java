package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

// 商铺业务：演示Redis缓存三大问题解法（穿透/击穿互斥/击穿逻辑过期，方案都在CacheClient里）
// 更新商铺用"先更库再删缓存"保证缓存一致性；分类列表支持按距离由近到远排序（Redis GEO）
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    // Redis操作模板：删缓存、GEO搜索都用它
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 缓存工具类：封装了三种缓存方案的读写
    @Resource
    private CacheClient cacheClient;

    // 按id查商铺：走缓存，缓存没有就去数据库查（默认用"缓存穿透"方案）
    @Override
    public Result queryById(Long id) {
        // 从缓存查，没有则查库并回填缓存；参数：key前缀、id、返回类型、查库回调、缓存时长
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 备选：缓存击穿方案（互斥锁排队查库 / 逻辑过期异步重建），把上面那行注释掉、启用下面任一行即可
        // Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        // 查不到（DB也没有），返回失败
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    // 更新商铺：先更新数据库，再删缓存（保证下次请求重新查库回填，避免脏缓存）
    @Override
    @Transactional
    public Result update(Shop shop) {
        // 取商铺id
        Long id = shop.getId();
        // id为空，参数错误
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库
        updateById(shop);
        // 删除Redis缓存，下次查询会重新从DB加载
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    // 按分类查商铺：没传坐标就DB分页；传了坐标(x,y)就用Redis GEO按距离由近到远分页
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 情况一：没传经纬度，直接按分类从数据库分页查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 计算分页的起止下标（GEOSEARCH不支持skip，只能先查出end条再内存分页）
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 情况二：带坐标，用Redis GEO搜索"以(x,y)为中心、半径5000米内的商铺，按距离升序、最多end条"
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),   // 以这个坐标为中心
                        new Distance(5000),                  // 半径5000米
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 半径内没有商铺，返回空
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        // 数据量还不够一页（本页第一条已经超出范围），返回空
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        // 收集本页商铺id和距离：skip(from)跳过上一页的数据，实现分页
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();   // GEO里存的是商铺id（字符串）
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr, result.getDistance());   // 记录每个商铺离我的距离
        });

        // 按id回查商铺详情；ORDER BY FIELD让DB按传入id的顺序返回，保证和距离排序一致
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 把刚才算好的距离填回商铺对象，返回给前端展示"距离多少米"
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
