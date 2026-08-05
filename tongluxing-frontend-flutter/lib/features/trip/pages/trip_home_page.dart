import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import '../../chat/pages/chat_session_page.dart';
import 'trip_create_page.dart';
import 'trip_detail_page.dart';
import 'trip_discovery_detail_page.dart';
import 'trip_discovery_page.dart';
import 'trip_navigation_page.dart';
import 'trip_search_results_page.dart';

class TripHomePage extends StatefulWidget {
  const TripHomePage({super.key});

  @override
  State<TripHomePage> createState() => _TripHomePageState();
}

class _TripHomePageState extends State<TripHomePage> {
  int section = 0;
  int refreshVersion = 0;
  bool loadingDashboard = true;
  String? dashboardError;
  Map<String, dynamic> dashboard = const {};
  List<TripModel> currentTrips = const [];
  Set<String> joinedTripIds = const {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadDashboard());
  }

  Future<void> _loadDashboard() async {
    if (mounted) {
      setState(() {
        loadingDashboard = true;
        dashboardError = null;
      });
    }
    try {
      final value = await TripService(context.read<AppSession>().api).dashboard();
      final trips = <TripModel>[];
      final joinedIds = <String>{};
      for (final key in const [
        'publishedCurrentTrip',
        'joinedCurrentTrip',
        'currentTrip',
      ]) {
        final raw = value[key];
        if (raw is! Map) continue;
        final trip = TripModel.fromJson(Map<String, dynamic>.from(raw));
        if (trip.id.isNotEmpty && !trips.any((item) => item.id == trip.id)) {
          trips.add(trip);
        }
        if (key == 'joinedCurrentTrip' && trip.id.isNotEmpty) {
          joinedIds.add(trip.id);
        }
      }
      if (!mounted) return;
      setState(() {
        dashboard = value;
        currentTrips = trips;
        joinedTripIds = joinedIds;
      });
    } catch (error) {
      if (mounted) setState(() => dashboardError = error.toString());
    } finally {
      if (mounted) setState(() => loadingDashboard = false);
    }
  }

  Future<void> _refreshAll() async {
    setState(() => refreshVersion++);
    await _loadDashboard();
  }

  Future<void> _openCreateTrip() async {
    final created = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const TripCreatePage()),
    );
    if (created == true && mounted) await _refreshAll();
  }

  Future<void> _openSearch() async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => const TripSearchResultsPage(initialKeyword: ''),
      ),
    );
  }

  Future<void> _openTrip(TripModel trip) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TripDetailPage(tripId: trip.id, initial: trip),
      ),
    );
    if (mounted) await _refreshAll();
  }

  Future<void> _openNavigation(TripModel trip) async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => TripNavigationPage(trip: trip)),
    );
    if (mounted) await _refreshAll();
  }

  Future<void> _openConversation(TripModel trip) async {
    try {
      final conversation = await ChatService(
        context.read<AppSession>().api,
      ).tripConversation(trip.id);
      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => ChatSessionPage(conversation: conversation),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }

  void _shareTrip(TripModel trip) {
    Clipboard.setData(
      ClipboardData(text: '${trip.title}：${trip.startName} → ${trip.endName}'),
    );
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('行程信息已复制，可直接分享给好友')),
    );
  }

  @override
  Widget build(BuildContext context) => AnnotatedRegion<SystemUiOverlayStyle>(
    value: const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      statusBarBrightness: Brightness.dark,
    ),
    child: Column(
      children: [
        _TripHeader(onSearch: _openSearch, onCreate: _openCreateTrip),
        Expanded(
          child: NestedScrollView(
            key: ValueKey('trip-home-$refreshVersion'),
            headerSliverBuilder: (context, innerBoxIsScrolled) => [
              SliverToBoxAdapter(
                child: _CurrentTripArea(
                  loading: loadingDashboard,
                  error: dashboardError,
                  trips: currentTrips,
                  joinedTripIds: joinedTripIds,
                  activeCount: (dashboard['activeCount'] as num?)?.toInt() ??
                      currentTrips.length,
                  onRetry: _loadDashboard,
                  onOpen: _openTrip,
                  onNavigate: _openNavigation,
                  onChat: _openConversation,
                  onShare: _shareTrip,
                ),
              ),
              SliverPersistentHeader(
                pinned: true,
                delegate: _TripTabHeaderDelegate(
                  child: _TripPrimaryTabs(
                    section: section,
                    onChanged: (value) => setState(() => section = value),
                  ),
                ),
              ),
            ],
            body: ColoredBox(
              color: AppColors.background,
              child: section == 0
                  ? TripDiscoveryPage(
                      userHasTrip: currentTrips.isNotEmpty,
                      onTripCreated: _refreshAll,
                      onApplicationSubmitted: () {
                        setState(() => refreshVersion++);
                      },
                    )
                  : _MyTripsPage(
                      key: ValueKey('my-trips-$refreshVersion'),
                      currentTrips: currentTrips,
                      joinedTripIds: joinedTripIds,
                      onChanged: _refreshAll,
                    ),
            ),
          ),
        ),
      ],
    ),
  );
}

