package com.tongluxing.match.dto;

import jakarta.validation.constraints.Size;

/**
 * 用户申请加入搜索到的公开行程。
 *
 * @param message 给队长的申请说明；为空时 Service 使用场景化默认文案
 * @param selfDrive 是否由申请人自驾，null 按 false 处理
 * @param applicantVehicleId 申请人选择的车辆 ID；非自驾或暂未选车时可以为空
 */
public record TripApplicationRequest(
        @Size(max = 255) String message,
        Boolean selfDrive,
        Long applicantVehicleId
) {
}
