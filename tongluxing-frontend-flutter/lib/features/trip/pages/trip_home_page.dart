import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/utils/display_text.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import '../../profile/widgets/user_avatar.dart';
import 'trip_create_page.dart';
import 'trip_detail_page.dart';
import 'trip_discovery_detail_page.dart';
import 'trip_discovery_page.dart';
import 'trip_drafts_page.dart';
import 'trip_overview_page.dart';
import '../widgets/trip_discovery_theme.dart';

class TripHomePage extends StatefulWidget {
  const TripHomePage({super.key});

  @override
  State<TripHomePage> createState() => _TripHomePageState();
}

class _TripHomePageState extends State<TripHomePage> {
  int section = 0;
  int refreshVersion = 0;
  TripModel? pinnedTrip;
  bool loadingPinned = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadPinnedTrip());
  }

  /// 查询当前行程置顶区。优先展示正在进行的行程，没有时展示最近一条
  /// 待出发行程；推荐接口仍会在后端独立判断“是否有自己的基准行程”。
  Future<void> _loadPinnedTrip() async {
    try {
      final dashboard = Map<String, dynamic>.from(
        await TripService(context.read<AppSession>().api).dashboard(),
      );
      TripModel? next;
      if (dashboard['currentTrip'] is Map) {
        next = TripModel.fromJson(
          Map<String, dynamic>.from(dashboard['currentTrip'] as Map),
        );
      } else {
        final upcoming = (dashboard['upcomingTrips'] as List? ?? const [])
            .whereType<Map>();
        if (upcoming.isNotEmpty) {
          next = TripModel.fromJson(
            Map<String, dynamic>.from(upcoming.first),
          );
        }
      }
      if (!mounted) return;
      setState(() {
        pinnedTrip = next;
        loadingPinned = false;
      });
    } catch (_) {
      if (mounted) setState(() => loadingPinned = false);
    }
  }

  Future<void> _openCreateTrip() async {
    final created = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const TripCreatePage()),
    );
    if (!mounted || created != true) return;
    setState(() => refreshVersion++);
    await _loadPinnedTrip();
  }

  Future<void> _openPinnedTrip() async {
    final trip = pinnedTrip;
    if (trip == null) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TripDetailPage(tripId: trip.id, initial: trip),
      ),
    );
    if (!mounted) return;
    setState(() => refreshVersion++);
    await _loadPinnedTrip();
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
        Container(
          width: double.infinity,
          decoration: const BoxDecoration(
            gradient: TripDiscoveryColors.headerGradient,
          ),
          child: SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: Row(
                children: [
                  const Expanded(
                    child: Text(
                      '行程',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 21,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: '发布新行程',
                    onPressed: _openCreateTrip,
                    style: IconButton.styleFrom(
                      foregroundColor: Colors.white,
                      backgroundColor: const Color(0x33FFFFFF),
                    ),
                    icon: const Icon(LucideIcons.plus, size: 24),
                  ),
                ],
              ),
            ),
          ),
        ),
        // 当前行程始终位于一级切换入口上方，符合“当前行程置顶区”的页面结构。
        if (loadingPinned)
          const LinearProgressIndicator(minHeight: 2)
        else if (pinnedTrip != null)
          _PinnedCurrentTripCard(
            trip: pinnedTrip!,
            onTap: _openPinnedTrip,
          ),
        Container(
          height: 50,
          color: Colors.white,
          child: Row(
            children: [
              _TopTab(
                label: '推荐',
                active: section == 0,
                onTap: () => setState(() => section = 0),
              ),
              _TopTab(
                label: '我的行程',
                active: section == 1,
                onTap: () => setState(() => section = 1),
              ),
            ],
          ),
        ),
        Expanded(
          child: ColoredBox(
            color: TripDiscoveryColors.pageBackground,
            child: KeyedSubtree(
              key: ValueKey<String>('$section-$refreshVersion'),
              child: section == 0
                  ? TripDiscoveryPage(
                      userHasTrip: pinnedTrip != null,
                      onTripCreated: () {
                        setState(() => refreshVersion++);
                        _loadPinnedTrip();
                      },
                      onApplicationSubmitted: () =>
                          setState(() => refreshVersion++),
                    )
                  : const _MyTripsPage(),
            ),
          ),
        ),
      ],
    ),
  );
}