class _TripHeader extends StatelessWidget {
  const _TripHeader({required this.onSearch, required this.onCreate});

  final VoidCallback onSearch;
  final VoidCallback onCreate;

  @override
  Widget build(BuildContext context) => Container(
    decoration: const BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF1948C8), AppColors.primary],
      ),
    ),
    child: SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(18, 8, 14, 14),
        child: Row(
          children: [
            const Expanded(
              child: Text(
                '行程',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 23,
                  fontWeight: FontWeight.w900,
                  letterSpacing: .2,
                ),
              ),
            ),
            _HeaderIconButton(
              tooltip: '搜索行程',
              icon: LucideIcons.search,
              onTap: onSearch,
            ),
            const SizedBox(width: 8),
            _HeaderIconButton(
              tooltip: '发布新行程',
              icon: LucideIcons.plus,
              onTap: onCreate,
            ),
          ],
        ),
      ),
    ),
  );
}

class _HeaderIconButton extends StatelessWidget {
  const _HeaderIconButton({
    required this.tooltip,
    required this.icon,
    required this.onTap,
  });

  final String tooltip;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Tooltip(
    message: tooltip,
    child: Material(
      color: const Color(0x2BFFFFFF),
      shape: const CircleBorder(),
      child: InkWell(
        onTap: onTap,
        customBorder: const CircleBorder(),
        child: SizedBox(
          width: 42,
          height: 42,
          child: Icon(icon, size: 21, color: Colors.white),
        ),
      ),
    ),
  );
}

class _CurrentTripArea extends StatelessWidget {
  const _CurrentTripArea({
    required this.loading,
    required this.error,
    required this.trips,
    required this.joinedTripIds,
    required this.activeCount,
    required this.onRetry,
    required this.onOpen,
    required this.onNavigate,
    required this.onChat,
    required this.onShare,
  });

  final bool loading;
  final String? error;
  final List<TripModel> trips;
  final Set<String> joinedTripIds;
  final int activeCount;
  final VoidCallback onRetry;
  final ValueChanged<TripModel> onOpen;
  final ValueChanged<TripModel> onNavigate;
  final ValueChanged<TripModel> onChat;
  final ValueChanged<TripModel> onShare;

