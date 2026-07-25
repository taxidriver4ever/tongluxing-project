import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/services/follow_service.dart';
import '../../chat/private_chat_flow.dart';
import '../../chat/pages/chat_session_page.dart';
import '../widgets/user_avatar.dart';
import 'profile_system_pages.dart';

/// 当前用户的关注关系总览：我关注的、关注我的、互相关注。
class FollowRelationsPage extends StatefulWidget {
  const FollowRelationsPage({this.initialIndex = 0, super.key});

  final int initialIndex;

  @override
  State<FollowRelationsPage> createState() => _FollowRelationsPageState();
}

class _FollowRelationsPageState extends State<FollowRelationsPage>
    with SingleTickerProviderStateMixin {
  late final TabController tabs = TabController(
    length: 3,
    initialIndex: widget.initialIndex.clamp(0, 2).toInt(),
    vsync: this,
  );

  List<Map<String, dynamic>> following = const [];
  List<Map<String, dynamic>> followers = const [];
  List<Map<String, dynamic>> mutual = const [];

  bool followingLoading = true;
  bool followersLoading = true;
  bool mutualLoading = true;
  String? followingError;
  String? followersError;
  String? mutualError;

  FollowService get _service => FollowService(context.read<AppSession>().api);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadAll());
  }

  @override
  void dispose() {
    tabs.dispose();
    super.dispose();
  }

  Future<void> _loadAll() async {
    if (!mounted) return;
    await Future.wait([
      _loadFollowing(),
      _loadFollowers(),
      _loadMutual(),
    ]);
  }

  Future<void> _loadFollowing() async {
    if (!mounted) return;
    setState(() {
      followingLoading = true;
      followingError = null;
    });
    try {
      final rows = await _service.myFollowing(size: 50);
      if (mounted) setState(() => following = rows);
    } catch (error) {
      if (mounted) setState(() => followingError = '$error');
    } finally {
      if (mounted) setState(() => followingLoading = false);
    }
  }

  Future<void> _loadFollowers() async {
    if (!mounted) return;
    setState(() {
      followersLoading = true;
      followersError = null;
    });
    try {
      final rows = await _service.myFollowers(size: 50);
      if (mounted) setState(() => followers = rows);
    } catch (error) {
      if (mounted) setState(() => followersError = '$error');
    } finally {
      if (mounted) setState(() => followersLoading = false);
    }
  }

  Future<void> _loadMutual() async {
    if (!mounted) return;
    setState(() {
      mutualLoading = true;
      mutualError = null;
    });
    try {
      final rows = await _service.myMutualFollows(size: 50);
      if (mounted) setState(() => mutual = rows);
    } catch (error) {
      if (mounted) setState(() => mutualError = '$error');
    } finally {
      if (mounted) setState(() => mutualLoading = false);
    }
  }

  Future<void> _toggleFollow(Map<String, dynamic> row) async {
    final userId = row['userId']?.toString() ?? '';
    if (userId.isEmpty) return;
    try {
      if (row['following'] == true) {
        await _service.unfollow(userId);
      } else {
        await _service.follow(userId);
      }
      if (!mounted) return;
      await _loadAll();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('$error')),
      );
    }
  }

  void _openProfile(Map<String, dynamic> row) {
    final userId = row['userId']?.toString() ?? '';
    if (userId.isEmpty) return;
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => PublicProfilePage(userId: userId)),
    ).then((_) {
      if (mounted) _loadAll();
    });
  }

  Future<void> _openChat(Map<String, dynamic> row) async {
    final userId = row['userId']?.toString() ?? '';
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

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF5F7FA),
    appBar: AppBar(
      title: const Text('关注与粉丝'),
      bottom: TabBar(
        controller: tabs,
        indicatorSize: TabBarIndicatorSize.label,
        tabs: [
          Tab(text: '我关注的${_countLabel(following, followingLoading)}'),
          Tab(text: '关注我的${_countLabel(followers, followersLoading)}'),
          Tab(text: '互相关注${_countLabel(mutual, mutualLoading)}'),
        ],
      ),
    ),
    body: TabBarView(
      controller: tabs,
      children: [
        _relationBody(
          rows: following,
          loading: followingLoading,
          error: followingError,
          emptyIcon: LucideIcons.userRound,
          emptyText: '暂时还没有关注其他人',
          onRefresh: _loadFollowing,
        ),
        _relationBody(
          rows: followers,
          loading: followersLoading,
          error: followersError,
          emptyIcon: LucideIcons.usersRound,
          emptyText: '暂时还没有人关注你',
          onRefresh: _loadFollowers,
        ),
        _relationBody(
          rows: mutual,
          loading: mutualLoading,
          error: mutualError,
          emptyIcon: LucideIcons.heart,
          emptyText: '互相关注后会显示在这里',
          onRefresh: _loadMutual,
        ),
      ],
    ),
  );

  String _countLabel(List<Map<String, dynamic>> rows, bool loading) =>
      loading ? '' : ' (${rows.length})';

  Widget _relationBody({
    required List<Map<String, dynamic>> rows,
    required bool loading,
    required String? error,
    required IconData emptyIcon,
    required String emptyText,
    required Future<void> Function() onRefresh,
  }) {
    if (loading) return const Center(child: CircularProgressIndicator());
    if (error != null) {
      return _FollowLoadError(message: error, onRetry: onRefresh);
    }
    if (rows.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            SizedBox(height: MediaQuery.sizeOf(context).height * .18),
            Icon(emptyIcon, size: 48, color: AppColors.muted),
            const SizedBox(height: 12),
            Center(
              child: Text(
                emptyText,
                style: const TextStyle(color: AppColors.secondaryText),
              ),
            ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(14, 14, 14, 28),
        itemCount: rows.length,
        separatorBuilder: (_, _) => const SizedBox(height: 10),
        itemBuilder: (context, index) {
          final row = rows[index];
          return _FollowUserCard(
            row: row,
            onProfile: () => _openProfile(row),
            onChat: () => _openChat(row),
            onToggleFollow: () => _toggleFollow(row),
          );
        },
      ),
    );
  }
}

