import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/routes.dart';
import '../../../app/theme.dart';
import '../../../data/services/app_services.dart';
import '../../shop/pages/shop_home_page.dart';
import 'customer_support_page.dart';
import 'driving_license_page.dart';
import 'invite_page.dart';
import 'merchant_onboarding_page.dart';
import 'profile_extra_pages.dart';
import 'follow_relations_page.dart';
import 'profile_system_pages.dart';
import 'vehicle_list_page.dart';

class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => ProfilePageState();
}

class ProfilePageState extends State<ProfilePage> {
  Map<String, dynamic> profile = const {};
  Map<String, dynamic>? merchantApplication;
  String avatarUrl = '';
  bool loading = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> refresh() => load();

  Future<void> load() async {
    if (loading) return;
    loading = true;
    try {
      final api = context.read<AppSession>().api;
      final values = await Future.wait<dynamic>([
        UserProfileService(api).me(),
        MerchantOnboardingService(api).latest(),
      ]);
      final nextProfile = Map<String, dynamic>.from(values[0] as Map);
      final avatarKey = nextProfile['avatarImageKey']?.toString() ?? '';
      var nextAvatarUrl = '';
      if (avatarKey.isNotEmpty) {
        try {
          nextAvatarUrl = await StorageUploadService(
            api,
          ).downloadUrlByObjectKey(avatarKey);
        } catch (_) {
          nextAvatarUrl = '';
        }
      }
      if (mounted) {
        setState(() {
          profile = nextProfile;
          merchantApplication = values[1] as Map<String, dynamic>?;
          avatarUrl = nextAvatarUrl;
        });
      }
    } catch (_) {
      // 网络抖动时保留上一次成功加载的个人资料。
    } finally {
      loading = false;
    }
  }

