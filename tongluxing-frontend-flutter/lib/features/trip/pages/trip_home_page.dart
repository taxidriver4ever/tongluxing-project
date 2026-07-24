import 'package:flutter/material.dart';
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

class TripHomePage extends StatefulWidget {
  const TripHomePage({super.key});

  @override
  State<TripHomePage> createState() => _TripHomePageState();
}

class _TripHomePageState extends State<TripHomePage> {
  int section = 0;

  @override
  Widget build(BuildContext context) => SafeArea(
    bottom: false,
    child: ColoredBox(
      color: const Color(0xFFF4F7FC),
      child: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 5),
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [AppColors.primaryDark, Color(0xFF3999F5)],
              ),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _TopTab(
                  label: '发现行程',
                  active: section == 0,
                  onTap: () => setState(() => section = 0),
                ),
                const SizedBox(width: 46),
                _TopTab(
                  label: '我的行程',
                  active: section == 1,
                  onTap: () => setState(() => section = 1),
                ),
              ],
            ),
          ),
          Expanded(
            child: IndexedStack(
              index: section,
              children: const [TripDiscoveryPage(), _MyTripsPage()],
            ),
          ),
        ],
      ),
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
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: TextStyle(
              color: active ? Colors.white : Colors.white70,
              fontSize: 17,
              fontWeight: active ? FontWeight.w900 : FontWeight.w600,
            ),
          ),
          const SizedBox(height: 5),
          AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            width: active ? 34 : 0,
            height: 4,
            decoration: BoxDecoration(
              color: Colors.white,
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

class _MyTripsPageState extends State<_MyTripsPage>
    with AutomaticKeepAliveClientMixin {
  TripModel? current;
  List<TripModel> upcoming = const [];
  List<TripModel> recent = const [];
  Map<String, dynamic> profile = const {};
  bool loading = true;
  String? error;

  @override
  bool get wantKeepAlive => true;

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
    try {
      final api = context.read<AppSession>().api;
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
        final api = context.read<AppSession>().api;
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
    super.build(context);
    if (loading && profile.isEmpty && current == null && upcoming.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    return RefreshIndicator(
      onRefresh: load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 34),
        children: [
          _DashboardHeader(profile: profile),
          const SizedBox(height: 22),
          Text(
            '你好，$nickname',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: AppColors.text,
              fontSize: 31,
              height: 1.15,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 6),
          const Text(
            '轻松规划下一段同行旅程',
            style: TextStyle(color: AppColors.muted, fontSize: 14),
          ),
          const SizedBox(height: 24),
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
          const SizedBox(height: 28),
          Row(
            children: [
              const Text(
                '即将出发',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
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
              height: 170,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: upcoming.length,
                separatorBuilder: (_, _) => const SizedBox(width: 12),
                itemBuilder: (context, index) {
                  final trip = upcoming[index];
                  return _UpcomingTripCard(
                    trip: trip,
                    onTap: () => open(
                      TripDetailPage(tripId: trip.id, initial: trip),
                    ),
                  );
                },
              ),
            ),
          if (recent.isNotEmpty) ...[
            const SizedBox(height: 28),
            Row(
              children: [
                const Text(
                  '最近完成',
                  style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
                ),
                const Spacer(),
                TextButton(
                  onPressed: () => open(
                    const TripOverviewPage(historyMode: true),
                  ),
                  child: const Text('查看历史'),
                ),
              ],
            ),
            const SizedBox(height: 8),
            ...recent.take(2).map(
              (trip) => _RecentTripTile(
                trip: trip,
                onTap: () => open(
                  TripDetailPage(tripId: trip.id, initial: trip),
                ),
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
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 9),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: const Color(0xFFDCE9F9)),
          ),
          child: const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(LucideIcons.navigation, size: 18),
              SizedBox(width: 8),
              Text(
                '同路行 1.0',
                style: TextStyle(fontWeight: FontWeight.w700),
              ),
              SizedBox(width: 5),
              Icon(LucideIcons.chevronDown, size: 16),
            ],
          ),
        ),
        const Spacer(),
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
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final gap = 12.0;
      final half = (constraints.maxWidth - gap) / 2;
      return Column(
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SizedBox(
                width: half,
                height: 230,
                child: _CurrentJourneyCard(
                  trip: current,
                  onTap: onCurrent,
                ),
              ),
              SizedBox(width: gap),
              SizedBox(
                width: half,
                height: 230,
                child: Column(
                  children: [
                    Expanded(
                      child: _ActionTile(
                        icon: LucideIcons.calendarDays,
                        label: '行程计划',
                        color: const Color(0xFFE7EEFF),
                        onTap: onPlan,
                      ),
                    ),
                    SizedBox(height: gap),
                    Expanded(
                      child: _ActionTile(
                        icon: LucideIcons.filePenLine,
                        label: '行程草稿',
                        color: const Color(0xFFDDEEFF),
                        onTap: onDrafts,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          SizedBox(height: gap),
          Row(
            children: [
              SizedBox(
                width: half,
                height: 112,
                child: _ActionTile(
                  icon: LucideIcons.history,
                  label: '历史行程',
                  color: const Color(0xFFE6F0FF),
                  onTap: onHistory,
                ),
              ),
              SizedBox(width: gap),
              SizedBox(
                width: half,
                height: 112,
                child: _ActionTile(
                  icon: LucideIcons.mapPinned,
                  label: '创建行程',
                  color: const Color(0xFFD9ECFF),
                  onTap: onCreate,
                ),
              ),
            ],
          ),
        ],
      );
    },
  );
}

class _CurrentJourneyCard extends StatelessWidget {
  const _CurrentJourneyCard({required this.trip, required this.onTap});

  final TripModel? trip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.transparent,
    borderRadius: BorderRadius.circular(24),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Ink(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF79B9F5), Color(0xFF175FC6)],
          ),
        ),
        child: Stack(
          children: [
            const Positioned(
              right: -20,
              top: 42,
              child: Icon(
                LucideIcons.mountainSnow,
                size: 145,
                color: Color(0x38FFFFFF),
              ),
            ),
            Positioned(
              left: 16,
              right: 16,
              bottom: 18,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    trip?.title ?? '规划下一段旅程',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 23,
                      height: 1.05,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    trip == null
                        ? '点击开始创建'
                        : '${trip!.startName} → ${trip!.endName}',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white, fontSize: 13),
                  ),
                  if (trip?.departureTime != null) ...[
                    const SizedBox(height: 4),
                    Text(
                      _dateLabel(trip!.departureTime),
                      style: const TextStyle(
                        color: Colors.white70,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _ActionTile extends StatelessWidget {
  const _ActionTile({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: color,
    borderRadius: BorderRadius.circular(24),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(24),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: const BoxDecoration(
                color: Color(0xCCFFFFFF),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: AppColors.primaryDark, size: 21),
            ),
            const Spacer(),
            Text(
              label,
              maxLines: 1,
              style: const TextStyle(
                fontSize: 19,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
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
    width: 178,
    child: Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(22),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              height: 96,
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
                size: 48,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 9, 12, 10),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    trip.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 16,
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
                      fontSize: 12,
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
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(22),
    ),
    child: Row(
      children: [
        const CircleAvatar(
          radius: 25,
          backgroundColor: AppColors.primarySoft,
          child: Icon(LucideIcons.calendarPlus, color: AppColors.primary),
        ),
        const SizedBox(width: 14),
        const Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '还没有即将出发的行程',
                style: TextStyle(fontWeight: FontWeight.w800),
              ),
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
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
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
