package com.tongluxing.user.vo;

/**
 * 草稿发布结果返回对象。
 *
 * @param draftId 原草稿主键
 * @param publishType 发布类型
 * @param publishedId 根据 publishType 指向普通行程或组队行程主键
 */
public record PublishResultVO(Long draftId, String publishType, Long publishedId) {
}

