package com.tongluxing.notify.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.notify.dto.CreateNotificationEventRequest;
import com.tongluxing.notify.service.NotificationService;
import com.tongluxing.notify.vo.NotificationVO;
import com.tongluxing.notify.vo.PageResult;
import com.tongluxing.notify.vo.UnreadCountVO;

import lombok.RequiredArgsConstructor;

/**
 * 站内通知接口。
 */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 查询当前用户通知列表。
     */
    @GetMapping("/v1/notifications/me")
    public Result<PageResult<NotificationVO>> myNotifications(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(notificationService.listCurrentUser(scene, unreadOnly, page, size));
    }

    /**
     * 查询当前用户未读通知数量。
     */
    @GetMapping("/v1/notifications/me/unread-count")
    public Result<UnreadCountVO> unreadCount() {
        return Result.success(notificationService.countCurrentUserUnread());
    }

    /**
     * 标记单条通知已读。
     */
    @PostMapping("/v1/notifications/{notificationId}/read")
    public Result<Void> markRead(@PathVariable Long notificationId) {
        notificationService.markCurrentUserRead(notificationId);
        return Result.success();
    }

    /**
     * 标记当前用户全部通知已读。
     */
    @PostMapping("/v1/notifications/read-all")
    public Result<Void> markAllRead() {
        notificationService.markCurrentUserAllRead();
        return Result.success();
    }

    /**
     * 内部接口：业务事件创建通知。
     */
    @PostMapping("/internal/v1/notifications/events")
    public Result<NotificationVO> createEvent(@Valid @RequestBody CreateNotificationEventRequest request) {
        return Result.success(notificationService.createEvent(request));
    }
}
