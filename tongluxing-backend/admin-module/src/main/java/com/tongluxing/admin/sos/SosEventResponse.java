package com.tongluxing.admin.sos;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * SOS 紧急事件对外返回视图。
 * 该模型只承载客户端需要的数据，避免直接暴露数据库实体及内部实现字段。
 */
public record SosEventResponse(String id,String userId,String requestId,BigDecimal latitude,BigDecimal longitude,
        BigDecimal locationAccuracyMeters,String address,String message,String alarmMode,String status,
        String acceptedBy,LocalDateTime acceptedAt,String resolvedBy,LocalDateTime resolvedAt,
        String resolutionNote,LocalDateTime occurredAt) {}
