import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../data/services/api_client.dart';
import '../data/services/app_services.dart';

class AppSession extends ChangeNotifier {
  static const _accessTokenKey = 'tlx_access_token';
  static const _refreshTokenKey = 'tlx_refresh_token';
  static const _userIdKey = 'tlx_user_id';
  static const _deviceIdKey = 'tlx_device_id';

  AppSession(this.api);
  final ApiClient api;
  String? userId;
  bool initialized = false;
  bool get signedIn => api.token?.isNotEmpty == true;

  Future<void> initialize() async {
    final storage = await SharedPreferences.getInstance();
    final storedToken = storage.getString(_accessTokenKey);
    final storedRefreshToken = storage.getString(_refreshTokenKey);
    if (api.useDemo) {
      api.token = storedToken;
      userId = storage.getString(_userIdKey);
    } else {
      final restored = await _restoreServerSession(
        storage,
        storedToken,
        storedRefreshToken,
      );
      if (!restored) await _clearStoredSession(storage);
    }
    initialized = true;
    notifyListeners();
  }

  Future<bool> _restoreServerSession(
    SharedPreferences storage,
    String? accessToken,
    String? refreshToken,
  ) async {
    final locallyValid = _isUsableAccessToken(accessToken);
    if (locallyValid) {
      api.token = accessToken;
      try {
        await AuthService(api).currentUser();
        userId = storage.getString(_userIdKey);
        return true;
      } on ApiException catch (error) {
        // 网络暂时不可达时保留未过期会话；401/403 才视为服务端已撤销。
        if (error.statusCode == null) {
          userId = storage.getString(_userIdKey);
          return true;
        }
      }
    }
    if (refreshToken?.isNotEmpty != true) return false;
    api.token = null;
    try {
      final refreshed = await AuthService(api).refresh(refreshToken!);
      if (refreshed.token.isEmpty || refreshed.refreshToken.isEmpty) {
        return false;
      }
      api.token = refreshed.token;
      await storage.setString(_accessTokenKey, refreshed.token);
      await storage.setString(_refreshTokenKey, refreshed.refreshToken);
      userId = storage.getString(_userIdKey);
      return true;
    } on ApiException {
      return false;
    }
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
    final storage = await SharedPreferences.getInstance();
    var deviceId = storage.getString(_deviceIdKey);
    if (deviceId == null || deviceId.isEmpty) {
      deviceId = 'flutter-${DateTime.now().microsecondsSinceEpoch}';
      await storage.setString(_deviceIdKey, deviceId);
    }
    final session = await AuthService(
      api,
    ).passwordLogin(phone, password, deviceId: deviceId);
    api.token = session.token;
    userId = session.userId;
    await storage.setString(_accessTokenKey, session.token);
    if (session.refreshToken?.isNotEmpty == true) {
      await storage.setString(_refreshTokenKey, session.refreshToken!);
    } else {
      await storage.remove(_refreshTokenKey);
    }
    if (session.userId != null) {
      await storage.setString(_userIdKey, session.userId!);
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
    await _clearStoredSession(storage);
    notifyListeners();
  }

  Future<void> _clearStoredSession(SharedPreferences storage) async {
    api.token = null;
    userId = null;
    await storage.remove(_accessTokenKey);
    await storage.remove(_refreshTokenKey);
    await storage.remove(_userIdKey);
  }
}
