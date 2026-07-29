package com.tongluxing.user.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 关注或粉丝列表的数据库查询结果。
 *
 * <p>Mapper 会把关注关系表中的关系时间，与用户资料、最新驾驶证认证状态和累计行程
 * 统计拼装到同一个对象中。Service 随后再补充“我是否关注对方、对方是否关注我”
 * 等与当前登录用户有关的状态，并转换为公开的 {@code FollowUserVO}。</p>
 */
@Data
public class UserFollowQueryDTO {
    /** 列表中目标用户的业务用户 ID，而不是关注关系记录的主键。 */
    private Long userId;

    /** 目标用户昵称；SQL 在昵称为空时回退为“同路行用户”。 */
    private String nickname;

    /** 目标用户头像在对象存储中的资源 Key，由调用端或资源服务转换为访问地址。 */
    private String avatarImageKey;

    /** 目标用户最新驾驶证认证状态；没有申请记录时由 SQL 归一为 UNSUBMITTED。 */
    private String certificationStatus;

    /** 目标用户累计行程数；没有统计行时由 SQL 归零。 */
    private Integer totalTripCount;

    /** 目标用户累计行驶距离，单位为米；没有统计行时由 SQL 归零。 */
    private Long totalDistanceMeters;

    /**
     * 关注关系建立时间。
     *
     * <p>在粉丝列表中表示对方关注当前用户的时间；在关注列表与互关列表中表示当前
     * 用户关注对方的时间。</p>
     */
    private LocalDateTime followedAt;
}
