import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:tongluxing_frontend_flutter/data/services/track_upload_queue.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('keeps sequence number stable while queued point retries', () async {
    SharedPreferences.setMockInitialValues({});
    final queue = TrackUploadQueue();
    final sequence = await queue.nextSequence('trip-1', 'user-1');
    await queue.enqueue('trip-1', 'user-1', {
      'tripId': 'trip-1',
      'sequenceNo': sequence,
      'recordTime': '2026-07-27T10:00:00',
    });
    final pending = await queue.pending('trip-1', 'user-1');
    expect(pending.single['sequenceNo'], sequence);
    expect(await queue.nextSequence('trip-1', 'user-1'), sequence + 1);
  });

  Map<String, dynamic> point(
    int index,
    double lat,
    double lng, {
    bool mock = false,
  }) => {
    'tripId': 'trip-1',
    'sequenceNo': index + 1,
    'recordTime': DateTime(
      2026,
      8,
      17,
      10,
    ).add(Duration(seconds: index * 10)).toIso8601String(),
    'latitude': lat,
    'longitude': lng,
    'accuracy': 5,
    'mockLocation': mock,
  };

  const policy = TrackQueuePolicy(
    maxPendingPoints: 10,
    level1PointCap: 6,
    level2PointCap: 5,
    level3PointCap: 4,
    level1Retention: 1,
    level2Retention: 1,
    level3Retention: 1,
    level1EpsilonMeters: 5,
    level2EpsilonMeters: 5,
    level3EpsilonMeters: 5,
  );
  const degrader = TrackQueueDegrader(policy);

  test('straight line is reduced to endpoints', () {
    final points = [
      for (var i = 0; i < 20; i++) point(i, 23, 113 + i * .00001),
    ];
    final result = degrader.simplifyWindow(points, TrackQuality.degradedL1);
    expect(result.length, 2);
    expect(result.first['sequenceNo'], 1);
    expect(result.last['sequenceNo'], 20);
  });

  test('corner and endpoints are preserved', () {
    final points = [
      point(0, 23, 113),
      point(1, 23, 113.001),
      point(2, 23, 113.002),
      point(3, 23.001, 113.002),
      point(4, 23.002, 113.002),
    ];
    final result = degrader.simplifyWindow(points, TrackQuality.degradedL1);
    expect(result.map((p) => p['sequenceNo']), contains(3));
    expect(result.first['sequenceNo'], 1);
    expect(result.last['sequenceNo'], 5);
  });

  test('point cap is enforced for ordinary winding points', () {
    const capDegrader = TrackQueueDegrader(
      TrackQueuePolicy(
        maxPendingPoints: 10,
        level3PointCap: 4,
        level3Retention: 1,
        level3EpsilonMeters: 0,
        abnormalBearingChangeDegrees: 181,
      ),
    );
    final points = [
      for (var i = 0; i < 40; i++)
        point(i, 23 + (i.isEven ? .0002 : 0), 113 + i * .0001),
    ];
    final result = capDegrader.simplifyWindow(points, TrackQuality.degradedL3);
    expect(result.length, lessThanOrEqualTo(4));
  });

  test('small and duplicate polylines do not fail', () {
    final one = [point(0, 23, 113)];
    expect(degrader.simplifyWindow(one, TrackQuality.degradedL1), one);
    final duplicate = [for (var i = 0; i < 8; i++) point(i, 23, 113)];
    final result = degrader.simplifyWindow(duplicate, TrackQuality.degradedL1);
    expect(result.length, 2);
  });

  test('must-keep mock point survives cap', () {
    final points = [
      for (var i = 0; i < 20; i++)
        point(i, 23, 113 + i * .00001, mock: i == 10),
    ];
    final result = degrader.simplifyWindow(points, TrackQuality.degradedL3);
    expect(result.map((p) => p['sequenceNo']), contains(11));
  });

  test('batch metadata preserves original sequence and time', () async {
    SharedPreferences.setMockInitialValues({});
    final queue = TrackUploadQueue(
      policy: const TrackQueuePolicy(
        maxPendingPoints: 5,
        keepRecent: Duration.zero,
        window: Duration(minutes: 10),
        level1Retention: 1,
        level1EpsilonMeters: 5,
      ),
    );
    for (var i = 0; i < 8; i++) {
      await queue.enqueue('trip-1', 'user-1', point(i, 23, 113 + i * .00001));
    }
    final batch = await queue.prepareBatch('trip-1', 'user-1');
    expect(batch.compression?['compressed'], true);
    expect(batch.segments, isNotEmpty);
    expect(batch.points.first['recordTime'], point(0, 23, 113)['recordTime']);
    expect(batch.points.last['sequenceNo'], 8);
  });
}
