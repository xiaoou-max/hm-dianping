package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 秒杀下单业务层接口（实现在 VoucherOrderServiceImpl）。本项目的分布式锁压轴所在。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀抢券下单：
     *   1) Lua 脚本原子校验“库存>0 且未重复购买”并扣库存、发消息
     *   2) 异步线程用 Redisson 分布式锁保证“一人一单”并真正建单
     */
    Result seckillVoucher(Long voucherId);
}
