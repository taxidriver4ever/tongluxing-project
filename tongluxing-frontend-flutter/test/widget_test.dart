import 'package:flutter/material.dart';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:tongluxing_frontend_flutter/app/app.dart';
import 'package:tongluxing_frontend_flutter/app/app_session.dart';
import 'package:tongluxing_frontend_flutter/data/services/api_client.dart';
import 'package:tongluxing_frontend_flutter/features/home/pages/map_home_page.dart';
import 'package:tongluxing_frontend_flutter/features/profile/pages/profile_page.dart';

void main() {
  testWidgets('登录页展示核心原型内容', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final session = AppSession(ApiClient());
    await session.initialize();
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: session,
        child: const TongLuXingApp(),
      ),
    );

    expect(find.text('同路行'), findsOneWidget);
    expect(find.text('欢迎回来\n开启下一段旅程'), findsOneWidget);
    expect(find.text('登录'), findsOneWidget);
    expect(find.text('微信手机号一键登录'), findsOneWidget);
  });

  testWidgets('发现地图初始只显示把手，点击后展示搜索框', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: IndexedStack(
            sizing: StackFit.expand,
            children: [MapHomePage()],
          ),
        ),
      ),
    );

    expect(find.byType(DraggableScrollableSheet), findsOneWidget);
    expect(find.text('创建行程'), findsNothing);
    expect(find.text('搜索地点或地址'), findsNothing);
    final initialCollapsedHeight = tester
        .getSize(find.byKey(const ValueKey('map-search-sheet-surface')))
        .height;
    await tester.tap(find.byKey(const ValueKey('map-search-sheet-handle')));
    await tester.pumpAndSettle();
    expect(find.text('搜索地点或地址'), findsOneWidget);
    expect(find.byType(TextField), findsOneWidget);

    // 连续输入和清空时，不应留下上一次搜索的 loading 状态。
    await tester.enterText(
      find.byKey(const ValueKey('map-place-search-field')),
      '广州',
    );
    await tester.pump();
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey('map-place-search-field')),
      '',
    );
    await tester.pump();
    expect(find.byType(LinearProgressIndicator), findsNothing);
    expect(find.byKey(const ValueKey('map-place-search-field')), findsOneWidget);
    await tester.pumpAndSettle();

    // 已展开时再点击把手，搜索区必须完全收起。
    await tester.tap(find.byKey(const ValueKey('map-search-sheet-handle')));
    await tester.pumpAndSettle();
    expect(find.text('搜索地点或地址'), findsNothing);
    final collapsedAfterSearchHeight = tester
        .getSize(find.byKey(const ValueKey('map-search-sheet-surface')))
        .height;
    expect(collapsedAfterSearchHeight, closeTo(initialCollapsedHeight, .01));
  });

  testWidgets('发现地图把手支持上滑展开搜索', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: MapHomePage())),
    );

    final gesture = await tester.startGesture(
      tester.getCenter(find.byKey(const ValueKey('map-search-sheet-handle'))),
    );
    await gesture.moveBy(const Offset(0, -120));
    await tester.pump();
    expect(find.text('搜索地点或地址'), findsOneWidget);
    await gesture.moveBy(const Offset(0, -200));
    await gesture.up();
    await tester.pumpAndSettle();
    expect(find.text('搜索地点或地址'), findsOneWidget);
  });

  testWidgets('个人中心长内容可以纵向滚动', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final session = AppSession(
      ApiClient(
        client: MockClient(
          (_) async => http.Response(
            jsonEncode(<String, Object?>{
              'code': 0,
              'data': <String, Object?>{},
            }),
            200,
            headers: const {'content-type': 'application/json'},
          ),
        ),
      ),
    );
    await session.initialize();
    await tester.pumpWidget(
      ChangeNotifierProvider.value(
        value: session,
        child: const MaterialApp(home: Scaffold(body: ProfilePage())),
      ),
    );

    final scrollable = tester.state<ScrollableState>(
      find.byType(Scrollable).first,
    );
    await tester.drag(find.byType(ListView), const Offset(0, -500));
    await tester.pump(const Duration(milliseconds: 250));
    expect(scrollable.position.pixels, greaterThan(0));
  });
}
