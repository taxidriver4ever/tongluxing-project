import 'dart:async';

import 'package:amap_map/amap_map.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import 'package:x_amap_base/x_amap_base.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/location_snapshot.dart';
import '../../chat/pages/chat_session_page.dart';
import '../../trip/pages/trip_create_page.dart';
import '../../trip/pages/trip_discovery_detail_page.dart';
import '../../trip/pages/trip_navigation_page.dart';
import '../../trip/pages/trip_search_results_page.dart';
import 'sos_confirm_page.dart';

/// 地图首页严格对应新 UI 的普通、行进中、已结束三种状态。
///
/// 旧版右上角“定位 / 路况 / SOS”悬浮工具已移除；定位在页面加载后自动请求，
/// SOS 只在行程进行中的底部操作区出现。
class MapHomePage extends StatefulWidget {
  const MapHomePage({
    super.key,
    this.onOpenTripRecommendations,
    this.onOpenMessages,
  });

  final VoidCallback? onOpenTripRecommendations;
  final VoidCallback? onOpenMessages;

  static bool get nativeAmap =>
      !kIsWeb &&
      defaultTargetPlatform == TargetPlatform.android &&
      const bool.fromEnvironment('AMAP_NATIVE', defaultValue: true);

  @override
  State<MapHomePage> createState() => _MapHomePageState();
}

class _MapHomePageState extends State<MapHomePage> {
  static const _permissionChannel = MethodChannel('com.tongluxing/permissions');

  AMapController? mapController;
  LatLng? lastLocation;
  bool checkingAmapSupport = true;
  bool amapRuntimeSupported = false;
  bool locationEnabled = false;
  bool centerOnNextLocation = false;
  bool loading = true;
  bool loadingHotTrips = false;
  String? error;
  TripModel? currentTrip;
  TripModel? completedTrip;
  List<TripRecommendModel> hotTrips = const [];

  _MapMode get mode {
    final trip = currentTrip;
    if (trip != null && _isRunning(trip.status)) return _MapMode.tracking;
    if (trip != null && _isCompleted(trip.status)) return _MapMode.ended;
    if (completedTrip != null && _isCompleted(completedTrip!.status)) {
      return _MapMode.ended;
    }
    return _MapMode.normal;
  }

  TripModel? get visibleTrip => currentTrip ?? completedTrip;

