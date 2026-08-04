import 'dart:convert';
import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../../common/constants/api_config.dart';
import '../mock/demo_api.dart';

class ApiException implements Exception {
  const ApiException(
    this.message, {
    this.statusCode,
    this.code,
    this.networkError = false,
  });
  final String message;
  final int? statusCode;
  final int? code;
  final bool networkError;
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
  static const Duration requestTimeout = Duration(seconds: 15);
  static final RegExp _tripDetailPath = RegExp(
    r'^/v1/trips/[^/]+(?:/public-detail)?$',
  );
  static final RegExp _storedRoutePath = RegExp(r'^/v1/trip/[^/]+/route$');
  static final RegExp _draftRoutePlanPath = RegExp(
    r'^/v1/trip/draft/[^/]+/route/plan$',
  );
  static int _routeRequestSequence = 0;

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

  /// 发送 PATCH 请求，适合只更新群名称、置顶等部分资源字段。
  Future<dynamic> patch(String path, {Object? body}) =>
      _request('PATCH', path, body: body);

  Future<dynamic> delete(String path) => _request('DELETE', path);

  Future<void> putBytes(
    String url,
    Uint8List bytes, {
    required String contentType,
  }) async {
    late http.Response response;
    try {
      response = await _client
          .put(
            Uri.parse(url),
            headers: {'Content-Type': contentType},
            body: bytes,
          )
          .timeout(requestTimeout);
    } on TimeoutException {
      throw const ApiException('文件上传超时，请重试', networkError: true);
    }
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
    final tracesRouteLoading = _tracesRouteLoading(method, path);
    final requestId = tracesRouteLoading
        ? '${DateTime.now().microsecondsSinceEpoch}-${++_routeRequestSequence}'
        : null;
    final requestStopwatch = Stopwatch()..start();
    if (requestId != null) {
      debugPrint(
        '[RoutePerformance] request_start '
        'requestId=$requestId timestamp=${DateTime.now().toIso8601String()} '
        'method=$method path=$path',
      );
    }

    if (useDemo) {
      try {
        final result = await DemoApi.request(method, path, body: body);
        if (requestId != null) {
          debugPrint(
            '[RoutePerformance] api_response '
            'requestId=$requestId timestamp=${DateTime.now().toIso8601String()} '
            'method=$method path=$path status=demo '
            'elapsedMs=${requestStopwatch.elapsedMilliseconds}',
          );
        }
        return result;
      } catch (_) {
        if (requestId != null) {
          debugPrint(
            '[RoutePerformance] api_response '
            'requestId=$requestId timestamp=${DateTime.now().toIso8601String()} '
            'method=$method path=$path status=demo_error '
            'elapsedMs=${requestStopwatch.elapsedMilliseconds}',
          );
        }
        rethrow;
      }
    }
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

    String? encodedBody;
    if (method == 'POST' || method == 'PUT' || method == 'PATCH') {
      try {
        encodedBody = jsonEncode(body);
      } catch (_) {
        // 请求体序列化失败属于客户端数据问题，不能误报成没有网络。
        throw const ApiException('请求数据格式异常，请检查定位数据');
      }
    }

    late http.Response response;
    try {
      response = await (switch (method) {
        'POST' => _client.post(uri, headers: headers, body: encodedBody),
        'PUT' => _client.put(uri, headers: headers, body: encodedBody),
        'PATCH' => _client.patch(uri, headers: headers, body: encodedBody),
        'DELETE' => _client.delete(uri, headers: headers),
        _ => _client.get(uri, headers: headers),
      }).timeout(requestTimeout);
    } on TimeoutException {
      if (requestId != null) {
        debugPrint(
          '[RoutePerformance] api_response '
          'requestId=$requestId timestamp=${DateTime.now().toIso8601String()} '
          'method=$method path=$path status=timeout '
          'elapsedMs=${requestStopwatch.elapsedMilliseconds}',
        );
      }
      throw const ApiException('请求超时，请检查网络后重试', networkError: true);
    } catch (_) {
      if (requestId != null) {
        debugPrint(
          '[RoutePerformance] api_response '
          'requestId=$requestId timestamp=${DateTime.now().toIso8601String()} '
          'method=$method path=$path status=network_error '
          'elapsedMs=${requestStopwatch.elapsedMilliseconds}',
        );
      }
      throw const ApiException('无法连接服务器，请确认后端已启动', networkError: true);
    }

    if (requestId != null) {
      debugPrint(
        '[RoutePerformance] api_response '
        'requestId=$requestId timestamp=${DateTime.now().toIso8601String()} '
        'method=$method path=$path status=${response.statusCode} '
        'elapsedMs=${requestStopwatch.elapsedMilliseconds} '
        'responseBytes=${response.bodyBytes.length}',
      );
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

  /// 只跟踪会直接提供或刷新路线折线的请求，避免普通接口污染性能日志。
  static bool _tracesRouteLoading(String method, String path) {
    if (path == '/v1/map/routes/plan' ||
        _draftRoutePlanPath.hasMatch(path) ||
        _storedRoutePath.hasMatch(path)) {
      return true;
    }
    return method == 'GET' && _tripDetailPath.hasMatch(path);
  }
}
