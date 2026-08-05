package com.tongluxing.notify.service;

import com.tongluxing.notify.entity.AppPushDevice;
import com.tongluxing.notify.entity.AppPushTask;

/** 厂商推送适配器；业务层不绑定具体 FCM/华为/小米 SDK。 */
public interface PushGateway {
    void send(AppPushDevice device, AppPushTask task);
}
