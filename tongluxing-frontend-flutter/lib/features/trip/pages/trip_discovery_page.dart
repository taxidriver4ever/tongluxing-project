import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/follow_service.dart';
import '../../../data/services/location_snapshot.dart';
import '../../chat/pages/chat_session_page.dart';
import '../../chat/private_chat_flow.dart';
import '../../profile/pages/profile_system_pages.dart';
import '../../profile/widgets/user_avatar.dart';
import '../widgets/trip_discovery_theme.dart';
import 'trip_discovery_detail_page.dart';
import 'trip_discovery_filter_sheet.dart';
import 'trip_discovery_widgets.dart';

class TripDiscoveryPage extends StatefulWidget {
  const TripDiscoveryPage({super.key});

  @override
  State<TripDiscoveryPage> createState() => _TripDiscoveryPageState();
}

class _TripDiscoveryPageState extends State<TripDiscoveryPage> {
  static const tabs = ['目的地', '起点', '路线', '发起人'];
  final search = TextEditingController();
  final scroll = ScrollController();
  Timer? debounce;
  int tabIndex = 0;
  String sort = 'RECOMMENDED';
  TripDiscoveryFilter filter = const TripDiscoveryFilter();
  List<TripDiscoverModel> trips = const [];
  List<Map<String, dynamic>> users = const [];
  bool loading = true;
  bool loadingMore = false;
  bool hasMore = true;
  int page = 1;
  int requestSerial = 0;
  String? error;

  bool get showingUsers => tabIndex == 3;
  String get placeholder => switch (tabIndex) {
    0 => '搜索目的地 / 城市 / 景点',
    1 => '搜索出发地 / 集合点',
    2 => '搜索路线关键词 / 经停点',
    _ => '搜索昵称 / 用户 ID',
  };

