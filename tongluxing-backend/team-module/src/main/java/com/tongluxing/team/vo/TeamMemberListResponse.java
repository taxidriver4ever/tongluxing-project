package com.tongluxing.team.vo;

import java.util.List;

/**
 * TeamMemberListResponse 响应数据对象。
 */
public record TeamMemberListResponse(
        List<TeamMemberResponse> members
) {
}
