import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../app/app_session.dart';
import '../../../app/routes.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/track_upload_queue.dart';
import '../widgets/route_map_view.dart';

class TripNavigationPage extends StatefulWidget {
  const TripNavigationPage({required this.trip, super.key});
  final TripModel trip;

  @override
  State<TripNavigationPage> createState() => _TripNavigationPageState();
}

class _TripNavigationPageState extends State<TripNavigationPage>
    with WidgetsBindingObserver {
  static const voice = MethodChannel('com.tongluxing/navigation_voice');
  static const location = MethodChannel('com.tongluxing/permissions');

  bool ending = false;
  bool voiceEnabled = true;
  bool uploadingTrack = false;
  bool requestingLocation = false;
  bool confirmingArrival = false;
  int trackedDistanceMeters = 0;
  int completedWaypointCount = 0;
  int teammateDistanceMeters = 0;
  int teammateDistanceStatus = 0;
  bool showTeamPanel = false;
  bool controlsCollapsed = false;
  DateTime? ignoreMapCollapseUntil;
  String? conversationId;
  List<LocationSelection> teamLocations = const [];
  DateTime? lastTrackUploadAt;
  DateTime? lastTrackCapturedAt;
  DateTime? backgroundStartedAt;
  String appLifecycleState = 'foreground';
  bool backgroundInterruptionShown = false;
  bool accuracyWarningShown = false;
  int invalidAccuracyCount = 0;
  String? lastTrackErrorMessage;
  DateTime? lastTrackErrorShownAt;
  bool settlementReviewWarningShown = false;
  Timer? trackTimer;
  final TrackUploadQueue trackQueue = TrackUploadQueue();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _announce();
      _ensureConversation();
      _startTracking();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      appLifecycleState = 'foreground';
      final backgroundAt = backgroundStartedAt;
      backgroundStartedAt = null;
      final lastCaptured = lastTrackCapturedAt;
      if (backgroundAt != null &&
          DateTime.now().difference(backgroundAt) > const Duration(seconds: 20) &&
          (lastCaptured == null ||
              DateTime.now().difference(lastCaptured) > const Duration(seconds: 20))) {
        _warnBackgroundTrackingInterrupted();
      }
      unawaited(_flushTrackQueue(showError: false));
      return;
    }
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive ||
        state == AppLifecycleState.hidden) {
      appLifecycleState = 'background';
      backgroundStartedAt ??= DateTime.now();
    }
  }

  Future<void> _warnBackgroundTrackingInterrupted() async {
    if (!mounted || backgroundInterruptionShown) return;
    backgroundInterruptionShown = true;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('后台定位已中断，可能影响路程和成长值结算'),
          duration: Duration(seconds: 5),
        ),
      );
  }

  Future<void> _startTracking() async {
    final granted =
        await location.invokeMethod<bool>('requestLocation') ?? false;
    if (!granted || !mounted) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('请开启定位权限，否则无法记录路程和结算成长值')),
        );
      }
      return;
    }
    final session = context.read<AppSession>();
    final userId = session.userId ?? 'anonymous';
    lastTrackCapturedAt = await trackQueue.lastCapturedAt(
      widget.trip.id,
      userId,
    );
    await _flushTrackQueue(showError: false);
    await _uploadCurrentPoint(force: true);
    _scheduleTrackTimer();
  }

  void _scheduleTrackTimer() {
    trackTimer?.cancel();
    trackTimer = Timer.periodic(
      const Duration(seconds: 5),
      (_) => _uploadCurrentPoint(),
    );
  }

  Future<void> _uploadCurrentPoint({bool force = false}) async {
    if (requestingLocation || uploadingTrack || !mounted) return;
    requestingLocation = true;
    final lastUpload = lastTrackUploadAt;
    if (!force &&
        lastUpload != null &&
        DateTime.now().difference(lastUpload) < const Duration(seconds: 3)) {
      requestingLocation = false;
      return;
    }
    try {
      final raw = await location.invokeMapMethod<String, dynamic>(
        'getCurrentLocation',
      );
      if (raw == null || !mounted) return;
      final locationTimeMillis = (raw['locationTimeMillis'] as num?)?.toInt();
      final nativeCapturedAt = locationTimeMillis == null
          ? DateTime.now()
          : DateTime.fromMillisecondsSinceEpoch(locationTimeMillis);
      await _uploadTrackValues(
        longitude: (raw['longitude'] as num).toDouble(),
        latitude: (raw['latitude'] as num).toDouble(),
        altitude: (raw['altitude'] as num?)?.toDouble(),
        speed: (raw['speed'] as num?)?.toDouble(),
        direction: (raw['direction'] as num?)?.toDouble(),
        accuracy: (raw['accuracy'] as num?)?.toDouble(),
        mockLocation: raw['isMock'] == true,
        provider: raw['provider']?.toString() ?? 'fused',
        force: force,
        capturedAt: nativeCapturedAt,
      );
    } on PlatformException {
      // 定位暂不可用时保留导航界面，下一周期自动重试。
    } finally {
      requestingLocation = false;
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
          const SnackBar(content: Text('尚未在当前节点 100 米范围内保持足够时间，请靠近后再确认')),
        );
    }
  }

  Future<void> _uploadTrackValues({
    required double longitude,
    required double latitude,
    double? altitude,
    double? speed,
    double? direction,
    double? accuracy,
    bool mockLocation = false,
    String provider = 'fused',
    bool force = false,
    DateTime? capturedAt,
  }) async {
    if (uploadingTrack || !mounted) return;
    if (accuracy == null || !accuracy.isFinite || accuracy <= 0) {
      invalidAccuracyCount += 1;
      if (invalidAccuracyCount >= 3 && !accuracyWarningShown && mounted) {
        accuracyWarningShown = true;
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(
            const SnackBar(content: Text('定位信号较弱，暂未记录无效定位点')),
          );
      }
      return;
    }
    invalidAccuracyCount = 0;
    final normalizedAccuracy = accuracy;
    final pointCapturedAt = capturedAt ?? DateTime.now();
    final moving = (speed ?? 0) >= 0.8;
    final minimumInterval = moving
        ? const Duration(seconds: 5)
        : const Duration(seconds: 10);
    final previousCapturedAt = lastTrackCapturedAt;
    if (previousCapturedAt != null &&
        !pointCapturedAt.isAfter(previousCapturedAt)) {
      return;
    }
    if (!force &&
        previousCapturedAt != null &&
        pointCapturedAt.difference(previousCapturedAt) < minimumInterval) {
      return;
    }

    final session = context.read<AppSession>();
    final userId = session.userId ?? 'anonymous';
    final sequenceNo = await trackQueue.nextSequence(widget.trip.id, userId);
    final point = <String, dynamic>{
      'tripId': widget.trip.id,
      'longitude': longitude,
      'latitude': latitude,
      'altitude': altitude,
      'speed': speed,
      'direction': direction,
      'accuracy': normalizedAccuracy,
      'recordTime': pointCapturedAt.toIso8601String(),
      'clientSendTime': DateTime.now().toIso8601String(),
      'deviceId': 'app-$userId',
      'sequenceNo': sequenceNo,
      'mockLocation': mockLocation,
      'provider': provider,
      'appState': appLifecycleState,
    };
    await trackQueue.enqueue(widget.trip.id, userId, point);
    await trackQueue.markCapturedAt(widget.trip.id, userId, pointCapturedAt);
    lastTrackCapturedAt = pointCapturedAt;
    backgroundInterruptionShown = false;
    accuracyWarningShown = false;
    await _flushTrackQueue(showError: true);
  }

  Future<void> _flushTrackQueue({required bool showError}) async {
    if (uploadingTrack || !mounted) return;
    uploadingTrack = true;
    final session = context.read<AppSession>();
    final userId = session.userId ?? 'anonymous';
    final service = TripService(session.api);
    try {
      final pending = await trackQueue.pending(widget.trip.id, userId);
      for (final point in pending) {
        late Map<String, dynamic> response;
        try {
          response = await service.uploadTrackPayload(point);
        } on ApiException catch (error, stackTrace) {
          final duplicateMessage =
              error.message.contains('已上传') ||
              error.message.contains('重复提交');
          final duplicateCode = error.code == 409 || error.statusCode == 409;
          if (duplicateCode && duplicateMessage) {
            await trackQueue.remove(
              widget.trip.id,
              userId,
              (point['sequenceNo'] as num).toInt(),
            );
            debugPrint(
              '轨迹幂等重复，已删除本地缓存：'
              'tripId=${widget.trip.id}, '
              'sequenceNo=${point['sequenceNo']}',
            );
            continue;
          }

          debugPrint(
            '轨迹接口返回异常：'
            'code=${error.code}, '
            'statusCode=${error.statusCode}, '
            'networkError=${error.networkError}, '
            'message=${error.message}, '
            'sequenceNo=${point['sequenceNo']}',
          );
          debugPrintStack(stackTrace: stackTrace);

          if (showError && mounted) {
            final pendingCount = await trackQueue.count(
              widget.trip.id,
              userId,
            );
            if (!mounted) return;
            final message = error.networkError
                ? '轨迹已本地保存，网络恢复后自动补传（待传 $pendingCount 点）'
                : '轨迹上传失败：${error.message}（待传 $pendingCount 点）';
            _showTrackUploadMessage(message);
          }
          // 保留当前点及后续点，避免越过失败点乱序补传。
          break;
        } catch (error, stackTrace) {
          debugPrint(
            '轨迹上传客户端处理异常：$error，'
            'sequenceNo=${point['sequenceNo']}',
          );
          debugPrintStack(stackTrace: stackTrace);
          if (showError && mounted) {
            final pendingCount = await trackQueue.count(
              widget.trip.id,
              userId,
            );
            if (!mounted) return;
            _showTrackUploadMessage(
              '轨迹处理异常，数据已保存在本地（待传 $pendingCount 点）',
            );
          }
          break;
        }

        // 服务端已经成功接收后再删除本地记录。
        await trackQueue.remove(
          widget.trip.id,
          userId,
          (point['sequenceNo'] as num).toInt(),
        );
        lastTrackUploadAt = DateTime.now();
        if (!mounted) return;

        // 页面状态更新失败不能再误报成“没有网络”，轨迹已经上传成功。
        try {
          setState(() {
            trackedDistanceMeters =
                (response['totalDistance'] as num?)?.toInt() ??
                trackedDistanceMeters;
          });
          _notifySettlement(response);
          final message = response['message']?.toString().trim() ?? '';
          final pointStatus =
              response['pointStatus']?.toString().trim().toUpperCase() ?? '';
          final accepted = response['accepted'] == true;
          final currentPointAbnormal =
              !accepted ||
              const {
                'IMPOSSIBLE_SPEED',
                'TELEPORT',
                'FATAL_REJECTED',
                'ROUND_TRIP_RECOVERY',
                'TIME_ANOMALY',
                'TIME_REVERSED',
                'DUPLICATE_POINT',
                'LOW_ACCURACY',
                'LOW_CONFIDENCE_REJECTED',
                'LOCATION_GAP',
              }.contains(pointStatus);

          debugPrint(
            '轨迹响应：sequenceNo=${point['sequenceNo']}, '
            'pointStatus=$pointStatus, accepted=$accepted, '
            'reviewRequired=${response['settlementReviewRequired']}, '
            'message=$message',
          );

          // 历史风险状态不能在每次正常定位上传后反复弹窗。
          // 只有当前点本身异常时才提示，并且同一导航页最多提示一次。
          if (message.isNotEmpty &&
              currentPointAbnormal &&
              !settlementReviewWarningShown) {
            settlementReviewWarningShown = true;
            ScaffoldMessenger.of(context)
              ..hideCurrentSnackBar()
              ..showSnackBar(SnackBar(content: Text(message)));
          }
          if (response['accepted'] == true) {
            await _shareAndLoadTeamLocations(
              latitude: (point['latitude'] as num).toDouble(),
              longitude: (point['longitude'] as num).toDouble(),
              speed: (point['speed'] as num?)?.toDouble(),
            );
          }
        } catch (error, stackTrace) {
          debugPrint('轨迹已上传，但页面响应处理失败：$error');
          debugPrintStack(stackTrace: stackTrace);
        }
      }
      await _refreshTeammateDistance();
    } catch (error, stackTrace) {
      // 这里只处理本地队列读取、删除等异常，不能统一描述成网络故障。
      debugPrint('轨迹本地队列处理失败：$error');
      debugPrintStack(stackTrace: stackTrace);
      if (showError && mounted) {
        _showTrackUploadMessage('轨迹本地缓存处理异常，请稍后重试');
      }
    } finally {
      uploadingTrack = false;
    }
  }

  void _showTrackUploadMessage(String message) {
    if (!mounted) return;
    final now = DateTime.now();
    final shownAt = lastTrackErrorShownAt;
    if (message == lastTrackErrorMessage &&
        shownAt != null &&
        now.difference(shownAt) < const Duration(seconds: 15)) {
      return;
    }
    lastTrackErrorMessage = message;
    lastTrackErrorShownAt = now;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _ensureConversation() async {
    if (conversationId != null || !mounted) return;
    try {
      final conversation = await ChatService(
        context.read<AppSession>().api,
      ).tripConversation(widget.trip.id);
      conversationId = conversation.id;
    } catch (_) {
      // 行程群可能刚创建，下一次定位上报时自动重试。
    }
  }

  Future<void> _shareAndLoadTeamLocations({
    required double latitude,
    required double longitude,
    double? speed,
  }) async {
    await _ensureConversation();
    final id = conversationId;
    if (id == null || id.isEmpty || !mounted) return;
    try {
      final rows = await ChatService(
        context.read<AppSession>().api,
      ).shareLocation(
        id,
        latitude: latitude,
        longitude: longitude,
        speed: speed,
      );
      final points = rows
          .map(
            (row) => LocationSelection(
              id: row['userId']?.toString(),
              name: row['nickname']?.toString().trim().isNotEmpty == true
                  ? row['nickname'].toString()
                  : '行程成员',
              address: '实时位置',
              latitude: (row['latitude'] as num).toDouble(),
              longitude: (row['longitude'] as num).toDouble(),
            ),
          )
          .toList(growable: false);
      if (mounted) setState(() => teamLocations = points);
    } catch (_) {
      // 实时位置共享暂时失败不影响本地轨迹计算。
    }
  }

  Future<void> _refreshTeammateDistance() async {
    try {
      final state = await TripService(
        context.read<AppSession>().api,
      ).teammateDistanceState(widget.trip.id);
      if (!mounted) return;
      setState(() {
        teammateDistanceMeters =
            (state['deviationDistance'] as num?)?.toInt() ?? 0;
        teammateDistanceStatus =
            (state['deviationStatus'] as num?)?.toInt() ?? 0;
      });
    } catch (_) {
      // 队友位置暂不可用不影响轨迹采集，下一批次自动恢复。
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
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('无法打开高德导航，请确认已安装高德地图')));
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
              '已按顺序到达节点“$waypoint”，下一导航目标已更新',
            ),
          ),
        );
      _announceCurrentStage();
      return;
    }
    // 成长值在结束后按有效轨迹统一结算，不在上传途中发放。
  }

  LocationSelection? get _currentTarget {
    if (completedWaypointCount < widget.trip.waypoints.length) {
      return widget.trip.waypoints[completedWaypointCount];
    }
    return widget.trip.endLocation ??
        (_routePoints.isEmpty ? null : _routePoints.last);
  }

  String get _routeText {
    final target = _currentTarget;
    if (target == null) {
      return '${widget.trip.startName}前往${widget.trip.endName}';
    }
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

  void _collapseControls() {
    if (!mounted || controlsCollapsed) return;
    final ignoreUntil = ignoreMapCollapseUntil;
    if (ignoreUntil != null && DateTime.now().isBefore(ignoreUntil)) return;
    setState(() => controlsCollapsed = true);
  }

  void _expandControls() {
    if (!mounted) return;
    // 防止地图惯性移动或原生地图延迟回调在展开后立即再次收起。
    ignoreMapCollapseUntil = DateTime.now().add(
      const Duration(milliseconds: 800),
    );
    if (controlsCollapsed) {
      setState(() => controlsCollapsed = false);
    }
  }

  Future<void> _announce() async {
    if (!voiceEnabled) return;
    try {
      await voice.invokeMethod('speak', {
        'text': '行程记录已开始。$_routeText。实际道路可根据路况灵活选择，请注意行车安全。',
      });
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
    var tripEnded = false;
    try {
      await _uploadCurrentPoint(force: true);
      await _flushTrackQueue(showError: false);
      final session = context.read<AppSession>();
      final pendingCount = await trackQueue.count(
        widget.trip.id,
        session.userId ?? 'anonymous',
      );
      if (pendingCount > 0) {
        throw const ApiException('仍有轨迹未同步，请恢复网络后再结束行程');
      }
      final service = TripService(session.api);
      await service.end(widget.trip.id);
      tripEnded = true;
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
                      '将按实际有效轨迹统一结算：每满 5 公里获得 1 成长值，不跨行程结转。',
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
          title: Text(
            result.status == 'REVIEW_REQUIRED'
                ? '已提交人工复核'
                : result.duplicate
                ? '该行程已结算'
                : '行程结算完成',
          ),
          content: Text(
            '有效成员：${result.memberCount} 人\n'
            '${result.status == 'REVIEW_REQUIRED' ? '轨迹覆盖率或节点到达证据不足，复核完成前暂不发成长值' : '成长值：每满 5 公里 1 点，本次每位成员 +${result.pointsPerMember}'}\n'
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
      if (!tripEnded && mounted) {
        _scheduleTrackTimer();
      }
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
    WidgetsBinding.instance.removeObserver(this);
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
              polylinePoints: showTeamPanel
                  ? (teamLocations.isEmpty ? _routeStops : teamLocations)
                  : _routePoints,
              stops: showTeamPanel
                  ? (teamLocations.isEmpty ? _routeStops : teamLocations)
                  : _routeStops,
              height: MediaQuery.sizeOf(context).height,
              showMyLocation: true,
              interactive: true,
              trafficEnabled: true,
              drawPolyline: !showTeamPanel,
              onMapInteraction: _collapseControls,
            ),
          ),
          if (!controlsCollapsed)
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
                  SegmentedButton<bool>(
                    segments: const [
                      ButtonSegment(value: false, label: Text('路线')),
                      ButtonSegment(value: true, label: Text('队友')),
                    ],
                    selected: {showTeamPanel},
                    showSelectedIcon: false,
                    onSelectionChanged: (value) =>
                        setState(() => showTeamPanel = value.first),
                    style: const ButtonStyle(
                      visualDensity: VisualDensity.compact,
                      backgroundColor: WidgetStatePropertyAll(Colors.white),
                    ),
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
          if (!controlsCollapsed && !showTeamPanel)
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
                    const Icon(
                      LucideIcons.route,
                      color: Colors.white,
                      size: 34,
                    ),
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
          if (!controlsCollapsed && showTeamPanel)
            Positioned(
              left: 16,
              right: 16,
              top: 82,
              child: Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(22),
                  boxShadow: const [
                    BoxShadow(color: Color(0x22000000), blurRadius: 20),
                  ],
                ),
                child: Row(
                  children: [
                    Icon(
                      teammateDistanceStatus == 3
                          ? LucideIcons.cloudOff
                          : teammateDistanceStatus == 2
                          ? LucideIcons.triangleAlert
                          : teammateDistanceStatus == 1
                          ? LucideIcons.moveRight
                          : LucideIcons.usersRound,
                      color: teammateDistanceStatus > 0
                          ? AppColors.danger
                          : AppColors.primary,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        teammateDistanceStatus == 3
                            ? '队友位置超过 2 分钟未更新'
                            : teammateDistanceStatus == 2
                            ? '与队长距离 ${teammateDistanceMeters}m，已持续严重远离'
                            : teammateDistanceStatus == 1
                            ? '与队长距离 ${teammateDistanceMeters}m，请注意跟队'
                            : '队友距离正常（${teammateDistanceMeters}m）',
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          if (!controlsCollapsed)
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
                        const Icon(
                          LucideIcons.mapPinCheck,
                          color: AppColors.primary,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                '下一导航目标',
                                style: TextStyle(
                                  fontSize: 11,
                                  color: AppColors.muted,
                                ),
                              ),
                              Text(
                                _currentTarget?.name ?? widget.trip.endName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      '路线仅供参考，系统只记录轨迹并按顺序判断节点到达。',
                      style: TextStyle(
                        fontSize: 11,
                        color: AppColors.secondaryText,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 10,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFFF4E5),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(
                            LucideIcons.triangleAlert,
                            size: 17,
                            color: Color(0xFFB66A00),
                          ),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              '请勿清理应用后台，否则将无法持续定位并计算本次行程的路程值。',
                              style: TextStyle(
                                fontSize: 11,
                                height: 1.35,
                                fontWeight: FontWeight.w700,
                                color: Color(0xFF8A5200),
                              ),
                            ),
                          ),
                        ],
                      ),
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
                          value:
                              '$completedWaypointCount/${widget.trip.waypoints.length}',
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
                        label: Text(
                          '导航到 ${_currentTarget?.name ?? widget.trip.endName}',
                        ),
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
    floatingActionButton: controlsCollapsed
        ? FloatingActionButton.extended(
            heroTag: 'trip-navigation-expand-controls',
            onPressed: _expandControls,
            elevation: 10,
            backgroundColor: AppColors.primary,
            foregroundColor: Colors.white,
            tooltip: '展开导航控件',
            icon: const Icon(Icons.keyboard_arrow_up_rounded),
            label: const Text(
              '展开控件',
              style: TextStyle(fontWeight: FontWeight.w800),
            ),
          )
        : null,
    floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
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
