package com.tongluxing.admin.vo;

import java.time.LocalDateTime;

/**
 * 运营配置响应。
 */
public record AdminConfigVO(
        /** 配置 ID。 */
        Long configId,
        /** 配置域。 */
        String configDomain,
        /** 配置键。 */
        String configKey,
        /** 当前版本号。 */
        Integer versionNo,
        /** 当前版本配置值。 */
        String configValue,
        /** 配置状态。 */
        String configStatus,
        /** 生效时间。 */
        LocalDateTime effectiveAt,
        /** 最近更新时间。 */
        LocalDateTime updatedAt
) {
}
