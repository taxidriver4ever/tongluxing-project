import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../profile/pages/profile_system_pages.dart';

class ChatGroupDetailsPage extends StatefulWidget {
  const ChatGroupDetailsPage({required this.conversation, super.key});
  final ConversationModel conversation;
  @override
  State<ChatGroupDetailsPage> createState() => _ChatGroupDetailsPageState();
}

class _ChatGroupDetailsPageState extends State<ChatGroupDetailsPage> {
  List<Map<String, dynamic>> members = [];
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
    ]);
    members = values[0] as List<Map<String, dynamic>>;
    final set = values[1] as Map<String, dynamic>;
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
    appBar: AppBar(title: const Text('群聊详情')),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : ListView(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '群成员（${members.length}）',
                      style: const TextStyle(
                        fontSize: 18,
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
              const SizedBox(height: 14),
              Wrap(
                spacing: 14,
                runSpacing: 16,
                children: members
                    .map(
                      (m) => SizedBox(
                        width: 62,
                        child: InkWell(
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => PublicProfilePage(
                                userId: m['userId'].toString(),
                              ),
                            ),
                          ),
                          child: Column(
                            children: [
                              CircleAvatar(
                                radius: 26,
                                backgroundColor: AppColors.primarySoft,
                                child: Text(
                                  (m['nickname']?.toString().isNotEmpty ??
                                          false)
                                      ? m['nickname']
                                            .toString()
                                            .characters
                                            .first
                                      : '同',
                                  style: const TextStyle(
                                    color: AppColors.primary,
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
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
                            ],
                          ),
                        ),
                      ),
                    )
                    .toList(),
              ),
              const SizedBox(height: 26),
              Container(
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(22),
                ),
                child: Column(
                  children: [
                    SwitchListTile(
                      value: muted,
                      onChanged: (v) => update(nextMuted: v),
                      title: const Text('消息免打扰'),
                      secondary: const Icon(LucideIcons.bellOff),
                    ),
                    const Divider(height: 1),
                    SwitchListTile(
                      value: pinned,
                      onChanged: (v) => update(nextPinned: v),
                      title: const Text('置顶聊天'),
                      secondary: const Icon(LucideIcons.pin),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFF7E8),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: const Text(
                  '群聊与当前行程绑定。行程结束后群聊自动归档，风险消息按平台规则留存。',
                  style: TextStyle(color: Color(0xFF8B5A00), height: 1.5),
                ),
              ),
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
