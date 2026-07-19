abstract final class ApiConfig {
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    // 开发默认面向 adb reverse 的 USB 真机；模拟器和生产环境显式覆盖。
    defaultValue: 'http://127.0.0.1:18080/api',
  );
}
