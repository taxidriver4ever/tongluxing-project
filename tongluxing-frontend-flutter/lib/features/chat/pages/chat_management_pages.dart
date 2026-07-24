import 'dart:convert';
import 'package:amap_map/amap_map.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import 'package:x_amap_base/x_amap_base.dart';
import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../profile/pages/profile_system_pages.dart';
import '../../profile/widgets/user_avatar.dart';
import '../../trip/pages/trip_detail_page.dart';

class ChatGroupDetailsPage extends StatefulWidget {
  const ChatGroupDetailsPage({required this.conversation, super.key});
  final ConversationModel conversation;
  @override
  State<ChatGroupDetailsPage> createState() => _ChatGroupDetailsPageState();
}

class _ChatGroupDetailsPageState extends State<ChatGroupDetailsPage> {
  List<Map<String, dynamic>> members = [];
  Map<String, dynamic> workspace = {};
  bool muted = false, pinned = false, loading = true;
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final s = ChatService(context.read<AppSession>().api);
    final values = await Future.wait([
      s.members(widget.conversation.id),
      s.settings(widget.conversation.id),
      s.groupWorkspace(widget.conversation.id),
    ]);
    members = values[0] as List<Map<String, dynamic>>;
    final set = values[1] as Map<String, dynamic>;
    workspace = values[2] as Map<String, dynamic>;
    muted = set['muted'] == true;
    pinned = set['pinned'] == true;
    if (mounted) setState(() => loading = false);
  }

  Future<void> update({bool? nextMuted, bool? nextPinned}) async {
    muted = nextMuted ?? muted;
    pinned = nextPinned ?? pinned;
    setState(() {});
    await ChatService(
      context.read<AppSession>().api,
    ).updateSettings(widget.conversation.id, muted: muted, pinned: pinned);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(toolbarHeight: 48, title: const Text('群聊详情', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800))),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : ListView(
            padding: const EdgeInsets.fromLTRB(12, 6, 12, 24),
            children: [
              _TripSummary(workspace: workspace),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '群成员（${members.length}）',
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  Text(
                    '点击头像查看资料',
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade500),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 10,
                runSpacing: 12,
                children: members.map((m) {
                  final vehicles =
                      (workspace['memberVehicles'] as List? ?? const [])
                          .where(
                            (v) =>
                                (v as Map)['userId']?.toString() ==
                                m['userId']?.toString(),
                          )
                          .toList();
                  final vehicle = vehicles.isEmpty
                      ? null
                      : Map<String, dynamic>.from(vehicles.first as Map);
                  return SizedBox(
                    width: 56,
                    child: InkWell(
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) =>
                              PublicProfilePage(userId: m['userId'].toString()),
                        ),
                      ),
                      child: Column(
                        children: [
                          UserAvatar(
                            nickname: m['nickname']?.toString() ?? '同路行用户',
                            avatarImageKey:
                                m['avatarImageKey']?.toString() ?? '',
                            avatarUrl: m['avatarUrl']?.toString() ?? '',
                            radius: 22,
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => PublicProfilePage(
                                  userId: m['userId'].toString(),
                                ),
                              ),
                            ).then((_) => load()),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            m['nickname']?.toString() ?? '同路行用户',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontSize: 11),
                          ),
                          if (m['memberRole'] == 'OWNER')
                            const Text(
                              '队长',
                              style: TextStyle(
                                fontSize: 10,
                                color: AppColors.primary,
                              ),
                            ),
                          if (m['memberRole'] == 'NAVIGATOR')
                            const Text(
                              '领航员',
                              style: TextStyle(
                                fontSize: 10,
                                color: Color(0xFFE58B00),
                              ),
                            ),
                          if (m['memberRole'] == 'ADMIN')
                            const Text(
                              '管理员',
                              style: TextStyle(
                                fontSize: 10,
                                color: Color(0xFF16A36A),
                              ),
                            ),
                          Text(
                            '${m['totalTripCount'] ?? 0}次 · ${(((m['totalDistanceMeters'] ?? 0) as num) / 1000).toStringAsFixed(0)}km',
                            style: const TextStyle(
                              fontSize: 9,
                              color: AppColors.muted,
                            ),
                          ),
                          if (vehicle != null)
                            Text(
                              '${vehicle['brand'] ?? ''}${vehicle['model'] ?? ''}',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 9,
                                color: AppColors.muted,
                              ),
                            ),
                        ],
                      ),
                    ),
                  );
                }).toList(),
              ),
              const SizedBox(height: 16),
              const Text(
                '群聊工具',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 12),
              GridView.count(
                crossAxisCount: 2,
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                mainAxisExtent: 78,
                children: [
                  _Tool(
                    icon: LucideIcons.route,
                    label: '行程信息',
                    onTap: () {
                      final tripId =
                          workspace['tripId']?.toString() ??
                          widget.conversation.bizId;
                      if (tripId.isEmpty) return;
                      _open(TripDetailPage(tripId: tripId));
                    },
                  ),
                  _Tool(
                    icon: LucideIcons.mapPinned,
                    label: '实时位置',
                    onTap: () => _open(
                      ChatLocationSharePage(conversation: widget.conversation),
                    ),
                  ),
                  _Tool(
                    icon: LucideIcons.megaphone,
                    label: '群公告',
                    onTap: () => _open(
                      ChatItemsPage(
                        conversation: widget.conversation,
                        type: 'ANNOUNCEMENT',
                        title: '群公告',
                      ),
                    ),
                  ),
                  _Tool(
                    icon: LucideIcons.listChecks,
                    label: '投票',
                    onTap: () => _open(
                      ChatItemsPage(
                        conversation: widget.conversation,
                        type: 'POLL',
                        title: '群投票',
                      ),
                    ),
                  ),
                  _Tool(
                    icon: LucideIcons.alarmClock,
                    label: '行程提醒',
                    onTap: () => _open(
                      ChatItemsPage(
                        conversation: widget.conversation,
                        type: 'REMINDER',
                        title: '行程提醒',
                      ),
                    ),
                  ),
                  if (workspace['selfRole'] == 'OWNER')
                    _Tool(
                      icon: LucideIcons.carFront,
                      label: '确认行程',
                      onTap: _createTripConfirmation,
                    ),
                ],
              ),
              const SizedBox(height: 14),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.danger,
                    alignment: Alignment.centerLeft,
                    padding: const EdgeInsets.symmetric(
                      horizontal: 18,
                      vertical: 15,
                    ),
                  ),
                  onPressed: () => _open(
                    ChatReportPage(
                      conversation: widget.conversation,
                      members: members,
                    ),
                  ),
                  icon: const Icon(LucideIcons.shieldAlert),
                  label: const Text('举报与违规反馈'),
                ),
              ),
              const SizedBox(height: 20),
              Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Column(
                  children: [
                    _SettingSwitchRow(
                      value: muted,
                      onChanged: (v) => update(nextMuted: v),
                      title: '消息免打扰',
                      icon: LucideIcons.bellOff,
                    ),
                    const Divider(height: 1),
                    _SettingSwitchRow(
                      value: pinned,
                      onChanged: (v) => update(nextPinned: v),
                      title: '置顶聊天',
                      icon: LucideIcons.pin,
                    ),
                    const Divider(height: 1),
                    ListTile(
                      dense: true,
                      leading: const Icon(LucideIcons.trash2, size: 20, color: AppColors.danger),
                      title: const Text('清空聊天记录', style: TextStyle(fontSize: 14, color: AppColors.danger)),
                      subtitle: const Text('仅清除当前账号看到的记录', style: TextStyle(fontSize: 11)),
                      onTap: _clearMessages,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              if (workspace['selfRole'] == 'OWNER') ...[
                OutlinedButton.icon(
                  onPressed: _manageMembers,
                  icon: const Icon(LucideIcons.usersRound),
                  label: const Text('管理群成员'),
                ),
                const SizedBox(height: 5),
                OutlinedButton.icon(
                  onPressed: _rename,
                  icon: const Icon(LucideIcons.pencil),
                  label: const Text('修改群名称'),
                ),
                const SizedBox(height: 8),
                OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.danger,
                  ),
                  onPressed: _closeGroup,
                  icon: const Icon(LucideIcons.logOut),
                  label: const Text('结束群聊'),
                ),
                const SizedBox(height: 16),
              ],
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFF7E8),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Text(
                  '群聊与当前行程绑定。行程结束后仅标记为“已结束”，群聊继续保留并允许成员交流；风险消息仍按平台规则留存。',
                  style: TextStyle(color: Color(0xFF8B5A00), height: 1.5),
                ),
              ),
            ],
          ),
  );

  Future<void> _clearMessages() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('清空聊天记录？'),
        content: const Text('该操作仅清除你当前账号看到的聊天记录，无法恢复，不影响其他成员和后台风控留档。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.danger),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('确认清空'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await ChatService(context.read<AppSession>().api)
        .clearLocalMessages(widget.conversation.id);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('聊天记录已清空')),
      );
    }
  }

  Future<void> _open(Widget page) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    await load();
  }

  Future<void> _createTripConfirmation() async {
    if (!const ['PUBLISHED', 'READY'].contains(workspace['tripStatus'])) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('行程确认仅用于待开始行程；当前行程已开始或已结束')));
      return;
    }
    await ChatService(
      context.read<AppSession>().api,
    ).createTripConfirmation(widget.conversation.id);
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('行程确认卡片已发送到群聊')));
    await load();
  }

  Future<void> _rename() async {
    final controller = TextEditingController(
      text: workspace['conversationName']?.toString(),
    );
    final name = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('修改群名称'),
        content: TextField(
          controller: controller,
          maxLength: 64,
          decoration: const InputDecoration(
            labelText: '群名称',
            contentPadding: EdgeInsets.fromLTRB(16, 18, 16, 16),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    // 等待弹窗退场动画结束，避免取消时 TextField 仍在绘制却提前销毁控制器。
    await Future<void>.delayed(const Duration(milliseconds: 250));
    controller.dispose();
    if (name?.isNotEmpty != true || !mounted) return;
    await ChatService(
      context.read<AppSession>().api,
    ).renameGroup(widget.conversation.id, name!);
    await load();
  }

  Future<void> _manageMembers() async {
    final selfId = context.read<AppSession>().userId;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: DraggableScrollableSheet(
          expand: false,
          initialChildSize: .62,
          maxChildSize: .9,
          builder: (_, controller) => ListView(
            controller: controller,
            padding: const EdgeInsets.fromLTRB(20, 14, 20, 24),
            children: [
              const Text(
                '管理群成员',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 10),
              for (final member in members)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: CircleAvatar(
                    backgroundColor: AppColors.primarySoft,
                    child: Text(
                      member['nickname']?.toString().characters.firstOrNull ??
                          '同',
                    ),
                  ),
                  title: Text(member['nickname']?.toString() ?? '同路行用户'),
                  subtitle: Text(
                    member['memberRole'] == 'OWNER'
                        ? '队长'
                        : member['memberRole'] == 'NAVIGATOR'
                        ? '领航员'
                        : '普通成员',
                  ),
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => PublicProfilePage(
                        userId: member['userId'].toString(),
                      ),
                    ),
                  ),
                  trailing:
                      member['memberRole'] == 'OWNER' ||
                          member['userId']?.toString() == selfId
                      ? null
                      : PopupMenuButton<String>(
                          tooltip: '成员权限',
                          itemBuilder: (_) => const [
                            PopupMenuItem(value: 'ADMIN', child: Text('设为管理员')),
                            PopupMenuItem(
                              value: 'NAVIGATOR',
                              child: Text('设为领航员'),
                            ),
                            PopupMenuItem(
                              value: 'MEMBER',
                              child: Text('设为普通成员'),
                            ),
                            PopupMenuDivider(),
                            PopupMenuItem(value: 'REMOVE', child: Text('移除成员')),
                          ],
                          onSelected: (value) async {
                            final service = ChatService(
                              context.read<AppSession>().api,
                            );
                            if (value == 'REMOVE') {
                              await service.removeMember(
                                widget.conversation.id,
                                member['userId'].toString(),
                              );
                            } else {
                              await service.updateMemberRole(
                                widget.conversation.id,
                                member['userId'].toString(),
                                value,
                              );
                            }
                            if (!sheetContext.mounted) return;
                            Navigator.pop(sheetContext);
                            await load();
                          },
                        ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _closeGroup() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('确认结束群聊？'),
        content: const Text('群聊将归档，成员不能继续发送消息。历史记录仍会按风控规则保留。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('确认结束'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    await ChatService(
      context.read<AppSession>().api,
    ).closeGroup(widget.conversation.id);
    if (mounted) Navigator.pop(context);
  }
}

class _TripSummary extends StatelessWidget {
  const _TripSummary({required this.workspace});
  final Map<String, dynamic> workspace;
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        workspace['tripName']?.toString() ?? '当前行程',
        style: const TextStyle(
          color: AppColors.text,
          fontSize: 17,
          fontWeight: FontWeight.w800,
        ),
      ),
      const SizedBox(height: 6),
      Text(
        '${workspace['startName'] ?? '起点'} → ${workspace['endName'] ?? '目的地'}',
        style: const TextStyle(color: AppColors.secondaryText, fontSize: 13),
      ),
      const SizedBox(height: 8),
      Text(
        '${workspace['departureTime'] ?? '时间待定'} · ${workspace['memberCount'] ?? 0}人 · ${workspace['vehicleCount'] ?? 0}辆',
        style: const TextStyle(color: AppColors.muted, fontSize: 12),
      ),
    ],
  );
}

