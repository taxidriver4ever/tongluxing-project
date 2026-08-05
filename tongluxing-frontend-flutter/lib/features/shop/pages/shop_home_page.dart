import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/app_services.dart';

class ShopHomePage extends StatefulWidget {
  const ShopHomePage({super.key});

  @override
  State<ShopHomePage> createState() => _ShopHomePageState();
}

class _ShopHomePageState extends State<ShopHomePage> {
  int category = 0;
  List<Map<String, dynamic>> merchantOffers = const [];
  List<Map<String, dynamic>> activeGroups = const [];
  static const categories = ['附近服务', '自驾装备', '露营户外', '特惠拼单'];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadOffers());
  }

  Future<void> _loadOffers() async {
    try {
      final api = context.read<AppSession>().api;
      final values = await Future.wait<dynamic>([
        MerchantEcosystemService(api).marketplace(),
        GroupbuyService(api).activities(status: 'WAITING'),
      ]);
      if (mounted) {
        setState(() {
          merchantOffers = values[0];
          activeGroups = values[1];
        });
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) => SafeArea(
    child: CustomScrollView(
      slivers: [
        SliverToBoxAdapter(
          child: Container(
            padding: const EdgeInsets.fromLTRB(22, 22, 22, 24),
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                colors: [Color(0xFF0877D1), Color(0xFF3A86FF)],
              ),
              borderRadius: BorderRadius.vertical(bottom: Radius.circular(30)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '沿途好物',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 28,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          Text(
                            '自驾服务与装备，一站拼购',
                            style: TextStyle(color: Colors.white70),
                          ),
                        ],
                      ),
                    ),
                    _HeaderButton(
                      icon: LucideIcons.usersRound,
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => const _MyGroupbuysPage(),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    _HeaderButton(
                      icon: LucideIcons.receiptText,
                      onTap: () => _showUnavailable(context, '我的订单'),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                TextField(
                  decoration: InputDecoration(
                    hintText: '搜索附近服务、自驾装备',
                    prefixIcon: const Icon(LucideIcons.search),
                    fillColor: Colors.white,
                    suffixIcon: IconButton(
                      onPressed: () {},
                      icon: const Icon(LucideIcons.slidersHorizontal),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
        if (merchantOffers.isNotEmpty) ...[
          const SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.fromLTRB(22, 20, 22, 4),
              child: Text(
                '合作商家优惠',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: SizedBox(
              height: 230,
              child: ListView.separated(
                padding: const EdgeInsets.fromLTRB(22, 10, 22, 10),
                scrollDirection: Axis.horizontal,
                itemCount: merchantOffers.length,
                separatorBuilder: (_, _) => const SizedBox(width: 12),
                itemBuilder: (_, i) =>
                    _MarketOfferCard(data: merchantOffers[i]),
              ),
            ),
          ),
        ],
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(22, 20, 22, 10),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _Quick(icon: LucideIcons.mapPin, label: '附近', onTap: () {}),
                _Quick(
                  icon: LucideIcons.ticket,
                  label: '领券',
                  onTap: () => _showUnavailable(context, '今日优惠'),
                ),
                _Quick(
                  icon: LucideIcons.heart,
                  label: '收藏',
                  onTap: () => _showUnavailable(context, '我的收藏'),
                ),
                _Quick(
                  icon: LucideIcons.headphones,
                  label: '售后',
                  onTap: () => _showUnavailable(context, '退款与售后'),
                ),
              ],
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: SizedBox(
            height: 48,
            child: ListView.separated(
              padding: const EdgeInsets.symmetric(horizontal: 22),
              scrollDirection: Axis.horizontal,
              itemCount: categories.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (_, i) => ChoiceChip(
                label: Text(categories[i]),
                selected: category == i,
                onSelected: (_) => setState(() => category = i),
              ),
            ),
          ),
        ),
        const SliverToBoxAdapter(
          child: Padding(
            padding: EdgeInsets.fromLTRB(22, 20, 22, 4),
            child: Text(
              '热门拼单',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
            ),
          ),
        ),
        if (activeGroups.isEmpty)
          const SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.all(30),
              child: Center(
                child: Text(
                  '暂时没有进行中的拼单',
                  style: TextStyle(color: AppColors.muted),
                ),
              ),
            ),
          )
        else
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(22, 10, 22, 28),
            sliver: SliverGrid.builder(
              itemCount: activeGroups.length,
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                childAspectRatio: .68,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
              ),
              itemBuilder: (_, i) =>
                  _LiveGroupCard(data: activeGroups[i], onChanged: _loadOffers),
            ),
          ),
      ],
    ),
  );

  static void _showUnavailable(BuildContext context, String title) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('$title尚未接入真实接口')),
    );
  }
}

