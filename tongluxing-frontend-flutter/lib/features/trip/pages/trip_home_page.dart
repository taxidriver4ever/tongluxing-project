import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import 'trip_create_page.dart';
import 'trip_detail_page.dart';
import 'trip_drafts_page.dart';
import 'companion_discovery_page.dart';

class TripHomePage extends StatefulWidget {
  const TripHomePage({super.key});
  @override
  State<TripHomePage> createState() => _TripHomePageState();
}

class _TripHomePageState extends State<TripHomePage> {
  bool loading = true;
  String? error;
  List<TripModel> trips = [];
  List<TripModel> history = [];
  TripModel? current;
  String activeFilter = 'PUBLISHED';
  String historyFilter = 'FINISHED';

  List<TripModel> get filteredTrips => trips.where((trip) {
    if (activeFilter == 'RUNNING') {
      return trip.status == 'RUNNING' || trip.status == 'ONGOING';
    }
    return trip.status == 'PUBLISHED';
  }).toList();

  List<TripModel> get filteredHistory => history.where((trip) {
    if (historyFilter == 'FINISHED') {
      return trip.status == 'FINISHED' || trip.status == 'ENDED';
    }
    return trip.status == 'SETTLED';
  }).toList();
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final service = TripService(context.read<AppSession>().api);
      final values = await Future.wait([
        service.mine(),
        service.current(),
        service.mine(scope: 'history'),
      ]);
      trips = values[0] as List<TripModel>;
      current = values[1] as TripModel?;
      history = values[2] as List<TripModel>;
      error = null;
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  @override
  Widget build(BuildContext context) => SafeArea(
    child: Column(
      children: [
        PageHeading(
          '行程',
          subtitle: '管理你的自驾旅程，寻找同行伙伴',
          action: IconButton.filled(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const TripCreatePage()),
            ),
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
                padding: const EdgeInsets.fromLTRB(22, 0, 22, 28),
                children: [
                  if (current != null) _DrivingCard(trip: current!),
                  const SizedBox(height: 16),
                  _DraftEntry(
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const TripDraftsPage()),
                    ),
                  ),
                  const SizedBox(height: 12),
                  _DiscoverEntry(
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const CompanionDiscoveryPage(),
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),
                  const Text(
                    '我的行程',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 10),
                  _StatusSelector(
                    value: activeFilter,
                    options: const {'PUBLISHED': '招募中', 'RUNNING': '进行中'},
                    onChanged: (value) => setState(() => activeFilter = value),
                  ),
                  if (filteredTrips.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 30),
                      child: Center(
                        child: Text(
                          '当前筛选下暂无行程',
                          style: TextStyle(color: AppColors.muted),
                        ),
                      ),
                    )
                  else
                    ...filteredTrips.map(
                      (trip) => _TripCard(
                        trip: trip,
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) =>
                                TripDetailPage(tripId: trip.id, initial: trip),
                          ),
                        ),
                      ),
                    ),
                  ...[
                    const SizedBox(height: 28),
                    const Text(
                      '历史与结算',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 10),
                    _StatusSelector(
                      value: historyFilter,
                      options: const {'FINISHED': '已完成', 'SETTLED': '已结算'},
                      onChanged: (value) =>
                          setState(() => historyFilter = value),
                    ),
                    if (filteredHistory.isEmpty)
                      const Padding(
                        padding: EdgeInsets.symmetric(vertical: 28),
                        child: Center(
                          child: Text(
                            '当前筛选下暂无历史行程',
                            style: TextStyle(color: AppColors.muted),
                          ),
                        ),
                      ),
                    ...filteredHistory
                        .take(10)
                        .map(
                          (trip) => _TripCard(
                            trip: trip,
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => TripDetailPage(
                                  tripId: trip.id,
                                  initial: trip,
                                ),
                              ),
                            ),
                          ),
                        ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ],
    ),
  );
}

