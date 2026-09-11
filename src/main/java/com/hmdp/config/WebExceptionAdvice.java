package com.hmdp.config;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 全局异常处理：用@RestControllerAdvice统一捕获Controller抛出的异常
// 目的：业务抛异常时不返回500白页，而是统一转成项目约定的Result格式
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    // 捕获所有RuntimeException（业务异常基本都是这个）
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        // 记录日志，方便开发排查
        log.error(e.toString(), e);
        // 返回统一的失败结果给前端
        return Result.fail("服务器异常");
    }
}
