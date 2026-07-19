import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:tongluxing_frontend_flutter/app/app.dart';
import 'package:tongluxing_frontend_flutter/app/app_session.dart';
import 'package:tongluxing_frontend_flutter/data/services/api_client.dart';
import 'package:tongluxing_frontend_flutter/features/home/pages/map_home_page.dart';
import 'package:tongluxing_frontend_flutter/features/profile/pages/profile_page.dart';
import 'package:tongluxing_frontend_flutter/features/shop/pages/shop_home_page.dart';

void main() {
  testWidgets('登录页展示核心原型内容', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final session = AppSession(ApiClient(useDemo: true));
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

  test('组件测试可显式注入 DemoApi', () async {
    SharedPreferences.setMockInitialValues({});
    final session = AppSession(ApiClient(useDemo: true));
    await session.initialize();
    await session.login('13888888888', '12345678');

    expect(session.signedIn, isTrue);
    expect(session.userId, '10086');
  });

  testWidgets('地图首页底部操作卡保持在屏幕下半区', (tester) async {
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

    final screenHeight = tester.getSize(find.byType(Scaffold)).height;
    final buttonCenter = tester.getCenter(find.text('创建行程'));
    expect(buttonCenter.dy, greaterThan(screenHeight / 2));
  });

  testWidgets('个人中心长内容可以纵向滚动', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final session = AppSession(ApiClient(useDemo: true));
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
    await tester.pump();
    expect(scrollable.position.pixels, greaterThan(0));
  });

  testWidgets('商城长内容可以纵向滚动', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(home: Scaffold(body: ShopHomePage())),
    );

    final scrollable = tester.state<ScrollableState>(
      find.byType(Scrollable).first,
    );
    await tester.drag(find.byType(CustomScrollView), const Offset(0, -500));
    await tester.pump();
    expect(scrollable.position.pixels, greaterThan(0));
  });
}
