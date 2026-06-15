package com.tongdao.user.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongdao.user.entity.UserIdentityCertification;

@Mapper
public interface UserIdentityCertificationMapper {

    @Select("""
            select id, user_id, real_name, id_card_no_cipher, id_card_no_mask, face_image_key, status,
                   reject_reason, submitted_at, reviewed_by, reviewed_at, created_at, updated_at
            from user_identity_certification
            where user_id = #{userId}
            order by submitted_at desc
            limit 1
            """)
    UserIdentityCertification findLatestByUserId(@Param("userId") Long userId);

    @Insert("""
            insert into user_identity_certification
                (id, user_id, real_name, id_card_no_cipher, id_card_no_mask, face_image_key, status,
                 reject_reason, submitted_at, reviewed_by, reviewed_at, created_at, updated_at)
            values
                (#{id}, #{userId}, #{realName}, #{idCardNoCipher}, #{idCardNoMask}, #{faceImageKey}, #{status},
                 #{rejectReason}, #{submittedAt}, #{reviewedBy}, #{reviewedAt}, #{createdAt}, #{updatedAt})
            """)
    int insert(UserIdentityCertification certification);
}
