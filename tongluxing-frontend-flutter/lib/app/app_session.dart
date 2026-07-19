import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../data/services/api_client.dart';
import '../data/services/app_services.dart';

class AppSession extends ChangeNotifier {
  AppSession(this.api);
  final ApiClient api;
  String? userId;
  bool initialized = false;
  bool get signedIn => api.token?.isNotEmpty == true;

  Future<void> initialize() async {
    final storage = await SharedPreferences.getInstance();
    final storedToken = storage.getString('tlx_access_token');
    if (api.useDemo || _isUsableAccessToken(storedToken)) {
      api.token = storedToken;
      userId = storage.getString('tlx_user_id');
    } else {
      // 清理早期 Mock Token 和已过期 JWT，避免显示已登录但接口持续返回 401。
      await storage.remove('tlx_access_token');
      await storage.remove('tlx_user_id');
    }
    initialized = true;
    notifyListeners();
  }

  bool _isUsableAccessToken(String? token) {
    if (token == null || token.isEmpty) return false;
    try {
      final parts = token.split('.');
      if (parts.length != 3) return false;
      final payload = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
      );
      final expiresAt = payload is Map ? payload['exp'] : null;
      return expiresAt is num &&
          expiresAt.toInt() > DateTime.now().millisecondsSinceEpoch ~/ 1000;
    } catch (_) {
      return false;
    }
  }

  Future<void> login(String phone, String password) async {
    final session = await AuthService(api).passwordLogin(phone, password);
    api.token = session.token;
    userId = session.userId;
    final storage = await SharedPreferences.getInstance();
    await storage.setString('tlx_access_token', session.token);
    if (session.userId != null) {
      await storage.setString('tlx_user_id', session.userId!);
    }
    notifyListeners();
  }

  Future<void> logout() async {
    try {
      await AuthService(api).logout();
    } catch (_) {}
    api.token = null;
    userId = null;
    final storage = await SharedPreferences.getInstance();
    await storage.remove('tlx_access_token');
    await storage.remove('tlx_user_id');
    notifyListeners();
  }
}
