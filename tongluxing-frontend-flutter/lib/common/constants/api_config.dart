abstract final class ApiConfig {
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    // 当前默认用于 Android 模拟器访问宿主机上的本地 Spring Boot。
    defaultValue: 'http://localhost:18080/api',
    // 桌面/浏览器本地调试：http://localhost:18080/api
    // 服务器 Nginx 地址保留：http://43.138.233.211/api
    // 上线构建时通过 --dart-define=API_BASE_URL=... 覆盖。
  );
}
