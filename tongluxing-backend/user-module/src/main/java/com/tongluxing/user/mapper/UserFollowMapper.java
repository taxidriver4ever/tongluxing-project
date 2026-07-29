package com.tongluxing.user.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.user.dto.UserFollowQueryDTO;

/**
 * 用户关注关系与关注通知数据访问接口。
 *
 * <p>{@code user_follow} 使用“关注者 + 被关注者”唯一索引保证一对用户只能存在一条
 * 关注关系；列表查询同时关联用户资料、统计和最新认证状态。写操作返回受影响行数，
 * 由 Service 决定幂等、事务和异常处理策略。</p>
 */
@Mapper
public interface UserFollowMapper {

    /**
     * 判断一条有方向的关注关系是否存在。
     *
     * @param followerId 主动发起关注的用户 ID
     * @param followedId 被关注的用户 ID
     * @return 已存在返回 1，否则返回 0；唯一索引保证结果不会大于 1
     */
    @Select("select count(*) from user_follow where follower_user_id=#{followerId} and followed_user_id=#{followedId}")
    int exists(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    /**
     * 新建关注关系。
     *
     * <p>并发重复插入会触发唯一索引异常，Service 将其作为幂等成功处理。</p>
     *
     * @param id 雪花算法生成的关系主键
     * @param followerId 关注者用户 ID
     * @param followedId 被关注者用户 ID
     * @param now 关系建立时间
     * @return 成功写入的行数
     */
    @Insert("""
            insert into user_follow(id,follower_user_id,followed_user_id,created_at)
            values(#{id},#{followerId},#{followedId},#{now})
            """)
    int insert(@Param("id") Long id, @Param("followerId") Long followerId,
               @Param("followedId") Long followedId, @Param("now") LocalDateTime now);

    /**
     * 为被关注者写入一条“新的关注”站内通知。
     *
     * <p>{@code requestId} 绑定具体关注关系，便于通知系统追踪来源并避免业务事件混淆。
     * 本方法与关注关系写入处于同一事务中。</p>
     *
     * @param id 通知消息主键
     * @param followerId 触发通知的关注者用户 ID
     * @param followedId 通知接收者用户 ID
     * @param requestId 由关系 ID 组成的业务幂等标识
     * @param now 通知创建与更新时间
     * @return 成功写入的行数
     */
    @Insert("""
            insert into notify_message
                (id, receiver_type, receiver_id, scene, event_type, title, content,
                 target_type, target_id, read_status, request_id, created_at, updated_at, deleted)
            values
                (#{id}, 'USER', #{followedId}, 'INTERACTION', 'USER_FOLLOW', '新的关注',
                 '有用户关注了你', 'USER', cast(#{followerId} as char), 'UNREAD',
                 #{requestId}, #{now}, #{now}, 0)
            """)
    int insertFollowNotification(@Param("id") Long id,
                                 @Param("followerId") Long followerId,
                                 @Param("followedId") Long followedId,
                                 @Param("requestId") String requestId,
                                 @Param("now") LocalDateTime now);

    /**
     * 统计指定用户尚未阅读的关注通知。
     *
     * @param userId 通知接收者用户 ID
     * @return 未删除且状态为 UNREAD 的 USER_FOLLOW 通知数量
     */
    @Select("""
            select count(*)
            from notify_message
            where receiver_type='USER' and receiver_id=#{userId}
              and scene='INTERACTION' and event_type='USER_FOLLOW'
              and read_status='UNREAD' and deleted=0
            """)
    long countUnreadFollowerNotifications(@Param("userId") Long userId);

    /**
     * 批量把指定用户的未读关注通知标记为已读。
     *
     * @param userId 通知接收者用户 ID
     * @param now 统一写入 read_at 与 updated_at 的时间
     * @return 实际更新的通知条数
     */
    @Update("""
            update notify_message
            set read_status='READ', read_at=#{now}, updated_at=#{now}
            where receiver_type='USER' and receiver_id=#{userId}
              and scene='INTERACTION' and event_type='USER_FOLLOW'
              and read_status='UNREAD' and deleted=0
            """)
    int markFollowerNotificationsRead(@Param("userId") Long userId,
                                      @Param("now") LocalDateTime now);

    /**
     * 删除一条有方向的关注关系。
     *
     * @return 删除的行数；关系不存在时为 0，因此天然支持重复取消关注
     */
    @Delete("delete from user_follow where follower_user_id=#{followerId} and followed_user_id=#{followedId}")
    int delete(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    /** @return 以该用户为被关注者的关系总数，即粉丝数 */
    @Select("select count(*) from user_follow where followed_user_id=#{userId}")
    long countFollowers(@Param("userId") Long userId);

    /** @return 以该用户为关注者的关系总数，即关注数 */
    @Select("select count(*) from user_follow where follower_user_id=#{userId}")
    long countFollowing(@Param("userId") Long userId);

    /**
     * 分页查询关注指定用户的人。
     *
     * <p>以关系表中的 follower_user_id 作为列表用户，左连接资料与统计，避免统计记录
     * 尚未初始化时丢失关注关系；最新认证状态通过相关子查询取得。</p>
     *
     * @param userId 被关注者用户 ID
     * @param offset 从零开始的 SQL 偏移量
     * @param size 本次最多读取的记录数
     */
    @Select("""
            select f.follower_user_id userId,coalesce(nullif(p.nickname,''),'同路行用户') nickname,
                   p.avatar_image_key avatarImageKey,
                   coalesce((select c.certification_status from user_driving_license_certification c
                     where c.user_id=f.follower_user_id and c.deleted=0 order by c.submitted_at desc limit 1),'UNSUBMITTED') certificationStatus,
                   coalesce(s.total_trip_count,0) totalTripCount,
                   coalesce(s.total_distance_meters,0) totalDistanceMeters,f.created_at followedAt
            from user_follow f
            left join user_profile p on p.user_id=f.follower_user_id and p.deleted=0
            left join user_statistics s on s.user_id=f.follower_user_id
            where f.followed_user_id=#{userId}
            order by f.created_at desc limit #{offset},#{size}
            """)
    List<UserFollowQueryDTO> followers(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    /**
     * 分页查询指定用户主动关注的人。
     *
     * <p>以 followed_user_id 作为列表用户，按关系建立时间倒序返回。</p>
     */
    @Select("""
            select f.followed_user_id userId,coalesce(nullif(p.nickname,''),'同路行用户') nickname,
                   p.avatar_image_key avatarImageKey,
                   coalesce((select c.certification_status from user_driving_license_certification c
                     where c.user_id=f.followed_user_id and c.deleted=0 order by c.submitted_at desc limit 1),'UNSUBMITTED') certificationStatus,
                   coalesce(s.total_trip_count,0) totalTripCount,
                   coalesce(s.total_distance_meters,0) totalDistanceMeters,f.created_at followedAt
            from user_follow f
            left join user_profile p on p.user_id=f.followed_user_id and p.deleted=0
            left join user_statistics s on s.user_id=f.followed_user_id
            where f.follower_user_id=#{userId}
            order by f.created_at desc limit #{offset},#{size}
            """)
    List<UserFollowQueryDTO> following(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    /**
     * 分页查询与指定用户互相关注的人。
     *
     * <p>通过 {@code reverse_follow} 自连接要求正向和反向关系同时存在，因此返回集合
     * 无需再在 Java 层过滤单向关注。</p>
     */
    @Select("""
            select f.followed_user_id userId,coalesce(nullif(p.nickname,''),'同路行用户') nickname,
                   p.avatar_image_key avatarImageKey,
                   coalesce((select c.certification_status from user_driving_license_certification c
                     where c.user_id=f.followed_user_id and c.deleted=0 order by c.submitted_at desc limit 1),'UNSUBMITTED') certificationStatus,
                   coalesce(s.total_trip_count,0) totalTripCount,
                   coalesce(s.total_distance_meters,0) totalDistanceMeters,
                   f.created_at followedAt
            from user_follow f
            inner join user_follow reverse_follow
                    on reverse_follow.follower_user_id=f.followed_user_id
                   and reverse_follow.followed_user_id=f.follower_user_id
            left join user_profile p on p.user_id=f.followed_user_id and p.deleted=0
            left join user_statistics s on s.user_id=f.followed_user_id
            where f.follower_user_id=#{userId}
            order by f.created_at desc
            limit #{offset},#{size}
            """)
    List<UserFollowQueryDTO> mutualFollows(@Param("userId") Long userId,
                                           @Param("offset") int offset,
                                           @Param("size") int size);
}
