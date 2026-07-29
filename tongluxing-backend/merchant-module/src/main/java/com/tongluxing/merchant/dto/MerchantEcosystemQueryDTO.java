package com.tongluxing.merchant.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 封装商家生态查询请求参数。
 * 字段上的 Jakarta Validation 约束在进入业务层前完成格式和取值范围校验。
 */
@Data
public class MerchantEcosystemQueryDTO {
    private Long storeId; private Long merchantId; private String merchantName;
    private String storeName; private String address; private String storeAddress;
    private BigDecimal longitude; private BigDecimal latitude; private String contactPhoneMask;
    private String businessHours; private String parkingInfo; private String storeStatus;
    private Long couponId; private String couponName; private String coverImageKey;
    private String description; private String category; private BigDecimal originalPrice;
    private BigDecimal salePrice; private Integer stock; private Integer soldCount; private Integer limitCount;
    private Boolean groupEnabled; private Integer groupPeople; private Integer groupTimeoutHours;
    private LocalDateTime publishTime; private LocalDateTime expireTime;
    private LocalDateTime useStartTime; private LocalDateTime useEndTime;
    private Boolean reservationRequired; private Boolean refundable;
    private Boolean holidayAvailable; private Boolean stackable;
    private String useInstructions; private String auditStatus; private String rejectReason;
    private String offerStatus;
}
