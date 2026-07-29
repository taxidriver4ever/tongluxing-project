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
import 'trip_route_map_page.dart';
import 'trip_discovery_widgets.dart';
import '../widgets/trip_discovery_theme.dart';
import '../widgets/route_map_view.dart';

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
  List<LocationSelection> roadRoute = const [];
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
      final api = context.read<AppSession>().api;
      final loaded = await TripDiscoveryService(
        api,
      ).publicDetail(widget.tripId);
      detail = loaded;
      followed = loaded.owner.followed;
      saved = loaded.favorited;
      roadRoute = loaded.routePoints;
      final stored = loaded.routePoints;
      // 旧行程常常只保存起点、途经点和终点。此时必须向路线规划接口
      // 请求真实道路折线，不能把这些位置直接用直线连接。
      if (stored.length >= 2 &&
          stored.length <= loaded.trip.waypoints.length + 2) {
        try {
          final planned = await TripService(api).planRoadRoute(
            start: stored.first,
            end: stored.last,
            waypoints: stored.sublist(1, stored.length - 1),
          );
          if (planned.polylinePoints.length > 2) {
            roadRoute = planned.polylinePoints;
          }
        } catch (_) {
          // 保留后端折线并展示加载失败状态；页面主体仍可正常使用。
        }
      }
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
        vehicleId: request.vehicleId,
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
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: const SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.light,
        statusBarBrightness: Brightness.dark,
      ),
      child: Scaffold(
        backgroundColor: TripDiscoveryColors.pageBackground,
        body: trip == null && loading
            ? const Center(child: CircularProgressIndicator())
            : trip == null
            ? _Error(onRetry: load, message: error)
            : RefreshIndicator(
                onRefresh: load,
                edgeOffset: 76,
                child: CustomScrollView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  slivers: [
                    SliverAppBar(
                      pinned: true,
                      toolbarHeight: 54,
                      elevation: 0,
                      scrolledUnderElevation: 0,
                      foregroundColor: Colors.white,
                      backgroundColor: TripDiscoveryColors.primaryDark,
                      surfaceTintColor: Colors.transparent,
                      leading: IconButton(
                        tooltip: '返回',
                        onPressed: () => Navigator.maybePop(context),
                        icon: const Icon(LucideIcons.chevronLeft, size: 26),
                      ),
                      actions: [
                        IconButton(
                          tooltip: saved ? '取消收藏' : '收藏',
                          onPressed: actionBusy ? null : toggleFavorite,
                          icon: Icon(
                            LucideIcons.star,
                            color: saved
                                ? const Color(0xFFFFD166)
                                : Colors.white,
                            size: 24,
                          ),
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
                          icon: const Icon(LucideIcons.share2, size: 23),
                        ),
                        IconButton(
                          tooltip: '更多',
                          onPressed: () {},
                          icon: const Icon(LucideIcons.ellipsis, size: 24),
                        ),
                        const SizedBox(width: 5),
                      ],
                    ),
                    SliverToBoxAdapter(
                      child: _DiscoveryHero(
                        trip: trip,
                        detail: current,
                        routePoints: roadRoute,
                      ),
                    ),
                    SliverPadding(
                      padding: const EdgeInsets.fromLTRB(12, 10, 12, 104),
                      sliver: SliverList.list(
                        children: [
                          if (current == null && loading)
                            const Padding(
                              padding: EdgeInsets.all(42),
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
                            const SizedBox(height: 9),
                            _TripInformationCard(detail: current),
                          ],
                        ],
                      ),
                    ),
                  ],
                ),
              ),
        bottomNavigationBar: current == null
            ? null
            : _DetailBottomBar(
                busy: actionBusy,
                onConsult: consult,
                onPrimary: _primaryAction(current),
                primaryIcon: _primaryIcon(current),
                primaryLabel: _primaryLabel(current),
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

class _DiscoveryHero extends StatelessWidget {
  const _DiscoveryHero({
    required this.trip,
    required this.detail,
    required this.routePoints,
  });

  final TripDiscoverModel trip;
  final TripPublicDetailModel? detail;
  final List<LocationSelection> routePoints;

  @override
  Widget build(BuildContext context) => Container(
    decoration: const BoxDecoration(
      gradient: TripDiscoveryColors.headerGradient,
    ),
    child: Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              _HeroTag(
                text: '顺路度 ${trip.matchScore}%',
                foreground: TripDiscoveryColors.primary,
                background: const Color(0xDDE6F5FF),
              ),
              const Spacer(),
              _HeroTag(
                text: trip.matchScore >= 90 ? '🔥 热门招募中' : '招募中',
                foreground: Colors.white,
                background: const Color(0x24FFFFFF),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            trip.title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 23,
              height: 1.15,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 7),
          Text(
            [trip.startName, ...trip.waypoints, trip.endName].join(' → '),
            maxLines: 2,
            overflow: TextOverflow.fade,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 13.5,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 7,
            runSpacing: 7,
            children: [
              _HeroMeta(
                icon: LucideIcons.calendarDays,
                text: '${formatDiscoverDate(trip.departureTime)} 出发',
              ),
              _HeroMeta(
                icon: LucideIcons.clock3,
                text: '预计${trip.estimatedDays}天',
              ),
              _HeroMeta(icon: LucideIcons.users, text: '${trip.memberCount}人'),
            ],
          ),
          if (detail != null && routePoints.length >= 2) ...[
            const SizedBox(height: 14),
            _TripRouteMapCard(detail: detail!, routePoints: routePoints),
          ] else ...[
            const Spacer(),
            _RouteStopsSummary(trip: trip),
          ],
        ],
      ),
    ),
  );
}

