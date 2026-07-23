import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/location_snapshot.dart';
import 'companion_discovery_page.dart';
import 'nearby_trips_page.dart';
import 'trip_create_page.dart';
import 'trip_detail_page.dart';
import 'trip_drafts_page.dart';
import 'trip_overview_page.dart';

class TripHomePage extends StatefulWidget {
  const TripHomePage({super.key});

  @override
  State<TripHomePage> createState() => _TripHomePageState();
}

class _TripHomePageState extends State<TripHomePage> {
  bool loading = true;
  bool nearbyLoading = true;
  String? error;
  TripModel? current;
  List<CompanionMatchModel> nearbyTrips = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      nearbyLoading = true;
    });
    final api = context.read<AppSession>().api;
    try {
      current = await TripService(api).current();
      error = null;
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);

    final point = LocationSnapshot.current ?? LocationSnapshot.demoFallback;
    try {
      nearbyTrips = await CompanionMatchService(
        api,
      ).nearbyTrips(latitude: point.latitude, longitude: point.longitude);
    } catch (_) {
      nearbyTrips = [];
    }
    if (mounted) setState(() => nearbyLoading = false);
  }

  Future<void> _openCreateTrip() async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const TripCreatePage()),
    );
    if (changed == true && mounted) load();
  }

  void _open(Widget page) =>
      Navigator.push(context, MaterialPageRoute(builder: (_) => page));

  @override
  Widget build(BuildContext context) => SafeArea(
    child: Column(
      children: [
        PageHeading(
          '行程',
          subtitle: '安排下一段旅程，也遇见附近同路的人',
          action: IconButton.filled(
            tooltip: '创建行程',
            onPressed: _openCreateTrip,
            icon: const Icon(LucideIcons.plus),
          ),
        ),
        Expanded(
          child: AsyncPanel(
            loading: loading,
            error: error,
            onRetry: load,
            child: RefreshIndicator(
              onRefresh: load,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(22, 0, 22, 34),
                children: [
                  _NavigationHero(
                    trip: current,
                    onCreate: _openCreateTrip,
                    onContinue: current == null
                        ? null
                        : () => _open(
                            TripDetailPage(
                              tripId: current!.id,
                              initial: current!,
                            ),
                          ),
                  ),
                  const SizedBox(height: 18),
                  GridView.count(
                    crossAxisCount: 2,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 1.28,
                    children: [
                      _ActionCard(
                        icon: LucideIcons.route,
                        title: '我的行程',
                        subtitle: '招募 · 进行 · 取消',
                        color: const Color(0xFFE7F1FF),
                        onTap: () =>
                            _open(const TripOverviewPage(historyMode: false)),
                      ),
                      _ActionCard(
                        icon: LucideIcons.history,
                        title: '历史与结算',
                        subtitle: '完成 · 已结算',
                        color: const Color(0xFFEDF4FF),
                        onTap: () =>
                            _open(const TripOverviewPage(historyMode: true)),
                      ),
                      _ActionCard(
                        icon: LucideIcons.filePenLine,
                        title: '我的草稿',
                        subtitle: '继续编辑未发布行程',
                        color: const Color(0xFFF0F6FF),
                        onTap: () => _open(const TripDraftsPage()),
                      ),
                      _ActionCard(
                        icon: LucideIcons.usersRound,
                        title: '发现同行',
                        subtitle: '按路线寻找伙伴',
                        color: const Color(0xFFE5F3FF),
                        onTap: () => _open(const CompanionDiscoveryPage()),
                      ),
                    ],
                  ),
                  const SizedBox(height: 26),
                  Row(
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '附近招募',
                              style: TextStyle(
                                fontSize: 21,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            SizedBox(height: 3),
                            Text(
                              '正在招募同路伙伴的公开行程',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.muted,
                              ),
                            ),
                          ],
                        ),
                      ),
                      TextButton(
                        onPressed: () => _open(const NearbyTripsPage()),
                        child: const Text('查看全部'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  if (nearbyLoading)
                    const SizedBox(
                      height: 154,
                      child: Center(child: CircularProgressIndicator()),
                    )
                  else if (nearbyTrips.isEmpty)
                    _NearbyEmpty(onTap: () => _open(const NearbyTripsPage()))
                  else
                    SizedBox(
                      height: 166,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: nearbyTrips.take(6).length,
                        separatorBuilder: (_, _) => const SizedBox(width: 12),
                        itemBuilder: (_, index) => _NearbyPreviewCard(
                          trip: nearbyTrips[index],
                          onTap: () => _open(
                            CompanionMatchDetailPage(
                              matchId: '',
                              initial: nearbyTrips[index],
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ],
    ),
  );
}

class _NavigationHero extends StatelessWidget {
  const _NavigationHero({
    required this.trip,
    required this.onCreate,
    required this.onContinue,
  });

  final TripModel? trip;
  final VoidCallback onCreate;
  final VoidCallback? onContinue;

  @override
  Widget build(BuildContext context) => Container(
    height: 192,
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      gradient: const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF246ED8), Color(0xFF58A5FF)],
      ),
      borderRadius: BorderRadius.circular(28),
      boxShadow: const [
        BoxShadow(
          color: Color(0x303A86FF),
          blurRadius: 24,
          offset: Offset(0, 12),
        ),
      ],
    ),
    child: Stack(
      children: [
        const Positioned(
          right: -12,
          top: -16,
          child: Icon(
            LucideIcons.navigation,
            size: 138,
            color: Color(0x28FFFFFF),
          ),
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              trip == null ? '下一段旅程' : '继续导航',
              style: const TextStyle(color: Colors.white70, fontSize: 13),
            ),
            const SizedBox(height: 9),
            Text(
              trip == null
                  ? '准备好，就出发'
                  : '${trip!.startName}  →  ${trip!.endName}',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 22,
                height: 1.3,
                fontWeight: FontWeight.w800,
              ),
            ),
            const Spacer(),
            FilledButton.icon(
              style: FilledButton.styleFrom(
                minimumSize: const Size(0, 44),
                backgroundColor: Colors.white,
                foregroundColor: AppColors.primaryDark,
              ),
              onPressed: onContinue ?? onCreate,
              icon: Icon(
                trip == null ? LucideIcons.plus : LucideIcons.navigation,
                size: 18,
              ),
              label: Text(trip == null ? '创建行程' : '继续导航'),
            ),
          ],
        ),
      ],
    ),
  );
}

