import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import 'trip_navigation_page.dart';
import '../widgets/trip_route_preview.dart';
import '../../chat/pages/chat_session_page.dart';

class TripDetailPage extends StatefulWidget {
  const TripDetailPage({required this.tripId, super.key, this.initial});
  final String tripId;
  final TripModel? initial;
  @override
  State<TripDetailPage> createState() => _TripDetailPageState();
}

class _TripDetailPageState extends State<TripDetailPage> {
  TripModel? trip;
  bool loading = false;
  String? error;
  bool settling = false;
  @override
  void initState() {
    super.initState();
    trip = widget.initial;
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      trip = await TripService(
        context.read<AppSession>().api,
      ).detail(widget.tripId);
      error = null;
    } catch (e) {
      if (trip == null) error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> start() async {
    try {
      final service = TripService(context.read<AppSession>().api);
      final state = await service.activeState();
      if (!mounted) return;
      final runningTripId = state['tripId']?.toString();
      if (state['active'] == true &&
          runningTripId != null &&
          runningTripId.isNotEmpty &&
          runningTripId != widget.tripId) {
        final openCurrent = await showDialog<bool>(
              context: context,
              builder: (dialogContext) => AlertDialog(
                title: const Text('已有进行中的行程'),
                content: Text(
                  state['message']?.toString() ?? '同一时间只能进行一个行程。',
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(dialogContext, false),
                    child: const Text('取消'),
                  ),
                  FilledButton(
                    onPressed: () => Navigator.pop(dialogContext, true),
                    child: const Text('查看当前行程'),
                  ),
                ],
              ),
            ) ??
            false;
        if (openCurrent && mounted) {
          Navigator.pushReplacement(
            context,
            MaterialPageRoute(
              builder: (_) => TripDetailPage(tripId: runningTripId),
            ),
          );
        }
        return;
      }
      trip = await service.start(widget.tripId);
      if (!mounted) return;
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (_) => TripNavigationPage(trip: trip!)),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  Future<void> settle() async {
    setState(() => settling = true);
    try {
      final result = await TripService(
        context.read<AppSession>().api,
      ).settle(widget.tripId);
      await load();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            result.duplicate
                ? '该行程已经完成结算'
                : '结算完成，每位有效成员 +${result.pointsPerMember} 成长值',
          ),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => settling = false);
    }
  }

  Future<void> openChat() async {
    try {
      final conversation = await ChatService(
        context.read<AppSession>().api,
      ).tripConversation(widget.tripId);
      if (!mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => ChatSessionPage(conversation: conversation),
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final currentUserId = context.watch<AppSession>().userId;
    final isOwner = trip?.ownerUserId != null && trip!.ownerUserId == currentUserId;
    return Scaffold(
      body: AsyncPanel(
      loading: loading && trip == null,
      error: error,
      onRetry: load,
      child: trip == null
          ? const SizedBox()
          : CustomScrollView(
              slivers: [
                SliverAppBar(
                  expandedHeight: 285,
                  pinned: true,
                  foregroundColor: Colors.white,
                  backgroundColor: const Color(0xFF285CFF),
                  flexibleSpace: FlexibleSpaceBar(
                    background: Container(
                      padding: const EdgeInsets.fromLTRB(22, 88, 22, 22),
                      decoration: const BoxDecoration(
                        color: Color(0xFF285CFF),
                        borderRadius: BorderRadius.vertical(
                          bottom: Radius.circular(32),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            trip!.title,
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 25,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            '${trip!.startName} → ${trip!.endName}',
                            style: const TextStyle(color: Colors.white70),
                          ),
                          const Spacer(),
                          Row(
                            children: [
                              const _GlassAction(
                                icon: LucideIcons.users,
                                label: '成员',
                              ),
                              _GlassAction(
                                icon: LucideIcons.messageCircle,
                                label: '进入群聊',
                                onTap: openChat,
                              ),
                              const _GlassAction(
                                icon: LucideIcons.route,
                                label: '路线',
                              ),
                              const _GlassAction(
                                icon: LucideIcons.share2,
                                label: '分享',
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                SliverPadding(
                  padding: const EdgeInsets.all(22),
                  sliver: SliverList.list(
                    children: [
                      const Text(
                        '行程信息',
                        style: TextStyle(
                          fontSize: 19,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 12),
                      TlxCard(
                        color: const Color(0xFFF6F8FC),
                        child: Column(
                          children: [
                            _Info(
                              label: '状态',
                              value: tripStatusLabel(trip!.status),
                            ),
                            _Info(
                              label: '出发时间',
                              value: trip!.departureTime ?? '待确定',
                            ),
                            _Info(
                              label: '车辆',
                              value:
                                  '${trip!.joinedVehicles} / ${trip!.maxVehicles}',
                            ),
                            _Info(
                              label: '预计距离',
                              value: trip!.distanceMeters == null
                                  ? '待规划'
                                  : '${(trip!.distanceMeters! / 1000).toStringAsFixed(0)} km',
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 22),
                      const Text(
                        '路线与经停点',
                        style: TextStyle(
                          fontSize: 19,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 12),
                      if (trip!.startLocation != null &&
                          trip!.endLocation != null)
                        TripRoutePreview(
                          points: [
                            trip!.startLocation!,
                            ...trip!.waypoints,
                            trip!.endLocation!,
                          ],
                        )
                      else
                        TlxCard(
                          color: const Color(0xFFF6F8FC),
                          child: Text(
                            [
                              trip!.startName,
                              ...trip!.waypoints.map((e) => '途经 · ${e.name}'),
                              trip!.endName,
                            ].join('\n↓\n'),
                            style: const TextStyle(height: 1.6),
                          ),
                        ),
                      const SizedBox(height: 24),
                      if (isOwner &&
                          (trip!.status == 'RUNNING' ||
                              trip!.status == 'ONGOING'))
                        FilledButton(
                          onPressed: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => TripNavigationPage(trip: trip!),
                            ),
                          ),
                          child: const Text('继续导航'),
                        )
                      else if (isOwner &&
                          (trip!.status == 'FINISHED' ||
                              trip!.status == 'ENDED'))
                        FilledButton.icon(
                          onPressed: settling ? null : settle,
                          icon: const Icon(LucideIcons.sparkles),
                          label: Text(settling ? '正在结算…' : '结算成长值'),
                        )
                      else if (isOwner && trip!.status == 'SETTLED')
                        Container(
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            color: const Color(0xFFE8F7EE),
                            borderRadius: BorderRadius.circular(16),
                          ),
                          child: const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(
                                LucideIcons.circleCheck,
                                color: Color(0xFF16A05D),
                              ),
                              SizedBox(width: 8),
                              Text(
                                '行程与成长值均已结算',
                                style: TextStyle(fontWeight: FontWeight.w700),
                              ),
                            ],
                          ),
                        )
                      else if (isOwner &&
                          const ['PUBLISHED', 'READY', 'CONFIRMING']
                              .contains(trip!.status))
                        FilledButton(
                          onPressed: start,
                          child: const Text('开启行程'),
                        ),
                    ],
                  ),
                ),
              ],
            ),
      ),
    );
  }
}

class _GlassAction extends StatelessWidget {
  const _GlassAction({required this.icon, required this.label, this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  @override
  Widget build(BuildContext context) => Expanded(
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(18),
      child: Container(
        height: 72,
        margin: const EdgeInsets.symmetric(horizontal: 4),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: .16),
          border: Border.all(color: Colors.white30),
          borderRadius: BorderRadius.circular(18),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, color: Colors.white, size: 22),
            const SizedBox(height: 5),
            Text(
              label,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 10.5,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _Info extends StatelessWidget {
  const _Info({required this.label, required this.value});
  final String label;
  final String value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Row(
      children: [
        Expanded(
          child: Text(
            label,
            style: const TextStyle(color: AppColors.secondaryText),
          ),
        ),
        Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
      ],
    ),
  );
}
