import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:http/http.dart' as http;
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import '../../profile/pages/profile_system_pages.dart';
import '../../profile/widgets/user_avatar.dart';
import 'chat_location_detail_page.dart';
import 'chat_management_pages.dart';

class ChatSessionPage extends StatefulWidget {
  const ChatSessionPage({required this.conversation, super.key});
  final ConversationModel conversation;
  @override
  State<ChatSessionPage> createState() => _ChatSessionPageState();
}

class _ChatSessionPageState extends State<ChatSessionPage> {
  static const permissionChannel = MethodChannel('com.tongluxing/permissions');
  final input = TextEditingController();
  final scroll = ScrollController();
  final focus = FocusNode();
  List<Map<String, dynamic>> messages = [];
  Map<String, dynamic> workspace = {};
  Map<String, dynamic> privatePermission = {};
  final Map<String, Future<String>> attachmentUrls = {};
  final Map<String, Future<Map<String, dynamic>>> confirmationDetails = {};
  final Set<String> respondingConfirmations = {};
  bool loading = true, sending = false, announcementHidden = false;
  bool toolsExpanded = false;
  Timer? pollingTimer;

  bool get isPrivate => widget.conversation.isPrivate;
  bool get privateUnlocked => privatePermission['unlocked'] == true;
  bool get canSendMedia =>
      !isPrivate ||
      (privatePermission.isEmpty
          ? widget.conversation.canSendMedia
          : privatePermission['canSendMedia'] == true);
  int get remainingPrivateMessages =>
      (privatePermission['remainingTextMessages'] as num?)?.toInt() ??
      widget.conversation.remainingTextMessages;

  @override
  void initState() {
    super.initState();
    load();
    pollingTimer = Timer.periodic(
      const Duration(seconds: 2),
      (_) => load(silent: true),
    );
    focus.addListener(() {
      if (focus.hasFocus) {
        if (toolsExpanded) setState(() => toolsExpanded = false);
        _showLatest();
      }
    });
  }

  @override
  void dispose() {
    input.dispose();
    scroll.dispose();
    focus.dispose();
    pollingTimer?.cancel();
    super.dispose();
  }

  Future<void> load({bool silent = false}) async {
    try {
      final service = ChatService(context.read<AppSession>().api);
      final values = await Future.wait<dynamic>([
        service.messages(widget.conversation.id),
        isPrivate
            ? service.privateConversationPermission(widget.conversation.id)
            : service.groupWorkspace(widget.conversation.id),
      ]);
      final nextMessages = (values[0] as List<Map<String, dynamic>>).reversed
          .toList();
      final previousLatestId = messages.isEmpty
          ? null
          : messages.last['messageId']?.toString();
      final nextLatestId = nextMessages.isEmpty
          ? null
          : nextMessages.last['messageId']?.toString();
      if (previousLatestId != nextLatestId) confirmationDetails.clear();
      messages = nextMessages;
      if (isPrivate) {
        privatePermission = Map<String, dynamic>.from(values[1] as Map);
        workspace = const {'selfRole': 'MEMBER', 'items': <Object>[]};
      } else {
        workspace = Map<String, dynamic>.from(values[1] as Map);
        privatePermission = const {};
      }
    } catch (_) {}
    if (mounted) {
      setState(() => loading = false);
      if (!silent) _showLatest();
    }
  }

