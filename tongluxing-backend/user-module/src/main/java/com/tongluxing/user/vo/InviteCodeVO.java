package com.tongluxing.user.vo;

/**
 * 用户邀请码返回对象。
 *
 * @param inviteCode 用户当前邀请码
 * @param scene 适用业务场景
 * @param enabled 当前是否允许使用
 */
public record InviteCodeVO(String inviteCode, String scene, Boolean enabled) {
}

