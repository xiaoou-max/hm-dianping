package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀券表（tb_seckill_voucher）。voucher_id 既是本表主键(IdType.INPUT)，也是关联 tb_voucher 的外键。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_seckill_voucher")
public class SeckillVoucher implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "voucher_id", type = IdType.INPUT)
    private Long voucherId;
    private Integer stock;
    private LocalDateTime createTime;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private LocalDateTime updateTime;
}
