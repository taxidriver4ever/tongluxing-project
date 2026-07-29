package com.tongluxing.merchant.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.merchant.dto.*;
import com.tongluxing.merchant.mapper.MerchantEcosystemMapper;
import com.tongluxing.merchant.mapper.MerchantProfileMapper;
import com.tongluxing.merchant.mapper.MerchantPartnerMapper;
import com.tongluxing.merchant.service.MerchantEcosystemService;
import com.tongluxing.merchant.vo.*;
import com.tongluxing.user.support.CurrentUserContext;
import lombok.RequiredArgsConstructor;

/**
 * 实现商家生态业务编排，集中处理权限、状态流转和事务边界。
 * 通过 Mapper/外部端口完成持久化或集成，并把内部模型转换为对外视图。
 */
@Service @RequiredArgsConstructor
public class MerchantEcosystemServiceImpl implements MerchantEcosystemService {
    private final CurrentUserContext currentUser;
    private final MerchantProfileMapper profileMapper;
    private final MerchantPartnerMapper partnerMapper;
    private final MerchantEcosystemMapper mapper;

    private MerchantQueryDTO merchant() {
        MerchantQueryDTO row=profileMapper.findByUserId(currentUser.requireUserId());
        if(row==null || !"APPROVED".equals(row.getAuditStatus())) throw new BusinessException(403,"仅审核通过的商家可使用商家中心");
        return row;
    }

    /** 执行 overview 对应的领域操作，并返回统一的业务结果。 */
    @Override public MerchantCenterOverviewVO overview(){
        MerchantQueryDTO m=merchant(); List<MerchantEcosystemQueryDTO> stores=mapper.stores(m.getMerchantId());
        List<MerchantEcosystemQueryDTO> offers=mapper.offersByMerchant(m.getMerchantId());
        var partner=partnerMapper.find(m.getMerchantId());
        return new MerchantCenterOverviewVO(m.getMerchantId(),m.getMerchantName(),m.getMerchantLevel(),partner==null?"NONE":partner.applicationStatus(),stores.size(),offers.size(),
            offers.stream().filter(x->"PENDING".equals(x.getAuditStatus())).count(),
            offers.stream().filter(x->"APPROVED".equals(x.getAuditStatus())).count(),
            offers.stream().mapToLong(x->x.getStock()==null?0:x.getStock()).sum(),
            offers.stream().mapToLong(x->x.getSoldCount()==null?0:x.getSoldCount()).sum());
    }

    /** 执行 stores 对应的领域操作，并返回统一的业务结果。 */
    @Override public List<MerchantStoreVO> stores(){MerchantQueryDTO m=merchant();return mapper.stores(m.getMerchantId()).stream().map(this::store).toList();}

    /** 校验请求并创建对应资源。 */
    @Override @Transactional public MerchantStoreVO createStore(MerchantStoreRequest r){
        MerchantQueryDTO m=merchant(); long id=SnowflakeIdGenerator.nextId(); LocalDateTime now=LocalDateTime.now();
        mapper.insertStore(id,m.getMerchantId(),r.storeName().trim(),r.address().trim(),r.longitude(),r.latitude(),mask(r.contactPhone()),r.businessHours().trim(),trim(r.parkingInfo()),now);
        return store(mapper.store(m.getMerchantId(),id));
    }

    /** 执行 offers 对应的领域操作，并返回统一的业务结果。 */
    @Override public List<MerchantCouponOfferVO> offers(){MerchantQueryDTO m=merchant();return mapper.offersByMerchant(m.getMerchantId()).stream().map(this::offer).toList();}

    /** 校验请求并创建对应资源。 */
    @Override @Transactional public MerchantCouponOfferVO createOffer(MerchantCouponOfferRequest r){
        MerchantQueryDTO m=merchant(); validate(m.getMerchantId(),r); long id=SnowflakeIdGenerator.nextId();
        insert(id,m.getMerchantId(),r); return offer(mapper.offer(id));
    }

