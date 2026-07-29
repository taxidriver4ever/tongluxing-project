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
import '../../trip/pages/trip_quick_edit_page.dart';

class ChatGroupDetailsPage extends StatefulWidget {
  const ChatGroupDetailsPage({required this.conversation, super.key});

  final ConversationModel conversation;

  @override
  State<ChatGroupDetailsPage> createState() => _ChatGroupDetailsPageState();
}

class _ChatGroupDetailsPageState extends State<ChatGroupDetailsPage> {
  List<Map<String, dynamic>> members = const [];
  Map<String, dynamic> workspace = const {};
  bool muted = false;
  bool pinned = false;
  bool loading = true;
  String? error;

  bool get isOwner => workspace['selfRole'] == 'OWNER';

  String get groupName {
    final value = workspace['conversationName']?.toString().trim() ?? '';
    return value.isEmpty ? widget.conversation.name : value;
  }

  String get groupIntroduction {
    final custom = workspace['groupIntroduction']?.toString().trim() ?? '';
    if (custom.isNotEmpty) return custom;
    final tripName = workspace['tripName']?.toString().trim() ?? '';
    return tripName.isEmpty ? '由行程自动创建的同行群聊' : '“$tripName”行程同行群';
  }

  String get groupAnnouncement {
    final value = workspace['announcement']?.toString().trim() ?? '';
    return value.isEmpty ? '未填写' : value;
  }

  String get tripNumber => workspace['tripNumber']?.toString().trim() ?? '';

