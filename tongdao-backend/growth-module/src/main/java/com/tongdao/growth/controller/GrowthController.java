package com.tongdao.growth.controller;
import org.springframework.web.bind.annotation.*;
import com.tongdao.common.result.Result;
import com.tongdao.growth.integration.GrowthFacade.GrowthGrantResult;
import com.tongdao.growth.service.GrowthService;
import com.tongdao.user.model.UserModels.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class GrowthController {

    private final GrowthService service;

    @GetMapping("/v1/growth/me")
    public Result<GrowthSummaryVO> me(){
        return Result.success(service.getCurrentSummary());

    }
    @GetMapping("/v1/growth/me/logs")
    public Result<PageResult<GrowthLogVO>> logs(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return Result.success(service.getCurrentLogs(page,size));}

    @GetMapping("/v1/growth/me/badges")
    public Result<BadgeWallVO> badges(){return Result.success(service.getCurrentBadges());}

    @GetMapping("/v1/growth/users/{userId}/public-summary")
    public Result<GrowthSummaryVO> publicSummary(@PathVariable Long userId){return Result.success(service.getSummary(userId));}

    @PostMapping("/internal/v1/growth/grants")
    public Result<GrowthGrantResult> grant(@RequestBody GrantRequest r){return Result.success(service.grant(r.userId(),r.bizType(),r.bizId(),r.points(),r.remark()));}

    public record GrantRequest(Long userId,String bizType,String bizId,Integer points,String remark){}
}
