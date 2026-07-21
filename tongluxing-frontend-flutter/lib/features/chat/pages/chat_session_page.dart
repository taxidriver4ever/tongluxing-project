import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:http/http.dart' as http;
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import 'chat_management_pages.dart';

class ChatSessionPage extends StatefulWidget {
  const ChatSessionPage({required this.conversation, super.key});
  final ConversationModel conversation;
  @override
  State<ChatSessionPage> createState() => _ChatSessionPageState();
}

class _ChatSessionPageState extends State<ChatSessionPage> {
  static const permissionChannel = MethodChannel('com.tongluxing/permissions');
  static const mediaChannel = MethodChannel('com.tongluxing/media');
  final input = TextEditingController();
  final scroll = ScrollController();
  final focus = FocusNode();
  List<Map<String, dynamic>> messages = [];
  Map<String, dynamic> workspace = {};
  final Map<String, Future<String>> attachmentUrls = {};
  bool loading = true, sending = false, announcementHidden = false;
  bool toolsExpanded = false;
  Timer? pollingTimer;

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
      final values = await Future.wait([
        service.messages(widget.conversation.id),
        service.groupWorkspace(widget.conversation.id),
      ]);
      messages = (values[0] as List<Map<String, dynamic>>).reversed.toList();
      workspace = values[1] as Map<String, dynamic>;
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
    if (announcementHidden) return null;
    final items = (workspace['items'] as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .where(
          (e) => e['itemType'] == 'ANNOUNCEMENT' && e['itemStatus'] == 'ACTIVE',
        );
    return items.isEmpty ? null : items.first;
  }

