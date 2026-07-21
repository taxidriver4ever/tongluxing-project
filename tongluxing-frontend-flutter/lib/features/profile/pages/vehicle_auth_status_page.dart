import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import 'vehicle_add_page.dart';

class VehicleAuthStatusPage extends StatefulWidget {
  const VehicleAuthStatusPage({super.key});

  @override
  State<VehicleAuthStatusPage> createState() => _VehicleAuthStatusPageState();
}

class _VehicleAuthStatusPageState extends State<VehicleAuthStatusPage> {
  VehicleAuthStatusModel? status;
  bool loading = true;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      status = await VehicleService(
        context.read<AppSession>().api,
      ).authStatus();
      error = null;
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('车辆认证进度')),
    body: AsyncPanel(
      loading: loading,
      error: error,
      onRetry: load,
      child: RefreshIndicator(
        onRefresh: load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(22),
          children: [_statusCard(), const SizedBox(height: 18), _timeline()],
        ),
      ),
    ),
  );

  Widget _statusCard() {
    final value = status?.status ?? 'UNSUBMITTED';
    final meta = switch (value) {
      'PENDING' => (
        '资料办理中',
        '管理员正在核对行驶证和车辆照片',
        LucideIcons.clock3,
        AppColors.warning,
      ),
      'PASS' => (
        '车辆认证已通过',
        '认证结果已同步到我的车辆',
        LucideIcons.badgeCheck,
        AppColors.success,
      ),
      'REJECT' => (
        '车辆认证未通过',
        status?.reason.isNotEmpty == true ? status!.reason : '请修改材料后重新提交',
        LucideIcons.circleX,
        AppColors.danger,
      ),
      _ => (
        '尚未提交认证',
        '提交完整材料后可进入审核流程',
        LucideIcons.shieldQuestion,
        AppColors.muted,
      ),
    };
    return TlxCard(
      color: const Color(0xFFF8FAFD),
      child: Column(
        children: [
          Container(
            width: 68,
            height: 68,
            decoration: BoxDecoration(
              color: meta.$4.withValues(alpha: .12),
              shape: BoxShape.circle,
            ),
            child: Icon(meta.$3, color: meta.$4, size: 34),
          ),
          const SizedBox(height: 16),
          Text(
            meta.$1,
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text(
            meta.$2,
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.muted, height: 1.5),
          ),
          if (status?.applyId != null) ...[
            const SizedBox(height: 14),
            Text(
              '申请编号 ${status!.applyId}',
              style: const TextStyle(
                color: AppColors.secondaryText,
                fontSize: 12,
              ),
            ),
          ],
          if (value == 'UNSUBMITTED' || value == 'REJECT') ...[
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () async {
                  final changed = await Navigator.push<bool>(
                    context,
                    MaterialPageRoute(builder: (_) => const VehicleAddPage()),
                  );
                  if (changed == true) load();
                },
                child: Text(value == 'REJECT' ? '重新提交认证' : '开始车辆认证'),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _timeline() {
    final value = status?.status ?? 'UNSUBMITTED';
    return TlxCard(
      color: Colors.white,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            '审核流程',
            style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 16),
          _step('提交认证材料', status?.submitted == true, _time(status?.submitTime)),
          _step(
            '平台办理审核',
            value == 'PASS' || value == 'REJECT',
            value == 'PENDING' ? '办理中' : '等待办理',
          ),
          _step(
            '同步认证结果',
            value == 'PASS',
            value == 'REJECT' ? '材料被驳回' : _time(status?.auditTime),
          ),
          const SizedBox(height: 8),
          const Text(
            '下拉页面可刷新最新审核结果',
            style: TextStyle(color: AppColors.muted, fontSize: 12),
          ),
        ],
      ),
    );
  }

  Widget _step(String title, bool completed, String subtitle) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: Row(
      children: [
        Icon(
          completed ? LucideIcons.circleCheck : LucideIcons.circle,
          color: completed ? AppColors.success : AppColors.muted,
          size: 22,
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            title,
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
        ),
        Text(
          subtitle,
          style: const TextStyle(color: AppColors.muted, fontSize: 12),
        ),
      ],
    ),
  );

  String _time(DateTime? value) {
    if (value == null) return '等待完成';
    String two(int number) => number.toString().padLeft(2, '0');
    return '${two(value.month)}-${two(value.day)} ${two(value.hour)}:${two(value.minute)}';
  }
}
