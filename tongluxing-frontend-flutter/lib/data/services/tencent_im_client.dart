import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:tencent_cloud_chat_sdk/enum/V2TimAdvancedMsgListener.dart';
import 'package:tencent_cloud_chat_sdk/enum/V2TimSDKListener.dart';
import 'package:tencent_cloud_chat_sdk/enum/log_level_enum.dart';
import 'package:tencent_cloud_chat_sdk/tencent_im_sdk_plugin.dart';

import 'api_client.dart';

/// 腾讯 IM 只承担实时连接和到达通知。
///
/// 消息发送仍先经过后端完成权限、风控、腾讯 IM 投递与本地审计落库；
/// 收到 SDK 推送后页面重新拉取后端消息，确保业务历史只有一个事实来源。
class TencentImClient {
  TencentImClient(this.api);

  final ApiClient api;
  final StreamController<void> _messageEvents =
      StreamController<void>.broadcast();
  V2TimAdvancedMsgListener? _messageListener;
  bool _initialized = false;
  bool _connecting = false;
  int? _sdkAppId;
  String? _loginUserId;

  Stream<void> get messageEvents => _messageEvents.stream;
  bool get connected => _loginUserId != null;

  Future<void> connect() async {
    if (_connecting) return;
    _connecting = true;
    try {
      final raw = await api.get('/v1/chats/im/user-sig');
      final ticket = Map<String, dynamic>.from(raw as Map);
      final sdkAppId = (ticket['sdkAppId'] as num).toInt();
      final userId = ticket['userId']?.toString() ?? '';
      final userSig = ticket['userSig']?.toString() ?? '';
      if (sdkAppId <= 0 || userId.isEmpty || userSig.isEmpty) return;

      if (!_initialized || _sdkAppId != sdkAppId) {
        if (_initialized) {
          await TencentImSDKPlugin.v2TIMManager.unInitSDK();
        }
        final init = await TencentImSDKPlugin.v2TIMManager.initSDK(
          sdkAppID: sdkAppId,
          loglevel: kDebugMode
              ? LogLevelEnum.V2TIM_LOG_INFO
              : LogLevelEnum.V2TIM_LOG_ERROR,
          showImLog: kDebugMode,
          listener: V2TimSDKListener(
            onUserSigExpired: () => unawaited(connect()),
          ),
        );
        if (init.code != 0 || init.data != true) {
          throw StateError('腾讯 IM SDK 初始化失败：${init.desc}');
        }
        _initialized = true;
        _sdkAppId = sdkAppId;
        _messageListener = V2TimAdvancedMsgListener(
          onRecvNewMessage: (_) => _messageEvents.add(null),
          onRecvMessageModified: (_) => _messageEvents.add(null),
          onRecvMessageRevoked: (_) => _messageEvents.add(null),
        );
        await TencentImSDKPlugin.v2TIMManager
            .getMessageManager()
            .addAdvancedMsgListener(listener: _messageListener!);
      }

      if (_loginUserId != null && _loginUserId != userId) {
        await TencentImSDKPlugin.v2TIMManager.logout();
        _loginUserId = null;
      }
      if (_loginUserId == userId) return;
      final login = await TencentImSDKPlugin.v2TIMManager.login(
        userID: userId,
        userSig: userSig,
      );
      if (login.code != 0) {
        throw StateError('腾讯 IM 登录失败：${login.code} ${login.desc}');
      }
      _loginUserId = userId;
    } finally {
      _connecting = false;
    }
  }

  Future<void> disconnect() async {
    if (_initialized && _loginUserId != null) {
      await TencentImSDKPlugin.v2TIMManager.logout();
    }
    _loginUserId = null;
  }

  Future<void> dispose() async {
    await disconnect();
    if (_initialized) {
      if (_messageListener != null) {
        await TencentImSDKPlugin.v2TIMManager
            .getMessageManager()
            .removeAdvancedMsgListener(listener: _messageListener);
      }
      await TencentImSDKPlugin.v2TIMManager.unInitSDK();
    }
    _initialized = false;
    _messageListener = null;
    await _messageEvents.close();
  }
}
