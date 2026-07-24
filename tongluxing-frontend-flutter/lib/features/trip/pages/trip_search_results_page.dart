import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
import 'trip_discovery_widgets.dart' show formatDiscoverDate;

class TripSearchResultsPage extends StatefulWidget {
  const TripSearchResultsPage({
    this.initialKeyword = '',
    this.initialTab = 0,
    super.key,
  });

  final String initialKeyword;
  final int initialTab;

  @override
  State<TripSearchResultsPage> createState() =>
      _TripSearchResultsPageState();
}

class _TripSearchResultsPageState extends State<TripSearchResultsPage> {
  static const tabs = ['目的地', '起点', '路线', '发起人'];

  final search = TextEditingController();
  final scroll = ScrollController();
  final searchFocus = FocusNode();

  late int tabIndex;
  String submittedKeyword = '';
  String sort = 'RECOMMENDED';
  TripDiscoveryFilter filter = const TripDiscoveryFilter();
  List<TripDiscoverModel> trips = const [];
  List<Map<String, dynamic>> users = const [];
  bool searched = false;
  bool loading = false;
  bool loadingMore = false;
  bool hasMore = false;
  int page = 1;
  int requestSerial = 0;
  String? error;

  bool get showingUsers => tabIndex == 3;

  String get placeholder => switch (tabIndex) {
    0 => '搜索目的地 / 城市 / 景点',
    1 => '搜索出发地 / 集合点',
    2 => '搜索路线 / 经停点 / 行程标题',
    _ => '搜索昵称 / 用户 ID',
  };

  String? get searchType => switch (tabIndex) {
    0 => 'DESTINATION',
    1 => 'ORIGIN',
    2 => 'ROUTE',
    _ => null,
  };

