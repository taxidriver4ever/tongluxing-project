package com.tongluxing.admin.sos;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record SosEventResponse(String id,String userId,String requestId,BigDecimal latitude,BigDecimal longitude,
        BigDecimal locationAccuracyMeters,String address,String message,String alarmMode,String status,
        String acceptedBy,LocalDateTime acceptedAt,String resolvedBy,LocalDateTime resolvedAt,
        String resolutionNote,LocalDateTime occurredAt) {}
