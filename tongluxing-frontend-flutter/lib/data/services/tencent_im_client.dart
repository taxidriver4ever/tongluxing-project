import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:tencent_cloud_chat_sdk/enum/V2TimAdvancedMsgListener.dart';
import 'package:tencent_cloud_chat_sdk/enum/V2TimConversationListener.dart';
import 'package:tencent_cloud_chat_sdk/enum/V2TimSDKListener.dart';
import 'package:tencent_cloud_chat_sdk/enum/group_member_filter_enum.dart';
import 'package:tencent_cloud_chat_sdk/enum/log_level_enum.dart';
import 'package:tencent_cloud_chat_sdk/enum/message_elem_type.dart';
import 'package:tencent_cloud_chat_sdk/enum/receive_message_opt_enum.dart';
import 'package:tencent_cloud_chat_sdk/tencent_im_sdk_plugin.dart';

import '../models/app_models.dart';
import 'api_client.dart';

/// Flutter 端腾讯 IM 适配器。
///
/// 职责边界：
/// 1. 腾讯 IM SDK 负责会话列表、最后消息、未读数、历史消息、实时消息、置顶和免打扰；
/// 2. 后端只返回同路行的业务绑定，例如本地 conversationId、tripId、teamId 和权限信息；
/// 3. 图片本体上传到 MinIO，自定义消息中只发送 fileId/mediaId 与必要元数据；
/// 4. 腾讯 IM 的发送后回调负责把消息保存为后端审计副本。
class TencentImClient {
  TencentImClient(this.api);

  final ApiClient api;
  final StreamController<void> _messageEvents =
      StreamController<void>.broadcast();
  final StreamController<void> _conversationEvents =
      StreamController<void>.broadcast();

  V2TimAdvancedMsgListener? _messageListener;
  V2TimConversationListener? _conversationListener;
  bool _initialized = false;
  Future<void>? _connectTask;
  int? _sdkAppId;
  String? _loginUserId;

  Stream<void> get messageEvents => _messageEvents.stream;
  Stream<void> get conversationEvents => _conversationEvents.stream;
  bool get connected => _loginUserId != null;

  /// 初始化并登录腾讯 IM。重复调用会复用同一个连接任务，避免页面并发登录。
  Future<void> connect() {
    final running = _connectTask;
    if (running != null) return running;

    final task = _connectInternal();
    _connectTask = task;
    return task.whenComplete(() {
      // 只有当前任务完成时才清空，防止后续新任务被旧任务误清理。
      if (identical(_connectTask, task)) _connectTask = null;
    });
  }

