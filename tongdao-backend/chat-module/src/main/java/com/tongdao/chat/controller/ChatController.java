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

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/chats")
public class ChatController {

    private final ChatService chatService;
    private final TencentImService tencentImService;

    @GetMapping("/im/user-sig")
    public Result<ImUserSigResponse> getImUserSig() {
        return Result.success(tencentImService.generateCurrentUserSig());
    }

    @PostMapping("/team-conversations")
    public Result<ConversationResponse> createTeamConversation(@Valid @RequestBody TeamConversationRequest request) {
        return Result.success(chatService.createTeamConversation(request));
    }

    @GetMapping("/conversations")
    public Result<ConversationListResponse> getConversations() {
        return Result.success(chatService.getConversations());
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Result<MessageListResponse> getMessages(@PathVariable Long conversationId,
                                                   @RequestParam(required = false) Long beforeMessageId,
                                                   @RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(chatService.getMessages(conversationId, beforeMessageId, limit));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public Result<MessageResponse> sendMessage(@PathVariable Long conversationId,
                                               @Valid @RequestBody SendMessageRequest request) {
        return Result.success(chatService.sendMessage(conversationId, request));
    }

    @PostMapping("/conversations/{conversationId}/members")
    public Result<ConversationMemberResponse> addMember(@PathVariable Long conversationId,
                                                        @Valid @RequestBody AddConversationMemberRequest request) {
        return Result.success(chatService.addMember(conversationId, request.userId()));
    }

    @DeleteMapping("/conversations/{conversationId}/members/me")
    public Result<Void> exitMe(@PathVariable Long conversationId) {
        chatService.exitMe(conversationId);
        return Result.success();
    }
}
