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
import com.tongluxing.chat.vo.PrivateChatPermissionResponse;
import java.util.List;
import com.tongluxing.common.result.Result;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

    /**
     * 获取当前登录用户的腾讯 IM SDK 登录会话。
     *
     * <p>App 使用该接口获得 sdkAppId、userId、userSig 和过期时间，随后直接登录腾讯 IM SDK。</p>
     */
    @GetMapping("/im/session")
    public Result<ImUserSigResponse> getImSession() {
        return Result.success(tencentImService.generateCurrentUserSig());
    }

    /** 兼容旧 App 的 UserSig 地址；新代码统一使用 /im/session。 */
    @Deprecated
    @GetMapping("/im/user-sig")
    public Result<ImUserSigResponse> getImUserSig() {
        return getImSession();
    }

    /**
     * 获取腾讯 IM 会话 ID 与同路行业务会话的绑定。
     *
     * <p>该接口不返回未读、置顶、免打扰或消息历史，这些信息全部以腾讯 IM SDK 为准。</p>
     */
    @GetMapping("/im/bindings")
    public Result<ConversationListResponse> getImBindings() {
        return Result.success(chatService.getImConversationBindings());
    }

    /** 创建或获取车队关联的群聊会话。 */
    @PostMapping("/team-conversations")
    public Result<ConversationResponse> createTeamConversation(@Valid @RequestBody TeamConversationRequest request) {
        return Result.success(chatService.createTeamConversation(request));
    }

    /** 兼容旧客户端：查询本地会话摘要；新 App 使用腾讯 IM SDK 会话列表。 */
    @Deprecated
    @GetMapping("/conversations")
    public Result<ConversationListResponse> getConversations(
            @RequestParam(required = false) @Size(max = 64) String title) {
        return Result.success(chatService.getConversations(title));
    }

    /** 查询当前用户是否可以与目标用户发起私聊。 */
    @GetMapping("/private/permission/{userId}")
    public Result<PrivateChatPermissionResponse> privatePermission(@PathVariable Long userId) {
        return Result.success(chatService.getPrivatePermission(userId));
    }

    /** 创建或返回当前用户与目标用户的唯一私聊会话。 */
    @PostMapping("/private/{userId}/start")
    public Result<ConversationResponse> startPrivate(@PathVariable Long userId) {
        return Result.success(chatService.startPrivateConversation(userId));
    }

    /** 查询当前用户在指定私聊会话中的实时发送权限。 */
    @GetMapping("/conversations/{conversationId}/private-permission")
    public Result<PrivateChatPermissionResponse> privateConversationPermission(
            @PathVariable Long conversationId) {
        return Result.success(chatService.getPrivateConversationPermission(conversationId));
    }

    /** 查询当前成员可访问的行程群聊；群聊由开启行程动作自动创建。 */
    @GetMapping("/trips/{tripId}/conversation")
    public Result<ConversationResponse> getTripConversation(@PathVariable Long tripId) {
        return Result.success(chatService.getTripConversation(tripId));
    }

    /** 兼容旧客户端：查询本地审计消息；新 App 使用腾讯 IM SDK 历史消息。 */
    @Deprecated
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<MessageListResponse> getMessages(@PathVariable Long conversationId,
                                                   @RequestParam(required = false) Long beforeMessageId,
                                                   @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(chatService.getMessages(conversationId, beforeMessageId, limit));
    }

    /** 从当前用户消息列表隐藏会话；聊天记录保留，新消息到达后自动重新出现。 */
    @DeleteMapping("/conversations/{conversationId}/visibility/me")
    public Result<Void> hideConversation(@PathVariable Long conversationId) {
        chatService.hideConversation(conversationId);
        return Result.success();
    }

    /** 兼容旧客户端。语义已调整为隐藏会话，不再清除历史消息。 */
    @DeleteMapping("/conversations/{conversationId}/messages/me")
    public Result<Void> clearLocalMessages(@PathVariable Long conversationId) {
        chatService.hideConversation(conversationId);
        return Result.success();
    }

    /** 兼容旧客户端：后端代发消息；新 App 直接通过腾讯 IM SDK 发送。 */
    @Deprecated
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

    /** 查询指定会话的有效成员及公开资料摘要。 */
    @GetMapping("/conversations/{conversationId}/members")
    public Result<List<ConversationMemberResponse>> members(@PathVariable Long conversationId) {
        return Result.success(chatService.getMembers(conversationId));
    }

    /** 兼容旧客户端：本地设置；新 App 的置顶和免打扰由腾讯 IM SDK 托管。 */
    @Deprecated
    @GetMapping("/conversations/{conversationId}/settings")
    public Result<ConversationSettingResponse> settings(@PathVariable Long conversationId) {
        return Result.success(chatService.getSettings(conversationId));
    }

    /** 兼容旧客户端：本地设置；新 App 直接调用腾讯 IM SDK。 */
    @Deprecated
    @PutMapping("/conversations/{conversationId}/settings")
    public Result<ConversationSettingResponse> updateSettings(@PathVariable Long conversationId,
            @Valid @RequestBody ConversationSettingRequest request) {
        return Result.success(chatService.updateSettings(conversationId, request.muted(), request.pinned()));
    }

    /** 当前用户向指定群聊提交入群申请。 */
    @PostMapping("/conversations/{conversationId}/join-applications")
    public Result<JoinApplicationResponse> applyToJoin(@PathVariable Long conversationId,
            @Valid @RequestBody JoinApplicationRequest request) {
        return Result.success(chatService.applyToJoin(conversationId, request.message()));
    }

    /** 查询当前用户作为群主有权处理的入群申请。 */
    @GetMapping("/join-applications")
    public Result<List<JoinApplicationResponse>> joinApplications(
            @RequestParam(defaultValue = "PENDING") String status) {
        return Result.success(chatService.getJoinApplications(status));
    }

    /** 群主通过或拒绝一条尚未处理的入群申请。 */
    @PostMapping("/join-applications/{applicationId}/review")
    public Result<JoinApplicationResponse> review(@PathVariable Long applicationId,
            @Valid @RequestBody JoinApplicationReviewRequest request) {
        return Result.success(chatService.reviewJoinApplication(applicationId, request.decision()));
    }
}
