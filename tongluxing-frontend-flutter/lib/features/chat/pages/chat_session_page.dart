import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
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
  final input = TextEditingController();
  List<Map<String, dynamic>> messages = [];
  bool loading = true;
  bool sending = false;
  Timer? pollingTimer;
  @override
  void initState() {
    super.initState();
    load();
    pollingTimer = Timer.periodic(
      const Duration(seconds: 2),
      (_) => load(silent: true),
    );
  }

  @override
  void dispose() {
    input.dispose();
    pollingTimer?.cancel();
    super.dispose();
  }

  Future<void> load({bool silent = false}) async {
    try {
      messages = await ChatService(
        context.read<AppSession>().api,
      ).messages(widget.conversation.id);
    } catch (_) {}
    if (mounted) setState(() => loading = false);
  }

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
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
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
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) =>
                  ChatGroupDetailsPage(conversation: widget.conversation),
            ),
          ),
          icon: const Icon(LucideIcons.ellipsis),
        ),
      ],
    ),
    body: Column(
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 9),
          color: const Color(0xFFFFF7E8),
          child: const Text(
            '请勿发送验证码、转账或违法信息；高风险内容将被拦截',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12, color: Color(0xFF8B5A00)),
          ),
        ),
        Expanded(
          child: loading
              ? const Center(child: CircularProgressIndicator())
              : messages.isEmpty
              ? const Center(child: Text('暂无消息，和车队成员打个招呼吧'))
              : ListView.builder(
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
                  padding: const EdgeInsets.all(16),
                  itemCount: messages.length,
                  itemBuilder: (c, i) {
                    final m = messages[messages.length - 1 - i];
                    final self =
                        m['isMine'] == true ||
                        m['senderUserId']?.toString() ==
                            context.read<AppSession>().userId;
                    final content =
                        m['content']?.toString() ??
                        m['messageContent']?.toString() ??
                        '';
                    final system = m['messageType'] == 'SYSTEM';
                    if (system) {
                      return Center(
                        child: Container(
                          margin: const EdgeInsets.only(bottom: 14),
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 7,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0xFFEFF2F7),
                            borderRadius: BorderRadius.circular(14),
                          ),
                          child: Text(
                            content,
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.muted,
                            ),
                          ),
                        ),
                      );
                    }
                    return Align(
                      alignment: self
                          ? Alignment.centerRight
                          : Alignment.centerLeft,
                      child: Container(
                        margin: const EdgeInsets.only(bottom: 14),
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 12,
                        ),
                        constraints: const BoxConstraints(maxWidth: 280),
                        decoration: BoxDecoration(
                          color: self ? AppColors.primary : Colors.white,
                          borderRadius: BorderRadius.circular(18),
                        ),
                        child: Text(
                          content,
                          style: TextStyle(
                            color: self ? Colors.white : AppColors.text,
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
        SafeArea(
          top: false,
          child: Container(
            padding: const EdgeInsets.all(12),
            color: Colors.white,
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: input,
                    decoration: const InputDecoration(hintText: '输入消息...'),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton.filled(
                  onPressed: sending ? null : send,
                  icon: sending
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(LucideIcons.send),
                ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}
