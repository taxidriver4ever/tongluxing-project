package com.tongluxing.admin.sos;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
/**
 * 描述SOS 紧急事件持久化记录及其当前业务状态。
 * 对象由 MyBatis 在数据库行与 Java 字段之间进行映射。
 */
@Data
public class SosEvent {
    private Long id; private Long userId; private String requestId;
    private BigDecimal latitude; private BigDecimal longitude; private BigDecimal locationAccuracyMeters;
    private String address; private String message; private String alarmMode; private String eventStatus;
    private Long acceptedBy; private LocalDateTime acceptedAt; private Long resolvedBy; private LocalDateTime resolvedAt;
    private String resolutionNote; private LocalDateTime occurredAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
