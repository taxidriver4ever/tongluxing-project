package com.tongluxing.common.exception;

import com.tongluxing.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>业务层主动抛出该异常后，会由全局异常处理器转换为统一失败响应。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    /**
     * 使用默认业务错误码创建异常。
     */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    /**
     * 使用预定义响应码创建异常。
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 使用预定义响应码和自定义文案创建异常。
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /**
     * 使用自定义错误码和文案创建异常。
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用自定义错误码、文案和原始异常创建异常。
     */
    public BusinessException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
