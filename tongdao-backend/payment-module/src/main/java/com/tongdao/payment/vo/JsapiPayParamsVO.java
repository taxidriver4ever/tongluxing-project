package com.tongdao.payment.vo;

public record JsapiPayParamsVO(
        String appId,
        String timeStamp,
        String nonceStr,
        String packageValue,
        String signType,
        String paySign
) {
}

