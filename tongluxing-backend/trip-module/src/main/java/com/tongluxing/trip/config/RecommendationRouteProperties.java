package com.tongluxing.trip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 推荐匹配路线的简化参数；完整路线持久化不受这些参数影响。 */
@Component
@ConfigurationProperties(prefix = "recommendation.route")
public class RecommendationRouteProperties {
    /** RDP 容差，单位为米；应使用真实路线 A/B 数据继续校准。 */
    private double rdpEpsilon = 50D;
    private int maxPoints = 60;

    public double getRdpEpsilon() { return rdpEpsilon; }

    public void setRdpEpsilon(double rdpEpsilon) {
        if (!Double.isFinite(rdpEpsilon) || rdpEpsilon < 0D) {
            throw new IllegalArgumentException("recommendation.route.rdp-epsilon must be a finite non-negative number");
        }
        this.rdpEpsilon = rdpEpsilon;
    }

    public int getMaxPoints() { return maxPoints; }

    public void setMaxPoints(int maxPoints) {
        if (maxPoints < 2) throw new IllegalArgumentException("recommendation.route.max-points must be at least 2");
        this.maxPoints = maxPoints;
    }
}
