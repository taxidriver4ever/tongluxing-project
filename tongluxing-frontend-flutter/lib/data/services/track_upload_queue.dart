import 'dart:convert';
import 'dart:math' as math;

import 'package:shared_preferences/shared_preferences.dart';

enum TrackQuality {
  normal('NORMAL'),
  degradedL1('DEGRADED_L1'),
  degradedL2('DEGRADED_L2'),
  degradedL3('DEGRADED_L3');

  const TrackQuality(this.wireName);
  final String wireName;
}

/// 缓存降级参数的唯一入口，后续接 Remote Config 时只替换此对象。
class TrackQueuePolicy {
  const TrackQueuePolicy({
    this.maxPendingPoints = 1200,
    this.level1Ratio = .60,
    this.level2Ratio = .80,
    this.level3Ratio = .95,
    this.window = const Duration(minutes: 10),
    this.keepRecent = const Duration(minutes: 10),
    this.level1Retention = .50,
    this.level2Retention = .35,
    this.level3Retention = .25,
    this.level1PointCap = 30,
    this.level2PointCap = 24,
    this.level3PointCap = 18,
    this.level1EpsilonMeters = 12,
    this.level2EpsilonMeters = 25,
    this.level3EpsilonMeters = 45,
    this.abnormalAccuracyMeters = 50,
    this.extremeSpeedMetersPerSecond = 55,
    this.gpsJumpMeters = 500,
    this.longGap = const Duration(seconds: 45),
    this.abnormalBearingChangeDegrees = 100,
  });

  final int maxPendingPoints;
  final double level1Ratio, level2Ratio, level3Ratio;
  final Duration window, keepRecent, longGap;
  final double level1Retention, level2Retention, level3Retention;
  final int level1PointCap, level2PointCap, level3PointCap;
  final double level1EpsilonMeters, level2EpsilonMeters, level3EpsilonMeters;
  final double abnormalAccuracyMeters, extremeSpeedMetersPerSecond;
  final double gpsJumpMeters, abnormalBearingChangeDegrees;

  TrackQuality qualityForCount(int count) {
    if (maxPendingPoints <= 0) return TrackQuality.normal;
    final ratio = count / maxPendingPoints;
    if (ratio >= level3Ratio) return TrackQuality.degradedL3;
    if (ratio >= level2Ratio) return TrackQuality.degradedL2;
    if (ratio >= level1Ratio) return TrackQuality.degradedL1;
    return TrackQuality.normal;
  }

  double epsilon(TrackQuality q) => switch (q) {
    TrackQuality.degradedL1 => level1EpsilonMeters,
    TrackQuality.degradedL2 => level2EpsilonMeters,
    TrackQuality.degradedL3 => level3EpsilonMeters,
    TrackQuality.normal => 0,
  };
  double retention(TrackQuality q) => switch (q) {
    TrackQuality.degradedL1 => level1Retention,
    TrackQuality.degradedL2 => level2Retention,
    TrackQuality.degradedL3 => level3Retention,
    TrackQuality.normal => 1,
  };
  int pointCap(TrackQuality q) => switch (q) {
    TrackQuality.degradedL1 => level1PointCap,
    TrackQuality.degradedL2 => level2PointCap,
    TrackQuality.degradedL3 => level3PointCap,
    TrackQuality.normal => maxPendingPoints,
  };
}

class TrackUploadBatch {
  const TrackUploadBatch({
    required this.points,
    this.compression,
    this.segments = const [],
  });
  final List<Map<String, dynamic>> points;
  final Map<String, dynamic>? compression;
  final List<Map<String, dynamic>> segments;
  Map<String, dynamic> toJson() => {
    'points': points,
    if (compression != null) 'compression': compression,
    if (segments.isNotEmpty) 'compressedSegments': segments,
  };
}

/// 先落盘再上传；仅在缓存压力下重构已结束的历史时间窗。
class TrackUploadQueue {
  TrackUploadQueue({this.policy = const TrackQueuePolicy()});
  final TrackQueuePolicy policy;

  String _queueKey(String tripId, String userId) =>
      'track_upload_queue:$tripId:$userId';
  String _sequenceKey(String tripId, String userId) =>
      'track_upload_sequence:$tripId:$userId';
  String _capturedAtKey(String tripId, String userId) =>
      'track_upload_last_captured_at:$tripId:$userId';

  Future<DateTime?> lastCapturedAt(String tripId, String userId) async {
    final raw = (await SharedPreferences.getInstance()).getString(
      _capturedAtKey(tripId, userId),
    );
    return raw == null ? null : DateTime.tryParse(raw);
  }

