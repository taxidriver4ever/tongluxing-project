import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import 'vehicle_add_page.dart';
import 'vehicle_auth_status_page.dart';

class VehicleListPage extends StatefulWidget {
  const VehicleListPage({super.key});
  @override
  State<VehicleListPage> createState() => _VehicleListPageState();
}

class _VehicleListPageState extends State<VehicleListPage> {
  bool loading = true;
  String? error;
  List<VehicleModel> rows = [];
  VehicleAuthStatusModel? authStatus;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final service = VehicleService(context.read<AppSession>().api);
      final values = await Future.wait([service.mine(), service.authStatus()]);
      rows = values[0] as List<VehicleModel>;
      authStatus = values[1] as VehicleAuthStatusModel;
      error = null;
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> setDefault(VehicleModel vehicle) async {
    try {
      await VehicleService(
        context.read<AppSession>().api,
      ).setDefault(vehicle.id);
      await load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('我的车辆')),
    body: AsyncPanel(
      loading: loading,
      error: error,
      onRetry: load,
      child: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          const Text(
            '管理你的车辆信息，设置常用车辆',
            style: TextStyle(color: AppColors.muted),
          ),
          const SizedBox(height: 22),
          _authOverview(context),
          const SizedBox(height: 18),
          ...rows.map(
            (vehicle) => Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: TlxCard(
                color: const Color(0xFFF8FAFD),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Container(
                          width: 90,
                          height: 70,
                          decoration: BoxDecoration(
                            color: AppColors.primarySoft,
                            borderRadius: BorderRadius.circular(16),
                          ),
                          child: const Icon(
                            LucideIcons.carFront,
                            color: AppColors.primary,
                            size: 34,
                          ),
                        ),
                        const SizedBox(width: 14),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '${vehicle.brand} ${vehicle.model}',
                                style: const TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              const SizedBox(height: 5),
                              Text(
                                vehicle.plate,
                                style: const TextStyle(
                                  color: AppColors.secondaryText,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                _status(vehicle.status),
                                style: const TextStyle(
                                  fontSize: 12,
                                  color: AppColors.primary,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const Icon(LucideIcons.chevronRight),
                      ],
                    ),
                    if (!vehicle.isDefault) ...[
                      const SizedBox(height: 14),
                      OutlinedButton(
                        onPressed: () => setDefault(vehicle),
                        child: const Text('设为主要车辆'),
                      ),
                    ] else
                      const Padding(
                        padding: EdgeInsets.only(top: 12),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            '目前主要车辆',
                            style: TextStyle(
                              color: AppColors.primary,
                              fontSize: 13,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          FilledButton.icon(
            onPressed: () async {
              final changed = await Navigator.push<bool>(
                context,
                MaterialPageRoute(builder: (_) => const VehicleAddPage()),
              );
              if (changed == true) load();
            },
            icon: const Icon(LucideIcons.plus),
            label: const Text('添加车辆'),
          ),
        ],
      ),
    ),
  );

  Widget _authOverview(BuildContext context) {
    final status = authStatus?.status ?? 'UNSUBMITTED';
    final meta = switch (status) {
      'PENDING' => (
        '车辆认证办理中',
        '材料已提交，等待管理端办理',
        LucideIcons.clock3,
        AppColors.warning,
      ),
      'PASS' => (
        '车辆认证已通过',
        '认证信息已同步完成',
        LucideIcons.badgeCheck,
        AppColors.success,
      ),
      'REJECT' => (
        '车辆认证被驳回',
        authStatus?.reason ?? '请修改材料后重新提交',
        LucideIcons.circleX,
        AppColors.danger,
      ),
      _ => (
        '车辆尚未认证',
        '提交证件及车辆照片开始认证',
        LucideIcons.shieldQuestion,
        AppColors.primary,
      ),
    };
    return InkWell(
      borderRadius: BorderRadius.circular(20),
      onTap: () async {
        await Navigator.push(
          context,
          MaterialPageRoute(builder: (_) => const VehicleAuthStatusPage()),
        );
        load();
      },
      child: TlxCard(
        color: const Color(0xFFF5F8FF),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: meta.$4.withValues(alpha: .12),
                shape: BoxShape.circle,
              ),
              child: Icon(meta.$3, color: meta.$4),
            ),
            const SizedBox(width: 13),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    meta.$1,
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    meta.$2,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(LucideIcons.chevronRight, color: AppColors.muted),
          ],
        ),
      ),
    );
  }

  String _status(String status) => switch (status) {
    'APPROVED' => '认证通过',
    'PENDING' => '审核中',
    'REJECTED' => '认证驳回',
    _ => '未认证',
  };
}