  @override
  void initState() {
    super.initState();
    _checkAmapSupport();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadState());
  }

  Future<void> _checkAmapSupport() async {
    if (!MapHomePage.nativeAmap) {
      if (mounted) setState(() => checkingAmapSupport = false);
      return;
    }
    try {
      final supported =
          await _permissionChannel.invokeMethod<bool>('isAmapSupported') ?? false;
      if (!mounted) return;
      setState(() {
        amapRuntimeSupported = supported;
        checkingAmapSupport = false;
      });
      if (supported) unawaited(_locateSilently());
    } on PlatformException {
      if (mounted) setState(() => checkingAmapSupport = false);
    } on MissingPluginException {
      if (mounted) setState(() => checkingAmapSupport = false);
    }
  }

  Future<void> _loadState() async {
    if (mounted) {
      setState(() {
        loading = true;
        error = null;
      });
    }
    try {
      final dashboard = await TripService(
        context.read<AppSession>().api,
      ).dashboard();
      TripModel? active;
      TripModel? justCompleted;
      for (final key in const [
        'joinedCurrentTrip',
        'currentTrip',
        'publishedCurrentTrip',
      ]) {
        final raw = dashboard[key];
        if (raw is! Map) continue;
        final candidate = TripModel.fromJson(Map<String, dynamic>.from(raw));
        if (candidate.id.isEmpty) continue;
        if (_isRunning(candidate.status)) {
          active = candidate;
          break;
        }
        if (_isCompleted(candidate.status)) justCompleted ??= candidate;
      }
      if (!mounted) return;
      setState(() {
        currentTrip = active;
        completedTrip = justCompleted;
      });
      if (active == null && justCompleted == null) await _loadHotTrips();
      _fitTripRoute();
    } catch (caught) {
      if (mounted) setState(() => error = caught.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _loadHotTrips() async {
    if (loadingHotTrips) return;
    if (mounted) setState(() => loadingHotTrips = true);
    try {
      final location = LocationSnapshot.current;
      final result = await TripDiscoveryService(
        context.read<AppSession>().api,
      ).recommend(
        sortBy: 'match_rate',
        userHasTrip: false,
        latitude: location?.latitude,
        longitude: location?.longitude,
        pageSize: 3,
      );
      final rows = (result['list'] ?? result['records']) as List? ?? const [];
      if (!mounted) return;
      setState(() {
        hotTrips = rows
            .whereType<Map>()
            .map((item) => TripRecommendModel.fromJson(
                  Map<String, dynamic>.from(item),
                ))
            .toList();
      });
    } catch (_) {
      // 热门推荐是普通地图状态的辅助内容，失败不阻断地图主体。
    } finally {
      if (mounted) setState(() => loadingHotTrips = false);
    }
  }

  Future<void> _locateSilently() async {
    if (!MapHomePage.nativeAmap || !amapRuntimeSupported) return;
    try {
      final granted =
          await _permissionChannel.invokeMethod<bool>('requestLocation') ?? false;
      if (!mounted || !granted) return;
      setState(() {
        locationEnabled = true;
        centerOnNextLocation = true;
      });
      if (lastLocation != null) _moveToLocation(lastLocation!);
    } on PlatformException {
      // 首页不弹权限错误，地图仍可按路线或默认城市展示。
    } on MissingPluginException {
      // 非原生预览环境使用静态地图背景。
    }
  }

  void _moveToLocation(LatLng location) {
    mapController?.moveCamera(CameraUpdate.newLatLngZoom(location, 15));
    centerOnNextLocation = false;
  }

  void _fitTripRoute() {
    final points = visibleTrip?.routePoints ?? const <LocationSelection>[];
    if (points.isEmpty) return;
    final center = points[points.length ~/ 2];
    WidgetsBinding.instance.addPostFrameCallback((_) {
      mapController?.moveCamera(
        CameraUpdate.newLatLngZoom(
          LatLng(center.latitude, center.longitude),
          points.length > 2 ? 9.5 : 11.5,
        ),
      );
    });
  }

  Future<void> _createTrip() async {
    final created = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const TripCreatePage()),
    );
    if (created == true && mounted) await _loadState();
  }

  Future<void> _openSearch() async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => const TripSearchResultsPage(initialKeyword: ''),
      ),
    );
  }

  Future<void> _openHotTrip(TripRecommendModel trip) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TripDiscoveryDetailPage(
          tripId: trip.tripId,
          initial: trip.toDiscoverModel(),
        ),
      ),
    );
  }

  Future<void> _openNavigation() async {
    final trip = visibleTrip;
    if (trip == null) return;
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => TripNavigationPage(trip: trip)),
    );
    if (mounted) await _loadState();
  }

  Future<void> _openChat() async {
    final trip = visibleTrip;
    if (trip == null) {
      widget.onOpenMessages?.call();
      return;
    }
    try {
      final conversation = await ChatService(
        context.read<AppSession>().api,
      ).tripConversation(trip.id);
      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => ChatSessionPage(conversation: conversation),
        ),
      );
    } catch (caught) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(caught.toString())));
    }
  }

  Future<void> _openSos() async {
    final location = lastLocation;
    if (location == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('正在获取当前位置，请稍后再试')),
      );
      unawaited(_locateSilently());
      return;
    }
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => SosConfirmPage(
          api: context.read<AppSession>().api,
          latitude: location.latitude,
          longitude: location.longitude,
          address: '当前地图定位',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Stack(
    children: [
      Positioned.fill(
        child: _MapSurface(
          nativeEnabled: amapRuntimeSupported,
          checkingSupport: checkingAmapSupport,
          locationEnabled: locationEnabled,
          trip: visibleTrip,
          mode: mode,
          onMapCreated: (controller) {
            mapController = controller;
            _fitTripRoute();
          },
          onLocationChanged: (location) {
            if (!_validLocation(location)) return;
            lastLocation = location.latLng;
            LocationSnapshot.current = LocationSnapshot(
              location.latLng.latitude,
              location.latLng.longitude,
            );
            if (centerOnNextLocation) _moveToLocation(location.latLng);
            if (mode == _MapMode.normal && hotTrips.isEmpty) {
              unawaited(_loadHotTrips());
            }
          },
        ),
      ),
      Positioned(
        left: 12,
        right: 12,
        top: MediaQuery.paddingOf(context).top + 12,
        child: _MapTopOverlay(
          mode: mode,
          trip: visibleTrip,
          hotTrips: hotTrips,
          loading: loading || loadingHotTrips,
          error: error,
          onOpenTrip: _openHotTrip,
          onOpenMessages: widget.onOpenMessages ?? _openChat,
        ),
      ),
      Positioned(
        left: 10,
        right: 10,
        bottom: 0,
        child: _MapBottomPanel(
          mode: mode,
          trip: visibleTrip,
          onSearch: _openSearch,
          onPublish: _createTrip,
          onRecommendations: widget.onOpenTripRecommendations,
          onNavigate: _openNavigation,
          onChat: _openChat,
          onSos: _openSos,
          onReportPosition: _openNavigation,
        ),
      ),
    ],
  );
}