  void _showLatest() => WidgetsBinding.instance.addPostFrameCallback((_) {
    if (scroll.hasClients) {
      scroll.animateTo(
        scroll.position.maxScrollExtent,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    }
  });

  Future<void> send() async {
    final text = input.text.trim();
    if (text.isEmpty || sending) return;
    setState(() => sending = true);
    try {
      final response = await ChatService(
        context.read<AppSession>().api,
      ).send(widget.conversation.id, text);
      if (response['messageStatus'] == 'BLOCKED') {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('消息触发安全规则，已拦截且不会发送给其他成员')),
          );
        }
      } else {
        input.clear();
        await load(silent: true);
        _showLatest();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  Map<String, dynamic> _payload(Map<String, dynamic> message) =>
      Map<String, dynamic>.from(message['payload'] as Map? ?? const {});

  Map<String, dynamic>? get _announcement {
    if (isPrivate || announcementHidden) return null;
    final items = (workspace['items'] as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .where(
          (e) => e['itemType'] == 'ANNOUNCEMENT' && e['itemStatus'] == 'ACTIVE',
        );
    return items.isEmpty ? null : items.first;
  }

  String _privateSubtitle() {
    final type =
        privatePermission['relationType']?.toString() ??
        widget.conversation.relationType;
    return switch (type) {
      'MUTUAL' => '互相关注 · 联系人',
      'SAME_TRIP' => '同一行程 · 可正常私聊',
      'REPLIED' => '对方已回复 · 已解除限制',
      'FOLLOWING' => '单向关注 · 剩余 $remainingPrivateMessages 条',
      _ => '私聊',
    };
  }

  String _groupSubtitle() {
    if (widget.conversation.status == 'HISTORY') return '行程已结束 · 群聊保留';
    return widget.conversation.providerType == 'TENCENT_IM'
        ? '行程群聊 · 腾讯 IM'
        : '行程群聊 · 实时同步';
  }

  Widget _privatePermissionBanner() {
    final unlocked = privateUnlocked;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
      color: AppColors.primarySoft,
      child: Row(
        children: [
          Icon(
            unlocked ? LucideIcons.badgeCheck : LucideIcons.messageCircle,
            size: 15,
            color: AppColors.primaryDark,
          ),
          const SizedBox(width: 7),
          Expanded(
            child: Text(
              unlocked
                  ? (_privateSubtitle())
                  : '对方回复或回关前，还可发送 $remainingPrivateMessages 条文字消息',
              style: const TextStyle(
                fontSize: 11.5,
                color: AppColors.primaryDark,
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final announcement = _announcement;
    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: AppBar(
        toolbarHeight: 48,
        titleSpacing: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.conversation.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
            ),
            Text(
              isPrivate ? _privateSubtitle() : _groupSubtitle(),
              style: const TextStyle(
                fontSize: 10.5,
                color: AppColors.muted,
                fontWeight: FontWeight.w400,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: isPrivate ? '查看资料' : '聊天信息',
            onPressed: () async {
              if (isPrivate) {
                final peerUserId = widget.conversation.peerUserId;
                if (peerUserId.isEmpty) return;
                await Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => PublicProfilePage(userId: peerUserId),
                  ),
                );
              } else {
                await Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) =>
                        ChatGroupDetailsPage(conversation: widget.conversation),
                  ),
                );
              }
              await load();
            },
            icon: Icon(
              isPrivate ? LucideIcons.userRound : LucideIcons.ellipsis,
              size: 20,
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          if (isPrivate && privatePermission.isNotEmpty)
            _privatePermissionBanner(),
          if (!isPrivate && widget.conversation.status == 'HISTORY')
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              color: AppColors.primarySoft,
              child: const Row(
                children: [
                  Icon(
                    LucideIcons.history,
                    size: 17,
                    color: AppColors.primaryDark,
                  ),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      '该行程已结束，群聊仍保留，成员可以继续发言。',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppColors.primaryDark,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          if (announcement != null)
            Material(
              color: const Color(0xFFFFF7E8),
              child: InkWell(
                onTap: () => showDialog<void>(
                  context: context,
                  builder: (c) => AlertDialog(
                    title: Text(announcement['title']?.toString() ?? '群公告'),
                    content: Text(announcement['content']?.toString() ?? ''),
                    actions: [
                      FilledButton(
                        onPressed: () => Navigator.pop(c),
                        child: const Text('知道了'),
                      ),
                    ],
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 10, 8, 10),
                  child: Row(
                    children: [
                      const Icon(
                        LucideIcons.megaphone,
                        size: 18,
                        color: Color(0xFFB36B00),
                      ),
                      const SizedBox(width: 9),
                      Expanded(
                        child: Text(
                          '${announcement['title']}：${announcement['content'] ?? ''}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 12,
                            color: Color(0xFF8B5A00),
                          ),
                        ),
                      ),
                      IconButton(
                        tooltip: '关闭公告',
                        onPressed: () =>
                            setState(() => announcementHidden = true),
                        icon: const Icon(LucideIcons.x, size: 18),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          Expanded(
            child: loading
                ? const Center(child: CircularProgressIndicator())
                : messages.isEmpty
                ? Center(
                    child: Text(
                      isPrivate ? '暂无消息，发一条文字打个招呼吧' : '暂无消息，和车队成员打个招呼吧',
                    ),
                  )
                : ListView.builder(
                    controller: scroll,
                    reverse: false,
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    padding: const EdgeInsets.fromLTRB(12, 10, 12, 6),
                    itemCount: messages.length,
                    itemBuilder: (_, i) => _message(messages[i]),
                  ),
          ),
          SafeArea(
            top: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  padding: const EdgeInsets.fromLTRB(6, 6, 8, 6),
                  color: Colors.white,
                  child: Row(
                    children: [
                      IconButton(
                        tooltip: toolsExpanded ? '收起' : '更多',
                        onPressed: _more,
                        iconSize: 21,
                        icon: AnimatedRotation(
                          turns: toolsExpanded ? .125 : 0,
                          duration: const Duration(milliseconds: 180),
                          child: const Icon(LucideIcons.circlePlus),
                        ),
                      ),
                      Expanded(
                        child: TextField(
                          controller: input,
                          focusNode: focus,
                          maxLength: 1000,
                          buildCounter:
                              (
                                _, {
                                required currentLength,
                                required isFocused,
                                maxLength,
                              }) => null,
                          onTap: _showLatest,
                          minLines: 1,
                          maxLines: 4,
                          decoration: const InputDecoration(
                            hintText: '输入消息...',
                            isDense: true,
                            contentPadding: EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 9,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      SizedBox(
                        width: 38,
                        height: 38,
                        child: IconButton.filled(
                          onPressed: sending ? null : send,
                          icon: sending
                              ? const SizedBox.square(
                                  dimension: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(LucideIcons.send, size: 18),
                        ),
                      ),
                    ],
                  ),
                ),
                AnimatedSize(
                  duration: const Duration(milliseconds: 220),
                  curve: Curves.easeOutCubic,
                  child: toolsExpanded ? _toolPanel() : const SizedBox.shrink(),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _message(Map<String, dynamic> message) {
    final self =
        message['senderUserId']?.toString() ==
        context.read<AppSession>().userId;
    final type = message['messageType']?.toString() ?? 'TEXT';
    final content = message['content']?.toString() ?? '';
    if (type == 'SYSTEM') {
      return Center(
        child: Container(
          margin: const EdgeInsets.only(bottom: 10),
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
          decoration: BoxDecoration(
            color: const Color(0xFFEFF2F7),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Text(
            content,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 12, color: AppColors.muted),
          ),
        ),
      );
    }
    final payload = _payload(message);
    final card = type.endsWith('_CARD') || type == 'LOCATION';
    final maxBubbleWidth = MediaQuery.sizeOf(context).width - 104;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        mainAxisAlignment: self
            ? MainAxisAlignment.end
            : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!self) _avatar(message),
          if (!self) const SizedBox(width: 7),
          Flexible(
            child: Column(
              crossAxisAlignment: self
                  ? CrossAxisAlignment.end
                  : CrossAxisAlignment.start,
              children: [
                if (!self)
                  Padding(
                    padding: const EdgeInsets.only(left: 3, bottom: 3),
                    child: Text(
                      message['senderNickname']?.toString() ?? '同路行用户',
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.muted,
                      ),
                    ),
                  ),
                GestureDetector(
                  onTap: type == 'LOCATION'
                      ? () => _showLocation(payload)
                      : card
                      ? () => _openCard(type, payload)
                      : type == 'IMAGE' || type == 'FILE'
                      ? () => _openAttachment(payload)
                      : null,
                  child: Container(
                    constraints: BoxConstraints(maxWidth: maxBubbleWidth),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 9,
                    ),
                    decoration: BoxDecoration(
                      color: card
                          ? Colors.white
                          : self
                          ? AppColors.primary
                          : Colors.white,
                      borderRadius: BorderRadius.circular(14),
                      border: card
                          ? Border.all(color: const Color(0xFFDDE7FF))
                          : null,
                    ),
                    child: _messageContent(type, content, payload, self),
                  ),
                ),
              ],
            ),
          ),
          if (self) const SizedBox(width: 7),
          if (self) _avatar(message),
        ],
      ),
    );
  }

  Widget _avatar(Map<String, dynamic> message) {
    final senderUserId = message['senderUserId']?.toString() ?? '';
    final avatarUrl = message['senderAvatarUrl']?.toString().trim() ?? '';
    final avatarKey = message['senderAvatarImageKey']?.toString().trim() ?? '';
    final nickname = message['senderNickname']?.toString() ?? '同路行用户';
    return UserAvatar(
      nickname: nickname,
      avatarImageKey: avatarKey,
      avatarUrl: avatarUrl,
      radius: 17,
      onTap: senderUserId.isEmpty
          ? null
          : () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => PublicProfilePage(userId: senderUserId),
              ),
            ).then((_) => load(silent: true)),
    );
  }

  Widget _messageContent(
    String type,
    String content,
    Map<String, dynamic> payload,
    bool self,
  ) {
    if (type == 'TRIP_CONFIRM_CARD') {
      return _tripConfirmationCard(content, payload);
    }
    if (type.endsWith('_CARD')) {
      final icon = type == 'POLL_CARD'
          ? LucideIcons.listChecks
          : type == 'REMINDER_CARD'
          ? LucideIcons.alarmClock
          : LucideIcons.carFront;
      final label = type == 'POLL_CARD'
          ? '群投票'
          : type == 'REMINDER_CARD'
          ? '行程提醒'
          : '行程确认';
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: AppColors.primary),
              const SizedBox(width: 8),
              Text(label, style: const TextStyle(fontWeight: FontWeight.w800)),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            payload['title']?.toString() ?? content,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text(content, style: const TextStyle(color: AppColors.muted)),
          const Divider(height: 22),
          const Row(
            children: [
              Expanded(child: Text('点击查看详情')),
              Icon(LucideIcons.chevronRight, size: 17),
            ],
          ),
        ],
      );
    }
    if (type == 'LOCATION') {
      return _LocationPreviewCard(
        payload: payload,
        title: payload['address']?.toString().trim().isNotEmpty == true
            ? payload['address'].toString()
            : content,
      );
    }
    if (type == 'IMAGE') {
      final fileId = payload['fileId'];
      if (fileId == null) {
        return const SizedBox(
          width: 180,
          height: 100,
          child: Center(child: Text('图片暂时无法加载')),
        );
      }
      return FutureBuilder<String>(
        future: attachmentUrls.putIfAbsent(
          fileId.toString(),
          () => ChatAttachmentService(
            context.read<AppSession>().api,
          ).downloadUrl(fileId),
        ),
        builder: (_, snapshot) => snapshot.hasData
            ? ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: Image.network(
                  snapshot.data!,
                  height: 170,
                  width: 220,
                  fit: BoxFit.cover,
                  errorBuilder: (_, _, _) => const SizedBox(
                    width: 220,
                    height: 120,
                    child: Center(child: Text('图片加载失败')),
                  ),
                ),
              )
            : const SizedBox(
                width: 220,
                height: 120,
                child: Center(child: CircularProgressIndicator()),
              ),
      );
    }
    if (type == 'FILE') {
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            LucideIcons.fileText,
            color: self ? Colors.white : AppColors.primary,
          ),
          const SizedBox(width: 9),
          Flexible(child: Text(content, style: _bubbleText(self))),
        ],
      );
    }
    return Text(content, style: _bubbleText(self));
  }

  Future<Map<String, dynamic>> _confirmation(String confirmationId) =>
      confirmationDetails.putIfAbsent(
        confirmationId,
        () => ChatService(
          context.read<AppSession>().api,
        ).tripConfirmation(widget.conversation.id, confirmationId),
      );

  Future<void> _respondFromCard(String confirmationId, String status) async {
    if (respondingConfirmations.contains(confirmationId)) return;
    setState(() => respondingConfirmations.add(confirmationId));
    try {
      await ChatService(
        context.read<AppSession>().api,
      ).respondTripConfirmation(widget.conversation.id, confirmationId, status);
      confirmationDetails.remove(confirmationId);
      await _confirmation(confirmationId);
      await load(silent: true);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(status == 'CONFIRMED' ? '已确认参加本次行程' : '已拒绝参加本次行程'),
          ),
        );
      }
    } on ApiException catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.message)));
      }
    } finally {
      if (mounted) {
        setState(() => respondingConfirmations.remove(confirmationId));
      }
    }
  }

  Widget _tripConfirmationCard(String content, Map<String, dynamic> payload) {
    final confirmationId = payload['confirmationId']?.toString() ?? '';
    if (confirmationId.isEmpty) return Text(content);
    return FutureBuilder<Map<String, dynamic>>(
      future: _confirmation(confirmationId),
      builder: (context, snapshot) {
        final row = snapshot.data;
        final records = (row?['records'] as List? ?? const [])
            .whereType<Map>()
            .map((value) => Map<String, dynamic>.from(value))
            .toList();
        final selfId = context.read<AppSession>().userId;
        final selfRecord = records
            .where((record) => record['userId']?.toString() == selfId)
            .firstOrNull;
        final selfRole =
            selfRecord?['memberRole']?.toString() ??
            workspace['selfRole']?.toString() ??
            'MEMBER';
        final currentStatus = selfRecord?['status']?.toString() ?? 'WAITING';
        final open = row?['confirmationStatus'] == 'OPEN';
        final busy = respondingConfirmations.contains(confirmationId);
        final route = [payload['startName'], payload['endName']]
            .where((value) => value?.toString().trim().isNotEmpty == true)
            .map((value) => value.toString())
            .join(' → ');

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(LucideIcons.carFront, color: AppColors.primary),
                SizedBox(width: 8),
                Text('行程确认', style: TextStyle(fontWeight: FontWeight.w800)),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              payload['title']?.toString() ?? content,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
            ),
            if (route.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(route, style: const TextStyle(color: AppColors.muted)),
            ],
            const SizedBox(height: 8),
            if (snapshot.connectionState == ConnectionState.waiting)
              const LinearProgressIndicator(minHeight: 2)
            else if (snapshot.hasError)
              const Text(
                '确认状态加载失败，点击卡片查看详情',
                style: TextStyle(color: AppColors.danger),
              )
            else ...[
              Text(
                '已确认 ${row?['confirmed'] ?? 0} · 待确认 ${row?['waiting'] ?? 0} · 不参加 ${row?['rejected'] ?? 0}',
                style: const TextStyle(fontSize: 12, color: AppColors.muted),
              ),
              if (open &&
                  selfRole != 'OWNER' &&
                  currentStatus == 'WAITING') ...[
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: busy
                            ? null
                            : () =>
                                  _respondFromCard(confirmationId, 'REJECTED'),
                        child: Text(
                          currentStatus == 'REJECTED' ? '已拒绝' : '拒绝参加',
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: FilledButton(
                        onPressed: busy
                            ? null
                            : () =>
                                  _respondFromCard(confirmationId, 'CONFIRMED'),
                        child: busy
                            ? const SizedBox.square(
                                dimension: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : Text(
                                currentStatus == 'CONFIRMED' ? '已确认' : '确认参加',
                              ),
                      ),
                    ),
                  ],
                ),
              ] else ...[
                const Divider(height: 22),
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        !open
                            ? '本次确认已结束'
                            : currentStatus == 'CONFIRMED'
                            ? '你已确认参加，点击查看进度'
                            : currentStatus == 'REJECTED'
                            ? '你已拒绝参加，点击查看进度'
                            : '点击查看确认进度',
                      ),
                    ),
                    const Icon(LucideIcons.chevronRight, size: 17),
                  ],
                ),
              ],
            ],
          ],
        );
      },
    );
  }

  TextStyle _bubbleText(bool self) =>
      TextStyle(color: self ? Colors.white : AppColors.text, height: 1.45);

  void _more() {
    focus.unfocus();
    if (isPrivate && !canSendMedia) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('对方回复或回关后才可发送图片和位置')));
      return;
    }
    setState(() => toolsExpanded = !toolsExpanded);
    if (toolsExpanded) _showLatest();
  }

  Widget _toolPanel() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 14),
      decoration: const BoxDecoration(
        color: Color(0xFFF5F7FB),
        border: Border(top: BorderSide(color: Color(0xFFE8ECF3))),
      ),
      child: Row(
        children: [
          Expanded(
            child: _action(LucideIcons.image, '图片', '从相册选择', _pickImage),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: _action(LucideIcons.mapPin, '位置', '发送当前位置', _sendLocation),
          ),
        ],
      ),
    );
  }

  Widget _action(
    IconData icon,
    String label,
    String subtitle,
    Future<void> Function() action,
  ) => InkWell(
    borderRadius: BorderRadius.circular(12),
    onTap: () async {
      setState(() => toolsExpanded = false);
      await action();
    },
    child: Container(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: AppColors.primarySoft,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: AppColors.primary, size: 19),
          ),
          const SizedBox(height: 6),
          Text(
            label,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 3),
          Text(
            subtitle,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 9, color: AppColors.muted),
          ),
        ],
      ),
    ),
  );

  Future<void> _pickImage() async {
    if (!canSendMedia) return;
    final file = await ImagePicker().pickImage(
      source: ImageSource.gallery,
      imageQuality: 88,
    );
    if (file == null || !mounted) return;
    await _upload(
      file.name,
      file.mimeType ?? 'image/jpeg',
      await file.readAsBytes(),
      true,
    );
  }

  Future<void> _upload(
    String name,
    String contentType,
    Uint8List bytes,
    bool image,
  ) async {
    setState(() => sending = true);
    try {
      final api = context.read<AppSession>().api;
      final meta = await ChatAttachmentService(api).upload(
        conversationId: widget.conversation.id,
        fileName: name,
        contentType: contentType,
        bytes: bytes,
        image: image,
      );
      await ChatService(api).send(
        widget.conversation.id,
        image ? '' : name,
        type: image ? 'IMAGE' : 'FILE',
        payload: meta,
      );
      await load(silent: true);
      _showLatest();
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  Future<void> _sendLocation() async {
    if (!canSendMedia) return;
    final api = context.read<AppSession>().api;
    final granted =
        await permissionChannel.invokeMethod<bool>('requestLocation') ?? false;
    if (!granted || !mounted) return;
    final location = Map<String, dynamic>.from(
      await permissionChannel.invokeMethod<Map>('getCurrentLocation') ??
          const {},
    );
    final address = location['address']?.toString().trim();
    await ChatService(api).send(
      widget.conversation.id,
      address?.isNotEmpty == true ? address! : '我的当前位置',
      type: 'LOCATION',
      payload: {
        ...location,
        'address': address?.isNotEmpty == true ? address : '当前位置',
      },
    );
    await load(silent: true);
    _showLatest();
  }

  Future<void> _openAttachment(Map<String, dynamic> payload) async {
    final fileId = payload['fileId'];
    if (fileId == null) return;
    final url = await ChatAttachmentService(
      context.read<AppSession>().api,
    ).downloadUrl(fileId);
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => _ChatImagePreviewPage(imageUrl: url)),
    );
  }

  Future<void> _showLocation(Map<String, dynamic> payload) async {
    final latitude = _coordinate(payload['latitude'] ?? payload['lat']);
    final longitude = _coordinate(payload['longitude'] ?? payload['lng']);
    if (latitude == null || longitude == null) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('该位置缺少有效的地图坐标')));
      }
      return;
    }
    final address = payload['address']?.toString().trim() ?? '';
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ChatLocationDetailPage(
          latitude: latitude,
          longitude: longitude,
          title: address.isEmpty ? '成员共享的位置' : address,
          address: address,
        ),
      ),
    );
  }

  double? _coordinate(Object? value) {
    if (value is num) return value.toDouble();
    return double.tryParse(value?.toString() ?? '');
  }

  Future<void> _openCard(String type, Map<String, dynamic> payload) async {
    final id =
        payload['itemId']?.toString() ?? payload['confirmationId']?.toString();
    if (id == null) return;
    if (type == 'POLL_CARD') {
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => PollCardPage(
            conversation: widget.conversation,
            itemId: id,
            selfRole: workspace['selfRole']?.toString() ?? 'MEMBER',
          ),
        ),
      );
    } else if (type == 'TRIP_CONFIRM_CARD') {
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => TripConfirmationCardPage(
            conversation: widget.conversation,
            confirmationId: id,
          ),
        ),
      );
    } else {
      showDialog<void>(
        context: context,
        builder: (c) => AlertDialog(
          title: Text(payload['title']?.toString() ?? '行程提醒'),
          content: Text(payload['content']?.toString() ?? ''),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(c),
              child: const Text('知道了'),
            ),
          ],
        ),
      );
    }
    await load(silent: true);
  }
}

