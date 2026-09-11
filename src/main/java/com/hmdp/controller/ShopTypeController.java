package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

// 商铺分类控制器（/shop-type）：只有查全部分类一个接口，给首页顶部分类栏用
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {

    // 分类服务
    @Resource
    private IShopTypeService typeService;

    // 查全部分类：按sort升序返回
    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
