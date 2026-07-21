package com.tongluxing;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.admin.service.AdminConfigService;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.growth.integration.GrowthFacade;
import com.tongluxing.invite.integration.InviteRewardPort;

import lombok.RequiredArgsConstructor;

/**
 * 邀请奖励到成长模块的适配器。
 *
 * <p>invite-module 只判断奖励资格和记录奖励状态，实际同路值发放统一通过 growth-module。</p>
 */
@Component
@RequiredArgsConstructor
public class InviteGrowthRewardAdapter implements InviteRewardPort {

    private static final Map<String, Integer> REWARD_POINTS = Map.of(
            "INVITE_STAGE_1", 50,
            "INVITE_STAGE_3", 200,
            "INVITE_STAGE_10", 500,
            "INVITE_STAGE_30", 2000,
            "INVITE_STAGE_50", 5000,
            "FIRST_TEAM", 100
    );

    private final GrowthFacade growthFacade;
    private final AdminConfigService adminConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public int rewardPoints(String ruleCode) {
        int fallback = ruleCode != null && ruleCode.startsWith("INV_REG:")
                ? 100 : REWARD_POINTS.getOrDefault(ruleCode, 0);
        try {
            JsonNode rule = objectMapper.readTree(
                    adminConfigService.getConfig("INVITE", "invite.rules").configValue());
            if (ruleCode != null && ruleCode.startsWith("INV_REG:")) {
                return positive(rule.path("registerPoints").asInt(fallback), fallback);
            }
            if ("FIRST_TEAM".equals(ruleCode)) {
                return positive(rule.path("firstTeamPoints").asInt(fallback), fallback);
            }
            if (ruleCode != null && ruleCode.startsWith("INVITE_STAGE_")) {
                String stage = ruleCode.substring("INVITE_STAGE_".length());
                return positive(rule.path("stagePoints").path(stage).asInt(fallback), fallback);
            }
        } catch (BusinessException ignored) {
            // 未配置规则时使用 MVP 默认值。
        } catch (Exception ignored) {
            // 配置解析失败时降级，避免奖励主链路不可用。
        }
        return fallback;
    }

    @Override
    public void grantInviteReward(Long beneficiaryUserId, String rewardBizNo, String ruleCode) {
        boolean registerReward = ruleCode != null && ruleCode.startsWith("INV_REG:");
        int points = rewardPoints(ruleCode);
        if (points <= 0) {
            return;
        }
        growthFacade.grant(beneficiaryUserId,
                registerReward ? "INVITE_USER_REGISTER" : "INVITE",
                rewardBizNo,
                points,
                registerReward ? "邀请新用户注册奖励" : "邀请奖励");
    }

    private int positive(int value, int fallback) {
        return value > 0 && value <= 10000 ? value : fallback;
    }
}