class _Tool extends StatelessWidget {
  const _Tool({required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(12),
    child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 11),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: AppColors.primarySoft,
              borderRadius: BorderRadius.circular(13),
            ),
            child: Icon(icon, size: 20, color: AppColors.primary),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700),
            ),
          ),
          const Icon(
            LucideIcons.chevronRight,
            size: 16,
            color: AppColors.muted,
          ),
        ],
      ),
    ),
  );
}

class _SettingSwitchRow extends StatelessWidget {
  const _SettingSwitchRow({
    required this.value,
    required this.onChanged,
    required this.title,
    required this.icon,
  });
  final bool value;
  final ValueChanged<bool> onChanged;
  final String title;
  final IconData icon;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 3),
    child: Row(
      children: [
        Icon(icon, color: AppColors.text),
        const SizedBox(width: 10),
        Expanded(child: Text(title, style: const TextStyle(fontSize: 14))),
        Switch(
          value: value,
          onChanged: onChanged,
          activeThumbColor: Colors.white,
          activeTrackColor: AppColors.primary,
          inactiveThumbColor: Colors.white,
          inactiveTrackColor: const Color(0xFFD0D5DD),
          trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
        ),
      ],
    ),
  );
}

class ChatTripWorkspacePage extends StatelessWidget {
  const ChatTripWorkspacePage({required this.conversation, super.key});
  final ConversationModel conversation;
  @override
  Widget build(BuildContext context) => FutureBuilder<Map<String, dynamic>>(
    future: ChatService(
      context.read<AppSession>().api,
    ).groupWorkspace(conversation.id),
    builder: (context, snapshot) => Scaffold(
      appBar: AppBar(title: const Text('行程信息')),
      body: !snapshot.hasData
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(20),
              children: [
                _TripSummary(workspace: snapshot.data!),
                const SizedBox(height: 18),
                for (final item in const [
                  ('行程详情', LucideIcons.info, true),
                  ('行程路线', LucideIcons.route, true),
                  ('经停点', LucideIcons.mapPin, true),
                  ('成员列表', LucideIcons.usersRound, false),
                ])
                  ListTile(
                    leading: Icon(item.$2, color: AppColors.primary),
                    title: Text(item.$1),
                    trailing: const Icon(LucideIcons.chevronRight),
                    onTap: () {
                      if (!item.$3) {
                        Navigator.pop(context);
                        return;
                      }
                      final tripId = snapshot.data!['tripId']?.toString();
                      if (tripId == null || tripId.isEmpty) return;
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => TripDetailPage(tripId: tripId),
                        ),
                      );
                    },
                  ),
              ],
            ),
    ),
  );
}

