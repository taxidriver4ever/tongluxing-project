import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import 'trip_detail_page.dart';

class TripOverviewPage extends StatefulWidget {
  const TripOverviewPage({
    required this.historyMode,
    this.initialFilter,
    super.key,
  });
  final bool historyMode;
  final String? initialFilter;

  @override
  State<TripOverviewPage> createState() => _TripOverviewPageState();
}

class _TripOverviewPageState extends State<TripOverviewPage> {
  bool loading = true;
  String? error;
  List<TripModel> active = [];
  List<TripModel> history = [];
  late String filter;

  @override
  void initState() {
    super.initState();
    filter =
        widget.initialFilter ??
        (widget.historyMode ? 'FINISHED' : 'PUBLISHED');
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final service = TripService(context.read<AppSession>().api);
      final values = await Future.wait([
        service.mine(),
        service.mine(scope: 'history'),
      ]);
      active = values[0];
      history = values[1];
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  List<TripModel> get visible {
    final source =
        filter == 'CANCELLED' ||
            filter == 'FINISHED' ||
            filter == 'SETTLED' ||
            widget.historyMode
        ? history
        : active;
    return source.where((trip) {
      if (filter == 'PUBLISHED') {
        return trip.status == 'PUBLISHED' || trip.status == 'RECRUITING';
      }
      if (filter == 'READY') {
        return trip.status == 'READY' ||
            trip.status == 'CONFIRMING' ||
            trip.status == 'WAITING';
      }
      if (filter == 'RUNNING')
        return trip.status == 'RUNNING' || trip.status == 'ONGOING';
      if (filter == 'FINISHED')
        return trip.status == 'FINISHED' || trip.status == 'ENDED';
      return trip.status == filter;
    }).toList();
  }

  Future<void> cancel(TripModel trip) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('取消这段行程？'),
        content: Text('“${trip.title}”取消后会进入已取消栏目，招募和对应群聊将不再继续。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('暂不取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('确认取消'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    try {
      await TripService(context.read<AppSession>().api).cancel(trip.id);
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('行程已取消')));
      await load();
      if (mounted) setState(() => filter = 'CANCELLED');
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  Future<void> openDetail(TripModel trip) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TripDetailPage(tripId: trip.id, initial: trip),
      ),
    );
    if (mounted) await load();
  }

  @override
  Widget build(BuildContext context) {
    final options = widget.historyMode
        ? const {'FINISHED': '已完成', 'SETTLED': '已结算'}
        : const {
            'PUBLISHED': '招募中',
            'READY': '待出发',
            'RUNNING': '进行中',
            'CANCELLED': '已取消',
          };
    return Scaffold(
      backgroundColor: const Color(0xFFF4F7FB),
      appBar: AppBar(
        title: Text(widget.historyMode ? '历史与结算' : '我的行程'),
      ),
      body: RefreshIndicator(
        onRefresh: load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(14, 12, 14, 24),
          children: [
            _StatusTabBar(
              options: options,
              selected: filter,
              onChanged: (value) => setState(() => filter = value),
            ),
            const SizedBox(height: 12),
            if (loading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(40),
                  child: CircularProgressIndicator(),
                ),
              )
            else if (error != null)
              _OverviewEmpty(
                icon: LucideIcons.wifiOff,
                title: '加载失败',
                subtitle: error!,
              )
            else if (visible.isEmpty)
              const _OverviewEmpty(
                icon: LucideIcons.calendarDays,
                title: '当前栏目暂无行程',
                subtitle: '其他状态的行程可以在上方切换查看',
              )
            else
              ...visible.map(
                (trip) => _TripOverviewCard(
                  trip: trip,
                  statusLabel: _label(trip.status),
                  onTap: () => openDetail(trip),
                  onCancel: trip.status == 'PUBLISHED'
                      ? () => cancel(trip)
                      : null,
                ),
              ),
          ],
        ),
      ),
    );
  }

  String _label(String status) => switch (status) {
    'PUBLISHED' || 'RECRUITING' => '招募中',
    'READY' || 'CONFIRMING' || 'WAITING' => '待出发',
    'RUNNING' || 'ONGOING' => '进行中',
    'CANCELLED' => '已取消',
    'SETTLED' => '已结算',
    _ => '已完成',
  };
}

