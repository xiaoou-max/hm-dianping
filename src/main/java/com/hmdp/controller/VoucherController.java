package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

// 优惠券控制器（/voucher）：管理端发券入口
// 新增普通券只写一张表；新增秒杀券要写两张表并把库存预热到Redis
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    // 优惠券服务
    @Resource
    private IVoucherService voucherService;

    // 新增秒杀券：会同时写优惠券表+秒杀券表，并把库存预热到Redis
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    // 新增普通券
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    // 按商铺查优惠券列表（普通券+秒杀券）
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
}
