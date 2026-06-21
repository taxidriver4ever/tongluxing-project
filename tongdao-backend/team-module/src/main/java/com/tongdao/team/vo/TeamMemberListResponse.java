package com.tongdao.team.vo;

import java.util.List;

public record TeamMemberListResponse(
        List<TeamMemberResponse> members
) {
}