  @override
  Widget build(BuildContext context) {
    if (loading && trips.isEmpty) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 28),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (trips.isEmpty) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(12, 14, 12, 12),
        child: error == null
            ? const SizedBox.shrink()
            : _InlineNotice(
                icon: LucideIcons.wifiOff,
                title: '当前行程加载失败',
                subtitle: '下拉刷新或点击重试',
                actionLabel: '重试',
                onAction: onRetry,
              ),
      );
    }
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 14, 12, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text(
                '当前行程',
                style: TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
              ),
              const Spacer(),
              Text(
                '查看全部  $activeCount 条',
                style: const TextStyle(
                  color: AppColors.primary,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ...trips.map(
            (trip) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: _CurrentTripCard(
                trip: trip,
                joined: joinedTripIds.contains(trip.id),
                onOpen: () => onOpen(trip),
                onPrimary: _isRunning(trip.status)
                    ? () => onNavigate(trip)
                    : () => onOpen(trip),
                onSecondary: _isRunning(trip.status)
                    ? () => onChat(trip)
                    : () => onShare(trip),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CurrentTripCard extends StatelessWidget {
  const _CurrentTripCard({
    required this.trip,
    required this.joined,
    required this.onOpen,
    required this.onPrimary,
    required this.onSecondary,
  });

  final TripModel trip;
  final bool joined;
  final VoidCallback onOpen;
  final VoidCallback onPrimary;
  final VoidCallback onSecondary;

  @override
  Widget build(BuildContext context) {
    final running = _isRunning(trip.status);
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(22),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onOpen,
        child: Container(
          decoration: BoxDecoration(
            border: Border.all(color: const Color(0xFFE5E7EB)),
            borderRadius: BorderRadius.circular(22),
            boxShadow: const [
              BoxShadow(
                color: Color(0x0E1D3969),
                blurRadius: 17,
                offset: Offset(0, 5),
              ),
            ],
          ),
          child: Stack(
            children: [
              const Positioned(
                left: 0,
                top: 0,
                bottom: 0,
                child: ColoredBox(
                  color: AppColors.primary,
                  child: SizedBox(width: 5),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(17, 14, 14, 14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            trip.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: AppColors.text,
                              fontSize: 16,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                        ),
                        _SoftTag(label: joined ? '我加入的' : '我发布的'),
                      ],
                    ),
                    const SizedBox(height: 7),
                    Row(
                      children: [
                        const Icon(
                          LucideIcons.route,
                          size: 15,
                          color: AppColors.primary,
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            '${trip.startName} → ${trip.endName}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: AppColors.secondaryText,
                              fontSize: 13,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 7),
                    Text(
                      running
                          ? '${_statusLabel(trip.status)} · ${_vehicleLabel(trip)} · 距终点持续更新'
                          : '${_statusLabel(trip.status)} · ${_vehicleLabel(trip)} · ${_dateLabel(trip.departureTime)}出发',
                      style: const TextStyle(
                        color: AppColors.muted,
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 13),
                    Row(
                      children: [
                        Expanded(
                          child: _CompactButton(
                            label: running ? '查看地图' : '管理行程',
                            icon: running ? LucideIcons.map : LucideIcons.settings2,
                            filled: true,
                            onTap: onPrimary,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _CompactButton(
                            label: running ? '进入群聊' : '分享邀请',
                            icon: running
                                ? LucideIcons.messageCircle
                                : LucideIcons.share2,
                            onTap: onSecondary,
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
}

class _TripPrimaryTabs extends StatelessWidget {
  const _TripPrimaryTabs({required this.section, required this.onChanged});

  final int section;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) => Container(
    color: Colors.white,
    padding: const EdgeInsets.symmetric(horizontal: 12),
    child: Row(
      children: [
        _PrimaryTab(
          label: '推荐',
          active: section == 0,
          onTap: () => onChanged(0),
        ),
        _PrimaryTab(
          label: '我的行程',
          active: section == 1,
          onTap: () => onChanged(1),
        ),
      ],
    ),
  );
}

class _PrimaryTab extends StatelessWidget {
  const _PrimaryTab({
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
      child: SizedBox(
        height: 52,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Text(
              label,
              style: TextStyle(
                color: active ? AppColors.primary : AppColors.secondaryText,
                fontSize: 15,
                fontWeight: active ? FontWeight.w900 : FontWeight.w700,
              ),
            ),
            const SizedBox(height: 11),
            AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              width: active ? 32 : 0,
              height: 3,
              decoration: BoxDecoration(
                color: AppColors.primary,
                borderRadius: BorderRadius.circular(99),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _TripTabHeaderDelegate extends SliverPersistentHeaderDelegate {
  const _TripTabHeaderDelegate({required this.child});

  final Widget child;

  @override
  double get minExtent => 52;

  @override
  double get maxExtent => 52;

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlapsContent) =>
      Material(
        elevation: overlapsContent ? 2 : 0,
        shadowColor: const Color(0x16000000),
        child: child,
      );

  @override
  bool shouldRebuild(covariant _TripTabHeaderDelegate oldDelegate) =>
      oldDelegate.child != child;
}

class _MyTripsPage extends StatefulWidget {
  const _MyTripsPage({
    required this.currentTrips,
    required this.joinedTripIds,
    required this.onChanged,
    super.key,
  });

  final List<TripModel> currentTrips;
  final Set<String> joinedTripIds;
  final Future<void> Function() onChanged;

  @override
  State<_MyTripsPage> createState() => _MyTripsPageState();
}

class _MyTripsPageState extends State<_MyTripsPage> {
  int category = 0;
  int favoriteFilter = 0;
  bool loading = true;
  String? error;
  List<TripDraftModel> drafts = const [];
  List<TripModel> active = const [];
  List<TripModel> history = const [];
  List<TripModel> exited = const [];
  List<TripApplicationModel> applications = const [];
  List<TripDiscoverModel> favorites = const [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    if (mounted) {
      setState(() {
        loading = true;
        error = null;
      });
    }
    final api = context.read<AppSession>().api;
    try {
      final values = await Future.wait<dynamic>([
        TripService(api).drafts().catchError((_) => <TripDraftModel>[]),
        TripService(api).mine(scope: 'active').catchError((_) => <TripModel>[]),
        TripService(api).mine(scope: 'history').catchError((_) => <TripModel>[]),
        TripService(api).mine(scope: 'exited').catchError((_) => <TripModel>[]),
        TripDiscoveryService(api)
            .myApplications()
            .catchError((_) => <TripApplicationModel>[]),
        TripDiscoveryService(api).favorites().catchError(
          (_) => <String, dynamic>{'records': <dynamic>[]},
        ),
      ]);
      final favoriteRaw = Map<String, dynamic>.from(values[5] as Map);
      final favoriteRows = (favoriteRaw['records'] ?? favoriteRaw['list']) as List? ??
          const [];
      if (!mounted) return;
      setState(() {
        drafts = List<TripDraftModel>.from(values[0] as List);
        active = _dedupeTrips([
          ...widget.currentTrips,
          ...List<TripModel>.from(values[1] as List),
        ]);
        history = List<TripModel>.from(values[2] as List);
        exited = List<TripModel>.from(values[3] as List);
        applications = List<TripApplicationModel>.from(values[4] as List);
        favorites = favoriteRows
            .whereType<Map>()
            .map((item) => TripDiscoverModel.fromJson(
                  Map<String, dynamic>.from(item),
                ))
            .toList();
      });
    } catch (caught) {
      if (mounted) setState(() => error = caught.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _open(Widget page) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    if (!mounted) return;
    await load();
    await widget.onChanged();
  }

  Future<void> _openChat(TripModel trip) async {
    try {
      final conversation = await ChatService(
        context.read<AppSession>().api,
      ).tripConversation(trip.id);
      if (!mounted) return;
      await _open(ChatSessionPage(conversation: conversation));
    } catch (caught) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(caught.toString())));
    }
  }

  Future<void> _unfavorite(TripDiscoverModel trip) async {
    try {
      await TripDiscoveryService(
        context.read<AppSession>().api,
      ).unfavorite(trip.tripId);
      if (!mounted) return;
      setState(() {
        favorites = favorites.where((item) => item.tripId != trip.tripId).toList();
      });
    } catch (caught) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(caught.toString())));
    }
  }

  @override
  Widget build(BuildContext context) => RefreshIndicator(
    onRefresh: load,
    child: CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        SliverToBoxAdapter(
          child: SizedBox(
            height: 57,
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(12, 11, 12, 8),
              scrollDirection: Axis.horizontal,
              itemCount: _categories.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) => ChoiceChip(
                label: Text(_categories[index]),
                selected: category == index,
                onSelected: (_) => setState(() => category = index),
                showCheckmark: false,
                selectedColor: AppColors.primary,
                backgroundColor: Colors.white,
                side: BorderSide(
                  color: category == index
                      ? AppColors.primary
                      : AppColors.border,
                ),
                labelStyle: TextStyle(
                  color: category == index
                      ? Colors.white
                      : AppColors.secondaryText,
                  fontWeight: FontWeight.w800,
                ),
                shape: const StadiumBorder(),
              ),
            ),
          ),
        ),
        if (loading)
          const SliverFillRemaining(
            hasScrollBody: false,
            child: Center(child: CircularProgressIndicator()),
          )
        else if (error != null)
          SliverFillRemaining(
            hasScrollBody: false,
            child: Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: _InlineNotice(
                  icon: LucideIcons.wifiOff,
                  title: '行程列表加载失败',
                  subtitle: error!,
                  actionLabel: '重试',
                  onAction: load,
                ),
              ),
            ),
          )
        else
          ..._categorySlivers(),
      ],
    ),
  );

  List<Widget> _categorySlivers() {
    switch (category) {
      case 0:
        return _upcomingSlivers();
      case 1:
        return _tripListSlivers(
          title: '进行中',
          subtitle: '地图、群聊和队伍管理入口',
          trips: active.where((trip) => _isRunning(trip.status)).toList(),
          emptyTitle: '暂无进行中的行程',
          emptySubtitle: '行程开始后会在这里持续显示',
          icon: LucideIcons.navigation,
        );
      case 2:
        return _tripListSlivers(
          title: '已完成',
          subtitle: '已完成行程与里程记录',
          trips: history.where((trip) => !_isCancelled(trip.status)).toList(),
          emptyTitle: '还没有完成的行程',
          emptySubtitle: '完成一次旅程后会在这里留下记录',
          icon: LucideIcons.circleCheck,
        );
      case 3:
        final rejected = applications
            .where((item) => item.status == 'REJECTED' || item.status == 'CANCELLED')
            .toList();
        return _cancelledSlivers(rejected);
      default:
        return _favoriteSlivers();
    }
  }

  List<Widget> _upcomingSlivers() {
    final upcoming = active.where((trip) => !_isRunning(trip.status)).toList();
    final pending = applications.where((item) => item.status == 'PENDING').toList();
    final widgets = <Widget>[
      _SectionHeaderSliver(
        title: '草稿',
        suffix: '${drafts.length}条',
        subtitle: '继续编辑或直接发布',
      ),
    ];
    if (drafts.isEmpty) {
      widgets.add(
        _EmptySliver(
          icon: LucideIcons.filePenLine,
          title: '暂无草稿',
          subtitle: '未发布的行程草稿会保存在这里',
        ),
      );
    } else {
      widgets.add(
        SliverPadding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          sliver: SliverList.separated(
            itemCount: drafts.length,
            separatorBuilder: (_, _) => const SizedBox(height: 10),
            itemBuilder: (context, index) {
              final draft = drafts[index];
              return _DraftCard(
                draft: draft,
                onEdit: () => _open(TripCreatePage(draft: draft)),
              );
            },
          ),
        ),
      );
    }
    widgets.add(
      const _SectionHeaderSliver(
        title: '正式行程',
        subtitle: '按出发时间升序',
      ),
    );
    if (upcoming.isEmpty && pending.isEmpty) {
      widgets.add(
        _EmptySliver(
          icon: LucideIcons.calendarPlus,
          title: '暂无待出发行程',
          subtitle: '发布行程或申请加入其他队伍',
          actionLabel: '发布行程',
          onAction: () => _open(const TripCreatePage()),
        ),
      );
    } else {
      widgets.add(
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
          sliver: SliverList.separated(
            itemCount: upcoming.length + pending.length,
            separatorBuilder: (_, _) => const SizedBox(height: 10),
            itemBuilder: (context, index) {
              if (index < upcoming.length) {
                final trip = upcoming[index];
                return _ManagedTripCard(
                  trip: trip,
                  joined: widget.joinedTripIds.contains(trip.id),
                  category: 0,
                  onOpen: () => _open(
                    TripDetailPage(tripId: trip.id, initial: trip),
                  ),
                  onChat: () => _openChat(trip),
                );
              }
              final application = pending[index - upcoming.length];
              return _ApplicationCard(
                application: application,
                onOpen: application.tripId.isEmpty
                    ? null
                    : () => _open(
                          TripDiscoveryDetailPage(tripId: application.tripId),
                        ),
              );
            },
          ),
        ),
      );
    }
    return widgets;
  }

  List<Widget> _tripListSlivers({
    required String title,
    required String subtitle,
    required List<TripModel> trips,
    required String emptyTitle,
    required String emptySubtitle,
    required IconData icon,
  }) => [
    _SectionHeaderSliver(title: title, subtitle: subtitle),
    if (trips.isEmpty)
      _EmptySliver(icon: icon, title: emptyTitle, subtitle: emptySubtitle)
    else
      SliverPadding(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
        sliver: SliverList.separated(
          itemCount: trips.length,
          separatorBuilder: (_, _) => const SizedBox(height: 10),
          itemBuilder: (context, index) {
            final trip = trips[index];
            return _ManagedTripCard(
              trip: trip,
              joined: widget.joinedTripIds.contains(trip.id),
              category: category,
              onOpen: () => _open(
                category == 1
                    ? TripNavigationPage(trip: trip)
                    : TripDetailPage(tripId: trip.id, initial: trip),
              ),
              onChat: () => _openChat(trip),
            );
          },
        ),
      ),
  ];

  List<Widget> _cancelledSlivers(List<TripApplicationModel> rejected) {
    final cancelledTrips = _dedupeTrips([
      ...history.where((trip) => _isCancelled(trip.status)),
      ...exited,
    ]);
    final count = cancelledTrips.length + rejected.length;
    return [
      _SectionHeaderSliver(
        title: '已取消',
        subtitle: '取消发布、退出和被拒记录',
        suffix: '$count条',
      ),
      if (count == 0)
        const _EmptySliver(
          icon: LucideIcons.circleX,
          title: '暂无取消记录',
          subtitle: '取消或退出的行程会保留在这里',
        )
      else
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
          sliver: SliverList.separated(
            itemCount: count,
            separatorBuilder: (_, _) => const SizedBox(height: 10),
            itemBuilder: (context, index) {
              if (index < cancelledTrips.length) {
                final trip = cancelledTrips[index];
                return _ManagedTripCard(
                  trip: trip,
                  joined: widget.joinedTripIds.contains(trip.id) ||
                      exited.any((item) => item.id == trip.id),
                  category: 3,
                  onOpen: () => _open(
                    TripDetailPage(tripId: trip.id, initial: trip),
                  ),
                  onChat: () => _openChat(trip),
                );
              }
              return _ApplicationCard(
                application: rejected[index - cancelledTrips.length],
                onOpen: null,
              );
            },
          ),
        ),
    ];
  }

  List<Widget> _favoriteSlivers() {
    final visibleFavorites = favorites.where((trip) {
      if (favoriteFilter == 1) return trip.hasCaptain;
      if (favoriteFilter == 2) return !trip.hasCaptain || trip.passengerDemand;
      return true;
    }).toList();
    return [
      _SectionHeaderSliver(
        title: '收藏',
        subtitle: '收藏的队伍与目的地',
        suffix: '${visibleFavorites.length}条',
      ),
      SliverToBoxAdapter(
        child: _FavoriteFilterBar(
          selected: favoriteFilter,
          onSelected: (value) => setState(() => favoriteFilter = value),
        ),
      ),
      if (visibleFavorites.isEmpty)
        _EmptySliver(
          icon: LucideIcons.heart,
          title: favoriteFilter == 0 ? '还没有收藏行程' : '该分类暂无收藏',
          subtitle: '在推荐列表中收藏喜欢的队伍或目的地',
        )
      else
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
          sliver: SliverList.separated(
            itemCount: visibleFavorites.length,
            separatorBuilder: (_, _) => const SizedBox(height: 10),
            itemBuilder: (context, index) {
              final trip = visibleFavorites[index];
              return _FavoriteTripCard(
                trip: trip,
                onOpen: () => _open(
                  TripDiscoveryDetailPage(tripId: trip.tripId, initial: trip),
                ),
                onRemove: () => _unfavorite(trip),
              );
            },
          ),
        ),
    ];
  }
}


class _FavoriteFilterBar extends StatelessWidget {
  const _FavoriteFilterBar({
    required this.selected,
    required this.onSelected,
  });

  final int selected;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(12, 0, 12, 11),
    child: Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: const Color(0xFFEFF3F9),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        children: [
          for (var index = 0; index < 3; index++)
            Expanded(
              child: Material(
                color: selected == index ? Colors.white : Colors.transparent,
                borderRadius: BorderRadius.circular(999),
                child: InkWell(
                  onTap: () => onSelected(index),
                  borderRadius: BorderRadius.circular(999),
                  child: SizedBox(
                    height: 34,
                    child: Center(
                      child: Text(
                        const ['全部', '队伍', '目的地'][index],
                        style: TextStyle(
                          color: selected == index
                              ? AppColors.primary
                              : AppColors.secondaryText,
                          fontSize: 12,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    ),
  );
}

class _DraftCard extends StatelessWidget {
  const _DraftCard({required this.draft, required this.onEdit});

  final TripDraftModel draft;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) => _WhiteCard(
    accentColor: const Color(0xFF9B6DFF),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                draft.title.trim().isEmpty
                    ? '${draft.startLocation?.name ?? '未设置起点'}至${draft.destination?.name ?? '未设置终点'}'
                    : draft.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
              ),
            ),
            const _SoftTag(label: '草稿'),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          draft.startTime == null ? '未设定出发时间' : _dateTimeLabel(draft.startTime),
          style: const TextStyle(color: AppColors.secondaryText),
        ),
        const SizedBox(height: 5),
        Text(
          '途经点 ${draft.waypoints.length}个 · 保存于 ${_relativeTime(draft.updatedAt)}',
          style: const TextStyle(color: AppColors.muted, fontSize: 12),
        ),
        const SizedBox(height: 13),
        Row(
          children: [
            Expanded(
              child: _CompactButton(
                label: '编辑草稿',
                icon: LucideIcons.filePenLine,
                onTap: onEdit,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: _CompactButton(
                label: '继续发布',
                icon: LucideIcons.send,
                filled: true,
                onTap: onEdit,
              ),
            ),
          ],
        ),
      ],
    ),
  );
}

class _ManagedTripCard extends StatelessWidget {
  const _ManagedTripCard({
    required this.trip,
    required this.joined,
    required this.category,
    required this.onOpen,
    required this.onChat,
  });

  final TripModel trip;
  final bool joined;
  final int category;
  final VoidCallback onOpen;
  final VoidCallback onChat;

  @override
  Widget build(BuildContext context) {
    return _WhiteCard(
      onTap: onOpen,
      accentColor: _managedTripAccent(category, trip.status),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  trip.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
                ),
              ),
              _SoftTag(label: _statusLabel(trip.status)),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '${trip.startName} → ${trip.endName}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: AppColors.secondaryText,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 7,
            children: [
              _MetaPill(
                icon: LucideIcons.calendarDays,
                label: _dateLabel(trip.departureTime),
              ),
              _MetaPill(
                icon: LucideIcons.carFront,
                label: _vehicleLabel(trip),
              ),
              _MetaPill(
                icon: joined ? LucideIcons.userRoundCheck : LucideIcons.crown,
                label: joined ? '已加入' : '我发布的',
              ),
            ],
          ),
          const SizedBox(height: 13),
          Row(
            children: [
              Expanded(
                child: _CompactButton(
                  label: category == 1
                      ? '查看地图'
                      : category == 2
                          ? '查看记录'
                          : '查看详情',
                  icon: category == 1
                      ? LucideIcons.map
                      : LucideIcons.chevronRight,
                  filled: true,
                  onTap: onOpen,
                ),
              ),
              if (category == 1 || joined) ...[
                const SizedBox(width: 10),
                Expanded(
                  child: _CompactButton(
                    label: '进入群聊',
                    icon: LucideIcons.messageCircle,
                    onTap: onChat,
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _ApplicationCard extends StatelessWidget {
  const _ApplicationCard({required this.application, required this.onOpen});

  final TripApplicationModel application;
  final VoidCallback? onOpen;

  @override
  Widget build(BuildContext context) => _WhiteCard(
    onTap: onOpen,
    accentColor: application.status == 'PENDING'
        ? AppColors.warning
        : const Color(0xFF98A2B3),
    child: Row(
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: const BoxDecoration(
            color: AppColors.primarySoft,
            shape: BoxShape.circle,
          ),
          child: Icon(
            application.status == 'PENDING'
                ? LucideIcons.clock3
                : LucideIcons.circleX,
            color: application.status == 'PENDING'
                ? AppColors.warning
                : AppColors.muted,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                application.conversationName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 4),
              Text(
                application.status == 'PENDING'
                    ? '申请已提交，等待队长审批'
                    : '申请未通过或已取消',
                style: const TextStyle(color: AppColors.muted, fontSize: 12),
              ),
            ],
          ),
        ),
        _SoftTag(
          label: application.status == 'PENDING' ? '待审批' : '已结束',
        ),
      ],
    ),
  );
}

class _FavoriteTripCard extends StatelessWidget {
  const _FavoriteTripCard({
    required this.trip,
    required this.onOpen,
    required this.onRemove,
  });

  final TripDiscoverModel trip;
  final VoidCallback onOpen;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) => _WhiteCard(
    onTap: onOpen,
    accentColor: const Color(0xFFFF6B9D),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                trip.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w900),
              ),
            ),
            IconButton(
              tooltip: '取消收藏',
              onPressed: onRemove,
              icon: const Icon(
                LucideIcons.heartOff,
                size: 19,
                color: AppColors.primary,
              ),
            ),
          ],
        ),
        Text(
          '${trip.startName} → ${trip.endName}',
          style: const TextStyle(
            color: AppColors.secondaryText,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 10),
        Wrap(
          spacing: 8,
          children: [
            _MetaPill(
              icon: LucideIcons.calendarDays,
              label: _dateLabel(trip.departureTime),
            ),
            _MetaPill(
              icon: LucideIcons.carFront,
              label: '${trip.joinedVehicleCount}/${trip.maxVehicleCount}车',
            ),
          ],
        ),
        const SizedBox(height: 13),
        _CompactButton(
          label: '查看行程',
          icon: LucideIcons.chevronRight,
          filled: true,
          onTap: onOpen,
        ),
      ],
    ),
  );
}

