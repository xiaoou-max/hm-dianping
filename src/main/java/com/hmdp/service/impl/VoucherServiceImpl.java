package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

// 优惠券业务：查店铺优惠券、新增秒杀券
// 秒杀券要同时写两张表（tb_voucher基础信息 + tb_seckill_voucher库存/时间），并把库存预热到Redis
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    // 秒杀券服务：操作tb_seckill_voucher表
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    // Redis操作模板：预热秒杀库存
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 按商铺查优惠券列表（普通券+秒杀券，一条SQL联表查出来）
    @Override
    public Result queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        return Result.ok(vouchers);
    }

    // 新增秒杀券：写优惠券表 + 写秒杀券表 + 库存预热到Redis（事务保证两表一致）
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 1.保存优惠券基础信息（tb_voucher）
        save(voucher);
        // 2.保存秒杀信息（tb_seckill_voucher）
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 3.把库存预热到Redis：下单时直接对Redis扣减，不压数据库
        stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
    }
}