class _MarketOfferCard extends StatelessWidget {
  const _MarketOfferCard({required this.data});
  final Map<String, dynamic> data;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: () => Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => _MarketOfferDetail(data: data)),
    ),
    borderRadius: BorderRadius.circular(22),
    child: Container(
      width: 245,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: const Color(0xFFE8EFF9)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 50,
                height: 50,
                decoration: BoxDecoration(
                  color: AppColors.primarySoft,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(
                  LucideIcons.badgePercent,
                  color: AppColors.primary,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  data['merchantName']?.toString() ?? '',
                  maxLines: 2,
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
              ),
            ],
          ),
          const SizedBox(height: 13),
          Text(
            data['couponName']?.toString() ?? '',
            maxLines: 2,
            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
          ),
          const Spacer(),
          Text(
            '${data['storeName'] ?? ''} · ${data['category'] ?? ''}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 11, color: AppColors.muted),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Text(
                '¥${data['salePrice'] ?? 0}',
                style: const TextStyle(
                  color: AppColors.danger,
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(width: 7),
              Text(
                '¥${data['originalPrice'] ?? 0}',
                style: const TextStyle(
                  color: AppColors.muted,
                  decoration: TextDecoration.lineThrough,
                ),
              ),
            ],
          ),
        ],
      ),
    ),
  );
}

class _MarketOfferDetail extends StatelessWidget {
  const _MarketOfferDetail({required this.data});
  final Map<String, dynamic> data;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('商家优惠详情')),
    body: ListView(
      padding: const EdgeInsets.all(22),
      children: [
        Container(
          height: 190,
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFF2E7BEA), Color(0xFF6AAEFF)],
            ),
            borderRadius: BorderRadius.circular(28),
          ),
          child: const Icon(
            LucideIcons.ticketPercent,
            color: Colors.white,
            size: 72,
          ),
        ),
        const SizedBox(height: 20),
        Text(
          data['couponName']?.toString() ?? '',
          style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 8),
        Text('${data['merchantName'] ?? ''} · ${data['storeName'] ?? ''}'),
        const SizedBox(height: 15),
        Row(
          children: [
            Text(
              '¥${data['salePrice'] ?? 0}',
              style: const TextStyle(
                color: AppColors.danger,
                fontSize: 30,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(width: 10),
            Text(
              '¥${data['originalPrice'] ?? 0}',
              style: const TextStyle(
                color: AppColors.muted,
                decoration: TextDecoration.lineThrough,
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),
        TlxCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('适用门店', style: TextStyle(fontWeight: FontWeight.w800)),
              const SizedBox(height: 8),
              Text(data['storeAddress']?.toString() ?? ''),
              const SizedBox(height: 8),
              Text('营业规则：${data['useInstructions'] ?? ''}'),
            ],
          ),
        ),
        const SizedBox(height: 18),
        FilledButton(
          onPressed: data['groupEnabled'] == true
              ? () => _createGroup(context)
              : null,
          child: Text(
            data['groupEnabled'] == true
                ? '${data['groupPeople'] ?? 2} 人拼单 · 立即发起'
                : '普通优惠券',
          ),
        ),
      ],
    ),
  );

  Future<void> _createGroup(BuildContext context) async {
    try {
      final result = await GroupbuyService(
        context.read<AppSession>().api,
      ).create(data['couponId'].toString());
      if (!context.mounted) return;
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => _GroupDetailPage(initial: result)),
      );
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }
}

class _LiveGroupCard extends StatelessWidget {
  const _LiveGroupCard({required this.data, required this.onChanged});
  final Map<String, dynamic> data;
  final VoidCallback onChanged;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: () => Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => _GroupDetailPage(initial: data)),
    ),
    child: TlxCard(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                color: AppColors.primarySoft,
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Icon(
                LucideIcons.usersRound,
                size: 46,
                color: AppColors.primary,
              ),
            ),
          ),
          const SizedBox(height: 10),
          Text(
            data['couponName']?.toString() ?? '拼单优惠',
            maxLines: 2,
            style: const TextStyle(fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 5),
          Text(
            '${data['merchantName'] ?? ''} · ${data['storeName'] ?? ''}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 10, color: AppColors.muted),
          ),
          const SizedBox(height: 8),
          Text(
            '¥${data['groupPrice'] ?? 0}',
            style: const TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w800,
              color: AppColors.danger,
            ),
          ),
          Text(
            '${data['currentPeople'] ?? 0}/${data['targetPeople'] ?? 0} 人 · 还差 ${((data['targetPeople'] as num?)?.toInt() ?? 0) - ((data['currentPeople'] as num?)?.toInt() ?? 0)} 人',
            style: const TextStyle(fontSize: 11, color: AppColors.primary),
          ),
        ],
      ),
    ),
  );
}

