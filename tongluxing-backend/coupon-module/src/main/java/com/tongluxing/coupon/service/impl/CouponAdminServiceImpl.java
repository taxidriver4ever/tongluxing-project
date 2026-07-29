package com.tongluxing.coupon.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.coupon.dto.AdminCouponTemplateRequest;
import com.tongluxing.coupon.dto.AdminCouponTemplateVO;
import com.tongluxing.coupon.mapper.CouponMapper;
import com.tongluxing.coupon.service.CouponAdminService;

import lombok.RequiredArgsConstructor;

/**
 * 实现优惠券管理端业务编排，集中处理权限、状态流转和事务边界。
 * 通过 Mapper/外部端口完成持久化或集成，并把内部模型转换为对外视图。
 */
@Service
@RequiredArgsConstructor
public class CouponAdminServiceImpl implements CouponAdminService {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "CAR_WASH", "MAINTENANCE", "CASH", "GENERAL_CASH",
            "FUEL", "EV_CHARGING", "PARKING", "HOTEL", "DINING",
            "CAR_BEAUTY", "TIRE", "CAR_SUPPLIES", "ROAD_RESCUE",
            "REPAIR", "SCENIC", "CAMPING", "DRIVER_SERVICE");

    private final CouponMapper mapper;
    private final ObjectMapper objectMapper;

    /** 校验请求并创建资源；重复请求由业务层按幂等规则处理。 */
    @Override public AdminCouponTemplateVO create(AdminCouponTemplateRequest request) {
        try { objectMapper.readTree(request.scopeJson()); } catch(Exception e) { throw new BusinessException("适用范围必须是合法 JSON"); }
        String couponType = request.couponType().trim().toUpperCase();
        if (!SUPPORTED_TYPES.contains(couponType)) {
            throw new BusinessException("不支持的券类型");
        }
        long id=SnowflakeIdGenerator.nextId();
        mapper.insertAdminTemplate(id,request.couponName().trim(),couponType,request.issuerId(),
                request.thresholdAmount(),request.discountAmount(),request.scopeJson(),request.validDays(),
                request.totalQuantity(),request.perUserLimit(),LocalDateTime.now());
        return mapper.findAdminTemplate(id);
    }

    /** 按筛选条件查询列表，并限制返回数量以保护接口与数据库。 */
    @Override public List<AdminCouponTemplateVO> list(String status) {
        String normalized=status==null?"":status.trim().toUpperCase();
        if(!normalized.isEmpty()&&!List.of("ACTIVE","INACTIVE").contains(normalized))throw new BusinessException("券模板状态不合法");
        return mapper.findAdminTemplates(normalized);
    }

    /** 执行 status 对应的领域操作，并返回统一的业务结果。 */
    @Override public AdminCouponTemplateVO status(Long id,String status) {
        String normalized=status==null?"":status.trim().toUpperCase();
        if(!List.of("ACTIVE","INACTIVE").contains(normalized))throw new BusinessException("券模板状态不合法");
        LocalDateTime now=LocalDateTime.now();
        if(mapper.updateAdminTemplateStatus(id,normalized,now)!=1)throw new BusinessException("券模板不存在");
        mapper.updatePartnerPoolStatusIfPresent(id,normalized,now);
        return mapper.findAdminTemplate(id);
    }
}
