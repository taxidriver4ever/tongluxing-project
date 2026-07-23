import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'app_session.dart';
import 'routes.dart';
import 'theme.dart';

class TongLuXingApp extends StatefulWidget {
  const TongLuXingApp({super.key});

  @override
  State<TongLuXingApp> createState() => _TongLuXingAppState();
}

class _TongLuXingAppState extends State<TongLuXingApp> {
  final navigatorKey = GlobalKey<NavigatorState>();
  bool forcedLogoutDialogVisible = false;

  void _scheduleForcedLogoutDialog(AppSession session) {
    if (!session.hasForcedLogout || forcedLogoutDialogVisible) return;
    forcedLogoutDialogVisible = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final context = navigatorKey.currentContext;
      if (context == null || !mounted) {
        forcedLogoutDialogVisible = false;
        return;
      }
      await showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (dialogContext) => AlertDialog(
          icon: const Icon(Icons.security),
          title: const Text('登录安全提醒'),
          content: Text(session.forcedLogoutMessage ?? '账号已在其他设备登录，请重新登录'),
          actions: [
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('确认'),
            ),
          ],
        ),
      );
      await session.confirmForcedLogout();
      navigatorKey.currentState?.pushNamedAndRemoveUntil(
        AppRoutes.login,
        (_) => false,
      );
      forcedLogoutDialogVisible = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    const showAmapProbe = bool.fromEnvironment('AMAP_PROBE');
    final session = context.watch<AppSession>();
    _scheduleForcedLogoutDialog(session);
    return MaterialApp(
      navigatorKey: navigatorKey,
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
