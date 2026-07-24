import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/follow_service.dart';
import '../../profile/pages/profile_system_pages.dart';
import '../../profile/widgets/user_avatar.dart';
import '../private_chat_flow.dart';
import 'chat_session_page.dart';

class InteractionMessagesPage extends StatefulWidget {
  const InteractionMessagesPage({super.key});

  @override
  State<InteractionMessagesPage> createState() => _InteractionMessagesPageState();
}

class _InteractionMessagesPageState extends State<InteractionMessagesPage>
    with SingleTickerProviderStateMixin {
  late final TabController tabs = TabController(length: 2, vsync: this);
  List<Map<String, dynamic>> applications = const [];
  List<Map<String, dynamic>> followers = const [];
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  @override
  void dispose() {
    tabs.dispose();
    super.dispose();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final session = context.read<AppSession>();
      final userId = session.userId?.toString() ?? '';
      final values = await Future.wait<dynamic>([
        ChatService(session.api).joinApplications(status: 'PENDING'),
        if (userId.isNotEmpty)
          FollowService(session.api).followers(userId, size: 50)
        else
          Future<List<Map<String, dynamic>>>.value(const []),
      ]);
      if (!mounted) return;
      setState(() {
        applications = List<Map<String, dynamic>>.from(values[0] as List);
        followers = List<Map<String, dynamic>>.from(values[1] as List);
      });
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> review(Map<String, dynamic> row, bool approve) async {
    try {
      await ChatService(context.read<AppSession>().api).reviewJoinApplication(
        row['applicationId']?.toString() ?? '',
        approve ? 'APPROVED' : 'REJECTED',
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(approve ? '已同意入队申请' : '已拒绝入队申请')),
        );
        await load();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  Future<void> toggleApplicationFollow(int index) async {
    final row = applications[index];
    final userId = row['applicantUserId']?.toString() ?? '';
    if (userId.isEmpty) return;
    try {
      final service = FollowService(context.read<AppSession>().api);
      final next = row['following'] == true
          ? await service.unfollow(userId)
          : await service.follow(userId);
      if (mounted) setState(() => applications[index] = {...row, ...next});
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  Future<void> toggleFollow(int index) async {
    final row = followers[index];
    final userId = row['userId']?.toString() ?? '';
    try {
      final service = FollowService(context.read<AppSession>().api);
      final next = row['following'] == true
          ? await service.unfollow(userId)
          : await service.follow(userId);
      if (mounted) setState(() => followers[index] = {...row, ...next});
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  Future<void> openChat(String userId) async {
    final conversation = await startPrivateChatFlow(context, userId);
    if (conversation == null || !mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ChatSessionPage(conversation: conversation),
      ),
    );
  }

  void openProfile(String userId) => Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => PublicProfilePage(userId: userId)),
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('互动消息'),
      bottom: TabBar(
        controller: tabs,
        indicatorSize: TabBarIndicatorSize.label,
        tabs: [
          Tab(text: '入队申请${applications.isEmpty ? '' : ' (${applications.length})'}'),
          Tab(text: '谁关注了我${followers.isEmpty ? '' : ' (${followers.length})'}'),
        ],
      ),
    ),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : error != null
            ? Center(
                child: FilledButton.icon(
                  onPressed: load,
                  icon: const Icon(LucideIcons.refreshCw, size: 17),
                  label: const Text('重新加载'),
                ),
              )
            : TabBarView(
                controller: tabs,
                children: [
                  _ApplicationList(
                    rows: applications,
                    onRefresh: load,
                    onFollow: toggleApplicationFollow,
                    onReview: review,
                    onProfile: openProfile,
                    onChat: openChat,
                  ),
                  _FollowerList(
                    rows: followers,
                    onFollow: toggleFollow,
                    onProfile: openProfile,
                    onChat: openChat,
                  ),
                ],
              ),
  );
}

class _ApplicationList extends StatelessWidget {
  const _ApplicationList({
    required this.rows,
    required this.onRefresh,
    required this.onFollow,
    required this.onReview,
    required this.onProfile,
    required this.onChat,
  });
  final List<Map<String, dynamic>> rows;
  final Future<void> Function() onRefresh;
  final ValueChanged<int> onFollow;
  final void Function(Map<String, dynamic>, bool) onReview;
  final ValueChanged<String> onProfile;
  final ValueChanged<String> onChat;

  @override
  Widget build(BuildContext context) {
    if (rows.isEmpty) return const _EmptyInteraction(text: '暂时没有待处理的入队申请');
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 24),
        itemCount: rows.length,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (_, index) {
          final row = rows[index];
          final userId = row['applicantUserId']?.toString() ?? '';
          final nickname = row['nickname']?.toString() ?? '同路行用户';
          return Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              children: [
                Row(
                  children: [
                    UserAvatar(
                      nickname: nickname,
                      avatarImageKey: row['avatarImageKey']?.toString() ?? '',
                      radius: 22,
                      onTap: () => onProfile(userId),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(nickname, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w900)),
                          const SizedBox(height: 3),
                          Text(
                            '申请加入 ${row['conversationName'] ?? '行程群'}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontSize: 12, color: AppColors.muted),
                          ),
                          if (row['applicationMessage']?.toString().trim().isNotEmpty == true)
                            Text(
                              row['applicationMessage'].toString(),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontSize: 12, color: AppColors.secondaryText),
                            ),
                        ],
                      ),
                    ),
                    Text(
                      _compactDate(row['createdAt']?.toString() ?? ''),
                      style: const TextStyle(fontSize: 11, color: AppColors.muted),
                    ),
                  ],
                ),
                const SizedBox(height: 9),
                Row(
                  children: [
                    Expanded(
                      child: SizedBox(
                        height: 31,
                        child: row['following'] == true
                            ? OutlinedButton(
                                onPressed: () => onFollow(index),
                                child: Text(row['mutual'] == true ? '互相关注' : '已关注'),
                              )
                            : FilledButton(
                                onPressed: () => onFollow(index),
                                child: Text(row['followedByTarget'] == true ? '回关' : '关注'),
                              ),
                      ),
                    ),
                    const SizedBox(width: 7),
                    Expanded(
                      child: SizedBox(
                        height: 31,
                        child: OutlinedButton(
                          onPressed: () => onChat(userId),
                          child: const Text('发起私聊'),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 7),
                Row(
                  children: [
                    Expanded(
                      child: SizedBox(
                        height: 31,
                        child: OutlinedButton(
                          onPressed: () => onReview(row, false),
                          child: const Text('拒绝'),
                        ),
                      ),
                    ),
                    const SizedBox(width: 7),
                    Expanded(
                      child: SizedBox(
                        height: 31,
                        child: FilledButton(
                          onPressed: () => onReview(row, true),
                          child: const Text('同意入队'),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _FollowerList extends StatelessWidget {
  const _FollowerList({
    required this.rows,
    required this.onFollow,
    required this.onProfile,
    required this.onChat,
  });
  final List<Map<String, dynamic>> rows;
  final ValueChanged<int> onFollow;
  final ValueChanged<String> onProfile;
  final ValueChanged<String> onChat;

  @override
  Widget build(BuildContext context) {
    if (rows.isEmpty) return const _EmptyInteraction(text: '暂时没有新的关注');
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
      itemCount: rows.length,
      separatorBuilder: (_, _) => const Divider(height: 1, indent: 66),
      itemBuilder: (_, index) {
        final row = rows[index];
        final userId = row['userId']?.toString() ?? '';
        final nickname = row['nickname']?.toString() ?? '同路行用户';
        final mutual = row['mutual'] == true;
        return SizedBox(
          height: 76,
          child: Row(
            children: [
              UserAvatar(
                nickname: nickname,
                avatarImageKey: row['avatarImageKey']?.toString() ?? '',
                radius: 22,
                onTap: () => onProfile(userId),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: InkWell(
                  onTap: () => onProfile(userId),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              nickname,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800),
                            ),
                          ),
                          if (mutual) ...[
                            const SizedBox(width: 5),
                            const Text(
                              '互相关注',
                              style: TextStyle(fontSize: 10, color: AppColors.primary),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 3),
                      Text(
                        '${_compactDate(row['followedAt']?.toString() ?? '')} 关注了你',
                        style: const TextStyle(fontSize: 12, color: AppColors.muted),
                      ),
                    ],
                  ),
                ),
              ),
              SizedBox(
                height: 31,
                child: mutual
                    ? OutlinedButton(
                        onPressed: () => onFollow(index),
                        child: const Text('互相关注'),
                      )
                    : FilledButton(
                        onPressed: () => onFollow(index),
                        child: const Text('回关'),
                      ),
              ),
              const SizedBox(width: 6),
              SizedBox(
                height: 31,
                child: OutlinedButton(
                  onPressed: () => onChat(userId),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 9),
                  ),
                  child: const Icon(LucideIcons.messageCircle, size: 17),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _EmptyInteraction extends StatelessWidget {
  const _EmptyInteraction({required this.text});
  final String text;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(LucideIcons.bell, size: 42, color: AppColors.muted),
        const SizedBox(height: 10),
        Text(text, style: const TextStyle(color: AppColors.muted)),
      ],
    ),
  );
}

String _compactDate(String raw) {
  final date = DateTime.tryParse(raw)?.toLocal();
  if (date == null) return '';
  return '${date.month}月${date.day}日';
}
