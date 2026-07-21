package com.tongluxing.admin.sos;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@Validated @RestController @RequiredArgsConstructor @RequestMapping("/v1/admin/sos/events")
public class AdminSosEventController {
    private final SosEventService service;
    @GetMapping public Result<List<SosEventResponse>> list(@RequestParam(required=false)String status,
        @RequestParam(defaultValue="100")Integer limit){return Result.success(service.list(status,limit));}
    @PostMapping("/{id}/accept") public Result<SosEventResponse> accept(@PathVariable Long id){return Result.success(service.accept(id));}
    @PostMapping("/{id}/resolve") public Result<SosEventResponse> resolve(@PathVariable Long id,
        @Valid @RequestBody ResolveSosEventRequest request){return Result.success(service.resolve(id,request));}
}
