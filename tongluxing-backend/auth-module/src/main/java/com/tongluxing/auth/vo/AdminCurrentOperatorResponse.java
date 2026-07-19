package com.tongluxing.auth.vo;

/** 当前 Web Admin 操作员信息。 */
public record AdminCurrentOperatorResponse(
        Long operatorId,
        String username,
        String displayName,
        Long expireAt
) {
}
