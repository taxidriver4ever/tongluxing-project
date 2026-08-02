package com.tongluxing.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 草稿发布请求。
 *
 * <p>publishType 决定发布为普通行程还是组队行程。</p>
 *
 * @param publishType TRIP 或 TEAM
 */
public record PublishDraftRequest(@NotBlank @Pattern(regexp = "TRIP|TEAM") String publishType) {
}

