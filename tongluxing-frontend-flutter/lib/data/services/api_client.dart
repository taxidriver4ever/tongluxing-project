import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import '../../common/constants/api_config.dart';
import '../mock/demo_api.dart';

class ApiException implements Exception {
  const ApiException(this.message, {this.statusCode, this.code});
  final String message;
  final int? statusCode;
  final int? code;
  @override
  String toString() => message;
}

class SessionInvalidation {
  const SessionInvalidation({required this.message, required this.kicked});
  final String message;
  final bool kicked;
}

class ApiClient {
  ApiClient({http.Client? client, this.useDemo = demoMode})
    : _client = client ?? http.Client();

  static const int accountLoggedInElsewhereCode = 40101;
  final http.Client _client;
  final bool useDemo;
  String? token;
  void Function(SessionInvalidation event)? onSessionInvalidated;

  /// App 默认连接后端；仅组件测试或离线演示显式开启 API_DEMO。
  static const bool demoMode = bool.fromEnvironment('API_DEMO');

  Future<dynamic> get(String path, {Map<String, String>? query}) =>
      _request('GET', path, query: query);

  Future<dynamic> post(String path, {Object? body}) =>
      _request('POST', path, body: body);

  Future<dynamic> put(String path, {Object? body}) =>
      _request('PUT', path, body: body);

  Future<dynamic> delete(String path) => _request('DELETE', path);

  Future<void> putBytes(
    String url,
    Uint8List bytes, {
    required String contentType,
  }) async {
    final response = await _client.put(
      Uri.parse(url),
      headers: {'Content-Type': contentType},
      body: bytes,
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException('文件上传失败', statusCode: response.statusCode);
    }
  }

  Future<dynamic> _request(
    String method,
    String path, {
    Map<String, String>? query,
    Object? body,
  }) async {
    if (useDemo) return DemoApi.request(method, path, body: body);
    final base = Uri.parse(ApiConfig.baseUrl);
    final uri = base.replace(
      path: '${base.path}${path.startsWith('/') ? path : '/$path'}',
      queryParameters: query?.isEmpty == true ? null : query,
    );
    final requestToken = token;
    final headers = <String, String>{'Content-Type': 'application/json'};
    if (requestToken?.isNotEmpty == true) {
      headers['Authorization'] = 'Bearer $requestToken';
    }

    late http.Response response;
    try {
      response = switch (method) {
        'POST' => await _client.post(
          uri,
          headers: headers,
          body: jsonEncode(body),
        ),
        'PUT' => await _client.put(
          uri,
          headers: headers,
          body: jsonEncode(body),
        ),
        'DELETE' => await _client.delete(uri, headers: headers),
        _ => await _client.get(uri, headers: headers),
      };
    } catch (_) {
      throw const ApiException('无法连接服务器，请确认后端已启动');
    }

    dynamic payload;
    try {
      payload = response.body.isEmpty ? null : jsonDecode(response.body);
    } catch (_) {
      throw ApiException('服务器返回了无法识别的数据', statusCode: response.statusCode);
    }

    final businessCode = payload is Map
        ? int.tryParse(payload['code']?.toString() ?? '')
        : null;
    final message = payload is Map
        ? payload['message']?.toString() ?? '请求失败'
        : '请求失败';
    if (businessCode == accountLoggedInElsewhereCode &&
        requestToken?.isNotEmpty == true) {
      onSessionInvalidated?.call(
        SessionInvalidation(message: message, kicked: true),
      );
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(
        message,
        statusCode: response.statusCode,
        code: businessCode,
      );
    }
    if (payload is Map && payload.containsKey('code')) {
      if (businessCode != 0 && businessCode != 200) {
        throw ApiException(message, code: businessCode);
      }
      return payload['data'];
    }
    return payload;
  }
}