enum _MapMode { normal, tracking, ended }

class _MapTopOverlay extends StatelessWidget {
  const _MapTopOverlay({
    required this.mode,
    required this.trip,
    required this.hotTrips,
    required this.loading,
    required this.error,
    required this.onOpenTrip,
    required this.onOpenMessages,
  });

  final _MapMode mode;
  final TripModel? trip;
  final List<TripRecommendModel> hotTrips;
  final bool loading;
  final String? error;
  final ValueChanged<TripRecommendModel> onOpenTrip;
  final VoidCallback onOpenMessages;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Expanded(
        child: mode == _MapMode.normal
            ? _NearbyTeamsOverlay(
                trips: hotTrips,
                loading: loading,
                error: error,
                onOpen: onOpenTrip,
              )
            : _TripStatusOverlay(mode: mode, trip: trip),
      ),
      const SizedBox(width: 10),
      Material(
        color: Colors.white,
        elevation: 4,
        shadowColor: const Color(0x1F000000),
        shape: const CircleBorder(),
        child: InkWell(
          onTap: onOpenMessages,
          customBorder: const CircleBorder(),
          child: const SizedBox(
            width: 48,
            height: 48,
            child: Stack(
              children: [
                Center(
                  child: Icon(
                    LucideIcons.messageCircle,
                    color: AppColors.primary,
                    size: 22,
                  ),
                ),
                Positioned(
                  right: 9,
                  top: 8,
                  child: CircleAvatar(
                    radius: 7,
                    backgroundColor: AppColors.danger,
                    child: Text(
                      '3',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 8,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    ],
  );
}

class _NearbyTeamsOverlay extends StatelessWidget {
  const _NearbyTeamsOverlay({
    required this.trips,
    required this.loading,
    required this.error,
    required this.onOpen,
  });

  final List<TripRecommendModel> trips;
  final bool loading;
  final String? error;
  final ValueChanged<TripRecommendModel> onOpen;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white.withValues(alpha: .97),
    borderRadius: BorderRadius.circular(20),
    elevation: 4,
    shadowColor: const Color(0x1C000000),
    child: Padding(
      padding: const EdgeInsets.fromLTRB(14, 13, 14, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Row(
            children: [
              Icon(LucideIcons.flame, color: AppColors.warning, size: 17),
              SizedBox(width: 6),
              Expanded(
                child: Text(
                  '附近热门队伍',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w900),
                ),
              ),
            ],
          ),
          const SizedBox(height: 3),
          const Text(
            '按当前位置发现近期出发的同行队伍',
            style: TextStyle(color: AppColors.muted, fontSize: 11),
          ),
          if (loading) ...[
            const SizedBox(height: 10),
            const LinearProgressIndicator(minHeight: 2),
          ] else if (trips.isNotEmpty) ...[
            const SizedBox(height: 9),
            ...trips.take(2).map(
              (trip) => InkWell(
                onTap: () => onOpen(trip),
                borderRadius: BorderRadius.circular(12),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    children: [
                      Container(
                        width: 28,
                        height: 28,
                        decoration: const BoxDecoration(
                          color: AppColors.primarySoft,
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(
                          LucideIcons.carFront,
                          size: 14,
                          color: AppColors.primary,
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              trip.tripName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            Text(
                              '${trip.currentVehicleCount}/${trip.vehicleLimit}车 · ${_distanceLabel(trip.distanceMeters)}',
                              style: const TextStyle(
                                color: AppColors.muted,
                                fontSize: 10,
                              ),
                            ),
                          ],
                        ),
                      ),
                      if (trip.heat != null)
                        Text(
                          '热度 ${trip.heat}',
                          style: const TextStyle(
                            color: AppColors.warning,
                            fontSize: 10,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ] else if (error != null) ...[
            const SizedBox(height: 8),
            const Text(
              '热门队伍暂时加载失败',
              style: TextStyle(color: AppColors.muted, fontSize: 11),
            ),
          ],
        ],
      ),
    ),
  );
}

class _TripStatusOverlay extends StatelessWidget {
  const _TripStatusOverlay({required this.mode, required this.trip});

  final _MapMode mode;
  final TripModel? trip;

  @override
  Widget build(BuildContext context) {
    final ended = mode == _MapMode.ended;
    return Material(
      color: Colors.white.withValues(alpha: .97),
      borderRadius: BorderRadius.circular(20),
      elevation: 4,
      shadowColor: const Color(0x1C000000),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 9,
                  height: 9,
                  decoration: BoxDecoration(
                    color: ended ? AppColors.success : AppColors.primary,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 7),
                Text(
                  ended ? '行程已完成' : '${trip?.title ?? '当前行程'} · 行进中',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w900),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              ended
                  ? '${trip?.startName ?? '起点'}至${trip?.endName ?? '终点'} · 全员已到达 · 轨迹已保存'
                  : '${trip?.startName ?? '起点'} → ${trip?.endName ?? '终点'} · 位置持续更新',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AppColors.secondaryText,
                fontSize: 11,
                height: 1.4,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MapBottomPanel extends StatelessWidget {
  const _MapBottomPanel({
    required this.mode,
    required this.trip,
    required this.onSearch,
    required this.onPublish,
    required this.onRecommendations,
    required this.onNavigate,
    required this.onChat,
    required this.onSos,
    required this.onReportPosition,
  });

  final _MapMode mode;
  final TripModel? trip;
  final VoidCallback onSearch;
  final VoidCallback onPublish;
  final VoidCallback? onRecommendations;
  final VoidCallback onNavigate;
  final VoidCallback onChat;
  final VoidCallback onSos;
  final VoidCallback onReportPosition;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: const BorderRadius.vertical(top: Radius.circular(26)),
    clipBehavior: Clip.antiAlias,
    elevation: 12,
    shadowColor: const Color(0x26000000),
    child: Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 14),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 42,
            height: 4,
            decoration: BoxDecoration(
              color: const Color(0xFFD5DAE3),
              borderRadius: BorderRadius.circular(99),
            ),
          ),
          const SizedBox(height: 12),
          if (mode == _MapMode.normal)
            _NormalPanel(
              onSearch: onSearch,
              onPublish: onPublish,
              onRecommendations: onRecommendations,
            )
          else if (mode == _MapMode.tracking)
            _TrackingPanel(
              trip: trip,
              onNavigate: onNavigate,
              onChat: onChat,
              onSos: onSos,
              onReportPosition: onReportPosition,
            )
          else
            _EndedPanel(
              trip: trip,
              onRecommendations: onRecommendations,
              onPublish: onPublish,
              onChat: onChat,
            ),
        ],
      ),
    ),
  );
}

class _NormalPanel extends StatelessWidget {
  const _NormalPanel({
    required this.onSearch,
    required this.onPublish,
    required this.onRecommendations,
  });

  final VoidCallback onSearch;
  final VoidCallback onPublish;
  final VoidCallback? onRecommendations;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const Row(
        children: [
          CircleAvatar(
            radius: 20,
            backgroundColor: AppColors.primarySoft,
            child: Icon(LucideIcons.mapPinned, color: AppColors.primary, size: 20),
          ),
          SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '当前状态',
                  style: TextStyle(color: AppColors.muted, fontSize: 11),
                ),
                SizedBox(height: 2),
                Text(
                  '你还没有行程',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                ),
              ],
            ),
          ),
        ],
      ),
      const SizedBox(height: 8),
      const Text(
        '加入别人的队伍，或自己发布一个行程。也可以直接查看附近热门队伍。',
        style: TextStyle(
          color: AppColors.secondaryText,
          fontSize: 12,
          height: 1.5,
        ),
      ),
      const SizedBox(height: 13),
      Row(
        children: [
          Expanded(
            child: _PanelAction(
              icon: LucideIcons.search,
              title: '搜索找队伍',
              subtitle: '发现附近同行者',
              onTap: onSearch,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: _PanelAction(
              icon: LucideIcons.plus,
              title: '发布新行程',
              subtitle: '创建自己的队伍',
              onTap: onPublish,
            ),
          ),
        ],
      ),
      const SizedBox(height: 11),
      SizedBox(
        width: double.infinity,
        child: FilledButton.icon(
          onPressed: onRecommendations,
          icon: const Icon(LucideIcons.sparkles, size: 18),
          label: const Text('去看看推荐行程'),
        ),
      ),
    ],
  );
}

