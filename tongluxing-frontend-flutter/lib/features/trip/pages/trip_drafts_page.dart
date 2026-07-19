import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../widgets/trip_route_preview.dart';
import 'trip_create_page.dart';

class TripDraftsPage extends StatefulWidget {
  const TripDraftsPage({super.key});
  @override
  State<TripDraftsPage> createState() => _TripDraftsPageState();
}

class _TripDraftsPageState extends State<TripDraftsPage> {
  bool loading = true;
  String? error;
  List<TripDraftModel> drafts = [];
  TripService get service => TripService(context.read<AppSession>().api);
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      drafts = await service.drafts();
      error = null;
    } catch (e) {
      error = '$e';
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> remove(TripDraftModel draft) async {
    final yes =
        await showDialog<bool>(
          context: context,
          builder: (_) => AlertDialog(
            title: const Text('删除草稿？'),
            content: Text(
              '“${draft.title.isEmpty ? '未命名行程' : draft.title}”删除后无法恢复。',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('取消'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('删除'),
              ),
            ],
          ),
        ) ??
        false;
    if (!yes) return;
    await service.deleteDraft(draft.id);
    await load();
  }

  Future<void> edit(TripDraftModel draft) async {
    final full = await service.draftDetail(draft.id);
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => TripCreatePage(draft: full)),
    );
    await load();
  }

  Future<void> detail(TripDraftModel draft) async {
    final full = await service.draftDetail(draft.id);
    if (!mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) =>
            _TripDraftDetailPage(draft: full, onEdit: () => edit(full)),
      ),
    );
    await load();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('我的草稿')),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : error != null
        ? Center(child: Text(error!))
        : RefreshIndicator(
            onRefresh: load,
            child: drafts.isEmpty
                ? ListView(
                    children: const [
                      SizedBox(height: 180),
                      Icon(
                        LucideIcons.filePenLine,
                        size: 54,
                        color: AppColors.muted,
                      ),
                      SizedBox(height: 14),
                      Center(child: Text('暂无草稿')),
                    ],
                  )
                : ListView.builder(
                    padding: const EdgeInsets.all(18),
                    itemCount: drafts.length,
                    itemBuilder: (_, i) {
                      final d = drafts[i];
                      return Card(
                        margin: const EdgeInsets.only(bottom: 14),
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                d.title.isEmpty ? '未命名行程' : d.title,
                                style: const TextStyle(
                                  fontSize: 17,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                '${d.startLocation?.name ?? '未选起点'} → ${d.destination?.name ?? '未选终点'}',
                                style: const TextStyle(
                                  color: AppColors.secondaryText,
                                ),
                              ),
                              const SizedBox(height: 5),
                              Text(
                                '最后编辑：${d.updatedAt ?? '-'}',
                                style: const TextStyle(
                                  fontSize: 12,
                                  color: AppColors.muted,
                                ),
                              ),
                              const SizedBox(height: 12),
                              Row(
                                children: [
                                  TextButton(
                                    onPressed: () => detail(d),
                                    child: const Text('详情'),
                                  ),
                                  TextButton(
                                    onPressed: () => edit(d),
                                    child: const Text('修改'),
                                  ),
                                  const Spacer(),
                                  IconButton(
                                    onPressed: () => remove(d),
                                    icon: const Icon(
                                      LucideIcons.trash2,
                                      color: AppColors.danger,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
  );
}

class _TripDraftDetailPage extends StatelessWidget {
  const _TripDraftDetailPage({required this.draft, required this.onEdit});
  final TripDraftModel draft;
  final VoidCallback onEdit;
  @override
  Widget build(BuildContext context) {
    final points = [
      if (draft.startLocation != null) draft.startLocation!,
      ...draft.waypoints.map((e) => e.location),
      if (draft.destination != null) draft.destination!,
    ];
    return Scaffold(
      appBar: AppBar(title: const Text('草稿详情')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(
            draft.title.isEmpty ? '未命名行程' : draft.title,
            style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text(
            '最后编辑：${draft.updatedAt ?? '-'}',
            style: const TextStyle(color: AppColors.muted),
          ),
          const SizedBox(height: 20),
          TripRoutePreview(points: points, route: draft.route),
          const SizedBox(height: 18),
          Text(draft.description.isEmpty ? '暂无行程说明' : draft.description),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: () {
              Navigator.pop(context);
              onEdit();
            },
            icon: const Icon(LucideIcons.filePenLine),
            label: const Text('修改草稿'),
          ),
        ],
      ),
    );
  }
}
