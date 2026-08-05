package com.tongluxing.notify.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.notify.dto.RegisterPushDeviceRequest;
import com.tongluxing.notify.service.AppPushService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** App 系统推送设备管理接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/push/devices")
public class AppPushDeviceController {

    private final AppPushService appPushService;

    @PostMapping
    public Result<Void> register(@Valid @RequestBody RegisterPushDeviceRequest request) {
        appPushService.registerCurrentDevice(request);
        return Result.success();
    }

    @DeleteMapping("/{deviceId}")
    public Result<Void> disable(@PathVariable String deviceId) {
        appPushService.disableCurrentDevice(deviceId);
        return Result.success();
    }
}
