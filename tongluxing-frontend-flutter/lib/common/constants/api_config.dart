abstract final class ApiConfig {
  static const baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    // 当前默认使用本地后端；USB 真机脚本会建立 adb reverse 并显式注入同一地址。
    defaultValue: 'http://127.0.0.1:18080/api',
  );
}
