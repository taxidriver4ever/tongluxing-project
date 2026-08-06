import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../../app/theme.dart';
import '../../chat/pages/chat_index_page.dart';
import '../../profile/pages/profile_page.dart';
import '../../trip/pages/trip_home_page.dart';
import 'main_tab_controller.dart';
import 'map_home_page.dart';

class MainShellPage extends StatefulWidget {
  const MainShellPage({super.key});

  @override
  State<MainShellPage> createState() => _MainShellPageState();
}

class _MainShellPageState extends State<MainShellPage> {
  int index = 0;
  final Set<int> loadedTabs = {0};
  final mapKey = GlobalKey<MapHomePageState>();
  final profileKey = GlobalKey<ProfilePageState>();

  late final List<Widget> pages;

  @override
  void initState() {
    super.initState();
    MainTabController.requests.addListener(_handleExternalTabRequest);
    pages = [
      MapHomePage(
        key: mapKey,
        onOpenTripRecommendations: () => selectTab(1),
        onOpenMessages: () => selectTab(2),
      ),
      const TripHomePage(),
      const ChatIndexPage(),
      ProfilePage(key: profileKey),
    ];
  }

  @override
  void dispose() {
    MainTabController.requests.removeListener(_handleExternalTabRequest);
    super.dispose();
  }

  void _handleExternalTabRequest() {
    final request = MainTabController.requests.value;
    if (request == null || !mounted) return;
    selectTab(request.tabIndex, refreshMap: request.refreshMap);
  }

  void selectTab(int value, {bool refreshMap = false}) {
    setState(() {
      index = value;
      loadedTabs.add(value);
    });
    if (value == 0 && refreshMap) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => mapKey.currentState?.refreshFromServer(),
      );
    }
    if (value == 3) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => profileKey.currentState?.refresh(),
      );
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    // 地图页底部面板直接贴住主 Tab，上层页面不因键盘改变地图主体高度。
    // 其他主 Tab 继续使用 Scaffold 默认的键盘避让行为。
    resizeToAvoidBottomInset: index != 0,
    body: IndexedStack(
      sizing: StackFit.expand,
      index: index,
      children: List<Widget>.generate(
        pages.length,
        (tabIndex) => loadedTabs.contains(tabIndex)
            ? pages[tabIndex]
            : const SizedBox.expand(),
      ),
    ),
    bottomNavigationBar: NavigationBar(
      height: 64,
      labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
      backgroundColor: Colors.white,
      indicatorColor: AppColors.primarySoft,
      selectedIndex: index,
      onDestinationSelected: selectTab,
      destinations: const [
        NavigationDestination(
          icon: Icon(LucideIcons.compass),
          selectedIcon: Icon(LucideIcons.compass, color: AppColors.primary),
          label: '发现',
        ),
        NavigationDestination(
          icon: Icon(LucideIcons.route),
          selectedIcon: Icon(LucideIcons.route, color: AppColors.primary),
          label: '行程',
        ),
        NavigationDestination(
          icon: Icon(LucideIcons.messageCircle),
          selectedIcon: Icon(
            LucideIcons.messageCircle,
            color: AppColors.primary,
          ),
          label: '消息',
        ),
        NavigationDestination(
          icon: Icon(LucideIcons.userRound),
          selectedIcon: Icon(LucideIcons.userRound, color: AppColors.primary),
          label: '我的',
        ),
      ],
    ),
  );
}
