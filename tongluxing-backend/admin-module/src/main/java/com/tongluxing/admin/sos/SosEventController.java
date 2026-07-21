package com.tongluxing.admin.sos;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@Validated @RestController @RequiredArgsConstructor @RequestMapping("/v1/sos/events")
public class SosEventController {
    private final SosEventService service;
    /** 平台内上报真实落库；MVP alarmMode固定MOCK，不会自动呼叫警方。 */
    @PostMapping public Result<SosEventResponse> create(@Valid @RequestBody CreateSosEventRequest request){
        return Result.success(service.create(request));}
}
