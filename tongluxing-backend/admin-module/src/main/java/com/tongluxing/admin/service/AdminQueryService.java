package com.tongluxing.admin.service;

import java.time.LocalDateTime;

import com.tongluxing.admin.dto.AdminAuditLogQueryRequest;
import com.tongluxing.admin.vo.AdminAuditLogVO;
import com.tongluxing.admin.vo.AdminDrivingLicenseDetailVO;
import com.tongluxing.admin.vo.AdminVehicleCertificationDetailVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.user.model.UserModels.DrivingLicenseAuditSummaryVO;
import com.tongluxing.vehicle.vo.VehicleCertificationAuditSummaryVO;

/**
 * 运营后台查询服务。
 *
 * <p>目前已实现审计日志查询；业务列表查询保留统一占位入口，方便后续接入各业务模块查询端口。</p>
 */
public interface AdminQueryService {

    /** 分页查询后台审计日志。 */
    PageResult<AdminAuditLogVO> auditLogs(AdminAuditLogQueryRequest request);

    /** 返回业务列表占位空分页，保持前端接口形态稳定。 */
    PageResult<Object> emptyBusinessPage(String module, String status, String keyword,
                                         LocalDateTime startTime, LocalDateTime endTime,
                                         int page, int size);

    /** 分页查询驾驶证认证申请。 */
    PageResult<DrivingLicenseAuditSummaryVO> drivingLicenseCertifications(
            String status, String keyword, int page, int size);

    /** 查询驾驶证审核详情并生成短期图片地址。 */
    AdminDrivingLicenseDetailVO drivingLicenseCertificationDetail(Long certificationId);

    /** 分页查询车辆认证申请。 */
    PageResult<VehicleCertificationAuditSummaryVO> vehicleCertifications(
            String status, String keyword, int page, int size);

    /** 查询车辆认证审核详情并生成短期图片地址。 */
    AdminVehicleCertificationDetailVO vehicleCertificationDetail(Long certificationId);
}