  Future<void> markCapturedAt(
    String tripId,
    String userId,
    DateTime value,
  ) async {
    await (await SharedPreferences.getInstance()).setString(
      _capturedAtKey(tripId, userId),
      value.toIso8601String(),
    );
  }

  Future<int> nextSequence(String tripId, String userId) async {
    final storage = await SharedPreferences.getInstance();
    final queuedMax = (await pending(tripId, userId)).fold<int>(
      0,
      (value, p) => math.max(value, (p['sequenceNo'] as num?)?.toInt() ?? 0),
    );
    final next =
        math.max(storage.getInt(_sequenceKey(tripId, userId)) ?? 0, queuedMax) +
        1;
    await storage.setInt(_sequenceKey(tripId, userId), next);
    return next;
  }

  Future<void> enqueue(
    String tripId,
    String userId,
    Map<String, dynamic> point,
  ) async {
    final storage = await SharedPreferences.getInstance();
    var rows = await pending(tripId, userId);
    final sequence = (point['sequenceNo'] as num).toInt();
    if (rows.any((p) => (p['sequenceNo'] as num?)?.toInt() == sequence)) {
      return;
    }
    rows.add(Map<String, dynamic>.from(point));
    rows.sort(_comparePoint);
    rows = TrackQueueDegrader(policy).degrade(rows);
    await storage.setString(_queueKey(tripId, userId), jsonEncode(rows));
  }

  Future<List<Map<String, dynamic>>> pending(
    String tripId,
    String userId,
  ) async {
    final raw = (await SharedPreferences.getInstance()).getString(
      _queueKey(tripId, userId),
    );
    if (raw == null || raw.isEmpty) return [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return [];
      final rows = decoded
          .whereType<Map>()
          .map((p) => Map<String, dynamic>.from(p))
          .toList();
      rows.sort(_comparePoint);
      return rows;
    } catch (_) {
      // 无 ACK 时不删除损坏缓存，避免把解析失败误认为上传成功。
      return [];
    }
  }

  Future<TrackUploadBatch> prepareBatch(
    String tripId,
    String userId, {
    int maxPoints = 200,
  }) async {
    final stored = (await pending(tripId, userId)).take(maxPoints).toList();
    final points = <Map<String, dynamic>>[],
        segments = <Map<String, dynamic>>[];
    var originalCount = stored.length;
    var highest = TrackQuality.normal;
    for (final storedPoint in stored) {
      final point = Map<String, dynamic>.from(storedPoint)
        ..removeWhere((key, _) => key.startsWith('_trackQueue'));
      points.add(point);
      final count = (storedPoint['_trackQueueOriginalPointCount'] as num?)
          ?.toInt();
      final from = (storedPoint['_trackQueueFromSequenceNo'] as num?)?.toInt();
      final to = (storedPoint['sequenceNo'] as num?)?.toInt();
      final qualityName = storedPoint['_trackQueueQuality']?.toString();
      if (count == null ||
          count <= 1 ||
          from == null ||
          to == null ||
          qualityName == null) {
        continue;
      }
      originalCount += math.max(0, count - 2);
      final quality = TrackQuality.values.firstWhere(
        (q) => q.wireName == qualityName,
        orElse: () => TrackQuality.degradedL1,
      );
      if (quality.index > highest.index) {
        highest = quality;
      }
      segments.add({
        'fromSequenceNo': from,
        'toSequenceNo': to,
        'quality': quality.wireName,
        'originalPointCount': count,
      });
    }
    return TrackUploadBatch(
      points: points,
      segments: segments,
      compression: segments.isEmpty
          ? null
          : {
              'compressed': true,
              'type': 'RDP',
              'quality': highest.wireName,
              'originalPointCount': originalCount,
              'uploadedPointCount': points.length,
            },
    );
  }

  Future<void> removeMany(
    String tripId,
    String userId,
    Iterable<int> sequenceNos,
  ) async {
    final targets = sequenceNos.toSet();
    if (targets.isEmpty) return;
    final storage = await SharedPreferences.getInstance();
    final rows = await pending(tripId, userId);
    rows.removeWhere(
      (p) => targets.contains((p['sequenceNo'] as num?)?.toInt()),
    );
    if (rows.isEmpty) {
      await storage.remove(_queueKey(tripId, userId));
    } else {
      await storage.setString(_queueKey(tripId, userId), jsonEncode(rows));
    }
  }

  Future<int> count(String tripId, String userId) async =>
      (await pending(tripId, userId)).length;

