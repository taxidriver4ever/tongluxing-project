import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/routes.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import 'growth_page.dart';
import 'invite_page.dart';
import 'profile_extra_pages.dart';
import 'vehicle_list_page.dart';
import '../../team/pages/team_page.dart';
import '../../shop/pages/shop_home_page.dart';
import '../../../data/services/app_services.dart';
import 'profile_system_pages.dart';
import 'merchant_onboarding_page.dart';
import 'customer_support_page.dart';
import 'driving_license_page.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});
  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  Map<String, dynamic> profile = const {};
  Map<String, dynamic> growth = const {};
  Map<String, dynamic>? merchantApplication;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    try {
      final api = context.read<AppSession>().api;
      final v = await Future.wait<dynamic>([
        UserProfileService(api).me(),
        GrowthService(api).summary(),
        MerchantOnboardingService(api).latest(),
      ]);
      if (mounted) {
        setState(() {
          profile = v[0];
          growth = v[1];
          merchantApplication = v[2] as Map<String, dynamic>?;
        });
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<AppSession>();
    return SafeArea(
      child: ListView(
        padding: const EdgeInsets.only(bottom: 28),
        children: [
          const PageHeading(
            '我的',
            action: Icon(LucideIcons.bell, color: AppColors.primary),
          ),
          InkWell(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) =>
                    PublicProfilePage(userId: session.userId ?? '0'),
              ),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Row(
                children: [
                  const CircleAvatar(
                    radius: 36,
                    backgroundColor: AppColors.primarySoft,
                    child: Icon(
                      LucideIcons.userRound,
                      size: 30,
                      color: AppColors.primary,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          profile['nickname']?.toString().isNotEmpty == true
                              ? profile['nickname'].toString()
                              : '同路行用户',
                          style: const TextStyle(
                            fontSize: 19,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 7),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.primarySoft,
                            borderRadius: BorderRadius.circular(14),
                          ),
                          child: Text(
                            '${growth['levelCode'] ?? 'LV1'} 自驾同行者',
                            style: const TextStyle(
                              fontSize: 12,
                              color: AppColors.primary,
                            ),
                          ),
                        ),
                        if (merchantApplication?['auditStatus'] ==
                            'APPROVED') ...[
                          const SizedBox(height: 6),
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: const Color(0xFFEAF8F3),
                              borderRadius: BorderRadius.circular(14),
                            ),
                            child: const Text(
                              '认证商家',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColors.success,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                        ],
                        const SizedBox(height: 6),
                        Text(
                          'UID ${session.userId ?? '—'}',
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
            ),
          ),
          const SizedBox(height: 24),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 22),
            child: _GrowthRibbon(
              points: (growth['totalPoints'] as num?)?.toInt() ?? 0,
              next: (growth['nextLevelPoints'] as num?)?.toInt() ?? 500,
              level: growth['levelCode']?.toString() ?? 'LV1',
            ),
          ),
          const SizedBox(height: 20),
          _Section(
            title: '驾驶与成长',
            items: [
              _Menu(
                LucideIcons.contact,
                '驾驶证认证',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const DrivingLicensePage()),
                ).then((_) => load()),
              ),
              _Menu(
                LucideIcons.carFront,
                '我的车辆',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const VehicleListPage()),
                ),
              ),
              _Menu(
                LucideIcons.trendingUp,
                '成长中心',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const GrowthPage()),
                ),
              ),
              _Menu(
                LucideIcons.medal,
                '我的勋章',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const BadgePage()),
                ),
              ),
              _Menu(
                LucideIcons.userPlus,
                '邀请好友',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const InvitePage()),
                ),
              ),
            ],
          ),
          _Section(
            title: '车队与消费',
            items: [
              _Menu(
                LucideIcons.usersRound,
                '我的车队',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const TeamPage()),
                ),
              ),
              _Menu(
                LucideIcons.store,
                '沿途商城',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => Scaffold(
                      appBar: AppBar(title: const Text('沿途商城')),
                      body: const ShopHomePage(),
                    ),
                  ),
                ),
              ),
              _Menu(
                LucideIcons.ticket,
                '我的券包',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const CouponWalletPage()),
                ),
              ),
            ],
          ),
          _Section(
            title: '账户与服务',
            items: [
              _Menu(
                LucideIcons.store,
                merchantApplication?['auditStatus'] == 'APPROVED'
                    ? '商家认证资料'
                    : '成为商家',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const MerchantOnboardingPage(),
                  ),
                ),
              ),
              _Menu(
                LucideIcons.userRoundCog,
                '个人资料',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const EditProfilePage()),
                ),
              ),
              _Menu(
                LucideIcons.shieldCheck,
                '认证状态',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const SettingsPage()),
                ),
              ),
              _Menu(
                LucideIcons.settings,
                '设置',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const SettingsPage()),
                ),
              ),
              _Menu(
                LucideIcons.headphones,
                '客服与投诉',
                () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const CustomerSupportPage(),
                  ),
                ),
              ),
              _Menu(LucideIcons.logOut, '退出登录', () async {
                await session.logout();
                if (context.mounted) {
                  Navigator.pushNamedAndRemoveUntil(
                    context,
                    AppRoutes.login,
                    (_) => false,
                  );
                }
              }),
            ],
          ),
        ],
      ),
    );
  }
}

class _GrowthRibbon extends StatelessWidget {
  const _GrowthRibbon({
    required this.points,
    required this.next,
    required this.level,
  });
  final int points, next;
  final String level;
  @override
  Widget build(BuildContext context) {
    final progress = points + next == 0 ? 1.0 : points / (points + next);
    return Container(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 17),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        boxShadow: const [
          BoxShadow(
            color: Color(0x14285CFF),
            blurRadius: 24,
            offset: Offset(0, 8),
          ),
        ],
        border: Border.all(color: const Color(0xFFE7EEFA)),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    colors: [Color(0xFF3B82F6), Color(0xFF7C5CFC)],
                  ),
                ),
                child: const Icon(
                  LucideIcons.sparkles,
                  color: Colors.white,
                  size: 21,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '$level 成长旅程',
                      style: const TextStyle(fontWeight: FontWeight.w800),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '$points 成长值',
                      style: const TextStyle(
                        color: AppColors.muted,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
              Text(
                '还差 $next',
                style: const TextStyle(
                  color: AppColors.primary,
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: LinearProgressIndicator(
              value: progress.clamp(0, 1),
              minHeight: 9,
              backgroundColor: const Color(0xFFEAF0FA),
              valueColor: const AlwaysStoppedAnimation(Color(0xFF6C63FF)),
            ),
          ),
        ],
      ),
    );
  }
}

class _Menu {
  const _Menu(this.icon, this.label, this.onTap);
  final IconData icon;
  final String label;
  final VoidCallback onTap;
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.items});
  final String title;
  final List<_Menu> items;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(22, 0, 22, 20),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 12),
        TlxCard(
          color: const Color(0xFFF8FAFD),
          padding: EdgeInsets.zero,
          child: Column(
            children: items
                .map(
                  (item) => ListTile(
                    leading: Container(
                      width: 30,
                      height: 30,
                      decoration: BoxDecoration(
                        color: AppColors.primarySoft,
                        borderRadius: BorderRadius.circular(9),
                      ),
                      child: Icon(
                        item.icon,
                        size: 17,
                        color: AppColors.primary,
                      ),
                    ),
                    title: Text(item.label),
                    trailing: const Icon(
                      LucideIcons.chevronRight,
                      size: 18,
                      color: AppColors.muted,
                    ),
                    onTap: item.onTap,
                  ),
                )
                .toList(),
          ),
        ),
      ],
    ),
  );
}