class _SectionHeaderSliver extends StatelessWidget {
  const _SectionHeaderSliver({
    required this.title,
    required this.subtitle,
    this.suffix,
  });

  final String title;
  final String subtitle;
  final String? suffix;

  @override
  Widget build(BuildContext context) => SliverToBoxAdapter(
    child: Padding(
      padding: const EdgeInsets.fromLTRB(14, 18, 14, 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
                ),
                const SizedBox(height: 3),
                Text(
                  subtitle,
                  style: const TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              ],
            ),
          ),
          if (suffix != null)
            Text(
              suffix!,
              style: const TextStyle(
                color: AppColors.primary,
                fontSize: 12,
                fontWeight: FontWeight.w800,
              ),
            ),
        ],
      ),
    ),
  );
}

class _EmptySliver extends StatelessWidget {
  const _EmptySliver({
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
  Widget build(BuildContext context) => SliverToBoxAdapter(
    child: Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 24),
      child: _InlineNotice(
        icon: icon,
        title: title,
        subtitle: subtitle,
        actionLabel: actionLabel,
        onAction: onAction,
      ),
    ),
  );
}

class _InlineNotice extends StatelessWidget {
  const _InlineNotice({
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
  Widget build(BuildContext context) => _WhiteCard(
    child: Row(
      children: [
        Container(
          width: 48,
          height: 48,
          decoration: const BoxDecoration(
            color: AppColors.primarySoft,
            shape: BoxShape.circle,
          ),
          child: Icon(icon, color: AppColors.primary),
        ),
        const SizedBox(width: 13),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
              const SizedBox(height: 4),
              Text(
                subtitle,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(color: AppColors.muted, fontSize: 12),
              ),
            ],
          ),
        ),
        if (actionLabel != null && onAction != null)
          TextButton(onPressed: onAction, child: Text(actionLabel!)),
      ],
    ),
  );
}

