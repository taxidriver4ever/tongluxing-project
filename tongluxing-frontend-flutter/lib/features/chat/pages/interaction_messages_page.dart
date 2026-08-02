import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/utils/display_text.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/follow_service.dart';
import '../../profile/pages/profile_system_pages.dart';
import '../../profile/widgets/user_avatar.dart';
import '../private_chat_flow.dart';
import 'chat_session_page.dart';

class InteractionMessagesPage extends StatefulWidget {
  const InteractionMessagesPage({super.key});

  @override
  State<InteractionMessagesPage> createState() =>
      _InteractionMessagesPageState();
}

class _InteractionMessagesPageState extends State<InteractionMessagesPage>
    with SingleTickerProviderStateMixin {
  late final TabController tabs = TabController(length: 2, vsync: this);
  List<Map<String, dynamic>> applications = const [];
  List<Map<String, dynamic>> followers = const [];
  bool applicationsLoading = true;
  bool followersLoading = true;
  bool followersMarkedRead = false;
  int selectedTab = 0;
  String? applicationsError;
  String? followersError;

  @override
  void initState() {
    super.initState();
    tabs.addListener(_handleTabChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  @override
  void dispose() {
    tabs.removeListener(_handleTabChanged);
    tabs.dispose();
    super.dispose();
  }

  void _handleTabChanged() {
    if (!tabs.indexIsChanging && tabs.index != selectedTab) {
      setState(() => selectedTab = tabs.index);
    }
    if (!tabs.indexIsChanging && tabs.index == 1) {
      _markFollowersSeen();
    }
  }

  Future<void> _markFollowersSeen() async {
    if (followersMarkedRead || followersLoading) return;
    followersMarkedRead = true;
    try {
      await FollowService(context.read<AppSession>().api).markFollowersRead();
    } catch (_) {
      followersMarkedRead = false;
    }
  }

  Future<void> load() async {
    await Future.wait([_loadApplications(), _loadFollowers()]);
  }

  Future<void> _loadApplications() async {
    if (mounted) {
      setState(() {
        applicationsLoading = true;
        applicationsError = null;
      });
    }
    try {
      final rows = await ChatService(
        context.read<AppSession>().api,
      ).receivedTeamApplications(status: 'PENDING');
      if (!mounted) return;
      setState(() => applications = rows);
    } catch (error) {
      if (mounted) setState(() => applicationsError = '$error');
    } finally {
      if (mounted) setState(() => applicationsLoading = false);
    }
  }

  Future<void> _loadFollowers() async {
    if (mounted) {
      setState(() {
        followersLoading = true;
        followersError = null;
      });
    }
    try {
      // 使用 /me/followers，避免本地 userId 尚未恢复或类型转换异常时列表变空。
      final rows = await FollowService(
        context.read<AppSession>().api,
      ).myFollowers(size: 50);
      if (!mounted) return;
      setState(() => followers = rows);
    } catch (error) {
      if (mounted) setState(() => followersError = '$error');
    } finally {
      if (mounted) {
        setState(() => followersLoading = false);
        if (tabs.index == 1) _markFollowersSeen();
      }
    }
  }

  Future<void> review(Map<String, dynamic> row, bool approve) async {
    try {
      await ChatService(context.read<AppSession>().api).reviewTeamApplication(
        row['applicationId']?.toString() ?? '',
        approve ? 'APPROVED' : 'REJECTED',
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(approve ? '已同意入队申请' : '已拒绝入队申请')),
        );
        await _loadApplications();
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> toggleFollow(int index) async {
    final row = followers[index];
    final userId = row['userId']?.toString() ?? '';
    if (userId.isEmpty) return;
    try {
      final service = FollowService(context.read<AppSession>().api);
      final next = row['following'] == true
          ? await service.unfollow(userId)
          : await service.follow(userId);
      if (mounted) setState(() => followers[index] = {...row, ...next});
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> openChat(String userId) async {
    if (userId.isEmpty) return;
    final conversation = await startPrivateChatFlow(context, userId);
    if (conversation == null || !mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ChatSessionPage(conversation: conversation),
      ),
    );
  }

  void openProfile(String userId) {
    if (userId.isEmpty) return;
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => PublicProfilePage(userId: userId)),
    );
  }

  Widget _tabBody({
    required bool loading,
    required String? error,
    required Future<void> Function() onRetry,
    required Widget child,
  }) {
    if (loading) return const Center(child: CircularProgressIndicator());
    if (error != null) {
      return _InteractionLoadError(message: error, onRetry: onRetry);
    }
    return child;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('互动消息'),
      bottom: TabBar(
        controller: tabs,
        indicatorSize: TabBarIndicatorSize.label,
        onTap: (index) {
          if (selectedTab != index) setState(() => selectedTab = index);
          if (index == 1) _markFollowersSeen();
        },
        tabs: [
          Tab(
            text:
                '入队申请${applications.isEmpty ? '' : ' (${applications.length})'}',
          ),
          Tab(
            text: '谁关注了我${followers.isEmpty ? '' : ' (${followers.length})'}',
          ),
        ],
      ),
    ),
    // 部分 Android 设备上 TabBarView 与页面里的原生地图 PlatformView
    // 组合后会出现标签已切换但第二页没有合成到画面的情况。这里根据
    // TabController 直接重建当前页，确保“谁关注了我”的真实数据可见。
    body: selectedTab == 0
        ? _tabBody(
            loading: applicationsLoading,
            error: applicationsError,
            onRetry: _loadApplications,
            child: _ApplicationList(
              rows: applications,
              onRefresh: _loadApplications,
              onReview: review,
              onProfile: openProfile,
            ),
          )
        : _tabBody(
            loading: followersLoading,
            error: followersError,
            onRetry: _loadFollowers,
            child: _FollowerList(
              rows: followers,
              onRefresh: _loadFollowers,
              onFollow: toggleFollow,
              onProfile: openProfile,
              onChat: openChat,
            ),
          ),
  );
}

class _InteractionLoadError extends StatelessWidget {
  const _InteractionLoadError({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(LucideIcons.circleAlert, size: 38, color: AppColors.muted),
          const SizedBox(height: 10),
          Text(
            message,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.secondaryText),
          ),
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed: onRetry,
            icon: const Icon(LucideIcons.refreshCw, size: 17),
            label: const Text('重新加载'),
          ),
        ],
      ),
    ),
  );
}

