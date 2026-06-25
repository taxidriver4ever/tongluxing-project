package com.tongdao.admin.vo;

import java.time.LocalDateTime;

public record AdminConfigVO(
        Long configId,
        String configDomain,
        String configKey,
        Integer versionNo,
        String configValue,
        String configStatus,
        LocalDateTime effectiveAt,
        LocalDateTime updatedAt
) {
}
