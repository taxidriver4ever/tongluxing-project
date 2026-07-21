import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import 'package:flutter/services.dart';

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
  int trackedDistanceMeters = 0;
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
      if (mounted) {
        setState(
          () => trackedDistanceMeters =
              (response['totalDistance'] as num?)?.toInt() ??
              trackedDistanceMeters,
        );
      }
    } on PlatformException {
      // 定位暂不可用时保留导航界面，下一周期自动重试。
    } finally {
      uploadingTrack = false;
    }
  }

  String get _routeText {
    final stops = widget.trip.waypoints.map((e) => e.name).join('、');
    return stops.isEmpty
        ? '${widget.trip.startName}前往${widget.trip.endName}'
        : '${widget.trip.startName}出发，途经$stops，前往${widget.trip.endName}';
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
                      style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      '当前状态为“已完成”，确认后将独立结算成长值。',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: AppColors.secondaryText),
                    ),
                    const SizedBox(height: 22),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed: () => Navigator.pop(sheetContext, true),
                        child: const Text('立即结算成长值'),
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
          title: Text(result.duplicate ? '该行程已结算' : '成长值结算完成'),
          content: Text(
            '行程状态：已结算\n'
            '有效成员：${result.memberCount} 人\n'
            '每人获得：+${result.pointsPerMember} 成长值',
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
              child: CustomPaint(painter: _NavPainter()),
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
                          '${widget.trip.waypoints.length} 个经停点',
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
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      _Metric(
                        value:
                            '${(trackedDistanceMeters / 1000).toStringAsFixed(1)} km',
                        label: uploadingTrack ? '轨迹同步中' : '已行驶',
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
                  const SizedBox(height: 16),
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
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = AppColors.primary
      ..strokeWidth = 9
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    final path = Path()
      ..moveTo(size.width * .2, size.height)
      ..cubicTo(
        size.width * .1,
        size.height * .7,
        size.width * .85,
        size.height * .55,
        size.width * .55,
        0,
      );
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