class _ActionCard extends StatelessWidget {
  const _ActionCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.color,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
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
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: .88),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 20, color: AppColors.primaryDark),
            ),
            const Spacer(),
            Text(
              title,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 3),
            Text(
              subtitle,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 11,
                color: AppColors.secondaryText,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _NearbyPreviewCard extends StatelessWidget {
  const _NearbyPreviewCard({required this.trip, required this.onTap});
  final CompanionMatchModel trip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 228,
    child: Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(22),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(
                    LucideIcons.mapPinned,
                    size: 18,
                    color: AppColors.primary,
                  ),
                  const Spacer(),
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 9,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.primarySoft,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Text(
                      '招募中',
                      style: TextStyle(fontSize: 11, color: AppColors.primary),
                    ),
                  ),
                ],
              ),
              const Spacer(),
              Text(
                trip.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 7),
              Text(
                '${trip.startName} → ${trip.endName}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 12,
                  color: AppColors.secondaryText,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                trip.departureTime,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 11, color: AppColors.muted),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _NearbyEmpty extends StatelessWidget {
  const _NearbyEmpty({required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: const Color(0xFFEDF5FF),
    borderRadius: BorderRadius.circular(22),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(22),
      child: const Padding(
        padding: EdgeInsets.all(20),
        child: Row(
          children: [
            Icon(LucideIcons.mapPinned, color: AppColors.primary),
            SizedBox(width: 12),
            Expanded(child: Text('附近暂时没有招募行程，点击查看全部')),
            Icon(LucideIcons.chevronRight, color: AppColors.muted),
          ],
        ),
      ),
    ),
  );
}
