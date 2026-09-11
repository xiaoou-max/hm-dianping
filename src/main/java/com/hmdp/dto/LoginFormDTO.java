package com.hmdp.dto;

import lombok.Data;

/**
 * 登录表单的“接收对象”（Data Transfer Object）。
 *
 * 为什么不用 User 实体直接接参数？因为前端只传 phone/code/password，
 * 用一个专门的 DTO 接参，既避免把整个 User（含数据库字段）暴露给接口，也更清晰。
 * 注意：code（验证码）和 password（密码）是“二选一”登录方式，不会同时出现。
 */
@Data
public class LoginFormDTO {
    /** 手机号 */
    private String phone;
    /** 短信验证码（验证码登录时使用） */
    private String code;
    /** 密码（密码登录时使用） */
    private String password;
}
