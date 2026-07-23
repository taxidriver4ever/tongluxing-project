package com.tongluxing.coupon.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.coupon.dto.AdminCouponTemplateRequest;
import com.tongluxing.coupon.dto.AdminCouponTemplateVO;
import com.tongluxing.coupon.mapper.CouponMapper;
import com.tongluxing.coupon.service.CouponAdminService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponAdminServiceImpl implements CouponAdminService {
    private final CouponMapper mapper;
    private final ObjectMapper objectMapper;

    @Override public AdminCouponTemplateVO create(AdminCouponTemplateRequest request) {
        try { objectMapper.readTree(request.scopeJson()); } catch(Exception e) { throw new BusinessException("适用范围必须是合法 JSON"); }
        long id=SnowflakeIdGenerator.nextId();
        mapper.insertAdminTemplate(id,request.couponName().trim(),request.couponType().trim(),request.issuerId(),
                request.thresholdAmount(),request.discountAmount(),request.scopeJson(),request.validDays(),
                request.totalQuantity(),request.perUserLimit(),LocalDateTime.now());
        return mapper.findAdminTemplate(id);
    }

    @Override public List<AdminCouponTemplateVO> list(String status) {
        String normalized=status==null?"":status.trim().toUpperCase();
        if(!normalized.isEmpty()&&!List.of("ACTIVE","INACTIVE").contains(normalized))throw new BusinessException("券模板状态不合法");
        return mapper.findAdminTemplates(normalized);
    }

    @Override public AdminCouponTemplateVO status(Long id,String status) {
        String normalized=status==null?"":status.trim().toUpperCase();
        if(!List.of("ACTIVE","INACTIVE").contains(normalized))throw new BusinessException("券模板状态不合法");
        LocalDateTime now=LocalDateTime.now();
        if(mapper.updateAdminTemplateStatus(id,normalized,now)!=1)throw new BusinessException("券模板不存在");
        mapper.updatePartnerPoolStatusIfPresent(id,normalized,now);
        return mapper.findAdminTemplate(id);
    }
}
