import 'package:flutter/material.dart';

import '../features/auth/pages/login_page.dart';
import '../features/home/pages/amap_probe_page.dart';
import '../features/home/pages/main_shell_page.dart';

abstract final class AppRoutes {
  static const login = '/login';
  static const amapProbe = '/amap-probe';
  static const home = '/home';

  static Route<void> onGenerateRoute(RouteSettings settings) {
    final builder = switch (settings.name) {
      amapProbe => (_) => const AmapProbePage(),
      home => (_) => const MainShellPage(),
      _ => (_) => const LoginPage(),
    };
    return MaterialPageRoute<void>(settings: settings, builder: builder);
  }
}
