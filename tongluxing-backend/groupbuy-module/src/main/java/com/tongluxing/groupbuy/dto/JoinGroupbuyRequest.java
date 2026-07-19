package com.tongluxing.groupbuy.dto;

import jakarta.validation.constraints.NotBlank;

/** 用户在 MVP 本地支付闭环中加入拼单；真实支付接入后由支付回调替代。 */
public record JoinGroupbuyRequest(@NotBlank String requestId) {}
