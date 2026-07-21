package com.tongluxing.match.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.tongluxing.common.result.Result;
import com.tongluxing.match.mapper.MatchAdminMapper;
import lombok.RequiredArgsConstructor;

/** 运营后台发现同行监控与推荐质量接口。 */
@RestController @RequiredArgsConstructor @RequestMapping("/v1/admin/matches")
public class MatchAdminController {
    private final MatchAdminMapper mapper;

    @GetMapping("/dashboard")
    public Result<Map<String,Object>> dashboard(@RequestParam(defaultValue="50") int limit) {
        int safeLimit=Math.max(1,Math.min(limit,100));
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("overview",mapper.overview());
        data.put("funnel",mapper.funnel());
        data.put("records",mapper.records(safeLimit));
        data.put("popularRoutes",mapper.popularRoutes(10));
        data.put("anomalies",mapper.anomalies());
        data.put("rule",Map.of("routeWeight",40,"destinationWeight",30,"timeWeight",20,"interestWeight",10,"maxTimeGapHours",24,"idealScore",80));
        return Result.success(data);
    }
}
