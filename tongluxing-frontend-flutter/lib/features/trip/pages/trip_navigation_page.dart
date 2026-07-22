import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';

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
  bool mockingDeviation = false;
  int trackedDistanceMeters = 0;
  int deviationStatus = 0;
  int deviationDistance = 0;
  DateTime? lastDeviationAnnouncement;
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

  Future<void> _uploadCurrentPoint() async {
    if (uploadingTrack || !mounted) return;
    uploadingTrack = true;
    try {
      final raw = await location.invokeMapMethod<String, dynamic>(
        'getCurrentLocation',
      );
      if (raw == null || !mounted) return;
      final response = await TripService(context.read<AppSession>().api)
          .uploadTrackPoint(
            tripId: widget.trip.id,
            longitude: (raw['longitude'] as num).toDouble(),
            latitude: (raw['latitude'] as num).toDouble(),
            speed: (raw['speed'] as num?)?.toDouble(),
            direction: (raw['direction'] as num?)?.toDouble(),
            accuracy: (raw['accuracy'] as num?)?.toDouble(),
            recordTime: DateTime.now(),
          );
      if (!mounted) return;
      setState(() {
        trackedDistanceMeters =
            (response['totalDistance'] as num?)?.toInt() ??
            trackedDistanceMeters;
        deviationStatus =
            (response['deviationStatus'] as num?)?.toInt() ?? 0;
        deviationDistance =
            (response['deviationDistance'] as num?)?.toInt() ?? 0;
      });
      await _notifyDeviationIfNeeded();
      _notifySettlement(response);
    } on PlatformException {
      // 定位暂不可用时保留导航界面，下一周期自动重试。
    } finally {
      uploadingTrack = false;
    }
  }

  Future<void> _mockDeviation() async {
    if (mockingDeviation) return;
    setState(() => mockingDeviation = true);
    try {
      final nextStatus = deviationStatus == 0 ? 1 : 0;
      final response = await TripService(
        context.read<AppSession>().api,
      ).mockDeviation(widget.trip.id, status: nextStatus);
      if (!mounted) return;
      setState(() {
        deviationStatus =
            (response['deviationStatus'] as num?)?.toInt() ?? nextStatus;
        deviationDistance =
            (response['deviationDistance'] as num?)?.toInt() ?? 0;
      });
      await _notifyDeviationIfNeeded(force: true);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$e')),
        );
      }
    } finally {
      if (mounted) setState(() => mockingDeviation = false);
    }
  }

  Future<void> _notifyDeviationIfNeeded({bool force = false}) async {
    if (deviationStatus <= 0) return;
    final now = DateTime.now();
    if (!force &&
        lastDeviationAnnouncement != null &&
        now.difference(lastDeviationAnnouncement!) <
            const Duration(minutes: 1)) {
      return;
    }
    lastDeviationAnnouncement = now;
    if (voiceEnabled) {
      await voice.invokeMethod('speak', {
        'text': '检测到偏航约$deviationDistance米。您可以继续当前路线，系统不会强制纠偏。',
      });
    }
  }

  void _notifySettlement(Map<String, dynamic> response) {
    final points = (response['grantedPoints'] as num?)?.toInt() ?? 0;
    final waypoint = response['reachedWaypointName']?.toString();
    if (points <= 0 || !mounted) return;
    final message = waypoint != null && waypoint.isNotEmpty
        ? '已到达途经点“$waypoint”，实时发放 +$points 成长值'
        : '完成新的 50 公里里程阶段，实时发放 +$points 成长值';
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  String get _routeText {
    final stops = widget.trip.waypoints.map((e) => e.name).join('、');
    return stops.isEmpty
        ? '${widget.trip.startName}前往${widget.trip.endName}'
        : '${widget.trip.startName}出发，途经$stops，前往${widget.trip.endName}';
  }

  List<LocationSelection> get _routePoints {
    final raw = widget.trip.routePolyline;
    if (raw != null && raw.trim().isNotEmpty) {
      try {
        final values = jsonDecode(raw);
        if (values is List) {
          final points = values
              .whereType<Map>()
              .map(
                (value) => LocationSelection.fromJson(
                  Map<String, dynamic>.from(value),
                ),
              )
              .where((point) => point.latitude != 0 || point.longitude != 0)
              .toList();
          if (points.length >= 2) return points;
        }
      } catch (_) {
        // 历史 polyline 无法解析时使用起点、途经点和终点兜底。
      }
    }
    return [
      if (widget.trip.startLocation != null) widget.trip.startLocation!,
      ...widget.trip.waypoints,
      if (widget.trip.endLocation != null) widget.trip.endLocation!,
    ];
  }

  Future<void> _announce() async {
    if (!voiceEnabled) return;
    try {
      await voice.invokeMethod('speak', {'text': '导航开始，$_routeText。请注意行车安全。'});
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('系统语音引擎暂不可用')));
      }
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
                      style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
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
        Navigator.popUntil(context, (route) => route.isFirst);
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
            '最终行程奖励：每人 +${result.pointsPerMember} 成长值\n'
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
      if (mounted) Navigator.popUntil(context, (route) => route.isFirst);
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
            child: Container(
              color: const Color(0xFFEAF2FF),
              child: CustomPaint(painter: _NavPainter(_routePoints)),
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
                          '${widget.trip.waypoints.length} 个经停点 · 规划路线已保存',
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
          if (deviationStatus > 0)
            Positioned(
              left: 16,
              right: 16,
              top: 184,
              child: Material(
                color: deviationStatus >= 2
                    ? const Color(0xFFFFE7E4)
                    : const Color(0xFFFFF3D8),
                borderRadius: BorderRadius.circular(18),
                elevation: 2,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 13, 10, 13),
                  child: Row(
                    children: [
                      Icon(
                        LucideIcons.triangleAlert,
                        color: deviationStatus >= 2
                            ? AppColors.danger
                            : const Color(0xFFD88700),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          '检测到偏航约 $deviationDistance 米。仅提醒队长，可继续偏航，系统不会强制纠偏。',
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                      IconButton(
                        tooltip: '我知道了',
                        onPressed: () => setState(() => deviationStatus = 0),
                        icon: const Icon(LucideIcons.x, size: 19),
                      ),
                    ],
                  ),
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
                        label: '规划里程',
                      ),
                      _Metric(
                        value: '${widget.trip.waypoints.length}',
                        label: '经停点',
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: mockingDeviation ? null : _mockDeviation,
                      icon: const Icon(LucideIcons.flaskConical, size: 18),
                      label: Text(
                        mockingDeviation
                            ? '模拟中…'
                            : deviationStatus > 0
                            ? '结束偏航模拟'
                            : '联调：模拟偏航提醒',
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

class _NavPainter extends CustomPainter {
  const _NavPainter(this.points);
  final List<LocationSelection> points;

  @override
  void paint(Canvas canvas, Size size) {
    final routePaint = Paint()
      ..color = AppColors.primary
      ..strokeWidth = 9
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final shadowPaint = Paint()
      ..color = Colors.white
      ..strokeWidth = 15
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    if (points.length < 2) {
      final fallback = Path()
        ..moveTo(size.width * .2, size.height)
        ..cubicTo(
          size.width * .1,
          size.height * .7,
          size.width * .85,
          size.height * .55,
          size.width * .55,
          0,
        );
      canvas.drawPath(fallback, shadowPaint);
      canvas.drawPath(fallback, routePaint);
      return;
    }

    final minLat = points.map((e) => e.latitude).reduce(math.min);
    final maxLat = points.map((e) => e.latitude).reduce(math.max);
    final minLng = points.map((e) => e.longitude).reduce(math.min);
    final maxLng = points.map((e) => e.longitude).reduce(math.max);
    final latSpan = math.max(0.000001, maxLat - minLat);
    final lngSpan = math.max(0.000001, maxLng - minLng);
    const horizontalPadding = 42.0;
    const topPadding = 240.0;
    const bottomPadding = 210.0;
    final drawHeight = math.max(100.0, size.height - topPadding - bottomPadding);
    final drawWidth = math.max(100.0, size.width - horizontalPadding * 2);

    Offset mapPoint(LocationSelection point) => Offset(
      horizontalPadding + (point.longitude - minLng) / lngSpan * drawWidth,
      topPadding + (maxLat - point.latitude) / latSpan * drawHeight,
    );

    final path = Path()..moveTo(mapPoint(points.first).dx, mapPoint(points.first).dy);
    for (final point in points.skip(1)) {
      final offset = mapPoint(point);
      path.lineTo(offset.dx, offset.dy);
    }
    canvas.drawPath(path, shadowPaint);
    canvas.drawPath(path, routePaint);
  }

  @override
  bool shouldRepaint(covariant _NavPainter oldDelegate) =>
      oldDelegate.points != points;
}
