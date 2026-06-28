package com.tongdao.common.result;

import lombok.Getter;

/**
 * 统一响应码定义。
 *
 * <p>用于在业务异常、全局异常处理和接口响应中保持错误码语义一致。</p>
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    METHOD_NOT_ALLOWED(405, "method not allowed"),
    VALIDATION_ERROR(422, "validation error"),
    INTERNAL_SERVER_ERROR(500, "internal server error"),
    BUSINESS_ERROR(10000, "business error");

    private final int code;
    private final String message;

    /**
     * 创建响应码枚举。
     */
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