class PollCardPage extends StatefulWidget {
  const PollCardPage({
    required this.conversation,
    required this.itemId,
    required this.selfRole,
    super.key,
  });
  final ConversationModel conversation;
  final String itemId, selfRole;
  @override
  State<PollCardPage> createState() => _PollCardPageState();
}

class _PollCardPageState extends State<PollCardPage> {
  Map<String, dynamic>? row;
  Map<String, dynamic> get payload {
    try {
      return Map<String, dynamic>.from(
        jsonDecode(row?['payloadJson']?.toString() ?? '{}') as Map,
      );
    } catch (_) {
      return {};
    }
  }

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    row = await ChatService(
      context.read<AppSession>().api,
    ).groupItem(widget.conversation.id, widget.itemId);
    if (mounted) setState(() {});
  }

  List<Map<String, String>> get options =>
      (payload['options'] as List? ?? const []).asMap().entries.map((entry) {
        final value = entry.value;
        if (value is Map) {
          return {
            'key': value['key']?.toString() ?? '${entry.key + 1}',
            'label': value['label']?.toString() ?? '选项 ${entry.key + 1}',
          };
        }
        return {'key': value.toString(), 'label': value.toString()};
      }).toList();

  Map<String, int> get voteCounts => {
    for (final value in row?['votes'] as List? ?? const [])
      (value as Map)['optionKey'].toString():
          ((value['votes'] as num?)?.toInt() ?? 0),
  };

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('投票详情')),
    body: row == null
        ? const Center(child: CircularProgressIndicator())
        : ListView(
            padding: const EdgeInsets.all(22),
            children: [
              Text(
                row!['title']?.toString() ?? '群投票',
                style: const TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              Text(row!['content']?.toString() ?? ''),
              const SizedBox(height: 18),
              Builder(
                builder: (context) {
                  final counts = voteCounts;
                  final total = counts.values.fold<int>(0, (a, b) => a + b);
                  final myVote = row!['myVote']?.toString();
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        myVote == null
                            ? '请选择一个选项，每人仅可投票一次'
                            : '你已完成投票 · 共 $total 人参与',
                        style: const TextStyle(color: AppColors.muted),
                      ),
                      const SizedBox(height: 14),
                      for (final option in options)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: InkWell(
                            borderRadius: BorderRadius.circular(18),
                            onTap:
                                row!['itemStatus'] == 'ACTIVE' && myVote == null
                                ? () async {
                                    await ChatService(
                                      context.read<AppSession>().api,
                                    ).vote(
                                      widget.conversation.id,
                                      widget.itemId,
                                      option['key']!,
                                    );
                                    await load();
                                  }
                                : null,
                            child: Container(
                              padding: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: myVote == option['key']
                                    ? AppColors.primarySoft
                                    : Colors.white,
                                borderRadius: BorderRadius.circular(18),
                                border: Border.all(
                                  color: myVote == option['key']
                                      ? AppColors.primary
                                      : const Color(0xFFE3E7EF),
                                ),
                              ),
                              child: Column(
                                children: [
                                  Row(
                                    children: [
                                      Icon(
                                        myVote == option['key']
                                            ? LucideIcons.circleCheckBig
                                            : LucideIcons.circle,
                                        size: 20,
                                        color: myVote == option['key']
                                            ? AppColors.primary
                                            : AppColors.muted,
                                      ),
                                      const SizedBox(width: 10),
                                      Expanded(
                                        child: Text(
                                          option['label']!,
                                          style: const TextStyle(
                                            fontWeight: FontWeight.w700,
                                          ),
                                        ),
                                      ),
                                      Text('${counts[option['key']] ?? 0} 票'),
                                    ],
                                  ),
                                  const SizedBox(height: 10),
                                  LinearProgressIndicator(
                                    value: total == 0
                                        ? 0
                                        : (counts[option['key']] ?? 0) / total,
                                    minHeight: 6,
                                    borderRadius: BorderRadius.circular(6),
                                    backgroundColor: const Color(0xFFE9EDF4),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                    ],
                  );
                },
              ),
              if (const ['OWNER', 'ADMIN'].contains(widget.selfRole) &&
                  row!['itemStatus'] == 'ACTIVE')
                OutlinedButton.icon(
                  onPressed: () async {
                    await ChatService(
                      context.read<AppSession>().api,
                    ).closePoll(widget.conversation.id, widget.itemId);
                    await load();
                  },
                  icon: const Icon(LucideIcons.circleStop),
                  label: const Text('截止投票'),
                ),
            ],
          ),
  );
}