    /** 执行 resubmitOffer 对应的领域操作，并返回统一的业务结果。 */
    @Override @Transactional public MerchantCouponOfferVO resubmitOffer(Long couponId,MerchantCouponOfferRequest r){
        MerchantQueryDTO m=merchant(); validate(m.getMerchantId(),r); LocalDateTime now=LocalDateTime.now();
        int n=mapper.resubmitOffer(couponId,m.getMerchantId(),r.couponName().trim(),r.coverImageKey().trim(),r.description().trim(),r.category().trim(),r.originalPrice(),r.salePrice(),r.stock(),r.limitCount(),r.groupEnabled(),r.groupPeople(),r.groupTimeoutHours(),r.publishTime(),r.expireTime(),r.useStartTime(),r.useEndTime(),r.reservationRequired(),r.refundable(),r.holidayAvailable(),r.stackable(),r.useInstructions().trim(),now);
        if(n!=1) throw new BusinessException(409,"仅已驳回的优惠券可以修改重提"); return offer(mapper.offer(couponId));
    }

    /** 执行 offersForAdmin 对应的领域操作，并返回统一的业务结果。 */
    @Override public PageResult<MerchantCouponOfferVO> offersForAdmin(String status,int page,int size){
        String s=trim(status).toUpperCase(); if(!s.isEmpty()&&!List.of("PENDING","APPROVED","REJECTED").contains(s))throw new BusinessException(ResultCode.BAD_REQUEST,"审核状态不合法");
        int p=Math.max(1,page),z=Math.min(100,Math.max(1,size)); return new PageResult<>(mapper.offersForAdmin(s,(p-1)*z,z).stream().map(this::offer).toList(),mapper.countOffers(s),p,z);
    }
    /** 执行 offerForAdmin 对应的领域操作，并返回统一的业务结果。 */
    @Override public MerchantCouponOfferVO offerForAdmin(Long id){MerchantEcosystemQueryDTO r=mapper.offer(id);if(r==null)throw new BusinessException(ResultCode.NOT_FOUND,"优惠券不存在");return offer(r);}
    /** 执行 auditOffer 对应的领域操作，并返回统一的业务结果。 */
    @Override @Transactional public MerchantCouponOfferVO auditOffer(Long id,String result,String reason,Long reviewerId){
        if(!List.of("APPROVED","REJECTED").contains(result))throw new BusinessException(ResultCode.BAD_REQUEST,"审核结果不合法");
        if("REJECTED".equals(result)&&trim(reason).length()<2)throw new BusinessException(ResultCode.BAD_REQUEST,"拒绝时必须填写原因");
        LocalDateTime now=LocalDateTime.now();
        if(mapper.auditOffer(id,result,"REJECTED".equals(result)?trim(reason):"",reviewerId,now)!=1)throw new BusinessException(409,"优惠券已完成审核");
        if("APPROVED".equals(result)&&mapper.activateClaimTemplate(id,now)!=1)throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR,"可领取优惠券模板激活失败");
        return offer(mapper.offer(id));
    }
    /** 执行 manageOfferStatus 对应的领域操作，并返回统一的业务结果。 */
    @Override @Transactional public MerchantCouponOfferVO manageOfferStatus(Long id,String status){
        if(!List.of("ACTIVE","INACTIVE").contains(status))throw new BusinessException(ResultCode.BAD_REQUEST,"上下架状态不合法");
        LocalDateTime now=LocalDateTime.now();
        if(mapper.updateOfferStatus(id,status,now)!=1)throw new BusinessException(409,"仅已审核通过的优惠券可以上下架");
        if(mapper.updateClaimTemplateStatus(id,status,now)!=1)throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR,"优惠券模板状态同步失败");
        return offer(mapper.offer(id));
    }
    /** 执行 marketplace 对应的领域操作，并返回统一的业务结果。 */
    @Override public PageResult<MerchantCouponOfferVO> marketplace(int page,int size){int p=Math.max(1,page),z=Math.min(50,Math.max(1,size));List<MerchantCouponOfferVO> list=mapper.marketOffers(LocalDateTime.now(),(p-1)*z,z).stream().map(this::offer).toList();return new PageResult<>(list,list.size(),p,z);}
    /** 执行 marketplaceDetail 对应的领域操作，并返回统一的业务结果。 */
    @Override public MerchantCouponOfferVO marketplaceDetail(Long id){MerchantCouponOfferVO v=offerForAdmin(id);if(!"APPROVED".equals(v.auditStatus())||!"ACTIVE".equals(v.offerStatus()))throw new BusinessException(ResultCode.NOT_FOUND,"优惠券未上线");return v;}

    /** 执行 groupbuySnapshot 对应的领域操作，并返回统一的业务结果。 */
    @Override public MerchantCouponOfferVO groupbuySnapshot(Long id){
        MerchantCouponOfferVO v=marketplaceDetail(id);
        if(!v.groupEnabled()) throw new BusinessException(ResultCode.BAD_REQUEST,"该优惠券未开启拼单");
        if(v.stock()==null||v.stock()<1) throw new BusinessException(409,"拼单优惠券库存不足");
        return v;
    }
    /** 执行 reserveGroupbuyStock 对应的领域操作，并返回统一的业务结果。 */
    @Override @Transactional public void reserveGroupbuyStock(Long id,Integer quantity){
        if(mapper.reserveGroupbuyStock(id,quantity,LocalDateTime.now())!=1) throw new BusinessException(409,"拼单优惠券库存不足或已下线");
    }

    private void insert(Long id,Long merchantId,MerchantCouponOfferRequest r){mapper.insertOffer(id,merchantId,r.storeId(),r.couponName().trim(),r.coverImageKey().trim(),r.description().trim(),r.category().trim(),r.originalPrice(),r.salePrice(),r.stock(),r.limitCount(),r.groupEnabled(),r.groupPeople(),r.groupTimeoutHours(),r.publishTime(),r.expireTime(),r.useStartTime(),r.useEndTime(),r.reservationRequired(),r.refundable(),r.holidayAvailable(),r.stackable(),r.useInstructions().trim(),LocalDateTime.now());}
    private void validate(Long merchantId,MerchantCouponOfferRequest r){if(mapper.store(merchantId,r.storeId())==null)throw new BusinessException(ResultCode.BAD_REQUEST,"适用门店不属于当前商家");if(r.salePrice().compareTo(r.originalPrice())>0)throw new BusinessException(ResultCode.BAD_REQUEST,"优惠价不能高于原价");if(!r.expireTime().isAfter(r.publishTime())||!r.useEndTime().isAfter(r.useStartTime()))throw new BusinessException(ResultCode.BAD_REQUEST,"时间范围不合法");if(r.groupEnabled()&&(r.groupPeople()==null||r.groupPeople()<2||r.groupTimeoutHours()==null||r.groupTimeoutHours()<1))throw new BusinessException(ResultCode.BAD_REQUEST,"开启拼单时必须设置至少2人成团和有效时长");}
    private MerchantStoreVO store(MerchantEcosystemQueryDTO r){return new MerchantStoreVO(r.getStoreId(),r.getMerchantId(),r.getStoreName(),r.getAddress(),r.getLongitude(),r.getLatitude(),r.getContactPhoneMask(),r.getBusinessHours(),r.getParkingInfo(),r.getStoreStatus());}
    private MerchantCouponOfferVO offer(MerchantEcosystemQueryDTO r){return new MerchantCouponOfferVO(r.getCouponId(),r.getMerchantId(),r.getMerchantName(),r.getStoreId(),r.getStoreName(),r.getStoreAddress(),r.getLongitude(),r.getLatitude(),r.getCouponName(),r.getCoverImageKey(),r.getDescription(),r.getCategory(),r.getOriginalPrice(),r.getSalePrice(),r.getStock(),r.getSoldCount(),r.getLimitCount(),Boolean.TRUE.equals(r.getGroupEnabled()),r.getGroupPeople(),r.getGroupTimeoutHours(),r.getPublishTime(),r.getExpireTime(),r.getUseStartTime(),r.getUseEndTime(),Boolean.TRUE.equals(r.getReservationRequired()),Boolean.TRUE.equals(r.getRefundable()),Boolean.TRUE.equals(r.getHolidayAvailable()),Boolean.TRUE.equals(r.getStackable()),r.getUseInstructions(),r.getAuditStatus(),r.getRejectReason(),r.getOfferStatus());}
    private String trim(String v){return v==null?"":v.trim();} private String mask(String p){return p.substring(0,3)+"****"+p.substring(7);}
}
