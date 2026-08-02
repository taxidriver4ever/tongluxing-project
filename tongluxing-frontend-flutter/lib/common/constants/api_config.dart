abstract final class ApiConfig {
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    // 默认连接生产环境；本地调试脚本仍会通过 --dart-define 显式覆盖此地址。
    defaultValue: 'http://43.138.233.211/api',
  );
}