class _TrackingPanel extends StatelessWidget {
  const _TrackingPanel({
    required this.trip,
    required this.onNavigate,
    required this.onChat,
    required this.onSos,
    required this.onReportPosition,
  });

  final TripModel? trip;
  final VoidCallback onNavigate;
  final VoidCallback onChat;
  final VoidCallback onSos;
  final VoidCallback onReportPosition;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  trip?.title ?? '当前行程',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 4),
                Text(
                  '${trip?.startName ?? '起点'} → ${trip?.endName ?? '终点'}',
                  style: const TextStyle(
                    color: AppColors.secondaryText,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: const Color(0xFFEAF8EF),
              borderRadius: BorderRadius.circular(99),
            ),
            child: const Row(
              children: [
                Icon(LucideIcons.radio, size: 13, color: AppColors.success),
                SizedBox(width: 5),
                Text(
                  '位置共享中',
                  style: TextStyle(
                    color: AppColors.success,
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      const SizedBox(height: 12),
      Row(
        children: [
          Expanded(child: _TripMetric(label: '剩余', value: '持续计算')),
          Expanded(child: _TripMetric(label: '预计', value: '导航更新')),
          Expanded(
            child: _TripMetric(label: '同行车队', value: _vehicleLabel(trip)),
          ),
        ],
      ),
      const SizedBox(height: 13),
      Row(
        children: [
          Expanded(
            child: _RoundAction(
              icon: LucideIcons.navigation,
              label: '共享位置',
              onTap: onNavigate,
            ),
          ),
          Expanded(
            child: _RoundAction(
              icon: LucideIcons.mapPinCheck,
              label: '报位置',
              onTap: onReportPosition,
            ),
          ),
          Expanded(
            child: _RoundAction(
              icon: LucideIcons.siren,
              label: 'SOS',
              danger: true,
              onTap: onSos,
            ),
          ),
        ],
      ),
      const SizedBox(height: 10),
      SizedBox(
        width: double.infinity,
        child: FilledButton.icon(
          onPressed: onChat,
          icon: const Icon(LucideIcons.messageCircle, size: 18),
          label: const Text('进入群聊 · 查看新消息'),
        ),
      ),
    ],
  );
}

class _EndedPanel extends StatelessWidget {
  const _EndedPanel({
    required this.trip,
    required this.onRecommendations,
    required this.onPublish,
    required this.onChat,
  });

  final TripModel? trip;
  final VoidCallback? onRecommendations;
  final VoidCallback onPublish;
  final VoidCallback onChat;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const Row(
        children: [
          CircleAvatar(
            radius: 21,
            backgroundColor: Color(0xFFEAF8EF),
            child: Icon(LucideIcons.flag, color: AppColors.success),
          ),
          SizedBox(width: 11),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '旅程完成',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                ),
                SizedBox(height: 3),
                Text(
                  '全员顺利到达，轨迹已保存',
                  style: TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              ],
            ),
          ),
        ],
      ),
      const SizedBox(height: 11),
      Row(
        children: [
          Expanded(
            child: _TripMetric(
              label: '总里程',
              value: _distanceLabel(trip?.distanceMeters ?? 0),
            ),
          ),
          const Expanded(child: _TripMetric(label: '总耗时', value: '已完成')),
          Expanded(
            child: _TripMetric(label: '同行车队', value: _vehicleLabel(trip)),
          ),
        ],
      ),
      const SizedBox(height: 13),
      SizedBox(
        width: double.infinity,
        child: FilledButton.icon(
          onPressed: onRecommendations,
          icon: const Icon(LucideIcons.sparkles, size: 18),
          label: const Text('去看看推荐行程'),
        ),
      ),
      const SizedBox(height: 9),
      Row(
        children: [
          Expanded(
            child: OutlinedButton.icon(
              onPressed: onPublish,
              icon: const Icon(LucideIcons.route, size: 17),
              label: const Text('再次出发'),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: OutlinedButton.icon(
              onPressed: onChat,
              icon: const Icon(LucideIcons.messageCircle, size: 17),
              label: const Text('回到群聊'),
            ),
          ),
        ],
      ),
    ],
  );
}

