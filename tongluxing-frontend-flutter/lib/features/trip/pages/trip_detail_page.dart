import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/routes.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import 'trip_navigation_page.dart';
import 'trip_quick_edit_page.dart';
import 'trip_route_map_page.dart';
import '../widgets/trip_route_preview.dart';
import '../../chat/pages/chat_session_page.dart';

class TripDetailPage extends StatefulWidget {
  const TripDetailPage({
    required this.tripId,
    super.key,
    this.initial,
    this.exitedView = false,
  });
  final String tripId;
  final TripModel? initial;
  final bool exitedView;
  @override
  State<TripDetailPage> createState() => _TripDetailPageState();
}

class _TripDetailPageState extends State<TripDetailPage> {
  static const _location = MethodChannel('com.tongluxing/permissions');
  static const double _startRadiusMeters = 5000;

  TripModel? trip;
  bool loading = false;
  String? error;
  bool settling = false;
  bool starting = false;
  @override
  void initState() {
    super.initState();
    trip = widget.initial;
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final service = TripService(context.read<AppSession>().api);
      trip = await service.detail(widget.tripId);
      error = null;
    } catch (e) {
      if (trip == null) error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> start() async {
    if (starting) return;
    setState(() => starting = true);
    try {
      final service = TripService(context.read<AppSession>().api);
      final state = await service.activeState();
      if (!mounted) return;
      final runningTripId = state['tripId']?.toString();
      if (state['active'] == true &&
          runningTripId != null &&
          runningTripId.isNotEmpty &&
          runningTripId != widget.tripId) {
        final openCurrent =
            await showDialog<bool>(
              context: context,
              builder: (dialogContext) => AlertDialog(
                title: const Text('已有进行中的行程'),
                content: Text(state['message']?.toString() ?? '同一时间只能进行一个行程。'),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(dialogContext, false),
                    child: const Text('取消'),
                  ),
                  FilledButton(
                    onPressed: () => Navigator.pop(dialogContext, true),
                    child: const Text('查看当前行程'),
                  ),
                ],
              ),
            ) ??
            false;
        if (openCurrent && mounted) {
          Navigator.pushReplacement(
            context,
            MaterialPageRoute(
              builder: (_) => TripDetailPage(tripId: runningTripId),
            ),
          );
        }
        return;
      }
      final allowed =
          await showDialog<bool>(
            context: context,
            builder: (dialogContext) => AlertDialog(
              icon: const Icon(
                LucideIcons.mapPinCheck,
                color: AppColors.primary,
              ),
              title: const Text('确认开启行程'),
              content: const Text(
                '开启后将持续使用定位计算实际行驶路程。请允许后台定位，并保持同路行在后台运行；如果清理或强制停止应用，将无法继续定位，也无法计算本次行程的路程值。\n\n'
                '为避免误操作，你需要位于起点 5 公里范围内。',
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(dialogContext, false),
                  child: const Text('暂不开启'),
                ),
                FilledButton(
                  onPressed: () => Navigator.pop(dialogContext, true),
                  child: const Text('继续并检查位置'),
                ),
              ],
            ),
          ) ??
          false;
      if (!allowed || !mounted) return;
      final granted =
          await _location.invokeMethod<bool>('requestLocation') ?? false;
      if (!granted) {
        _showStartMessage('需要定位权限才能核验起点并开启行程');
        return;
      }
      final raw = await _location.invokeMapMethod<String, dynamic>(
        'getCurrentLocation',
      );
      if (raw == null) {
        _showStartMessage('暂时无法获取当前位置，请稍后重试');
        return;
      }
      final latitude = (raw['latitude'] as num?)?.toDouble();
      final longitude = (raw['longitude'] as num?)?.toDouble();
      final accuracy = (raw['accuracy'] as num?)?.toDouble();
      if (latitude == null ||
          longitude == null ||
          !latitude.isFinite ||
          !longitude.isFinite ||
          latitude < -90 ||
          latitude > 90 ||
          longitude < -180 ||
          longitude > 180) {
        _showStartMessage('当前位置数据不完整，请稍后重试');
        return;
      }
      final startLocation = trip?.startLocation;
      if (startLocation != null) {
        final distance = _distanceMeters(
          latitude,
          longitude,
          startLocation.latitude,
          startLocation.longitude,
        );
        if (distance > _startRadiusMeters) {
          _showStartMessage(
            '你距离行程起点约 ${(distance / 1000).toStringAsFixed(1)} 公里，需进入 5 公里范围内才能开启行程',
          );
          return;
        }
      }
      trip = await service.start(
        widget.tripId,
        latitude: latitude,
        longitude: longitude,
        accuracy: accuracy,
      );
      if (!mounted) return;
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (_) => TripNavigationPage(trip: trip!)),
      );
    } on PlatformException catch (e) {
      _showStartMessage(
        e.message?.trim().isNotEmpty == true
            ? e.message!.trim()
            : '定位服务暂不可用，请确认已开启系统定位后重试',
      );
    } on ApiException catch (e) {
      _showStartMessage(e.message);
    } catch (_) {
      _showStartMessage('开启行程失败，请稍后重试');
    } finally {
      if (mounted) setState(() => starting = false);
    }
  }

  void _showStartMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  double _distanceMeters(
    double latitude1,
    double longitude1,
    double latitude2,
    double longitude2,
  ) {
    const radius = 6371000.0;
    final latitudeDelta = (latitude2 - latitude1) * math.pi / 180;
    final longitudeDelta = (longitude2 - longitude1) * math.pi / 180;
    final startLatitude = latitude1 * math.pi / 180;
    final endLatitude = latitude2 * math.pi / 180;
    final value =
        math.sin(latitudeDelta / 2) * math.sin(latitudeDelta / 2) +
        math.cos(startLatitude) *
            math.cos(endLatitude) *
            math.sin(longitudeDelta / 2) *
            math.sin(longitudeDelta / 2);
    final safeValue = value.clamp(0.0, 1.0);
    return radius *
        2 *
        math.atan2(math.sqrt(safeValue), math.sqrt(1 - safeValue));
  }

  Future<void> settle() async {
    setState(() => settling = true);
    try {
      final result = await TripService(
        context.read<AppSession>().api,
      ).settle(widget.tripId);
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          icon: const Icon(LucideIcons.sparkles, color: AppColors.primary),
          title: Text(
            result.status == 'REVIEW_REQUIRED'
                ? '已提交人工复核'
                : result.duplicate
                ? '该行程已经完成结算'
                : '结算完成',
          ),
          content: Text(
            result.status == 'REVIEW_REQUIRED'
                ? '轨迹覆盖率或节点到达证据不足，复核完成前暂不发成长值'
                : '行程数据与成长值结算结果已更新',
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('返回首页'),
            ),
          ],
        ),
      );
      if (!mounted) return;
      Navigator.pushNamedAndRemoveUntil(context, AppRoutes.home, (_) => false);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => settling = false);
    }
  }

  Future<void> openChat() async {
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
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentUserId = context.watch<AppSession>().userId;
    final isOwner =
        trip?.ownerUserId != null && trip!.ownerUserId == currentUserId;
    return Scaffold(
      backgroundColor: Colors.white,
      bottomNavigationBar:
          isOwner &&
              !widget.exitedView &&
              trip != null &&
              const ['PUBLISHED', 'READY', 'CONFIRMING'].contains(trip!.status)
          ? SafeArea(
              minimum: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: SizedBox(
                height: 52,
                child: FilledButton.icon(
                  onPressed: starting ? null : start,
                  icon: const Icon(LucideIcons.navigation),
                  label: Text(starting ? '正在检查并开始…' : '开始行程'),
                ),
              ),
            )
          : null,
      body: AsyncPanel(
        loading: loading && trip == null,
        error: error,
        onRetry: load,
        child: trip == null
            ? const SizedBox()
            : CustomScrollView(
                slivers: [
                  SliverAppBar(
                    expandedHeight: 108,
                    pinned: true,
                    foregroundColor: AppColors.text,
                    backgroundColor: Colors.white,
                    surfaceTintColor: Colors.white,
                    actions:
                        isOwner &&
                            !const [
                              'FINISHED',
                              'SETTLED',
                              'ENDED',
                              'CANCELLED',
                              'ARCHIVED',
                            ].contains(trip!.status)
                        ? [
                            IconButton(
                              tooltip: '修改行程',
                              icon: const Icon(LucideIcons.squarePen),
                              onPressed: () async {
                                final changed = await Navigator.push<bool>(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) => TripQuickEditPage(
                                      tripId: widget.tripId,
                                    ),
                                  ),
                                );
                                if (changed == true && mounted) await load();
                              },
                            ),
                          ]
                        : null,
                    flexibleSpace: const FlexibleSpaceBar(
                      titlePadding: EdgeInsets.fromLTRB(18, 0, 18, 12),
                      title: Text(
                        '行程详情',
                        style: TextStyle(
                          color: AppColors.text,
                          fontSize: 25,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                  ),
                  SliverToBoxAdapter(
                    child: _RouteHeader(
                      trip: trip!,
                      onTap: () {
                        final nodes = [
                          if (trip!.startLocation != null) trip!.startLocation!,
                          ...trip!.waypoints,
                          if (trip!.endLocation != null) trip!.endLocation!,
                        ];
                        final route = buildSmoothOverviewRoute(nodes);
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => TripRouteMapPage.forTrip(
                              trip: trip!,
                              routePoints: route,
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                  SliverToBoxAdapter(
                    child: _TripDetailBody(
                      trip: trip!,
                      isOwner: isOwner,
                      exitedView: widget.exitedView,
                      settling: settling,
                      onOpenChat: openChat,
                      onStart: start,
                      onSettle: settle,
                      onContinue: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => TripNavigationPage(trip: trip!),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}

class _RouteHeader extends StatelessWidget {
  const _RouteHeader({required this.trip, required this.onTap});

  static const double _aspectRatio = 2.48;
  static const double _maxWidth = 560;

  final TripModel trip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(14, 8, 14, 0),
    child: Align(
      alignment: Alignment.center,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: _maxWidth),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final mapHeight = (constraints.maxWidth / _aspectRatio)
                .clamp(124.0, 210.0)
                .toDouble();
            if (trip.startLocation != null && trip.endLocation != null) {
              return InkWell(
                onTap: onTap,
                borderRadius: BorderRadius.circular(18),
                child: Stack(
                  alignment: Alignment.bottomRight,
                  children: [
                    IgnorePointer(
                      child: TripRoutePreview(
                        mapOnly: true,
                        simplifiedOverview: true,
                        mapHeight: mapHeight,
                        performanceLabel: 'trip_detail:${trip.id}',
                        route: TripDraftRouteModel(
                          status: 'SUCCESS',
                          points: [
                            trip.startLocation!,
                            ...trip.waypoints,
                            trip.endLocation!,
                          ],
                          routePolyline: trip.routePolyline ?? '',
                          distanceMeters: trip.distanceMeters,
                          providerType: 'AMAP_WEB_V5',
                        ),
                        points: [
                          trip.startLocation!,
                          ...trip.waypoints,
                          trip.endLocation!,
                        ],
                      ),
                    ),
                    Container(
                      margin: const EdgeInsets.all(10),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 11,
                        vertical: 7,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: .94),
                        borderRadius: BorderRadius.circular(99),
                      ),
                      child: const Text(
                        '查看路线概览',
                        style: TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                  ],
                ),
              );
            }
            return SizedBox(
              height: mapHeight,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(18),
                clipBehavior: Clip.hardEdge,
                child: ColoredBox(
                  color: const Color(0xFFEFF4FA),
                  child: CustomPaint(
                    painter: const _FallbackRoutePainter(),
                    child: const Center(
                      child: Icon(
                        LucideIcons.route,
                        size: 34,
                        color: AppColors.primary,
                      ),
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    ),
  );
}

class _TripDetailBody extends StatelessWidget {
  const _TripDetailBody({
    required this.trip,
    required this.isOwner,
    required this.exitedView,
    required this.settling,
    required this.onOpenChat,
    required this.onStart,
    required this.onSettle,
    required this.onContinue,
  });

  final TripModel trip;
  final bool isOwner;
  final bool exitedView;
  final bool settling;
  final VoidCallback onOpenChat;
  final VoidCallback onStart;
  final VoidCallback onSettle;
  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) => ColoredBox(
    color: Colors.white,
    child: Padding(
      padding: const EdgeInsets.fromLTRB(18, 6, 18, 34),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  trip.title,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 5,
                ),
                decoration: BoxDecoration(
                  color: AppColors.primarySoft,
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Text(
                  exitedView ? '已退出' : tripStatusLabel(trip.status),
                  style: const TextStyle(
                    color: AppColors.primaryDark,
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 5),
          Text(
            trip.departureTime ?? '出发时间待确定',
            style: const TextStyle(fontSize: 13, color: AppColors.muted),
          ),
          if (exitedView) ...[
            const SizedBox(height: 14),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              decoration: BoxDecoration(
                color: const Color(0xFFFFF4E8),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Row(
                children: [
                  Icon(LucideIcons.logOut, size: 19, color: Color(0xFFB85C00)),
                  SizedBox(width: 10),
                  Text(
                    '行程已经退出',
                    style: TextStyle(
                      color: Color(0xFF9A4D00),
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 22),
          _RouteAddresses(trip: trip),
          const SizedBox(height: 18),
          Row(
            children: [
              _Fact(
                icon: LucideIcons.carFront,
                value: '${trip.joinedVehicles}/${trip.maxVehicles} 辆',
              ),
              const SizedBox(width: 24),
              _Fact(
                icon: LucideIcons.gauge,
                value: trip.distanceMeters == null
                    ? '距离待规划'
                    : '${(trip.distanceMeters! / 1000).toStringAsFixed(0)} km',
              ),
            ],
          ),
          const SizedBox(height: 18),
          const Divider(height: 1, color: Color(0xFFE8EBEF)),
          if (!exitedView) ...[
            _DetailAction(
              icon: LucideIcons.messageCircle,
              label: '进入车队群聊',
              onTap: onOpenChat,
            ),
            const Divider(height: 1, color: Color(0xFFE8EBEF)),
          ],
          _DetailAction(
            icon: LucideIcons.usersRound,
            label: '成员与车辆',
            value: '${trip.joinedVehicles} 辆车已加入',
          ),
          const Divider(height: 1, color: Color(0xFFE8EBEF)),
          _DetailAction(
            icon: LucideIcons.mapPinned,
            label: '路线与经停点',
            value: '${trip.waypoints.length} 个经停点',
          ),
          if (trip.description?.isNotEmpty == true) ...[
            const Divider(height: 1, color: Color(0xFFE8EBEF)),
            _DetailAction(
              icon: LucideIcons.fileText,
              label: '行程说明',
              value: trip.description!,
            ),
          ],
          if (isOwner && !exitedView) ...[
            const SizedBox(height: 18),
            const Divider(height: 1, color: Color(0xFFE8EBEF)),
            _LifecycleAction(
              trip: trip,
              settling: settling,
              onStart: onStart,
              onSettle: onSettle,
              onContinue: onContinue,
            ),
          ],
        ],
      ),
    ),
  );
}

class _RouteAddresses extends StatelessWidget {
  const _RouteAddresses({required this.trip});
  final TripModel trip;

  @override
  Widget build(BuildContext context) {
    final nodes = <({String label, String name, String address, Color color})>[
      (
        label: '起点',
        name: trip.startLocation?.name ?? trip.startName,
        address: trip.startLocation?.address ?? '',
        color: AppColors.success,
      ),
      for (final entry in trip.waypoints.asMap().entries)
        (
          label: '途经点 ${entry.key + 1}',
          name: entry.value.name,
          address: entry.value.address,
          color: const Color(0xFFFFA928),
        ),
      (
        label: '目的地',
        name: trip.endLocation?.name ?? trip.endName,
        address: trip.endLocation?.address ?? '',
        color: AppColors.primary,
      ),
    ];
    return Column(
      children: [
        for (final entry in nodes.asMap().entries)
          _RouteAddressNode(
            label: entry.value.label,
            name: entry.value.name,
            address: entry.value.address,
            color: entry.value.color,
            showConnector: entry.key < nodes.length - 1,
          ),
      ],
    );
  }
}

class _RouteAddressNode extends StatelessWidget {
  const _RouteAddressNode({
    required this.label,
    required this.name,
    required this.address,
    required this.color,
    required this.showConnector,
  });

  final String label;
  final String name;
  final String address;
  final Color color;
  final bool showConnector;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      SizedBox(
        width: 26,
        child: Column(
          children: [
            Container(
              width: 10,
              height: 10,
              margin: const EdgeInsets.only(top: 5),
              decoration: BoxDecoration(
                color: Colors.white,
                shape: BoxShape.circle,
                border: Border.all(color: color, width: 2.5),
              ),
            ),
            if (showConnector)
              Container(
                width: 2,
                height: address.trim().isEmpty ? 32 : 46,
                color: const Color(0xFFD9E2EE),
              ),
          ],
        ),
      ),
      const SizedBox(width: 10),
      Expanded(
        child: Padding(
          padding: EdgeInsets.only(bottom: showConnector ? 12 : 0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: TextStyle(
                  color: color,
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              if (address.trim().isNotEmpty && address.trim() != name.trim())
                Text(
                  address,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: AppColors.muted, fontSize: 11),
                ),
            ],
          ),
        ),
      ),
    ],
  );
}

class _Fact extends StatelessWidget {
  const _Fact({required this.icon, required this.value});
  final IconData icon;
  final String value;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Icon(icon, size: 17, color: AppColors.primary),
      const SizedBox(width: 7),
      Text(
        value,
        style: const TextStyle(fontSize: 13, color: AppColors.secondaryText),
      ),
    ],
  );
}

class _DetailAction extends StatelessWidget {
  const _DetailAction({
    required this.icon,
    required this.label,
    this.value,
    this.onTap,
  });
  final IconData icon;
  final String label;
  final String? value;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: SizedBox(
      height: 54,
      child: Row(
        children: [
          Icon(icon, size: 19, color: AppColors.primaryDark),
          const SizedBox(width: 13),
          Expanded(
            child: Text(
              label,
              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
            ),
          ),
          if (value != null)
            Flexible(
              child: Text(
                value!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.right,
                style: const TextStyle(fontSize: 12, color: AppColors.muted),
              ),
            ),
          if (onTap != null) ...[
            const SizedBox(width: 6),
            const Icon(
              LucideIcons.chevronRight,
              size: 18,
              color: AppColors.muted,
            ),
          ],
        ],
      ),
    ),
  );
}

class _LifecycleAction extends StatelessWidget {
  const _LifecycleAction({
    required this.trip,
    required this.settling,
    required this.onStart,
    required this.onSettle,
    required this.onContinue,
  });
  final TripModel trip;
  final bool settling;
  final VoidCallback onStart;
  final VoidCallback onSettle;
  final VoidCallback onContinue;

  @override
  Widget build(BuildContext context) {
    if (trip.status == 'RUNNING' || trip.status == 'ONGOING') {
      return _PrimaryAction(label: '继续导航', onTap: onContinue);
    }
    if (trip.status == 'FINISHED' || trip.status == 'ENDED') {
      return _PrimaryAction(
        label: settling ? '正在结算…' : '完成行程结算',
        onTap: settling ? null : onSettle,
      );
    }
    if (trip.status == 'SETTLED') {
      return const SizedBox(
        height: 54,
        child: Row(
          children: [
            Icon(LucideIcons.circleCheck, color: AppColors.success, size: 20),
            SizedBox(width: 12),
            Text(
              '行程已完成结算',
              style: TextStyle(
                color: AppColors.success,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
      );
    }
    if (const ['PUBLISHED', 'READY', 'CONFIRMING'].contains(trip.status)) {
      return _PrimaryAction(label: '开启行程', onTap: onStart);
    }
    return const SizedBox.shrink();
  }
}

class _PrimaryAction extends StatelessWidget {
  const _PrimaryAction({required this.label, required this.onTap});
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: SizedBox(
      height: 54,
      child: Row(
        children: [
          const Icon(
            LucideIcons.navigation,
            color: AppColors.primary,
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              label,
              style: const TextStyle(
                color: AppColors.primary,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const Icon(
            LucideIcons.chevronRight,
            size: 18,
            color: AppColors.primary,
          ),
        ],
      ),
    ),
  );
}

class _FallbackRoutePainter extends CustomPainter {
  const _FallbackRoutePainter();
  @override
  void paint(Canvas canvas, Size size) {
    final road = Paint()
      ..color = const Color(0xFFDCE6F2)
      ..strokeWidth = 12
      ..strokeCap = StrokeCap.round;
    canvas.drawLine(
      Offset(-10, size.height * .72),
      Offset(size.width + 20, size.height * .28),
      road,
    );
    canvas.drawLine(
      Offset(size.width * .2, -10),
      Offset(size.width * .7, size.height + 10),
      road,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
