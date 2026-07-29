import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/location_snapshot.dart';
import 'trip_discovery_detail_page.dart';
import 'trip_discovery_filter_sheet.dart';
import 'trip_discovery_widgets.dart';
import 'trip_search_results_page.dart';

class TripDiscoveryPage extends StatefulWidget {
  const TripDiscoveryPage({super.key});

  @override
  State<TripDiscoveryPage> createState() => _TripDiscoveryPageState();
}

class _TripDiscoveryPageState extends State<TripDiscoveryPage> {
  static const _sortTabs = <({String label, String value})>[
    (label: '顺路优先', value: 'ROUTE_MATCH'),
    (label: '附近优先', value: 'NEARBY'),
    (label: '时间优先', value: 'DEPARTURE_TIME'),
  ];

  final search = TextEditingController();
  final searchFocus = FocusNode();
  final scroll = ScrollController();

  String sort = 'ROUTE_MATCH';
  TripDiscoveryFilter filter = const TripDiscoveryFilter();
  List<TripDiscoverModel> trips = const [];
  bool loading = true;
  bool loadingMore = false;
  bool hasMore = true;
  int page = 1;
  int requestSerial = 0;
  int refreshSeed = DateTime.now().microsecondsSinceEpoch;
  String? error;

  @override
  void initState() {
    super.initState();
    searchFocus.addListener(_refreshSearchPanel);
    scroll.addListener(() {
      if (scroll.position.extentAfter < 360) {
        _load(reset: false);
      }
    });
    WidgetsBinding.instance.addPostFrameCallback((_) => _load(reset: true));
  }

  @override
  void dispose() {
    search.dispose();
    searchFocus
      ..removeListener(_refreshSearchPanel)
      ..dispose();
    scroll.dispose();
    super.dispose();
  }

  void _refreshSearchPanel() {
    if (mounted) setState(() {});
  }

  Future<void> _load({required bool reset}) async {
    if (reset) {
      refreshSeed++;
      setState(() {
        loading = true;
        error = null;
      });
    } else {
      if (loading || loadingMore || !hasMore) return;
      setState(() => loadingMore = true);
    }
    final serial = ++requestSerial;
    final targetPage = reset ? 1 : page + 1;
    try {
      final point = LocationSnapshot.current ?? LocationSnapshot.demoFallback;
      final result = await TripDiscoveryService(context.read<AppSession>().api)
          .discover(
            sort: sort,
            latitude: point.latitude,
            longitude: point.longitude,
            startCity: filter.startCity,
            destination: filter.destination,
            departureDateFrom: filter.departureFrom,
            departureDateTo: filter.departureTo,
            vehicleType: filter.vehicleType,
            minimumRemainingSeats: filter.minimumRemainingSeats,
            page: targetPage,
            size: 8,
            refreshSeed: refreshSeed,
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
        page = targetPage;
        hasMore = targetPage * 8 < total;
        error = null;
      });
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
    searchFocus.unfocus();
    final next = await TripDiscoveryFilterSheet.show(context, filter);
    if (next == null || !mounted) return;
    setState(() => filter = next);
    await _load(reset: true);
  }