  @override
  void initState() {
    super.initState();
    scroll.addListener(() {
      if (!showingUsers && scroll.position.extentAfter < 360) {
        _load(reset: false);
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _load(reset: true));
  }

  @override
  void dispose() {
    debounce?.cancel();
    search.dispose();
    scroll.dispose();
    super.dispose();
  }

  void _searchChanged(String _) {
    setState(() {});
    debounce?.cancel();
    debounce = Timer(const Duration(milliseconds: 360), () => _load(reset: true));
  }

  Future<void> _load({required bool reset}) async {
    if (reset) {
      setState(() {
        loading = true;
        error = null;
      });
    } else {
      if (loading || loadingMore || !hasMore || showingUsers) return;
      setState(() => loadingMore = true);
    }
    final serial = ++requestSerial;
    final targetPage = reset ? 1 : page + 1;
    try {
      if (showingUsers) {
        final result = await UserDiscoveryService(context.read<AppSession>().api)
            .search(keyword: search.text, page: 1, size: 30);
        if (!mounted || serial != requestSerial) return;
        setState(() {
          users = result;
          trips = const [];
          page = 1;
          hasMore = false;
        });
      } else {
        final point = LocationSnapshot.current ?? LocationSnapshot.demoFallback;
        final keyword = search.text.trim();
        final result = await TripDiscoveryService(context.read<AppSession>().api)
            .discover(
              keyword: tabIndex == 2 ? keyword : null,
              startCity: tabIndex == 1 && keyword.isNotEmpty
                  ? keyword
                  : filter.startCity,
              destination: tabIndex == 0 && keyword.isNotEmpty
                  ? keyword
                  : filter.destination,
              sort: sort,
              latitude: point.latitude,
              longitude: point.longitude,
              departureDateFrom: filter.departureFrom,
              departureDateTo: filter.departureTo,
              vehicleType: filter.vehicleType,
              minimumRemainingSeats: filter.minimumRemainingSeats,
              page: targetPage,
              size: 8,
            );
        if (!mounted || serial != requestSerial) return;
        final values =
            (result['records'] as List? ?? result['items'] as List? ?? const [])
                .map(
                  (e) => TripDiscoverModel.fromJson(
                    Map<String, dynamic>.from(e as Map),
                  ),
                )
                .toList();
        final total = (result['total'] as num?)?.toInt() ?? values.length;
        setState(() {
          trips = reset ? values : [...trips, ...values];
          users = const [];
          page = targetPage;
          hasMore = targetPage * 8 < total;
        });
      }
      error = null;
    } catch (e) {
      if (!mounted || serial != requestSerial) return;
      setState(() => error = e.toString());
    } finally {
      if (mounted && serial == requestSerial) {
        setState(() {
          loading = false;
          loadingMore = false;
        });
      }
    }
  }

  Future<void> _openFilter() async {
    final next = await TripDiscoveryFilterSheet.show(context, filter);
    if (next == null || !mounted) return;
    setState(() => filter = next);
    await _load(reset: true);
  }

  Future<void> _toggleFollow(int index) async {
    final row = users[index];
    final id = row['userId']?.toString() ?? '';
    if (id.isEmpty) return;
    try {
      final service = FollowService(context.read<AppSession>().api);
      final value = row['following'] == true
          ? await service.unfollow(id)
          : await service.follow(id);
      if (!mounted) return;
      setState(() => users[index] = {...row, ...value});
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  Future<void> _startChat(String userId) async {
    final conversation = await startPrivateChatFlow(context, userId);
    if (conversation == null || !mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ChatSessionPage(conversation: conversation),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      _SearchHeader(
        controller: search,
        placeholder: placeholder,
        onChanged: _searchChanged,
        onClear: () {
          search.clear();
          _searchChanged('');
        },
      ),
      Container(
        color: Colors.white,
        child: Row(
          children: List.generate(
            tabs.length,
            (index) => Expanded(
              child: _SearchTab(
                label: tabs[index],
                active: tabIndex == index,
                onTap: () {
                  if (tabIndex == index) return;
                  setState(() => tabIndex = index);
                  _load(reset: true);
                },
              ),
            ),
          ),
        ),
      ),
      if (!showingUsers)
        _FilterStrip(
          sort: sort,
          filterActive: filter.active,
          onSort: (value) {
            setState(() => sort = value);
            _load(reset: true);
          },
          onFilter: _openFilter,
        ),
      Expanded(child: _body()),
    ],
  );

  Widget _body() {
    if (loading && trips.isEmpty && users.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (error != null && trips.isEmpty && users.isEmpty) {
      return _DiscoveryState(
        icon: LucideIcons.wifiOff,
        title: '加载失败',
        subtitle: '请检查网络后重试',
        button: '重新加载',
        onTap: () => _load(reset: true),
      );
    }
    if (showingUsers) {
      if (users.isEmpty) {
        return _DiscoveryState(
          icon: LucideIcons.userSearch,
          title: '没有找到相关发起人',
          subtitle: '换一个昵称或用户 ID 试试',
          button: '清空搜索',
          onTap: () {
            search.clear();
            _load(reset: true);
          },
        );
      }
      return RefreshIndicator(
        onRefresh: () => _load(reset: true),
        child: ListView.separated(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 24),
          itemCount: users.length,
          separatorBuilder: (_, _) => const SizedBox(height: 8),
          itemBuilder: (_, index) => _UserResultCard(
            data: users[index],
            onOpen: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => PublicProfilePage(
                  userId: users[index]['userId']?.toString() ?? '',
                ),
              ),
            ),
            onFollow: () => _toggleFollow(index),
            onChat: () => _startChat(users[index]['userId']?.toString() ?? ''),
          ),
        ),
      );
    }
    if (trips.isEmpty) {
      return _DiscoveryState(
        icon: LucideIcons.routeOff,
        title: '没有找到合适的公开行程',
        subtitle: '试试更换关键词或放宽筛选条件',
        button: '调整筛选',
        onTap: _openFilter,
      );
    }
    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      child: ListView.separated(
        controller: scroll,
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
        itemCount: trips.length + (hasMore || loadingMore ? 1 : 0),
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (_, index) {
          if (index == trips.length) {
            return const Padding(
              padding: EdgeInsets.all(14),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          final trip = trips[index];
          return TripDiscoveryCard(
            trip: trip,
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => TripDiscoveryDetailPage(
                  tripId: trip.tripId,
                  initial: trip,
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _SearchHeader extends StatelessWidget {
  const _SearchHeader({
    required this.controller,
    required this.placeholder,
    required this.onChanged,
    required this.onClear,
  });
  final TextEditingController controller;
  final String placeholder;
  final ValueChanged<String> onChanged;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) => Container(
    color: Colors.white,
    padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
    child: SizedBox(
      height: 40,
      child: TextField(
        controller: controller,
        onChanged: onChanged,
        onSubmitted: onChanged,
        textInputAction: TextInputAction.search,
        style: const TextStyle(fontSize: 13.5),
        decoration: InputDecoration(
          hintText: placeholder,
          prefixIcon: const Icon(LucideIcons.search, size: 18),
          suffixIcon: controller.text.isEmpty
              ? null
              : IconButton(
                  onPressed: onClear,
                  icon: const Icon(LucideIcons.x, size: 17),
                ),
          filled: true,
          fillColor: const Color(0xFFF3F6FA),
          contentPadding: EdgeInsets.zero,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide.none,
          ),
        ),
      ),
    ),
  );
}

class _SearchTab extends StatelessWidget {
  const _SearchTab({required this.label, required this.active, required this.onTap});
  final String label;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: SizedBox(
      height: 43,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 14,
              color: active ? AppColors.primary : AppColors.secondaryText,
              fontWeight: active ? FontWeight.w900 : FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          Container(
            width: 24,
            height: 3,
            decoration: BoxDecoration(
              color: active ? AppColors.primary : Colors.transparent,
              borderRadius: BorderRadius.circular(99),
            ),
          ),
        ],
      ),
    ),
  );
}

class _FilterStrip extends StatelessWidget {
  const _FilterStrip({
    required this.sort,
    required this.filterActive,
    required this.onSort,
    required this.onFilter,
  });
  final String sort;
  final bool filterActive;
  final ValueChanged<String> onSort;
  final VoidCallback onFilter;

  @override
  Widget build(BuildContext context) => Container(
    height: 43,
    color: const Color(0xFFF8FAFC),
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
    child: ListView(
      scrollDirection: Axis.horizontal,
      children: [
        _FilterChipButton('RECOMMENDED', '综合排序', sort, onSort),
        _FilterChipButton('DEPARTURE_TIME', '时间', sort, onSort),
        _FilterChipButton('NEARBY', '距离', sort, onSort),
        _ActionChipButton(
          label: filterActive ? '筛选已启用' : '筛选',
          icon: LucideIcons.listFilter,
          active: filterActive,
          onTap: onFilter,
        ),
      ],
    ),
  );
}

class _FilterChipButton extends StatelessWidget {
  const _FilterChipButton(this.value, this.label, this.selected, this.onTap);
  final String value;
  final String label;
  final String selected;
  final ValueChanged<String> onTap;

  @override
  Widget build(BuildContext context) {
    final active = value == selected;
    return Padding(
      padding: const EdgeInsets.only(right: 7),
      child: InkWell(
        onTap: () => onTap(value),
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: active ? AppColors.primarySoft : Colors.white,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: active ? AppColors.primary : AppColors.secondaryText,
              fontWeight: active ? FontWeight.w800 : FontWeight.w600,
            ),
          ),
        ),
      ),
    );
  }
}

class _ActionChipButton extends StatelessWidget {
  const _ActionChipButton({
    required this.label,
    required this.icon,
    required this.active,
    required this.onTap,
  });
  final String label;
  final IconData icon;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(8),
    child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 11),
      decoration: BoxDecoration(
        color: active ? AppColors.primarySoft : Colors.white,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          Icon(icon, size: 14, color: active ? AppColors.primary : AppColors.muted),
          const SizedBox(width: 5),
          Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
        ],
      ),
    ),
  );
}

