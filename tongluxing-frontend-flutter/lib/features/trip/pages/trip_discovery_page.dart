import 'dart:async';

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

class TripDiscoveryPage extends StatefulWidget {
  const TripDiscoveryPage({super.key});
  @override
  State<TripDiscoveryPage> createState() => _TripDiscoveryPageState();
}

class _TripDiscoveryPageState extends State<TripDiscoveryPage>
    with AutomaticKeepAliveClientMixin {
  final search = TextEditingController();
  final scroll = ScrollController();
  Timer? debounce;
  List<TripDiscoverModel> trips = [];
  String sort = 'RECOMMENDED';
  String? referenceTripId;
  TripDiscoveryFilter filter = const TripDiscoveryFilter();
  bool loading = false;
  bool loadingMore = false;
  bool hasMore = true;
  int page = 1;
  String? error;
  int requestSerial = 0;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    scroll.addListener(_onScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) => refresh());
  }

  @override
  void dispose() {
    debounce?.cancel();
    search.dispose();
    scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (scroll.position.extentAfter < 420) loadMore();
  }

  void _onSearch(String _) {
    debounce?.cancel();
    debounce = Timer(const Duration(milliseconds: 420), refresh);
  }

  Future<void> refresh() => _load(reset: true);
  Future<void> loadMore() => _load(reset: false);

  Future<void> _load({required bool reset}) async {
    if (reset) {
      if (loading) return;
      setState(() {
        loading = true;
        error = null;
      });
    } else {
      if (loading || loadingMore || !hasMore) return;
      setState(() => loadingMore = true);
    }
    final currentRequest = ++requestSerial;
    final targetPage = reset ? 1 : page + 1;
    try {
      final point = LocationSnapshot.current ?? LocationSnapshot.demoFallback;
      final data = await TripDiscoveryService(context.read<AppSession>().api)
          .discover(
            keyword: search.text,
            sort: sort,
            latitude: point.latitude,
            longitude: point.longitude,
            referenceTripId: referenceTripId,
            startCity: filter.startCity,
            destination: filter.destination,
            departureDateFrom: filter.departureFrom,
            departureDateTo: filter.departureTo,
            vehicleType: filter.vehicleType,
            minimumRemainingSeats: filter.minimumRemainingSeats,
            page: targetPage,
            size: 8,
          );
      if (!mounted || currentRequest != requestSerial) return;
      final values =
          (data['records'] as List? ?? data['items'] as List? ?? const [])
              .map(
                (e) => TripDiscoverModel.fromJson(
                  Map<String, dynamic>.from(e as Map),
                ),
              )
              .toList();
      setState(() {
        trips = reset ? values : [...trips, ...values];
        page = targetPage;
        final total = (data['total'] as num?)?.toInt() ?? values.length;
        hasMore = data['hasMore'] == true || targetPage * 8 < total;
        error = null;
      });
    } catch (e) {
      if (!mounted || currentRequest != requestSerial) return;
      setState(() => error = e.toString());
    } finally {
      if (mounted && currentRequest == requestSerial) {
        setState(() {
          loading = false;
          loadingMore = false;
        });
      }
    }
  }

  Future<void> changeSort(String value) async {
    if (value == 'ROUTE_MATCH') {
      final mine = await TripService(context.read<AppSession>().api).mine();
      if (!mounted) return;
      final available = mine
          .where(
            (e) => [
              'PUBLISHED',
              'RECRUITING',
              'RUNNING',
              'ONGOING',
            ].contains(e.status),
          )
          .toList();
      if (available.isEmpty) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('你还没有可作为顺路基准的已发布行程')));
        return;
      }
      final selected = await showModalBottomSheet<String>(
        context: context,
        showDragHandle: true,
        builder: (sheetContext) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
            children: [
              const Text(
                '选择一段我的行程',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 12),
              ...available.map(
                (trip) => ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 4),
                  leading: const CircleAvatar(
                    backgroundColor: AppColors.primarySoft,
                    child: Icon(LucideIcons.route, color: AppColors.primary),
                  ),
                  title: Text(
                    trip.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: Text('${trip.startName} → ${trip.endName}'),
                  onTap: () => Navigator.pop(sheetContext, trip.id),
                ),
              ),
            ],
          ),
        ),
      );
      if (selected == null) return;
      referenceTripId = selected;
    } else {
      referenceTripId = null;
    }
    setState(() => sort = value);
    await refresh();
  }

  Future<void> openFilter() async {
    final selected = await TripDiscoveryFilterSheet.show(context, filter);
    if (selected == null || !mounted) return;
    setState(() => filter = selected);
    await refresh();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 0),
          child: TextField(
            controller: search,
            textInputAction: TextInputAction.search,
            onChanged: _onSearch,
            onSubmitted: (_) => refresh(),
            decoration: InputDecoration(
              hintText: '搜索目的地、路线、经停点或发起人',
              prefixIcon: const Icon(LucideIcons.search),
              suffixIcon: search.text.isEmpty
                  ? null
                  : IconButton(
                      onPressed: () {
                        search.clear();
                        setState(() {});
                        refresh();
                      },
                      icon: const Icon(LucideIcons.x),
                    ),
              fillColor: Colors.white,
            ),
          ),
        ),
        SizedBox(
          height: 58,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            children: [
              _SortButton('RECOMMENDED', '推荐', sort, changeSort),
              _SortButton('NEARBY', '附近', sort, changeSort),
              _SortButton('DEPARTURE_TIME', '即将出发', sort, changeSort),
              _SortButton('ROUTE_MATCH', '顺路优先', sort, changeSort),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: ActionChip(
                  avatar: Icon(
                    LucideIcons.listFilter,
                    size: 16,
                    color: filter.active ? Colors.white : AppColors.primaryDark,
                  ),
                  label: const Text('筛选'),
                  labelStyle: TextStyle(
                    color: filter.active ? Colors.white : AppColors.primaryDark,
                    fontWeight: FontWeight.w700,
                  ),
                  backgroundColor: filter.active
                      ? AppColors.primary
                      : Colors.white,
                  onPressed: openFilter,
                ),
              ),
            ],
          ),
        ),
        Expanded(child: _body()),
      ],
    );
  }

  Widget _body() {
    if (loading && trips.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (error != null && trips.isEmpty) {
      return _DiscoveryState(
        icon: LucideIcons.wifiOff,
        title: '暂时无法加载推荐',
        subtitle: '请检查网络后重试',
        button: '重新加载',
        onTap: refresh,
      );
    }
    if (trips.isEmpty) {
      return _DiscoveryState(
        icon: LucideIcons.routeOff,
        title: '没有找到合适的公开行程',
        subtitle: '试试放宽日期、目的地或剩余名额',
        button: '调整筛选',
        onTap: openFilter,
      );
    }
    return RefreshIndicator(
      onRefresh: refresh,
      child: ListView.separated(
        controller: scroll,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 30),
        itemCount: trips.length + 1,
        separatorBuilder: (_, _) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          if (index == trips.length) {
            return Padding(
              padding: const EdgeInsets.all(18),
              child: Center(
                child: loadingMore
                    ? const CircularProgressIndicator()
                    : Text(
                        hasMore ? '继续上滑加载' : '已经看到全部公开行程',
                        style: const TextStyle(color: AppColors.muted),
                      ),
              ),
            );
          }
          final trip = trips[index];
          return TripDiscoveryCard(
            trip: trip,
            onTap: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => TripDiscoveryDetailPage(
                    tripId: trip.tripId,
                    initial: trip,
                  ),
                ),
              );
              if (mounted) refresh();
            },
          );
        },
      ),
    );
  }
}

class _SortButton extends StatelessWidget {
  const _SortButton(this.value, this.label, this.selected, this.onTap);
  final String value;
  final String label;
  final String selected;
  final ValueChanged<String> onTap;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 4),
    child: ChoiceChip(
      label: Text(label),
      selected: selected == value,
      onSelected: (_) => onTap(value),
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
      padding: const EdgeInsets.all(30),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 54, color: AppColors.muted),
          const SizedBox(height: 16),
          Text(
            title,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 7),
          Text(subtitle, style: const TextStyle(color: AppColors.muted)),
          const SizedBox(height: 18),
          OutlinedButton(onPressed: onTap, child: Text(button)),
        ],
      ),
    ),
  );
}
