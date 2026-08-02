package com.tongluxing.vehicle.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 车辆认证图片明细，对应 vehicle_certification_image 表。
 *
 * <p>一次认证可包含行驶证正副页和多张车辆外观图，因此与认证主表是多对一关系。</p>
 */
@Data
public class VehicleCertificationImage {
    /** 图片明细主键。 */
    private Long id;
    /** 所属认证申请 ID，用于查询某次提交的全部附件。 */
    private Long certificationId;
    /** 所属车辆 ID，用于删除车辆驳回历史时批量清理。 */
    private Long vehicleId;
    /** 图片类型，如 REGISTRATION_LICENSE 或 VEHICLE。 */
    private String imageType;
    /** 对象存储资源 key 或可访问 URL，不保存图片二进制内容。 */
    private String imageKey;
    /** 同一认证申请内的展示顺序，从 0 开始。 */
    private Integer sortNo;
    /** 附件记录创建时间。 */
    private LocalDateTime createdAt;
    /** 逻辑删除标记：0 有效，1 已删除。 */
    private Integer deleted;
}