class _TopTab extends StatelessWidget {
  const _TopTab({
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
      child: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Text(
            label,
            style: TextStyle(
              color: active ? AppColors.primary : AppColors.secondaryText,
              fontSize: 15,
              fontWeight: active ? FontWeight.w900 : FontWeight.w600,
            ),
          ),
          const SizedBox(height: 9),
          Container(
            width: 42,
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

class _PinnedCurrentTripCard extends StatelessWidget {
  const _PinnedCurrentTripCard({required this.trip, required this.onTap});

  final TripModel trip;
  final VoidCallback onTap;

  String get departureLabel {
    final value = DateTime.tryParse(
      (trip.departureTime ?? '').replaceFirst(' ', 'T'),
    );
    if (value == null) return '时间待定';
    return '${value.month}月${value.day}日 '
        '${value.hour.toString().padLeft(2, '0')}:'
        '${value.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) => Container(
    color: Colors.white,
    padding: const EdgeInsets.fromLTRB(12, 12, 12, 8),
    child: Material(
      color: const Color(0xFFF5F9FF),
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Padding(
          padding: const EdgeInsets.all(13),
          child: Row(
            children: [
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: AppColors.primary,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  LucideIcons.navigation,
                  color: Colors.white,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '当前行程',
                      style: TextStyle(
                        color: AppColors.primary,
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '${trip.startName} → ${trip.endName} · $departureLabel',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w800),
                    ),
                  ],
                ),
              ),
              const Icon(
                LucideIcons.chevronRight,
                color: AppColors.muted,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _MyTripsPage extends StatefulWidget {
  const _MyTripsPage();

  @override
  State<_MyTripsPage> createState() => _MyTripsPageState();
}

class _MyTripsPageState extends State<_MyTripsPage> {
  TripModel? current;
  List<TripModel> upcoming = const [];
  List<TripModel> recent = const [];
  List<TripApplicationModel> pendingApplications = const [];
  Map<String, dynamic> profile = const {};
  bool loading = true;
  String? error;

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
    final profileFuture = UserProfileService(
      api,
    ).me().catchError((_) => <String, dynamic>{});
    final applicationsFuture = TripDiscoveryService(api)
        .myApplications()
        .catchError((_) => <TripApplicationModel>[]);
    try {
      final dashboard = Map<String, dynamic>.from(
        await TripService(api).dashboard(),
      );
      TripModel? nextCurrent;
      if (dashboard['currentTrip'] is Map) {
        nextCurrent = TripModel.fromJson(
          Map<String, dynamic>.from(dashboard['currentTrip'] as Map),
        );
      }
      final nextUpcoming = _tripList(dashboard['upcomingTrips']);
      final nextRecent = _tripList(dashboard['recentTrips']);
      final loadedProfile = await profileFuture;
      final loadedApplications = await applicationsFuture;
      if (mounted) {
        setState(() {
          current = nextCurrent;
          upcoming = nextUpcoming;
          recent = nextRecent;
          pendingApplications = loadedApplications
              .where((application) => application.status == 'PENDING')
              .toList();
          profile = Map<String, dynamic>.from(loadedProfile);
        });
      }
    } catch (e) {
      // 仅当聚合接口尚未部署时回退；网络、鉴权或资料接口失败不能触发
      // 整套行程接口二次请求。
      final mayFallback =
          e is ApiException && (e.statusCode == 404 || e.statusCode == 501);
      if (!mayFallback) {
        if (mounted) setState(() => error = e.toString());
        return;
      }
      try {
        final values = await Future.wait<dynamic>([
          TripService(api).current(),
          TripService(api).mine(),
          TripService(api).mine(scope: 'history'),
        ]);
        final loadedProfile = await profileFuture;
        final loadedApplications = await applicationsFuture;
        if (mounted) {
          setState(() {
            current = values[0] as TripModel?;
            upcoming = List<TripModel>.from(values[1] as List);
            recent = List<TripModel>.from(values[2] as List);
            pendingApplications = loadedApplications
                .where((application) => application.status == 'PENDING')
                .toList();
            profile = Map<String, dynamic>.from(loadedProfile);
          });
        }
      } catch (fallbackError) {
        if (mounted) setState(() => error = fallbackError.toString());
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  List<TripModel> _tripList(dynamic value) => (value as List? ?? const [])
      .whereType<Map>()
      .map((e) => TripModel.fromJson(Map<String, dynamic>.from(e)))
      .toList();

  Future<void> open(Widget page) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    if (mounted) await load();
  }

  String get nickname {
    final value = profile['nickname']?.toString().trim() ?? '';
    return value.isEmpty ? '同路行车友' : value;
  }

  @override
  Widget build(BuildContext context) {
    if (loading && profile.isEmpty && current == null && upcoming.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    return RefreshIndicator(
      onRefresh: load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(10, 14, 10, 28),
        children: [
          _DashboardHeader(profile: profile),
          const SizedBox(height: 16),
          _DashboardGrid(
            current: current,
            onCurrent: () => current == null
                ? open(const TripCreatePage())
                : open(TripDetailPage(tripId: current!.id, initial: current)),
            onPlan: () => open(
              const TripOverviewPage(
                historyMode: false,
                initialFilter: 'PUBLISHED',
              ),
            ),
            onDrafts: () => open(const TripDraftsPage()),
            onHistory: () => open(const TripOverviewPage(historyMode: true)),
            onCreate: () => open(const TripCreatePage()),
          ),
          if (pendingApplications.isNotEmpty) ...[
            const SizedBox(height: 22),
            const Row(
              children: [
                Text(
                  '待出发 · 待审批',
                  style: TextStyle(fontSize: 19, fontWeight: FontWeight.w800),
                ),
              ],
            ),
            const SizedBox(height: 10),
            ...pendingApplications.map(
              (application) => Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  leading: const CircleAvatar(
                    child: Icon(LucideIcons.clock3, size: 19),
                  ),
                  title: Text(
                    application.conversationName.isEmpty
                        ? '行程入队申请'
                        : application.conversationName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: const Text('申请已提交，等待队长审批'),
                  trailing: const Text(
                    '待审批',
                    style: TextStyle(
                      color: Color(0xFFF59E0B),
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  onTap: application.tripId.isEmpty
                      ? null
                      : () => open(
                          TripDiscoveryDetailPage(
                            tripId: application.tripId,
                          ),
                        ),
                ),
              ),
            ),
          ],
          const SizedBox(height: 22),
          Row(
            children: [
              const Text(
                '即将出发',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.w800),
              ),
              const Spacer(),
              TextButton(
                onPressed: () => open(
                  const TripOverviewPage(
                    historyMode: false,
                    initialFilter: 'READY',
                  ),
                ),
                child: const Text('查看全部'),
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (error != null && upcoming.isEmpty)
            _InlineError(message: error!, onRetry: load)
          else if (upcoming.isEmpty)
            _EmptyUpcoming(onCreate: () => open(const TripCreatePage()))
          else
            SizedBox(
              height: 166,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: upcoming.length,
                separatorBuilder: (_, _) => const SizedBox(width: 12),
                itemBuilder: (context, index) {
                  final trip = upcoming[index];
                  return _UpcomingTripCard(
                    trip: trip,
                    onTap: () =>
                        open(TripDetailPage(tripId: trip.id, initial: trip)),
                  );
                },
              ),
            ),
          if (recent.isNotEmpty) ...[
            const SizedBox(height: 22),
            Row(
              children: [
                const Text(
                  '最近完成',
                  style: TextStyle(fontSize: 19, fontWeight: FontWeight.w800),
                ),
                const Spacer(),
                TextButton(
                  onPressed: () =>
                      open(const TripOverviewPage(historyMode: true)),
                  child: const Text('查看历史'),
                ),
              ],
            ),
            const SizedBox(height: 6),
            ...recent
                .take(2)
                .map(
                  (trip) => _RecentTripTile(
                    trip: trip,
                    onTap: () =>
                        open(TripDetailPage(tripId: trip.id, initial: trip)),
                  ),
                ),
          ],
        ],
      ),
    );
  }
}

class _DashboardHeader extends StatelessWidget {
  const _DashboardHeader({required this.profile});

  final Map<String, dynamic> profile;

  @override
  Widget build(BuildContext context) {
    final nickname = profile['nickname']?.toString() ?? '同路行用户';
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '你好，${compactDisplayName(nickname)}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: AppColors.text,
                  fontSize: 23,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 5),
              const Text(
                '轻松规划下一段同行旅程',
                style: TextStyle(color: AppColors.muted, fontSize: 13),
              ),
            ],
          ),
        ),
        UserAvatar(
          nickname: nickname,
          avatarImageKey: profile['avatarImageKey']?.toString() ?? '',
          radius: 21,
        ),
      ],
    );
  }
}

class _DashboardGrid extends StatelessWidget {
  const _DashboardGrid({
    required this.current,
    required this.onCurrent,
    required this.onPlan,
    required this.onDrafts,
    required this.onHistory,
    required this.onCreate,
  });

  final TripModel? current;
  final VoidCallback onCurrent;
  final VoidCallback onPlan;
  final VoidCallback onDrafts;
  final VoidCallback onHistory;
  final VoidCallback onCreate;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      AspectRatio(
        // 高 / 宽约为 0.618，保持横向黄金矩形。
        aspectRatio: 1 / 0.618,
        child: _CurrentJourneyCard(trip: current, onTap: onCurrent),
      ),
      const SizedBox(height: 10),
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 9),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(17),
          border: Border.all(color: const Color(0xFFE4EBF4)),
        ),
        child: Column(
          children: [
            Row(
              children: [
                Expanded(
                  child: _QuickAction(
                    icon: LucideIcons.calendarDays,
                    label: '行程计划',
                    onTap: onPlan,
                  ),
                ),
                Expanded(
                  child: _QuickAction(
                    icon: LucideIcons.filePenLine,
                    label: '行程草稿',
                    onTap: onDrafts,
                  ),
                ),
                Expanded(
                  child: _QuickAction(
                    icon: LucideIcons.history,
                    label: '历史行程',
                    onTap: onHistory,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            SizedBox(
              width: double.infinity,
              height: 50,
              child: FilledButton.icon(
                onPressed: onCreate,
                icon: const Icon(LucideIcons.plus, size: 23),
                label: const Text(
                  '创建行程',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                ),
              ),
            ),
          ],
        ),
      ),
    ],
  );
}

class _CurrentJourneyCard extends StatelessWidget {
  const _CurrentJourneyCard({required this.trip, required this.onTap});

  final TripModel? trip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.transparent,
    borderRadius: BorderRadius.circular(18),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Ink(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF70B5F4), Color(0xFF1761C8)],
          ),
        ),
        child: Stack(
          children: [
            const Positioned(
              right: -12,
              bottom: -30,
              child: Icon(
                LucideIcons.mountainSnow,
                size: 128,
                color: Color(0x2EFFFFFF),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(15, 13, 14, 13),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: const Color(0x2EFFFFFF),
                            borderRadius: BorderRadius.circular(99),
                          ),
                          child: Text(
                            trip == null ? '下一段旅程' : '当前行程',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 10.5,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        const Spacer(),
                        Text(
                          trip?.title ?? '规划下一段旅程',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 23,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          trip == null
                              ? '点击发布新行程，寻找同路伙伴'
                              : '${trip!.startName} → ${trip!.endName} · ${_dateLabel(trip!.departureTime)}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Colors.white70,
                            fontSize: 13.5,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        if (trip != null) ...[
                          const SizedBox(height: 8),
                          Text(
                            '车型要求：${trip!.vehicleRequirements.join(' / ')}',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 12.5,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Container(
                    width: 38,
                    height: 38,
                    decoration: const BoxDecoration(
                      color: Color(0xDFFFFFFF),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      LucideIcons.chevronRight,
                      color: AppColors.primaryDark,
                      size: 20,
                    ),
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

class _QuickAction extends StatelessWidget {
  const _QuickAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(12),
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: AppColors.primarySoft,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 17, color: AppColors.primary),
          ),
          const SizedBox(height: 7),
          Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: AppColors.text,
              fontSize: 11.5,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    ),
  );
}

class _UpcomingTripCard extends StatelessWidget {
  const _UpcomingTripCard({required this.trip, required this.onTap});

  final TripModel trip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 156,
    child: Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(18),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              height: 88,
              width: double.infinity,
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color(0xFFB8D9FF), Color(0xFF4D91DE)],
                ),
              ),
              child: const Icon(
                LucideIcons.mountain,
                color: Colors.white70,
                size: 40,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 7, 10, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    trip.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    _dateLabel(trip.departureTime),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 10.5,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    '车型：${trip.vehicleRequirements.join(' / ')}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.primary,
                      fontSize: 10.5,
                      fontWeight: FontWeight.w700,
                    ),
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

class _RecentTripTile extends StatelessWidget {
  const _RecentTripTile({required this.trip, required this.onTap});

  final TripModel trip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 10),
    child: ListTile(
      onTap: onTap,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      leading: const CircleAvatar(
        backgroundColor: AppColors.primarySoft,
        child: Icon(LucideIcons.flag, color: AppColors.primary),
      ),
      title: Text(
        trip.title,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontWeight: FontWeight.w800),
      ),
      subtitle: Text('${trip.startName} → ${trip.endName}'),
      trailing: const Icon(LucideIcons.chevronRight, size: 19),
    ),
  );
}

