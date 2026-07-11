package com.tongluxing.groupbuy.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 拼团活动数据库实体，对应 groupbuy_activity 表。
 *
 * <p>记录一次拼团从发起、进行中、成团到失败或下线的完整状态。</p>
 */
@Data
public class GroupbuyActivity {
    /** 活动主键。 */
    private Long id;
    /** 商家 ID，来自商品快照。 */
    private Long merchantId;
    /** 被拼团购买的商品 ID。 */
    private Long productId;
    /** 发起拼团的用户 ID。 */
    private Long initiatorUserId;
    /** 成团所需人数。 */
    private Integer targetPeople;
    /** 当前已支付参团人数。 */
    private Integer currentPeople;
    /** 拼团价格，创建时从商品快照固化。 */
    private BigDecimal groupPrice;
    /** 阶梯价格规则 JSON，保留创建时的商品规则快照。 */
    private String ladderPriceJson;
    /** 活动状态：ONGOING、SUCCESS、FAILED、OFFLINE。 */
    private String activityStatus;
    /** 活动开始时间。 */
    private LocalDateTime startAt;
    /** 活动过期时间。 */
    private LocalDateTime expireAt;
    /** 成团成功时间。 */
    private LocalDateTime successAt;
    /** 失败或下线时间。 */
    private LocalDateTime failedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，0 表示有效。 */
    private Integer deleted;
}

