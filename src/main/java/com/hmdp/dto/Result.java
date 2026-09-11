package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一响应结果类：整个项目所有 Controller 都返回它，前端只认这一种格式。
 *
 * 设计思想（后端通用套路）：
 *   - success：是否成功
 *   - errorMsg：失败时的错误信息（成功时为 null）
 *   - data：成功时返回的业务数据（可以是任意对象/集合）
 *   - total：分页时的总条数
 *
 * 用“静态工厂方法” ok()/fail() 代替 new，调用更简洁：
 *   return Result.ok(shop);          // 成功带数据
 *   return Result.ok(list, total);   // 成功带数据和总数
 *   return Result.fail("错误信息");    // 失败带错误信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    /** 是否成功 */
    private Boolean success;
    /** 失败时的错误信息 */
    private String errorMsg;
    /** 成功时返回的业务数据 */
    private Object data;
    /** 分页总条数 */
    private Long total;

    /** 成功，无数据（如发送验证码） */
    public static Result ok(){
        return new Result(true, null, null, null);
    }
    /** 成功，带单个数据 */
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }
    /** 成功，带集合数据 + 总数（分页场景） */
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }
    /** 失败，带错误信息 */
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}
