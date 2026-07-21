import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';

class CompanionDiscoveryPage extends StatefulWidget {
  const CompanionDiscoveryPage({super.key});
  @override
  State<CompanionDiscoveryPage> createState() => _CompanionDiscoveryPageState();
}

class _CompanionDiscoveryPageState extends State<CompanionDiscoveryPage> {
  bool loading = true;
  String? error;
  List<TripModel> trips = [];
  List<CompanionMatchModel> matches = [];
  String? selectedTripId;
  String statusFilter = 'ALL';

  List<CompanionMatchModel> get filteredMatches => matches.where((match) {
    if (statusFilter == 'ALL') return true;
    if (statusFilter == 'RUNNING') {
      return match.status == 'RUNNING' || match.status == 'ONGOING';
    }
    return match.status == statusFilter;
  }).toList();

  /// 下拉菜单始终把当前行程放在第一项，其他行程保持接口返回顺序。
  List<TripModel> get orderedTrips {
    final selected = selectedTripId;
    if (selected == null) return trips;
    return [
      ...trips.where((trip) => trip.id == selected),
      ...trips.where((trip) => trip.id != selected),
    ];
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => loadTrips());
  }

  Future<void> loadTrips() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      trips = await TripService(context.read<AppSession>().api).mine();
      if (trips.isNotEmpty) {
        if (selectedTripId == null ||
            !trips.any((trip) => trip.id == selectedTripId)) {
          selectedTripId = trips.first.id;
        }
        await loadMatches();
      }
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> loadMatches() async {
    final id = selectedTripId;
    if (id == null) return;
    final result = await CompanionMatchService(
      context.read<AppSession>().api,
    ).recommendations(id);
    if (!mounted || selectedTripId != id) return;
    matches = result;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFF7F9FC),
    appBar: AppBar(
      title: const Text('发现同行'),
      backgroundColor: const Color(0xFFF7F9FC),
    ),
    body: AsyncPanel(
      loading: loading,
      error: error,
      onRetry: loadTrips,
      child: RefreshIndicator(
        onRefresh: loadTrips,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
          children: [
            _Hero(matches: matches.length),
            const SizedBox(height: 18),
            if (trips.isEmpty)
              const _EmptyTrip()
            else ...[
              const Text(
                '以哪段行程寻找同行',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 8),
              DropdownButtonFormField<String>(
                initialValue: selectedTripId,
                isExpanded: true,
                decoration: InputDecoration(
                  filled: true,
                  fillColor: Colors.white,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(16),
                    borderSide: BorderSide.none,
                  ),
                ),
                items: orderedTrips
                    .map(
                      (t) => DropdownMenuItem(
                        value: t.id,
                        child: Text(
                          '${t.startName} → ${t.endName}',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(),
                onChanged: (value) async {
                  selectedTripId = value;
                  setState(() {
                    loading = true;
                    matches = [];
                    error = null;
                  });
                  try {
                    await loadMatches();
                  } catch (e) {
                    error = e.toString();
                  }
                  if (mounted) setState(() => loading = false);
                },
              ),
              const SizedBox(height: 24),
              Wrap(
                spacing: 8,
                children:
                    const {'ALL': '全部', 'PUBLISHED': '招募中', 'RUNNING': '进行中'}
                        .entries
                        .map(
                          (entry) => ChoiceChip(
                            label: Text(entry.value),
                            selected: statusFilter == entry.key,
                            onSelected: (_) =>
                                setState(() => statusFilter = entry.key),
                          ),
                        )
                        .toList(),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      '为你匹配',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  Text(
                    '${filteredMatches.length} 条结果',
                    style: const TextStyle(color: AppColors.secondaryText),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              if (filteredMatches.isEmpty)
                const _EmptyMatch()
              else
                ...filteredMatches.map(
                  (m) => _MatchCard(
                    match: m,
                    onTap: () async {
                      await Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => CompanionMatchDetailPage(
                            matchId: m.matchId,
                            initial: m,
                          ),
                        ),
                      );
                      await loadTrips();
                    },
                  ),
                ),
            ],
          ],
        ),
      ),
    ),
  );
}

class _Hero extends StatelessWidget {
  const _Hero({required this.matches});
  final int matches;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      gradient: const LinearGradient(
        colors: [Color(0xFF285CFF), Color(0xFF5B83FF)],
      ),
      borderRadius: BorderRadius.circular(24),
    ),
    child: Row(
      children: [
        const CircleAvatar(
          radius: 28,
          backgroundColor: Colors.white24,
          child: Icon(LucideIcons.usersRound, color: Colors.white, size: 28),
        ),
        const SizedBox(width: 15),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '一路有伴，风景更近',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 5),
              Text(
                matches == 0 ? '发布公开行程后，系统会自动匹配路线' : '已找到 $matches 组路线相近的车友',
                style: const TextStyle(color: Colors.white70),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _MatchCard extends StatelessWidget {
  const _MatchCard({required this.match, required this.onTap});
  final CompanionMatchModel match;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(22),
      child: TlxCard(
        color: Colors.white,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    match.title,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: match.matchScore >= 80
                        ? const Color(0xFFEAF8F2)
                        : AppColors.primarySoft,
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: Text(
                    match.status == 'PUBLISHED'
                        ? '招募中 · ${match.matchScore}%'
                        : '进行中 · ${match.matchScore}%',
                    style: TextStyle(
                      color: match.matchScore >= 80
                          ? const Color(0xFF059669)
                          : AppColors.primary,
                      fontWeight: FontWeight.w700,
                      fontSize: 12,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 15),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Column(
                  children: [
                    Icon(
                      LucideIcons.circleDot,
                      size: 16,
                      color: AppColors.primary,
                    ),
                    SizedBox(height: 8),
                    SizedBox(height: 18, child: VerticalDivider(width: 1)),
                    SizedBox(height: 8),
                    Icon(
                      LucideIcons.mapPin,
                      size: 17,
                      color: Color(0xFFFF7A45),
                    ),
                  ],
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    '${match.startName}\n\n${match.endName}',
                    style: const TextStyle(height: 1.25),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              '出发 ${match.departureTime}  ·  相差 ${_gap(match.departureGapMinutes)}',
              style: const TextStyle(
                color: AppColors.secondaryText,
                fontSize: 12,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Icon(
                  match.joinable ? LucideIcons.users : LucideIcons.route,
                  size: 16,
                  color: AppColors.primary,
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    match.joinable
                        ? '车队 ${match.currentMembers ?? 1}/${match.maxMembers ?? '-'} 人，可申请加入'
                        : '尚未组建车队，可先查看行程',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.secondaryText,
                      fontSize: 12,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                const Icon(LucideIcons.chevronRight, size: 18),
              ],
            ),
          ],
        ),
      ),
    ),
  );
  static String _gap(int minutes) =>
      minutes < 60 ? '$minutes 分钟' : '${(minutes / 60).toStringAsFixed(1)} 小时';
}

