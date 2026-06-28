package com.tongdao.chat.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.chat.dto.AddConversationMemberRequest;
import com.tongdao.chat.dto.SendMessageRequest;
import com.tongdao.chat.dto.TeamConversationRequest;
import com.tongdao.chat.service.ChatService;
import com.tongdao.chat.service.TencentImService;
import com.tongdao.chat.vo.ConversationListResponse;
import com.tongdao.chat.vo.ConversationMemberResponse;
import com.tongdao.chat.vo.ConversationResponse;
import com.tongdao.chat.vo.ImUserSigResponse;
import com.tongdao.chat.vo.MessageListResponse;
import com.tongdao.chat.vo.MessageResponse;
import com.tongdao.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 聊天模块接口。
 *
 * <p>负责本地会话、成员、消息记录的管理，同时提供腾讯云 IM 登录票据获取入口。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/chats")
public class ChatController {

    /** 本地聊天业务服务。 */
    private final ChatService chatService;
    /** 腾讯云 IM 服务，用于生成 UserSig 和同步群组成员。 */
    private final TencentImService tencentImService;

    /** 获取当前登录用户的腾讯云 IM UserSig。 */
    @GetMapping("/im/user-sig")
    public Result<ImUserSigResponse> getImUserSig() {
        return Result.success(tencentImService.generateCurrentUserSig());
    }

    /** 创建或获取车队关联的群聊会话。 */
    @PostMapping("/team-conversations")
    public Result<ConversationResponse> createTeamConversation(@Valid @RequestBody TeamConversationRequest request) {
        return Result.success(chatService.createTeamConversation(request));
    }

    /** 查询当前登录用户参与的会话列表。 */
    @GetMapping("/conversations")
    public Result<ConversationListResponse> getConversations() {
        return Result.success(chatService.getConversations());
    }

    /** 分页查询指定会话的历史消息。 */
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<MessageListResponse> getMessages(@PathVariable Long conversationId,
                                                   @RequestParam(required = false) Long beforeMessageId,
                                                   @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(chatService.getMessages(conversationId, beforeMessageId, limit));
    }

    /** 向指定会话发送消息。 */
    @PostMapping("/conversations/{conversationId}/messages")
    public Result<MessageResponse> sendMessage(@PathVariable Long conversationId,
                                               @Valid @RequestBody SendMessageRequest request) {
        return Result.success(chatService.sendMessage(conversationId, request));
    }

    /** 向指定会话添加成员。 */
    @PostMapping("/conversations/{conversationId}/members")
    public Result<ConversationMemberResponse> addMember(@PathVariable Long conversationId,
                                                        @Valid @RequestBody AddConversationMemberRequest request) {
        return Result.success(chatService.addMember(conversationId, request.userId()));
    }

    /** 当前登录用户退出指定会话。 */
    @DeleteMapping("/conversations/{conversationId}/members/me")
    public Result<Void> exitMe(@PathVariable Long conversationId) {
        chatService.exitMe(conversationId);
        return Result.success();
    }
}
