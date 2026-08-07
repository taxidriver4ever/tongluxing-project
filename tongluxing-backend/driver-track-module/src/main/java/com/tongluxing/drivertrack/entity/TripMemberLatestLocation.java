package com.tongluxing.drivertrack.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 行进中普通成员的最新位置快照。
 *
 * <p>普通成员只保留一条最新位置，用于成员与队长距离检测和失联判断；
 * 不参与轨迹里程、风险汇总或成长值结算。</p>
 */
@Data
public class TripMemberLatestLocation {
    private Long id;
    private Long tripId;
    private Long captainUserId;
    private Long memberUserId;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal speed;
    private BigDecimal accuracy;
    private Long sequenceNo;
    private Integer mockLocation;
    private LocalDateTime recordTime;
    private LocalDateTime serverReceiveTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
