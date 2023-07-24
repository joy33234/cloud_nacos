package com.seektop.common.mvc.exception;

import com.seektop.common.rest.Result;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BindException.class)
    public Result bindExceptionHandler(BindException e) {
        String message = e.getFieldErrors().get(0).getDefaultMessage();
        return Result.newBuilder().fail(ResultCode.PARAM_ERROR).setMessage(message).build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result bindExceptionHandler(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        String fields = e.getBindingResult().getFieldErrors().get(0).getField()+" ";
        return Result.newBuilder().fail(ResultCode.PARAM_ERROR).setMessage(fields+message).build();
    }

    @ExceptionHandler(GlobalException.class)
    public Result globalExceptionHandler(GlobalException e) {
        log.error(String.format("message:%s,extraMessage:%s", e.getMessage(), e.getExtraMessage()), e);
        return Result.newBuilder().fail(e.getCode()).setMessage(e.getExtraMessage()).build();
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public Result missingRequestHeaderExceptionHandler(MissingRequestHeaderException e) {
        log.error(e.getMessage(), e);
        return Result.newBuilder().fail(ResultCode.ILLEGAL_REQUEST).setMessage(e.getMessage()).build();
    }

    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception e, HttpServletRequest httpServletRequest) {
        log.error("请求路径："+httpServletRequest.getRequestURL()+"出错");
        log.error(e.getMessage(), e);
        return Result.newBuilder().fail(ResultCode.SERVER_ERROR).build();
    }
}