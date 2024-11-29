package com.hmdp.handler;

import com.hmdp.common.exception.BaseException;
import com.hmdp.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler
    public Result handleException(BaseException ex){
        log.error("异常信息：{}",ex.getMessage());
        return Result.fail(ex.getMessage());
    }
}