class _MyGroupbuysPage extends StatefulWidget {
  const _MyGroupbuysPage();

  @override
  State<_MyGroupbuysPage> createState() => _MyGroupbuysPageState();
}

class _MyGroupbuysPageState extends State<_MyGroupbuysPage> {
  late Future<List<Map<String, dynamic>>> future;

  @override
  void initState() {
    super.initState();
    future = GroupbuyService(context.read<AppSession>().api).mine();
  }

  void reload() => setState(() {
    future = GroupbuyService(context.read<AppSession>().api).mine();
  });

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('我的拼单')),
    body: FutureBuilder<List<Map<String, dynamic>>>(
      future: future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(
            child: FilledButton.tonal(
              onPressed: reload,
              child: const Text('加载失败，点击重试'),
            ),
          );
        }
        final rows = snapshot.data ?? const [];
        if (rows.isEmpty) {
          return const Center(child: Text('暂无拼单记录'));
        }
        return RefreshIndicator(
          onRefresh: () async {
            reload();
            await future;
          },
          child: ListView.separated(
            padding: const EdgeInsets.all(20),
            itemCount: rows.length,
            separatorBuilder: (_, _) => const SizedBox(height: 12),
            itemBuilder: (_, index) {
              final row = rows[index];
              final current = (row['currentPeople'] as num?)?.toInt() ?? 0;
              final target = (row['targetPeople'] as num?)?.toInt() ?? 0;
              return TlxCard(
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const CircleAvatar(
                    backgroundColor: AppColors.primarySoft,
                    child: Icon(
                      LucideIcons.usersRound,
                      color: AppColors.primary,
                    ),
                  ),
                  title: Text(row['couponName']?.toString() ?? '拼单优惠'),
                  subtitle: Text(
                    '${row['storeName'] ?? ''}  ·  $current/$target 人\n${row['activityStatus'] ?? ''}',
                  ),
                  isThreeLine: true,
                  trailing: const Icon(LucideIcons.chevronRight),
                  onTap: () async {
                    await Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => _GroupDetailPage(initial: row),
                      ),
                    );
                    reload();
                  },
                ),
              );
            },
          ),
        );
      },
    ),
  );
}

class _GroupDetailPage extends StatefulWidget {
  const _GroupDetailPage({required this.initial});
  final Map<String, dynamic> initial;
  @override
  State<_GroupDetailPage> createState() => _GroupDetailPageState();
}

class _GroupDetailPageState extends State<_GroupDetailPage> {
  late Map<String, dynamic> data = widget.initial;
  bool joining = false;
  String label(String s) =>
      {
        'WAITING': '等待拼单',
        'SUCCESS': '拼单成功',
        'FORCE_SUCCESS': '后台强制成功',
        'TIMEOUT': '超时失败',
        'FORCE_FAIL': '后台强制失败',
        'SUSPEND': '已暂停',
        'CANCEL': '已关闭',
      }[s] ??
      s;
  Future<void> join() async {
    setState(() => joining = true);
    try {
      data = await GroupbuyService(
        context.read<AppSession>().api,
      ).join(data['activityId'].toString());
      if (mounted) setState(() {});
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => joining = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = data['activityStatus']?.toString() ?? '';
    final current = (data['currentPeople'] as num?)?.toInt() ?? 0,
        target = (data['targetPeople'] as num?)?.toInt() ?? 0;
    return Scaffold(
      appBar: AppBar(title: const Text('拼单详情')),
      body: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          Container(
            height: 180,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF287EE0), Color(0xFF22A88A)],
              ),
              borderRadius: BorderRadius.circular(26),
            ),
            child: const Icon(
              LucideIcons.usersRound,
              size: 70,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 20),
          Text(
            data['couponName']?.toString() ?? '',
            style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w800),
          ),
          Text(
            '${data['merchantName'] ?? ''} · ${data['storeName'] ?? ''}',
            style: const TextStyle(color: AppColors.muted),
          ),
          const SizedBox(height: 18),
          TlxCard(
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      label(status),
                      style: const TextStyle(
                        fontWeight: FontWeight.w800,
                        color: AppColors.primary,
                      ),
                    ),
                    Text('$current / $target 人'),
                  ],
                ),
                const SizedBox(height: 12),
                LinearProgressIndicator(
                  value: target == 0 ? 0 : current / target,
                ),
                const SizedBox(height: 12),
                Text('截止时间：${data['expireAt'] ?? ''}'),
                Text('适用地址：${data['storeAddress'] ?? ''}'),
              ],
            ),
          ),
          const SizedBox(height: 18),
          FilledButton(
            onPressed: status == 'WAITING' && !joining ? join : null,
            child: Text(
              joining
                  ? '加入中…'
                  : status == 'WAITING'
                  ? '加入拼单'
                  : '当前不可加入',
            ),
          ),
        ],
      ),
    );
  }
}

