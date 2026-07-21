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
  String filter = 'ALL';
  String? operatingVehicleId;

  List<VehicleModel> get filteredRows => filter == 'ALL'
      ? rows
      : rows.where((vehicle) => vehicle.status == filter).toList();

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
    await _runVehicleAction(
      vehicle,
      () => VehicleService(
        context.read<AppSession>().api,
      ).setDefault(vehicle.id),
      successMessage: '已设为主要车辆',
    );
  }

  Future<void> _confirmRemoveApproved(VehicleModel vehicle) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('移除认证车辆'),
        content: Text(
          '确定要移除 ${vehicle.brand} ${vehicle.model}（${vehicle.plate}）吗？\n\n移除后，该车辆将从你的车辆列表中隐藏，不能继续用于行程和车队；已通过认证记录会保留，用于防止相同车辆重复认证。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.danger),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确定移除'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await _runVehicleAction(
      vehicle,
      () => VehicleService(context.read<AppSession>().api).remove(vehicle.id),
      successMessage: '车辆已移除',
    );
  }

  Future<void> _confirmDeleteRejected(VehicleModel vehicle) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('删除驳回记录'),
        content: Text(
          '确定删除 ${vehicle.brand} ${vehicle.model}（${vehicle.plate}）的认证驳回记录吗？\n\n删除后可以重新添加车辆并提交新的认证材料。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.danger),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('删除记录'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await _runVehicleAction(
      vehicle,
      () => VehicleService(
        context.read<AppSession>().api,
      ).deleteRejectedHistory(vehicle.id),
      successMessage: '驳回记录已删除',
    );
  }

  Future<void> _runVehicleAction(
    VehicleModel vehicle,
    Future<void> Function() action, {
    required String successMessage,
  }) async {
    setState(() => operatingVehicleId = vehicle.id);
    try {
      await action();
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(successMessage)));
      await load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => operatingVehicleId = null);
    }
  }

  Future<void> _showRejectDetail(VehicleModel vehicle) => showDialog<void>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: const Row(
        children: [
          Icon(LucideIcons.circleX, color: AppColors.danger),
          SizedBox(width: 10),
          Text('认证驳回详情'),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${vehicle.brand} ${vehicle.model}',
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 4),
            Text(
              vehicle.plate,
              style: const TextStyle(color: AppColors.muted),
            ),
            const SizedBox(height: 18),
            const Text(
              '驳回原因',
              style: TextStyle(fontSize: 13, color: AppColors.muted),
            ),
            const SizedBox(height: 7),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: const Color(0xFFFFF2F2),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: const Color(0xFFFFD7D7)),
              ),
              child: Text(
                vehicle.rejectReason.trim().isEmpty
                    ? '后台暂未填写具体驳回原因，请联系客服核实。'
                    : vehicle.rejectReason.trim(),
                style: const TextStyle(height: 1.55),
              ),
            ),
            if (vehicle.certificationId?.isNotEmpty == true) ...[
              const SizedBox(height: 14),
              Text(
                '认证申请编号：${vehicle.certificationId}',
                style: const TextStyle(fontSize: 12, color: AppColors.muted),
              ),
            ],
            if (vehicle.certificationSubmittedAt != null) ...[
              const SizedBox(height: 5),
              Text(
                '提交时间：${_formatDateTime(vehicle.certificationSubmittedAt!)}',
                style: const TextStyle(fontSize: 12, color: AppColors.muted),
              ),
            ],
            if (vehicle.certificationReviewedAt != null) ...[
              const SizedBox(height: 5),
              Text(
                '审核时间：${_formatDateTime(vehicle.certificationReviewedAt!)}',
                style: const TextStyle(fontSize: 12, color: AppColors.muted),
              ),
            ],
          ],
        ),
      ),
      actions: [
        FilledButton(
          onPressed: () => Navigator.pop(dialogContext),
          child: const Text('知道了'),
        ),
      ],
    ),
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('我的车辆')),
    body: AsyncPanel(
      loading: loading,
      error: error,
      onRetry: load,
      child: RefreshIndicator(
        onRefresh: load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(22),
          children: [
            const Text(
              '管理你的车辆信息，设置常用车辆',
              style: TextStyle(color: AppColors.muted),
            ),
            const SizedBox(height: 22),
            _authOverview(context),
            const SizedBox(height: 18),
            _statusFilters(),
            const SizedBox(height: 18),
            if (filteredRows.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 36),
                child: Center(
                  child: Text(
                    '当前筛选条件下暂无车辆',
                    style: TextStyle(color: AppColors.muted),
                  ),
                ),
              ),
            ...filteredRows.map(_vehicleCard),
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
    ),
  );

  Widget _vehicleCard(VehicleModel vehicle) {
    final operating = operatingVehicleId == vehicle.id;
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: TlxCard(
        color: const Color(0xFFF8FAFD),
        child: Column(
          children: [
            Row(
              children: [
                Container(
                  width: 82,
                  height: 66,
                  decoration: BoxDecoration(
                    color: AppColors.primarySoft,
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: const Icon(
                    LucideIcons.carFront,
                    color: AppColors.primary,
                    size: 32,
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
                        style: TextStyle(
                          fontSize: 12,
                          color: _statusColor(vehicle.status),
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (vehicle.status == 'REJECTED') ...[
              const SizedBox(height: 14),
              InkWell(
                onTap: () => _showRejectDetail(vehicle),
                borderRadius: BorderRadius.circular(14),
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(13),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFFF2F2),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: const Color(0xFFFFD7D7)),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(
                        LucideIcons.info,
                        size: 18,
                        color: AppColors.danger,
                      ),
                      const SizedBox(width: 9),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              '驳回原因',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.danger,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              vehicle.rejectReason.trim().isEmpty
                                  ? '后台暂未填写具体原因，点击查看详情'
                                  : vehicle.rejectReason.trim(),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(fontSize: 13, height: 1.4),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Icon(
                        LucideIcons.chevronRight,
                        size: 17,
                        color: AppColors.danger,
                      ),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(height: 14),
            if (operating)
              const SizedBox(
                height: 36,
                child: Center(
                  child: SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                ),
              )
            else
              _vehicleActions(vehicle),
          ],
        ),
      ),
    );
  }

  Widget _vehicleActions(VehicleModel vehicle) {
    if (vehicle.status == 'APPROVED') {
      return Wrap(
        spacing: 10,
        runSpacing: 8,
        alignment: WrapAlignment.start,
        children: [
          if (!vehicle.isDefault)
            OutlinedButton(
              onPressed: () => setDefault(vehicle),
              child: const Text('设为主要车辆'),
            )
          else
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 10),
              child: Text(
                '目前主要车辆',
                style: TextStyle(
                  color: AppColors.primary,
                  fontSize: 13,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: AppColors.danger,
              side: const BorderSide(color: Color(0xFFFFC7C7)),
            ),
            onPressed: () => _confirmRemoveApproved(vehicle),
            icon: const Icon(LucideIcons.trash2, size: 17),
            label: const Text('移除车辆'),
          ),
        ],
      );
    }
    if (vehicle.status == 'REJECTED') {
      return Row(
        children: [
          Expanded(
            child: OutlinedButton(
              onPressed: () => _showRejectDetail(vehicle),
              child: const Text('查看驳回详情'),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                foregroundColor: AppColors.danger,
                side: const BorderSide(color: Color(0xFFFFC7C7)),
              ),
              onPressed: () => _confirmDeleteRejected(vehicle),
              icon: const Icon(LucideIcons.trash2, size: 17),
              label: const Text('删除记录'),
            ),
          ),
        ],
      );
    }
    if (vehicle.status == 'PENDING') {
      return const Align(
        alignment: Alignment.centerLeft,
        child: Text(
          '材料正在审核中，审核期间不能删除车辆',
          style: TextStyle(color: AppColors.muted, fontSize: 12),
        ),
      );
    }
    return const Align(
      alignment: Alignment.centerLeft,
      child: Text(
        '完成认证后可设为主要车辆',
        style: TextStyle(color: AppColors.muted, fontSize: 12),
      ),
    );
  }

  Widget _statusFilters() {
    const filters = [
      ('ALL', '全部'),
      ('PENDING', '认证中'),
      ('APPROVED', '认证通过'),
      ('REJECTED', '认证驳回'),
    ];
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: filters
            .map(
              (item) => Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(
                  label: Text(item.$2),
                  selected: filter == item.$1,
                  showCheckmark: false,
                  onSelected: (_) => setState(() => filter = item.$1),
                ),
              ),
            )
            .toList(),
      ),
    );
  }

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

  Color _statusColor(String status) => switch (status) {
    'APPROVED' => AppColors.success,
    'PENDING' => AppColors.warning,
    'REJECTED' => AppColors.danger,
    _ => AppColors.primary,
  };

  static String _formatDateTime(DateTime value) {
    final local = value.toLocal();
    String two(int v) => v.toString().padLeft(2, '0');
    return '${local.year}-${two(local.month)}-${two(local.day)} '
        '${two(local.hour)}:${two(local.minute)}';
  }
}