  Future<void> _copyTripNumber() async {
    if (tripNumber.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: tripNumber));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('行程号已复制，可以发送给其他人')));
  }

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    if (mounted) {
      setState(() {
        loading = true;
        error = null;
      });
    }
    try {
      final service = ChatService(context.read<AppSession>().api);
      final values = await Future.wait([
        service.members(widget.conversation.id),
        service.settings(widget.conversation.id),
        service.groupWorkspace(widget.conversation.id),
      ]);
      if (!mounted) return;
      final settings = values[1] as Map<String, dynamic>;
      setState(() {
        members = values[0] as List<Map<String, dynamic>>;
        workspace = values[2] as Map<String, dynamic>;
        muted = settings['muted'] == true;
        pinned = settings['pinned'] == true;
        loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        loading = false;
        error = '$e';
      });
    }
  }

  Future<void> update({bool? nextMuted, bool? nextPinned}) async {
    final oldMuted = muted;
    final oldPinned = pinned;
    setState(() {
      muted = nextMuted ?? muted;
      pinned = nextPinned ?? pinned;
    });
    try {
      await ChatService(
        context.read<AppSession>().api,
      ).updateSettings(widget.conversation.id, muted: muted, pinned: pinned);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        muted = oldMuted;
        pinned = oldPinned;
      });
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F6F8),
      appBar: AppBar(
        centerTitle: true,
        backgroundColor: const Color(0xFFF5F6F8),
        toolbarHeight: 50,
        title: const Text(
          '聊天信息',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
        ),
      ),
      body: loading
          ? const Center(child: CircularProgressIndicator())
          : error != null
          ? Center(
              child: FilledButton.icon(
                onPressed: load,
                icon: const Icon(LucideIcons.refreshCw, size: 17),
                label: const Text('重新加载'),
              ),
            )
          : ListView(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 28),
              children: [
                _GroupIdentityCard(
                  conversation: widget.conversation,
                  groupName: groupName,
                ),
                const SizedBox(height: 12),
                _MembersPreviewCard(members: members, onTap: _openMembers),
                const SizedBox(height: 12),
                _ChatInfoSection(
                  children: [
                    _ChatInfoRow(
                      title: '群名称与头像',
                      value: groupName,
                      avatar: UserAvatar(
                        nickname: groupName,
                        avatarImageKey: widget.conversation.avatarImageKey,
                        avatarUrl: widget.conversation.avatarUrl,
                        radius: 13,
                      ),
                      showChevron: isOwner,
                      onTap: isOwner ? _rename : null,
                    ),
                    _ChatInfoRow(title: '群简介', value: groupIntroduction),
                    _ChatInfoRow(title: '群公告', value: groupAnnouncement),
                    _ChatInfoRow(
                      title: '行程号',
                      value: tripNumber.isEmpty ? '暂未生成' : tripNumber,
                      trailing: tripNumber.isEmpty
                          ? null
                          : const Icon(
                              LucideIcons.copy,
                              size: 18,
                              color: AppColors.primary,
                            ),
                      onTap: tripNumber.isEmpty ? null : _copyTripNumber,
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _ChatInfoSection(
                  children: [
                    _ChatInfoRow(
                      title: '行程信息',
                      showChevron: true,
                      onTap: _openTripInfo,
                    ),
                    if (isOwner)
                      _ChatInfoRow(
                        title: '修改行程',
                        value: '保存后会通知群友',
                        showChevron: true,
                        onTap: _editTrip,
                      ),
                    if (isOwner)
                      _ChatInfoRow(
                        title: '修改群名称',
                        showChevron: true,
                        onTap: _rename,
                      ),
                    _ChatInfoRow(
                      title: '举报与违规反馈',
                      showChevron: true,
                      onTap: () => _open(
                        ChatReportPage(
                          conversation: widget.conversation,
                          members: members,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _ChatInfoSection(
                  children: [
                    _SettingSwitchRow(
                      value: muted,
                      onChanged: (value) => update(nextMuted: value),
                      title: '消息免打扰',
                      icon: LucideIcons.bellOff,
                    ),
                    _SettingSwitchRow(
                      value: pinned,
                      onChanged: (value) => update(nextPinned: value),
                      title: '置顶聊天',
                      icon: LucideIcons.pin,
                    ),
                  ],
                ),
                if (!isOwner) ...[
                  const SizedBox(height: 12),
                  _ChatInfoSection(
                    children: [
                      _ChatInfoRow(
                        title: '退出群聊',
                        titleColor: AppColors.danger,
                        trailing: const Icon(
                          LucideIcons.logOut,
                          color: AppColors.danger,
                          size: 21,
                        ),
                        onTap: _exitGroup,
                      ),
                    ],
                  ),
                ],
              ],
            ),
    );
  }

  void _openMembers() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ChatGroupMembersPage(
          conversation: widget.conversation,
          members: members,
        ),
      ),
    );
  }

  void _openTripInfo() {
    final tripId = workspace['tripId']?.toString() ?? widget.conversation.bizId;
    if (tripId.isEmpty) return;
    _open(TripDetailPage(tripId: tripId));
  }

  Future<void> _editTrip() async {
    if (!isOwner) return;
    final tripId = workspace['tripId']?.toString() ?? widget.conversation.bizId;
    if (tripId.isEmpty) return;
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => TripQuickEditPage(tripId: tripId)),
    );
    if (changed == true && mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('行程已更新，群友已收到通知')));
      await load();
    }
  }

  Future<void> _exitGroup() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('退出群聊？'),
        content: const Text(
          '退出后将同时退出该车队和关联行程，并立即释放成员名额。'
          '如果行程正在进行，你将无法获得本次行程成长值。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('退出'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await ChatService(
      context.read<AppSession>().api,
    ).exitGroup(widget.conversation.id);
    if (mounted) Navigator.pop(context, true);
  }

  Future<void> _open(Widget page) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    if (mounted) await load();
  }

  Future<void> _rename() async {
    if (!isOwner) return;
    final controller = TextEditingController(text: groupName);
    final name = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('修改群名称'),
        content: TextField(
          controller: controller,
          maxLength: 64,
          autofocus: true,
          decoration: const InputDecoration(labelText: '群名称'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () =>
                Navigator.pop(dialogContext, controller.text.trim()),
            child: const Text('保存'),
          ),
        ],
      ),
    );
    await Future<void>.delayed(const Duration(milliseconds: 250));
    controller.dispose();
    if (name?.isNotEmpty != true || !mounted) return;
    await ChatService(
      context.read<AppSession>().api,
    ).renameGroup(widget.conversation.id, name!);
    await load();
  }
}

class ChatGroupMembersPage extends StatefulWidget {
  const ChatGroupMembersPage({
    required this.conversation,
    required this.members,
    super.key,
  });

