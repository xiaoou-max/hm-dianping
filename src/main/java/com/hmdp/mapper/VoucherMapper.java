package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 优惠券表 DAO 接口，对应 tb_voucher。继承 BaseMapper 即拥有基础 CRUD。
 *
 * 唯一自定义方法 queryVoucherOfShop：按商铺 id 查券列表。
 *   @Param("shopId") 给 SQL 里的 #{shopId} 命名绑定参数；对应的 SQL 写在
 *   resources/mapper/VoucherMapper.xml 里（MP 无法生成的复杂查询就写在 XML 中）。
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
