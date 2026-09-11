package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户资料表（tb_user_info），与 User 一对一。把"账号"(User)与"展示资料"(本类)分开，互不干扰。
 * 主键 user_id 即用户 id。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user_info")
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;
    private String city;
    private String introduce;
    private Integer fans;
    private Integer followee;
    private Boolean gender;
    private LocalDate birthday;
    private Integer credits;
    private Boolean level;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
