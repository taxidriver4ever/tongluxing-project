import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/follow_service.dart';
import '../../chat/pages/chat_session_page.dart';
import '../../profile/pages/profile_system_pages.dart';
import '../../profile/widgets/user_avatar.dart';
import 'trip_detail_page.dart';
import 'trip_discovery_widgets.dart';

class TripDiscoveryDetailPage extends StatefulWidget {
  const TripDiscoveryDetailPage({
    required this.tripId,
    this.initial,
    super.key,
  });
  final String tripId;
  final TripDiscoverModel? initial;
  @override
  State<TripDiscoveryDetailPage> createState() =>
      _TripDiscoveryDetailPageState();
}

class _TripDiscoveryDetailPageState extends State<TripDiscoveryDetailPage> {
  TripPublicDetailModel? detail;
  bool loading = true;
  bool actionBusy = false;
  bool followed = false;
  bool saved = false;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      detail = await TripDiscoveryService(
        context.read<AppSession>().api,
      ).publicDetail(widget.tripId);
      followed = detail!.owner.followed;
      saved = detail!.favorited;
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> toggleFavorite() async {
    if (actionBusy) return;
    setState(() => actionBusy = true);
    try {
      final service = TripDiscoveryService(context.read<AppSession>().api);
      final next = saved
          ? await service.unfavorite(widget.tripId)
          : await service.favorite(widget.tripId);
      if (!mounted) return;
      setState(() => saved = next);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(saved ? '已收藏行程' : '已取消收藏')));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => actionBusy = false);
    }
  }

  Future<void> toggleFollow() async {
    if (actionBusy || detail == null) return;
    setState(() => actionBusy = true);
    try {
      final service = FollowService(context.read<AppSession>().api);
      final result = followed
          ? await service.unfollow(detail!.owner.userId)
          : await service.follow(detail!.owner.userId);
      if (!mounted) return;
      setState(() => followed = result['following'] == true);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(followed ? '已关注发起人' : '已取消关注')));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => actionBusy = false);
    }
  }

  Future<void> apply() async {
    if (actionBusy || detail == null) return;
    final request = await showModalBottomSheet<_ApplyRequest>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _ApplySheet(trip: detail!.trip),
    );
    if (request == null || !mounted) return;
    setState(() => actionBusy = true);
    try {
      await TripDiscoveryService(context.read<AppSession>().api).apply(
        widget.tripId,
        message: request.message,
        selfDrive: request.selfDrive,
        companionCount: request.companionCount,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('申请已提交，请等待队长审核')));
      await load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => actionBusy = false);
    }
  }

  Future<void> openChat() async {
    setState(() => actionBusy = true);
    try {
      final conversation = await ChatService(
        context.read<AppSession>().api,
      ).tripConversation(widget.tripId);
      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => ChatSessionPage(conversation: conversation),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => actionBusy = false);
    }
  }

  Future<void> consult() async {
    if (detail?.allowConsultation != true) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('陌生用户请先关注发起人，或提交加入申请')));
      return;
    }
    final controller = TextEditingController();
    final content = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('咨询队长'),
        content: TextField(
          controller: controller,
          autofocus: true,
          minLines: 3,
          maxLines: 5,
          maxLength: 500,
          decoration: const InputDecoration(
            hintText: '说明你想咨询的集合、路线或车辆问题',
            contentPadding: EdgeInsets.fromLTRB(16, 18, 16, 14),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(
              dialogContext,
              controller.text.trim().isEmpty
                  ? '你好，我想咨询这段行程'
                  : controller.text.trim(),
            ),
            child: const Text('发送咨询'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (content == null || !mounted) return;
    setState(() => actionBusy = true);
    try {
      final result = await TripDiscoveryService(
        context.read<AppSession>().api,
      ).consult(widget.tripId, content: content);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            result['status'] == 'DIRECT_ALLOWED'
                ? '你们已具备直接沟通关系，可在消息中联系队长'
                : '咨询已发送，队长回复后会建立本行程临时会话',
          ),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => actionBusy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final current = detail;
    final fallback = widget.initial;
    final trip = current?.trip ?? fallback;
    return Scaffold(
      backgroundColor: const Color(0xFFF3F7FC),
      body: trip == null && loading
          ? const Center(child: CircularProgressIndicator())
          : trip == null
          ? _Error(onRetry: load, message: error)
          : RefreshIndicator(
              onRefresh: load,
              child: CustomScrollView(
                slivers: [
                  SliverAppBar(
                    pinned: true,
                    expandedHeight: 245,
                    foregroundColor: Colors.white,
                    backgroundColor: AppColors.primaryDark,
                    actions: [
                      IconButton(
                        tooltip: saved ? '取消收藏' : '收藏',
                        onPressed: actionBusy ? null : toggleFavorite,
                        icon: Icon(saved ? LucideIcons.star : LucideIcons.star),
                      ),
                      IconButton(
                        tooltip: '分享',
                        onPressed: () async {
                          await Clipboard.setData(
                            ClipboardData(
                              text: '同路行：${trip.title}（行程 ${trip.tripId}）',
                            ),
                          );
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('行程信息已复制，可分享给好友')),
                            );
                          }
                        },
                        icon: const Icon(LucideIcons.share2),
                      ),
                    ],
                    flexibleSpace: FlexibleSpaceBar(
                      background: Container(
                        padding: const EdgeInsets.fromLTRB(22, 88, 22, 22),
                        decoration: const BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                            colors: [AppColors.primaryDark, Color(0xFF42A5F5)],
                          ),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                _HeaderTag('顺路度 ${trip.matchScore}%'),
                                const Spacer(),
                                _HeaderTag(
                                  trip.status == 'RECRUITING' ? '招募中' : '公开招募',
                                ),
                              ],
                            ),
                            const SizedBox(height: 13),
                            Text(
                              trip.title,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 25,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              [
                                trip.startName,
                                ...trip.waypoints,
                                trip.endName,
                              ].join(' → '),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 10),
                            Text(
                              '${formatDiscoverTime(trip.departureTime)} · 预计${trip.estimatedDays}天 · ${trip.memberCount}/${trip.maxMemberCount}人',
                              style: const TextStyle(
                                color: Colors.white70,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 120),
                    sliver: SliverList.list(
                      children: [
                        RouteSketch(
                          start: trip.startName,
                          waypoints: trip.waypoints,
                          end: trip.endName,
                          routePolyline: current?.routePolyline ?? '',
                        ),
                        const SizedBox(height: 14),
                        if (current == null && loading)
                          const Padding(
                            padding: EdgeInsets.all(32),
                            child: Center(child: CircularProgressIndicator()),
                          )
                        else if (current == null)
                          _Error(onRetry: load, message: error)
                        else ...[
                          _OwnerCard(
                            owner: current.owner,
                            followed: followed,
                            busy: actionBusy,
                            onFollow: toggleFollow,
                            onOpen: () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => PublicProfilePage(
                                  userId: current.owner.userId,
                                ),
                              ),
                            ).then((_) => load()),
                          ),
                          const SizedBox(height: 14),
                          _DetailCard(detail: current),
                          const SizedBox(height: 14),
                          _MembersCard(detail: current),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
      bottomNavigationBar: current == null
          ? null
          : SafeArea(
              child: Container(
                padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
                decoration: const BoxDecoration(
                  color: Colors.white,
                  boxShadow: [
                    BoxShadow(
                      color: Color(0x16000000),
                      blurRadius: 18,
                      offset: Offset(0, -5),
                    ),
                  ],
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: actionBusy ? null : consult,
                        icon: const Icon(LucideIcons.messageCircle, size: 18),
                        label: const Text('咨询队长'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      flex: 2,
                      child: FilledButton.icon(
                        onPressed: actionBusy ? null : _primaryAction(current),
                        icon: actionBusy
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : Icon(_primaryIcon(current)),
                        label: Text(_primaryLabel(current)),
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }

  VoidCallback? _primaryAction(TripPublicDetailModel value) {
    if (value.ownerTrip) {
      return () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => TripDetailPage(tripId: widget.tripId),
        ),
      );
    }
    if (value.trip.relationshipStatus == 'JOINED') return openChat;
    if (value.trip.relationshipStatus == 'PENDING') return null;
    if (!value.allowApply || !value.joinable) return null;
    return apply;
  }

  String _primaryLabel(TripPublicDetailModel value) {
    if (value.ownerTrip) return '进入行程管理';
    return switch (value.trip.relationshipStatus) {
      'JOINED' => '进入群聊',
      'PENDING' => '审核中',
      'REJECTED' => value.allowApply ? '重新申请' : '申请已拒绝',
      _ =>
        value.joinable
            ? '申请加入'
            : value.trip.remainingSeats <= 0
            ? '行程已满'
            : '已停止招募',
    };
  }

  IconData _primaryIcon(TripPublicDetailModel value) =>
      value.trip.relationshipStatus == 'JOINED'
      ? LucideIcons.messageCircle
      : value.ownerTrip
      ? LucideIcons.settings
      : LucideIcons.send;
}

class _OwnerCard extends StatelessWidget {
  const _OwnerCard({
    required this.owner,
    required this.followed,
    required this.busy,
    required this.onFollow,
    required this.onOpen,
  });
  final DiscoverOwnerModel owner;
  final bool followed;
  final bool busy;
  final VoidCallback onFollow;
  final VoidCallback onOpen;
  @override
  Widget build(BuildContext context) => _WhiteCard(
    child: Column(
      children: [
        InkWell(
          onTap: onOpen,
          child: Row(
            children: [
              UserAvatar(
                nickname: owner.nickname,
                avatarImageKey: owner.avatarImageKey,
                radius: 28,
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
                            owner.nickname,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 17,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        const SizedBox(width: 7),
                        Text(
                          owner.levelCode,
                          style: const TextStyle(color: AppColors.primary),
                        ),
                      ],
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '${owner.certificationStatus == 'APPROVED' ? '认证车主' : '公开资料'} · ${activeLabel(owner.lastActiveAt)}',
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.muted,
                      ),
                    ),
                  ],
                ),
              ),
              OutlinedButton(
                onPressed: busy ? null : onFollow,
                child: Text(followed ? '已关注' : '+ 关注'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 18),
        Row(
          children: [
            _OwnerStat('${owner.totalTripCount}', '发起行程'),
            _OwnerStat(
              '${(owner.totalDistanceMeters / 1000).toStringAsFixed(0)}km+',
              '累计里程',
            ),
            _OwnerStat('${owner.badgeCount}', '勋章'),
            _OwnerStat(
              owner.rating <= 0 ? '暂无' : owner.rating.toStringAsFixed(1),
              '评分',
            ),
          ],
        ),
      ],
    ),
  );
}

class _OwnerStat extends StatelessWidget {
  const _OwnerStat(this.value, this.label);
  final String value;
  final String label;
  @override
  Widget build(BuildContext context) => Expanded(
    child: Column(
      children: [
        Text(
          value,
          style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16),
        ),
        const SizedBox(height: 3),
        Text(
          label,
          style: const TextStyle(fontSize: 11, color: AppColors.muted),
        ),
      ],
    ),
  );
}

class _DetailCard extends StatelessWidget {
  const _DetailCard({required this.detail});
  final TripPublicDetailModel detail;
  @override
  Widget build(BuildContext context) {
    final values = <(IconData, String, String)>[
      (LucideIcons.alignLeft, '行程说明', detail.trip.description),
      (LucideIcons.car, '车辆要求', detail.vehicleRequirement),
      (LucideIcons.walletCards, '费用预算', detail.budgetDescription),
      (LucideIcons.handCoins, '分摊方式', detail.costSharingType),
      (LucideIcons.mapPin, '集合地点', detail.meetingPoint),
      (LucideIcons.circleAlert, '注意事项', detail.notes),
      (LucideIcons.userRoundCheck, '加入要求', detail.joinRequirement),
    ];
    return _WhiteCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '行程详情',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 14),
          ...values.map(
            (item) => Padding(
              padding: const EdgeInsets.only(bottom: 13),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(item.$1, size: 18, color: AppColors.primary),
                  const SizedBox(width: 10),
                  SizedBox(
                    width: 66,
                    child: Text(
                      item.$2,
                      style: const TextStyle(color: AppColors.secondaryText),
                    ),
                  ),
                  Expanded(
                    child: Text(
                      item.$3.trim().isEmpty ? '发起人暂未填写' : item.$3,
                      style: TextStyle(
                        color: item.$3.trim().isEmpty
                            ? AppColors.muted
                            : AppColors.text,
                        height: 1.4,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (detail.trip.waypoints.isNotEmpty)
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: detail.trip.waypoints
                  .map((e) => Chip(label: Text('经停 · $e')))
                  .toList(),
            ),
        ],
      ),
    );
  }
}

class _MembersCard extends StatelessWidget {
  const _MembersCard({required this.detail});
  final TripPublicDetailModel detail;
  @override
  Widget build(BuildContext context) => _WhiteCard(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '行程成员（${detail.trip.memberCount}/${detail.trip.maxMemberCount}）',
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 14),
        SizedBox(
          height: 83,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: detail.members.length,
            separatorBuilder: (_, _) => const SizedBox(width: 17),
            itemBuilder: (_, index) {
              final member = detail.members[index];
              return InkWell(
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => PublicProfilePage(userId: member.userId),
                  ),
                ),
                child: SizedBox(
                  width: 58,
                  child: Column(
                    children: [
                      UserAvatar(
                        nickname: member.nickname,
                        avatarImageKey: member.avatarImageKey,
                        radius: 23,
                      ),
                      const SizedBox(height: 5),
                      Text(
                        member.role == 'OWNER'
                            ? '${member.nickname}·队长'
                            : member.nickname,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11,
                          color: member.role == 'OWNER'
                              ? AppColors.primary
                              : AppColors.text,
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
        const Text(
          '仅展示成员公开资料，不显示手机号、证件或完整车牌。',
          style: TextStyle(fontSize: 11, color: AppColors.muted),
        ),
      ],
    ),
  );
}

class _ApplyRequest {
  const _ApplyRequest(this.message, this.selfDrive, this.companionCount);
  final String message;
  final bool selfDrive;
  final int companionCount;
}

class _ApplySheet extends StatefulWidget {
  const _ApplySheet({required this.trip});
  final TripDiscoverModel trip;
  @override
  State<_ApplySheet> createState() => _ApplySheetState();
}

class _ApplySheetState extends State<_ApplySheet> {
  final message = TextEditingController(text: '路线很合适，希望能一起出发');
  bool selfDrive = false;
  int count = 1;
  @override
  void dispose() {
    message.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
    child: Container(
      padding: const EdgeInsets.fromLTRB(22, 14, 22, 24),
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Center(
                child: SizedBox(width: 40, child: Divider(thickness: 4)),
              ),
              const SizedBox(height: 12),
              const Text(
                '申请加入行程',
                style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 6),
              Text(
                widget.trip.title,
                style: const TextStyle(color: AppColors.muted),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: message,
                minLines: 3,
                maxLines: 5,
                decoration: const InputDecoration(
                  labelText: '申请说明',
                  alignLabelWithHint: true,
                  contentPadding: EdgeInsets.fromLTRB(16, 20, 16, 14),
                ),
              ),
              const SizedBox(height: 12),
              SwitchListTile.adaptive(
                contentPadding: EdgeInsets.zero,
                title: const Text('我会自驾'),
                subtitle: const Text('审核通过后再完善本次出行车辆'),
                value: selfDrive,
                onChanged: (value) => setState(() => selfDrive = value),
              ),
              Row(
                children: [
                  const Expanded(child: Text('同行人数（含本人）')),
                  IconButton(
                    onPressed: count > 1 ? () => setState(() => count--) : null,
                    icon: const Icon(LucideIcons.minus),
                  ),
                  Text(
                    '$count',
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  IconButton(
                    onPressed: count < widget.trip.remainingSeats
                        ? () => setState(() => count++)
                        : null,
                    icon: const Icon(LucideIcons.plus),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: message.text.trim().isEmpty
                      ? null
                      : () => Navigator.pop(
                          context,
                          _ApplyRequest(message.text.trim(), selfDrive, count),
                        ),
                  child: const Text('提交申请'),
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _WhiteCard extends StatelessWidget {
  const _WhiteCard({required this.child});
  final Widget child;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(22),
      boxShadow: const [BoxShadow(color: Color(0x09000000), blurRadius: 14)],
    ),
    child: child,
  );
}

class _HeaderTag extends StatelessWidget {
  const _HeaderTag(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
    decoration: BoxDecoration(
      color: const Color(0x33FFFFFF),
      borderRadius: BorderRadius.circular(9),
    ),
    child: Text(
      text,
      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
    ),
  );
}

class _Error extends StatelessWidget {
  const _Error({required this.onRetry, this.message});
  final VoidCallback onRetry;
  final String? message;
  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(LucideIcons.circleAlert, size: 46, color: AppColors.muted),
          const SizedBox(height: 10),
          Text(message ?? '加载失败', textAlign: TextAlign.center),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: onRetry, child: const Text('重试')),
        ],
      ),
    ),
  );
}
