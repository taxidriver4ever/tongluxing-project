package com.tongdao.growth.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.tongdao.growth.dto.GrowthQueryDTO;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 成长模块数据库访问接口。
 *
 * <p>这里使用 MyBatis 注解 SQL 管理成长账户、成长流水、等级规则和徽章授予数据。
 * Service 层负责业务编排，Mapper 只表达清晰的数据读写动作。</p>
 */
@Mapper
public interface GrowthMapper {

    /**
     * 查询用户成长账户。
     */
    @Select("""
            select id,
                   total_points totalPoints,
                   level_code levelCode,
                   version
            from growth_account
            where user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    GrowthQueryDTO findAccount(@Param("userId") Long userId);

    /**
     * 查询用户成长账户并加行锁。
     *
     * <p>发放成长值时需要先锁定账户，避免并发请求同时更新导致成长值覆盖。</p>
     */
    @Select("""
            select id,
                   total_points totalPoints,
                   level_code levelCode,
                   version
            from growth_account
            where user_id = #{userId}
              and deleted = 0
            limit 1 for update
            """)
    GrowthQueryDTO findAccountForUpdate(@Param("userId") Long userId);

    /**
     * 初始化用户成长账户，默认成长值为 0、等级为 LV1。
     */
    @Insert("""
            insert into growth_account(
                id, user_id, total_points, level_code, version,
                created_at, updated_at, deleted
            )
            values (
                #{id}, #{userId}, 0, 'LV1', 0,
                #{now}, #{now}, 0
            )
            """)
    int insertAccount(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 根据成长值查询当前匹配的等级编码。
     */
    @Select("""
            select level_code
            from growth_level_rule
            where enabled_flag = 1
              and deleted = 0
              and min_points <= #{points}
              and (max_points is null or max_points >= #{points})
            order by min_points desc
            limit 1
            """)
    String findLevelCode(@Param("points") int points);

    /**
     * 查询下一等级的起始成长值，用于计算距离升级还差多少成长值。
     */
    @Select("""
            select min_points
            from growth_level_rule
            where enabled_flag = 1
              and deleted = 0
              and min_points > #{points}
            order by min_points
            limit 1
            """)
    Integer findNextLevelPoints(@Param("points") int points);

    /**
     * 更新成长账户余额和等级。
     *
     * <p>通过 version 字段做乐观锁校验，防止并发更新覆盖。</p>
     */
    @Update("""
            update growth_account
            set total_points = #{points},
                level_code = #{levelCode},
                version = version + 1,
                updated_at = #{now}
            where id = #{id}
              and version = #{version}
              and deleted = 0
            """)
    int updateAccount(@Param("id") Long id, @Param("points") int points,
                      @Param("levelCode") String levelCode, @Param("version") int version,
                      @Param("now") LocalDateTime now);

    /**
     * 写入成长值变更流水。
     *
     * <p>数据库唯一索引会保证同一业务事件不会重复写入。</p>
     */
    @Insert("""
            insert into growth_log(
                id, user_id, biz_type, biz_id, point_delta,
                balance_after, remark, created_at, updated_at, deleted
            )
            values (
                #{id}, #{userId}, #{bizType}, #{bizId}, #{delta},
                #{balance}, #{remark}, #{now}, #{now}, 0
            )
            """)
    int insertLog(@Param("id") Long id, @Param("userId") Long userId,
                  @Param("bizType") String bizType, @Param("bizId") String bizId,
                  @Param("delta") int delta, @Param("balance") int balance,
                  @Param("remark") String remark, @Param("now") LocalDateTime now);

    /**
     * 分页查询用户成长值流水。
     */
    @Select("""
            select id,
                   biz_type bizType,
                   biz_id bizId,
                   point_delta pointDelta,
                   balance_after balanceAfter,
                   remark,
                   created_at createdAt
            from growth_log
            where user_id = #{userId}
              and deleted = 0
            order by created_at desc
            limit #{offset}, #{size}
            """)
    List<GrowthQueryDTO> findLogs(@Param("userId") Long userId,
                                  @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计用户成长值流水数量。
     */
    @Select("""
            select count(*)
            from growth_log
            where user_id = #{userId}
              and deleted = 0
            """)
    long countLogs(@Param("userId") Long userId);

    /**
     * 统计用户某类业务事件已发生次数，用于判断徽章达成条件。
     */
    @Select("""
            select count(*)
            from growth_log
            where user_id = #{userId}
              and biz_type = #{bizType}
              and deleted = 0
            """)
    int countEvents(@Param("userId") Long userId, @Param("bizType") String bizType);

    /**
     * 查询当前事件次数已经满足条件的徽章。
     */
    @Select("""
            select id
            from growth_badge
            where enabled_flag = 1
              and deleted = 0
              and event_type = #{bizType}
              and threshold <= #{count}
            """)
    List<Long> findEligibleBadges(@Param("bizType") String bizType, @Param("count") int count);

    /**
     * 给用户授予徽章。
     *
     * <p>使用 insert ignore 配合唯一索引，保证同一用户同一徽章只授予一次。</p>
     */
    @Insert("""
            insert ignore into growth_user_badge(
                id, user_id, badge_id, source_biz_id,
                awarded_at, created_at, updated_at, deleted
            )
            values (
                #{id}, #{userId}, #{badgeId}, #{bizId},
                #{now}, #{now}, #{now}, 0
            )
            """)
    int insertUserBadge(@Param("id") Long id, @Param("userId") Long userId,
                        @Param("badgeId") Long badgeId, @Param("bizId") String bizId,
                        @Param("now") LocalDateTime now);

    /**
     * 查询用户已经获得的徽章。
     */
    @Select("""
            select b.id badgeId,
                   b.badge_code badgeCode,
                   b.badge_name badgeName,
                   b.badge_image_key badgeImageKey,
                   ub.awarded_at awardedAt
            from growth_badge b
            join growth_user_badge ub
              on ub.badge_id = b.id
             and ub.user_id = #{userId}
             and ub.deleted = 0
            where b.enabled_flag = 1
              and b.deleted = 0
            order by ub.awarded_at desc
            """)
    List<GrowthQueryDTO> findEarnedBadges(@Param("userId") Long userId);

    /**
     * 查询用户尚未获得但仍启用展示的徽章。
     */
    @Select("""
            select b.id badgeId,
                   b.badge_code badgeCode,
                   b.badge_name badgeName,
                   b.badge_image_key badgeImageKey,
                   null awardedAt
            from growth_badge b
            where b.enabled_flag = 1
              and b.deleted = 0
              and not exists (
                  select 1
                  from growth_user_badge ub
                  where ub.user_id = #{userId}
                    and ub.badge_id = b.id
                    and ub.deleted = 0
              )
            order by b.id
            """)
    List<GrowthQueryDTO> findLockedBadges(@Param("userId") Long userId);
}
