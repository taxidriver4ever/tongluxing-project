import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'app_session.dart';
import 'routes.dart';
import 'theme.dart';

class TongLuXingApp extends StatelessWidget {
  const TongLuXingApp({super.key});

  @override
  Widget build(BuildContext context) {
    const showAmapProbe = bool.fromEnvironment('AMAP_PROBE');
    final session = context.watch<AppSession>();
    return MaterialApp(
      title: '同路行',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      initialRoute: showAmapProbe
          ? AppRoutes.amapProbe
          : (session.signedIn ? AppRoutes.home : AppRoutes.login),
      onGenerateRoute: AppRoutes.onGenerateRoute,
      builder: (context, child) => GestureDetector(
        behavior: HitTestBehavior.translucent,
        onTap: () => FocusManager.instance.primaryFocus?.unfocus(),
        child: child ?? const SizedBox.shrink(),
      ),
    );
  }
}