class ChatItemsPage extends StatefulWidget {
  const ChatItemsPage({
    required this.conversation,
    required this.type,
    required this.title,
    super.key,
  });
  final ConversationModel conversation;
  final String type;
  final String title;
  @override
  State<ChatItemsPage> createState() => _ChatItemsPageState();
}

class _ChatItemsPageState extends State<ChatItemsPage> {
  List<Map<String, dynamic>> rows = [];
  bool loading = true;
  String selfRole = 'MEMBER';
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final w = await ChatService(
      context.read<AppSession>().api,
    ).groupWorkspace(widget.conversation.id);
    selfRole = w['selfRole']?.toString() ?? 'MEMBER';
    rows = (w['items'] as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .where((e) => e['itemType'] == widget.type)
        .toList();
    if (mounted) setState(() => loading = false);
  }

  Future<void> create() async {
    final title = TextEditingController(),
        content = TextEditingController(),
        scheduledAt = TextEditingController(
          text: _displayDateTime(DateTime.now().add(const Duration(hours: 1))),
        );
    DateTime selectedTime = DateTime.now().add(const Duration(hours: 1));
    final optionControllers = [
      TextEditingController(),
      TextEditingController(),
    ];
    final ok = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => StatefulBuilder(
        builder: (context, setSheetState) => Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.viewInsetsOf(context).bottom,
          ),
          child: Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(context).height * .88,
            ),
            padding: const EdgeInsets.fromLTRB(22, 18, 22, 22),
            decoration: const BoxDecoration(
              color: Color(0xFFF7F8FC),
              borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
            ),
            child: ListView(
              shrinkWrap: true,
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              children: [
                Center(
                  child: Container(
                    width: 42,
                    height: 4,
                    decoration: BoxDecoration(
                      color: const Color(0xFFD7DBE5),
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                Text(
                  '新建${widget.title}',
                  style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 18),
                _editorField(title, '标题', hint: '用一句话说明主题'),
                const SizedBox(height: 14),
                _editorField(content, '详细内容', hint: '补充成员需要了解的信息', maxLines: 4),
                if (widget.type == 'POLL') ...[
                  const SizedBox(height: 20),
                  const Text(
                    '投票选项',
                    style: TextStyle(fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 10),
                  for (var i = 0; i < optionControllers.length; i++) ...[
                    _editorField(
                      optionControllers[i],
                      '选项 ${i + 1}',
                      hint: '请输入可供选择的内容',
                    ),
                    const SizedBox(height: 10),
                  ],
                  if (optionControllers.length < 8)
                    OutlinedButton.icon(
                      onPressed: () => setSheetState(
                        () => optionControllers.add(TextEditingController()),
                      ),
                      icon: const Icon(LucideIcons.plus),
                      label: const Text('添加选项'),
                    ),
                ],
                if (widget.type == 'REMINDER') ...[
                  const SizedBox(height: 14),
                  TextField(
                    controller: scheduledAt,
                    readOnly: true,
                    onTap: () async {
                      final date = await showDatePicker(
                        context: context,
                        firstDate: DateTime.now(),
                        lastDate: DateTime.now().add(const Duration(days: 365)),
                        initialDate: selectedTime,
                      );
                      if (date == null || !context.mounted) return;
                      final time = await showTimePicker(
                        context: context,
                        initialTime: TimeOfDay.fromDateTime(selectedTime),
                      );
                      if (time == null) return;
                      selectedTime = DateTime(
                        date.year,
                        date.month,
                        date.day,
                        time.hour,
                        time.minute,
                      );
                      scheduledAt.text = _displayDateTime(selectedTime);
                    },
                    decoration: _editorDecoration('集合 / 提醒时间', '点击选择日期和时间')
                        .copyWith(
                          suffixIcon: const Icon(LucideIcons.calendarClock),
                        ),
                  ),
                ],
                const SizedBox(height: 22),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => Navigator.pop(sheetContext, false),
                        child: const Text('取消'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: FilledButton(
                        onPressed: () => Navigator.pop(sheetContext, true),
                        child: const Text('发布到群聊'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
    if (ok == true && title.text.trim().isNotEmpty && mounted) {
      await ChatService(context.read<AppSession>().api).createGroupItem(
        widget.conversation.id,
        type: widget.type,
        title: title.text.trim(),
        content: content.text.trim(),
        payload: widget.type == 'POLL'
            ? {
                'options': optionControllers
                    .map((e) => e.text.trim())
                    .where((e) => e.isNotEmpty)
                    .toList(),
              }
            : widget.type == 'REMINDER'
            ? {'scheduledAt': selectedTime.toIso8601String().split('.').first}
            : {},
      );
      await load();
    }
    // BottomSheet 关闭后仍有一小段退场动画，延后销毁输入控制器。
    await Future<void>.delayed(const Duration(milliseconds: 250));
    title.dispose();
    content.dispose();
    for (final controller in optionControllers) {
      controller.dispose();
    }
    scheduledAt.dispose();
  }

  String _displayDateTime(DateTime value) =>
      '${value.year}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}  ${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';

  InputDecoration _editorDecoration(String label, String hint) =>
      InputDecoration(
        labelText: label,
        hintText: hint,
        filled: true,
        fillColor: Colors.white,
        contentPadding: const EdgeInsets.fromLTRB(16, 20, 16, 17),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
      );

  Widget _editorField(
    TextEditingController controller,
    String label, {
    required String hint,
    int maxLines = 1,
  }) => TextField(
    controller: controller,
    maxLines: maxLines,
    decoration: _editorDecoration(label, hint),
  );

  Map<String, dynamic> payload(Map<String, dynamic> row) {
    try {
      return Map<String, dynamic>.from(
        jsonDecode(row['payloadJson']?.toString() ?? '{}') as Map,
      );
    } catch (_) {
      return {};
    }
  }

  Future<void> edit(Map<String, dynamic> row) async {
    final title = TextEditingController(text: row['title']?.toString()),
        content = TextEditingController(text: row['content']?.toString());
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        insetPadding: const EdgeInsets.symmetric(horizontal: 22),
        title: Text('修改${widget.title}'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: title,
              decoration: _editorDecoration('标题', '请输入标题'),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: content,
              maxLines: 4,
              decoration: _editorDecoration('内容', '请输入公告或提醒内容'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    if (ok == true && title.text.trim().isNotEmpty && mounted) {
      await ChatService(context.read<AppSession>().api).updateGroupItem(
        widget.conversation.id,
        row['id'].toString(),
        type: widget.type,
        title: title.text.trim(),
        content: content.text.trim(),
        payload: payload(row),
      );
      await load();
    }
    // AlertDialog 取消时先完成退场动画，再释放控制器，避免偶发 disposed error。
    await Future<void>.delayed(const Duration(milliseconds: 250));
    title.dispose();
    content.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(widget.title),
      actions:
          const ['ANNOUNCEMENT', 'POLL', 'REMINDER'].contains(widget.type) &&
              !const ['OWNER', 'ADMIN'].contains(selfRole)
          ? null
          : [IconButton(onPressed: create, icon: const Icon(LucideIcons.plus))],
    ),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : rows.isEmpty
        ? Center(child: Text('暂无${widget.title}'))
        : ListView.separated(
            padding: const EdgeInsets.all(20),
            itemCount: rows.length,
            separatorBuilder: (_, _) => const SizedBox(height: 12),
            itemBuilder: (context, i) {
              final r = rows[i], p = payload(r);
              return Container(
                padding: const EdgeInsets.all(17),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            r['title']?.toString() ?? '',
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                        if (const ['OWNER', 'ADMIN'].contains(selfRole) &&
                            widget.type != 'POLL')
                          IconButton(
                            tooltip: '修改',
                            onPressed: () => edit(r),
                            icon: const Icon(LucideIcons.pencil, size: 18),
                          ),
                      ],
                    ),
                    if (r['content']?.toString().isNotEmpty == true) ...[
                      const SizedBox(height: 8),
                      Text(r['content'].toString()),
                    ],
                    if (widget.type == 'POLL')
                      ...(p['options'] as List? ?? const []).map((o) {
                        final option = o is Map
                            ? Map<String, dynamic>.from(o)
                            : {'key': o.toString(), 'label': o.toString()};
                        return Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: OutlinedButton(
                            onPressed: () async {
                              final service = ChatService(
                                context.read<AppSession>().api,
                              );
                              final details = await service.groupItem(
                                widget.conversation.id,
                                r['id'].toString(),
                              );
                              if (details['myVote'] != null) {
                                if (context.mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(
                                      content: Text('你已经投过票，每人只能投一次'),
                                    ),
                                  );
                                }
                                return;
                              }
                              await service.vote(
                                widget.conversation.id,
                                r['id'].toString(),
                                option['key'].toString(),
                              );
                              if (!context.mounted) return;
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('投票成功')),
                              );
                            },
                            child: Align(
                              alignment: Alignment.centerLeft,
                              child: Text(option['label'].toString()),
                            ),
                          ),
                        );
                      }),
                    const SizedBox(height: 8),
                    Text(
                      r['createdAt']?.toString() ?? '',
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.muted,
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
  );
}

class ChatLocationSharePage extends StatefulWidget {
  const ChatLocationSharePage({required this.conversation, super.key});
  final ConversationModel conversation;
  @override
  State<ChatLocationSharePage> createState() => _ChatLocationSharePageState();
}

class _ChatLocationSharePageState extends State<ChatLocationSharePage> {
  static const channel = MethodChannel('com.tongluxing/permissions');
  LatLng? me;
  List<Map<String, dynamic>> locations = [];
  bool enabled = false;
  DateTime? lastUploadedAt;
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final w = await ChatService(
      context.read<AppSession>().api,
    ).groupWorkspace(widget.conversation.id);
    locations = (w['locations'] as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
    if (mounted) setState(() {});
  }

  Future<void> share() async {
    final granted =
        await channel.invokeMethod<bool>('requestLocation') ?? false;
    if (!granted || !mounted) return;
    setState(() => enabled = true);
  }

  Future<void> submit() async {
    final p = me;
    if (p == null) return;
    final now = DateTime.now();
    if (lastUploadedAt != null &&
        now.difference(lastUploadedAt!) < const Duration(seconds: 5)) {
      return;
    }
    lastUploadedAt = now;
    locations = await ChatService(context.read<AppSession>().api).shareLocation(
      widget.conversation.id,
      latitude: p.latitude,
      longitude: p.longitude,
    );
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    AMapInitializer.init(context);
    AMapInitializer.updatePrivacyAgree(
      const AMapPrivacyStatement(
        hasContains: true,
        hasShow: true,
        hasAgree: true,
      ),
    );
    return Scaffold(
      appBar: AppBar(title: const Text('车队实时位置')),
      body: Column(
        children: [
          Expanded(
            child: AMapWidget(
              myLocationStyleOptions: MyLocationStyleOptions(true),
              onLocationChanged: (l) {
                if (isLocationValid(l)) {
                  me = l.latLng;
                  if (enabled) submit();
                }
              },
            ),
          ),
          Container(
            padding: const EdgeInsets.all(16),
            color: Colors.white,
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        '当前共享 ${locations.length} 辆车',
                        style: const TextStyle(fontWeight: FontWeight.w800),
                      ),
                    ),
                    FilledButton.icon(
                      onPressed: share,
                      icon: const Icon(LucideIcons.locateFixed),
                      label: Text(enabled ? '共享中' : '共享我的位置'),
                    ),
                  ],
                ),
                for (final l in locations.take(4))
                  ListTile(
                    dense: true,
                    leading: const Icon(LucideIcons.carFront),
                    title: Text(l['nickname']?.toString() ?? '车队成员'),
                    subtitle: Text(
                      '${l['latitude']}, ${l['longitude']} · ${l['recordedAt']}',
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class ChatReportPage extends StatefulWidget {
  const ChatReportPage({
    required this.conversation,
    required this.members,
    super.key,
  });
  final ConversationModel conversation;
  final List<Map<String, dynamic>> members;
  @override
  State<ChatReportPage> createState() => _ChatReportPageState();
}

class _ChatReportPageState extends State<ChatReportPage> {
  String targetType = 'CONVERSATION', reportType = 'HARASSMENT';
  String? memberId;
  final reason = TextEditingController();
  @override
  void dispose() {
    reason.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    final target = targetType == 'CONVERSATION'
        ? widget.conversation.id
        : (memberId ?? '');
    if (target.isEmpty || reason.text.trim().isEmpty) {
      return;
    }
    final r = await ChatService(context.read<AppSession>().api).report(
      widget.conversation.id,
      targetType: targetType,
      targetId: target,
      reportType: reportType,
      reason: reason.text.trim(),
    );
    if (mounted) {
      showDialog<void>(
        context: context,
        builder: (c) => AlertDialog(
          title: const Text('举报已提交'),
          content: Text('举报编号：${r['reportId']}\n运营人员会进行人工审核。'),
          actions: [
            FilledButton(
              onPressed: () {
                Navigator.pop(c);
                Navigator.pop(context);
              },
              child: const Text('完成'),
            ),
          ],
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('举报与违规反馈')),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        DropdownButtonFormField(
          initialValue: targetType,
          items: const [
            DropdownMenuItem(value: 'CONVERSATION', child: Text('举报群聊')),
            DropdownMenuItem(value: 'MEMBER', child: Text('举报成员')),
          ],
          onChanged: (v) => setState(() => targetType = v!),
        ),
        if (targetType == 'MEMBER')
          DropdownButtonFormField(
            initialValue: memberId,
            hint: const Text('选择成员'),
            items: widget.members
                .map(
                  (m) => DropdownMenuItem(
                    value: m['userId'].toString(),
                    child: Text(m['nickname']?.toString() ?? '成员'),
                  ),
                )
                .toList(),
            onChanged: (v) => setState(() => memberId = v),
          ),
        const SizedBox(height: 14),
        DropdownButtonFormField(
          initialValue: reportType,
          items: const [
            DropdownMenuItem(value: 'HARASSMENT', child: Text('骚扰辱骂')),
            DropdownMenuItem(value: 'FRAUD', child: Text('诈骗引流')),
            DropdownMenuItem(value: 'ILLEGAL', child: Text('违法违规')),
            DropdownMenuItem(value: 'OTHER', child: Text('其他')),
          ],
          onChanged: (v) => setState(() => reportType = v!),
        ),
        const SizedBox(height: 14),
        TextField(
          controller: reason,
          maxLength: 500,
          maxLines: 5,
          decoration: const InputDecoration(
            labelText: '举报原因与证据说明',
            alignLabelWithHint: true,
          ),
        ),
        FilledButton(onPressed: submit, child: const Text('提交举报')),
      ],
    ),
  );
}

class JoinApplicationsPage extends StatefulWidget {
  const JoinApplicationsPage({super.key});
  @override
  State<JoinApplicationsPage> createState() => _JoinApplicationsPageState();
}

class _JoinApplicationsPageState extends State<JoinApplicationsPage> {
  List<Map<String, dynamic>> rows = [];
  bool loading = true;
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    rows = await ChatService(context.read<AppSession>().api).joinApplications();
    if (mounted) setState(() => loading = false);
  }

  Future<void> review(Map<String, dynamic> row, String decision) async {
    await ChatService(
      context.read<AppSession>().api,
    ).reviewJoinApplication(row['applicationId'].toString(), decision);
    await load();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(decision == 'APPROVED' ? '已同意入群' : '已拒绝申请')),
      );
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('入群申请')),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : rows.isEmpty
        ? const Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  LucideIcons.userRoundCheck,
                  size: 48,
                  color: AppColors.primary,
                ),
                SizedBox(height: 12),
                Text(
                  '暂时没有待处理申请',
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
                SizedBox(height: 5),
                Text(
                  '新的车队入群申请会出现在这里',
                  style: TextStyle(color: AppColors.muted),
                ),
              ],
            ),
          )
        : ListView.separated(
            padding: const EdgeInsets.all(20),
            itemCount: rows.length,
            separatorBuilder: (_, _) => const SizedBox(height: 12),
            itemBuilder: (c, i) {
              final r = rows[i];
              return Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Column(
                  children: [
                    Row(
                      children: [
                        InkWell(
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => PublicProfilePage(
                                userId: r['applicantUserId'].toString(),
                              ),
                            ),
                          ),
                          child: CircleAvatar(
                            radius: 26,
                            backgroundColor: AppColors.primarySoft,
                            child: Text(
                              (r['nickname']?.toString().isNotEmpty ?? false)
                                  ? r['nickname'].toString().characters.first
                                  : '同',
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                r['nickname']?.toString() ?? '同路行用户',
                                style: const TextStyle(
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              const SizedBox(height: 3),
                              Text(
                                '申请加入 ${r['conversationName'] ?? '车队群聊'}',
                                style: const TextStyle(
                                  fontSize: 12,
                                  color: AppColors.muted,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    if (r['applicationMessage']?.toString().isNotEmpty ==
                        true) ...[
                      const SizedBox(height: 12),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Text(r['applicationMessage'].toString()),
                      ),
                    ],
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () => review(r, 'REJECTED'),
                            child: const Text('拒绝'),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: FilledButton(
                            onPressed: () => review(r, 'APPROVED'),
                            child: const Text('同意'),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              );
            },
          ),
  );
}