class _PanelAction extends StatelessWidget {
  const _PanelAction({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: const Color(0xFFF4F7FC),
    borderRadius: BorderRadius.circular(18),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(18),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: const BoxDecoration(
                color: AppColors.primarySoft,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 18, color: AppColors.primary),
            ),
            const SizedBox(width: 9),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w900),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: AppColors.muted, fontSize: 9.5),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _RoundAction extends StatelessWidget {
  const _RoundAction({
    required this.icon,
    required this.label,
    required this.onTap,
    this.danger = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool danger;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(16),
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Column(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: danger ? const Color(0xFFFFECEA) : AppColors.primarySoft,
              shape: BoxShape.circle,
            ),
            child: Icon(
              icon,
              color: danger ? AppColors.danger : AppColors.primary,
              size: 19,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            label,
            style: TextStyle(
              color: danger ? AppColors.danger : AppColors.secondaryText,
              fontSize: 11,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    ),
  );
}

class _TripMetric extends StatelessWidget {
  const _TripMetric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(
        value,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w900),
      ),
      const SizedBox(height: 3),
      Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 10)),
    ],
  );
}

class _MapSurface extends StatelessWidget {
  const _MapSurface({
    required this.nativeEnabled,
    required this.checkingSupport,
    required this.locationEnabled,
    required this.trip,
    required this.mode,
    required this.onMapCreated,
    required this.onLocationChanged,
  });

