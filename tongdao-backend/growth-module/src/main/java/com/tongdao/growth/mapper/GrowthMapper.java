package com.tongdao.growth.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface GrowthMapper {
    @Select("select id,total_points totalPoints,level_code levelCode,version from growth_account where user_id=#{userId} and deleted=0 limit 1")
    Map<String,Object> findAccount(@Param("userId") Long userId);
    @Select("select id,total_points totalPoints,level_code levelCode,version from growth_account where user_id=#{userId} and deleted=0 limit 1 for update")
    Map<String,Object> findAccountForUpdate(@Param("userId") Long userId);
    @Insert("insert into growth_account(id,user_id,total_points,level_code,version,created_at,updated_at,deleted) values(#{id},#{userId},0,'LV1',0,#{now},#{now},0)")
    int insertAccount(@Param("id") Long id,@Param("userId") Long userId,@Param("now") LocalDateTime now);
    @Select("select level_code from growth_level_rule where enabled_flag=1 and deleted=0 and min_points<=#{points} and (max_points is null or max_points>=#{points}) order by min_points desc limit 1")
    String findLevelCode(@Param("points") int points);
    @Select("select min_points from growth_level_rule where enabled_flag=1 and deleted=0 and min_points>#{points} order by min_points limit 1")
    Integer findNextLevelPoints(@Param("points") int points);
    @Update("update growth_account set total_points=#{points},level_code=#{levelCode},version=version+1,updated_at=#{now} where id=#{id} and version=#{version} and deleted=0")
    int updateAccount(@Param("id") Long id,@Param("points") int points,@Param("levelCode") String levelCode,@Param("version") int version,@Param("now") LocalDateTime now);
    @Insert("insert into growth_log(id,user_id,biz_type,biz_id,point_delta,balance_after,remark,created_at,updated_at,deleted) values(#{id},#{userId},#{bizType},#{bizId},#{delta},#{balance},#{remark},#{now},#{now},0)")
    int insertLog(@Param("id") Long id,@Param("userId") Long userId,@Param("bizType") String bizType,@Param("bizId") String bizId,@Param("delta") int delta,@Param("balance") int balance,@Param("remark") String remark,@Param("now") LocalDateTime now);
    @Select("select id,biz_type bizType,biz_id bizId,point_delta pointDelta,balance_after balanceAfter,remark,created_at createdAt from growth_log where user_id=#{userId} and deleted=0 order by created_at desc limit #{offset},#{size}")
    List<Map<String,Object>> findLogs(@Param("userId") Long userId,@Param("offset") int offset,@Param("size") int size);
    @Select("select count(*) from growth_log where user_id=#{userId} and deleted=0") long countLogs(@Param("userId") Long userId);
    @Select("select count(*) from growth_log where user_id=#{userId} and biz_type=#{bizType} and deleted=0") int countEvents(@Param("userId") Long userId,@Param("bizType") String bizType);
    @Select("select id from growth_badge where enabled_flag=1 and deleted=0 and json_unquote(json_extract(condition_json,'$.eventType'))=#{bizType} and cast(json_unquote(json_extract(condition_json,'$.threshold')) as unsigned)<=#{count}")
    List<Long> findEligibleBadges(@Param("bizType") String bizType,@Param("count") int count);
    @Insert("insert ignore into growth_user_badge(id,user_id,badge_id,source_biz_id,awarded_at,created_at,updated_at,deleted) values(#{id},#{userId},#{badgeId},#{bizId},#{now},#{now},#{now},0)")
    int insertUserBadge(@Param("id") Long id,@Param("userId") Long userId,@Param("badgeId") Long badgeId,@Param("bizId") String bizId,@Param("now") LocalDateTime now);
    @Select("select b.id badgeId,b.badge_code badgeCode,b.badge_name badgeName,b.badge_image_key badgeImageKey,ub.awarded_at awardedAt from growth_badge b join growth_user_badge ub on ub.badge_id=b.id and ub.user_id=#{userId} and ub.deleted=0 where b.enabled_flag=1 and b.deleted=0 order by ub.awarded_at desc")
    List<Map<String,Object>> findEarnedBadges(@Param("userId") Long userId);
    @Select("select b.id badgeId,b.badge_code badgeCode,b.badge_name badgeName,b.badge_image_key badgeImageKey,null awardedAt from growth_badge b where b.enabled_flag=1 and b.deleted=0 and not exists(select 1 from growth_user_badge ub where ub.user_id=#{userId} and ub.badge_id=b.id and ub.deleted=0) order by b.id")
    List<Map<String,Object>> findLockedBadges(@Param("userId") Long userId);
}