class CompanionMatchDetailPage extends StatefulWidget {
  const CompanionMatchDetailPage({
    required this.matchId,
    required this.initial,
    super.key,
  });
  final String matchId;
  final CompanionMatchModel initial;
  @override
  State<CompanionMatchDetailPage> createState() =>
      _CompanionMatchDetailPageState();
}

class _CompanionMatchDetailPageState extends State<CompanionMatchDetailPage> {
  late CompanionMatchModel match = widget.initial;
  bool loading = true;
  bool applying = false;
  String? error;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    try {
      match = await CompanionMatchService(
        context.read<AppSession>().api,
      ).detail(widget.matchId);
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> apply() async {
    setState(() => applying = true);
    try {
      await CompanionMatchService(
        context.read<AppSession>().api,
      ).apply(widget.matchId);
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('申请已提交，等待队长审核')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
    if (mounted) setState(() => applying = false);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('同行详情')),
    body: AsyncPanel(
      loading: loading,
      error: error,
      onRetry: load,
      child: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          Center(
            child: Stack(
              alignment: Alignment.center,
              children: [
                SizedBox(
                  width: 112,
                  height: 112,
                  child: CircularProgressIndicator(
                    value: match.matchScore / 100,
                    strokeWidth: 9,
                    backgroundColor: AppColors.primarySoft,
                  ),
                ),
                Column(
                  children: [
                    Text(
                      '${match.matchScore}%',
                      style: const TextStyle(
                        fontSize: 26,
                        fontWeight: FontWeight.w800,
                        color: AppColors.primary,
                      ),
                    ),
                    const Text('综合匹配'),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 28),
          TlxCard(
            color: const Color(0xFFF6F8FC),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  match.title,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 18),
                _Info(
                  icon: LucideIcons.route,
                  label: '同行路线',
                  value: '${match.startName} → ${match.endName}',
                ),
                _Info(
                  icon: LucideIcons.calendarClock,
                  label: '出发时间',
                  value: match.departureTime,
                ),
                _Info(
                  icon: LucideIcons.mapPinned,
                  label: '路线重合',
                  value: '${match.overlapRate}%',
                ),
                _Info(
                  icon: LucideIcons.users,
                  label: '车队状态',
                  value: match.joinable
                      ? '${match.currentMembers ?? 1}/${match.maxMembers ?? '-'} 人，开放申请'
                      : '暂未开放车队',
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          const Text(
            '匹配说明',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          const Text(
            '系统按照路线相似度、目的地、出发时间和同行偏好综合计算。提交申请后，由对方队长审核；通过后可进入车队群聊。',
            style: TextStyle(color: AppColors.secondaryText, height: 1.7),
          ),
        ],
      ),
    ),
    bottomNavigationBar: SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: FilledButton.icon(
          onPressed: match.joinable && !applying ? apply : null,
          icon: Icon(
            match.joinable ? LucideIcons.userPlus : LucideIcons.clock3,
          ),
          label: Text(
            match.joinable ? (applying ? '提交中…' : '申请加入车队') : '等待对方组建车队',
          ),
        ),
      ),
    ),
  );
}

class _Info extends StatelessWidget {
  const _Info({required this.icon, required this.label, required this.value});
  final IconData icon;
  final String label, value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: Row(
      children: [
        Icon(icon, color: AppColors.primary, size: 20),
        const SizedBox(width: 12),
        SizedBox(
          width: 68,
          child: Text(
            label,
            style: const TextStyle(color: AppColors.secondaryText),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
        ),
      ],
    ),
  );
}

class _EmptyTrip extends StatelessWidget {
  const _EmptyTrip();
  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.symmetric(vertical: 60),
    child: Column(
      children: [
        Icon(LucideIcons.route, size: 46, color: AppColors.muted),
        SizedBox(height: 14),
        Text(
          '先发布一段公开行程',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
        ),
        SizedBox(height: 6),
        Text(
          '系统会在发布时自动生成同行推荐',
          style: TextStyle(color: AppColors.secondaryText),
        ),
      ],
    ),
  );
}

class _EmptyMatch extends StatelessWidget {
  const _EmptyMatch();
  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.symmetric(vertical: 40),
    child: Center(
      child: Text(
        '暂未找到合适同行，稍后再来看看',
        style: TextStyle(color: AppColors.secondaryText),
      ),
    ),
  );
}