  final bool nativeEnabled;
  final bool checkingSupport;
  final bool locationEnabled;
  final TripModel? trip;
  final _MapMode mode;
  final ValueChanged<AMapController> onMapCreated;
  final ValueChanged<AMapLocation> onLocationChanged;

  static const privacy = AMapPrivacyStatement(
    hasContains: true,
    hasShow: true,
    hasAgree: true,
  );

  @override
  Widget build(BuildContext context) {
    final route = trip?.routePoints ?? const <LocationSelection>[];
    if (nativeEnabled) {
      AMapInitializer.init(context);
      AMapInitializer.updatePrivacyAgree(privacy);
      final markers = <Marker>{};
      if (route.isNotEmpty) {
        markers.add(
          Marker(
            position: LatLng(route.first.latitude, route.first.longitude),
            infoWindow: InfoWindow(title: trip?.startName ?? '起点'),
          ),
        );
        if (route.length > 1) {
          markers.add(
            Marker(
              position: LatLng(route.last.latitude, route.last.longitude),
              infoWindow: InfoWindow(title: trip?.endName ?? '终点'),
            ),
          );
        }
      }
      final polylines = route.length >= 2
          ? {
              Polyline(
                points: route
                    .map((point) => LatLng(point.latitude, point.longitude))
                    .toList(growable: false),
                width: 8,
                color: mode == _MapMode.ended
                    ? AppColors.success
                    : AppColors.primary,
                capType: CapType.round,
                joinType: JoinType.round,
              ),
            }
          : const <Polyline>{};
      return AMapWidget(
        initialCameraPosition: CameraPosition(
          target: route.isEmpty
              ? const LatLng(23.1291, 113.2644)
              : LatLng(route.first.latitude, route.first.longitude),
          zoom: route.isEmpty ? 11.5 : 10.5,
        ),
        trafficEnabled: false,
        myLocationStyleOptions: MyLocationStyleOptions(
          locationEnabled,
          circleFillColor: const Color(0x223A86FF),
          circleStrokeColor: AppColors.primary,
          circleStrokeWidth: 1,
        ),
        markers: markers,
        polylines: polylines,
        onMapCreated: onMapCreated,
        onLocationChanged: onLocationChanged,
      );
    }
    return Stack(
      fit: StackFit.expand,
      children: [
        CustomPaint(
          painter: _RoadPainter(
            routeVisible: route.length >= 2,
            ended: mode == _MapMode.ended,
          ),
          child: const ColoredBox(color: Color(0xFFF2F6FC)),
        ),
        const Positioned(left: 36, top: 150, child: _MapLabel('天河')),
        const Positioned(right: 54, top: 208, child: _MapLabel('白云山')),
        const Positioned(left: 62, top: 310, child: _MapLabel('珠江新城')),
        const Positioned(right: 42, top: 385, child: _MapLabel('黄埔')),
        if (MapHomePage.nativeAmap && !checkingSupport)
          Center(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 55),
              padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 11),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: .92),
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Text(
                '当前模拟器不支持高德原生地图\n请使用 ARM64 Android 真机预览',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppColors.secondaryText, height: 1.5),
              ),
            ),
          ),
      ],
    );
  }
}

