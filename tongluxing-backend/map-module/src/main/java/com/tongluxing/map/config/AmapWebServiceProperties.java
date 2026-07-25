package com.tongluxing.map.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 高德 Web 服务配置。
 *
 * <p>密钥只从服务端环境变量读取，禁止下发到 Flutter 或 Web 前端。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "amap.web-service")
public class AmapWebServiceProperties {

    /** Web 服务 Key。 */
    private String key;

    /** 高德 Web 服务基础地址。 */
    private String baseUrl = "https://restapi.amap.com";

    /** 驾车路线规划策略，32 为高德推荐策略。 */
    private int drivingStrategy = 32;
}
