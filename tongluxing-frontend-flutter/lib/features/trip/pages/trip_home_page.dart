import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../profile/widgets/user_avatar.dart';
import 'trip_create_page.dart';
import 'trip_detail_page.dart';
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
              padding: const EdgeInsets.fromLTRB(16, 6, 16, 7),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  _TopTab(
                    label: '发现行程',
                    active: section == 0,
                    onTap: () => setState(() => section = 0),
                  ),
                  const SizedBox(width: 42),
                  _TopTab(
                    label: '我的行程',
                    active: section == 1,
                    onTap: () => setState(() => section = 1),
                  ),
                ],
              ),
            ),
          ),
        ),
        Expanded(
          child: ColoredBox(
            color: TripDiscoveryColors.pageBackground,
            // 只挂载当前页，避免“我的行程”在隐藏状态加载数据并触发布局断言。
            child: KeyedSubtree(
              key: ValueKey<int>(section),
              child: section == 0
                  ? const TripDiscoveryPage()
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
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(12),
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: TextStyle(
              color: active ? Colors.white : Colors.white70,
              fontSize: 16,
              fontWeight: active ? FontWeight.w900 : FontWeight.w600,
            ),
          ),
          const SizedBox(height: 5),
          Container(
            width: 27,
            height: 3,
            decoration: BoxDecoration(
              color: active ? Colors.white : Colors.transparent,
              borderRadius: BorderRadius.circular(99),
            ),
          ),
        ],
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
    try {
      final values = await Future.wait<dynamic>([
        TripService(api).dashboard(),
        UserProfileService(api).me(),
      ]);
      final dashboard = Map<String, dynamic>.from(values[0] as Map);
      TripModel? nextCurrent;
      if (dashboard['currentTrip'] is Map) {
        nextCurrent = TripModel.fromJson(
          Map<String, dynamic>.from(dashboard['currentTrip'] as Map),
        );
      }
      final nextUpcoming = _tripList(dashboard['upcomingTrips']);
      final nextRecent = _tripList(dashboard['recentTrips']);
      if (mounted) {
        setState(() {
          current = nextCurrent;
          upcoming = nextUpcoming;
          recent = nextRecent;
          profile = Map<String, dynamic>.from(values[1] as Map);
        });
      }
    } catch (e) {
      // 兼容尚未部署聚合接口的旧后端，自动回退到原有接口。
      try {
        final values = await Future.wait<dynamic>([
          TripService(api).current(),
          TripService(api).mine(),
          TripService(api).mine(scope: 'history'),
          UserProfileService(api).me(),
        ]);
        if (mounted) {
          setState(() {
            current = values[0] as TripModel?;
            upcoming = List<TripModel>.from(values[1] as List);
            recent = List<TripModel>.from(values[2] as List);
            profile = Map<String, dynamic>.from(values[3] as Map);
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
          const SizedBox(height: 17),
          Text(
            '你好，$nickname',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: AppColors.text,
              fontSize: 22,
              height: 1.15,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            '轻松规划下一段同行旅程',
            style: TextStyle(color: AppColors.muted, fontSize: 12.5),
          ),
          const SizedBox(height: 14),
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
              height: 150,
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
    return Align(
      alignment: Alignment.centerRight,
      child: UserAvatar(
        nickname: nickname,
        avatarImageKey: profile['avatarImageKey']?.toString() ?? '',
        radius: 18,
      ),
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
        child: Row(
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
            Expanded(
              child: _QuickAction(
                icon: LucideIcons.mapPinned,
                label: '创建行程',
                emphasized: true,
                onTap: onCreate,
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
                            fontSize: 18,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const SizedBox(height: 5),
                        Text(
                          trip == null
                              ? '点击创建行程，寻找同路伙伴'
                              : '${trip!.startName} → ${trip!.endName} · ${_dateLabel(trip!.departureTime)}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            color: Colors.white70,
                            fontSize: 11.5,
                          ),
                        ),
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
    this.emphasized = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool emphasized;

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
              color: emphasized ? AppColors.primary : AppColors.primarySoft,
              shape: BoxShape.circle,
            ),
            child: Icon(
              icon,
              size: 17,
              color: emphasized ? Colors.white : AppColors.primary,
            ),
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
              height: 82,
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
                '创建行程后会在这里展示',
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
