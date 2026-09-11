package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

// 商铺模块控制器（/shop）：商铺增删改查
// 重点：查详情走Redis缓存；更新先更库再删缓存；分类列表支持按距离排序
@RestController
@RequestMapping("/shop")
public class ShopController {

    // 商铺服务：缓存查询、更新等业务
    @Resource
    public IShopService shopService;

    // 按id查商铺详情（带Redis缓存）
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    // 新增商铺，返回新商铺id
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        shopService.save(shop);
        return Result.ok(shop.getId());
    }

    // 更新商铺（先更库再删缓存）
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        return shopService.update(shop);
    }

    // 按分类查商铺：typeId分类id，current页码；传了x/y就按距离由近到远排
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
       return shopService.queryShopByType(typeId, current, x, y);
    }

    // 按名称模糊查询商铺（分页）
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 名字为空就不带name条件；like模糊查询 + 分页
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
}
