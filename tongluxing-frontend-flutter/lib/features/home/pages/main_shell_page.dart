import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../../app/theme.dart';
import '../../chat/pages/chat_index_page.dart';
import '../../profile/pages/profile_page.dart';
import '../../shop/pages/shop_home_page.dart';
import '../../trip/pages/trip_home_page.dart';
import 'map_home_page.dart';

class MainShellPage extends StatefulWidget {
  const MainShellPage({super.key});
  @override
  State<MainShellPage> createState() => _MainShellPageState();
}

class _MainShellPageState extends State<MainShellPage> {
  int index = 0;
  final profileKey = GlobalKey<ProfilePageState>();

  late final List<Widget> pages = [
    const MapHomePage(),
    const TripHomePage(),
    const ChatIndexPage(),
    const ShopHomePage(),
    ProfilePage(key: profileKey),
  ];

  void selectTab(int value) {
    setState(() => index = value);
    if (value == 4) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => profileKey.currentState?.refresh(),
      );
    }
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    // IndexedStack 默认用 loose 约束布局子页面。地图页以 Stack 为根节点，
    // loose 约束会让它只按顶部搜索框的高度收缩，从而把底部浮层顶到状态栏。
    // expand 可保证五个主页面始终获得完整的可用屏幕高度。
    body: IndexedStack(sizing: StackFit.expand, index: index, children: pages),
    bottomNavigationBar: NavigationBar(
      height: 72,
      backgroundColor: Colors.white,
      indicatorColor: AppColors.primarySoft,
      selectedIndex: index,
      onDestinationSelected: selectTab,
      destinations: const [
        NavigationDestination(
          icon: Icon(LucideIcons.map),
          selectedIcon: Icon(LucideIcons.map, color: AppColors.primary),
          label: '地图',
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
          icon: Icon(LucideIcons.store),
          selectedIcon: Icon(LucideIcons.store, color: AppColors.primary),
          label: '商城',
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
