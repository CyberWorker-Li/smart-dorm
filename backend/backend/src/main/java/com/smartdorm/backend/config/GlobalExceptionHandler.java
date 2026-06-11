package com.smartdorm.backend.config;

import com.smartdorm.backend.common.BusinessException;
import com.smartdorm.backend.common.Result;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        return Result.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldError() == null
                ? "参数校验失败"
                : exception.getBindingResult().getFieldError().getDefaultMessage();
        return Result.fail(400, message);
    }

    /** 请求体不是合法 JSON、或数字字段被传成空字符串等 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        return Result.fail(400, "请求体格式错误，请检查 JSON；数字项（层数、房间数）勿传空字符串或非数字");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception) {
        return Result.fail(500, exception.getMessage());
    }
}