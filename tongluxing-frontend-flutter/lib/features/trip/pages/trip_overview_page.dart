import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import 'trip_detail_page.dart';

class TripOverviewPage extends StatefulWidget {
  const TripOverviewPage({required this.historyMode, super.key});
  final bool historyMode;

  @override
  State<TripOverviewPage> createState() => _TripOverviewPageState();
}

class _TripOverviewPageState extends State<TripOverviewPage> {
  bool loading = true;
  String? error;
  List<TripModel> active = [];
  List<TripModel> history = [];
  late String filter = widget.historyMode ? 'FINISHED' : 'PUBLISHED';

  @override
  void initState() {
    super.initState();
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
    final source = filter == 'CANCELLED' || widget.historyMode
        ? history
        : active;
    return source.where((trip) {
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

  @override
  Widget build(BuildContext context) {
    final options = widget.historyMode
        ? const {'FINISHED': '已完成', 'SETTLED': '已结算'}
        : const {'PUBLISHED': '招募中', 'RUNNING': '进行中', 'CANCELLED': '已取消'};
    return Scaffold(
      appBar: AppBar(title: Text(widget.historyMode ? '历史与结算' : '我的行程')),
      body: RefreshIndicator(
        onRefresh: load,
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Wrap(
              spacing: 8,
              children: options.entries
                  .map(
                    (entry) => ChoiceChip(
                      label: Text(entry.value),
                      selected: filter == entry.key,
                      onSelected: (_) => setState(() => filter = entry.key),
                    ),
                  )
                  .toList(),
            ),
            const SizedBox(height: 18),
            if (loading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(40),
                  child: CircularProgressIndicator(),
                ),
              )
            else if (error != null)
              Center(child: Text(error!))
            else if (visible.isEmpty)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(40),
                  child: Text('当前栏目暂无行程'),
                ),
              )
            else
              ...visible.map(
                (trip) => Card(
                  margin: const EdgeInsets.only(bottom: 12),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
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
                            Text(
                              _label(trip.status),
                              style: const TextStyle(color: AppColors.primary),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Text('${trip.startName} → ${trip.endName}'),
                        const SizedBox(height: 6),
                        Text(
                          trip.departureTime ?? '待确定',
                          style: const TextStyle(
                            color: AppColors.secondaryText,
                          ),
                        ),
                        const SizedBox(height: 10),
                        Row(
                          children: [
                            TextButton.icon(
                              onPressed: () => Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => TripDetailPage(
                                    tripId: trip.id,
                                    initial: trip,
                                  ),
                                ),
                              ),
                              icon: const Icon(LucideIcons.eye),
                              label: const Text('查看'),
                            ),
                            const Spacer(),
                            if (trip.status == 'PUBLISHED')
                              OutlinedButton.icon(
                                onPressed: () => cancel(trip),
                                icon: const Icon(LucideIcons.x),
                                label: const Text('取消行程'),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  String _label(String status) => switch (status) {
    'PUBLISHED' => '招募中',
    'RUNNING' || 'ONGOING' => '进行中',
    'CANCELLED' => '已取消',
    'SETTLED' => '已结算',
    _ => '已完成',
  };
}