class _StatusTabBar extends StatelessWidget {
  const _StatusTabBar({
    required this.options,
    required this.selected,
    required this.onChanged,
  });

  final Map<String, String> options;
  final String selected;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(4),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(13),
      border: Border.all(color: const Color(0xFFE2E9F2)),
    ),
    child: Row(
      children: options.entries
          .map(
            (entry) => Expanded(
              child: InkWell(
                onTap: () => onChanged(entry.key),
                borderRadius: BorderRadius.circular(10),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 160),
                  height: 36,
                  alignment: Alignment.center,
                  decoration: BoxDecoration(
                    color: selected == entry.key
                        ? AppColors.primarySoft
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    entry.value,
                    style: TextStyle(
                      color: selected == entry.key
                          ? AppColors.primary
                          : AppColors.secondaryText,
                      fontSize: 12.5,
                      fontWeight: selected == entry.key
                          ? FontWeight.w900
                          : FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ),
          )
          .toList(),
    ),
  );
}

class _TripOverviewCard extends StatelessWidget {
  const _TripOverviewCard({
    required this.trip,
    required this.statusLabel,
    required this.onTap,
    this.onCancel,
  });

  final TripModel trip;
  final String statusLabel;
  final VoidCallback onTap;
  final VoidCallback? onCancel;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 10),
    child: Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(15),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(14, 13, 10, 12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
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
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.primarySoft,
                      borderRadius: BorderRadius.circular(99),
                    ),
                    child: Text(
                      statusLabel,
                      style: const TextStyle(
                        color: AppColors.primary,
                        fontSize: 10.5,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  if (onCancel != null)
                    PopupMenuButton<String>(
                      padding: EdgeInsets.zero,
                      icon: const Icon(
                        LucideIcons.ellipsisVertical,
                        size: 18,
                        color: AppColors.muted,
                      ),
                      onSelected: (value) {
                        if (value == 'cancel') onCancel!();
                      },
                      itemBuilder: (_) => const [
                        PopupMenuItem(
                          value: 'cancel',
                          child: Row(
                            children: [
                              Icon(
                                LucideIcons.circleX,
                                size: 17,
                                color: AppColors.danger,
                              ),
                              SizedBox(width: 8),
                              Text('取消行程'),
                            ],
                          ),
                        ),
                      ],
                    ),
                ],
              ),
              const SizedBox(height: 9),
              Row(
                children: [
                  const Icon(
                    LucideIcons.route,
                    size: 14,
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
                        fontSize: 12.5,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  const Icon(
                    LucideIcons.calendarDays,
                    size: 14,
                    color: AppColors.muted,
                  ),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      _overviewDateLabel(trip.departureTime),
                      style: const TextStyle(
                        color: AppColors.muted,
                        fontSize: 11.5,
                      ),
                    ),
                  ),
                  const Text(
                    '查看详情',
                    style: TextStyle(
                      color: AppColors.primary,
                      fontSize: 11.5,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(width: 2),
                  const Icon(
                    LucideIcons.chevronRight,
                    size: 16,
                    color: AppColors.primary,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _OverviewEmpty extends StatelessWidget {
  const _OverviewEmpty({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 34),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(15),
    ),
    child: Column(
      children: [
        Icon(icon, size: 34, color: AppColors.muted),
        const SizedBox(height: 11),
        Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
        const SizedBox(height: 5),
        Text(
          subtitle,
          textAlign: TextAlign.center,
          maxLines: 3,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(color: AppColors.muted, fontSize: 12),
        ),
      ],
    ),
  );
}

String _overviewDateLabel(String? raw) {
  final value = DateTime.tryParse(raw ?? '');
  if (value == null) return '出发时间待确定';
  String two(int number) => number.toString().padLeft(2, '0');
  return '${value.year}-${two(value.month)}-${two(value.day)} '
      '${two(value.hour)}:${two(value.minute)} 出发';
}
