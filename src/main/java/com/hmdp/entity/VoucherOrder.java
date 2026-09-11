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
 * 优惠券订单表（tb_voucher_order）。id 用 IdType.INPUT：订单号由 Redis 自增 ID 生成(见 RedisIdWorker)，
 * 而非自增，高性能且有全局唯一、带时间片段的订单号。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_voucher_order")
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long voucherId;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;
}
