package com.tongluxing.groupbuy.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.groupbuy.entity.GroupbuyActivity;

/**
 * 拼团活动表 MyBatis Mapper。
 *
 * <p>集中封装 groupbuy_activity 表的新增、查询、分页统计和状态流转更新。</p>
 */
@Mapper
public interface GroupbuyActivityMapper {
    /**
     * 插入拼团活动。
     *
     * @param activity 待持久化的活动实体
     */
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

    /**
     * 根据活动 ID 查询未删除的拼团活动。
     *
     * @param activityId 活动 ID
     * @return 活动实体；不存在时返回 null
     */
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

    /**
     * 按活动状态分页查询活动列表。
     *
     * @param status 活动状态，可为空
     * @param offset 分页偏移量
     * @param size 每页条数
     * @return 活动列表
     */
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

    /**
     * 统计指定状态下的活动总数。
     *
     * @param status 活动状态，可为空
     * @return 总记录数
     */
    @Select("""
            select count(1)
            from groupbuy_activity
            where deleted = 0
              and (#{status} is null or #{status} = '' or activity_status = #{status})
            """)
    long count(@Param("status") String status);

    /**
     * 为进行中的活动增加已支付参团人数。
     *
     * @param activityId 活动 ID
     * @param now 更新时间
     * @return 受影响行数
     */
    @Update("""
            update groupbuy_activity
            set current_people = current_people + 1,
                updated_at = #{now}
            where id = #{activityId}
              and activity_status = 'ONGOING'
              and deleted = 0
            """)
    int increasePeople(@Param("activityId") Long activityId, @Param("now") LocalDateTime now);

    /**
     * 当当前人数达到目标人数时，将进行中的活动标记为成功。
     *
     * @param activityId 活动 ID
     * @param now 成团时间
     * @return 受影响行数
     */
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

    /**
     * 当活动已过期且未达到目标人数时，将活动标记为失败。
     *
     * @param activityId 活动 ID
     * @param now 过期检查时间
     * @return 受影响行数
     */
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

    /**
     * 运营后台强制成团。
     *
     * @param activityId 活动 ID
     * @param now 干预时间
     * @return 受影响行数
     */
    @Update("""
            update groupbuy_activity
            set activity_status = 'SUCCESS',
                success_at = #{now},
                updated_at = #{now}
            where id = #{activityId}
              and activity_status = 'ONGOING'
              and deleted = 0
            """)
    int forceSuccess(@Param("activityId") Long activityId, @Param("now") LocalDateTime now);

    /**
     * 运营后台强制失败或下线活动。
     *
     * @param activityId 活动 ID
     * @param status 目标状态，通常为 FAILED 或 OFFLINE
     * @param now 干预时间
     * @return 受影响行数
     */
    @Update("""
            update groupbuy_activity
            set activity_status = #{status},
                failed_at = #{now},
                updated_at = #{now}
            where id = #{activityId}
              and activity_status = 'ONGOING'
              and deleted = 0
            """)
    int forceEnd(@Param("activityId") Long activityId,
                 @Param("status") String status,
                 @Param("now") LocalDateTime now);
}
