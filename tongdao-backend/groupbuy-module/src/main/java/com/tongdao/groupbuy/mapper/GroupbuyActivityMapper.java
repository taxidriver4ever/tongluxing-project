package com.tongdao.groupbuy.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.groupbuy.entity.GroupbuyActivity;
/**
 * GroupbuyActivityMapper MyBatis Mapper。
 */
@Mapper
public interface GroupbuyActivityMapper {
    @Insert("""
            insert into groupbuy_activity
                (id, merchant_id, product_id, initiator_user_id, target_people, current_people,
                 group_price, ladder_price_json, activity_status, start_at, expire_at,
                 success_at, failed_at, created_at, updated_at, deleted)
            values
                (#{id}, #{merchantId}, #{productId}, #{initiatorUserId}, #{targetPeople}, #{currentPeople},
                 #{groupPrice}, #{ladderPriceJson}, #{activityStatus}, #{startAt}, #{expireAt},
                 #{successAt}, #{failedAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(GroupbuyActivity activity);

    @Select("""
            select id, merchant_id, product_id, initiator_user_id, target_people, current_people,
                   group_price, ladder_price_json, activity_status, start_at, expire_at,
                   success_at, failed_at, created_at, updated_at, deleted
            from groupbuy_activity
            where id = #{activityId}
              and deleted = 0
            limit 1
            """)
    GroupbuyActivity findById(@Param("activityId") Long activityId);

    @Select("""
            select id, merchant_id, product_id, initiator_user_id, target_people, current_people,
                   group_price, ladder_price_json, activity_status, start_at, expire_at,
                   success_at, failed_at, created_at, updated_at, deleted
            from groupbuy_activity
            where deleted = 0
              and (#{status} is null or #{status} = '' or activity_status = #{status})
            order by created_at desc
            limit #{offset}, #{size}
            """)
    List<GroupbuyActivity> list(@Param("status") String status, @Param("offset") int offset, @Param("size") int size);

    @Select("""
            select count(1)
            from groupbuy_activity
            where deleted = 0
              and (#{status} is null or #{status} = '' or activity_status = #{status})
            """)
    long count(@Param("status") String status);

    @Update("""
            update groupbuy_activity
            set current_people = current_people + 1,
                updated_at = #{now}
            where id = #{activityId}
              and activity_status = 'ONGOING'
              and deleted = 0
            """)
    int increasePeople(@Param("activityId") Long activityId, @Param("now") LocalDateTime now);

    @Update("""
            update groupbuy_activity
            set activity_status = 'SUCCESS',
                success_at = #{now},
                updated_at = #{now}
            where id = #{activityId}
              and activity_status = 'ONGOING'
              and current_people >= target_people
              and deleted = 0
            """)
    int markSuccessIfReached(@Param("activityId") Long activityId, @Param("now") LocalDateTime now);

    @Update("""
            update groupbuy_activity
            set activity_status = 'FAILED',
                failed_at = #{now},
                updated_at = #{now}
            where id = #{activityId}
              and activity_status = 'ONGOING'
              and expire_at <= #{now}
              and current_people < target_people
              and deleted = 0
            """)
    int markFailedIfExpired(@Param("activityId") Long activityId, @Param("now") LocalDateTime now);
}

