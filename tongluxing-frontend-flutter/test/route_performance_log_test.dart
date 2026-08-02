import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:tongluxing_frontend_flutter/data/models/app_models.dart';
import 'package:tongluxing_frontend_flutter/data/services/api_client.dart';
import 'package:tongluxing_frontend_flutter/features/trip/widgets/route_map_view.dart';

void main() {
  late DebugPrintCallback originalDebugPrint;
  late List<String> logs;

  setUp(() {
    originalDebugPrint = debugPrint;
    logs = <String>[];
    debugPrint = (message, {wrapWidth}) {
      if (message != null) logs.add(message);
    };
  });

  tearDown(() {
    debugPrint = originalDebugPrint;
  });

  test('路线接口输出请求开始和接口返回日志', () async {
    final api = ApiClient(
      client: MockClient(
        (_) async => http.Response(
          jsonEncode(<String, Object?>{'code': 0, 'data': <String, Object?>{}}),
          200,
          headers: const {'content-type': 'application/json'},
        ),
      ),
      useDemo: false,
    );

    await api.post('/v1/map/routes/plan', body: const <String, Object?>{});

    expect(
      logs.any(
        (line) =>
            line.contains('[RoutePerformance] request_start') &&
            line.contains('path=/v1/map/routes/plan'),
      ),
      isTrue,
    );
    expect(
      logs.any(
        (line) =>
            line.contains('[RoutePerformance] api_response') &&
            line.contains('status=200') &&
            line.contains('elapsedMs='),
      ),
      isTrue,
    );
  });

  testWidgets('RouteMapView 在地图帧完成后输出点数量和绘制耗时', (tester) async {
    const points = <LocationSelection>[
      LocationSelection(
        name: '起点',
        address: '',
        latitude: 39.90,
        longitude: 116.39,
      ),
      LocationSelection(
        name: '途经点',
        address: '',
        latitude: 39.91,
        longitude: 116.40,
      ),
      LocationSelection(
        name: '终点',
        address: '',
        latitude: 39.92,
        longitude: 116.41,
      ),
    ];

    try {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: RouteMapView(
              polylinePoints: points,
              performanceLabel: 'test_route',
            ),
          ),
        ),
      );
      await tester.pump();

      expect(
        logs.any(
          (line) =>
              line.contains('[RoutePerformance] map_draw_complete') &&
              line.contains('label=test_route') &&
              line.contains('rawPoints=3') &&
              line.contains('drawnPoints=3') &&
              line.contains('buildCount=1'),
        ),
        isTrue,
      );
    } finally {
      // Widget 测试框架会在 tearDown 之前检查全局 debug 变量，必须在测试体结束前恢复。
      debugPrint = originalDebugPrint;
    }
  });
}