class _RouteStopsSummary extends StatelessWidget {
  const _RouteStopsSummary({required this.trip});

  final TripDiscoverModel trip;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: const Color(0xEEFFFFFF),
      borderRadius: BorderRadius.circular(18),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Icon(
          LucideIcons.route,
          size: 20,
          color: TripDiscoveryColors.primary,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            [trip.startName, ...trip.waypoints, trip.endName].join(' → '),
            style: const TextStyle(
              color: TripDiscoveryColors.text,
              fontSize: 12.5,
              height: 1.5,
              fontWeight: FontWeight.w700,
            ),
          ),
        ),
      ],
    ),
  );
}

class _HeroTag extends StatelessWidget {
  const _HeroTag({
    required this.text,
    required this.foreground,
    required this.background,
  });

  final String text;
  final Color foreground;
  final Color background;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
    decoration: BoxDecoration(
      color: background,
      borderRadius: BorderRadius.circular(7),
    ),
    child: Text(
      text,
      style: TextStyle(
        color: foreground,
        fontSize: 10.5,
        fontWeight: FontWeight.w800,
      ),
    ),
  );
}

class _HeroMeta extends StatelessWidget {
  const _HeroMeta({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
    decoration: BoxDecoration(
      color: const Color(0x1EFFFFFF),
      borderRadius: BorderRadius.circular(9),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, color: Colors.white, size: 13),
        const SizedBox(width: 5),
        Text(
          text,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 10,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    ),
  );
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
    padding: const EdgeInsets.fromLTRB(15, 15, 15, 13),
    child: Column(
      children: [
        InkWell(
          onTap: onOpen,
          borderRadius: BorderRadius.circular(14),
          child: Row(
            children: [
              UserAvatar(
                nickname: owner.nickname,
                avatarImageKey: owner.avatarImageKey,
                radius: 27,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(
                            owner.nickname,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: TripDiscoveryColors.text,
                              fontSize: 16,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        _SmallLabel(
                          owner.levelCode.toUpperCase(),
                          const Color(0xFFFFEFD9),
                          const Color(0xFF7A4C22),
                        ),
                        if (owner.rating > 0) ...[
                          const SizedBox(width: 5),
                          _SmallLabel(
                            '★ ${owner.rating.toStringAsFixed(1)}',
                            const Color(0xFFE5F4FF),
                            TripDiscoveryColors.primary,
                          ),
                        ],
                      ],
                    ),
                    if (owner.certificationStatus == 'APPROVED') ...[
                      const SizedBox(height: 5),
                      const Align(
                        alignment: Alignment.centerLeft,
                        child: _SmallLabel(
                          '● 认证车主',
                          Color(0xFFE5F4FF),
                          TripDiscoveryColors.primary,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 8),
              SizedBox(
                height: 34,
                child: OutlinedButton(
                  onPressed: busy ? null : onFollow,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: TripDiscoveryColors.primary,
                    side: const BorderSide(
                      color: TripDiscoveryColors.primary,
                      width: 1.2,
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    shape: const StadiumBorder(),
                  ),
                  child: Text(followed ? '已关注' : '+ 关注'),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        Row(
          children: [
            _OwnerStat('${owner.totalTripCount}', '发起行程'),
            _OwnerStat(
              '${(owner.totalDistanceMeters / 1000).toStringAsFixed(0)}km+',
              '累计里程',
            ),
            _OwnerBadgeStat(owner.badgeCount),
          ],
        ),
      ],
    ),
  );
}

class _SmallLabel extends StatelessWidget {
  const _SmallLabel(this.text, this.background, this.foreground);

  final String text;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
    decoration: BoxDecoration(
      color: background,
      borderRadius: BorderRadius.circular(7),
    ),
    child: Text(
      text,
      style: TextStyle(
        color: foreground,
        fontSize: 9.5,
        fontWeight: FontWeight.w800,
      ),
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
          style: const TextStyle(
            color: TripDiscoveryColors.text,
            fontWeight: FontWeight.w900,
            fontSize: 15,
          ),
        ),
        const SizedBox(height: 5),
        Text(
          label,
          style: const TextStyle(
            fontSize: 10.5,
            color: TripDiscoveryColors.secondaryText,
          ),
        ),
      ],
    ),
  );
}

class _OwnerBadgeStat extends StatelessWidget {
  const _OwnerBadgeStat(this.count);

  final int count;

  @override
  Widget build(BuildContext context) => Expanded(
    child: Column(
      children: [
        const Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            _BadgeDot(Color(0xFFE99B16), '旅'),
            _BadgeDot(Color(0xFF746154), '途'),
            _BadgeDot(Color(0xFF81766A), '行'),
            _BadgeDot(TripDiscoveryColors.primary, '者'),
          ],
        ),
        const SizedBox(height: 5),
        Text(
          '$count枚勋章',
          style: const TextStyle(
            fontSize: 10.5,
            color: TripDiscoveryColors.secondaryText,
          ),
        ),
      ],
    ),
  );
}

class _BadgeDot extends StatelessWidget {
  const _BadgeDot(this.color, this.text);

  final Color color;
  final String text;

  @override
  Widget build(BuildContext context) => Container(
    width: 19,
    height: 21,
    margin: const EdgeInsets.symmetric(horizontal: 1),
    alignment: Alignment.center,
    decoration: BoxDecoration(
      color: color,
      borderRadius: BorderRadius.circular(7),
      border: Border.all(color: Colors.white, width: 1),
    ),
    child: Text(
      text,
      style: const TextStyle(
        color: Colors.white,
        fontSize: 9,
        fontWeight: FontWeight.w900,
      ),
    ),
  );
}

class _TripRouteMapCard extends StatelessWidget {
  const _TripRouteMapCard({required this.detail, required this.routePoints});

  static const double aspectRatio = 2.48;
  static const double minHeight = 124;
  static const double maxHeight = 210;
  static const double maxWidth = 560;

  final TripPublicDetailModel detail;
  final List<LocationSelection> routePoints;

  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.center,
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: maxWidth),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final mapHeight = (constraints.maxWidth / aspectRatio)
              .clamp(minHeight, maxHeight)
              .toDouble();
          final radius = BorderRadius.circular(16);
          return DecoratedBox(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: radius,
              boxShadow: const [
                BoxShadow(
                  color: Color(0x160B4A87),
                  blurRadius: 14,
                  offset: Offset(0, 5),
                ),
              ],
            ),
            child: Material(
              color: Colors.transparent,
              borderRadius: radius,
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => TripRouteMapPage(
                      detail: detail,
                      routePoints: routePoints,
                    ),
                  ),
                ),
                child: SizedBox(
                  width: double.infinity,
                  height: mapHeight,
                  child: Stack(
                    fit: StackFit.expand,
                    clipBehavior: Clip.hardEdge,
                    children: [
                      IgnorePointer(
                        child: RouteMapView(
                          polylinePoints: routePoints,
                          stops: [
                            LocationSelection(
                              name: detail.trip.startName,
                              address: '',
                              latitude: routePoints.first.latitude,
                              longitude: routePoints.first.longitude,
                            ),
                            LocationSelection(
                              name: detail.trip.endName,
                              address: '',
                              latitude: routePoints.last.latitude,
                              longitude: routePoints.last.longitude,
                            ),
                          ],
                          height: mapHeight,
                          interactive: false,
                        ),
                      ),
                      Positioned(
                        right: 10,
                        bottom: 10,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 7,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: .94),
                            borderRadius: BorderRadius.circular(99),
                            boxShadow: const [
                              BoxShadow(
                                color: Color(0x19000000),
                                blurRadius: 8,
                                offset: Offset(0, 2),
                              ),
                            ],
                          ),
                          child: const Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(LucideIcons.scanSearch, size: 14),
                              SizedBox(width: 5),
                              Text(
                                '查看完整路线',
                                style: TextStyle(
                                  fontSize: 11.5,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        },
      ),
    ),
  );
}

