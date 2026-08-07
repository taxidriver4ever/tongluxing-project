import 'dart:async';
import 'dart:math' as math;

import 'package:amap_map/amap_map.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:x_amap_base/x_amap_base.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/location_snapshot.dart';
import '../../../data/services/track_upload_queue.dart';
import '../../chat/pages/chat_session_page.dart';
import '../../trip/pages/trip_create_page.dart';
import '../../trip/pages/trip_discovery_detail_page.dart';
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
  State<MapHomePage> createState() => MapHomePageState();
}

class MapHomePageState extends State<MapHomePage> with WidgetsBindingObserver {
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
  List<LocationSelection> exactRoutePoints = const [];
  List<LocationSelection> teamLocations = const [];
  List<TripRecommendModel> hotTrips = const [];
  int unreadMessageCount = 0;
  bool loadingUnreadMessageCount = false;
  StreamSubscription<void>? imConversationSubscription;
  StreamSubscription<void>? imMessageSubscription;
  final TrackUploadQueue trackQueue = TrackUploadQueue();
  Timer? trackTimer;
  String? trackingTripId;
  String? conversationId;
  DateTime? lastTrackCapturedAt;
  DateTime? lastTrackUploadAt;
  bool requestingLocation = false;
  bool uploadingTrack = false;
  bool sharingLocation = true;
  bool endingTrip = false;
  int trackedDistanceMeters = 0;
  int completedWaypointCount = 0;
  String appLifecycleState = 'foreground';

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
    WidgetsBinding.instance.addObserver(this);
    final im = context.read<AppSession>().tencentIm;
    imConversationSubscription = im.conversationEvents.listen(
      (_) => unawaited(_loadUnreadMessageCount()),
    );
    imMessageSubscription = im.messageEvents.listen(
      (_) => unawaited(_loadUnreadMessageCount()),
    );
    _checkAmapSupport();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(_loadState());
      unawaited(_loadUnreadMessageCount());
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    trackTimer?.cancel();
    imConversationSubscription?.cancel();
    imMessageSubscription?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    appLifecycleState = state == AppLifecycleState.resumed
        ? 'foreground'
        : 'background';
    if (state == AppLifecycleState.resumed && currentTrip != null) {
      unawaited(_flushTrackQueue(showError: false));
      unawaited(refreshFromServer());
    }
  }

  Future<void> refreshFromServer() => _loadState();

  Future<void> _loadUnreadMessageCount() async {
    if (!mounted || loadingUnreadMessageCount) return;
    loadingUnreadMessageCount = true;
    try {
      final session = context.read<AppSession>();
      final bindings = await ChatService(session.api).imBindings();
      await session.tencentIm.connect();
      final conversations = await session.tencentIm.conversations(bindings);
      final count = conversations.fold<int>(
        0,
        (total, conversation) => total + conversation.unread,
      );
      if (!mounted) return;
      setState(() => unreadMessageCount = count);
    } catch (_) {
      // 腾讯 IM 或业务绑定暂不可用时隐藏角标，绝不使用固定 Mock 数量兜底。
      if (mounted && unreadMessageCount != 0) {
        setState(() => unreadMessageCount = 0);
      }
    } finally {
      loadingUnreadMessageCount = false;
    }
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
      final visible = active ?? justCompleted;
      await _loadExactRoute(visible);
      if (active != null) {
        await _ensureTracking(active);
      } else {
        _stopTracking(clearRuntimeState: true);
      }
      if (active == null && justCompleted == null) await _loadHotTrips();
      _fitTripRoute();
    } catch (caught) {
      if (mounted) setState(() => error = caught.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _loadExactRoute(TripModel? trip) async {
    if (trip == null) {
      if (mounted) setState(() => exactRoutePoints = const []);
      return;
    }
    if (_isCompleted(trip.status)) {
      try {
        final session = context.read<AppSession>();
        final actualTrack = await TripService(session.api).trackPoints(
          trip.id,
          driverId: session.userId,
        );
        if (actualTrack.length >= 2) {
          if (mounted) setState(() => exactRoutePoints = actualTrack);
          return;
        }
      } catch (_) {
        // 历史轨迹不可用时继续展示发布时保存的完整道路路线。
      }
    }
    final saved = trip.routePoints;
    try {
      // 导航优先读取发布时持久化的完整真实道路路线，避免每次进入地图 Tab 再调用第三方地图规划。
      final stored = await TripService(
        context.read<AppSession>().api,
      ).tripRoute(trip.id);
      final points = stored?.polylinePoints ?? const <LocationSelection>[];
      if (!mounted) return;
      setState(() {
        exactRoutePoints = points.length >= 2 ? points : saved;
      });
    } catch (_) {
      // 路线接口暂时不可用时退化为详情中的简化预览节点，不重新向高德发起规划请求。
      if (mounted) setState(() => exactRoutePoints = saved);
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
    final points = exactRoutePoints.isNotEmpty
        ? exactRoutePoints
        : visibleTrip?.routePoints ?? const <LocationSelection>[];
    if (points.isEmpty) return;
    var minLat = points.first.latitude;
    var maxLat = points.first.latitude;
    var minLng = points.first.longitude;
    var maxLng = points.first.longitude;
    for (final point in points.skip(1)) {
      minLat = math.min(minLat, point.latitude);
      maxLat = math.max(maxLat, point.latitude);
      minLng = math.min(minLng, point.longitude);
      maxLng = math.max(maxLng, point.longitude);
    }
    final center = LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2);
    final span = math.max(maxLat - minLat, maxLng - minLng);
    final zoom = switch (span) {
      > 20 => 3.8,
      > 10 => 4.8,
      > 5 => 5.8,
      > 2 => 6.8,
      > 1 => 7.8,
      > .5 => 8.8,
      > .2 => 9.8,
      > .1 => 10.8,
      > .05 => 11.8,
      _ => 13.0,
    };
    WidgetsBinding.instance.addPostFrameCallback((_) {
      mapController?.moveCamera(
        CameraUpdate.newLatLngZoom(center, zoom),
      );
    });
  }

  Future<void> _ensureTracking(TripModel trip) async {
    if (trackingTripId == trip.id && trackTimer?.isActive == true) return;
    _stopTracking(clearRuntimeState: trackingTripId != trip.id);
    try {
      final granted =
          await _permissionChannel.invokeMethod<bool>('requestLocation') ?? false;
      if (!granted || !mounted) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('请开启定位权限，否则无法记录本次行程轨迹')),
          );
        }
        return;
      }
      final session = context.read<AppSession>();
      final userId = session.userId ?? 'anonymous';
      trackingTripId = trip.id;
      lastTrackCapturedAt = await trackQueue.lastCapturedAt(trip.id, userId);
      if (mounted) setState(() => locationEnabled = true);
      await _flushTrackQueue(showError: false);
      await _uploadCurrentPoint(force: true);
      trackTimer = Timer.periodic(
        const Duration(seconds: 10),
        (_) => unawaited(_uploadCurrentPoint()),
      );
    } on PlatformException {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('定位服务暂不可用，地图仍可查看完整路线')),
        );
      }
    } on MissingPluginException {
      // 非 Android 原生环境只展示路线，不启动轨迹采集。
    }
  }

  void _stopTracking({required bool clearRuntimeState}) {
    trackTimer?.cancel();
    trackTimer = null;
    trackingTripId = null;
    conversationId = null;
    if (clearRuntimeState && mounted) {
      setState(() {
        trackedDistanceMeters = 0;
        completedWaypointCount = 0;
        teamLocations = const [];
      });
    }
  }

  Future<void> _uploadCurrentPoint({bool force = false}) async {
    final trip = currentTrip;
    if (trip == null || !_isRunning(trip.status)) return;
    if (requestingLocation || uploadingTrack || !mounted) return;
    final lastUpload = lastTrackUploadAt;
    if (!force &&
        lastUpload != null &&
        DateTime.now().difference(lastUpload) < const Duration(seconds: 3)) {
      return;
    }
    requestingLocation = true;
    try {
      final raw = await _permissionChannel.invokeMapMethod<String, dynamic>(
        'getCurrentLocation',
      );
      if (raw == null || !mounted) return;
      final latitude = (raw['latitude'] as num?)?.toDouble();
      final longitude = (raw['longitude'] as num?)?.toDouble();
      final accuracy = (raw['accuracy'] as num?)?.toDouble();
      if (latitude == null ||
          longitude == null ||
          accuracy == null ||
          !latitude.isFinite ||
          !longitude.isFinite ||
          !accuracy.isFinite ||
          accuracy <= 0) {
        return;
      }
      final locationTimeMillis = (raw['locationTimeMillis'] as num?)?.toInt();
      final capturedAt = locationTimeMillis == null
          ? DateTime.now()
          : DateTime.fromMillisecondsSinceEpoch(locationTimeMillis);
      final speed = (raw['speed'] as num?)?.toDouble();
      if (force &&
          lastTrackCapturedAt != null &&
          !capturedAt.isAfter(lastTrackCapturedAt!)) {
        // 结束前系统定位可能返回刚刚缓存的同一时间点。此时不重复入队，
        // 否则同一 recordTime 的两个 sequence 会让整批补传被服务端拒绝。
        await _flushTrackQueue(showError: false);
        return;
      }
      const minimumInterval = Duration(seconds: 10);
      if (!force &&
          lastTrackCapturedAt != null &&
          capturedAt.difference(lastTrackCapturedAt!) < minimumInterval) {
        return;
      }
      lastLocation = LatLng(latitude, longitude);
      LocationSnapshot.current = LocationSnapshot(latitude, longitude);
      final session = context.read<AppSession>();
      final userId = session.userId ?? 'anonymous';
      final sequenceNo = await trackQueue.nextSequence(trip.id, userId);
      final point = <String, dynamic>{
        'tripId': trip.id,
        'longitude': longitude,
        'latitude': latitude,
        'altitude': (raw['altitude'] as num?)?.toDouble(),
        'speed': speed,
        'direction': (raw['direction'] as num?)?.toDouble(),
        'accuracy': accuracy,
        'recordTime': capturedAt.toIso8601String(),
        'clientSendTime': DateTime.now().toIso8601String(),
        'deviceId': 'app-$userId',
        'sequenceNo': sequenceNo,
        'mockLocation': raw['isMock'] == true,
        'provider': raw['provider']?.toString() ?? 'fused',
        'appState': appLifecycleState,
      };
      await trackQueue.enqueue(trip.id, userId, point);
      await trackQueue.markCapturedAt(trip.id, userId, capturedAt);
      lastTrackCapturedAt = capturedAt;

      // 每 10 秒采集并可靠落本地；正常情况下每 60 秒批量上传一次。
      // force 用于开始/恢复/结束前立即补传，确保结束结算前服务端拿到最后定位。
      final shouldFlush =
          force ||
          lastTrackUploadAt == null ||
          DateTime.now().difference(lastTrackUploadAt!) >=
              const Duration(seconds: 60);
      if (shouldFlush) {
        await _flushTrackQueue(showError: true);
      }
    } on PlatformException {
      // 定位短暂不可用时保留行进中界面，下一个周期继续采集。
    } finally {
      requestingLocation = false;
    }
  }

  Future<void> _flushTrackQueue({required bool showError}) async {
    final trip = currentTrip;
    if (trip == null || uploadingTrack || !mounted) return;
    uploadingTrack = true;
    final session = context.read<AppSession>();
    final userId = session.userId ?? 'anonymous';
    final service = TripService(session.api);
    try {
      var pending = await trackQueue.pending(trip.id, userId);
      while (pending.isNotEmpty) {
        // 后端单批上限 200；断网很久后的历史轨迹按采集时间分批补传。
        final chunk = pending.take(200).toList(growable: false);
        Map<String, dynamic> response;
        try {
          response = await service.uploadTrackBatchPayload(chunk);
        } on ApiException {
          if (showError && mounted) {
            final count = await trackQueue.count(trip.id, userId);
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text('轨迹已保存在本地，待网络恢复后补传（$count 点）')),
              );
            }
          }
          break;
        }

        final confirmed = (response['confirmedSequenceNos'] as List? ?? const [])
            .whereType<num>()
            .map((value) => value.toInt())
            .toSet();
        if (confirmed.isEmpty) {
          // 没有得到服务端明确确认时绝不能清理本地队列。
          break;
        }
        await trackQueue.removeMany(trip.id, userId, confirmed);
        lastTrackUploadAt = DateTime.now();
        if (!mounted) return;
        setState(() {
          trackedDistanceMeters =
              (response['totalDistance'] as num?)?.toInt() ??
              trackedDistanceMeters;
        });

        // 位置共享只发送本批最后一个已确认点，避免补传几十个历史点时重复刷新群位置。
        Map<String, dynamic>? latestConfirmedPoint;
        for (final point in chunk.reversed) {
          final sequenceNo = (point['sequenceNo'] as num?)?.toInt();
          if (sequenceNo != null && confirmed.contains(sequenceNo)) {
            latestConfirmedPoint = point;
            break;
          }
        }
        if (latestConfirmedPoint != null && sharingLocation) {
          await _shareAndLoadTeamLocations(
            latitude: (latestConfirmedPoint['latitude'] as num).toDouble(),
            longitude: (latestConfirmedPoint['longitude'] as num).toDouble(),
            speed: (latestConfirmedPoint['speed'] as num?)?.toDouble(),
          );
        }

        pending = pending
            .where(
              (point) => !confirmed.contains(
                (point['sequenceNo'] as num?)?.toInt(),
              ),
            )
            .toList(growable: false);
        if (confirmed.length < chunk.length) {
          // 部分确认时保留未确认数据，下一次重传，不继续越过它上传更晚的点。
          break;
        }
      }
    } finally {
      uploadingTrack = false;
    }
  }

  void _handleReachedWaypoint(Map<String, dynamic> response) {
    final waypoint = response['reachedWaypointName']?.toString().trim() ?? '';
    final trip = currentTrip;
    if (waypoint.isEmpty || trip == null || !mounted) return;
    setState(() {
      completedWaypointCount = math.min(
        completedWaypointCount + 1,
        trip.waypoints.length,
      );
    });
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text('已到达“$waypoint”，下一导航目标已更新')));
  }

  Future<void> _ensureConversation() async {
    final trip = currentTrip;
    if (trip == null || conversationId != null || !mounted) return;
    try {
      final conversation = await ChatService(
        context.read<AppSession>().api,
      ).tripConversation(trip.id);
      conversationId = conversation.id;
    } catch (_) {
      // 群聊刚创建时下一次位置上传继续重试。
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
      final rows = await ChatService(context.read<AppSession>().api)
          .shareLocation(
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
      // 实时位置共享失败不影响本地轨迹记录。
    }
  }

  Future<void> _toggleLocationSharing() async {
    if (!mounted) return;
    setState(() => sharingLocation = !sharingLocation);
    if (sharingLocation) {
      await _uploadCurrentPoint(force: true);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('已停止在车队地图展示位置；后台定位仍用于队长轨迹或成员脱队检测')),
      );
    }
  }

  Future<void> _openAmapNavigation() async {
    final trip = currentTrip;
    if (trip == null) return;
    final route = exactRoutePoints.isNotEmpty ? exactRoutePoints : trip.routePoints;
    final end = trip.endLocation ?? (route.isEmpty ? null : route.last);
    if (end == null) return;
    final current = lastLocation;
    final start = current == null
        ? trip.startLocation ?? (route.isEmpty ? null : route.first)
        : LocationSelection(
            name: '当前位置',
            address: '',
            latitude: current.latitude,
            longitude: current.longitude,
          );
    if (start == null) return;
    final remainingWaypoints = trip.waypoints
        .skip(completedWaypointCount)
        .toList(growable: false);
    final query = <String, String>{
      'sourceApplication': '同路行',
      'sid': 'TLX_START',
      'slat': '${start.latitude}',
      'slon': '${start.longitude}',
      'sname': start.name,
      'did': 'TLX_END',
      'dlat': '${end.latitude}',
      'dlon': '${end.longitude}',
      'dname': end.name.isEmpty ? trip.endName : end.name,
      'dev': '0',
      't': '0',
      'm': '4',
      if (remainingWaypoints.isNotEmpty)
        'vian': '${remainingWaypoints.length}',
      if (remainingWaypoints.isNotEmpty)
        'vialons': remainingWaypoints.map((point) => point.longitude).join('|'),
      if (remainingWaypoints.isNotEmpty)
        'vialats': remainingWaypoints.map((point) => point.latitude).join('|'),
      if (remainingWaypoints.isNotEmpty)
        'vianames': remainingWaypoints.map((point) => point.name).join('|'),
    };
    final nativeUri = Uri(
      scheme: 'amapuri',
      host: 'route',
      path: '/plan/',
      queryParameters: query,
    );
    try {
      if (await launchUrl(nativeUri, mode: LaunchMode.externalApplication)) {
        return;
      }
      final webQuery = <String, String>{
        'from': '${start.longitude},${start.latitude},${start.name}',
        'to': '${end.longitude},${end.latitude},${end.name}',
        if (remainingWaypoints.isNotEmpty)
          'via': '${remainingWaypoints.first.longitude},${remainingWaypoints.first.latitude},${remainingWaypoints.first.name}',
        'mode': 'car',
        'policy': '1',
        'src': 'tongluxing',
        'callnative': '1',
      };
      if (await launchUrl(
        Uri.https('uri.amap.com', '/navigation', webQuery),
        mode: LaunchMode.externalApplication,
      )) {
        return;
      }
    } catch (_) {
      // 下方统一提示。
    }
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('无法打开高德地图，请确认已经安装高德地图')),
      );
    }
  }

  Future<void> _waitForTrackIoIdle() async {
    for (var i = 0; i < 50 && (requestingLocation || uploadingTrack); i++) {
      await Future<void>.delayed(const Duration(milliseconds: 100));
    }
    if (requestingLocation || uploadingTrack) {
      throw const ApiException('轨迹正在同步，请稍后重新结束导航');
    }
  }

  Future<void> _endNavigation() async {
    final trip = currentTrip;
    if (trip == null || endingTrip) return;
    final confirmed =
        await showDialog<bool>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: const Text('结束导航？'),
            content: const Text(
              '结束导航会同时结束当前行程。若队长已进入终点1公里范围且轨迹无明显异常，系统会立即自动结算成长值；否则提交管理员审核。',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(dialogContext, false),
                child: const Text('继续行程'),
              ),
              FilledButton(
                style: FilledButton.styleFrom(
                  backgroundColor: AppColors.danger,
                ),
                onPressed: () => Navigator.pop(dialogContext, true),
                child: const Text('确认结束'),
              ),
            ],
          ),
        ) ??
        false;
    if (!confirmed || !mounted) return;
    setState(() => endingTrip = true);
    trackTimer?.cancel();
    try {
      // 等待周期性采集/上传结束，再执行最后一次定位和强制补传，避免结束按钮与定时器竞争。
      await _waitForTrackIoIdle();
      await _uploadCurrentPoint(force: true);
      await _flushTrackQueue(showError: false);
      final session = context.read<AppSession>();
      final pending = await trackQueue.count(
        trip.id,
        session.userId ?? 'anonymous',
      );
      if (pending > 0) {
        throw const ApiException('仍有轨迹未同步，请恢复网络后再结束导航');
      }
      final ended = await TripService(session.api).end(trip.id);
      if (!mounted) return;
      _stopTracking(clearRuntimeState: false);
      setState(() {
        currentTrip = null;
        completedTrip = ended;
        endingTrip = false;
      });
      await _loadExactRoute(ended);
      _fitTripRoute();
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(
              ended.status == 'SETTLED'
                  ? '行程已结束，成长值已自动结算'
                  : '行程已结束，成长值已提交管理员审核',
            ),
          ),
        );
    } catch (caught) {
      if (mounted) {
        setState(() => endingTrip = false);
        trackTimer = Timer.periodic(
          const Duration(seconds: 10),
          (_) => unawaited(_uploadCurrentPoint()),
        );
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(caught.toString())),
        );
      }
    }
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

  bool _isCurrentUserCaptain(BuildContext context) {
    final trip = visibleTrip;
    final userId = context.watch<AppSession>().userId;
    if (trip == null || userId == null) return false;
    final captainId = trip.captainUserId?.isNotEmpty == true
        ? trip.captainUserId
        : trip.ownerUserId;
    return captainId == userId;
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
          routePoints: exactRoutePoints,
          teamLocations: teamLocations,
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
          unreadMessageCount: unreadMessageCount,
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
          onToggleLocationSharing: _toggleLocationSharing,
          onGoNavigation: _openAmapNavigation,
          onEndNavigation: _endNavigation,
          onChat: _openChat,
          onSos: _openSos,
          sharingLocation: sharingLocation,
          endingTrip: endingTrip,
          trackedDistanceMeters: trackedDistanceMeters,
          canEndTrip: _isCurrentUserCaptain(context),
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
    required this.unreadMessageCount,
  });

  final _MapMode mode;
  final TripModel? trip;
  final List<TripRecommendModel> hotTrips;
  final bool loading;
  final String? error;
  final ValueChanged<TripRecommendModel> onOpenTrip;
  final VoidCallback onOpenMessages;
  final int unreadMessageCount;

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
          child: SizedBox(
            width: 48,
            height: 48,
            child: Stack(
              children: [
                const Center(
                  child: Icon(
                    LucideIcons.messageCircle,
                    color: AppColors.primary,
                    size: 22,
                  ),
                ),
                if (unreadMessageCount > 0)
                  Positioned(
                    right: 6,
                    top: 6,
                    child: Container(
                      constraints: const BoxConstraints(minWidth: 16),
                      height: 16,
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      alignment: Alignment.center,
                      decoration: const BoxDecoration(
                        color: AppColors.danger,
                        borderRadius: BorderRadius.all(Radius.circular(8)),
                      ),
                      child: Text(
                        unreadMessageCount > 99
                            ? '99+'
                            : '$unreadMessageCount',
                        style: const TextStyle(
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
    required this.onToggleLocationSharing,
    required this.onGoNavigation,
    required this.onEndNavigation,
    required this.onChat,
    required this.onSos,
    required this.sharingLocation,
    required this.endingTrip,
    required this.trackedDistanceMeters,
    required this.canEndTrip,
  });

  final _MapMode mode;
  final TripModel? trip;
  final VoidCallback onSearch;
  final VoidCallback onPublish;
  final VoidCallback? onRecommendations;
  final VoidCallback onToggleLocationSharing;
  final VoidCallback onGoNavigation;
  final VoidCallback onEndNavigation;
  final VoidCallback onChat;
  final VoidCallback onSos;
  final bool sharingLocation;
  final bool endingTrip;
  final int trackedDistanceMeters;
  final bool canEndTrip;

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
              onToggleLocationSharing: onToggleLocationSharing,
              onGoNavigation: onGoNavigation,
              onEndNavigation: onEndNavigation,
              onSos: onSos,
              sharingLocation: sharingLocation,
              endingTrip: endingTrip,
              trackedDistanceMeters: trackedDistanceMeters,
              canEndTrip: canEndTrip,
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
    required this.onToggleLocationSharing,
    required this.onGoNavigation,
    required this.onEndNavigation,
    required this.onSos,
    required this.sharingLocation,
    required this.endingTrip,
    required this.trackedDistanceMeters,
    required this.canEndTrip,
  });

  final TripModel? trip;
  final VoidCallback onToggleLocationSharing;
  final VoidCallback onGoNavigation;
  final VoidCallback onEndNavigation;
  final VoidCallback onSos;
  final bool sharingLocation;
  final bool endingTrip;
  final int trackedDistanceMeters;
  final bool canEndTrip;

  @override
  Widget build(BuildContext context) {
    final plannedDistance = (trip?.plannedDistanceMeters ?? 0) > 0
        ? trip!.plannedDistanceMeters
        : trip?.distanceMeters ?? 0;
    final remaining = math.max(0, plannedDistance - trackedDistanceMeters);
    final plannedDuration = trip?.routeDurationSeconds ?? 0;
    final remainingDuration = plannedDistance <= 0
        ? plannedDuration
        : (plannedDuration * (remaining / plannedDistance)).round();
    return Column(
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
                color: sharingLocation
                    ? const Color(0xFFEAF8EF)
                    : const Color(0xFFF2F4F7),
                borderRadius: BorderRadius.circular(99),
              ),
              child: Row(
                children: [
                  Icon(
                    sharingLocation ? LucideIcons.radio : LucideIcons.wifiOff,
                    size: 13,
                    color: sharingLocation ? AppColors.success : AppColors.muted,
                  ),
                  const SizedBox(width: 5),
                  Text(
                    sharingLocation ? '位置共享中' : '位置共享已关闭',
                    style: TextStyle(
                      color: sharingLocation ? AppColors.success : AppColors.muted,
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
            Expanded(
              child: _TripMetric(
                label: '已行驶',
                value: _distanceLabel(trackedDistanceMeters),
              ),
            ),
            Expanded(
              child: _TripMetric(
                label: '预计',
                value: _durationLabel(remainingDuration),
              ),
            ),
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
                icon: sharingLocation
                    ? LucideIcons.mapPinCheck
                    : LucideIcons.mapPinOff,
                label: '共享位置',
                active: sharingLocation,
                onTap: onToggleLocationSharing,
              ),
            ),
            Expanded(
              child: _RoundAction(
                icon: LucideIcons.navigation,
                label: '去导航',
                onTap: onGoNavigation,
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
        if (canEndTrip)
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              style: FilledButton.styleFrom(backgroundColor: AppColors.danger),
              onPressed: endingTrip ? null : onEndNavigation,
              icon: const Icon(LucideIcons.square, size: 17),
              label: Text(endingTrip ? '正在结束导航…' : '结束导航'),
            ),
          )
        else
          const SizedBox(
            width: double.infinity,
            child: Text(
              '只有队长可以结束行程',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: AppColors.secondaryText,
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
      ],
    );
  }
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
          Expanded(
            child: _TripMetric(
              label: '总耗时',
              value: _elapsedLabel(trip?.actualStartTime, trip?.actualEndTime),
            ),
          ),
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
    this.active = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool danger;
  final bool active;

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
              color: danger
                  ? const Color(0xFFFFECEA)
                  : active
                  ? AppColors.primary
                  : AppColors.primarySoft,
              shape: BoxShape.circle,
            ),
            child: Icon(
              icon,
              color: danger
                  ? AppColors.danger
                  : active
                  ? Colors.white
                  : AppColors.primary,
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
    required this.routePoints,
    required this.teamLocations,
    required this.mode,
    required this.onMapCreated,
    required this.onLocationChanged,
  });

  final bool nativeEnabled;
  final bool checkingSupport;
  final bool locationEnabled;
  final TripModel? trip;
  final List<LocationSelection> routePoints;
  final List<LocationSelection> teamLocations;
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
    final route = routePoints.isNotEmpty
        ? routePoints
        : trip?.routePoints ?? const <LocationSelection>[];
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
      for (final waypoint in trip?.waypoints ?? const <LocationSelection>[]) {
        markers.add(
          Marker(
            position: LatLng(waypoint.latitude, waypoint.longitude),
            infoWindow: InfoWindow(title: waypoint.name.isEmpty ? '途经点' : waypoint.name),
          ),
        );
      }
      for (final teammate in teamLocations) {
        markers.add(
          Marker(
            position: LatLng(teammate.latitude, teammate.longitude),
            infoWindow: InfoWindow(title: teammate.name, snippet: '车队实时位置'),
          ),
        );
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
        trafficEnabled: mode == _MapMode.tracking,
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
            route: route,
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
                '当前模拟器不支持高德原生地图\n请使用 ARM64 Android 真机预览真实道路路线',
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
  const _RoadPainter({required this.route, required this.ended});

  final List<LocationSelection> route;
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
    if (route.length < 2) {
      canvas.drawCircle(
        Offset(size.width * .48, size.height * .44),
        11,
        Paint()..color = AppColors.primary,
      );
      return;
    }

    var minLat = route.first.latitude;
    var maxLat = route.first.latitude;
    var minLng = route.first.longitude;
    var maxLng = route.first.longitude;
    for (final point in route.skip(1)) {
      minLat = math.min(minLat, point.latitude);
      maxLat = math.max(maxLat, point.latitude);
      minLng = math.min(minLng, point.longitude);
      maxLng = math.max(maxLng, point.longitude);
    }
    final latSpan = math.max(maxLat - minLat, .000001);
    final lngSpan = math.max(maxLng - minLng, .000001);
    const padding = 30.0;
    Offset project(LocationSelection point) => Offset(
      padding +
          ((point.longitude - minLng) / lngSpan) *
              math.max(1, size.width - padding * 2),
      padding +
          ((maxLat - point.latitude) / latSpan) *
              math.max(1, size.height - padding * 2),
    );

    final path = Path()..moveTo(project(route.first).dx, project(route.first).dy);
    for (final point in route.skip(1)) {
      final offset = project(point);
      path.lineTo(offset.dx, offset.dy);
    }
    canvas.drawPath(
      path,
      Paint()
        ..color = ended ? AppColors.success : AppColors.primary
        ..strokeWidth = 7
        ..style = PaintingStyle.stroke
        ..strokeJoin = StrokeJoin.round
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawCircle(project(route.first), 9, Paint()..color = AppColors.success);
    canvas.drawCircle(
      project(route.last),
      9,
      Paint()..color = ended ? AppColors.success : AppColors.primaryDark,
    );
  }

  @override
  bool shouldRepaint(covariant _RoadPainter oldDelegate) =>
      route != oldDelegate.route || ended != oldDelegate.ended;
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

String _durationLabel(int seconds) {
  if (seconds <= 0) return '时间计算中';
  final hours = seconds ~/ 3600;
  final minutes = (seconds % 3600) ~/ 60;
  if (hours <= 0) return '$minutes分钟';
  if (minutes == 0) return '$hours小时';
  return '$hours小时$minutes分';
}

String _elapsedLabel(String? start, String? end) {
  final startAt = DateTime.tryParse(start ?? '');
  final endAt = DateTime.tryParse(end ?? '');
  if (startAt == null || endAt == null || endAt.isBefore(startAt)) return '已完成';
  return _durationLabel(endAt.difference(startAt).inSeconds);
}
