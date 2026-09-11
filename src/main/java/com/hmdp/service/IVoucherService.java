package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 优惠券业务层接口（实现在 VoucherServiceImpl）。这是“发券”的管理端逻辑。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    /** 按商铺查优惠券列表（含普通券和秒杀券） */
    Result queryVoucherOfShop(Long shopId);

    /** 新增秒杀券：同时写 tb_voucher 和 tb_seckill_voucher 两张表（事务） */
    void addSeckillVoucher(Voucher voucher);
}