class TripConfirmationCardPage extends StatefulWidget {
  const TripConfirmationCardPage({
    required this.conversation,
    required this.confirmationId,
    super.key,
  });
  final ConversationModel conversation;
  final String confirmationId;
  @override
  State<TripConfirmationCardPage> createState() =>
      _TripConfirmationCardPageState();
}

class _TripConfirmationCardPageState extends State<TripConfirmationCardPage> {
  Map<String, dynamic>? row;
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    row = await ChatService(
      context.read<AppSession>().api,
    ).tripConfirmation(widget.conversation.id, widget.confirmationId);
    if (mounted) setState(() {});
  }

  Future<void> respond(String status) async {
    await ChatService(context.read<AppSession>().api).respondTripConfirmation(
      widget.conversation.id,
      widget.confirmationId,
      status,
    );
    await load();
  }

  @override
  Widget build(BuildContext context) {
    final records = row?['records'] as List? ?? const [];
    final selfId = context.read<AppSession>().userId;
    final self = records
        .cast<Map>()
        .where((e) => e['userId']?.toString() == selfId)
        .firstOrNull;
    final owner = self?['memberRole'] == 'OWNER';
    return Scaffold(
      appBar: AppBar(title: const Text('行程确认')),
      body: row == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(22),
              children: [
                const Icon(
                  LucideIcons.carFront,
                  size: 52,
                  color: AppColors.primary,
                ),
                const SizedBox(height: 12),
                Text(
                  '最终出行确认',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    _count('已确认', row!['confirmed'], const Color(0xFF16A36A)),
                    _count('待确认', row!['waiting'], const Color(0xFFE58B00)),
                    _count('不参加', row!['rejected'], AppColors.danger),
                  ],
                ),
                const SizedBox(height: 18),
                for (final entry in records)
                  ListTile(
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => PublicProfilePage(
                          userId: entry['userId']?.toString() ?? '',
                        ),
                      ),
                    ),
                    leading: UserAvatar(
                      nickname: entry['nickname']?.toString() ?? '同路行用户',
                      avatarImageKey: entry['avatarImageKey']?.toString() ?? '',
                      radius: 20,
                    ),
                    title: Text(entry['nickname']?.toString() ?? '同路行用户'),
                    subtitle: Text(entry['memberRole']?.toString() ?? 'MEMBER'),
                    trailing: Text(_status(entry['status']?.toString())),
                  ),
                if (row!['confirmationStatus'] == 'OPEN' &&
                    !owner &&
                    self?['status'] == 'WAITING') ...[
                  const SizedBox(height: 14),
                  FilledButton(
                    onPressed: () => respond('CONFIRMED'),
                    child: const Text('确认参加'),
                  ),
                  TextButton(
                    onPressed: () => respond('REJECTED'),
                    child: const Text('暂不参加'),
                  ),
                ],
                if (row!['confirmationStatus'] == 'OPEN' &&
                    !owner &&
                    self?['status'] != 'WAITING')
                  Padding(
                    padding: const EdgeInsets.only(top: 14),
                    child: Center(
                      child: Text(
                        self?['status'] == 'CONFIRMED' ? '你已确认参加' : '你已拒绝参加',
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                  ),
                if (row!['confirmationStatus'] == 'OPEN' && owner) ...[
                  const SizedBox(height: 14),
                  FilledButton.icon(
                    onPressed: () async {
                      final unresolved =
                          ((row!['waiting'] as num?)?.toInt() ?? 0) +
                          ((row!['rejected'] as num?)?.toInt() ?? 0);
                      if (unresolved > 0) {
                        final proceed = await showDialog<bool>(
                          context: context,
                          builder: (dialogContext) => AlertDialog(
                            title: const Text('仍要开启行程吗？'),
                            content: Text('当前有$unresolved名成员未确认，\n是否继续开启行程？'),
                            actions: [
                              TextButton(
                                onPressed: () =>
                                    Navigator.pop(dialogContext, false),
                                child: const Text('取消'),
                              ),
                              FilledButton(
                                onPressed: () =>
                                    Navigator.pop(dialogContext, true),
                                child: const Text('继续开启'),
                              ),
                            ],
                          ),
                        );
                        if (proceed != true || !context.mounted) return;
                      }
                      await ChatService(
                        context.read<AppSession>().api,
                      ).startConfirmedTrip(
                        widget.conversation.id,
                        widget.confirmationId,
                      );
                      if (context.mounted) Navigator.pop(context);
                    },
                    icon: const Icon(LucideIcons.navigation),
                    label: const Text('开启行程'),
                  ),
                ],
              ],
            ),
    );
  }

  Widget _count(String label, Object? value, Color color) => Expanded(
    child: Column(
      children: [
        Text(
          '${value ?? 0}',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w800,
            color: color,
          ),
        ),
        Text(
          label,
          style: const TextStyle(fontSize: 11, color: AppColors.muted),
        ),
      ],
    ),
  );

  String _status(String? value) => switch (value) {
    'CONFIRMED' => '✅ 已确认',
    'REJECTED' => '❌ 不参加',
    _ => '⏳ 待确认',
  };
}