  Future<void> _connectInternal() async {
    // 登录票据必须由后端签发，SecretKey 永远不能放进 Flutter 包。
    final raw = await api.get('/v1/chats/im/session');
    final ticket = Map<String, dynamic>.from(raw as Map);
    final sdkAppId = (ticket['sdkAppId'] as num).toInt();
    final userId = ticket['userId']?.toString() ?? '';
    final userSig = ticket['userSig']?.toString() ?? '';
    if (sdkAppId <= 0 || userId.isEmpty || userSig.isEmpty) {
      throw StateError('腾讯 IM 登录票据不完整');
    }

    if (!_initialized || _sdkAppId != sdkAppId) {
      if (_initialized) await TencentImSDKPlugin.v2TIMManager.unInitSDK();
      final init = await TencentImSDKPlugin.v2TIMManager.initSDK(
        sdkAppID: sdkAppId,
        loglevel: kDebugMode
            ? LogLevelEnum.V2TIM_LOG_INFO
            : LogLevelEnum.V2TIM_LOG_ERROR,
        showImLog: kDebugMode,
        listener: V2TimSDKListener(
          onUserSigExpired: () => unawaited(_refreshLogin()),
          onKickedOffline: () {
            _loginUserId = null;
            _conversationEvents.add(null);
          },
        ),
      );
      if (init.code != 0 || init.data != true) {
        throw StateError('腾讯 IM SDK 初始化失败：${init.desc}');
      }
      _initialized = true;
      _sdkAppId = sdkAppId;
      await _installListeners();
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
    _conversationEvents.add(null);
  }

  /// UserSig 过期后先清理 SDK 登录态，再向后端申请新票据重新登录。
  Future<void> _refreshLogin() async {
    try {
      if (_initialized && _loginUserId != null) {
        await TencentImSDKPlugin.v2TIMManager.logout();
      }
    } finally {
      _loginUserId = null;
    }
    await connect();
  }

  /// 安装消息与会话监听。监听器只通知页面刷新，不在回调线程执行网络请求。
  Future<void> _installListeners() async {
    _messageListener = V2TimAdvancedMsgListener(
      onRecvNewMessage: (_) {
        _messageEvents.add(null);
        _conversationEvents.add(null);
      },
      onRecvMessageModified: (_) {
        _messageEvents.add(null);
        _conversationEvents.add(null);
      },
      onRecvMessageRevoked: (_) {
        _messageEvents.add(null);
        _conversationEvents.add(null);
      },
    );
    await TencentImSDKPlugin.v2TIMManager
        .getMessageManager()
        .addAdvancedMsgListener(listener: _messageListener!);

    _conversationListener = V2TimConversationListener(
      onSyncServerFinish: () => _conversationEvents.add(null),
      onNewConversation: (_) => _conversationEvents.add(null),
      onConversationChanged: (_) => _conversationEvents.add(null),
      onTotalUnreadMessageCountChanged: (_) =>
          _conversationEvents.add(null),
    );
    await TencentImSDKPlugin.v2TIMManager
        .getConversationManager()
        .addConversationListener(listener: _conversationListener!);
  }

  /// 获取腾讯 IM 会话，并与后端业务绑定合并。
  ///
  /// SDK 字段决定聊天展示状态；后端字段只补充行程/车队业务 ID 和权限。
  Future<List<ConversationModel>> conversations(
    List<ConversationModel> bindings,
  ) async {
    await _requireConnected();
    final manager = TencentImSDKPlugin.v2TIMManager.getConversationManager();
    final sdkRows = <Map<String, dynamic>>[];
    var nextSeq = '0';

    // 腾讯建议分页拉取；最多十页可避免异常序列导致死循环。
    for (var page = 0; page < 10; page++) {
      final result = await manager.getConversationList(
        nextSeq: nextSeq,
        count: 100,
      );
      if (result.code != 0) {
        throw StateError('获取腾讯 IM 会话失败：${result.code} ${result.desc}');
      }
      final dynamic data = result.data;
      final dynamic list = data?.conversationList;
      if (list is List) {
        for (final dynamic item in list) {
          if (item == null) continue;
          sdkRows.add(Map<String, dynamic>.from(item.toJson() as Map));
        }
      }
      if (data == null || data.isFinished == true) break;
      final candidate = data.nextSeq?.toString() ?? '';
      if (candidate.isEmpty || candidate == nextSeq) break;
      nextSeq = candidate;
    }

    final byImId = <String, ConversationModel>{
      for (final binding in bindings)
        if (binding.imConversationId.isNotEmpty)
          binding.imConversationId: binding,
    };
    final merged = <ConversationModel>[];
    for (final sdk in sdkRows) {
      final imId = sdk['conversationID']?.toString() ?? '';
      final binding = byImId.remove(imId);
      // 不展示没有同路行业务绑定的陌生会话，避免绕过业务权限直接产生会话。
      if (binding == null) continue;
      merged.add(_mergeConversation(binding, sdk));
    }

    // 新建但尚无消息的群可能暂未出现在 SDK 会话列表中，保留业务绑定作为兜底。
    merged.addAll(byImId.values);
    return merged;
  }

  /// 拉取单聊或群聊历史消息，返回 UI 已使用的统一 Map 结构。
  Future<List<Map<String, dynamic>>> messages(
    ConversationModel conversation, {
    int count = 100,
  }) async {
    await _requireConnected();
    final manager = TencentImSDKPlugin.v2TIMManager.getMessageManager();
    final result = conversation.isPrivate
        ? await manager.getC2CHistoryMessageList(
            userID: conversation.imPeerUserId,
            count: count,
            lastMsgID: null,
          )
        : await manager.getGroupHistoryMessageList(
            groupID: conversation.imGroupId,
            count: count,
            lastMsgID: null,
          );
    if (result.code != 0) {
      throw StateError('获取腾讯 IM 历史消息失败：${result.code} ${result.desc}');
    }

    final rows = <Map<String, dynamic>>[];
    final dynamic data = result.data;
    if (data is List) {
      for (final dynamic message in data) {
        if (message == null) continue;
        rows.add(_messageToMap(Map<String, dynamic>.from(message.toJson() as Map)));
      }
    }
    // SDK 默认从新到旧返回；聊天页面需要从旧到新排列。
    return rows.reversed.toList();
  }

  /// 通过腾讯 IM SDK 直接发送文本消息。
  Future<void> sendText(ConversationModel conversation, String text) async {
    final content = text.trim();
    if (content.isEmpty) return;
    await _requireConnected();
    final manager = TencentImSDKPlugin.v2TIMManager.getMessageManager();
    final created = await manager.createTextMessage(text: content);
    final dynamic createData = created.data;
    final id = createData?.id?.toString() ?? '';
    if (created.code != 0 || id.isEmpty) {
      throw StateError('创建腾讯 IM 文本消息失败：${created.desc}');
    }
    await _sendCreatedMessage(conversation, id);
  }

  /// 发送同路行自定义消息，例如 MinIO 图片、文件、位置和业务卡片。
  Future<void> sendCustom(
    ConversationModel conversation, {
    required Map<String, dynamic> payload,
    required String description,
    required String extension,
  }) async {
    await _requireConnected();
    final manager = TencentImSDKPlugin.v2TIMManager.getMessageManager();
    final created = await manager.createCustomMessage(
      data: jsonEncode(payload),
      desc: description,
      extension: extension,
    );
    final dynamic createData = created.data;
    final id = createData?.id?.toString() ?? '';
    if (created.code != 0 || id.isEmpty) {
      throw StateError('创建腾讯 IM 自定义消息失败：${created.desc}');
    }
    await _sendCreatedMessage(conversation, id);
  }

  /// 发送已经由 createTextMessage/createCustomMessage 创建的消息。
  Future<void> _sendCreatedMessage(
    ConversationModel conversation,
    String id,
  ) async {
    final result = await TencentImSDKPlugin.v2TIMManager
        .getMessageManager()
        .sendMessage(
          id: id,
          receiver: conversation.isPrivate ? conversation.imPeerUserId : '',
          groupID: conversation.isPrivate ? '' : conversation.imGroupId,
          onlineUserOnly: false,
          isExcludedFromUnreadCount: false,
          isExcludedFromLastMessage: false,
        );
    if (result.code != 0) {
      throw StateError('腾讯 IM 消息发送失败：${result.code} ${result.desc}');
    }
    _messageEvents.add(null);
    _conversationEvents.add(null);
  }

  /// 修改腾讯 IM 云端会话置顶状态。
  Future<void> setPinned(ConversationModel conversation, bool pinned) async {
    await _requireConnected();
    final result = await TencentImSDKPlugin.v2TIMManager
        .getConversationManager()
        .pinConversation(
          conversationID: conversation.imConversationId,
          isPinned: pinned,
        );
    if (result.code != 0) {
      throw StateError('修改会话置顶失败：${result.code} ${result.desc}');
    }
    _conversationEvents.add(null);
  }

  /// 修改腾讯 IM 云端免打扰状态；消息仍接收，只是不提醒。
  Future<void> setMuted(ConversationModel conversation, bool muted) async {
    await _requireConnected();
    final option = muted
        ? ReceiveMsgOptEnum.V2TIM_RECEIVE_NOT_NOTIFY_MESSAGE
        : ReceiveMsgOptEnum.V2TIM_RECEIVE_MESSAGE;
    final manager = TencentImSDKPlugin.v2TIMManager.getMessageManager();
    final result = conversation.isPrivate
        ? await manager.setC2CReceiveMessageOpt(
            userIDList: [conversation.imPeerUserId],
            opt: option,
          )
        : await manager.setGroupReceiveMessageOpt(
            groupID: conversation.imGroupId,
            opt: option,
          );
    if (result.code != 0) {
      throw StateError('修改消息免打扰失败：${result.code} ${result.desc}');
    }
    _conversationEvents.add(null);
  }

  /// 打开会话后清除腾讯 IM 未读数。
  Future<void> markRead(ConversationModel conversation) async {
    if (!connected) return;
    final manager = TencentImSDKPlugin.v2TIMManager.getMessageManager();
    if (conversation.isPrivate) {
      await manager.markC2CMessageAsRead(userID: conversation.imPeerUserId);
    } else {
      await manager.markGroupMessageAsRead(groupID: conversation.imGroupId);
    }
    _conversationEvents.add(null);
  }

  /// 仅从当前设备/账号的腾讯 IM 会话列表删除会话，不清空云端历史消息。
  Future<void> deleteConversation(ConversationModel conversation) async {
    await _requireConnected();
    final result = await TencentImSDKPlugin.v2TIMManager
        .getConversationManager()
        .deleteConversation(conversationID: conversation.imConversationId);
    if (result.code != 0) {
      throw StateError('删除腾讯 IM 会话失败：${result.code} ${result.desc}');
    }
    _conversationEvents.add(null);
  }

  /// 从腾讯 IM 获取群成员资料，供群信息页展示。
  Future<List<Map<String, dynamic>>> groupMembers(
    ConversationModel conversation,
  ) async {
    if (conversation.isPrivate) return const [];
    await _requireConnected();
    final manager = TencentImSDKPlugin.v2TIMManager.getGroupManager();
    final rows = <Map<String, dynamic>>[];
    var nextSeq = '0';
    for (var page = 0; page < 20; page++) {
      final result = await manager.getGroupMemberList(
        groupID: conversation.imGroupId,
        filter: GroupMemberFilterTypeEnum.V2TIM_GROUP_MEMBER_FILTER_ALL,
        nextSeq: nextSeq,
        count: 100,
      );
      if (result.code != 0) {
        throw StateError('获取腾讯 IM 群成员失败：${result.code} ${result.desc}');
      }
      final dynamic data = result.data;
      final dynamic list = data?.memberInfoList;
      if (list is List) {
        for (final dynamic member in list) {
          if (member == null) continue;
          final json = Map<String, dynamic>.from(member.toJson() as Map);
          final imUserId = json['userID']?.toString() ?? '';
          final nameCard = json['nameCard']?.toString().trim() ?? '';
          final nickname = json['nickName']?.toString().trim() ?? '';
          rows.add({
            'userId': _businessUserId(imUserId),
            'nickname': nameCard.isNotEmpty
                ? nameCard
                : nickname.isNotEmpty
                ? nickname
                : '同路行用户',
            'avatarUrl': json['faceUrl']?.toString() ?? '',
            'avatarImageKey': '',
            'memberRole': _memberRole(json['role']),
            'memberStatus': 'ACTIVE',
            'joinedAt': _timestampText(json['joinTime']),
          });
        }
      }
      final candidate = data?.nextSeq?.toString() ?? '0';
      if (candidate == '0' || candidate.isEmpty || candidate == nextSeq) break;
      nextSeq = candidate;
    }
    return rows;
  }

  Future<void> _requireConnected() async {
    if (!connected) await connect();
    if (!connected) throw StateError('腾讯 IM 尚未连接');
  }

  ConversationModel _mergeConversation(
    ConversationModel binding,
    Map<String, dynamic> sdk,
  ) {
    final last = sdk['lastMessage'] is Map
        ? Map<String, dynamic>.from(sdk['lastMessage'] as Map)
        : const <String, dynamic>{};
    final sdkName = sdk['showName']?.toString().trim() ?? '';
    final sdkAvatar = sdk['faceUrl']?.toString().trim() ?? '';
    final recvOpt = _asInt(sdk['recvOpt']) ?? 0;
    return ConversationModel(
      id: binding.id,
      name: sdkName.isEmpty ? binding.name : sdkName,
      preview: last.isEmpty ? binding.preview : _messagePreview(last),
      time: last.isEmpty ? binding.time : _timestampText(last['timestamp']),
      unread: _asInt(sdk['unreadCount']) ?? 0,
      bizType: binding.bizType,
      bizId: binding.bizId,
      status: binding.status,
      providerType: binding.providerType,
      providerConversationKey: binding.providerConversationKey,
      imConversationId: binding.imConversationId,
      pinned: sdk['isPinned'] == true,
      muted: recvOpt != 0,
      avatarImageKey: binding.avatarImageKey,
      avatarUrl: sdkAvatar.isEmpty ? binding.avatarUrl : sdkAvatar,
      peerUserId: binding.peerUserId,
      relationType: binding.relationType,
      remainingTextMessages: binding.remainingTextMessages,
      canSendMedia: binding.canSendMedia,
    );
  }

  Map<String, dynamic> _messageToMap(Map<String, dynamic> raw) {
    final elemType = _asInt(raw['elemType']) ?? 0;
    var type = 'UNKNOWN';
    var content = '[不支持的消息]';
    var payload = <String, dynamic>{};

    if (elemType == MessageElemType.V2TIM_ELEM_TYPE_TEXT) {
      final text = raw['textElem'] is Map
          ? Map<String, dynamic>.from(raw['textElem'] as Map)
          : const <String, dynamic>{};
      type = 'TEXT';
      content = text['text']?.toString() ?? '';
    } else if (elemType == MessageElemType.V2TIM_ELEM_TYPE_CUSTOM) {
      final custom = raw['customElem'] is Map
          ? Map<String, dynamic>.from(raw['customElem'] as Map)
          : const <String, dynamic>{};
      final decoded = _decodeCustom(custom['data']);
      final customType = decoded['type']?.toString().toUpperCase() ?? 'CUSTOM';
      type = switch (customType) {
        'CHAT_IMAGE' => 'IMAGE',
        'CHAT_FILE' => 'FILE',
        _ => customType,
      };
      payload = Map<String, dynamic>.from(decoded);
      if (payload['mediaId'] != null && payload['fileId'] == null) {
        payload['fileId'] = payload['mediaId'];
      }
      content = payload['content']?.toString() ??
          payload['fileName']?.toString() ??
          custom['desc']?.toString() ??
          _customPreview(type);
    } else if (elemType == MessageElemType.V2TIM_ELEM_TYPE_LOCATION) {
      final location = raw['locationElem'] is Map
          ? Map<String, dynamic>.from(raw['locationElem'] as Map)
          : <String, dynamic>{};
      type = 'LOCATION';
      content = location['desc']?.toString() ?? '位置';
      payload = {
        ...location,
        'address': location['desc']?.toString() ?? '位置',
      };
    } else if (elemType == MessageElemType.V2TIM_ELEM_TYPE_IMAGE) {
      // 兼容历史上直接通过腾讯 IM 上传的原生图片消息。
      type = 'IMAGE';
      content = '[图片]';
      payload = raw['imageElem'] is Map
          ? Map<String, dynamic>.from(raw['imageElem'] as Map)
          : <String, dynamic>{};
    } else if (elemType == MessageElemType.V2TIM_ELEM_TYPE_FILE) {
      type = 'FILE';
      final file = raw['fileElem'] is Map
          ? Map<String, dynamic>.from(raw['fileElem'] as Map)
          : <String, dynamic>{};
      content = file['fileName']?.toString() ?? '[文件]';
      payload = file;
    }

    final imSender = raw['sender']?.toString() ?? '';
    final nameCard = raw['nameCard']?.toString().trim() ?? '';
    final nickname = raw['nickName']?.toString().trim() ?? '';
    return {
      'messageId': raw['msgID']?.toString() ?? raw['id']?.toString() ?? '',
      'senderUserId': _businessUserId(imSender),
      'senderNickname': nameCard.isNotEmpty
          ? nameCard
          : nickname.isNotEmpty
          ? nickname
          : '同路行用户',
      'senderAvatarImageKey': '',
      'senderAvatarUrl': raw['faceUrl']?.toString() ?? '',
      'messageType': type,
      'content': content,
      'payload': payload,
      'sentAt': _timestampText(raw['timestamp']),
      'messageStatus': 'NORMAL',
    };
  }

  Map<String, dynamic> _decodeCustom(Object? value) {
    if (value == null) return <String, dynamic>{};
    try {
      final decoded = jsonDecode(value.toString());
      return decoded is Map
          ? Map<String, dynamic>.from(decoded)
          : <String, dynamic>{'content': value.toString()};
    } catch (_) {
      return <String, dynamic>{'content': value.toString()};
    }
  }

  String _messagePreview(Map<String, dynamic> raw) {
    final mapped = _messageToMap(raw);
    final type = mapped['messageType']?.toString() ?? '';
    return switch (type) {
      'TEXT' => mapped['content']?.toString() ?? '',
      'IMAGE' => '[图片]',
      'FILE' => '[文件]',
      'LOCATION' => '[位置]',
      'SYSTEM' => mapped['content']?.toString() ?? '[系统消息]',
      _ when type.endsWith('_CARD') => '[${mapped['content'] ?? '业务消息'}]',
      _ => mapped['content']?.toString() ?? '[新消息]',
    };
  }

  String _customPreview(String type) => switch (type) {
    'IMAGE' => '[图片]',
    'FILE' => '[文件]',
    'LOCATION' => '[位置]',
    'SYSTEM' => '[系统消息]',
    _ => '[自定义消息]',
  };

  String _businessUserId(String imUserId) =>
      imUserId.startsWith('u_') ? imUserId.substring(2) : imUserId;

  String _memberRole(Object? value) {
    final role = _asInt(value) ?? 0;
    return switch (role) {
      400 => 'OWNER',
      300 => 'ADMIN',
      _ => 'MEMBER',
    };
  }

  int? _asInt(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '');
  }

  String _timestampText(Object? value) {
    final seconds = _asInt(value);
    if (seconds == null || seconds <= 0) return '';
    return DateTime.fromMillisecondsSinceEpoch(seconds * 1000)
        .toLocal()
        .toIso8601String();
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
            .removeAdvancedMsgListener(listener: _messageListener!);
      }
      if (_conversationListener != null) {
        await TencentImSDKPlugin.v2TIMManager
            .getConversationManager()
            .removeConversationListener(listener: _conversationListener!);
      }
      await TencentImSDKPlugin.v2TIMManager.unInitSDK();
    }
    _initialized = false;
    _messageListener = null;
    _conversationListener = null;
    await _messageEvents.close();
    await _conversationEvents.close();
  }
}
