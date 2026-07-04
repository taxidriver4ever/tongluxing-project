package com.tongluxing.common.exception;

import java.util.Objects;
import java.util.stream.Collectors;

import com.tongluxing.common.result.Result;
import com.tongluxing.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器。
 *
 * <p>统一拦截参数校验、请求格式、业务异常和未处理异常，并转换为标准 {@link Result} 响应。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务层主动抛出的业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException exception) {
        log.warn("Business exception: code={}, message={}", exception.getCode(), exception.getMessage());
        return Result.fail(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理请求体参数校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        return Result.fail(ResultCode.VALIDATION_ERROR, buildFieldErrorMessage(exception));
    }

    /**
     * 处理表单或查询参数绑定失败。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.VALIDATION_ERROR, message);
    }

    /**
     * 处理单个参数约束校验失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.VALIDATION_ERROR, message);
    }

    /**
     * 处理缺少参数、类型不匹配或请求体不可读等错误请求。
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public Result<Void> handleBadRequestException(Exception exception) {
        log.warn("Bad request: {}", exception.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, exception.getMessage());
    }

    /**
     * 处理 HTTP 方法不支持。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException exception) {
        return Result.fail(ResultCode.METHOD_NOT_ALLOWED, exception.getMessage());
    }

    /**
     * 处理接口路径不存在。
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandlerFoundException(NoHandlerFoundException exception) {
        return Result.fail(ResultCode.NOT_FOUND, exception.getMessage());
    }

    /**
     * 兜底处理未预期异常，避免异常堆栈直接暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception. path={}, method={}", request.getRequestURI(), request.getMethod(), exception);
        return Result.fail(ResultCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 拼接请求体字段校验错误信息。
     */
    private String buildFieldErrorMessage(MethodArgumentNotValidException exception) {
        return exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
    }

    /**
     * 格式化单个字段错误信息。
     */
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + Objects.requireNonNullElse(fieldError.getDefaultMessage(), "invalid value");
    }
}