  @override
  Widget build(BuildContext context) {
    final announcement = _announcement;
    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.conversation.name),
            Text(
              widget.conversation.providerType == 'TENCENT_IM'
                  ? '腾讯 IM 实时通道'
                  : '行程群聊 · 本地实时同步',
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.muted,
                fontWeight: FontWeight.w400,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: '群聊详情',
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) =>
                      ChatGroupDetailsPage(conversation: widget.conversation),
                ),
              );
              await load();
            },
            icon: const Icon(LucideIcons.ellipsis),
          ),
        ],
      ),
      body: Column(
        children: [
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
                ? const Center(child: Text('暂无消息，和车队成员打个招呼吧'))
                : ListView.builder(
                    controller: scroll,
                    reverse: false,
                    keyboardDismissBehavior:
                        ScrollViewKeyboardDismissBehavior.onDrag,
                    padding: const EdgeInsets.fromLTRB(14, 14, 14, 8),
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
                  padding: const EdgeInsets.fromLTRB(8, 9, 10, 9),
                  color: Colors.white,
                  child: Row(
                    children: [
                      IconButton(
                        tooltip: toolsExpanded ? '收起' : '更多',
                        onPressed: _more,
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
                          onTap: _showLatest,
                          minLines: 1,
                          maxLines: 4,
                          decoration: const InputDecoration(
                            hintText: '输入消息...',
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      IconButton.filled(
                        onPressed: sending ? null : send,
                        icon: sending
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(LucideIcons.send),
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
          margin: const EdgeInsets.only(bottom: 14),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
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
    final card = type.endsWith('_CARD');
    final maxBubbleWidth = MediaQuery.sizeOf(context).width - 122;
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Row(
        mainAxisAlignment: self
            ? MainAxisAlignment.end
            : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!self) _avatar(message),
          if (!self) const SizedBox(width: 9),
          Flexible(
            child: Column(
              crossAxisAlignment: self
                  ? CrossAxisAlignment.end
                  : CrossAxisAlignment.start,
              children: [
                if (!self)
                  Padding(
                    padding: const EdgeInsets.only(left: 4, bottom: 5),
                    child: Text(
                      message['senderNickname']?.toString() ?? '同路行用户',
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.muted,
                      ),
                    ),
                  ),
                GestureDetector(
                  onTap: card
                      ? () => _openCard(type, payload)
                      : type == 'IMAGE' || type == 'FILE'
                      ? () => _openAttachment(payload)
                      : type == 'LOCATION'
                      ? () => _showLocation(payload)
                      : null,
                  child: Container(
                    constraints: BoxConstraints(maxWidth: maxBubbleWidth),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 15,
                      vertical: 12,
                    ),
                    decoration: BoxDecoration(
                      color: card
                          ? Colors.white
                          : self
                          ? AppColors.primary
                          : Colors.white,
                      borderRadius: BorderRadius.circular(18),
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
          if (self) const SizedBox(width: 9),
          if (self) _avatar(message),
        ],
      ),
    );
  }

  Widget _avatar(Map<String, dynamic> message) {
    final avatarUrl = message['senderAvatarUrl']?.toString().trim() ?? '';
    final nickname = message['senderNickname']?.toString() ?? '同路行用户';
    return CircleAvatar(
      radius: 19,
      backgroundColor: AppColors.primarySoft,
      foregroundImage: avatarUrl.isEmpty ? null : NetworkImage(avatarUrl),
      onForegroundImageError: avatarUrl.isEmpty ? null : (_, __) {},
      child: avatarUrl.isEmpty
          ? Text(
              nickname.isNotEmpty ? nickname.characters.first : '同',
              style: const TextStyle(
                color: AppColors.primary,
                fontWeight: FontWeight.w800,
              ),
            )
          : null,
    );
  }

  Widget _messageContent(
    String type,
    String content,
    Map<String, dynamic> payload,
    bool self,
  ) {
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
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            LucideIcons.mapPin,
            color: self ? Colors.white : AppColors.primary,
          ),
          const SizedBox(width: 9),
          Flexible(child: Text(content, style: _bubbleText(self))),
        ],
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
                  errorBuilder: (_, __, ___) => const SizedBox(
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

  TextStyle _bubbleText(bool self) =>
      TextStyle(color: self ? Colors.white : AppColors.text, height: 1.45);

  void _more() {
    focus.unfocus();
    setState(() => toolsExpanded = !toolsExpanded);
    if (toolsExpanded) _showLatest();
  }

  Widget _toolPanel() {
    final manager = const ['OWNER', 'ADMIN'].contains(workspace['selfRole']);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(18, 18, 18, 22),
      decoration: const BoxDecoration(
        color: Color(0xFFF5F7FB),
        border: Border(top: BorderSide(color: Color(0xFFE8ECF3))),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '聊天工具',
            style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: _action(LucideIcons.image, '发送图片', '从相册选择', _pickImage),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _action(
                  LucideIcons.mapPin,
                  '当前位置',
                  '分享给车队',
                  _sendLocation,
                ),
              ),
              if (manager) ...[
                const SizedBox(width: 12),
                Expanded(
                  child: _action(
                    LucideIcons.listChecks,
                    '群投票',
                    '发起共同决策',
                    () => _openManagement('POLL', '群投票'),
                  ),
                ),
              ],
            ],
          ),
          if (manager) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _action(
                    LucideIcons.alarmClock,
                    '行程提醒',
                    '发送提醒卡片',
                    () => _openManagement('REMINDER', '行程提醒'),
                  ),
                ),
                const Expanded(flex: 2, child: SizedBox()),
              ],
            ),
          ],
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
    borderRadius: BorderRadius.circular(18),
    onTap: () async {
      setState(() => toolsExpanded = false);
      await action();
    },
    child: Container(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 50,
            height: 50,
            decoration: BoxDecoration(
              color: AppColors.primarySoft,
              borderRadius: BorderRadius.circular(18),
            ),
            child: Icon(icon, color: AppColors.primary),
          ),
          const SizedBox(height: 9),
          Text(
            label,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800),
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
    final api = context.read<AppSession>().api;
    final granted =
        await permissionChannel.invokeMethod<bool>('requestLocation') ?? false;
    if (!granted || !mounted) return;
    final location = Map<String, dynamic>.from(
      await permissionChannel.invokeMethod<Map>('getCurrentLocation') ??
          const {},
    );
    await ChatService(api).send(
      widget.conversation.id,
      '我的当前位置',
      type: 'LOCATION',
      payload: location,
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
      MaterialPageRoute(
        builder: (_) => _ChatImagePreviewPage(imageUrl: url),
      ),
    );
  }

  Future<void> _showLocation(Map<String, dynamic> payload) => showDialog<void>(
    context: context,
    builder: (c) => AlertDialog(
      title: const Text('成员共享的位置'),
      content: Text(
        '纬度：${payload['latitude'] ?? '--'}\n经度：${payload['longitude'] ?? '--'}',
      ),
      actions: [
        FilledButton(
          onPressed: () => Navigator.pop(c),
          child: const Text('知道了'),
        ),
      ],
    ),
  );

  Future<void> _openManagement(String type, String title) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ChatItemsPage(
          conversation: widget.conversation,
          type: type,
          title: title,
        ),
      ),
    );
    await load();
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
                    leading: const CircleAvatar(
                      child: Icon(LucideIcons.userRound),
                    ),
                    title: Text(entry['nickname']?.toString() ?? '同路行用户'),
                    subtitle: Text(entry['memberRole']?.toString() ?? 'MEMBER'),
                    trailing: Text(_status(entry['status']?.toString())),
                  ),
                if (row!['confirmationStatus'] == 'OPEN' && !owner) ...[
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
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('保存失败：$e')),
        );
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
          loadingBuilder: (_, child, progress) => progress == null
              ? child
              : const CircularProgressIndicator(),
          errorBuilder: (_, __, ___) => const Text(
            '图片加载失败',
            style: TextStyle(color: Colors.white),
          ),
        ),
      ),
    ),
  );
}
