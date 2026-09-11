package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

// 秒杀下单控制器（/voucher-order）：用户端抢券入口，分布式锁"一人一单"的最终落地接口
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    // 秒杀订单服务：真正执行下单逻辑
    @Resource
    private IVoucherOrderService voucherOrderService;

    // 秒杀抢券：路径里的id是要抢的优惠券id，转发给Service处理
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