  Future<void> _open(Widget page, {bool refreshAfter = false}) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => page));
    if (refreshAfter && mounted) await load();
  }

  Future<void> _copyTongluxingId(String value) async {
    if (value.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: value));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('同路行号已复制')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<AppSession>();
    final nickname = profile['nickname']?.toString().isNotEmpty == true
        ? profile['nickname'].toString()
        : '同路行用户';
    final approvedMerchant = merchantApplication?['auditStatus'] == 'APPROVED';
    final tongluxingId = profile['tongluxingId']?.toString().trim() ?? '';

    void openPublicProfile() => _open(
      PublicProfilePage(userId: session.userId ?? '0'),
      refreshAfter: true,
    );

    return SafeArea(
      child: ColoredBox(
        color: const Color(0xFFF3F5F8),
        child: RefreshIndicator(
          onRefresh: refresh,
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
            children: [
              Row(
                children: [
                  _HeaderButton(
                    tooltip: '查看个人主页',
                    icon: LucideIcons.userSearch,
                    onTap: openPublicProfile,
                  ),
                  const Spacer(),
                  Container(
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(22),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          tooltip: '编辑个人资料',
                          constraints: const BoxConstraints.tightFor(
                            width: 42,
                            height: 42,
                          ),
                          padding: EdgeInsets.zero,
                          onPressed: () => _open(
                            const EditProfilePage(),
                            refreshAfter: true,
                          ),
                          icon: const Icon(LucideIcons.pencil, size: 18),
                        ),
                        IconButton(
                          tooltip: '设置',
                          constraints: const BoxConstraints.tightFor(
                            width: 42,
                            height: 42,
                          ),
                          padding: EdgeInsets.zero,
                          onPressed: () => _open(const SettingsPage()),
                          icon: const Icon(LucideIcons.settings, size: 18),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Center(
                child: InkWell(
                  onTap: openPublicProfile,
                  customBorder: const CircleBorder(),
                  child: CircleAvatar(
                    radius: 50,
                    backgroundColor: const Color(0xFFDDEBFF),
                    backgroundImage: avatarUrl.isEmpty
                        ? null
                        : NetworkImage(avatarUrl),
                    child: avatarUrl.isEmpty
                        ? const Icon(
                            LucideIcons.userRound,
                            size: 42,
                            color: AppColors.primary,
                          )
                        : null,
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Flexible(
                    child: Text(
                      nickname,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 23,
                        height: 1.2,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                  if (approvedMerchant) ...[
                    const SizedBox(width: 7),
                    const Icon(
                      LucideIcons.badgeCheck,
                      size: 19,
                      color: AppColors.success,
                    ),
                  ],
                ],
              ),
              if (tongluxingId.isNotEmpty) ...[
                const SizedBox(height: 7),
                Center(
                  child: InkWell(
                    onTap: () => _copyTongluxingId(tongluxingId),
                    borderRadius: BorderRadius.circular(8),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            '同路行号：$tongluxingId',
                            style: const TextStyle(
                              color: AppColors.secondaryText,
                              fontSize: 12.5,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const SizedBox(width: 5),
                          const Icon(
                            LucideIcons.copy,
                            size: 14,
                            color: AppColors.muted,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 24),
              _MenuGroup(
                items: [
                  _Menu(
                    LucideIcons.usersRound,
                    '关注与粉丝',
                    () => _open(const FollowRelationsPage()),
                  ),
                  _Menu(
                    LucideIcons.contact,
                    '驾驶证认证',
                    () => _open(const DrivingLicensePage(), refreshAfter: true),
                  ),
                  _Menu(
                    LucideIcons.carFront,
                    '我的车辆',
                    () => _open(const VehicleListPage()),
                  ),
                  _Menu(
                    LucideIcons.medal,
                    '我的勋章',
                    () => _open(const BadgePage()),
                  ),
                  _Menu(
                    LucideIcons.userPlus,
                    '邀请好友',
                    () => _open(const InvitePage()),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _MenuGroup(
                items: [
                  _Menu(
                    LucideIcons.store,
                    '沿途商城',
                    () => _open(
                      const Scaffold(
                        appBar: _ShopAppBar(),
                        body: ShopHomePage(),
                      ),
                    ),
                  ),
                  _Menu(
                    LucideIcons.ticket,
                    '我的券包',
                    () => _open(const CouponWalletPage()),
                  ),
                  _Menu(
                    LucideIcons.badgeCheck,
                    approvedMerchant ? '商家认证资料' : '成为商家',
                    () => _open(
                      const MerchantOnboardingPage(),
                      refreshAfter: true,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              _MenuGroup(
                items: [
                  _Menu(
                    LucideIcons.userRoundCog,
                    '个人资料',
                    () => _open(const EditProfilePage(), refreshAfter: true),
                  ),
                  _Menu(
                    LucideIcons.shieldCheck,
                    '认证状态',
                    () => _open(const SettingsPage()),
                  ),
                  _Menu(
                    LucideIcons.settings,
                    '设置',
                    () => _open(const SettingsPage()),
                  ),
                  _Menu(
                    LucideIcons.headphones,
                    '客服与投诉',
                    () => _open(const CustomerSupportPage()),
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
                  }, danger: true),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HeaderButton extends StatelessWidget {
  const _HeaderButton({
    required this.tooltip,
    required this.icon,
    required this.onTap,
  });
  final String tooltip;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 42,
    height: 42,
    child: Material(
      color: Colors.white,
      shape: const CircleBorder(),
      child: IconButton(
        tooltip: tooltip,
        padding: EdgeInsets.zero,
        onPressed: onTap,
        icon: Icon(icon, size: 20),
      ),
    ),
  );
}

class _ShopAppBar extends StatelessWidget implements PreferredSizeWidget {
  const _ShopAppBar();

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) => AppBar(title: const Text('沿途商城'));
}

class _Menu {
  const _Menu(this.icon, this.label, this.onTap, {this.danger = false});
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool danger;
}

class _MenuGroup extends StatelessWidget {
  const _MenuGroup({required this.items});
  final List<_Menu> items;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(22),
    clipBehavior: Clip.antiAlias,
    child: Column(
      children: [
        for (var index = 0; index < items.length; index++) ...[
          _MenuRow(item: items[index]),
          if (index != items.length - 1)
            const Divider(
              height: 1,
              indent: 58,
              endIndent: 16,
              color: Color(0xFFE8EBF0),
            ),
        ],
      ],
    ),
  );
}

class _MenuRow extends StatelessWidget {
  const _MenuRow({required this.item});
  final _Menu item;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: item.onTap,
    child: SizedBox(
      height: 52,
      child: Row(
        children: [
          const SizedBox(width: 18),
          Icon(
            item.icon,
            size: 20,
            color: item.danger ? AppColors.danger : AppColors.primaryDark,
          ),
          const SizedBox(width: 18),
          Expanded(
            child: Text(
              item.label,
              style: TextStyle(
                fontSize: 15,
                color: item.danger ? AppColors.danger : AppColors.text,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
          Icon(
            LucideIcons.chevronRight,
            size: 18,
            color: item.danger ? AppColors.danger : AppColors.muted,
          ),
          const SizedBox(width: 16),
        ],
      ),
    ),
  );
}
