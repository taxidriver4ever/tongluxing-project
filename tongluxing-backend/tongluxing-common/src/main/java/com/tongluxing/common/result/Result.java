package com.tongluxing.common.result;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口响应模型。
 *
 * <p>所有 Controller 返回值通过该对象表达业务状态、提示信息和响应数据。</p>
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer code;

    private String message;

    private T data;

    private Boolean success;

    /**
     * 构造无数据的成功响应。
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 构造带数据的成功响应。
     */
    public static <T> Result<T> success(T data) {
        return of(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, true);
    }

    /**
     * 构造自定义提示文案的成功响应。
     */
    public static <T> Result<T> success(String message, T data) {
        return of(ResultCode.SUCCESS.getCode(), message, data, true);
    }

    /**
     * 构造默认业务失败响应。
     */
    public static <T> Result<T> fail() {
        return fail(ResultCode.BUSINESS_ERROR);
    }

    /**
     * 构造自定义失败文案的业务失败响应。
     */
    public static <T> Result<T> fail(String message) {
        return of(ResultCode.BUSINESS_ERROR.getCode(), message, null, false);
    }

    /**
     * 构造自定义错误码和文案的失败响应。
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return of(code, message, null, false);
    }

    /**
     * 按预定义响应码构造失败响应。
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return of(resultCode.getCode(), resultCode.getMessage(), null, false);
    }

    /**
     * 按预定义响应码和自定义文案构造失败响应。
     */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return of(resultCode.getCode(), message, null, false);
    }

    /**
     * 构造完整响应对象。
     */
    public static <T> Result<T> of(Integer code, String message, T data, Boolean success) {
        return new Result<>(code, message, data, success);
    }
}
