package com.hmdp.dto;

import lombok.Data;

/**
 * 用户对外传输对象（脱敏版 User）。
 *
 * 关键安全点：它只含 id / 昵称 / 头像，故意**不含 phone、password、salt 等敏感字段**。
 * 登录成功后，把 User 拷贝成 UserDTO 再存进 Redis Token、再返回给前端，
 * 这样密码永远不会离开服务端。这比直接返回 User 实体安全得多。
 */
@Data
public class UserDTO {
    /** 用户 id */
    private Long id;
    /** 昵称 */
    private String nickName;
    /** 头像 */
    private String icon;
}