class _ApplicationList extends StatelessWidget {
  const _ApplicationList({
    required this.rows,
    required this.onRefresh,
    required this.onReview,
    required this.onProfile,
  });
  final List<Map<String, dynamic>> rows;
  final Future<void> Function() onRefresh;
  final void Function(Map<String, dynamic>, bool) onReview;
  final ValueChanged<String> onProfile;

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
                          Text(
                            compactDisplayName(nickname),
                            style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            '申请加入 ${row['conversationName'] ?? '行程群'}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.muted,
                            ),
                          ),
                          if (row['wantsToDrive'] == true)
                            Padding(
                              padding: const EdgeInsets.only(top: 4),
                              child: Row(
                                children: [
                                  const Icon(
                                    LucideIcons.carFront,
                                    size: 14,
                                    color: AppColors.primary,
                                  ),
                                  const SizedBox(width: 4),
                                  Expanded(
                                    child: Text(
                                      '要开车${row['vehicleSummary']?.toString().trim().isNotEmpty == true ? ' · ${row['vehicleSummary']}' : ''}',
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(
                                        fontSize: 12,
                                        color: AppColors.primary,
                                        fontWeight: FontWeight.w800,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          if (row['applyMessage']
                                  ?.toString()
                                  .trim()
                                  .isNotEmpty ==
                              true)
                            Text(
                              row['applyMessage'].toString(),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 12,
                                color: AppColors.secondaryText,
                              ),
                            ),
                        ],
                      ),
                    ),
                    Text(
                      _compactDate(row['createdAt']?.toString() ?? ''),
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.muted,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
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
    required this.onRefresh,
    required this.onFollow,
    required this.onProfile,
    required this.onChat,
  });

  final List<Map<String, dynamic>> rows;
  final Future<void> Function() onRefresh;
  final ValueChanged<int> onFollow;
  final ValueChanged<String> onProfile;
  final ValueChanged<String> onChat;

  @override
  Widget build(BuildContext context) {
    if (rows.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: const CustomScrollView(
          physics: AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverFillRemaining(
              hasScrollBody: false,
              child: _EmptyInteraction(text: '暂时没有新的关注'),
            ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 24),
        itemCount: rows.length,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (_, index) {
          final row = rows[index];
          final userId = row['userId']?.toString() ?? '';
          final nickname = row['nickname']?.toString() ?? '同路行用户';
          final mutual = row['mutual'] == true;
          final avatarKey = row['avatarImageKey']?.toString() ?? '';
          return Material(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () => onProfile(userId),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 11, 8, 11),
                child: Row(
                  children: [
                    UserAvatar(
                      nickname: nickname,
                      avatarImageKey: avatarKey,
                      radius: 22,
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            compactDisplayName(nickname),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            '${_compactDate(row['followedAt']?.toString() ?? '')} 关注了你${mutual ? ' · 互相关注' : ''}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.muted,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 6),
                    TextButton(
                      onPressed: () => onFollow(index),
                      child: Text(mutual ? '已互关' : '回关'),
                    ),
                    IconButton(
                      tooltip: '发起私聊',
                      onPressed: () => onChat(userId),
                      icon: const Icon(
                        LucideIcons.messageCircle,
                        size: 19,
                        color: AppColors.primary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
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
