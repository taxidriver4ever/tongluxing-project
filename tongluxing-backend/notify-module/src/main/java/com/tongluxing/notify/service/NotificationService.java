package com.tongluxing.notify.service;

import com.tongluxing.notify.dto.CreateNotificationEventRequest;
import com.tongluxing.notify.vo.NotificationVO;
import com.tongluxing.notify.vo.PageResult;
import com.tongluxing.notify.vo.UnreadCountVO;

/**
 * 通知业务服务。
 */
public interface NotificationService {

    /**
     * 内部业务事件创建通知。
     */
    NotificationVO createEvent(CreateNotificationEventRequest request);

    /**
     * 查询当前用户通知列表。
     */
    PageResult<NotificationVO> listCurrentUser(String scene, Boolean unreadOnly, int page, int size);

    /**
     * 查询当前用户未读数量。
     */
    UnreadCountVO countCurrentUserUnread();

    /**
     * 标记当前用户的单条通知已读。
     */
    void markCurrentUserRead(Long notificationId);

    /**
     * 标记当前用户全部通知已读。
     */
    void markCurrentUserAllRead();
}