  Future<void> _openSearch({String? keyword, int initialTab = 0}) async {
    final value = (keyword ?? search.text).trim();
    searchFocus.unfocus();
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TripSearchResultsPage(
          initialKeyword: value,
          initialTab: initialTab,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Stack(
    clipBehavior: Clip.none,
    children: [
      Column(
        children: [
          _DiscoverySearchEntry(
            controller: search,
            focusNode: searchFocus,
            focused: searchFocus.hasFocus,
            onChanged: (_) => setState(() {}),
            onSearch: () => _openSearch(),
            onClear: () {
              search.clear();
              setState(() {});
            },
          ),
          _MainSortBar(
            items: _sortTabs,
            selected: sort,
            filterActive: filter.active,
            onSort: (value) {
              if (value == sort) return;
              setState(() => sort = value);
              _load(reset: true);
            },
            onFilter: _openFilter,
          ),
          Expanded(child: _body()),
        ],
      ),
      if (searchFocus.hasFocus) ...[
        Positioned.fill(
          top: 59,
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            onTap: searchFocus.unfocus,
            child: const SizedBox.expand(),
          ),
        ),
        Positioned(
          left: 0,
          right: 0,
          top: 59,
          child: Material(
            color: Colors.transparent,
            elevation: 10,
            shadowColor: const Color(0x240F172A),
            child: _SearchSuggestionPanel(
              keyword: search.text.trim(),
              onSelected: (keyword, tab) =>
                  _openSearch(keyword: keyword, initialTab: tab),
            ),
          ),
        ),
      ],
    ],
  );

  Widget _body() {
    if (loading && trips.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (error != null && trips.isEmpty) {
      return _DiscoveryState(
        icon: LucideIcons.wifiOff,
        title: '加载失败',
        subtitle: '请检查网络后重试',
        button: '重新加载',
        onTap: () => _load(reset: true),
      );
    }
    if (trips.isEmpty) {
      return _DiscoveryState(
        icon: LucideIcons.routeOff,
        title: '暂时没有合适的公开行程',
        subtitle: '可以调整筛选，或搜索指定路线',
        button: '去搜索',
        onTap: () => _openSearch(),
      );
    }
    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      color: AppColors.primary,
      backgroundColor: Colors.white,
      child: ListView.separated(
        controller: scroll,
        physics: const AlwaysScrollableScrollPhysics(),
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
                builder: (_) =>
                    TripDiscoveryDetailPage(tripId: trip.tripId, initial: trip),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _DiscoverySearchEntry extends StatelessWidget {
  const _DiscoverySearchEntry({
    required this.controller,
    required this.focusNode,
    required this.focused,
    required this.onChanged,
    required this.onSearch,
    required this.onClear,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final bool focused;
  final ValueChanged<String> onChanged;
  final VoidCallback onSearch;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) => Container(
    color: Colors.white,
    padding: const EdgeInsets.fromLTRB(12, 8, 12, 9),
    child: GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: focusNode.requestFocus,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        height: 42,
        decoration: BoxDecoration(
          color: focused ? Colors.white : const Color(0xFFF3F6FA),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: focused ? AppColors.primary : Colors.transparent,
            width: focused ? 1.4 : 1,
          ),
          boxShadow: focused
              ? const [
                  BoxShadow(
                    color: Color(0x241A73E8),
                    blurRadius: 10,
                    offset: Offset(0, 3),
                  ),
                ]
              : const [],
        ),
        child: Row(
          children: [
            const SizedBox(width: 8),
            Icon(
              LucideIcons.search,
              size: 18,
              color: focused ? AppColors.primary : AppColors.muted,
            ),
            const SizedBox(width: 2),
            Expanded(
              child: TextField(
                controller: controller,
                focusNode: focusNode,
                onChanged: onChanged,
                onSubmitted: (_) => onSearch(),
                textInputAction: TextInputAction.search,
                style: const TextStyle(fontSize: 13.5),
                decoration: const InputDecoration(
                  hintText: '搜索目的地、起点、路线或同路人',
                  border: InputBorder.none,
                  enabledBorder: InputBorder.none,
                  focusedBorder: InputBorder.none,
                  isCollapsed: true,
                  contentPadding: EdgeInsets.zero,
                  hintStyle: TextStyle(color: AppColors.muted),
                ),
              ),
            ),
            if (controller.text.isNotEmpty)
              IconButton(
                onPressed: onClear,
                visualDensity: VisualDensity.compact,
                icon: const Icon(LucideIcons.circleX, size: 17),
              ),
            Container(
              width: 1,
              height: 20,
              color: focused
                  ? const Color(0xFFBFD8FA)
                  : const Color(0xFFDCE3EC),
            ),
            TextButton(
              onPressed: onSearch,
              style: TextButton.styleFrom(
                foregroundColor: AppColors.primary,
                padding: const EdgeInsets.symmetric(horizontal: 13),
                minimumSize: const Size(0, 42),
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                shape: const RoundedRectangleBorder(
                  borderRadius: BorderRadius.horizontal(
                    right: Radius.circular(11),
                  ),
                ),
              ),
              child: const Text(
                '搜索',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w800),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _SearchSuggestionPanel extends StatelessWidget {
  const _SearchSuggestionPanel({
    required this.keyword,
    required this.onSelected,
  });

  final String keyword;
  final void Function(String keyword, int tab) onSelected;

  @override
  Widget build(BuildContext context) {
    final suggestions = keyword.isEmpty
        ? const <({String label, String keyword, int tab, IconData icon})>[
            (label: '深圳周末自驾', keyword: '深圳', tab: 0, icon: LucideIcons.flame),
            (label: '广州出发', keyword: '广州', tab: 1, icon: LucideIcons.mapPin),
            (label: '沿海路线', keyword: '沿海', tab: 2, icon: LucideIcons.route),
          ]
        : <({String label, String keyword, int tab, IconData icon})>[
            (
              label: '搜索“$keyword”相关目的地',
              keyword: keyword,
              tab: 0,
              icon: LucideIcons.mapPinned,
            ),
            (
              label: '搜索“$keyword”相关起点',
              keyword: keyword,
              tab: 1,
              icon: LucideIcons.navigation,
            ),
            (
              label: '搜索“$keyword”相关路线',
              keyword: keyword,
              tab: 2,
              icon: LucideIcons.route,
            ),
            (
              label: '搜索同路人“$keyword”',
              keyword: keyword,
              tab: 3,
              icon: LucideIcons.userSearch,
            ),
            (
              label: '按完整行程号搜索“$keyword”',
              keyword: keyword,
              tab: 4,
              icon: LucideIcons.hash,
            ),
          ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 2, 12, 8),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFFE4EAF1)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x100F172A),
              blurRadius: 12,
              offset: Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 10, 12, 5),
              child: Row(
                children: [
                  Text(
                    keyword.isEmpty ? '热门搜索' : '搜索提示',
                    style: const TextStyle(
                      color: AppColors.secondaryText,
                      fontSize: 11.5,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
            ...suggestions.map(
              (item) => InkWell(
                onTap: () => onSelected(item.keyword, item.tab),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 9,
                  ),
                  child: Row(
                    children: [
                      Icon(item.icon, size: 16, color: AppColors.primary),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          item.label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 13),
                        ),
                      ),
                      const Icon(
                        LucideIcons.chevronRight,
                        size: 16,
                        color: AppColors.muted,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MainSortBar extends StatelessWidget {
  const _MainSortBar({
    required this.items,
    required this.selected,
    required this.filterActive,
    required this.onSort,
    required this.onFilter,
  });

  final List<({String label, String value})> items;
  final String selected;
  final bool filterActive;
  final ValueChanged<String> onSort;
  final VoidCallback onFilter;

  @override
  Widget build(BuildContext context) => Container(
    height: 46,
    decoration: const BoxDecoration(
      color: Colors.white,
      border: Border(bottom: BorderSide(color: Color(0xFFEAEFF5))),
    ),
    child: Row(
      children: [
        ...items.map(
          (item) => Expanded(
            child: InkWell(
              onTap: () => onSort(item.value),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Text(
                    item.label,
                    style: TextStyle(
                      color: selected == item.value
                          ? AppColors.primary
                          : AppColors.secondaryText,
                      fontSize: 13.5,
                      fontWeight: selected == item.value
                          ? FontWeight.w900
                          : FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    width: 24,
                    height: 3,
                    decoration: BoxDecoration(
                      color: selected == item.value
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
        InkWell(
          onTap: onFilter,
          child: SizedBox(
            width: 48,
            child: Stack(
              alignment: Alignment.center,
              children: [
                Icon(
                  LucideIcons.listFilter,
                  size: 20,
                  color: filterActive ? AppColors.primary : AppColors.muted,
                ),
                if (filterActive)
                  const Positioned(
                    right: 10,
                    top: 10,
                    child: CircleAvatar(
                      radius: 3.5,
                      backgroundColor: AppColors.primary,
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
          const SizedBox(height: 12),
          Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
          const SizedBox(height: 5),
          Text(
            subtitle,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.muted, fontSize: 12),
          ),
          const SizedBox(height: 14),
          FilledButton(onPressed: onTap, child: Text(button)),
        ],
      ),
    ),
  );
}