class _WhiteCard extends StatelessWidget {
  const _WhiteCard({required this.child, this.onTap, this.accentColor});

  final Widget child;
  final VoidCallback? onTap;
  final Color? accentColor;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(22),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border.all(color: const Color(0xFFE5E7EB)),
          borderRadius: BorderRadius.circular(22),
          boxShadow: const [
            BoxShadow(
              color: Color(0x0D1D3969),
              blurRadius: 16,
              offset: Offset(0, 5),
            ),
          ],
        ),
        child: Stack(
          children: [
            if (accentColor != null)
              Positioned(
                left: 0,
                top: 0,
                bottom: 0,
                child: ColoredBox(
                  color: accentColor!,
                  child: const SizedBox(width: 4),
                ),
              ),
            Padding(
              padding: EdgeInsets.fromLTRB(
                accentColor == null ? 15 : 17,
                15,
                15,
                15,
              ),
              child: child,
            ),
          ],
        ),
      ),
    ),
  );
}

class _CompactButton extends StatelessWidget {
  const _CompactButton({
    required this.label,
    required this.icon,
    required this.onTap,
    this.filled = false,
  });

  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final bool filled;

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 40,
    child: filled
        ? FilledButton.icon(
            onPressed: onTap,
            style: FilledButton.styleFrom(
              minimumSize: const Size(0, 40),
              padding: const EdgeInsets.symmetric(horizontal: 12),
            ),
            icon: Icon(icon, size: 16),
            label: Text(label, maxLines: 1),
          )
        : OutlinedButton.icon(
            onPressed: onTap,
            style: OutlinedButton.styleFrom(
              foregroundColor: AppColors.primary,
              side: const BorderSide(color: Color(0xFFBFD7FF)),
              shape: const StadiumBorder(),
              padding: const EdgeInsets.symmetric(horizontal: 12),
            ),
            icon: Icon(icon, size: 16),
            label: Text(label, maxLines: 1),
          ),
  );
}