// ignore: unused_element
class _ProductCard extends StatelessWidget {
  const _ProductCard({required this.product});
  final _Product product;

  @override
  Widget build(BuildContext context) => InkWell(
    borderRadius: BorderRadius.circular(20),
    onTap: () => Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => _ProductDetailPage(product: product)),
    ),
    child: TlxCard(
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Container(
              width: double.infinity,
              decoration: const BoxDecoration(
                color: AppColors.primarySoft,
                borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
              ),
              alignment: Alignment.center,
              child: Text(product.emoji, style: const TextStyle(fontSize: 48)),
            ),
          ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  product.name,
                  maxLines: 2,
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 5),
                Text(
                  product.shop,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: AppColors.muted, fontSize: 11),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Text(
                      '¥${product.price}',
                      style: const TextStyle(
                        color: AppColors.danger,
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const Spacer(),
                    Text(
                      product.sales,
                      style: const TextStyle(
                        color: AppColors.muted,
                        fontSize: 10,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _ProductDetailPage extends StatelessWidget {
  const _ProductDetailPage({required this.product});
  final _Product product;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('商品详情'),
      actions: [
        IconButton(onPressed: () {}, icon: const Icon(LucideIcons.heart)),
      ],
    ),
    body: ListView(
      padding: const EdgeInsets.all(22),
      children: [
        Container(
          height: 240,
          decoration: BoxDecoration(
            color: AppColors.primarySoft,
            borderRadius: BorderRadius.circular(28),
          ),
          alignment: Alignment.center,
          child: Text(product.emoji, style: const TextStyle(fontSize: 90)),
        ),
        const SizedBox(height: 20),
        Text(
          product.name,
          style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 8),
        Text(
          product.shop,
          style: const TextStyle(color: AppColors.secondaryText),
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Text(
              '¥${product.price}',
              style: const TextStyle(
                color: AppColors.danger,
                fontSize: 30,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(width: 10),
            Text(
              '¥${product.original}',
              style: const TextStyle(
                decoration: TextDecoration.lineThrough,
                color: AppColors.muted,
              ),
            ),
            const Spacer(),
            Text(product.sales),
          ],
        ),
        const SizedBox(height: 20),
        const TlxCard(
          color: Color(0xFFF8FAFD),
          child: Text(
            '随时退 · 过期自动退 · 到店免预约\n演示阶段不发起真实支付或核销。',
            style: TextStyle(height: 1.8),
          ),
        ),
        const SizedBox(height: 22),
        FilledButton(
          onPressed: () => ScaffoldMessenger.of(
            context,
          ).showSnackBar(const SnackBar(content: Text('已创建演示拼单'))),
          child: const Text('立即拼单'),
        ),
      ],
    ),
  );
}

class _ShopListPage extends StatelessWidget {
  const _ShopListPage({required this.title, required this.rows});
  final String title;
  final List<_SimpleRow> rows;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(title)),
    body: ListView.separated(
      padding: const EdgeInsets.all(22),
      itemCount: rows.length,
      separatorBuilder: (_, _) => const SizedBox(height: 12),
      itemBuilder: (_, i) => TlxCard(
        color: const Color(0xFFF8FAFD),
        child: Row(
          children: [
            CircleAvatar(
              backgroundColor: AppColors.primarySoft,
              child: Icon(rows[i].icon, color: AppColors.primary),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    rows[i].title,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    rows[i].subtitle,
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
    ),
  );
}

class _HeaderButton extends StatelessWidget {
  const _HeaderButton({required this.icon, required this.onTap});
  final IconData icon;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => IconButton.filledTonal(
    onPressed: onTap,
    icon: Icon(icon),
    style: IconButton.styleFrom(
      backgroundColor: Colors.white24,
      foregroundColor: Colors.white,
    ),
  );
}

class _Quick extends StatelessWidget {
  const _Quick({required this.icon, required this.label, required this.onTap});
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: Column(
      children: [
        CircleAvatar(
          radius: 24,
          backgroundColor: AppColors.primarySoft,
          child: Icon(icon, color: AppColors.primary),
        ),
        const SizedBox(height: 7),
        Text(label, style: const TextStyle(fontSize: 12)),
      ],
    ),
  );
}

class _Product {
  const _Product(
    this.name,
    this.shop,
    this.emoji,
    this.price,
    this.original,
    this.sales,
  );
  final String name, shop, emoji, sales;
  final num price, original;
}

class _SimpleRow {
  const _SimpleRow(this.icon, this.title, this.subtitle);
  final IconData icon;
  final String title, subtitle;
}
