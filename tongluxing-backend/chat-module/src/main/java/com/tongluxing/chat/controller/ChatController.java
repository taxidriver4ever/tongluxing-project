package com.tongluxing.chat.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.chat.dto.AddConversationMemberRequest;
import com.tongluxing.chat.dto.SendMessageRequest;
import com.tongluxing.chat.dto.TeamConversationRequest;
import com.tongluxing.chat.dto.ConversationSettingRequest;
import com.tongluxing.chat.dto.JoinApplicationRequest;
import com.tongluxing.chat.dto.JoinApplicationReviewRequest;
import com.tongluxing.chat.service.ChatService;
import com.tongluxing.chat.service.TencentImService;
import com.tongluxing.chat.vo.ConversationListResponse;
import com.tongluxing.chat.vo.ConversationMemberResponse;
import com.tongluxing.chat.vo.ConversationResponse;
import com.tongluxing.chat.vo.ImUserSigResponse;
import com.tongluxing.chat.vo.MessageListResponse;
import com.tongluxing.chat.vo.MessageResponse;
import com.tongluxing.chat.vo.ConversationSettingResponse;
import com.tongluxing.chat.vo.JoinApplicationResponse;
import java.util.List;
import com.tongluxing.common.result.Result;

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

    /** 查询当前成员可访问的行程群聊；群聊由开启行程动作自动创建。 */
    @GetMapping("/trips/{tripId}/conversation")
    public Result<ConversationResponse> getTripConversation(@PathVariable Long tripId) {
        return Result.success(chatService.getTripConversation(tripId));
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

    @GetMapping("/conversations/{conversationId}/members")
    public Result<List<ConversationMemberResponse>> members(@PathVariable Long conversationId) {
        return Result.success(chatService.getMembers(conversationId));
    }

    @GetMapping("/conversations/{conversationId}/settings")
    public Result<ConversationSettingResponse> settings(@PathVariable Long conversationId) {
        return Result.success(chatService.getSettings(conversationId));
    }

    @PutMapping("/conversations/{conversationId}/settings")
    public Result<ConversationSettingResponse> updateSettings(@PathVariable Long conversationId,
            @Valid @RequestBody ConversationSettingRequest request) {
        return Result.success(chatService.updateSettings(conversationId, request.muted(), request.pinned()));
    }

    @PostMapping("/conversations/{conversationId}/join-applications")
    public Result<JoinApplicationResponse> applyToJoin(@PathVariable Long conversationId,
            @Valid @RequestBody JoinApplicationRequest request) {
        return Result.success(chatService.applyToJoin(conversationId, request.message()));
    }

    @GetMapping("/join-applications")
    public Result<List<JoinApplicationResponse>> joinApplications(
            @RequestParam(defaultValue = "PENDING") String status) {
        return Result.success(chatService.getJoinApplications(status));
    }

    @PostMapping("/join-applications/{applicationId}/review")
    public Result<JoinApplicationResponse> review(@PathVariable Long applicationId,
            @Valid @RequestBody JoinApplicationReviewRequest request) {
        return Result.success(chatService.reviewJoinApplication(applicationId, request.decision()));
    }
}
