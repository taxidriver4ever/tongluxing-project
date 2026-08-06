import 'package:flutter/foundation.dart';

/// 主页面 Tab 的轻量跨页面控制器。
///
/// 行程详情等子路由完成“开启行程”后，通过这里通知根页面：关闭子页面、
/// 切换到地图 Tab，并让地图重新读取当前行程状态。它不创建任何新页面。
abstract final class MainTabController {
  static final ValueNotifier<MainTabRequest?> requests =
      ValueNotifier<MainTabRequest?>(null);

  static void showMap({bool refresh = true}) {
    requests.value = MainTabRequest(
      tabIndex: 0,
      refreshMap: refresh,
      issuedAt: DateTime.now().microsecondsSinceEpoch,
    );
  }
}

class MainTabRequest {
  const MainTabRequest({
    required this.tabIndex,
    required this.refreshMap,
    required this.issuedAt,
  });

  final int tabIndex;
  final bool refreshMap;
  final int issuedAt;
}
