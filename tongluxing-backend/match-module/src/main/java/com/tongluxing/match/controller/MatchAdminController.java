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

/**
 * 运营后台“发现同行”监控与推荐质量接口。
 *
 * <p>该接口只聚合只读统计，不修改推荐规则或业务数据。路径位于 admin 命名空间，
 * 由统一 SecurityConfig 要求管理员角色。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/matches")
public class MatchAdminController {
    /** 运营统计专用 Mapper，不复用在线推荐查询，避免监控逻辑影响用户链路。 */
    private final MatchAdminMapper mapper;

    /**
     * 返回运营看板所需的概览、转化漏斗、近期推荐、热门路线和异常记录。
     *
     * @param limit 近期推荐记录数，最终限制在 1~100
     * @return 使用有序 Map 组织的看板聚合数据
     */
    @GetMapping("/dashboard")
    public Result<Map<String,Object>> dashboard(@RequestParam(defaultValue="50") int limit) {
        // 防止负数 LIMIT 或一次拉取过多记录拖慢运营查询。
        int safeLimit=Math.max(1,Math.min(limit,100));
        // LinkedHashMap 保持前端调试时字段顺序稳定。
        Map<String,Object> data=new LinkedHashMap<>();
        // 概览是活跃行程、推荐量、平均分及各行为累计值。
        data.put("overview",mapper.overview());
        // 漏斗按 IMPRESSION → CLICK → APPLY → ACCEPT → START → FINISH 聚合。
        data.put("funnel",mapper.funnel());
        data.put("records",mapper.records(safeLimit));
        // 热门路线固定取前 10，避免由外部参数放大分组统计成本。
        data.put("popularRoutes",mapper.popularRoutes(10));
        data.put("anomalies",mapper.anomalies());
        // 同步返回当前评分规则说明，方便运营解释分值；这里只展示，不动态改权重。
        data.put("rule",Map.of("routeWeight",40,"destinationWeight",30,"timeWeight",20,"interestWeight",10,"maxTimeGapHours",24,"idealScore",80));
        return Result.success(data);
    }
}
