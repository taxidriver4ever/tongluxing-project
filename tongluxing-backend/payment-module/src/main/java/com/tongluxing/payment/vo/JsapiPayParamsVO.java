package com.tongluxing.payment.vo;
/**
 * JsapiPayParamsVO 视图响应对象。
 */

public record JsapiPayParamsVO(
        String appId,
        String timeStamp,
        String nonceStr,
        String packageValue,
        String signType,
        String paySign
) {
}