  static int _comparePoint(Map<String, dynamic> a, Map<String, dynamic> b) {
    final at = DateTime.tryParse(a['recordTime']?.toString() ?? '');
    final bt = DateTime.tryParse(b['recordTime']?.toString() ?? '');
    final byTime = (at ?? DateTime.fromMillisecondsSinceEpoch(0)).compareTo(
      bt ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
    return byTime != 0
        ? byTime
        : ((a['sequenceNo'] as num?)?.toInt() ?? 0).compareTo(
            (b['sequenceNo'] as num?)?.toInt() ?? 0,
          );
  }
}

/// 纯算法组件，RDP 的距离单位为米。
class TrackQueueDegrader {
  const TrackQueueDegrader(this.policy);
  final TrackQueuePolicy policy;

  List<Map<String, dynamic>> degrade(List<Map<String, dynamic>> input) {
    final quality = policy.qualityForCount(input.length);
    if (quality == TrackQuality.normal || input.length <= 2) return input;
    final latest = _time(input.last);
    if (latest == null) return input;
    final cutoff = latest.subtract(policy.keepRecent);
    final result = <Map<String, dynamic>>[];
    var window = <Map<String, dynamic>>[];
    DateTime? start;
    void flush() {
      if (window.isEmpty) return;
      final existingLevel = window.fold<int>(0, (level, point) {
        final name = point['_trackQueueQuality']?.toString();
        final pointLevel = TrackQuality.values
            .where((value) => value.wireName == name)
            .fold<int>(0, (value, item) => math.max(value, item.index));
        return math.max(level, pointLevel);
      });
      result.addAll(
        existingLevel >= quality.index
            ? window
            : simplifyWindow(window, quality),
      );
      window = [];
      start = null;
    }

    for (final point in input) {
      final time = _time(point);
      if (time == null || !time.isBefore(cutoff)) {
        flush();
        result.add(point);
        continue;
      }
      start ??= time;
      if (time.difference(start!) >= policy.window) {
        flush();
        start = time;
      }
      window.add(point);
    }
    flush();
    result.sort(TrackUploadQueue._comparePoint);
    return result;
  }

  List<Map<String, dynamic>> simplifyWindow(
    List<Map<String, dynamic>> points,
    TrackQuality quality,
  ) {
    if (points.length <= 2) return points;
    final mustKeep = _mustKeepIndexes(points);
    final anchors = mustKeep.toList()..sort();
    final kept = <int>{...mustKeep};
    for (var i = 0; i < anchors.length - 1; i++) {
      kept.addAll(
        _rdpIndexes(
          points,
          anchors[i],
          anchors[i + 1],
          policy.epsilon(quality),
        ),
      );
    }
    var indexes = kept.toList()..sort();
    final ratioCap = math.max(
      2,
      (points.length * policy.retention(quality)).ceil(),
    );
    indexes = _capIndexes(
      indexes,
      mustKeep,
      math.min(policy.pointCap(quality), ratioCap),
    );
    return [
      for (var i = 0; i < indexes.length; i++)
        Map<String, dynamic>.from(points[indexes[i]])
          ..['_trackQueueQuality'] = quality.wireName
          ..addAll(
            i == 0
                ? {}
                : {
                    '_trackQueueFromSequenceNo':
                        (points[indexes[i - 1]]['sequenceNo'] as num?)?.toInt(),
                    '_trackQueueOriginalPointCount': _originalPointCount(
                      points,
                      indexes[i - 1],
                      indexes[i],
                    ),
                  },
          ),
    ];
  }

  int _originalPointCount(
    List<Map<String, dynamic>> points,
    int start,
    int end,
  ) {
    var count = 1;
    for (var i = start + 1; i <= end; i++) {
      final represented =
          (points[i]['_trackQueueOriginalPointCount'] as num?)?.toInt() ?? 2;
      count += math.max(1, represented - 1);
    }
    return count;
  }

  Set<int> _mustKeepIndexes(List<Map<String, dynamic>> points) {
    final keep = <int>{0, points.length - 1};
    for (var i = 0; i < points.length; i++) {
      final p = points[i];
      final accuracy = (p['accuracy'] as num?)?.toDouble();
      final speed = (p['speed'] as num?)?.toDouble();
      if (p['mockLocation'] == true ||
          p['businessCritical'] == true ||
          p['nearBusinessNode'] == true ||
          (accuracy != null && accuracy > policy.abnormalAccuracyMeters) ||
          (speed != null && speed > policy.extremeSpeedMetersPerSecond)) {
        keep.add(i);
      }
      if (i == 0) {
        continue;
      }
      final previous = points[i - 1];
      final previousTime = _time(previous), currentTime = _time(p);
      final distance = _distanceMeters(previous, p);
      final seconds = previousTime == null || currentTime == null
          ? 0
          : currentTime.difference(previousTime).inSeconds;
      if (distance >= policy.gpsJumpMeters ||
          (seconds > 0 &&
              distance / seconds > policy.extremeSpeedMetersPerSecond) ||
          seconds > policy.longGap.inSeconds) {
        keep
          ..add(i - 1)
          ..add(i);
      }
      if (i < points.length - 1 &&
          _angleDifference(_bearing(previous, p), _bearing(p, points[i + 1])) >=
              policy.abnormalBearingChangeDegrees) {
        keep.add(i);
      }
    }
    return keep;
  }

