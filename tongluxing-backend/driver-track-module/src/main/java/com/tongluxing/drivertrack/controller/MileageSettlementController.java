package com.tongluxing.drivertrack.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.drivertrack.dto.MileageSettlementRequest;
import com.tongluxing.drivertrack.service.MileageSettlementService;
import com.tongluxing.drivertrack.vo.MileageSettlementResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 内部里程结算接口，用于联调、测试数据和未来里程来源适配。
 */
@Validated
@RestController
@RequiredArgsConstructor
public class MileageSettlementController {

    private final MileageSettlementService mileageSettlementService;

    @PostMapping("/internal/v1/mileage/settlements")
    public Result<MileageSettlementResponse> settleMileage(@Valid @RequestBody MileageSettlementRequest request) {
        return Result.success(mileageSettlementService.settleMileage(
                request.tripId(),
                request.userId(),
                request.distanceMeters()
        ));
    }
}
