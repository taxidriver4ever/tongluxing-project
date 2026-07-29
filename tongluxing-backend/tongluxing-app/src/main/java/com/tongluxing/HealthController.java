package com.tongluxing;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供给 Nginx、Docker Compose 和运维探针使用的匿名健康检查。
 */
@RestController
public class HealthController {

    /**
     * 仅返回进程存活状态，不暴露下游连接信息或敏感配置。
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
