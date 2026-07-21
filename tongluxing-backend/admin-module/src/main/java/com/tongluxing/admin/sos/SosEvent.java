package com.tongluxing.admin.sos;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
@Data
public class SosEvent {
    private Long id; private Long userId; private String requestId;
    private BigDecimal latitude; private BigDecimal longitude; private BigDecimal locationAccuracyMeters;
    private String address; private String message; private String alarmMode; private String eventStatus;
    private Long acceptedBy; private LocalDateTime acceptedAt; private Long resolvedBy; private LocalDateTime resolvedAt;
    private String resolutionNote; private LocalDateTime occurredAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
