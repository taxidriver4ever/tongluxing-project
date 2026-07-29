package com.tongluxing.admin.sos;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tongluxing.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * 负责管理端 SOS 紧急事件相关 HTTP 接口的参数接收、校验和统一结果封装。
 * 具体业务规则委托给服务层，控制器本身不直接操作数据库。
 */
@Validated @RestController @RequiredArgsConstructor @RequestMapping("/v1/admin/sos/events")
public class AdminSosEventController {
    private final SosEventService service;
    /** 按筛选条件查询列表，并限制返回数量以保护接口与数据库。 */
    @GetMapping public Result<List<SosEventResponse>> list(@RequestParam(required=false)String status,
        @RequestParam(defaultValue="100")Integer limit){return Result.success(service.list(status,limit));}
    /** 受理待处理的 SOS 事件，并记录当前操作人与受理时间。 */
    @PostMapping("/{id}/accept") public Result<SosEventResponse> accept(@PathVariable Long id){return Result.success(service.accept(id));}
    /** 将 SOS 事件结案，并保存结案说明与操作信息。 */
    @PostMapping("/{id}/resolve") public Result<SosEventResponse> resolve(@PathVariable Long id,
        @Valid @RequestBody ResolveSosEventRequest request){return Result.success(service.resolve(id,request));}
}
