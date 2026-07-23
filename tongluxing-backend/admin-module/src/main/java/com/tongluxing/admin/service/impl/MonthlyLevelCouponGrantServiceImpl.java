package com.tongluxing.admin.service.impl;

import java.time.YearMonth;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.admin.service.AdminConfigService;
import com.tongluxing.admin.service.MonthlyLevelCouponGrantService;
import com.tongluxing.admin.vo.AdminConfigVO;
import com.tongluxing.coupon.integration.CouponFacade;
import com.tongluxing.growth.mapper.GrowthMapper;
import com.tongluxing.growth.dto.GrowthEligibleUserDTO;

import lombok.RequiredArgsConstructor;

/** 等级月度优惠券发放实现，依靠 coupon_user 的来源唯一键保证重复执行幂等。 */
@Service
@RequiredArgsConstructor
public class MonthlyLevelCouponGrantServiceImpl implements MonthlyLevelCouponGrantService {
    public static final String CONFIG_KEY = "coupon.monthly-level-grants";
    private static final Logger log = LoggerFactory.getLogger(MonthlyLevelCouponGrantServiceImpl.class);

    private final AdminConfigService configService;
    private final GrowthMapper growthMapper;
    private final CouponFacade couponFacade;
    private final ObjectMapper objectMapper;

    @Override
    public GrantSummary grant(YearMonth month) {
        AdminConfigVO config = configService.getConfig("COUPON", CONFIG_KEY);
        RuleConfig rules = parse(config.configValue());
        if (!rules.enabled()) {
            return new GrantSummary(month.toString(), 0, 0, 0, 0, false);
        }
        LocalDate runDate = month.atDay(1);
        if (rules.startDate() != null && runDate.isBefore(rules.startDate())
                || rules.endDate() != null && runDate.isAfter(rules.endDate())) {
            return new GrantSummary(month.toString(), 0, 0, 0, 0, false);
        }

        int users = 0;
        int issued = 0;
        int duplicates = 0;
        int failed = 0;
        for (Map.Entry<String, LevelRule> entry : rules.levels().entrySet()) {
            String level = entry.getKey().toUpperCase();
            if (!List.of("LV4", "LV5", "LV6").contains(level)) {
                continue;
            }
            List<GrowthEligibleUserDTO> candidates = growthMapper.findEligibleUsersByLevel(level).stream()
                    .filter(user -> rules.cityCodes().isEmpty() || rules.cityCodes().contains(user.cityCode()))
                    .limit(Math.max(0, rules.maxUsersPerRun() - users))
                    .toList();
            users += candidates.size();
            for (GrowthEligibleUserDTO user : candidates) {
                Long userId = user.userId();
                Counter wash = issueCopies(userId, rules.washTemplateId(), entry.getValue().wash(),
                        month, level, "WASH");
                Counter maintenance = issueCopies(userId, rules.maintenanceTemplateId(), entry.getValue().maintenance(),
                        month, level, "MAINTENANCE");
                issued += wash.issued + maintenance.issued;
                duplicates += wash.duplicates + maintenance.duplicates;
                failed += wash.failed + maintenance.failed;
            }
        }
        return new GrantSummary(month.toString(), users, issued, duplicates, failed, true);
    }

    private Counter issueCopies(Long userId, Long templateId, int copies, YearMonth month,
                                String level, String benefitType) {
        Counter result = new Counter();
        if (copies <= 0) {
            return result;
        }
        if (templateId == null) {
            result.failed += copies;
            log.warn("Monthly coupon template is not configured: level={}, type={}", level, benefitType);
            return result;
        }
        for (int index = 1; index <= copies; index++) {
            String sourceBizId = "%s:%s:%s:%d".formatted(month, level, benefitType, index);
            try {
                CouponFacade.CouponIssueResult issued = couponFacade.issue(
                        userId, templateId, "LEVEL_MONTHLY", sourceBizId);
                if (issued.duplicate()) {
                    result.duplicates++;
                } else {
                    result.issued++;
                }
            } catch (RuntimeException exception) {
                result.failed++;
                log.warn("Monthly coupon issue failed: userId={}, templateId={}, sourceBizId={}, reason={}",
                        userId, templateId, sourceBizId, exception.getMessage());
            }
        }
        return result;
    }

    private RuleConfig parse(String json) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalArgumentException("月度等级发券规则为空");
        }
        try {
            RuleConfig config = objectMapper.readValue(json, RuleConfig.class);
            Map<String, LevelRule> levels = config.levels() == null ? defaultLevels() : config.levels();
            Map<String, LevelRule> normalizedLevels = new LinkedHashMap<>();
            levels.forEach((level, rule) -> normalizedLevels.put(level,
                    new LevelRule(clampCopies(rule.wash()), clampCopies(rule.maintenance()))));
            List<String> cityCodes = config.cityCodes() == null ? List.of() : config.cityCodes();
            int maxUsers = config.maxUsersPerRun() <= 0 ? 100000 : Math.min(config.maxUsersPerRun(), 1000000);
            return new RuleConfig(config.enabled(), config.washTemplateId(),
                    config.maintenanceTemplateId(), cityCodes, config.startDate(), config.endDate(), maxUsers, normalizedLevels);
        } catch (Exception exception) {
            throw new IllegalArgumentException("月度等级发券规则 JSON 格式错误", exception);
        }
    }

    private Map<String, LevelRule> defaultLevels() {
        Map<String, LevelRule> levels = new LinkedHashMap<>();
        levels.put("LV4", new LevelRule(1, 0));
        levels.put("LV5", new LevelRule(2, 1));
        levels.put("LV6", new LevelRule(3, 2));
        return levels;
    }

    private int clampCopies(int value) {
        return Math.min(20, Math.max(0, value));
    }

    public record RuleConfig(boolean enabled, Long washTemplateId, Long maintenanceTemplateId,
                             List<String> cityCodes, LocalDate startDate, LocalDate endDate,
                             int maxUsersPerRun, Map<String, LevelRule> levels) {
    }

    public record LevelRule(int wash, int maintenance) {
    }

    private static final class Counter {
        private int issued;
        private int duplicates;
        private int failed;
    }
}
