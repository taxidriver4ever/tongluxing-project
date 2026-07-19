import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';

class TeamPage extends StatelessWidget {
  const TeamPage({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    body: CustomScrollView(
      slivers: [
        SliverAppBar(
          pinned: true,
          expandedHeight: 230,
          foregroundColor: Colors.white,
          backgroundColor: const Color(0xFF285CFF),
          actions: [
            IconButton(
              onPressed: () => _openManage(context),
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
              child: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  CircleAvatar(
                    radius: 28,
                    backgroundColor: Colors.white24,
                    child: Icon(LucideIcons.mountain, color: Colors.white),
                  ),
                  SizedBox(height: 12),
                  Text(
                    '杭州自驾联盟',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 25,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  Text(
                    '车队编号 TLX-HZ-318 · 28 位成员',
                    style: TextStyle(color: Colors.white70),
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
              TlxCard(
                color: const Color(0xFFF8FAFD),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    _Action(
                      icon: LucideIcons.messageCircle,
                      label: '群聊',
                      onTap: () => _toast(context, '已进入车队群聊演示'),
                    ),
                    _Action(
                      icon: LucideIcons.users,
                      label: '成员',
                      onTap: () => _openMembers(context),
                    ),
                    _Action(
                      icon: LucideIcons.megaphone,
                      label: '公告',
                      onTap: () => _toast(context, '周六 07:30 龙井路停车场集合'),
                    ),
                    _Action(
                      icon: LucideIcons.share2,
                      label: '邀请',
                      onTap: () => _toast(context, '邀请链接已准备好'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 22),
              const Text(
                '车队公告',
                style: TextStyle(fontSize: 19, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 12),
              const TlxCard(
                color: Color(0xFFFFF7E8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '置顶 · 皖南川藏线出行提醒',
                      style: TextStyle(fontWeight: FontWeight.w700),
                    ),
                    SizedBox(height: 8),
                    Text(
                      '请所有成员检查胎压、对讲机和随车工具，周六 07:30 准时集合。',
                      style: TextStyle(
                        height: 1.6,
                        color: AppColors.secondaryText,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 22),
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      '近期行程',
                      style: TextStyle(
                        fontSize: 19,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  TextButton(onPressed: () {}, child: const Text('全部')),
                ],
              ),
              const SizedBox(height: 8),
              const TlxCard(
                color: Color(0xFFF8FAFD),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '皖南川藏线 · 周末轻越野',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    SizedBox(height: 10),
                    Text(
                      '杭州 → 宁国 → 泾县',
                      style: TextStyle(color: AppColors.secondaryText),
                    ),
                    SizedBox(height: 8),
                    Text(
                      '7月25日出发 · 6/8 辆车',
                      style: TextStyle(color: AppColors.primary),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              const TlxCard(
                color: Color(0xFFF8FAFD),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '莫干山露营观星',
                      style: TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    SizedBox(height: 10),
                    Text(
                      '杭州 → 德清莫干山',
                      style: TextStyle(color: AppColors.secondaryText),
                    ),
                    SizedBox(height: 8),
                    Text(
                      '8月8日出发 · 招募中',
                      style: TextStyle(color: AppColors.success),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    ),
  );

  static void _openMembers(BuildContext context) => Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => const TeamMembersPage()),
  );
  static void _openManage(BuildContext context) => Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => const TeamManagePage()),
  );
  static void _toast(BuildContext context, String text) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
}

class TeamMembersPage extends StatelessWidget {
  const TeamMembersPage({super.key});
  static const members = [
    ('阿哲', '领队 · 坦克300', '队长'),
    ('林宇', '副领队 · 唐 DM-p', '管理员'),
    ('小鹿', '成员 · 理想 L8', '成员'),
    ('远山', '成员 · 牧马人', '成员'),
    ('Kiki', '成员 · Model Y', '成员'),
  ];

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('车队成员'),
      actions: [
        IconButton(onPressed: () {}, icon: const Icon(LucideIcons.userPlus)),
      ],
    ),
    body: ListView.separated(
      padding: const EdgeInsets.all(22),
      itemCount: members.length,
      separatorBuilder: (_, _) => const Divider(),
      itemBuilder: (_, i) => ListTile(
        contentPadding: EdgeInsets.zero,
        leading: CircleAvatar(
          backgroundColor: AppColors.primarySoft,
          child: Text(members[i].$1.characters.first),
        ),
        title: Text(
          members[i].$1,
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        subtitle: Text(members[i].$2),
        trailing: Text(
          members[i].$3,
          style: TextStyle(
            color: i < 2 ? AppColors.primary : AppColors.muted,
            fontSize: 12,
          ),
        ),
      ),
    ),
  );
}

class TeamManagePage extends StatelessWidget {
  const TeamManagePage({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('车队管理')),
    body: ListView(
      padding: const EdgeInsets.all(22),
      children: [
        const TlxCard(
          color: Color(0xFFF8FAFD),
          child: Column(
            children: [
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(LucideIcons.penLine, color: AppColors.primary),
                title: Text('编辑车队资料'),
                trailing: Icon(LucideIcons.chevronRight),
              ),
              Divider(),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(LucideIcons.userCheck, color: AppColors.primary),
                title: Text('入队审核'),
                trailing: Badge(label: Text('3')),
              ),
              Divider(),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(LucideIcons.users, color: AppColors.primary),
                title: Text('成员与权限'),
                trailing: Icon(LucideIcons.chevronRight),
              ),
              Divider(),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(LucideIcons.megaphone, color: AppColors.primary),
                title: Text('发布车队公告'),
                trailing: Icon(LucideIcons.chevronRight),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        OutlinedButton(
          onPressed: () {},
          style: OutlinedButton.styleFrom(foregroundColor: AppColors.danger),
          child: const Text('退出车队'),
        ),
      ],
    ),
  );
}

class _Action extends StatelessWidget {
  const _Action({required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: Column(
      children: [
        CircleAvatar(
          backgroundColor: AppColors.primarySoft,
          child: Icon(icon, color: AppColors.primary),
        ),
        const SizedBox(height: 7),
        Text(label, style: const TextStyle(fontSize: 12)),
      ],
    ),
  );
}