class _StatusSelector extends StatelessWidget {
  const _StatusSelector({
    required this.value,
    required this.options,
    required this.onChanged,
  });
  final String value;
  final Map<String, String> options;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) => Wrap(
    spacing: 8,
    children: options.entries
        .map(
          (entry) => ChoiceChip(
            label: Text(entry.value),
            selected: value == entry.key,
            onSelected: (_) => onChanged(entry.key),
          ),
        )
        .toList(),
  );
}

class _DrivingCard extends StatelessWidget {
  const _DrivingCard({required this.trip});
  final TripModel trip;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      color: const Color(0xFF285CFF),
      borderRadius: BorderRadius.circular(24),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '正在驾驶中',
          style: TextStyle(color: Colors.white70, fontSize: 13),
        ),
        const SizedBox(height: 10),
        Text(
          '${trip.startName}\n↓\n${trip.endName}',
          style: const TextStyle(
            color: Colors.white,
            fontSize: 22,
            fontWeight: FontWeight.w700,
            height: 1.35,
          ),
        ),
        const SizedBox(height: 14),
        FilledButton(
          style: FilledButton.styleFrom(
            backgroundColor: Colors.white,
            foregroundColor: AppColors.primary,
          ),
          onPressed: () => Navigator.push(
            context,
            MaterialPageRoute(
              builder: (_) => TripDetailPage(tripId: trip.id, initial: trip),
            ),
          ),
          child: const Text('继续导航'),
        ),
      ],
    ),
  );
}

class _DraftEntry extends StatelessWidget {
  const _DraftEntry({required this.onTap});
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(20),
    child: Container(
      padding: const EdgeInsets.all(17),
      decoration: BoxDecoration(
        color: const Color(0xFFF5F8FF),
        border: Border.all(color: const Color(0xFFDBE7FF)),
        borderRadius: BorderRadius.circular(20),
      ),
      child: const Row(
        children: [
          CircleAvatar(
            backgroundColor: Color(0xFFE4EDFF),
            child: Icon(LucideIcons.filePenLine, color: AppColors.primary),
          ),
          SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('我的草稿', style: TextStyle(fontWeight: FontWeight.w700)),
                Text(
                  '继续编辑、删除或发布未完成行程',
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.secondaryText,
                  ),
                ),
              ],
            ),
          ),
          Icon(LucideIcons.chevronRight),
        ],
      ),
    ),
  );
}

class _DiscoverEntry extends StatelessWidget {
  const _DiscoverEntry({required this.onTap});
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(20),
    child: Container(
      padding: const EdgeInsets.all(17),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFFF1F5FF), Color(0xFFF8FAFF)],
        ),
        border: Border.all(color: const Color(0xFFD7E3FF)),
        borderRadius: BorderRadius.circular(20),
      ),
      child: const Row(
        children: [
          CircleAvatar(
            backgroundColor: Color(0xFF285CFF),
            child: Icon(LucideIcons.usersRound, color: Colors.white),
          ),
          SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('发现同行', style: TextStyle(fontWeight: FontWeight.w700)),
                Text(
                  '按路线、目的地和出发时间寻找伙伴',
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.secondaryText,
                  ),
                ),
              ],
            ),
          ),
          Icon(LucideIcons.chevronRight),
        ],
      ),
    ),
  );
}

class _TripCard extends StatelessWidget {
  const _TripCard({required this.trip, required this.onTap});
  final TripModel trip;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 14),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(22),
      child: TlxCard(
        color: const Color(0xFFF6F8FC),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    trip.title,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 5,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.primarySoft,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Text(
                    tripStatusLabel(trip.status),
                    style: const TextStyle(
                      color: AppColors.primary,
                      fontSize: 12,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text('${trip.startName}\n│\n${trip.endName}'),
            const SizedBox(height: 12),
            Text(
              '出发：${trip.departureTime ?? '待确定'}  ·  车辆 ${trip.joinedVehicles}/${trip.maxVehicles}',
              style: const TextStyle(
                color: AppColors.secondaryText,
                fontSize: 13,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
