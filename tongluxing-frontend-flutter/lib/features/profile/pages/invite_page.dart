import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/app_services.dart';

class InvitePage extends StatefulWidget {
  const InvitePage({super.key});
  @override
  State<InvitePage> createState() => _InvitePageState();
}

class _InvitePageState extends State<InvitePage> {
  bool loading = true;
  String? error;
  Map<String, dynamic> code = {};
  Map<String, dynamic> summary = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final service = InviteService(context.read<AppSession>().api);
      final values = await Future.wait([service.code(), service.summary()]);
      code = values[0];
      summary = values[1];
      error = null;
    } catch (e) {
      error = '$e';
    }
    if (mounted) setState(() => loading = false);
  }

  String get inviteCode =>
      code['inviteCode']?.toString() ?? code['code']?.toString() ?? '—';

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('邀请好友')),
    body: AsyncPanel(
      loading: loading,
      error: error,
      onRetry: load,
      child: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.primary,
              borderRadius: BorderRadius.circular(26),
            ),
            child: Column(
              children: [
                const Icon(
                  LucideIcons.usersRound,
                  color: Colors.white,
                  size: 56,
                ),
                const SizedBox(height: 14),
                const Text(
                  '邀请好友一起出发',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  '好友完成注册后，你将获得成长值',
                  style: TextStyle(color: Colors.white70),
                ),
                const SizedBox(height: 22),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 14,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: Text(
                    inviteCode,
                    style: const TextStyle(
                      fontSize: 26,
                      letterSpacing: 4,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          TlxCard(
            color: const Color(0xFFF8FAFD),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _Metric(
                  label: '已邀请',
                  value:
                      '${summary['inviteCount'] ?? summary['invitedCount'] ?? summary['totalCount'] ?? 0}',
                ),
                _Metric(
                  label: '已生效',
                  value:
                      '${summary['effectiveCount'] ?? summary['completedCount'] ?? 0}',
                ),
                _Metric(
                  label: '成长奖励',
                  value:
                      '${summary['rewardAmount'] ?? summary['rewardExperience'] ?? summary['rewardValue'] ?? 0}',
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          FilledButton.icon(
            onPressed: () => ScaffoldMessenger.of(
              context,
            ).showSnackBar(SnackBar(content: Text('邀请码 $inviteCode 已准备分享'))),
            icon: const Icon(LucideIcons.share2),
            label: const Text('分享给好友'),
          ),
        ],
      ),
    ),
  );
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value});
  final String label, value;
  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(
        value,
        style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
      ),
      const SizedBox(height: 5),
      Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 12)),
    ],
  );
}