class _SoftTag extends StatelessWidget {
  const _SoftTag({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
    decoration: BoxDecoration(
      color: AppColors.primarySoft,
      borderRadius: BorderRadius.circular(99),
    ),
    child: Text(
      label,
      style: const TextStyle(
        color: AppColors.primaryDark,
        fontSize: 11,
        fontWeight: FontWeight.w800,
      ),
    ),
  );
}

class _MetaPill extends StatelessWidget {
  const _MetaPill({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 6),
    decoration: BoxDecoration(
      color: const Color(0xFFF3F6FB),
      borderRadius: BorderRadius.circular(99),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 13, color: AppColors.secondaryText),
        const SizedBox(width: 5),
        Text(
          label,
          style: const TextStyle(
            color: AppColors.secondaryText,
            fontSize: 11,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    ),
  );
}

Color _managedTripAccent(int category, String status) {
  if (_isRunning(status) || category == 1) return const Color(0xFF22C55E);
  if (_isCancelled(status) || category == 3) return const Color(0xFF98A2B3);
  if (category == 2) return const Color(0xFF7C8AA5);
  return AppColors.primary;
}

const _categories = ['待出发', '进行中', '已完成', '已取消', '收藏'];

List<TripModel> _dedupeTrips(Iterable<TripModel> values) {
  final seen = <String>{};
  return values.where((trip) => trip.id.isNotEmpty && seen.add(trip.id)).toList();
}

bool _isRunning(String status) {
  final value = status.toUpperCase();
  return value == 'RUNNING' || value == 'ONGOING' || value == 'STARTED';
}

bool _isCancelled(String status) {
  final value = status.toUpperCase();
  return value == 'CANCELLED' || value == 'CANCELED' || value == 'EXITED';
}

String _statusLabel(String status) {
  switch (status.toUpperCase()) {
    case 'RUNNING':
    case 'ONGOING':
    case 'STARTED':
      return '行进中';
    case 'FINISHED':
    case 'ENDED':
    case 'SETTLED':
    case 'COMPLETED':
      return '已完成';
    case 'CANCELLED':
    case 'CANCELED':
    case 'EXITED':
      return '已取消';
    case 'DRAFT':
      return '草稿';
    case 'PUBLISHED':
    case 'RECRUITING':
      return '招募中';
    default:
      return '待出发';
  }
}

String _vehicleLabel(TripModel trip) {
  final max = trip.maxVehicles <= 0 ? '?' : trip.maxVehicles.toString();
  return '${trip.joinedVehicles}/$max车';
}

String _dateLabel(String? raw) {
  final value = DateTime.tryParse(raw ?? '');
  if (value == null) return '时间待定';
  return '${value.month}月${value.day}日';
}

String _dateTimeLabel(String? raw) {
  final value = DateTime.tryParse(raw ?? '');
  if (value == null) return '时间待定';
  return '${value.month}月${value.day}日 ${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';
}

String _relativeTime(String? raw) {
  final value = DateTime.tryParse(raw ?? '');
  if (value == null) return '最近';
  final days = DateTime.now().difference(value).inDays;
  if (days <= 0) return '今天';
  if (days == 1) return '1天前';
  return '$days天前';
}
