package com.tongluxing.drivertrack.vo;

import java.util.List;

/**
 * GPS 轨迹批量上传轻量响应。
 *
 * <p>confirmedSequenceNos 表示服务端已经处理或确认重复的点，客户端收到后即可从本地
 * 可靠队列删除这些 sequenceNo；失败且未确认的点必须继续保留等待下次补传。</p>
 */
public record DriverTrackBatchUploadResponse(
        List<Long> confirmedSequenceNos,
        Integer totalDistance,
        String riskLevel,
        Boolean settlementReviewRequired
) {
}
