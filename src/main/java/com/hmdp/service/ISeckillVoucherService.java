package com.hmdp.service;

import com.hmdp.entity.SeckillVoucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 秒杀券业务层接口（实现在 SeckillVoucherServiceImpl），与 tb_voucher 一对一关联。
 *
 * 没有额外自定义方法，仅继承 MP 的 IService<SeckillVoucher> 通用 CRUD。
 */
public interface ISeckillVoucherService extends IService<SeckillVoucher> {

}
