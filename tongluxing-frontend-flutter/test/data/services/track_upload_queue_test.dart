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
}