class _MapLabel extends StatelessWidget {
  const _MapLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) => Text(
    label,
    style: const TextStyle(
      color: Color(0xFF8390A4),
      fontSize: 12,
      fontWeight: FontWeight.w700,
    ),
  );
}

class _RoadPainter extends CustomPainter {
  const _RoadPainter({required this.routeVisible, required this.ended});

  final bool routeVisible;
  final bool ended;

  @override
  void paint(Canvas canvas, Size size) {
    final minorRoad = Paint()
      ..color = const Color(0xFFDDE8F5)
      ..strokeWidth = 13
      ..strokeCap = StrokeCap.round;
    canvas.drawLine(
      Offset(-30, size.height * .30),
      Offset(size.width + 40, size.height * .18),
      minorRoad,
    );
    canvas.drawLine(
      Offset(10, size.height * .60),
      Offset(size.width + 20, size.height * .46),
      minorRoad,
    );
    canvas.drawLine(
      Offset(size.width * .30, -30),
      Offset(size.width * .68, size.height),
      minorRoad,
    );
    if (routeVisible) {
      final route = Paint()
        ..color = ended ? AppColors.success : AppColors.primary
        ..strokeWidth = 8
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round;
      final path = Path()
        ..moveTo(size.width * .14, size.height * .66)
        ..cubicTo(
          size.width * .30,
          size.height * .50,
          size.width * .56,
          size.height * .40,
          size.width * .82,
          size.height * .24,
        );
      canvas.drawPath(path, route);
      canvas.drawCircle(
        Offset(size.width * .14, size.height * .66),
        10,
        Paint()..color = AppColors.primary,
      );
      canvas.drawCircle(
        Offset(size.width * .82, size.height * .24),
        10,
        Paint()..color = ended ? AppColors.success : AppColors.primaryDark,
      );
    } else {
      canvas.drawCircle(
        Offset(size.width * .48, size.height * .44),
        11,
        Paint()..color = AppColors.primary,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _RoadPainter oldDelegate) =>
      routeVisible != oldDelegate.routeVisible || ended != oldDelegate.ended;
}

bool _validLocation(AMapLocation location) {
  final latitude = location.latLng.latitude;
  final longitude = location.latLng.longitude;
  return latitude.abs() <= 90 &&
      longitude.abs() <= 180 &&
      latitude != 0 &&
      longitude != 0;
}

bool _isRunning(String status) {
  final value = status.toUpperCase();
  return value == 'RUNNING' || value == 'ONGOING' || value == 'STARTED';
}

bool _isCompleted(String status) {
  final value = status.toUpperCase();
  return value == 'FINISHED' ||
      value == 'ENDED' ||
      value == 'SETTLED' ||
      value == 'COMPLETED';
}

String _vehicleLabel(TripModel? trip) {
  if (trip == null) return '0/0车';
  final max = trip.maxVehicles <= 0 ? '?' : trip.maxVehicles.toString();
  return '${trip.joinedVehicles}/$max车';
}

String _distanceLabel(int meters) {
  if (meters <= 0) return '距离计算中';
  if (meters < 1000) return '${meters}m';
  final km = meters / 1000;
  return km >= 10 ? '${km.round()}km' : '${km.toStringAsFixed(1)}km';
}
