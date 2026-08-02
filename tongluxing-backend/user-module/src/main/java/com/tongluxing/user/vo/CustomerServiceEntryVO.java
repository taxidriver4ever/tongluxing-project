package com.tongluxing.user.vo;

/**
 * 客服入口返回对象。
 *
 * @param scene 入口所属页面或业务场景
 * @param channel 客服渠道类型
 * @param target 渠道目标标识，例如会话或客服账号
 */
public record CustomerServiceEntryVO(String scene, String channel, String target) {
}

