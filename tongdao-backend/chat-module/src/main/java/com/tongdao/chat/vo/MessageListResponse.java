package com.tongdao.chat.vo;

import java.util.List;

public record MessageListResponse(
        List<MessageResponse> messages
) {
}
