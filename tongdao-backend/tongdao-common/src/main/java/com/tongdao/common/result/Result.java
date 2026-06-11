package com.tongdao.common.result;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return of(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, true);
    }

    public static <T> Result<T> success(String message, T data) {
        return of(ResultCode.SUCCESS.getCode(), message, data, true);
    }

    public static <T> Result<T> fail() {
        return fail(ResultCode.BUSINESS_ERROR);
    }

    public static <T> Result<T> fail(String message) {
        return of(ResultCode.BUSINESS_ERROR.getCode(), message, null, false);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return of(code, message, null, false);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return of(resultCode.getCode(), resultCode.getMessage(), null, false);
    }

    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return of(resultCode.getCode(), message, null, false);
    }

    public static <T> Result<T> of(Integer code, String message, T data, Boolean success) {
        return new Result<>(code, message, data, success);
    }
}