class _UserResultCard extends StatelessWidget {
  const _UserResultCard({
    required this.data,
    required this.onOpen,
    required this.onFollow,
    required this.onChat,
  });
  final Map<String, dynamic> data;
  final VoidCallback onOpen;
  final VoidCallback onFollow;
  final VoidCallback onChat;

  String get followText {
    if (data['mutual'] == true) return '互相关注';
    if (data['following'] == true) return '已关注';
    if (data['followedByTarget'] == true) return '回关';
    return '关注';
  }

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(12),
    child: InkWell(
      onTap: onOpen,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            UserAvatar(
              nickname: data['nickname']?.toString() ?? '同路行用户',
              avatarImageKey: data['avatarImageKey']?.toString() ?? '',
              radius: 23,
            ),
            const SizedBox(width: 11),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          data['nickname']?.toString().trim().isNotEmpty == true
                              ? data['nickname'].toString()
                              : '同路行用户',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w900),
                        ),
                      ),
                      if (data['certificationStatus'] == 'APPROVED')
                        const Icon(LucideIcons.badgeCheck, size: 16, color: AppColors.primary),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    data['bio']?.toString().trim().isNotEmpty == true
                        ? data['bio'].toString()
                        : '愿每一次出发都有同路人',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 12, color: AppColors.muted),
                  ),
                  const SizedBox(height: 7),
                  Text(
                    '${data['cityName']?.toString().isNotEmpty == true ? data['cityName'] : '未填写常驻地'} · '
                    '${data['totalTripCount'] ?? 0} 次行程 · ${data['followerCount'] ?? 0} 粉丝',
                    style: const TextStyle(fontSize: 11.5, color: AppColors.secondaryText),
                  ),
                  const SizedBox(height: 9),
                  Row(
                    children: [
                      SizedBox(
                        height: 32,
                        child: data['following'] == true
                            ? OutlinedButton(onPressed: onFollow, child: Text(followText))
                            : FilledButton(onPressed: onFollow, child: Text(followText)),
                      ),
                      const SizedBox(width: 8),
                      SizedBox(
                        height: 32,
                        child: OutlinedButton.icon(
                          onPressed: onChat,
                          icon: const Icon(LucideIcons.messageCircle, size: 15),
                          label: const Text('发起私聊'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _DiscoveryState extends StatelessWidget {
  const _DiscoveryState({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.button,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  final String button;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 42, color: AppColors.muted),
          const SizedBox(height: 10),
          Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
          const SizedBox(height: 5),
          Text(subtitle, textAlign: TextAlign.center, style: const TextStyle(color: AppColors.muted)),
          const SizedBox(height: 13),
          OutlinedButton(onPressed: onTap, child: Text(button)),
        ],
      ),
    ),
  );
}
