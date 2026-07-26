import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/app_services.dart';

class BadgePage extends StatefulWidget {
  const BadgePage({super.key});
  @override
  State<BadgePage> createState() => _BadgePageState();
}

class _BadgePageState extends State<BadgePage> {
  bool loading = true;
  String? error;
  List<Map<String, dynamic>> earned = [];
  List<Map<String, dynamic>> locked = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    if (mounted) setState(() => loading = true);
    try {
      final data = await GrowthService(context.read<AppSession>().api).badges();
      earned = _maps(data['earned']);
      locked = _maps(data['locked']);
      error = null;
    } catch (exception) {
      error = '$exception';
    }
    if (mounted) setState(() => loading = false);
  }

  List<Map<String, dynamic>> _maps(Object? source) =>
      (source as List? ?? const [])
          .map((item) => Map<String, dynamic>.from(item as Map))
          .toList();

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('我的勋章')),
    body: AsyncPanel(
      loading: loading,
      error: error,
      onRetry: load,
      child: RefreshIndicator(
        onRefresh: load,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
          children: [
            _BadgeSummary(
              earned: earned.length,
              total: earned.length + locked.length,
            ),
            const SizedBox(height: 22),
            if (earned.isNotEmpty) ...[
              const _BadgeSectionTitle(title: '已经点亮', subtitle: '每一枚都来自真实成就'),
              const SizedBox(height: 10),
              ...earned.map(
                (badge) => _BadgeTile(badge: badge, unlocked: true),
              ),
              const SizedBox(height: 16),
            ],
            const _BadgeSectionTitle(title: '继续探索', subtitle: '完成条件后由系统自动点亮'),
            const SizedBox(height: 10),
            ...locked.map((badge) => _BadgeTile(badge: badge, unlocked: false)),
          ],
        ),
      ),
    ),
  );
}

