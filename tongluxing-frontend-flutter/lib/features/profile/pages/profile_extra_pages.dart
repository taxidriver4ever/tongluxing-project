import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';

class BadgePage extends StatelessWidget {
  const BadgePage({super.key});
  static const badges = [
    ('初次启程', '完成首次自驾行程', '🧭', true),
    ('千里同行', '累计驾驶 1000 公里', '🏔️', true),
    ('车队核心', '参与 10 次车队活动', '🚙', true),
    ('高原探索者', '完成海拔 3000 米路线', '🏕️', false),
    ('邀请达人', '成功邀请 20 位好友', '🤝', false),
    ('环国旅行家', '点亮 20 个省份', '🗺️', false),
  ];

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('我的勋章')),
    body: GridView.builder(
      padding: const EdgeInsets.all(22),
      itemCount: badges.length,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: .92,
        crossAxisSpacing: 12,
        mainAxisSpacing: 12,
      ),
      itemBuilder: (_, i) => Opacity(
        opacity: badges[i].$4 ? 1 : .48,
        child: TlxCard(
          color: badges[i].$4
              ? const Color(0xFFFFF7E8)
              : const Color(0xFFF0F2F5),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(badges[i].$3, style: const TextStyle(fontSize: 42)),
              const SizedBox(height: 10),
              Text(
                badges[i].$1,
                style: const TextStyle(fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 5),
              Text(
                badges[i].$2,
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppColors.muted, fontSize: 11),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class CouponWalletPage extends StatefulWidget {
  const CouponWalletPage({super.key});
  @override
  State<CouponWalletPage> createState() => _CouponWalletPageState();
}

class _CouponWalletPageState extends State<CouponWalletPage> {
  int tab = 0;
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
            onSelectionChanged: (v) => setState(() => tab = v.first),
            showSelectedIcon: false,
          ),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(22),
            children: tab == 0
                ? const [
                    _Coupon(
                      value: '¥20',
                      title: '养车服务满减券',
                      desc: '满 100 元可用 · 7 天后过期',
                    ),
                    SizedBox(height: 14),
                    _Coupon(
                      value: '9折',
                      title: '露营装备折扣券',
                      desc: '最高优惠 50 元 · 全场可用',
                    ),
                    SizedBox(height: 14),
                    _Coupon(value: '¥30', title: '新旅程加油礼', desc: '指定合作加油站可用'),
                  ]
                : [
                    const SizedBox(height: 120),
                    const Icon(
                      LucideIcons.ticketX,
                      size: 48,
                      color: AppColors.muted,
                    ),
                    const SizedBox(height: 12),
                    Center(
                      child: Text(
                        tab == 1 ? '暂无已使用优惠券' : '暂无已过期优惠券',
                        style: const TextStyle(color: AppColors.muted),
                      ),
                    ),
                  ],
          ),
        ),
      ],
    ),
  );
}

class _Coupon extends StatelessWidget {
  const _Coupon({required this.value, required this.title, required this.desc});
  final String value, title, desc;
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
        TextButton(onPressed: () {}, child: const Text('去使用')),
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
  bool location = true;
  bool notification = true;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('设置与个人信息')),
    body: ListView(
      padding: const EdgeInsets.all(22),
      children: [
        const _SettingCard(
          title: '基本信息',
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('头像与昵称'),
              subtitle: Text('林宇 · 同路行号 TLX10086'),
              trailing: Icon(LucideIcons.chevronRight),
            ),
            Divider(),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('手机号'),
              trailing: Text('138****8000'),
            ),
            Divider(),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('微信账号'),
              trailing: Text('已绑定', style: TextStyle(color: AppColors.success)),
            ),
          ],
        ),
        const SizedBox(height: 16),
        const _SettingCard(
          title: '认证状态',
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(LucideIcons.badgeCheck, color: AppColors.success),
              title: Text('实名认证'),
              trailing: Text('已认证'),
            ),
            Divider(),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(LucideIcons.contact, color: AppColors.success),
              title: Text('驾驶证认证'),
              trailing: Text('已认证'),
            ),
            Divider(),
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(LucideIcons.carFront, color: AppColors.warning),
              title: Text('车辆认证'),
              trailing: Text('1 辆审核中'),
            ),
          ],
        ),
        const SizedBox(height: 16),
        TlxCard(
          color: const Color(0xFFF8FAFD),
          child: Column(
            children: [
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('行程定位'),
                subtitle: const Text('用于导航、轨迹和队伍位置'),
                value: location,
                onChanged: (v) => setState(() => location = v),
              ),
              const Divider(),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('消息通知'),
                subtitle: const Text('行程提醒、聊天和优惠通知'),
                value: notification,
                onChanged: (v) => setState(() => notification = v),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        const _SettingCard(
          title: '其他',
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('隐私与权限'),
              trailing: Icon(LucideIcons.chevronRight),
            ),
            Divider(),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('清理缓存'),
              trailing: Text('18.6 MB'),
            ),
            Divider(),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('关于同路行'),
              trailing: Text('v1.0.0'),
            ),
          ],
        ),
      ],
    ),
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
