package com.tongluxing.user.vo;

import java.time.LocalDate;

/**
 * 当前用户完整资料返回对象。
 *
 * <p>该对象用于“我的资料”，因此包含生日、城市编码等可编辑字段；不包含驾驶证
 * 明文、隐私设置或数据库审计字段。</p>
 *
 * @param userId 平台用户 ID
 * @param tongluxingId 公开且不可变的同路行号
 * @param nickname 昵称
 * @param avatarImageKey 头像资源 Key
 * @param gender 性别编码
 * @param birthday 生日
 * @param cityCode 城市编码
 * @param cityName 城市名称
 * @param bio 个人简介
 * @param profileStatus 资料状态
 * @param drivingLicenseCertificationStatus 最新驾驶证认证状态
 */
public record UserProfileVO(
        Long userId, String tongluxingId, String nickname, String avatarImageKey, Integer gender, LocalDate birthday,
        String cityCode, String cityName, String bio, String profileStatus, String drivingLicenseCertificationStatus
) {
}

