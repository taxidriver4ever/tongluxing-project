package com.tongluxing.chat.media;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import lombok.RequiredArgsConstructor;

/** 聊天媒体访问接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/chats/media")
public class ChatMediaController {

    private final ChatMediaService chatMediaService;

    /**
     * 获取聊天图片或附件的短期 MinIO 访问地址。
     *
     * <p>腾讯 IM 消息中只保存 fileId；本接口会验证当前用户仍属于文件绑定的会话。</p>
     */
    @GetMapping("/{fileId}/access-url")
    public Result<ChatMediaAccessResponse> accessUrl(@PathVariable Long fileId) {
        return Result.success(chatMediaService.accessUrl(fileId));
    }
}
