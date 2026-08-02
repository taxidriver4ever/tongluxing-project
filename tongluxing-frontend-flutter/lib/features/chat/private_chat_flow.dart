import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../app/app_session.dart';
import '../../data/models/app_models.dart';
import '../../data/services/app_services.dart';
import '../../data/services/follow_service.dart';

/// 统一处理“发起私聊”规则：未关注时提示关注，已有会话直接复用。
Future<ConversationModel?> startPrivateChatFlow(
  BuildContext context,
  String targetUserId,
) async {
  final api = context.read<AppSession>().api;
  final chat = ChatService(api);
  try {
    var permission = await chat.privatePermission(targetUserId);
    if (permission['canStart'] != true) {
      if (!context.mounted) return null;
      final followAndStart = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('关注后发起私聊'),
          content: const Text('关注对方后，可以先发送 3 条文字消息；对方回复或回关后将解除限制。'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('关注并发起私聊'),
            ),
          ],
        ),
      );
      if (followAndStart != true || !context.mounted) return null;
      await FollowService(api).follow(targetUserId);
      permission = await chat.privatePermission(targetUserId);
    }
    if (permission['canStart'] != true) {
      throw StateError(permission['reason']?.toString() ?? '暂时无法发起私聊');
    }
    return await chat.startPrivate(targetUserId);
  } catch (error) {
    if (context.mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    }
    return null;
  }
}
