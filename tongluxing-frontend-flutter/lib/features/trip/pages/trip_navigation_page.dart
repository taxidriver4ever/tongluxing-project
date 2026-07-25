import 'dart:async';
import 'dart:math' as math;

import 'package:amap_map/amap_map.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:x_amap_base/x_amap_base.dart';

import '../../../app/app_session.dart';
import '../../../app/routes.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../widgets/route_map_view.dart';

class TripNavigationPage extends StatefulWidget {
  const TripNavigationPage({required this.trip, super.key});
  final TripModel trip;

  @override
  State<TripNavigationPage> createState() => _TripNavigationPageState();
}

class _TripNavigationPageState extends State<TripNavigationPage> {
  static const voice = MethodChannel('com.tongluxing/navigation_voice');
  static const location = MethodChannel('com.tongluxing/permissions');

  bool ending = false;
  bool voiceEnabled = true;
  bool uploadingTrack = false;
  bool confirmingArrival = false;
  int trackedDistanceMeters = 0;
  int completedWaypointCount = 0;
  DateTime? lastTrackUploadAt;
  Timer? trackTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _announce();
      _startTracking();
    });
  }

  Future<void> _startTracking() async {
    final granted =
        await location.invokeMethod<bool>('requestLocation') ?? false;
    if (!granted || !mounted) return;
    await _uploadCurrentPoint();
    trackTimer = Timer.periodic(
      const Duration(seconds: 15),
      (_) => _uploadCurrentPoint(),
    );
  }

  Future<void> _uploadCurrentPoint({bool force = false}) async {
    if (uploadingTrack || !mounted) return;
    final lastUpload = lastTrackUploadAt;
    if (!force &&
        lastUpload != null &&
        DateTime.now().difference(lastUpload) < const Duration(seconds: 10)) {
      return;
    }
    try {
      final raw = await location.invokeMapMethod<String, dynamic>(
        'getCurrentLocation',
      );
      if (raw == null || !mounted) return;
      await _uploadTrackValues(
        longitude: (raw['longitude'] as num).toDouble(),
        latitude: (raw['latitude'] as num).toDouble(),
        speed: (raw['speed'] as num?)?.toDouble(),
        direction: (raw['direction'] as num?)?.toDouble(),
        accuracy: (raw['accuracy'] as num?)?.toDouble(),
      );
    } on PlatformException {
      // 定位暂不可用时保留导航界面，下一周期自动重试。
    }
  }

  Future<void> _confirmArrival() async {
    if (confirmingArrival || uploadingTrack) return;
    final before = completedWaypointCount;
    setState(() => confirmingArrival = true);
    await _uploadCurrentPoint(force: true);
    if (!mounted) return;
    setState(() => confirmingArrival = false);
    if (completedWaypointCount == before) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          const SnackBar(
            content: Text('尚未进入当前节点 150 米范围，请靠近后再确认'),
          ),
        );
    }
  }

  Future<void> _handleAmapLocation(AMapLocation value) async {
    if (!isLocationValid(value)) return;
    final lastUpload = lastTrackUploadAt;
    if (lastUpload != null &&
        DateTime.now().difference(lastUpload) < const Duration(seconds: 10)) {
      return;
    }
    await _uploadTrackValues(
      longitude: value.latLng.longitude,
      latitude: value.latLng.latitude,
      speed: value.speed,
      direction: value.bearing,
      accuracy: value.accuracy,
    );
  }

  Future<void> _uploadTrackValues({
    required double longitude,
    required double latitude,
    double? speed,
    double? direction,
    double? accuracy,
  }) async {
    if (uploadingTrack || !mounted) return;
    uploadingTrack = true;
    try {
      final response = await TripService(context.read<AppSession>().api)
          .uploadTrackPoint(
            tripId: widget.trip.id,
            longitude: longitude,
            latitude: latitude,
            speed: speed,
            direction: direction,
            accuracy: accuracy,
            recordTime: DateTime.now(),
          );
      lastTrackUploadAt = DateTime.now();
      if (!mounted) return;
      setState(() {
        trackedDistanceMeters =
            (response['totalDistance'] as num?)?.toInt() ??
            trackedDistanceMeters;
      });
      _notifySettlement(response);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text('轨迹同步失败：$error')));
      }
    } finally {
      uploadingTrack = false;
    }
  }

  Future<void> _openAmapNavigation() async {
    final points = _routePoints;
    final target = _currentTarget;
    if (points.isEmpty || target == null) return;
    final start = widget.trip.startLocation ?? points.first;
    final end = target;
    final nativeUri = Uri(
      scheme: 'amapuri',
      host: 'route',
      path: '/plan/',
      queryParameters: {
        'sourceApplication': '同路行',
        'sid': 'TLX_START',
        'slat': '${start.latitude}',
        'slon': '${start.longitude}',
        'sname': widget.trip.startName,
        'did': 'TLX_END',
        'dlat': '${end.latitude}',
        'dlon': '${end.longitude}',
        'dname': end.name,
        'dev': '0',
        't': '0',
      },
    );
    try {
      if (await launchUrl(nativeUri, mode: LaunchMode.externalApplication)) {
        return;
      }
      final webUri = Uri.https('uri.amap.com', '/navigation', {
        'to': '${end.longitude},${end.latitude},${end.name}',
        'mode': 'car',
        'policy': '1',
        'src': 'tongluxing',
        'coordinate': 'gaode',
        'callnative': '1',
      });
      if (await launchUrl(webUri, mode: LaunchMode.externalApplication)) {
        return;
      }
    } catch (_) {
      // 统一在下方提示，避免第三方应用未安装时抛错中断导航页。
    }
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('无法打开高德导航，请确认已安装高德地图')),
      );
    }
  }

  void _notifySettlement(Map<String, dynamic> response) {
    final points = (response['grantedPoints'] as num?)?.toInt() ?? 0;
    final waypoint = response['reachedWaypointName']?.toString();
    if (!mounted) return;
    if (waypoint != null && waypoint.isNotEmpty) {
      setState(() {
        completedWaypointCount = math.min(
          completedWaypointCount + 1,
          widget.trip.waypoints.length,
        );
      });
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(
              points > 0
                  ? '已按顺序到达节点“$waypoint”，本阶段已结算并发放 +$points 成长值'
                  : '已按顺序到达节点“$waypoint”，下一导航目标已更新',
            ),
          ),
        );
      _announceCurrentStage();
      return;
    }
    if (points > 0) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('完成新的 50 公里里程阶段，实时发放 +$points 成长值')));
    }
  }

  LocationSelection? get _currentTarget {
    if (completedWaypointCount < widget.trip.waypoints.length) {
      return widget.trip.waypoints[completedWaypointCount];
    }
    return widget.trip.endLocation ?? (_routePoints.isEmpty ? null : _routePoints.last);
  }

  String get _routeText {
    final target = _currentTarget;
    if (target == null) return '${widget.trip.startName}前往${widget.trip.endName}';
    return '当前阶段：前往 ${target.name}';
  }

  List<LocationSelection> get _routePoints => widget.trip.routePoints;

  List<LocationSelection> get _routeStops {
    final points = _routePoints;
    if (points.length < 2) return const <LocationSelection>[];
    return <LocationSelection>[
      widget.trip.startLocation ?? points.first,
      ...widget.trip.waypoints,
      widget.trip.endLocation ?? points.last,
    ];
  }

  Future<void> _announce() async {
    if (!voiceEnabled) return;
    try {
      await voice.invokeMethod('speak', {'text': '行程记录已开始。$_routeText。实际道路可根据路况灵活选择，请注意行车安全。'});
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('系统语音引擎暂不可用')));
      }
    }
  }


  Future<void> _announceCurrentStage() async {
    if (!voiceEnabled) return;
    final target = _currentTarget;
    if (target == null) return;
    try {
      await voice.invokeMethod('speak', {
        'text': '节点已完成，下一导航目标是${target.name}。',
      });
    } catch (_) {
      // 语音不可用不影响节点切换。
    }
  }
  Future<void> _toggleVoice() async {
    setState(() => voiceEnabled = !voiceEnabled);
    if (voiceEnabled) {
      await _announce();
    } else {
      await voice.invokeMethod('stop');
    }
  }

  Future<void> end() async {
    final ok =
        await showDialog<bool>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: const Text('结束行程？'),
            content: const Text('将停止轨迹采集并进入行程结算。'),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(dialogContext, false),
                child: const Text('取消'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(dialogContext, true),
                child: const Text('确认结束'),
              ),
            ],
          ),
        ) ??
        false;
    if (!ok || !mounted) return;
    trackTimer?.cancel();
    setState(() => ending = true);
    try {
      final service = TripService(context.read<AppSession>().api);
      await service.end(widget.trip.id);
      if (!mounted) return;
      final settleNow =
          await showModalBottomSheet<bool>(
            context: context,
            isDismissible: false,
            enableDrag: false,
            builder: (sheetContext) => SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const CircleAvatar(
                      radius: 30,
                      backgroundColor: Color(0xFFE8F7EE),
                      child: Icon(
                        LucideIcons.flag,
                        color: Color(0xFF16A05D),
                        size: 30,
                      ),
                    ),
                    const SizedBox(height: 14),
                    const Text(
                      '行程已结束',
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      '实际里程已在行驶过程中按途经点和每 50 公里分段发放。',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: AppColors.secondaryText),
                    ),
                    const SizedBox(height: 22),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed: () => Navigator.pop(sheetContext, true),
                        child: const Text('完成最终行程结算'),
                      ),
                    ),
                    TextButton(
                      onPressed: () => Navigator.pop(sheetContext, false),
                      child: const Text('稍后在行程详情结算'),
                    ),
                  ],
                ),
              ),
            ),
          ) ??
          false;
      if (!mounted) return;
      if (!settleNow) {
        Navigator.pushNamedAndRemoveUntil(
          context,
          AppRoutes.home,
          (_) => false,
        );
        return;
      }
      final result = await service.settle(widget.trip.id);
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          icon: const Icon(
            LucideIcons.sparkles,
            color: AppColors.primary,
            size: 36,
          ),
          title: Text(result.duplicate ? '该行程已结算' : '行程结算完成'),
          content: Text(
            '有效成员：${result.memberCount} 人\n'
            '行程结束不发固定奖励，成长值按累计每 50 公里实时发放\n'
            '实际驾驶里程：${(trackedDistanceMeters / 1000).toStringAsFixed(1)} km',
            textAlign: TextAlign.center,
          ),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('完成'),
            ),
          ],
        ),
      );
      if (mounted) {
        Navigator.pushNamedAndRemoveUntil(
          context,
          AppRoutes.home,
          (_) => false,
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
    if (mounted) setState(() => ending = false);
  }

  @override
  void dispose() {
    trackTimer?.cancel();
    voice.invokeMethod<void>('stop');
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: Stack(
        fit: StackFit.expand,
        children: [
          Positioned.fill(
            child: RouteMapView(
              polylinePoints: _routePoints,
              stops: _routeStops,
              height: MediaQuery.sizeOf(context).height,
              showMyLocation: true,
              trafficEnabled: true,
              onLocationChanged: _handleAmapLocation,
            ),
          ),
          Positioned(
            left: 18,
            right: 18,
            top: 18,
            child: Row(
              children: [
                IconButton.filled(
                  onPressed: () => Navigator.pop(context),
                  icon: const Icon(LucideIcons.arrowLeft),
                ),
                const Spacer(),
                IconButton.filled(
                  onPressed: _toggleVoice,
                  icon: Icon(
                    voiceEnabled ? LucideIcons.volume2 : LucideIcons.volumeX,
                  ),
                ),
              ],
            ),
          ),
          Positioned(
            left: 16,
            right: 16,
            top: 82,
            child: Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: const Color(0xFF285CFF),
                borderRadius: BorderRadius.circular(22),
              ),
              child: Row(
                children: [
                  const Icon(LucideIcons.route, color: Colors.white, size: 34),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '已完成 $completedWaypointCount / ${widget.trip.waypoints.length} 个途经节点',
                          style: const TextStyle(color: Colors.white70),
                        ),
                        Text(
                          _routeText,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 20,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          Positioned(
            left: 16,
            right: 16,
            bottom: 20,
            child: Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(26),
                boxShadow: const [
                  BoxShadow(color: Color(0x22000000), blurRadius: 24),
                ],
              ),
              child: Column(
                children: [
                  Row(
                    children: [
                      const Icon(LucideIcons.mapPinCheck, color: AppColors.primary),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text('下一导航目标', style: TextStyle(fontSize: 11, color: AppColors.muted)),
                            Text(
                              _currentTarget?.name ?? widget.trip.endName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontWeight: FontWeight.w900),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    '路线仅供参考，系统只记录轨迹并按顺序判断节点到达。',
                    style: TextStyle(fontSize: 11, color: AppColors.secondaryText),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      _Metric(
                        value:
                            '${(trackedDistanceMeters / 1000).toStringAsFixed(1)} km',
                        label: uploadingTrack ? '轨迹同步中' : '实际里程',
                      ),
                      _Metric(
                        value: widget.trip.distanceMeters == null
                            ? '--'
                            : '${((widget.trip.distanceMeters ?? 0) / 1000).round()} km',
                        label: '参考里程',
                      ),
                      _Metric(
                        value: '$completedWaypointCount/${widget.trip.waypoints.length}',
                        label: '节点进度',
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: _openAmapNavigation,
                      icon: const Icon(LucideIcons.navigation, size: 18),
                      label: Text('导航到 ${_currentTarget?.name ?? widget.trip.endName}'),
                    ),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: confirmingArrival || uploadingTrack
                          ? null
                          : _confirmArrival,
                      icon: const Icon(LucideIcons.mapPinCheck, size: 18),
                      label: Text(
                        confirmingArrival ? '正在核验位置…' : '确认到达当前节点',
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  FilledButton(
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.danger,
                    ),
                    onPressed: ending ? null : end,
                    child: Text(ending ? '正在结束…' : '结束行程'),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class _Metric extends StatelessWidget {
  const _Metric({required this.value, required this.label});
  final String value;
  final String label;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(
        value,
        style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
      ),
      Text(label, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
    ],
  );
}