class _TripInformationCard extends StatelessWidget {
  const _TripInformationCard({required this.detail});

  final TripPublicDetailModel detail;

  @override
  Widget build(BuildContext context) {
    return _WhiteCard(
      padding: const EdgeInsets.fromLTRB(15, 14, 15, 15),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '行程详情',
            style: TextStyle(
              color: TripDiscoveryColors.text,
              fontSize: 16,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            detail.trip.description.trim().isEmpty
                ? '发起人暂未填写行程说明。'
                : detail.trip.description,
            style: const TextStyle(
              color: TripDiscoveryColors.secondaryText,
              fontSize: 12.5,
              height: 1.55,
            ),
          ),
          const SizedBox(height: 10),
          _InfoLine(
            icon: LucideIcons.carFront,
            text: '车型要求：${detail.vehicleRequirements.join(' / ')}',
          ),
          if (detail.budgetDescription.trim().isNotEmpty)
            _InfoLine(
              icon: LucideIcons.walletCards,
              text: '费用预算：${detail.budgetDescription}',
            ),
          if (detail.notes.trim().isNotEmpty)
            _InfoLine(
              icon: LucideIcons.circleAlert,
              text: '注意事项：${detail.notes}',
            ),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 11),
            child: Divider(height: 1, color: TripDiscoveryColors.divider),
          ),
          Text(
            '行程成员（${detail.trip.memberCount}人）',
            style: const TextStyle(
              color: TripDiscoveryColors.text,
              fontSize: 15,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 11),
          if (detail.members.isEmpty)
            const Text(
              '暂无可展示的公开成员资料',
              style: TextStyle(color: TripDiscoveryColors.muted),
            )
          else
            LayoutBuilder(
              builder: (context, constraints) {
                const columns = 5;
                const spacing = 8.0;
                final tileWidth =
                    (constraints.maxWidth - spacing * (columns - 1)) / columns;
                return Wrap(
                  alignment: WrapAlignment.start,
                  spacing: spacing,
                  runSpacing: 10,
                  children: detail.members
                      .map(
                        (member) => SizedBox(
                          width: tileWidth,
                          child: _MemberTile(member: member),
                        ),
                      )
                      .toList(),
                );
              },
            ),
        ],
      ),
    );
  }
}

