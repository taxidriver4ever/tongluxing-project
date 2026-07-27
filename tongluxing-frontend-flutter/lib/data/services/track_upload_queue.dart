import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// 行程轨迹本地可靠队列。
///
/// 定位点在请求服务端前先落盘；网络恢复后仍使用原始采集时间和原 sequenceNo
/// 顺序补传，只有服务端成功接收后才从队列删除。
class TrackUploadQueue {
  String _queueKey(String tripId, String userId) =>
      'track_upload_queue:$tripId:$userId';

  String _sequenceKey(String tripId, String userId) =>
      'track_upload_sequence:$tripId:$userId';

  Future<int> nextSequence(String tripId, String userId) async {
    final storage = await SharedPreferences.getInstance();
    final queue = await pending(tripId, userId);
    final queuedMax = queue.fold<int>(
      0,
      (value, point) => point['sequenceNo'] is num
          ? (point['sequenceNo'] as num).toInt() > value
                ? (point['sequenceNo'] as num).toInt()
                : value
          : value,
    );
    final next = ((storage.getInt(_sequenceKey(tripId, userId)) ?? 0) >
                queuedMax
            ? storage.getInt(_sequenceKey(tripId, userId)) ?? 0
            : queuedMax) +
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
    final rows = await pending(tripId, userId);
    final sequenceNo = (point['sequenceNo'] as num).toInt();
    if (rows.any((row) => (row['sequenceNo'] as num?)?.toInt() == sequenceNo)) {
      return;
    }
    rows.add(Map<String, dynamic>.from(point));
    rows.sort(_comparePoint);
    await storage.setString(_queueKey(tripId, userId), jsonEncode(rows));
  }

  Future<List<Map<String, dynamic>>> pending(
    String tripId,
    String userId,
  ) async {
    final storage = await SharedPreferences.getInstance();
    final raw = storage.getString(_queueKey(tripId, userId));
    if (raw == null || raw.isEmpty) return <Map<String, dynamic>>[];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return <Map<String, dynamic>>[];
      final rows = decoded
          .whereType<Map>()
          .map((row) => Map<String, dynamic>.from(row))
          .toList(growable: true);
      rows.sort(_comparePoint);
      return rows;
    } catch (_) {
      // 损坏的本地缓存不可继续反复阻塞上传，清理后从新点重新建段。
      await storage.remove(_queueKey(tripId, userId));
      return <Map<String, dynamic>>[];
    }
  }

  Future<void> remove(
    String tripId,
    String userId,
    int sequenceNo,
  ) async {
    final storage = await SharedPreferences.getInstance();
    final rows = await pending(tripId, userId);
    rows.removeWhere(
      (row) => (row['sequenceNo'] as num?)?.toInt() == sequenceNo,
    );
    if (rows.isEmpty) {
      await storage.remove(_queueKey(tripId, userId));
      return;
    }
    await storage.setString(_queueKey(tripId, userId), jsonEncode(rows));
  }

  Future<int> count(String tripId, String userId) async =>
      (await pending(tripId, userId)).length;

  static int _comparePoint(
    Map<String, dynamic> left,
    Map<String, dynamic> right,
  ) {
    final leftTime = DateTime.tryParse(left['recordTime']?.toString() ?? '');
    final rightTime = DateTime.tryParse(right['recordTime']?.toString() ?? '');
    final byTime = (leftTime ?? DateTime.fromMillisecondsSinceEpoch(0)).compareTo(
      rightTime ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
    if (byTime != 0) return byTime;
    return ((left['sequenceNo'] as num?)?.toInt() ?? 0).compareTo(
      (right['sequenceNo'] as num?)?.toInt() ?? 0,
    );
  }
}
