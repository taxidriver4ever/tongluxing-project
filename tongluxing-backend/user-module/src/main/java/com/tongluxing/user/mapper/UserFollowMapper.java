package com.tongluxing.user.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tongluxing.user.dto.UserFollowQueryDTO;

/** 用户关注关系数据访问。 */
@Mapper
public interface UserFollowMapper {

    @Select("select count(*) from user_follow where follower_user_id=#{followerId} and followed_user_id=#{followedId}")
    int exists(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    @Insert("""
            insert into user_follow(id,follower_user_id,followed_user_id,created_at)
            values(#{id},#{followerId},#{followedId},#{now})
            """)
    int insert(@Param("id") Long id, @Param("followerId") Long followerId,
               @Param("followedId") Long followedId, @Param("now") LocalDateTime now);

    @Delete("delete from user_follow where follower_user_id=#{followerId} and followed_user_id=#{followedId}")
    int delete(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    @Select("select count(*) from user_follow where followed_user_id=#{userId}")
    long countFollowers(@Param("userId") Long userId);

    @Select("select count(*) from user_follow where follower_user_id=#{userId}")
    long countFollowing(@Param("userId") Long userId);

    @Select("""
            select p.user_id userId,p.nickname,p.avatar_image_key avatarImageKey,
                   coalesce((select c.certification_status from user_driving_license_certification c
                     where c.user_id=p.user_id and c.deleted=0 order by c.submitted_at desc limit 1),'UNSUBMITTED') certificationStatus,
                   coalesce(s.total_trip_count,0) totalTripCount,
                   coalesce(s.total_distance_meters,0) totalDistanceMeters,f.created_at followedAt
            from user_follow f join user_profile p on p.user_id=f.follower_user_id and p.deleted=0
            left join user_statistics s on s.user_id=p.user_id
            where f.followed_user_id=#{userId}
            order by f.created_at desc limit #{offset},#{size}
            """)
    List<UserFollowQueryDTO> followers(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    @Select("""
            select p.user_id userId,p.nickname,p.avatar_image_key avatarImageKey,
                   coalesce((select c.certification_status from user_driving_license_certification c
                     where c.user_id=p.user_id and c.deleted=0 order by c.submitted_at desc limit 1),'UNSUBMITTED') certificationStatus,
                   coalesce(s.total_trip_count,0) totalTripCount,
                   coalesce(s.total_distance_meters,0) totalDistanceMeters,f.created_at followedAt
            from user_follow f join user_profile p on p.user_id=f.followed_user_id and p.deleted=0
            left join user_statistics s on s.user_id=p.user_id
            where f.follower_user_id=#{userId}
            order by f.created_at desc limit #{offset},#{size}
            """)
    List<UserFollowQueryDTO> following(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);
}