class _InfoLine extends StatelessWidget {
  const _InfoLine({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 5.5),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 17, color: TripDiscoveryColors.primary),
        const SizedBox(width: 9),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              color: TripDiscoveryColors.secondaryText,
              fontSize: 12,
              height: 1.35,
            ),
          ),
        ),
      ],
    ),
  );
}

class _MemberTile extends StatelessWidget {
  const _MemberTile({required this.member});

  final TripPublicMemberModel member;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: () => Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => PublicProfilePage(userId: member.userId),
      ),
    ),
    borderRadius: BorderRadius.circular(12),
    child: SizedBox(
      height: 76,
      child: Column(
        children: [
          UserAvatar(
            nickname: member.nickname,
            avatarImageKey: member.avatarImageKey,
            radius: 19,
          ),
          const SizedBox(height: 5),
          Text(
            member.nickname,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: TripDiscoveryColors.text,
              fontSize: 9.5,
            ),
          ),
          SizedBox(
            height: 14,
            child: member.role == 'OWNER'
                ? const Text(
                    '队长',
                    style: TextStyle(
                      color: TripDiscoveryColors.primary,
                      fontSize: 9,
                      fontWeight: FontWeight.w700,
                    ),
                  )
                : null,
          ),
        ],
      ),
    ),
  );
}

class _DetailBottomBar extends StatelessWidget {
  const _DetailBottomBar({
    required this.busy,
    required this.onConsult,
    required this.onPrimary,
    required this.primaryIcon,
    required this.primaryLabel,
  });

  final bool busy;
  final VoidCallback onConsult;
  final VoidCallback? onPrimary;
  final IconData primaryIcon;
  final String primaryLabel;

