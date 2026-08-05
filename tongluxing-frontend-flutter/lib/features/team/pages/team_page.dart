import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/app_services.dart';

/// P0 队长管理页：所有数据来自真实车队、成员和异常接口，不再保留演示成员。
class TeamPage extends StatefulWidget {
  const TeamPage({super.key});

  @override
  State<TeamPage> createState() => _TeamPageState();
}

class _TeamPageState extends State<TeamPage> {
  Map<String, dynamic>? team;
  List<Map<String, dynamic>> members = const [];
  List<Map<String, dynamic>> alerts = const [];
  bool loading = true;
  bool saving = false;
  String? error;

  TeamP0Service get service => TeamP0Service(context.read<AppSession>().api);
  P0TripStateService get stateService =>
      P0TripStateService(context.read<AppSession>().api);

  bool get owner => team?['currentUserOwner'] == true;
  String get teamId => team?['teamId']?.toString() ?? '';
  String get tripId => team?['tripId']?.toString() ?? '';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    if (mounted) {
      setState(() {
        loading = true;
        error = null;
      });
    }
    try {
      final current = await service.current();
      final id = current?['teamId']?.toString() ?? '';
      final memberRows = id.isEmpty ? <Map<String, dynamic>>[] : await service.members(id);
      final alertRows = current?['currentUserOwner'] == true &&
              current?['tripId'] != null
          ? await stateService.teamAlerts(current!['tripId'].toString())
          : <Map<String, dynamic>>[];
      if (!mounted) return;
      setState(() {
        team = current;
        members = memberRows;
        alerts = alertRows;
      });
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> updateSettings(Map<String, dynamic> values) async {
    if (!owner || saving) return;
    setState(() => saving = true);
    try {
      final updated = await service.updateSettings(teamId, values);
      if (!mounted) return;
      setState(() => team = updated);
      _message('车队设置已更新');
    } catch (e) {
      _message('$e');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Future<void> removeMember(Map<String, dynamic> member) async {
    final userId = member['userId']?.toString() ?? '';
    if (!owner || userId.isEmpty) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('移除成员'),
        content: Text('确定将 ${member['nicknameSnapshot'] ?? '该成员'} 移出队伍吗？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确认移除'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await service.removeMember(teamId, userId);
      await load();
      _message('成员已移除，可重新提交归队申请');
    } catch (e) {
      _message('$e');
    }
  }

  /// 被乘客选择的车主确认或拒绝同车关系，结果会立即刷新真实成员数据。
  Future<void> confirmPassengerVehicle(
    Map<String, dynamic> member,
    bool approved,
  ) async {
    final passengerUserId = member['userId']?.toString() ?? '';
    if (passengerUserId.isEmpty) return;
    try {
      await service.confirmPassengerVehicle(teamId, passengerUserId, approved);
      await load();
      _message(approved ? '已确认乘客关联车辆' : '已拒绝乘客关联车辆');
    } catch (e) {
      _message('$e');
    }
  }

  Future<void> handleAlert(Map<String, dynamic> alert, String action) async {
    try {
      await stateService.handleTeamAlert(alert['id'].toString(), action);
      await load();
      _message(action == 'REMOVE' ? '成员已移除' : '异常已忽略');
    } catch (e) {
      _message('$e');
    }
  }

  Future<void> exitTeam() async {
    if (owner) {
      _message('队长不能直接退出，请在群管理中解散群聊并结束行程');
      return;
    }
    try {
      await service.exit(teamId);
      if (!mounted) return;
      setState(() {
        team = null;
        members = const [];
        alerts = const [];
      });
      _message('已退出车队');
    } catch (e) {
      _message('$e');
    }
  }

  void _message(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    if (loading && team == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (team == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('我的车队')),
        body: RefreshIndicator(
          onRefresh: load,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(24),
            children: [
              const SizedBox(height: 100),
              const Icon(LucideIcons.users, size: 58, color: AppColors.muted),
              const SizedBox(height: 16),
              Text(
                error ?? '当前没有作为队长或成员加入的有效车队',
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.secondaryText),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      body: RefreshIndicator(
        onRefresh: load,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverAppBar(
              pinned: true,
              expandedHeight: 220,
              foregroundColor: Colors.white,
              backgroundColor: const Color(0xFF285CFF),
              actions: [
                if (owner)
                  IconButton(
                    tooltip: '队长设置',
                    onPressed: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => TeamManagePage(
                          team: Map<String, dynamic>.from(team!),
                          onSave: updateSettings,
                        ),
                      ),
                    ),
                    icon: const Icon(LucideIcons.settings),
                  ),
              ],
              flexibleSpace: FlexibleSpaceBar(
                background: Container(
                  padding: const EdgeInsets.fromLTRB(22, 92, 22, 24),
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      colors: [Color(0xFF1948C8), Color(0xFF3A86FF)],
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const CircleAvatar(
                        radius: 27,
                        backgroundColor: Colors.white24,
                        child: Icon(LucideIcons.carFront, color: Colors.white),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        team!['teamName']?.toString() ?? '行程车队',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 24,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      Text(
                        '${owner ? '我的身份：队长' : '我的身份：队员'} · ${members.length}/${team!['maxMemberCount'] ?? '-'} 人',
                        style: const TextStyle(color: Colors.white70),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.all(18),
              sliver: SliverList.list(
                children: [
                  _TeamStatusCard(team: team!, owner: owner),
                  if (owner && alerts.isNotEmpty) ...[
                    const SizedBox(height: 18),
                    const Text(
                      '需要处理的成员异常',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 10),
                    ...alerts.map(
                      (alert) => _AlertCard(
                        alert: alert,
                        onIgnore: () => handleAlert(alert, 'IGNORE'),
                        onRemove: () => handleAlert(alert, 'REMOVE'),
                      ),
                    ),
                  ],
                  const SizedBox(height: 18),
                  Row(
                    children: [
                      const Expanded(
                        child: Text(
                          '真实成员',
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                        ),
                      ),
                      Text('${members.length} 人'),
                    ],
                  ),
                  const SizedBox(height: 8),
                  if (members.isEmpty)
                    const TlxCard(child: Text('暂无有效成员'))
                  else
                    ...members.map(
                      (member) => Card(
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: AppColors.primarySoft,
                            child: Text(
                              (member['nicknameSnapshot']?.toString() ?? '车')
                                  .characters
                                  .first,
                            ),
                          ),
                          title: Text(
                            member['nicknameSnapshot']?.toString() ?? '同路行车友',
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          subtitle: Text(_memberSubtitle(member)),
                          trailing: _memberActions(member),
                        ),
                      ),
                    ),
                  const SizedBox(height: 20),
                  OutlinedButton(
                    onPressed: saving ? null : exitTeam,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.danger,
                    ),
                    child: Text(owner ? '队长需解散群聊后结束行程' : '退出车队'),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _memberSubtitle(Map<String, dynamic> member) {
    final parts = <String>[
      _roleLabel(member['memberRole']),
      member['vehicleSnapshot']?.toString().isNotEmpty == true
          ? member['vehicleSnapshot'].toString()
          : '乘客',
    ];
    final plate = member['plateReference']?.toString() ?? '';
    if (plate.isNotEmpty) parts.add('关联车牌 $plate');
    final confirm = member['ownerConfirmStatus']?.toString();
    if (confirm == 'PENDING') parts.add('等待车主确认');
    if (confirm == 'CONFIRMED') parts.add('车辆已确认');
    if (confirm == 'REJECTED') parts.add('车辆关联已拒绝');
    return parts.join(' · ');
  }

  Widget? _memberActions(Map<String, dynamic> member) {
    final currentUserId = context.read<AppSession>().userId;
    final canConfirm = member['ownerConfirmStatus'] == 'PENDING' &&
        member['linkedOwnerUserId']?.toString() == currentUserId;
    final canRemove = owner && member['canRemove'] == true;
    if (!canConfirm && !canRemove) return null;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (canConfirm) ...[
          IconButton(
            tooltip: '确认乘客关联车辆',
            onPressed: () => confirmPassengerVehicle(member, true),
            icon: const Icon(LucideIcons.circleCheck, color: AppColors.success),
          ),
          IconButton(
            tooltip: '拒绝乘客关联车辆',
            onPressed: () => confirmPassengerVehicle(member, false),
            icon: const Icon(LucideIcons.circleX, color: AppColors.warning),
          ),
        ],
        if (canRemove)
          IconButton(
            tooltip: '移除成员',
            onPressed: () => removeMember(member),
            icon: const Icon(LucideIcons.userRoundX, color: AppColors.danger),
          ),
      ],
    );
  }

  String _roleLabel(dynamic value) => switch (value?.toString()) {
    'OWNER' => '队长',
    'DRIVER' => '车主队员',
    _ => '乘客队员',
  };
}

class _TeamStatusCard extends StatelessWidget {
  const _TeamStatusCard({required this.team, required this.owner});
  final Map<String, dynamic> team;
  final bool owner;

  @override
  Widget build(BuildContext context) => TlxCard(
    color: const Color(0xFFF8FAFD),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '${team['startName'] ?? '起点'} → ${team['endName'] ?? '终点'}',
          style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 8),
        Text('招募：${_recruitment(team['recruitmentStatus'])}'),
        Text('途中加入：${team['allowMidwayJoin'] == true ? '允许' : '关闭'}'),
        Text('一级脱队：${_km(team['deviationWarningDistanceM'])} / ${team['deviationWarningMinutes'] ?? 30} 分钟'),
        Text('严重脱队：${_km(team['severeDeviationDistanceM'])} / ${team['severeDeviationMinutes'] ?? 60} 分钟'),
        Text('失联提醒：${team['missingLocationMinutes'] ?? 720} 分钟'),
        if (owner) ...[
          const SizedBox(height: 8),
          const Text(
            '系统只发现异常，是否移除由队长决定。',
            style: TextStyle(color: AppColors.secondaryText, fontSize: 12),
          ),
        ],
      ],
    ),
  );

  static String _km(dynamic meters) {
    final value = (meters as num?)?.toDouble() ?? 0;
    return '${(value / 1000).toStringAsFixed(value % 1000 == 0 ? 0 : 1)}km';
  }

  static String _recruitment(dynamic value) => switch (value?.toString()) {
    'PAUSED' => '已暂停',
    'CLOSED' => '已关闭',
    _ => '招募中',
  };
}

class _AlertCard extends StatelessWidget {
  const _AlertCard({
    required this.alert,
    required this.onIgnore,
    required this.onRemove,
  });
  final Map<String, dynamic> alert;
  final VoidCallback onIgnore;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) => Card(
    color: const Color(0xFFFFF5F3),
    child: Padding(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${_label(alert['alertLevel'])} · 成员 ${alert['memberUserId']}',
            style: const TextStyle(fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 5),
          Text('距离：${alert['distanceM'] ?? 0} 米 · 开始于 ${alert['startedAt'] ?? '-'}'),
          const SizedBox(height: 10),
          Row(
            children: [
              TextButton(onPressed: onIgnore, child: const Text('忽略')),
              const Spacer(),
              FilledButton.tonal(
                onPressed: onRemove,
                child: const Text('移除成员'),
              ),
            ],
          ),
        ],
      ),
    ),
  );

  static String _label(dynamic value) => switch (value?.toString()) {
    'MISSING' => '长时间失联',
    'SEVERE' => '严重脱队',
    _ => '一级脱队',
  };
}

/// 队长调整 P0 队伍参数。每个保存字段都由后端再次校验范围。
class TeamManagePage extends StatefulWidget {
  const TeamManagePage({required this.team, required this.onSave, super.key});
  final Map<String, dynamic> team;
  final Future<void> Function(Map<String, dynamic>) onSave;

  @override
  State<TeamManagePage> createState() => _TeamManagePageState();
}

class _TeamManagePageState extends State<TeamManagePage> {
  late String recruitment;
  late bool midway;
  late String privacy;
  late final warningDistance = TextEditingController();
  late final warningMinutes = TextEditingController();
  late final severeDistance = TextEditingController();
  late final severeMinutes = TextEditingController();
  late final missingMinutes = TextEditingController();
  late final joinRadius = TextEditingController();
  bool saving = false;

  @override
  void initState() {
    super.initState();
    recruitment = widget.team['recruitmentStatus']?.toString() ?? 'OPEN';
    midway = widget.team['allowMidwayJoin'] == true;
    privacy = widget.team['privacyLevel']?.toString() ?? 'STANDARD';
    warningDistance.text = '${widget.team['deviationWarningDistanceM'] ?? 50000}';
    warningMinutes.text = '${widget.team['deviationWarningMinutes'] ?? 30}';
    severeDistance.text = '${widget.team['severeDeviationDistanceM'] ?? 100000}';
    severeMinutes.text = '${widget.team['severeDeviationMinutes'] ?? 60}';
    missingMinutes.text = '${widget.team['missingLocationMinutes'] ?? 720}';
    joinRadius.text = '${widget.team['joinRadiusM'] ?? 100000}';
  }

  @override
  void dispose() {
    warningDistance.dispose();
    warningMinutes.dispose();
    severeDistance.dispose();
    severeMinutes.dispose();
    missingMinutes.dispose();
    joinRadius.dispose();
    super.dispose();
  }

  Future<void> save() async {
    setState(() => saving = true);
    try {
      await widget.onSave({
        'recruitmentStatus': recruitment,
        'allowMidwayJoin': midway,
        'privacyLevel': privacy,
        'deviationWarningDistanceM': int.parse(warningDistance.text),
        'deviationWarningMinutes': int.parse(warningMinutes.text),
        'severeDeviationDistanceM': int.parse(severeDistance.text),
        'severeDeviationMinutes': int.parse(severeMinutes.text),
        'missingLocationMinutes': int.parse(missingMinutes.text),
        'joinRadiusM': int.parse(joinRadius.text),
      });
      if (mounted) Navigator.pop(context);
    } catch (_) {
      // 父页面统一展示后端校验错误。
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('队长管理设置')),
    body: ListView(
      padding: const EdgeInsets.all(18),
      children: [
        DropdownButtonFormField<String>(
          initialValue: recruitment,
          decoration: const InputDecoration(labelText: '招募状态'),
          items: const [
            DropdownMenuItem(value: 'OPEN', child: Text('招募中')),
            DropdownMenuItem(value: 'PAUSED', child: Text('暂停招募')),
            DropdownMenuItem(value: 'CLOSED', child: Text('关闭招募')),
          ],
          onChanged: (value) => setState(() => recruitment = value ?? 'OPEN'),
        ),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: const Text('允许途中加入'),
          subtitle: const Text('开启后，行进中的队伍仍可接收入队申请'),
          value: midway,
          onChanged: (value) => setState(() => midway = value),
        ),
        DropdownButtonFormField<String>(
          initialValue: privacy,
          decoration: const InputDecoration(labelText: '队伍隐私级别'),
          items: const [
            DropdownMenuItem(value: 'OPEN', child: Text('公开')),
            DropdownMenuItem(value: 'STANDARD', child: Text('标准脱敏')),
            DropdownMenuItem(value: 'PRIVATE', child: Text('严格隐私')),
          ],
          onChanged: (value) => setState(() => privacy = value ?? 'STANDARD'),
        ),
        const SizedBox(height: 16),
        _number(warningDistance, '一级脱队距离（米）'),
        _number(warningMinutes, '一级脱队持续时间（分钟）'),
        _number(severeDistance, '严重脱队距离（米）'),
        _number(severeMinutes, '严重脱队持续时间（分钟）'),
        _number(missingMinutes, '失联时间（分钟）'),
        _number(joinRadius, '加入/自动出发范围（米）'),
        const SizedBox(height: 20),
        FilledButton(
          onPressed: saving ? null : save,
          child: Text(saving ? '保存中…' : '保存队伍设置'),
        ),
      ],
    ),
  );

  Widget _number(TextEditingController controller, String label) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextField(
      controller: controller,
      keyboardType: TextInputType.number,
      decoration: InputDecoration(labelText: label),
    ),
  );
}
