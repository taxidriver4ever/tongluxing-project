import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
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
    child: Column(
      children: [
        Container(
          padding: const EdgeInsets.fromLTRB(18, 12, 18, 12),
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              colors: [AppColors.primaryDark, Color(0xFF479EFF)],
            ),
          ),
          child: Row(
            children: [
              Expanded(
                child: _Tab(
                  '发现行程',
                  0,
                  section,
                  () => setState(() => section = 0),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _Tab(
                  '我的行程',
                  1,
                  section,
                  () => setState(() => section = 1),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: ColoredBox(
            color: const Color(0xFFF3F7FC),
            child: IndexedStack(
              index: section,
              children: const [TripDiscoveryPage(), _MyTripsPage()],
            ),
          ),
        ),
      ],
    ),
  );
}

class _Tab extends StatelessWidget {
  const _Tab(this.label, this.value, this.selected, this.onTap);
  final String label;
  final int value;
  final int selected;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) {
    final active = selected == value;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        height: 44,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: active ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(16),
        ),
        child: Text(
          label,
          style: TextStyle(
            color: active ? AppColors.primaryDark : Colors.white70,
            fontSize: 17,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

class _MyTripsPage extends StatefulWidget {
  const _MyTripsPage();
  @override
  State<_MyTripsPage> createState() => _MyTripsPageState();
}

class _MyTripsPageState extends State<_MyTripsPage>
    with AutomaticKeepAliveClientMixin {
  TripModel? current;
  bool loading = true;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    try {
      current = await TripService(context.read<AppSession>().api).current();
    } catch (_) {
      current = null;
    }
    if (mounted) setState(() => loading = false);
  }

  void open(Widget page) => Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => page),
  ).then((_) => load());

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return RefreshIndicator(
      onRefresh: load,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(18, 18, 18, 34),
        children: [
          if (loading)
            const SizedBox(
              height: 128,
              child: Center(child: CircularProgressIndicator()),
            )
          else
            _CurrentTrip(
              trip: current,
              onTap: current == null
                  ? () => open(const TripCreatePage())
                  : () => open(
                      TripDetailPage(tripId: current!.id, initial: current),
                    ),
            ),
          const SizedBox(height: 18),
          const Text(
            '行程管理',
            style: TextStyle(fontSize: 21, fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 12),
          _ManageTile(
            icon: LucideIcons.radioTower,
            title: '招募中的行程',
            subtitle: '管理公开招募、成员申请和行程信息',
            onTap: () => open(const TripOverviewPage(historyMode: false)),
          ),
          _ManageTile(
            icon: LucideIcons.calendarClock,
            title: '待出发的行程',
            subtitle: '查看已发布并准备开启的行程',
            onTap: () => open(const TripOverviewPage(historyMode: false)),
          ),
          _ManageTile(
            icon: LucideIcons.navigation,
            title: '进行中的行程',
            subtitle: '进入导航、成员位置与群聊',
            onTap: () => open(const TripOverviewPage(historyMode: false)),
          ),
          _ManageTile(
            icon: LucideIcons.history,
            title: '历史行程',
            subtitle: '已完成、已结算与历史车队群',
            onTap: () => open(const TripOverviewPage(historyMode: true)),
          ),
          _ManageTile(
            icon: LucideIcons.filePenLine,
            title: '行程草稿',
            subtitle: '继续编辑七天内保存的草稿',
            onTap: () => open(const TripDraftsPage()),
          ),
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed: () => open(const TripCreatePage()),
            icon: const Icon(LucideIcons.plus),
            label: const Text('创建新行程'),
          ),
        ],
      ),
    );
  }
}

class _CurrentTrip extends StatelessWidget {
  const _CurrentTrip({required this.trip, required this.onTap});
  final TripModel? trip;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(24),
    child: Ink(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppColors.primaryDark, Color(0xFF58A5FF)],
        ),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  trip == null ? '还没有进行中的行程' : '当前进行中的行程',
                  style: const TextStyle(color: Colors.white70, fontSize: 12),
                ),
                const SizedBox(height: 8),
                Text(
                  trip?.title ?? '规划下一段同行路线',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                if (trip != null) ...[
                  const SizedBox(height: 7),
                  Text(
                    '${trip!.startName} → ${trip!.endName}',
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white70),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: 12),
          CircleAvatar(
            radius: 24,
            backgroundColor: Colors.white,
            child: Icon(
              trip == null ? LucideIcons.plus : LucideIcons.navigation,
              color: AppColors.primaryDark,
            ),
          ),
        ],
      ),
    ),
  );
}

class _ManageTile extends StatelessWidget {
  const _ManageTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.only(bottom: 10),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(18),
    ),
    child: ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 7),
      leading: CircleAvatar(
        backgroundColor: AppColors.primarySoft,
        child: Icon(icon, color: AppColors.primary),
      ),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
      subtitle: Text(
        subtitle,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontSize: 12),
      ),
      trailing: const Icon(LucideIcons.chevronRight),
      onTap: onTap,
    ),
  );
}
