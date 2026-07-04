package com.tongluxing.verification.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.verification.entity.VerificationReversalRequest;

/**
 * 核销冲正申请 Mapper。
 */
@Mapper
public interface VerificationReversalRequestMapper {

    /**
     * 新增冲正申请。
     */
    @Insert("""
            insert into verification_reversal_request
                (id, verification_id, merchant_id, applicant_id, reason,
                 audit_status, reviewer_id, reviewed_at, reject_reason,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{verificationId}, #{merchantId}, #{applicantId}, #{reason},
                 #{auditStatus}, #{reviewerId}, #{reviewedAt}, #{rejectReason},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(VerificationReversalRequest request);

    /**
     * 根据冲正申请 ID 查询记录。
     */
    @Select("""
            select id, verification_id, merchant_id, applicant_id, reason,
                   audit_status, reviewer_id, reviewed_at, reject_reason,
                   created_at, updated_at, deleted
            from verification_reversal_request
            where id = #{id}
              and deleted = 0
            limit 1
            """)
    VerificationReversalRequest findById(@Param("id") Long id);

    /**
     * 查询指定核销记录最近一次冲正申请。
     */
    @Select("""
            select id, verification_id, merchant_id, applicant_id, reason,
                   audit_status, reviewer_id, reviewed_at, reject_reason,
                   created_at, updated_at, deleted
            from verification_reversal_request
            where verification_id = #{verificationId}
              and deleted = 0
            order by created_at desc, id desc
            limit 1
            """)
    VerificationReversalRequest findLatestByVerificationId(@Param("verificationId") Long verificationId);
}