  @override
  void initState() {
    super.initState();
    tabIndex = widget.initialTab.clamp(0, tabs.length - 1).toInt();
    search.text = widget.initialKeyword.trim();
    scroll.addListener(() {
      if (!showingUsers && scroll.position.extentAfter < 320) {
        _load(reset: false);
      }
    });
    if (search.text.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _submitSearch());
    }
  }

  @override
  void dispose() {
    search.dispose();
    scroll.dispose();
    searchFocus.dispose();
    super.dispose();
  }

  Future<void> _submitSearch() async {
    final keyword = search.text.trim();
    searchFocus.unfocus();
    if (keyword.isEmpty) {
      setState(() {
        submittedKeyword = '';
        searched = false;
        trips = const [];
        users = const [];
        error = null;
      });
      return;
    }
    submittedKeyword = keyword;
    searched = true;
    await _load(reset: true);
  }

  Future<void> _load({required bool reset}) async {
    if (!searched || submittedKeyword.isEmpty) return;
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
            .search(keyword: submittedKeyword, page: 1, size: 40);
        if (!mounted || serial != requestSerial) return;
        setState(() {
          users = result;
          trips = const [];
          page = 1;
          hasMore = false;
          error = null;
        });
      } else {
        final point = LocationSnapshot.current ?? LocationSnapshot.demoFallback;
        final result = await TripDiscoveryService(context.read<AppSession>().api)
            .discover(
              keyword: submittedKeyword,
              searchType: searchType,
              sort: sort,
              latitude: point.latitude,
              longitude: point.longitude,
              departureDateFrom: filter.departureFrom,
              departureDateTo: filter.departureTo,
              vehicleType: filter.vehicleType,
              minimumRemainingSeats: filter.minimumRemainingSeats,
              page: targetPage,
              size: 10,
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
          hasMore = targetPage * 10 < total;
          error = null;
        });
      }
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
    if (searched) await _load(reset: true);
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
  Widget build(BuildContext context) => AnnotatedRegion<SystemUiOverlayStyle>(
    value: SystemUiOverlayStyle.dark,
    child: Scaffold(
      backgroundColor: showingUsers
          ? Colors.white
          : TripDiscoveryColors.pageBackground,
      body: SafeArea(
        child: Column(
          children: [
            _SearchTopBar(
              controller: search,
              focusNode: searchFocus,
              placeholder: placeholder,
              onChanged: (_) => setState(() {}),
              onClear: () {
                search.clear();
                setState(() {
                  submittedKeyword = '';
                  searched = false;
                  trips = const [];
                  users = const [];
                  error = null;
                });
              },
              onBack: () => Navigator.pop(context),
              onSearch: _submitSearch,
            ),
            _SearchTabs(
              labels: tabs,
              activeIndex: tabIndex,
              onTap: (index) {
                if (tabIndex == index) return;
                setState(() {
                  tabIndex = index;
                  trips = const [];
                  users = const [];
                  error = null;
                });
                if (searched) _load(reset: true);
              },
            ),
            if (!showingUsers && searched)
              _ResultFilterBar(
                sort: sort,
                filterActive: filter.active,
                onSort: (value) {
                  if (sort == value) return;
                  setState(() => sort = value);
                  _load(reset: true);
                },
                onFilter: _openFilter,
              ),
            Expanded(child: _body()),
          ],
        ),
      ),
    ),
  );

  Widget _body() {
    if (!searched) {
      return _SearchPrompt(
        icon: LucideIcons.search,
        title: '输入关键词后点击搜索',
        subtitle: '可分别查找目的地、起点、路线和发起人',
      );
    }
    if (loading && trips.isEmpty && users.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (error != null && trips.isEmpty && users.isEmpty) {
      return _SearchPrompt(
        icon: LucideIcons.wifiOff,
        title: '搜索失败',
        subtitle: '请检查网络后重新搜索',
        actionLabel: '重新搜索',
        onAction: _submitSearch,
      );
    }
    if (showingUsers) {
      if (users.isEmpty) {
        return _SearchPrompt(
          icon: LucideIcons.userSearch,
          title: '没有找到相关发起人',
          subtitle: '换一个昵称或用户 ID 试试',
        );
      }
      return RefreshIndicator(
        onRefresh: () => _load(reset: true),
        child: ListView.separated(
          padding: const EdgeInsets.fromLTRB(16, 5, 12, 24),
          itemCount: users.length,
          separatorBuilder: (_, _) => const Divider(
            height: 1,
            indent: 68,
            color: Color(0xFFEFF2F6),
          ),
          itemBuilder: (_, index) => _SearchUserRow(
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
      return _SearchPrompt(
        icon: LucideIcons.routeOff,
        title: '没有找到相关行程',
        subtitle: '换一个关键词，或放宽筛选条件',
        actionLabel: '调整筛选',
        onAction: _openFilter,
      );
    }
    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      child: ListView.separated(
        controller: scroll,
        padding: const EdgeInsets.fromLTRB(12, 9, 12, 24),
        itemCount: trips.length + (hasMore || loadingMore ? 1 : 0),
        separatorBuilder: (_, _) => const SizedBox(height: 9),
        itemBuilder: (_, index) {
          if (index == trips.length) {
            return const Padding(
              padding: EdgeInsets.all(14),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          final trip = trips[index];
          return _SearchTripCard(
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

class _SearchTopBar extends StatelessWidget {
  const _SearchTopBar({
    required this.controller,
    required this.focusNode,
    required this.placeholder,
    required this.onChanged,
    required this.onClear,
    required this.onBack,
    required this.onSearch,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final String placeholder;
  final ValueChanged<String> onChanged;
  final VoidCallback onClear;
  final VoidCallback onBack;
  final VoidCallback onSearch;

  @override
  Widget build(BuildContext context) => Container(
    color: Colors.white,
    padding: const EdgeInsets.fromLTRB(6, 8, 12, 7),
    child: Row(
      children: [
        IconButton(
          onPressed: onBack,
          visualDensity: VisualDensity.compact,
          icon: const Icon(LucideIcons.chevronLeft, size: 27),
        ),
        Expanded(
          child: Container(
            height: 43,
            decoration: BoxDecoration(
              color: const Color(0xFFF3F5F8),
              borderRadius: BorderRadius.circular(11),
            ),
            child: Row(
              children: [
                const SizedBox(width: 12),
                const Icon(
                  LucideIcons.search,
                  size: 17,
                  color: AppColors.muted,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: controller,
                    focusNode: focusNode,
                    autofocus: controller.text.isEmpty,
                    onChanged: onChanged,
                    onSubmitted: (_) => onSearch(),
                    textInputAction: TextInputAction.search,
                    style: const TextStyle(fontSize: 14),
                    decoration: InputDecoration(
                      hintText: placeholder,
                      isCollapsed: true,
                      border: InputBorder.none,
                      hintStyle: const TextStyle(color: AppColors.muted),
                    ),
                  ),
                ),
                if (controller.text.isNotEmpty)
                  IconButton(
                    onPressed: onClear,
                    visualDensity: VisualDensity.compact,
                    icon: const Icon(LucideIcons.circleX, size: 17),
                  ),
                Container(width: 1, height: 21, color: const Color(0xFFD9DEE6)),
                TextButton(
                  onPressed: onSearch,
                  style: TextButton.styleFrom(
                    foregroundColor: AppColors.text,
                    minimumSize: const Size(58, 43),
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  ),
                  child: const Text(
                    '搜索',
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w900),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}

class _SearchTabs extends StatelessWidget {
  const _SearchTabs({
    required this.labels,
    required this.activeIndex,
    required this.onTap,
  });

  final List<String> labels;
  final int activeIndex;
  final ValueChanged<int> onTap;

  @override
  Widget build(BuildContext context) => Container(
    height: 48,
    color: Colors.white,
    decoration: const BoxDecoration(
      border: Border(bottom: BorderSide(color: Color(0xFFEDF0F4))),
    ),
    child: Row(
      children: List.generate(
        labels.length,
        (index) => Expanded(
          child: InkWell(
            onTap: () => onTap(index),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Text(
                  labels[index],
                  style: TextStyle(
                    color: activeIndex == index
                        ? AppColors.text
                        : AppColors.secondaryText,
                    fontSize: 14.5,
                    fontWeight: activeIndex == index
                        ? FontWeight.w900
                        : FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 9),
                Container(
                  width: 25,
                  height: 3,
                  decoration: BoxDecoration(
                    color: activeIndex == index
                        ? AppColors.primary
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}

class _ResultFilterBar extends StatelessWidget {
  const _ResultFilterBar({
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
    height: 44,
    color: Colors.white,
    child: Row(
      children: [
        _FilterItem(
          label: '综合排序',
          active: sort == 'RECOMMENDED',
          onTap: () => onSort('RECOMMENDED'),
        ),
        _FilterItem(
          label: '出发时间',
          active: sort == 'DEPARTURE_TIME',
          onTap: () => onSort('DEPARTURE_TIME'),
        ),
        _FilterItem(
          label: '距离',
          active: sort == 'NEARBY',
          onTap: () => onSort('NEARBY'),
        ),
        Expanded(
          child: InkWell(
            onTap: onFilter,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  filterActive ? '筛选中' : '筛选',
                  style: TextStyle(
                    color: filterActive
                        ? AppColors.primary
                        : AppColors.secondaryText,
                    fontSize: 13,
                    fontWeight: filterActive
                        ? FontWeight.w800
                        : FontWeight.w600,
                  ),
                ),
                const SizedBox(width: 4),
                Icon(
                  LucideIcons.listFilter,
                  size: 15,
                  color: filterActive ? AppColors.primary : AppColors.muted,
                ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}

class _FilterItem extends StatelessWidget {
  const _FilterItem({
    required this.label,
    required this.active,
    required this.onTap,
  });

  final String label;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Expanded(
    child: InkWell(
      onTap: onTap,
      child: Center(
        child: Text(
          label,
          style: TextStyle(
            color: active ? AppColors.primary : AppColors.secondaryText,
            fontSize: 13,
            fontWeight: active ? FontWeight.w800 : FontWeight.w600,
          ),
        ),
      ),
    ),
  );
}

class _SearchTripCard extends StatelessWidget {
  const _SearchTripCard({required this.trip, required this.onTap});

  final TripDiscoverModel trip;
  final VoidCallback onTap;

  String get route => [trip.startName, ...trip.waypoints, trip.endName].join(' → ');

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(12),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(13, 12, 13, 11),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    trip.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.text,
                      fontSize: 17,
                      height: 1.2,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  '顺路 ${trip.matchScore}%',
                  style: const TextStyle(
                    color: AppColors.primary,
                    fontSize: 14.5,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 7),
            Text(
              route,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AppColors.secondaryText,
                fontSize: 12.5,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 9),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                _SearchTag('${formatDiscoverDate(trip.departureTime)}出发'),
                _SearchTag('预计${trip.estimatedDays}天'),
                _SearchTag('余${trip.remainingSeats}位'),
                ...trip.tags.take(2).map((tag) => _SearchTag(tag)),
              ],
            ),
            if (trip.description.trim().isNotEmpty) ...[
              const SizedBox(height: 9),
              Text(
                trip.description,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: AppColors.muted, fontSize: 12),
              ),
            ],
            const SizedBox(height: 10),
            Row(
              children: [
                UserAvatar(
                  nickname: trip.owner.nickname,
                  avatarImageKey: trip.owner.avatarImageKey,
                  radius: 15,
                ),
                const SizedBox(width: 7),
                Expanded(
                  child: Text(
                    '${trip.owner.nickname} · 发起人',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.secondaryText,
                      fontSize: 11.5,
                    ),
                  ),
                ),
                const Icon(LucideIcons.users, size: 13, color: AppColors.muted),
                const SizedBox(width: 4),
                Text(
                  '${trip.memberCount}/${trip.maxMemberCount}人',
                  style: const TextStyle(color: AppColors.muted, fontSize: 11),
                ),
                const SizedBox(width: 9),
                const Icon(
                  LucideIcons.chevronRight,
                  size: 17,
                  color: AppColors.muted,
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

class _SearchTag extends StatelessWidget {
  const _SearchTag(this.label);

  final String label;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
    decoration: BoxDecoration(
      color: const Color(0xFFF2F5F8),
      borderRadius: BorderRadius.circular(4),
    ),
    child: Text(
      label,
      style: const TextStyle(color: AppColors.secondaryText, fontSize: 10.5),
    ),
  );
}

class _SearchUserRow extends StatelessWidget {
  const _SearchUserRow({
    required this.data,
    required this.onOpen,
    required this.onFollow,
    required this.onChat,
  });

  final Map<String, dynamic> data;
  final VoidCallback onOpen;
  final VoidCallback onFollow;
  final VoidCallback onChat;

  String get nickname {
    final value = data['nickname']?.toString().trim() ?? '';
    return value.isEmpty ? '同路行用户' : value;
  }

  String get followText {
    if (data['mutual'] == true) return '互相关注';
    if (data['following'] == true) return '已关注';
    if (data['followedByTarget'] == true) return '回关';
    return '关注';
  }

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onOpen,
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Row(
        children: [
          UserAvatar(
            nickname: nickname,
            avatarImageKey: data['avatarImageKey']?.toString() ?? '',
            radius: 27,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        nickname,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppColors.text,
                          fontSize: 15.5,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                    ),
                    if (data['certificationStatus'] == 'APPROVED') ...[
                      const SizedBox(width: 5),
                      const Icon(
                        LucideIcons.badgeCheck,
                        size: 16,
                        color: AppColors.primary,
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  '${data['followerCount'] ?? 0} 粉丝 · ${data['totalTripCount'] ?? 0} 次行程',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
                const SizedBox(height: 3),
                Text(
                  data['bio']?.toString().trim().isNotEmpty == true
                      ? data['bio'].toString()
                      : '${data['cityName']?.toString().trim().isNotEmpty == true ? data['cityName'] : '未填写常驻地'} · 愿每一次出发都有同路人',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: AppColors.secondaryText,
                    fontSize: 11.5,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          SizedBox(
            width: 34,
            height: 34,
            child: OutlinedButton(
              onPressed: onChat,
              style: OutlinedButton.styleFrom(
                padding: EdgeInsets.zero,
                side: const BorderSide(color: Color(0xFFCBD8E8)),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(9),
                ),
              ),
              child: const Icon(
                LucideIcons.messageCircle,
                size: 16,
                color: AppColors.primary,
              ),
            ),
          ),
          const SizedBox(width: 7),
          SizedBox(
            width: 72,
            height: 34,
            child: data['following'] == true
                ? OutlinedButton(
                    onPressed: onFollow,
                    style: OutlinedButton.styleFrom(
                      padding: EdgeInsets.zero,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(9),
                      ),
                    ),
                    child: Text(followText),
                  )
                : FilledButton(
                    onPressed: onFollow,
                    style: FilledButton.styleFrom(
                      padding: EdgeInsets.zero,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(9),
                      ),
                    ),
                    child: Text(followText),
                  ),
          ),
        ],
      ),
    ),
  );
}

class _SearchPrompt extends StatelessWidget {
  const _SearchPrompt({
    required this.icon,
    required this.title,
    required this.subtitle,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(30),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 58,
            height: 58,
            decoration: const BoxDecoration(
              color: AppColors.primarySoft,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: AppColors.primary, size: 27),
          ),
          const SizedBox(height: 14),
          Text(
            title,
            style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 6),
          Text(
            subtitle,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.muted, fontSize: 12),
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 15),
            FilledButton(onPressed: onAction, child: Text(actionLabel!)),
          ],
        ],
      ),
    ),
  );
}