class _EmptyUpcoming extends StatelessWidget {
  const _EmptyUpcoming({required this.onCreate});

  final VoidCallback onCreate;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(17),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(18),
    ),
    child: Row(
      children: [
        const CircleAvatar(
          radius: 22,
          backgroundColor: AppColors.primarySoft,
          child: Icon(LucideIcons.calendarPlus, color: AppColors.primary),
        ),
        const SizedBox(width: 11),
        const Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('还没有即将出发的行程', style: TextStyle(fontWeight: FontWeight.w800)),
              SizedBox(height: 3),
              Text(
                '发布新行程后会在这里展示',
                style: TextStyle(color: AppColors.muted, fontSize: 12),
              ),
            ],
          ),
        ),
        TextButton(onPressed: onCreate, child: const Text('去创建')),
      ],
    ),
  );
}

class _InlineError extends StatelessWidget {
  const _InlineError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(15),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(17),
    ),
    child: Row(
      children: [
        const Icon(LucideIcons.wifiOff, color: AppColors.muted),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            message,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: AppColors.muted),
          ),
        ),
        TextButton(onPressed: onRetry, child: const Text('重试')),
      ],
    ),
  );
}

String _dateLabel(String? raw) {
  final value = DateTime.tryParse(raw ?? '');
  if (value == null) return '时间待定';
  return '${value.month.toString().padLeft(2, '0')}月${value.day.toString().padLeft(2, '0')}日';
}