  final ConversationModel conversation;
  final List<Map<String, dynamic>> members;

  @override
  State<ChatGroupMembersPage> createState() => _ChatGroupMembersPageState();
}

class _ChatGroupMembersPageState extends State<ChatGroupMembersPage> {
  final search = TextEditingController();
  late List<Map<String, dynamic>> rows;

  @override
  void initState() {
    super.initState();
    rows = List<Map<String, dynamic>>.from(widget.members);
  }

  @override
  void dispose() {
    search.dispose();
    super.dispose();
  }

  void _filter(String value) {
    final keyword = value.trim().toLowerCase();
    setState(() {
      rows = keyword.isEmpty
          ? List<Map<String, dynamic>>.from(widget.members)
          : widget.members.where((member) {
              final nickname =
                  member['nickname']?.toString().toLowerCase() ?? '';
              final userId = member['userId']?.toString().toLowerCase() ?? '';
              return nickname.contains(keyword) || userId.contains(keyword);
            }).toList();
    });
  }

  bool _isManager(Map<String, dynamic> member) {
    final role = member['memberRole']?.toString() ?? '';
    return role == 'OWNER' || role == 'ADMIN' || role == 'NAVIGATOR';
  }

  @override
  Widget build(BuildContext context) {
    final managers = rows.where(_isManager).toList();
    final regularMembers = rows.where((member) => !_isManager(member)).toList();
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        centerTitle: true,
        backgroundColor: Colors.white,
        toolbarHeight: 58,
        title: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              '群成员(${widget.members.length})',
              style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
            ),
            const Text(
              '群主优先',
              style: TextStyle(fontSize: 10.5, color: AppColors.muted),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          Container(
            height: 46,
            margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            decoration: BoxDecoration(
              color: const Color(0xFFF4F5F7),
              borderRadius: BorderRadius.circular(12),
            ),
            child: TextField(
              controller: search,
              onChanged: _filter,
              textAlignVertical: TextAlignVertical.center,
              decoration: const InputDecoration(
                hintText: '搜索',
                prefixIcon: Icon(LucideIcons.search, size: 21),
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                contentPadding: EdgeInsets.zero,
              ),
            ),
          ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
              children: [
                if (managers.isNotEmpty) ...[
                  _MemberSectionTitle('群主/管理员（${managers.length}人）'),
                  const SizedBox(height: 8),
                  ...managers.map(_memberTile),
                  const SizedBox(height: 16),
                ],
                _MemberSectionTitle('群成员（${regularMembers.length}人）'),
                const SizedBox(height: 8),
                if (regularMembers.isEmpty)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 32),
                    child: Center(
                      child: Text(
                        '没有匹配的群成员',
                        style: TextStyle(color: AppColors.muted),
                      ),
                    ),
                  )
                else
                  ...regularMembers.map(_memberTile),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _memberTile(Map<String, dynamic> member) {
    final role = member['memberRole']?.toString() ?? '';
    final roleName = switch (role) {
      'OWNER' => '群主',
      'ADMIN' => '管理员',
      'NAVIGATOR' => '领航员',
      _ => '',
    };
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) =>
              PublicProfilePage(userId: member['userId']?.toString() ?? ''),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(
          children: [
            UserAvatar(
              nickname: member['nickname']?.toString() ?? '同路行用户',
              avatarImageKey: member['avatarImageKey']?.toString() ?? '',
              avatarUrl: member['avatarUrl']?.toString() ?? '',
              radius: 24,
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Text(
                member['nickname']?.toString() ?? '同路行用户',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            if (roleName.isNotEmpty)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.primarySoft,
                  borderRadius: BorderRadius.circular(5),
                ),
                child: Text(
                  roleName,
                  style: const TextStyle(
                    color: AppColors.primary,
                    fontSize: 10.5,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _MemberSectionTitle extends StatelessWidget {
  const _MemberSectionTitle(this.value);

  final String value;

  @override
  Widget build(BuildContext context) => Text(
    value,
    style: const TextStyle(
      color: AppColors.muted,
      fontSize: 13,
      fontWeight: FontWeight.w600,
    ),
  );
}

class _GroupIdentityCard extends StatelessWidget {
  const _GroupIdentityCard({
    required this.conversation,
    required this.groupName,
  });

  final ConversationModel conversation;
  final String groupName;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(14),
    ),
    child: Row(
      children: [
        UserAvatar(
          nickname: groupName,
          avatarImageKey: conversation.avatarImageKey,
          avatarUrl: conversation.avatarUrl,
          radius: 30,
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Text(
            groupName,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
          ),
        ),
      ],
    ),
  );
}

class _MembersPreviewCard extends StatelessWidget {
  const _MembersPreviewCard({required this.members, required this.onTap});

  final List<Map<String, dynamic>> members;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final preview = members.take(5).toList();
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        borderRadius: BorderRadius.circular(14),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 14),
          child: Column(
            children: [
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      '群聊成员',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  Text(
                    '${members.length}',
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 14,
                    ),
                  ),
                  const SizedBox(width: 8),
                  const SizedBox(
                    width: 20,
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: Icon(
                        LucideIcons.chevronRight,
                        size: 18,
                        color: AppColors.muted,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  for (final member in preview)
                    Expanded(
                      child: Column(
                        children: [
                          UserAvatar(
                            nickname: member['nickname']?.toString() ?? '同路行用户',
                            avatarImageKey:
                                member['avatarImageKey']?.toString() ?? '',
                            avatarUrl: member['avatarUrl']?.toString() ?? '',
                            radius: 22,
                          ),
                          const SizedBox(height: 6),
                          Text(
                            member['nickname']?.toString() ?? '同路行用户',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 10.5,
                              color: AppColors.secondaryText,
                            ),
                          ),
                        ],
                      ),
                    ),
                  for (var i = preview.length; i < 5; i++)
                    const Expanded(child: SizedBox()),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChatInfoSection extends StatelessWidget {
  const _ChatInfoSection({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(14),
    ),
    child: Column(children: children),
  );
}

class _ChatInfoRow extends StatelessWidget {
  const _ChatInfoRow({
    required this.title,
    this.value = '',
    this.avatar,
    this.trailing,
    this.showChevron = false,
    this.onTap,
    this.titleColor,
  });

  final String title;
  final String value;
  final Widget? avatar;
  final Widget? trailing;
  final bool showChevron;
  final VoidCallback? onTap;
  final Color? titleColor;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final titleWidth = (constraints.maxWidth * .38).clamp(126.0, 152.0);
      return Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(14),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 15),
            child: Row(
              children: [
                SizedBox(
                  width: titleWidth,
                  child: Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      color: titleColor ?? AppColors.text,
                      fontSize: 14.5,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      if (avatar != null) ...[
                        avatar!,
                        const SizedBox(width: 8),
                      ],
                      if (value.isNotEmpty)
                        Flexible(
                          child: Text(
                            value,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            textAlign: TextAlign.right,
                            style: const TextStyle(
                              color: AppColors.muted,
                              fontSize: 13.5,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                SizedBox(
                  width: 20,
                  child: Align(
                    alignment: Alignment.centerRight,
                    child: trailing != null
                        ? trailing!
                        : showChevron
                        ? const Icon(
                            LucideIcons.chevronRight,
                            size: 18,
                            color: AppColors.muted,
                          )
                        : const SizedBox.shrink(),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    },
  );
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
    rows = await ChatService(
      context.read<AppSession>().api,
    ).receivedTeamApplications();
    if (mounted) setState(() => loading = false);
  }

  Future<void> review(Map<String, dynamic> row, String decision) async {
    await ChatService(
      context.read<AppSession>().api,
    ).reviewTeamApplication(row['applicationId'].toString(), decision);
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
                              if (r['wantsToDrive'] == true)
                                Text(
                                  '要开车${r['vehicleSummary']?.toString().trim().isNotEmpty == true ? ' · ${r['vehicleSummary']}' : ''}',
                                  style: const TextStyle(
                                    fontSize: 12,
                                    color: AppColors.primary,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                            ],
                          ),
                        ),
                      ],
                    ),
                    if (r['applyMessage']?.toString().isNotEmpty == true) ...[
                      const SizedBox(height: 12),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Text(r['applyMessage'].toString()),
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
