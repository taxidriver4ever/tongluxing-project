import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../../app/theme.dart';
import '../../chat/pages/chat_index_page.dart';
import '../../profile/pages/profile_page.dart';
import '../../trip/pages/trip_home_page.dart';
import 'map_home_page.dart';

class MainShellPage extends StatefulWidget {
  const MainShellPage({super.key});

  @override
  State<MainShellPage> createState() => _MainShellPageState();
}

class _MainShellPageState extends State<MainShellPage> {
  int index = 0;
  final Set<int> loadedTabs = {0};
  final profileKey = GlobalKey<ProfilePageState>();

  late final List<Widget> pages = [
    const MapHomePage(),
    const TripHomePage(),
    const ChatIndexPage(),
    ProfilePage(key: profileKey),
  ];

  void selectTab(int value) {
    setState(() {
      index = value;
      loadedTabs.add(value);
    });
    if (value == 3) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => profileKey.currentState?.refresh(),
      );
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
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