class _FollowUserCard extends StatelessWidget {
  const _FollowUserCard({
    required this.row,
    required this.onProfile,
    required this.onChat,
    required this.onToggleFollow,
  });

  final Map<String, dynamic> row;
  final VoidCallback onProfile;
  final VoidCallback onChat;
  final VoidCallback onToggleFollow;

  @override
  Widget build(BuildContext context) {
    final nickname = row['nickname']?.toString().trim().isNotEmpty == true
        ? row['nickname'].toString().trim()
        : '同路行用户';
    final following = row['following'] == true;
    final followedByTarget = row['followedByTarget'] == true;
    final mutual = row['mutual'] == true;
    final tripCount = (row['totalTripCount'] as num?)?.toInt() ?? 0;
    final distance = (row['totalDistanceMeters'] as num?)?.toInt() ?? 0;

    final followText = mutual
        ? '互相关注'
        : following
        ? '已关注'
        : followedByTarget
        ? '回关'
        : '关注';

    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        onTap: onProfile,
        borderRadius: BorderRadius.circular(18),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 14, 12, 14),
          child: Row(
            children: [
              UserAvatar(
                nickname: nickname,
                avatarImageKey: row['avatarImageKey']?.toString() ?? '',
                radius: 25,
                onTap: onProfile,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            nickname,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontWeight: FontWeight.w800,
                              fontSize: 16,
                            ),
                          ),
                        ),
                        if (row['certificationStatus'] == 'APPROVED') ...[
                          const SizedBox(width: 5),
                          const Icon(
                            LucideIcons.badgeCheck,
                            size: 16,
                            color: AppColors.success,
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '$tripCount 次行程 · ${_distanceText(distance)}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AppColors.secondaryText,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: '发起私聊',
                onPressed: onChat,
                icon: const Icon(LucideIcons.messageCircle, size: 20),
              ),
              const SizedBox(width: 2),
              SizedBox(
                width: 82,
                child: following
                    ? OutlinedButton(
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                        ),
                        onPressed: onToggleFollow,
                        child: FittedBox(
                          fit: BoxFit.scaleDown,
                          child: Text(followText, maxLines: 1),
                        ),
                      )
                    : FilledButton(
                        style: FilledButton.styleFrom(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                        ),
                        onPressed: onToggleFollow,
                        child: FittedBox(
                          fit: BoxFit.scaleDown,
                          child: Text(followText, maxLines: 1),
                        ),
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static String _distanceText(int meters) {
    if (meters < 1000) return '$meters 米';
    return '${(meters / 1000).toStringAsFixed(meters >= 100000 ? 0 : 1)} 公里';
  }
}

class _FollowLoadError extends StatelessWidget {
  const _FollowLoadError({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(
            LucideIcons.circleAlert,
            size: 38,
            color: AppColors.danger,
          ),
          const SizedBox(height: 12),
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.secondaryText),
          ),
          const SizedBox(height: 14),
          OutlinedButton.icon(
            onPressed: onRetry,
            icon: const Icon(LucideIcons.refreshCw, size: 18),
            label: const Text('重新加载'),
          ),
        ],
      ),
    ),
  );
}
