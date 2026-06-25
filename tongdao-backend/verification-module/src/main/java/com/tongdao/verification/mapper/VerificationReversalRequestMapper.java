package com.tongdao.verification.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.verification.entity.VerificationReversalRequest;

@Mapper
public interface VerificationReversalRequestMapper {

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