class _LocationPreviewCard extends StatelessWidget {
  const _LocationPreviewCard({required this.payload, required this.title});
  final Map<String, dynamic> payload;
  final String title;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 224,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: SizedBox(
              height: 126,
              width: double.infinity,
              child: CustomPaint(
                painter: _LocationMapPainter(),
                child: const Center(
                  child: Icon(
                    LucideIcons.mapPin,
                    color: AppColors.danger,
                    size: 34,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(height: 9),
          Text(
            title.isEmpty ? '当前位置' : title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              fontWeight: FontWeight.w800,
              color: AppColors.text,
            ),
          ),
          const SizedBox(height: 5),
          const Row(
            children: [
              Expanded(
                child: Text(
                  '点击查看地图',
                  style: TextStyle(fontSize: 11, color: AppColors.muted),
                ),
              ),
              Icon(LucideIcons.chevronRight, size: 14, color: AppColors.muted),
            ],
          ),
        ],
      ),
    );
  }
}

class _LocationMapPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(
      Offset.zero & size,
      Paint()..color = const Color(0xFFEAF4EC),
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(
          -20,
          size.height * .62,
          size.width * .62,
          size.height * .35,
        ),
        const Radius.circular(30),
      ),
      Paint()..color = const Color(0xFFCDEEFF),
    );
    final road = Paint()
      ..color = Colors.white
      ..strokeWidth = 5
      ..strokeCap = StrokeCap.round;
    for (double y = 18; y < size.height; y += 34) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y + 10), road);
    }
    for (double x = 24; x < size.width; x += 48) {
      canvas.drawLine(Offset(x, 0), Offset(x - 18, size.height), road);
    }
    final mainRoad = Paint()
      ..color = const Color(0xFFF7CE69)
      ..strokeWidth = 10
      ..strokeCap = StrokeCap.round;
    final mainPath = Path()
      ..moveTo(-10, size.height * .28)
      ..cubicTo(
        size.width * .25,
        size.height * .18,
        size.width * .55,
        size.height * .72,
        size.width + 10,
        size.height * .58,
      );
    canvas.drawPath(mainPath, mainRoad);
    final route = Paint()
      ..color = AppColors.primary
      ..strokeWidth = 4
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    final routePath = Path()
      ..moveTo(size.width * .08, size.height * .8)
      ..quadraticBezierTo(
        size.width * .43,
        size.height * .48,
        size.width * .5,
        size.height * .5,
      )
      ..quadraticBezierTo(
        size.width * .72,
        size.height * .18,
        size.width * .92,
        size.height * .25,
      );
    canvas.drawPath(routePath, route);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _ChatImagePreviewPage extends StatefulWidget {
  const _ChatImagePreviewPage({required this.imageUrl});
  final String imageUrl;

  @override
  State<_ChatImagePreviewPage> createState() => _ChatImagePreviewPageState();
}

