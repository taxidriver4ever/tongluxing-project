package com.tongluxing.chat.controller;

import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.chat.dto.AddConversationMemberRequest;
import com.tongluxing.chat.dto.RenameConversationRequest;
import com.tongluxing.chat.dto.TeamConversationRequest;
import com.tongluxing.chat.group.ChatGroupService;
import com.tongluxing.chat.service.ChatService;
import com.tongluxing.chat.vo.ConversationMemberResponse;
import com.tongluxing.chat.vo.ConversationResponse;
import com.tongluxing.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * App 使用的群业务管理接口。
 *
 * <p>腾讯 IM SDK 负责聊天消息、会话列表、群资料展示、成员列表、置顶和免打扰；
 * 本控制器只处理必须经过同路行后端校验的业务操作，包括建群、拉人、踢人、
 * 改名、退群以及解散群并结束关联行程。</p>
 *
 * <p>旧的 /v1/chats/conversations/{id}/group 接口暂时保留给小程序兼容，
 * Flutter App 统一调用本控制器下的清晰资源路径。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/chats/groups")
public class ChatGroupManagementController {

    private final ChatService chatService;
    private final ChatGroupService chatGroupService;

    /**
     * 队长创建车队群。
     *
     * <p>群主从当前登录用户获取，客户端不能传 ownerUserId。服务层会校验当前用户
     * 确实是车队队长，并同时创建本地业务绑定与腾讯 IM 群。</p>
     */
    @PostMapping
    public Result<ConversationResponse> create(@Valid @RequestBody TeamConversationRequest request) {
        return Result.success(chatService.createTeamConversation(request));
    }

    /**
     * 队长修改群名称，并同步腾讯 IM 群资料。
     */
    @PatchMapping("/{conversationId}")
    public Result<Map<String, Object>> rename(
            @PathVariable Long conversationId,
            @Valid @RequestBody RenameConversationRequest request) {
        return Result.success(chatGroupService.rename(conversationId, request.name()));
    }

    /**
     * 邀请用户加入群聊。
     *
     * <p>任何处于 ACTIVE 状态的群成员都可以邀请；服务层仍会再次校验操作者身份、
     * 目标用户状态和重复成员，并同步腾讯 IM 群成员。</p>
     */
    @PostMapping("/{conversationId}/members")
    public Result<ConversationMemberResponse> addMember(
            @PathVariable Long conversationId,
            @Valid @RequestBody AddConversationMemberRequest request) {
        return Result.success(chatService.addMember(conversationId, request.userId()));
    }

    /**
     * 当前普通成员主动退出群聊。
     *
     * <p>队长不能直接退出，必须先解散群聊；退出操作会同步业务成员关系和腾讯 IM。</p>
     */
    @DeleteMapping("/{conversationId}/members/me")
    public Result<Void> exit(@PathVariable Long conversationId) {
        chatService.exitMe(conversationId);
        return Result.success();
    }

    /**
     * 队长将指定用户移出群聊，并同步本地成员状态与腾讯 IM 群成员关系。
     */
    @DeleteMapping("/{conversationId}/members/{userId}")
    public Result<Void> removeMember(
            @PathVariable Long conversationId,
            @PathVariable Long userId) {
        chatGroupService.remove(conversationId, userId);
        return Result.success();
    }

    /**
     * 队长解散群聊并终止关联行程。
     *
     * <p>进行中的行程执行结束；尚未开始的行程执行取消。随后归档本地会话、
     * 移出成员并销毁腾讯 IM 群，保证聊天状态和行程状态保持一致。</p>
     */
    @PostMapping("/{conversationId}/dissolve")
    public Result<Void> dissolve(@PathVariable Long conversationId) {
        chatGroupService.close(conversationId);
        return Result.success();
    }
}