  Set<int> _rdpIndexes(
    List<Map<String, dynamic>> points,
    int start,
    int end,
    double epsilon,
  ) {
    if (end <= start + 1) return {start, end};
    var farthest = -1, maxDistance = -1.0;
    for (var i = start + 1; i < end; i++) {
      final d = perpendicularDistance(points[i], points[start], points[end]);
      if (d > maxDistance) {
        maxDistance = d;
        farthest = i;
      }
    }
    return farthest >= 0 && maxDistance > epsilon
        ? {
            ..._rdpIndexes(points, start, farthest, epsilon),
            ..._rdpIndexes(points, farthest, end, epsilon),
          }
        : {start, end};
  }

  List<int> _capIndexes(List<int> indexes, Set<int> mustKeep, int maxPoints) {
    if (indexes.length <= maxPoints) return indexes;
    final protected = indexes.where(mustKeep.contains).toSet();
    // Must-Keep 优先于 cap；异常密集窗口允许暂时超过上限。
    if (protected.length >= maxPoints) {
      return protected.toList()..sort();
    }
    final candidates = indexes.where((i) => !protected.contains(i)).toList();
    final slots = maxPoints - protected.length;
    final selected = <int>{...protected};
    for (var i = 0; i < slots; i++) {
      selected.add(
        candidates[slots == 1
            ? candidates.length ~/ 2
            : (i * (candidates.length - 1) / (slots - 1)).round()],
      );
    }
    return selected.toList()..sort();
  }

  double perpendicularDistance(
    Map<String, dynamic> p,
    Map<String, dynamic> start,
    Map<String, dynamic> end,
  ) {
    final cosLat = math.cos(_lat(start) * math.pi / 180);
    double x(Map<String, dynamic> v) =>
        (_lng(v) - _lng(start)) * 111320 * cosLat;
    double y(Map<String, dynamic> v) => (_lat(v) - _lat(start)) * 110540;
    final px = x(p), py = y(p), ex = x(end), ey = y(end);
    final length = ex * ex + ey * ey;
    if (length == 0) return math.sqrt(px * px + py * py);
    final t = ((px * ex + py * ey) / length).clamp(0.0, 1.0);
    return math.sqrt(math.pow(px - t * ex, 2) + math.pow(py - t * ey, 2));
  }

  double _distanceMeters(Map<String, dynamic> a, Map<String, dynamic> b) {
    const radius = 6371000.0;
    final lat1 = _lat(a) * math.pi / 180, lat2 = _lat(b) * math.pi / 180;
    final dLat = lat2 - lat1, dLng = (_lng(b) - _lng(a)) * math.pi / 180;
    final h =
        math.sin(dLat / 2) * math.sin(dLat / 2) +
        math.cos(lat1) *
            math.cos(lat2) *
            math.sin(dLng / 2) *
            math.sin(dLng / 2);
    return radius * 2 * math.atan2(math.sqrt(h), math.sqrt(1 - h));
  }

  double _bearing(Map<String, dynamic> a, Map<String, dynamic> b) {
    final lat1 = _lat(a) * math.pi / 180, lat2 = _lat(b) * math.pi / 180;
    final dLng = (_lng(b) - _lng(a)) * math.pi / 180;
    return math.atan2(
          math.sin(dLng) * math.cos(lat2),
          math.cos(lat1) * math.sin(lat2) -
              math.sin(lat1) * math.cos(lat2) * math.cos(dLng),
        ) *
        180 /
        math.pi;
  }

  double _angleDifference(double a, double b) {
    final d = (a - b).abs() % 360;
    return d > 180 ? 360 - d : d;
  }

  DateTime? _time(Map<String, dynamic> p) =>
      DateTime.tryParse(p['recordTime']?.toString() ?? '');
  double _lat(Map<String, dynamic> p) =>
      (p['latitude'] as num?)?.toDouble() ?? 0;
  double _lng(Map<String, dynamic> p) =>
      (p['longitude'] as num?)?.toDouble() ?? 0;
}