class _BadgeSummary extends StatelessWidget {
  const _BadgeSummary({required this.earned, required this.total});
  final int earned;
  final int total;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      gradient: const LinearGradient(
        colors: [Color(0xFF2F80ED), Color(0xFF56A8FF)],
      ),
      borderRadius: BorderRadius.circular(24),
    ),
    child: Row(
      children: [
        Container(
          width: 66,
          height: 66,
          decoration: const BoxDecoration(
            color: Colors.white24,
            shape: BoxShape.circle,
          ),
          child: const Icon(LucideIcons.medal, size: 34, color: Colors.white),
        ),
        const SizedBox(width: 18),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '我的荣誉足迹',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 19,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 7),
              Text(
                '已点亮 $earned / $total 枚',
                style: const TextStyle(color: Colors.white70),
              ),
              const SizedBox(height: 10),
              LinearProgressIndicator(
                value: total == 0 ? 0 : earned / total,
                minHeight: 7,
                borderRadius: BorderRadius.circular(9),
                color: Colors.white,
                backgroundColor: Colors.white24,
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _BadgeSectionTitle extends StatelessWidget {
  const _BadgeSectionTitle({required this.title, required this.subtitle});
  final String title;
  final String subtitle;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisAlignment: MainAxisAlignment.spaceBetween,
    children: [
      Text(
        title,
        style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
      ),
      Text(
        subtitle,
        style: const TextStyle(fontSize: 11, color: AppColors.muted),
      ),
    ],
  );
}

class _BadgeTile extends StatelessWidget {
  const _BadgeTile({required this.badge, required this.unlocked});
  final Map<String, dynamic> badge;
  final bool unlocked;

  String get code => '${badge['badgeCode'] ?? ''}';
  IconData get icon {
    if (code.contains('INVITE')) return LucideIcons.usersRound;
    if (code.contains('DISTANCE')) return LucideIcons.route;
    if (code.contains('HELP')) return LucideIcons.handHeart;
    if (code.contains('G318')) return LucideIcons.mountain;
    if (code.contains('SEASONS')) return LucideIcons.leaf;
    return LucideIcons.compass;
  }

  @override
  Widget build(BuildContext context) {
    final current = (badge['currentValue'] as num?)?.toInt() ?? 0;
    final threshold = (badge['threshold'] as num?)?.toInt() ?? 1;
    final progress = threshold <= 0
        ? 0.0
        : (current / threshold).clamp(0.0, 1.0);
    return Padding(
      padding: const EdgeInsets.only(bottom: 11),
      child: TlxCard(
        color: unlocked ? const Color(0xFFFFF8E9) : const Color(0xFFF7F8FA),
        child: Row(
          children: [
            Container(
              width: 58,
              height: 58,
              decoration: BoxDecoration(
                color: unlocked
                    ? const Color(0xFFFFE3A3)
                    : const Color(0xFFE7EAF0),
                shape: BoxShape.circle,
              ),
              child: Icon(
                icon,
                color: unlocked ? const Color(0xFFB76B00) : AppColors.muted,
                size: 28,
              ),
            ),
            const SizedBox(width: 15),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '${badge['badgeName'] ?? ''}',
                          style: const TextStyle(
                            fontWeight: FontWeight.w800,
                            fontSize: 15,
                          ),
                        ),
                      ),
                      Text(
                        unlocked ? '已获得' : '$current / $threshold',
                        style: TextStyle(
                          fontSize: 11,
                          color: unlocked ? AppColors.success : AppColors.muted,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 5),
                  Text(
                    '${badge['conditionDescription'] ?? ''}',
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.muted,
                    ),
                  ),
                  const SizedBox(height: 9),
                  LinearProgressIndicator(
                    value: unlocked ? 1 : progress,
                    minHeight: 5,
                    borderRadius: BorderRadius.circular(8),
                    color: unlocked
                        ? const Color(0xFFF2A93B)
                        : AppColors.primary,
                    backgroundColor: const Color(0xFFE4E8EE),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class CouponWalletPage extends StatefulWidget {
  const CouponWalletPage({super.key});
  @override
  State<CouponWalletPage> createState() => _CouponWalletPageState();
}

class _CouponWalletPageState extends State<CouponWalletPage> {
  int tab = 0;
  bool loading = true;
  List<Map<String, dynamic>> coupons = [];
  List<Map<String, dynamic>> claimable = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final service = CouponWalletService(context.read<AppSession>().api);
      final status = ['AVAILABLE', 'USED', 'EXPIRED'][tab];
      coupons = await service.mine(status);
      claimable = tab == 0 ? await service.claimable() : const [];
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> claim(String id) async {
    try {
      await CouponWalletService(context.read<AppSession>().api).claim(id);
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('领取成功，已放入券包')));
      await load();
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  Future<void> showVerificationCode(Map<String, dynamic> coupon) async {
    try {
      final session = context.read<AppSession>();
      final service = CouponWalletService(session.api);
      final detail = await service.detail('${coupon['id']}');
      final merchantId = detail['issuerId'];
      if (merchantId == null) throw Exception('该平台通用券无需到店扫码核销');
      final result = await service.createVerificationCode({
        'bizType': 'COUPON',
        'bizId': coupon['id'],
        'userId': session.userId,
        'merchantId': merchantId,
        'userCouponId': coupon['id'],
        'amount': detail['discountAmount'] ?? 0,
        'expireAt': DateTime.now()
            .add(const Duration(minutes: 10))
            .toIso8601String(),
        'requestId':
            'APP-COUPON-${coupon['id']}-${DateTime.now().millisecondsSinceEpoch}',
      });
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('到店核销码'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                LucideIcons.qrCode,
                size: 72,
                color: AppColors.primary,
              ),
              const SizedBox(height: 14),
              SelectableText(
                '${result['verificationCode']}',
                style: const TextStyle(
                  fontSize: 25,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                '10 分钟内向商家出示',
                style: TextStyle(color: AppColors.muted),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('完成'),
            ),
          ],
        ),
      );
    } catch (e) {
      if (mounted)
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('我的券包')),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 22),
          child: SegmentedButton<int>(
            segments: const [
              ButtonSegment(value: 0, label: Text('可使用')),
              ButtonSegment(value: 1, label: Text('已使用')),
              ButtonSegment(value: 2, label: Text('已过期')),
            ],
            selected: {tab},
            onSelectionChanged: (v) {
              setState(() => tab = v.first);
              load();
            },
            showSelectedIcon: false,
          ),
        ),
        Expanded(
          child: loading
              ? const Center(child: CircularProgressIndicator())
              : RefreshIndicator(
                  onRefresh: load,
                  child: ListView(
                    padding: const EdgeInsets.all(22),
                    children: [
                      if (tab == 0 && claimable.isNotEmpty) ...[
                        const Text(
                          '可领取的平台合作券',
                          style: TextStyle(
                            fontSize: 17,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 10),
                        ...claimable.map(
                          (item) => Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: _Coupon(
                              value: '¥${item['discountAmount']}',
                              title: '${item['couponName']}',
                              desc:
                                  '领取后 ${item['validDays']} 天有效 · 剩余 ${(item['totalQuantity'] as num? ?? 0).toInt() - (item['claimedQuantity'] as num? ?? 0).toInt()}',
                              action: '领取',
                              onTap: () => claim('${item['templateId']}'),
                            ),
                          ),
                        ),
                        const SizedBox(height: 14),
                      ],
                      if (coupons.isNotEmpty)
                        ...coupons.map(
                          (item) => Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: _Coupon(
                              value: '¥${item['discountAmount']}',
                              title: '${item['couponName']}',
                              desc:
                                  '${item['couponStatus']} · 有效至 ${item['validEndAt']}',
                              action: tab == 0 ? '出示核销码' : '',
                              onTap: () => showVerificationCode(item),
                            ),
                          ),
                        ),
                      if (coupons.isEmpty && claimable.isEmpty) ...[
                        const SizedBox(height: 120),
                        const Icon(
                          LucideIcons.ticketX,
                          size: 48,
                          color: AppColors.muted,
                        ),
                        const SizedBox(height: 12),
                        Center(
                          child: Text(
                            tab == 0
                                ? '暂无可用或可领取优惠券'
                                : tab == 1
                                ? '暂无已使用优惠券'
                                : '暂无已过期优惠券',
                            style: const TextStyle(color: AppColors.muted),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
        ),
      ],
    ),
  );
}

class _Coupon extends StatelessWidget {
  const _Coupon({
    required this.value,
    required this.title,
    required this.desc,
    required this.action,
    required this.onTap,
  });
  final String value, title, desc, action;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => TlxCard(
    color: const Color(0xFFFFF8F0),
    child: Row(
      children: [
        SizedBox(
          width: 78,
          child: Text(
            value,
            style: const TextStyle(
              color: AppColors.danger,
              fontSize: 26,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
        Container(width: 1, height: 56, color: const Color(0xFFFFD7B0)),
        const SizedBox(width: 16),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 7),
              Text(
                desc,
                style: const TextStyle(color: AppColors.muted, fontSize: 12),
              ),
            ],
          ),
        ),
        if (action.isNotEmpty)
          TextButton(onPressed: onTap, child: Text(action)),
      ],
    ),
  );
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});
  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  bool loading = true;
  String? error;
  Map<String, dynamic> profile = const {};
  Map<String, dynamic> account = const {};
  List<dynamic> vehicles = const [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final api = context.read<AppSession>().api;
      final values = await Future.wait<dynamic>([
        UserProfileService(api).me(),
        UserProfileService(api).account(),
        VehicleService(api).mine(),
      ]);
      if (!mounted) return;
      setState(() {
        profile = Map<String, dynamic>.from(values[0] as Map);
        account = Map<String, dynamic>.from(values[1] as Map);
        vehicles = List<dynamic>.from(values[2] as List);
      });
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final certified =
        profile['drivingLicenseCertificationStatus']?.toString() ??
        'UNSUBMITTED';
    final approvedVehicles = vehicles
        .where((item) => item.status == 'APPROVED')
        .length;
    return Scaffold(
      appBar: AppBar(title: const Text('设置与个人信息')),
      body: loading && profile.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: load,
              child: ListView(
                padding: const EdgeInsets.all(22),
                children: [
                  if (error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(
                        error!,
                        style: const TextStyle(color: AppColors.danger),
                      ),
                    ),
                  _SettingCard(
                    title: '基本信息',
                    children: [
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text('头像与昵称'),
                        subtitle: Text(
                          '${profile['nickname'] ?? '同路行用户'} · ${profile['tongluxingId'] ?? ''}',
                        ),
                      ),
                      const Divider(),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('手机号'),
                        trailing: Text(account['phone']?.toString() ?? '未获取'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  _SettingCard(
                    title: '认证状态',
                    children: [
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Icon(
                          LucideIcons.contact,
                          color: certified == 'APPROVED'
                              ? AppColors.success
                              : AppColors.warning,
                        ),
                        title: const Text('驾驶证认证'),
                        trailing: Text(_certificationLabel(certified)),
                      ),
                      const Divider(),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: Icon(
                          LucideIcons.carFront,
                          color: approvedVehicles > 0
                              ? AppColors.success
                              : AppColors.warning,
                        ),
                        title: const Text('车辆认证'),
                        trailing: Text('$approvedVehicles 辆已认证'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  _SettingCard(
                    title: '其他',
                    children: [
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(LucideIcons.shieldCheck),
                        title: const Text('隐私与权限'),
                        subtitle: const Text('公开资料、行程定位与消息通知'),
                        trailing: const Icon(LucideIcons.chevronRight),
                        onTap: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const PrivacyPermissionsPage(),
                          ),
                        ),
                      ),
                      const Divider(),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('关于同路行'),
                        trailing: const Text('v1.0.0'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
    );
  }

  String _certificationLabel(String value) => switch (value) {
    'APPROVED' => '已认证',
    'PENDING' => '审核中',
    'REJECTED' => '未通过',
    _ => '未认证',
  };
}

class PrivacyPermissionsPage extends StatefulWidget {
  const PrivacyPermissionsPage({super.key});

  @override
  State<PrivacyPermissionsPage> createState() => _PrivacyPermissionsPageState();
}

class _PrivacyPermissionsPageState extends State<PrivacyPermissionsPage> {
  Map<String, dynamic> settings = const {};
  bool loading = true;
  bool saving = false;
  String? error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    try {
      final value = await UserProfileService(
        context.read<AppSession>().api,
      ).privacySettings();
      if (mounted) setState(() => settings = value);
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> update(String key, Object value) async {
    if (saving) return;
    final before = settings;
    setState(() {
      saving = true;
      settings = {...settings, key: value};
    });
    try {
      final result = await UserProfileService(
        context.read<AppSession>().api,
      ).updatePrivacySettings({key: value});
      if (mounted) setState(() => settings = result);
    } catch (e) {
      if (mounted) {
        setState(() => settings = before);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('隐私与权限')),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : error != null && settings.isEmpty
        ? Center(
            child: FilledButton(onPressed: load, child: const Text('重新加载')),
          )
        : ListView(
            padding: const EdgeInsets.all(20),
            children: [
              _SettingCard(
                title: '公开资料',
                children: [
                  _privacySwitch(
                    '公开个人主页',
                    '关闭后，其他用户无法查看你的资料主页',
                    settings['profileVisibility'] == 'PUBLIC',
                    (value) => update(
                      'profileVisibility',
                      value ? 'PUBLIC' : 'PRIVATE',
                    ),
                  ),
                  const Divider(),
                  _privacySwitch(
                    '展示常驻城市',
                    '在个人主页展示城市信息',
                    settings['cityVisible'] == true,
                    (value) => update('cityVisible', value),
                  ),
                  const Divider(),
                  _privacySwitch(
                    '展示个人简介',
                    '在个人主页展示自我介绍',
                    settings['bioVisible'] == true,
                    (value) => update('bioVisible', value),
                  ),
                  const Divider(),
                  _privacySwitch(
                    '展示行程数据',
                    '公开行程次数、累计里程等数据',
                    settings['tripStatsVisible'] == true,
                    (value) => update('tripStatsVisible', value),
                  ),
                  const Divider(),
                  _privacySwitch(
                    '展示用户等级',
                    '公开当前等级和勋章',
                    settings['levelVisible'] == true,
                    (value) => update('levelVisible', value),
                  ),
                  const Divider(),
                  _privacySwitch(
                    '展示主要车型',
                    '公开默认车辆的品牌、型号和车辆类型',
                    settings['vehicleVisibility'] == 'PUBLIC',
                    (value) => update(
                      'vehicleVisibility',
                      value ? 'PUBLIC' : 'PRIVATE',
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              _SettingCard(
                title: '应用权限',
                children: [
                  _privacySwitch(
                    '行程定位',
                    '用于导航、轨迹与队伍位置共享',
                    settings['locationEnabled'] == true,
                    (value) => update('locationEnabled', value),
                  ),
                  const Divider(),
                  _privacySwitch(
                    '消息通知',
                    '行程提醒、聊天和系统通知',
                    settings['notificationEnabled'] == true,
                    (value) => update('notificationEnabled', value),
                  ),
                  const Divider(),
                  _privacySwitch(
                    '允许同行邀请',
                    '允许其他用户向你发送同行邀请',
                    settings['inviteEnabled'] == true,
                    (value) => update('inviteEnabled', value),
                  ),
                ],
              ),
            ],
          ),
  );

  Widget _privacySwitch(
    String title,
    String subtitle,
    bool value,
    ValueChanged<bool> onChanged,
  ) => SwitchListTile(
    contentPadding: EdgeInsets.zero,
    title: Text(title),
    subtitle: Text(subtitle),
    value: value,
    onChanged: saving ? null : onChanged,
  );
}

class _SettingCard extends StatelessWidget {
  const _SettingCard({required this.title, required this.children});
  final String title;
  final List<Widget> children;
  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Padding(
        padding: const EdgeInsets.only(left: 4, bottom: 9),
        child: Text(title, style: const TextStyle(fontWeight: FontWeight.w700)),
      ),
      TlxCard(
        color: const Color(0xFFF8FAFD),
        child: Column(children: children),
      ),
    ],
  );
}
