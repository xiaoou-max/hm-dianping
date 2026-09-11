package com.hmdp.mapper;

import com.hmdp.entity.SeckillVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 秒杀优惠券表 DAO 接口，对应 tb_seckill_voucher。继承 BaseMapper 即拥有基础 CRUD。
 * 与优惠券是一对一关系：voucher_id 同时是本表主键和关联 tb_voucher 的外键。
 *
 * @author 虎哥
 * @since 2022-01-04
 */
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

}