  @override
  Widget build(BuildContext context) => SafeArea(
    top: false,
    child: Container(
      margin: const EdgeInsets.fromLTRB(10, 5, 10, 6),
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: const [
          BoxShadow(
            color: Color(0x160A2E58),
            blurRadius: 18,
            offset: Offset(0, -4),
          ),
        ],
      ),
      child: Row(
        children: [
          Expanded(
            flex: 4,
            child: _BottomAction(
              icon: LucideIcons.messageCircle,
              label: '咨询队长',
              onTap: busy ? null : onConsult,
              outlined: true,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            flex: 6,
            child: _BottomAction(
              icon: primaryIcon,
              label: primaryLabel,
              onTap: busy ? null : onPrimary,
              loading: busy,
            ),
          ),
        ],
      ),
    ),
  );
}

class _BottomAction extends StatelessWidget {
  const _BottomAction({
    required this.icon,
    required this.label,
    required this.onTap,
    this.outlined = false,
    this.loading = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  final bool outlined;
  final bool loading;

  @override
  Widget build(BuildContext context) {
    final enabled = onTap != null;
    return Opacity(
      opacity: enabled ? 1 : .55,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(28),
          child: Ink(
            height: 44,
            decoration: BoxDecoration(
              gradient: outlined || !enabled
                  ? null
                  : TripDiscoveryColors.buttonGradient,
              color: outlined ? Colors.white : (enabled ? null : Colors.grey),
              borderRadius: BorderRadius.circular(28),
              border: outlined
                  ? Border.all(color: TripDiscoveryColors.primary, width: 1.2)
                  : null,
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (loading)
                  const SizedBox(
                    width: 15,
                    height: 15,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                else
                  Icon(
                    icon,
                    size: 17,
                    color: outlined
                        ? TripDiscoveryColors.primary
                        : Colors.white,
                  ),
                const SizedBox(width: 7),
                Flexible(
                  child: Text(
                    label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: outlined
                          ? TripDiscoveryColors.primary
                          : Colors.white,
                      fontSize: 13,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ApplyRequest {
  const _ApplyRequest(this.message, this.selfDrive, this.vehicleId);
  final String message;
  final bool selfDrive;
  final String? vehicleId;
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
  bool eligibilityLoading = true;
  bool drivingLicenseApproved = false;
  VehicleModel? primaryVehicle;

  bool get canDrive =>
      drivingLicenseApproved &&
      primaryVehicle != null &&
      primaryVehicle!.status == 'APPROVED';

  @override
  void initState() {
    super.initState();
    Future.microtask(_loadDrivingEligibility);
  }

  Future<void> _loadDrivingEligibility() async {
    try {
      final api = context.read<AppSession>().api;
      final values = await Future.wait<dynamic>([
        UserProfileService(api).me(),
        VehicleService(api).mine(),
      ]);
      final profile = Map<String, dynamic>.from(values[0] as Map);
      final vehicles = values[1] as List<VehicleModel>;
      VehicleModel? selected;
      for (final vehicle in vehicles) {
        if (vehicle.isDefault && vehicle.status == 'APPROVED') {
          selected = vehicle;
          break;
        }
      }
      if (!mounted) return;
      setState(() {
        drivingLicenseApproved =
            profile['drivingLicenseCertificationStatus'] == 'APPROVED';
        primaryVehicle = selected;
        eligibilityLoading = false;
        if (!canDrive) selfDrive = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          eligibilityLoading = false;
          selfDrive = false;
        });
      }
    }
  }

  String get drivingEligibilityText {
    if (eligibilityLoading) return '正在检查驾驶证、行驶证和主要车辆';
    if (!drivingLicenseApproved) return '驾驶证认证通过后可选择';
    if (primaryVehicle == null) return '请先设置一辆已通过行驶证认证的主要车辆';
    return '主要车辆：${primaryVehicle!.brand} ${primaryVehicle!.model} · ${primaryVehicle!.plate}';
  }

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
                title: const Text('我会开车'),
                subtitle: Text(drivingEligibilityText),
                value: selfDrive,
                onChanged: canDrive
                    ? (value) => setState(() => selfDrive = value)
                    : null,
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: message.text.trim().isEmpty
                      ? null
                      : () => Navigator.pop(
                          context,
                          _ApplyRequest(
                            message.text.trim(),
                            selfDrive,
                            selfDrive ? primaryVehicle?.id : null,
                          ),
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
  const _WhiteCard({
    required this.child,
    this.padding = const EdgeInsets.all(18),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) => Container(
    padding: padding,
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
      boxShadow: const [
        BoxShadow(
          color: Color(0x0A173A63),
          blurRadius: 14,
          offset: Offset(0, 3),
        ),
      ],
    ),
    child: child,
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