class _ChatImagePreviewPageState extends State<_ChatImagePreviewPage> {
  static const mediaChannel = MethodChannel('com.tongluxing/media');
  bool saving = false;

  Future<void> _save() async {
    if (saving) return;
    setState(() => saving = true);
    try {
      final response = await http.get(Uri.parse(widget.imageUrl));
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw Exception('图片下载失败');
      }
      final contentType = response.headers['content-type'] ?? 'image/jpeg';
      await mediaChannel.invokeMethod<String>('saveImageToGallery', {
        'bytes': response.bodyBytes,
        'mimeType': contentType,
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('图片已保存到相册 Pictures/Tongluxing')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('保存失败：$e')));
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Colors.black,
    appBar: AppBar(
      backgroundColor: Colors.black,
      foregroundColor: Colors.white,
      title: const Text('查看图片'),
      actions: [
        IconButton(
          tooltip: '保存到相册',
          onPressed: saving ? null : _save,
          icon: saving
              ? const SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(LucideIcons.download),
        ),
      ],
    ),
    body: Center(
      child: InteractiveViewer(
        minScale: 0.8,
        maxScale: 5,
        child: Image.network(
          widget.imageUrl,
          fit: BoxFit.contain,
          loadingBuilder: (_, child, progress) =>
              progress == null ? child : const CircularProgressIndicator(),
          errorBuilder: (_, _, _) =>
              const Text('图片加载失败', style: TextStyle(color: Colors.white)),
        ),
      ),
    ),
  );
}
