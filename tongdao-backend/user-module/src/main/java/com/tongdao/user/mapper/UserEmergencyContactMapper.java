package com.tongdao.user.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.user.entity.UserEmergencyContact;

@Mapper
public interface UserEmergencyContactMapper {

    @Select("""
            select id, user_id, contact_name, relation, phone_cipher, phone_mask, is_default, created_at, updated_at, deleted
            from user_emergency_contact
            where user_id = #{userId} and deleted = 0
            order by is_default desc, created_at desc
            """)
    List<UserEmergencyContact> findByUserId(@Param("userId") Long userId);

    @Select("""
            select id, user_id, contact_name, relation, phone_cipher, phone_mask, is_default, created_at, updated_at, deleted
            from user_emergency_contact
            where id = #{id} and user_id = #{userId} and deleted = 0
            limit 1
            """)
    UserEmergencyContact findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Insert("""
            insert into user_emergency_contact
                (id, user_id, contact_name, relation, phone_cipher, phone_mask, is_default, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{contactName}, #{relation}, #{phoneCipher}, #{phoneMask}, #{isDefault},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    int insert(UserEmergencyContact contact);

    @Update("""
            update user_emergency_contact
            set contact_name = #{contactName},
                relation = #{relation},
                phone_cipher = #{phoneCipher},
                phone_mask = #{phoneMask},
                is_default = #{isDefault},
                updated_at = #{updatedAt}
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    int update(UserEmergencyContact contact);

    @Update("""
            update user_emergency_contact
            set deleted = 1, updated_at = #{updatedAt}
            where id = #{id} and user_id = #{userId} and deleted = 0
            """)
    int logicDelete(@Param("id") Long id, @Param("userId") Long userId, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            update user_emergency_contact
            set is_default = 0, updated_at = #{updatedAt}
            where user_id = #{userId} and deleted = 0
            """)
    int clearDefault(@Param("userId") Long userId, @Param("updatedAt") LocalDateTime updatedAt);
}
