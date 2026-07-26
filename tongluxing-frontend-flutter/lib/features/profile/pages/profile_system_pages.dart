import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/follow_service.dart';
import '../../chat/pages/chat_session_page.dart';
import '../../chat/private_chat_flow.dart';
import '../../trip/pages/trip_discovery_detail_page.dart';
import '../widgets/user_avatar.dart';

class PublicProfilePage extends StatefulWidget {
  const PublicProfilePage({required this.userId, super.key});
  final String userId;

  @override
  State<PublicProfilePage> createState() => _PublicProfilePageState();
}

class _PublicProfilePageState extends State<PublicProfilePage> {
  Map<String, dynamic>? data;
  List<TripDiscoverModel> publicTrips = const [];
  String? error;
  String avatarUrl = '';
  bool followBusy = false;
  bool publicTripsLoading = true;
  int section = 0;

  bool get isOwner =>
      widget.userId == context.read<AppSession>().userId?.toString();

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final api = context.read<AppSession>().api;
    if (mounted) {
      setState(() {
        error = null;
        data = null;
        publicTrips = const [];
        publicTripsLoading = true;
        avatarUrl = '';
      });
    }

    try {
      final profileService = UserProfileService(api);
      final homepage = await profileService.homepage(widget.userId);
      final profile = <String, dynamic>{
        ...Map<String, dynamic>.from(homepage['profile'] as Map? ?? const {}),
        'levelCode': (homepage['growth'] as Map?)?['levelCode'],
        'mainVehicle': homepage['mainVehicle'],
      };

      Map<String, dynamic> follow = <String, dynamic>{
        'userId': widget.userId,
        'following': false,
        'followedByTarget': false,
        'mutual': false,
        'followerCount': 0,
        'followingCount': 0,
      };
      try {
        follow = await FollowService(api).status(widget.userId);
      } catch (_) {
        // 关注状态属于附加信息，读取失败时仍然展示公开资料卡片。
      }

      if (!mounted) return;
      setState(() {
        data = <String, dynamic>{'profile': profile, 'follow': follow};
        error = null;
      });

      final avatarKey = profile['avatarImageKey']?.toString() ?? '';
      if (avatarKey.isNotEmpty) {
        try {
          final value = await StorageUploadService(
            api,
          ).downloadUrlByObjectKey(avatarKey);
          if (mounted) setState(() => avatarUrl = value);
        } catch (_) {
          // 头像签名失败时使用昵称占位头像，不阻断主页主体。
        }
      }

      try {
        final tripsData = await TripDiscoveryService(
          api,
        ).publicTripsByUser(widget.userId, size: 20);
        final values = (tripsData['records'] as List? ?? const [])
            .map(
              (e) => TripDiscoverModel.fromJson(
                Map<String, dynamic>.from(e as Map),
              ),
            )
            .toList();
        if (mounted) {
          setState(() {
            publicTrips = values;
            publicTripsLoading = false;
          });
        }
      } catch (_) {
        // 公开行程属于附加区域；接口异常时保留资料主体并允许下拉刷新。
        if (mounted) setState(() => publicTripsLoading = false);
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        data = null;
        error = e.toString();
      });
    }
  }

  Future<void> _toggleFollow() async {
    if (isOwner || followBusy) return;
    final service = FollowService(context.read<AppSession>().api);
    final follow = Map<String, dynamic>.from(
      data?['follow'] as Map? ?? const {},
    );
    final currentlyFollowing = follow['following'] == true;
    if (currentlyFollowing) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: const Text('取消关注？'),
          content: const Text('取消后，尚未解锁的单向私聊仍会保留记录，但不能继续新增破冰消息。'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('保留关注'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('取消关注'),
            ),
          ],
        ),
      );
      if (confirmed != true) return;
    }
    setState(() => followBusy = true);
    try {
      final value = currentlyFollowing
          ? await service.unfollow(widget.userId)
          : await service.follow(widget.userId);
      if (!mounted) return;
      data = {...?data, 'follow': value};
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(value['following'] == true ? '已关注' : '已取消关注')),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => followBusy = false);
    }
  }

  Future<void> _startChat() async {
    final conversation = await startPrivateChatFlow(context, widget.userId);
    if (conversation == null || !mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ChatSessionPage(conversation: conversation),
      ),
    );
    if (mounted) load();
  }

  Future<bool> _editProfile() async {
    if (!isOwner) return false;
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const EditProfilePage()),
    );
    if (changed == true) await load();
    return changed == true;
  }

  Future<void> _previewAvatar(Map<String, dynamic> profile) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => AvatarPreviewPage(
          nickname: profile['nickname']?.toString() ?? '同路行用户',
          avatarImageKey: profile['avatarImageKey']?.toString() ?? '',
          avatarUrl: avatarUrl,
          canEdit: isOwner,
          onEdit: _editProfile,
        ),
      ),
    );
    if (changed == true) await load();
  }

  Future<void> _copyTongluxingId(String value) async {
    if (value.isEmpty) return;
    await Clipboard.setData(ClipboardData(text: value));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('同路行号已复制')));
  }

  String _followText(Map<String, dynamic> follow) {
    if (follow['mutual'] == true) return '互相关注';
    if (follow['following'] == true) return '已关注';
    if (follow['followedByTarget'] == true) return '回关';
    return '+ 关注';
  }

  @override
  Widget build(BuildContext context) {
    final p = Map<String, dynamic>.from(data?['profile'] as Map? ?? const {});
    final follow = Map<String, dynamic>.from(
      data?['follow'] as Map? ?? const {},
    );
    final nickname = p['nickname']?.toString().trim().isNotEmpty == true
        ? p['nickname'].toString()
        : '同路行用户';
    final tongluxingId = p['tongluxingId']?.toString().trim() ?? '';
    final cityName = p['cityName']?.toString().trim() ?? '';
    final bio = p['bio']?.toString().trim() ?? '';
    final certified = p['drivingLicenseCertificationStatus'] == 'APPROVED';
    final levelCode = p['levelCode']?.toString() ?? '';
    final mainVehicle = Map<String, dynamic>.from(
      p['mainVehicle'] as Map? ?? const {},
    );
    final vehicleText = [
      mainVehicle['brand']?.toString() ?? '',
      mainVehicle['model']?.toString() ?? '',
      mainVehicle['vehicleType']?.toString() ?? '',
    ].where((value) => value.trim().isNotEmpty).join(' · ');
    final totalDistanceMeters =
        (p['totalDistanceMeters'] as num?)?.toDouble() ?? 0;

    return Scaffold(
      backgroundColor: const Color(0xFFF3F5F8),
      body: data == null
          ? Center(
              child: error == null
                  ? const CircularProgressIndicator()
                  : Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 24),
                          child: Text(error!, textAlign: TextAlign.center),
                        ),
                        const SizedBox(height: 10),
                        FilledButton(
                          onPressed: load,
                          child: const Text('重新加载'),
                        ),
                      ],
                    ),
            )
          : RefreshIndicator(
              onRefresh: load,
              child: CustomScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                slivers: [
                  SliverToBoxAdapter(
                    child: Container(
                      decoration: const BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [
                            Color(0xFF075DBB),
                            Color(0xFF0877E5),
                            Color(0xFF2E9AEF),
                          ],
                        ),
                      ),
                      child: SafeArea(
                        bottom: false,
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(12, 4, 12, 13),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              SizedBox(
                                height: 32,
                                child: Row(
                                  children: [
                                    SizedBox(
                                      width: 30,
                                      height: 30,
                                      child: IconButton(
                                        tooltip: '返回',
                                        constraints:
                                            const BoxConstraints.tightFor(
                                              width: 30,
                                              height: 30,
                                            ),
                                        padding: EdgeInsets.zero,
                                        onPressed: () => Navigator.pop(context),
                                        icon: const Icon(
                                          LucideIcons.arrowLeft,
                                          size: 20,
                                          color: Colors.white,
                                        ),
                                      ),
                                    ),
                                    const Spacer(),
                                    if (isOwner)
                                      SizedBox(
                                        width: 30,
                                        height: 30,
                                        child: IconButton(
                                          tooltip: '编辑个人资料',
                                          constraints:
                                              const BoxConstraints.tightFor(
                                                width: 30,
                                                height: 30,
                                              ),
                                          padding: EdgeInsets.zero,
                                          onPressed: _editProfile,
                                          icon: const Icon(
                                            LucideIcons.pencil,
                                            size: 16,
                                            color: Colors.white,
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 6),
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: [
                                  UserAvatar(
                                    nickname: nickname,
                                    avatarImageKey:
                                        p['avatarImageKey']?.toString() ?? '',
                                    avatarUrl: avatarUrl,
                                    radius: 30,
                                    onTap: () => _previewAvatar(p),
                                    heroTag: 'profile-avatar-${widget.userId}',
                                  ),
                                  const SizedBox(width: 11),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Row(
                                          children: [
                                            Flexible(
                                              child: Text(
                                                nickname,
                                                maxLines: 1,
                                                overflow: TextOverflow.ellipsis,
                                                style: const TextStyle(
                                                  color: Colors.white,
                                                  fontSize: 17,
                                                  height: 1.15,
                                                  fontWeight: FontWeight.w900,
                                                ),
                                              ),
                                            ),
                                            if (certified) ...[
                                              const SizedBox(width: 5),
                                              Container(
                                                padding:
                                                    const EdgeInsets.symmetric(
                                                      horizontal: 5,
                                                      vertical: 2,
                                                    ),
                                                decoration: BoxDecoration(
                                                  color: Colors.white
                                                      .withValues(alpha: 0.16),
                                                  borderRadius:
                                                      BorderRadius.circular(5),
                                                  border: Border.all(
                                                    color: Colors.white24,
                                                  ),
                                                ),
                                                child: const Row(
                                                  mainAxisSize:
                                                      MainAxisSize.min,
                                                  children: [
                                                    Icon(
                                                      LucideIcons.badgeCheck,
                                                      size: 11,
                                                      color: Color(0xFF8CF5D1),
                                                    ),
                                                    SizedBox(width: 3),
                                                    Text(
                                                      '已认证',
                                                      style: TextStyle(
                                                        color: Colors.white,
                                                        fontSize: 9.5,
                                                        fontWeight:
                                                            FontWeight.w700,
                                                      ),
                                                    ),
                                                  ],
                                                ),
                                              ),
                                            ],
                                          ],
                                        ),
                                        if (tongluxingId.isNotEmpty) ...[
                                          const SizedBox(height: 5),
                                          InkWell(
                                            onTap: () =>
                                                _copyTongluxingId(tongluxingId),
                                            borderRadius: BorderRadius.circular(
                                              5,
                                            ),
                                            child: Padding(
                                              padding:
                                                  const EdgeInsets.symmetric(
                                                    vertical: 1,
                                                  ),
                                              child: Row(
                                                mainAxisSize: MainAxisSize.min,
                                                children: [
                                                  Flexible(
                                                    child: Text(
                                                      '同路行号：$tongluxingId',
                                                      maxLines: 1,
                                                      overflow:
                                                          TextOverflow.ellipsis,
                                                      style: const TextStyle(
                                                        color: Colors.white70,
                                                        fontSize: 11,
                                                        fontWeight:
                                                            FontWeight.w700,
                                                      ),
                                                    ),
                                                  ),
                                                  const SizedBox(width: 4),
                                                  const Icon(
                                                    LucideIcons.copy,
                                                    size: 11,
                                                    color: Colors.white60,
                                                  ),
                                                ],
                                              ),
                                            ),
                                          ),
                                        ],
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 9),
                              _PublicProfileLine(
                                icon: certified
                                    ? LucideIcons.shieldCheck
                                    : LucideIcons.shield,
                                text: certified ? '驾驶证已认证' : '暂未完成驾驶认证',
                              ),
                              const SizedBox(height: 5),
                              _PublicProfileLine(
                                icon: LucideIcons.mapPin,
                                text: cityName.isNotEmpty
                                    ? '$cityName · 同路行车友'
                                    : '常驻地暂未填写',
                              ),
                              if (levelCode.isNotEmpty) ...[
                                const SizedBox(height: 5),
                                _PublicProfileLine(
                                  icon: LucideIcons.sparkles,
                                  text: '用户等级：$levelCode',
                                ),
                              ],
                              if (vehicleText.isNotEmpty) ...[
                                const SizedBox(height: 5),
                                _PublicProfileLine(
                                  icon: LucideIcons.carFront,
                                  text: '主要车型：$vehicleText',
                                ),
                              ],
                              const SizedBox(height: 5),
                              _PublicProfileLine(
                                icon: LucideIcons.squarePen,
                                text: bio.isNotEmpty ? bio : '愿每一次出发都有同路人',
                                maxLines: 2,
                              ),
                              const SizedBox(height: 11),
                              if (isOwner)
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.start,
                                  children: [
                                    _ProfileMetric(
                                      value: '${follow['followingCount'] ?? 0}',
                                      label: '关注',
                                    ),
                                    _ProfileMetric(
                                      value: '${follow['followerCount'] ?? 0}',
                                      label: '粉丝',
                                    ),
                                    _ProfileMetric(
                                      value: '${p['totalTripCount'] ?? 0}',
                                      label: '行程',
                                    ),
                                    _ProfileMetric(
                                      value:
                                          '${(totalDistanceMeters / 1000).round()}',
                                      label: '公里',
                                    ),
                                  ],
                                )
                              else
                                Row(
                                  children: [
                                    Expanded(
                                      child: FittedBox(
                                        fit: BoxFit.scaleDown,
                                        alignment: Alignment.centerLeft,
                                        child: Row(
                                          mainAxisSize: MainAxisSize.min,
                                          children: [
                                            _ProfileMetric(
                                              value:
                                                  '${follow['followingCount'] ?? 0}',
                                              label: '关注',
                                            ),
                                            _ProfileMetric(
                                              value:
                                                  '${follow['followerCount'] ?? 0}',
                                              label: '粉丝',
                                            ),
                                            _ProfileMetric(
                                              value:
                                                  '${p['totalTripCount'] ?? 0}',
                                              label: '行程',
                                            ),
                                            _ProfileMetric(
                                              value:
                                                  '${(totalDistanceMeters / 1000).round()}',
                                              label: '公里',
                                            ),
                                          ],
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 6),
                                    SizedBox(
                                      width: 80,
                                      height: 30,
                                      child: follow['following'] == true
                                          ? OutlinedButton(
                                              style: OutlinedButton.styleFrom(
                                                minimumSize: Size.zero,
                                                foregroundColor: Colors.white,
                                                padding:
                                                    const EdgeInsets.symmetric(
                                                      horizontal: 8,
                                                    ),
                                                side: const BorderSide(
                                                  color: Colors.white70,
                                                ),
                                                shape: const StadiumBorder(),
                                                textStyle: const TextStyle(
                                                  fontSize: 11,
                                                  fontWeight: FontWeight.w700,
                                                ),
                                              ),
                                              onPressed: followBusy
                                                  ? null
                                                  : _toggleFollow,
                                              child: Text(_followText(follow)),
                                            )
                                          : FilledButton(
                                              style: FilledButton.styleFrom(
                                                minimumSize: Size.zero,
                                                backgroundColor: const Color(
                                                  0xFF22D3AE,
                                                ),
                                                foregroundColor: Colors.white,
                                                padding:
                                                    const EdgeInsets.symmetric(
                                                      horizontal: 8,
                                                    ),
                                                shape: const StadiumBorder(),
                                                textStyle: const TextStyle(
                                                  fontSize: 11,
                                                  fontWeight: FontWeight.w800,
                                                ),
                                              ),
                                              onPressed: followBusy
                                                  ? null
                                                  : _toggleFollow,
                                              child: Text(_followText(follow)),
                                            ),
                                    ),
                                    const SizedBox(width: 6),
                                    SizedBox(
                                      width: 30,
                                      height: 30,
                                      child: OutlinedButton(
                                        style: OutlinedButton.styleFrom(
                                          minimumSize: Size.zero,
                                          padding: EdgeInsets.zero,
                                          foregroundColor: Colors.white,
                                          side: const BorderSide(
                                            color: Colors.white70,
                                          ),
                                          shape: const CircleBorder(),
                                        ),
                                        onPressed: _startChat,
                                        child: const Icon(
                                          LucideIcons.messageCircle,
                                          size: 15,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                  SliverPersistentHeader(
                    pinned: true,
                    delegate: _ProfileTabHeader(
                      section: section,
                      tripCount: publicTrips.length,
                      onChanged: (value) => setState(() => section = value),
                    ),
                  ),
                  if (section == 0)
                    publicTripsLoading
                        ? const SliverFillRemaining(
                            hasScrollBody: false,
                            child: Center(child: CircularProgressIndicator()),
                          )
                        : publicTrips.isEmpty
                        ? const SliverFillRemaining(
                            hasScrollBody: false,
                            child: Center(
                              child: Text(
                                '暂时没有公开招募中的行程',
                                style: TextStyle(color: AppColors.muted),
                              ),
                            ),
                          )
                        : SliverPadding(
                            padding: const EdgeInsets.fromLTRB(10, 8, 10, 24),
                            sliver: SliverList(
                              delegate: SliverChildBuilderDelegate((_, index) {
                                if (index.isOdd) {
                                  return const SizedBox(height: 7);
                                }
                                final trip = publicTrips[index ~/ 2];
                                return _PublicTripTile(
                                  trip: trip,
                                  onTap: () => Navigator.push(
                                    context,
                                    MaterialPageRoute(
                                      builder: (_) => TripDiscoveryDetailPage(
                                        tripId: trip.tripId,
                                        initial: trip,
                                      ),
                                    ),
                                  ),
                                );
                              }, childCount: publicTrips.length * 2 - 1),
                            ),
                          )
                  else
                    SliverPadding(
                      padding: const EdgeInsets.fromLTRB(10, 8, 10, 24),
                      sliver: SliverList(
                        delegate: SliverChildListDelegate([
                          _ProfileInfoCard(
                            icon: LucideIcons.userRound,
                            title: '个人简介',
                            value: bio.isNotEmpty ? bio : '暂未填写',
                          ),
                          const SizedBox(height: 7),
                          _ProfileInfoCard(
                            icon: LucideIcons.mapPin,
                            title: '常驻地',
                            value: cityName.isNotEmpty ? cityName : '暂未填写',
                          ),
                          const SizedBox(height: 7),
                          _ProfileInfoCard(
                            icon: LucideIcons.shieldCheck,
                            title: '驾驶认证',
                            value: certified ? '已通过驾驶证认证' : '暂未认证',
                          ),
                        ]),
                      ),
                    ),
                ],
              ),
            ),
    );
  }
}

class _PublicProfileLine extends StatelessWidget {
  const _PublicProfileLine({
    required this.icon,
    required this.text,
    this.maxLines = 1,
  });

  final IconData icon;
  final String text;
  final int maxLines;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Padding(
        padding: const EdgeInsets.only(top: 1),
        child: Icon(icon, size: 13, color: Colors.white70),
      ),
      const SizedBox(width: 6),
      Expanded(
        child: Text(
          text,
          maxLines: maxLines,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 11.5,
            height: 1.28,
          ),
        ),
      ),
    ],
  );
}

class _ProfileMetric extends StatelessWidget {
  const _ProfileMetric({required this.value, required this.label});

  final String value;
  final String label;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 50,
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          value,
          maxLines: 1,
          overflow: TextOverflow.fade,
          softWrap: false,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 13,
            height: 1.05,
            fontWeight: FontWeight.w500,
          ),
        ),
        const SizedBox(height: 1),
        Text(
          label,
          style: const TextStyle(
            color: Colors.white70,
            fontSize: 9,
            height: 1.1,
            fontWeight: FontWeight.w400,
          ),
        ),
      ],
    ),
  );
}

class _ProfileTabHeader extends SliverPersistentHeaderDelegate {
  const _ProfileTabHeader({
    required this.section,
    required this.tripCount,
    required this.onChanged,
  });

  final int section;
  final int tripCount;
  final ValueChanged<int> onChanged;

  @override
  double get minExtent => 44;

  @override
  double get maxExtent => 44;

  @override
  Widget build(
    BuildContext context,
    double shrinkOffset,
    bool overlapsContent,
  ) => Material(
    color: Colors.white,
    elevation: overlapsContent ? 1 : 0,
    borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
    clipBehavior: Clip.antiAlias,
    child: Row(
      children: [
        _ProfileTab(
          label: '发布 ($tripCount)',
          active: section == 0,
          onTap: () => onChanged(0),
        ),
        _ProfileTab(
          label: '资料',
          active: section == 1,
          onTap: () => onChanged(1),
        ),
      ],
    ),
  );

  @override
  bool shouldRebuild(covariant _ProfileTabHeader oldDelegate) =>
      oldDelegate.section != section || oldDelegate.tripCount != tripCount;
}

class _ProfileTab extends StatelessWidget {
  const _ProfileTab({
    required this.label,
    required this.active,
    required this.onTap,
  });

  final String label;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Expanded(
    child: InkWell(
      onTap: onTap,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 13,
              color: active ? AppColors.text : AppColors.muted,
              fontWeight: active ? FontWeight.w900 : FontWeight.w600,
            ),
          ),
          const SizedBox(height: 7),
          Container(
            width: 26,
            height: 2.5,
            decoration: BoxDecoration(
              color: active ? const Color(0xFF22D3AE) : Colors.transparent,
              borderRadius: BorderRadius.circular(99),
            ),
          ),
        ],
      ),
    ),
  );
}

class _PublicTripTile extends StatelessWidget {
  const _PublicTripTile({required this.trip, required this.onTap});

  final TripDiscoverModel trip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(10),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(11, 10, 10, 9),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    trip.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                const Text(
                  '招募中',
                  style: TextStyle(
                    color: AppColors.primary,
                    fontSize: 10.5,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 5),
            Text(
              '${trip.startName} → ${trip.endName}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 11.5,
                color: AppColors.secondaryText,
              ),
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                const Icon(
                  LucideIcons.calendar,
                  size: 12.5,
                  color: AppColors.muted,
                ),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    trip.departureTime,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 10.5,
                      color: AppColors.muted,
                    ),
                  ),
                ),
                Text(
                  '${trip.memberCount}/${trip.maxMemberCount} 人',
                  style: const TextStyle(
                    fontSize: 10.5,
                    color: AppColors.muted,
                  ),
                ),
                const SizedBox(width: 3),
                const Icon(
                  LucideIcons.chevronRight,
                  size: 14,
                  color: AppColors.muted,
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

class _ProfileInfoCard extends StatelessWidget {
  const _ProfileInfoCard({
    required this.icon,
    required this.title,
    required this.value,
  });

  final IconData icon;
  final String title;
  final String value;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.fromLTRB(11, 10, 11, 10),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(10),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 16, color: AppColors.primary),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(fontSize: 10.5, color: AppColors.muted),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: const TextStyle(
                  fontSize: 12.5,
                  fontWeight: FontWeight.w700,
                  height: 1.35,
                ),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class EditProfilePage extends StatefulWidget {
  const EditProfilePage({super.key});

  @override
  State<EditProfilePage> createState() => _EditProfilePageState();
}

class _EditProfilePageState extends State<EditProfilePage> {
  final nickname = TextEditingController();
  final city = TextEditingController();
  final bio = TextEditingController();
  final ImagePicker picker = ImagePicker();
  String cityCode = '';

  int gender = 0;
  bool loading = true;
  bool saving = false;
  Uint8List? avatarBytes;
  String avatarFileName = '';
  String avatarImageKey = '';
  String avatarUrl = '';

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    nickname.dispose();
    city.dispose();
    bio.dispose();
    super.dispose();
  }

  Future<void> load() async {
    try {
      final api = context.read<AppSession>().api;
      final p = await UserProfileService(api).me();
      nickname.text = p['nickname']?.toString() ?? '';
      city.text = p['cityName']?.toString() ?? '';
      cityCode = p['cityCode']?.toString() ?? '';
      bio.text = p['bio']?.toString() ?? '';
      gender = (p['gender'] as num?)?.toInt() ?? 0;
      avatarImageKey = p['avatarImageKey']?.toString() ?? '';
      if (avatarImageKey.isNotEmpty) {
        avatarUrl = await StorageUploadService(
          api,
        ).downloadUrlByObjectKey(avatarImageKey);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> pickAvatar() async {
    try {
      final file = await picker.pickImage(
        source: ImageSource.gallery,
        maxWidth: 1600,
        imageQuality: 90,
      );
      if (file == null) return;
      final bytes = await file.readAsBytes();
      if (bytes.length > 10 * 1024 * 1024) {
        throw const ApiException('头像图片不能超过 10MB');
      }
      if (mounted) {
        setState(() {
          avatarBytes = bytes;
          avatarFileName = file.name;
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
  }

  Future<void> save() async {
    if (nickname.text.trim().isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('昵称不能为空')));
      return;
    }
    if (cityCode.isEmpty || city.text.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请选择常住城市')));
      return;
    }
    setState(() => saving = true);
    try {
      final api = context.read<AppSession>().api;
      var nextAvatarKey = avatarImageKey;
      if (avatarBytes != null) {
        final session = context.read<AppSession>();
        final uploaded = await StorageUploadService(api).upload(
          bizType: 'USER_AVATAR',
          bizId: session.userId,
          fileName: avatarFileName.isEmpty ? 'avatar.jpg' : avatarFileName,
          bytes: avatarBytes!,
        );
        nextAvatarKey = uploaded['objectKey']?.toString() ?? '';
        if (nextAvatarKey.isEmpty) {
          throw const ApiException('头像上传结果缺少 objectKey');
        }
      }
      await UserProfileService(api).update({
        'nickname': nickname.text.trim(),
        'avatarImageKey': nextAvatarKey,
        'cityCode': cityCode,
        'cityName': city.text.trim(),
        'bio': bio.text.trim(),
        'gender': gender,
      });
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('个人资料已保存')));
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  ImageProvider<Object>? get avatarProvider {
    if (avatarBytes != null) return MemoryImage(avatarBytes!);
    if (avatarUrl.isNotEmpty) return NetworkImage(avatarUrl);
    return null;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('编辑个人资料')),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : ListView(
            padding: const EdgeInsets.all(22),
            children: [
              Center(
                child: InkWell(
                  onTap: saving ? null : pickAvatar,
                  borderRadius: BorderRadius.circular(52),
                  child: Stack(
                    children: [
                      CircleAvatar(
                        radius: 46,
                        backgroundColor: AppColors.primarySoft,
                        backgroundImage: avatarProvider,
                        child: avatarProvider == null
                            ? const Icon(
                                LucideIcons.userRound,
                                size: 40,
                                color: AppColors.primary,
                              )
                            : null,
                      ),
                      Positioned(
                        right: 0,
                        bottom: 0,
                        child: CircleAvatar(
                          radius: 16,
                          backgroundColor: AppColors.primary,
                          child: const Icon(
                            LucideIcons.camera,
                            size: 15,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 9),
              const Center(
                child: Text(
                  '点击头像从手机相册选择图片',
                  style: TextStyle(color: AppColors.muted, fontSize: 12),
                ),
              ),
              const SizedBox(height: 24),
              TextField(
                controller: nickname,
                maxLength: 16,
                decoration: const InputDecoration(
                  labelText: '昵称',
                  counterText: '最多 16 个字',
                ),
              ),
              const SizedBox(height: 14),
              DropdownButtonFormField<int>(
                initialValue: gender,
                decoration: const InputDecoration(labelText: '性别'),
                items: const [
                  DropdownMenuItem(value: 0, child: Text('保密')),
                  DropdownMenuItem(value: 1, child: Text('男')),
                  DropdownMenuItem(value: 2, child: Text('女')),
                ],
                onChanged: (v) => gender = v ?? 0,
              ),
              const SizedBox(height: 14),
              TextField(
                controller: city,
                readOnly: true,
                onTap: saving ? null : _chooseCity,
                decoration: const InputDecoration(
                  labelText: '常住城市',
                  hintText: '请选择城市',
                  suffixIcon: Icon(LucideIcons.chevronDown),
                ),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: bio,
                maxLength: 100,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: '个人简介',
                  counterText: '最多 100 个字',
                  alignLabelWithHint: true,
                  contentPadding: EdgeInsets.fromLTRB(16, 18, 16, 16),
                ),
              ),
              const SizedBox(height: 24),
              FilledButton(
                onPressed: saving ? null : save,
                child: Text(saving ? '图片上传并保存中...' : '保存资料'),
              ),
            ],
          ),
  );

  Future<void> _chooseCity() async {
    final selected = await showModalBottomSheet<_CityOption>(
      context: context,
      isScrollControlled: true,
      builder: (_) => const _CityPickerSheet(),
    );
    if (selected == null || !mounted) return;
    setState(() {
      cityCode = selected.code;
      city.text = selected.name;
    });
  }
}

class _CityOption {
  const _CityOption(this.code, this.province, this.name);
  final String code;
  final String province;
  final String name;
}

const _cityOptions = <_CityOption>[
  _CityOption('110100', '北京', '北京市'),
  _CityOption('120100', '天津', '天津市'),
  _CityOption('130100', '河北', '石家庄市'),
  _CityOption('140100', '山西', '太原市'),
  _CityOption('150100', '内蒙古', '呼和浩特市'),
  _CityOption('210100', '辽宁', '沈阳市'),
  _CityOption('210200', '辽宁', '大连市'),
  _CityOption('220100', '吉林', '长春市'),
  _CityOption('230100', '黑龙江', '哈尔滨市'),
  _CityOption('310100', '上海', '上海市'),
  _CityOption('320100', '江苏', '南京市'),
  _CityOption('320200', '江苏', '无锡市'),
  _CityOption('320500', '江苏', '苏州市'),
  _CityOption('330100', '浙江', '杭州市'),
  _CityOption('330200', '浙江', '宁波市'),
  _CityOption('330300', '浙江', '温州市'),
  _CityOption('340100', '安徽', '合肥市'),
  _CityOption('350100', '福建', '福州市'),
  _CityOption('350200', '福建', '厦门市'),
  _CityOption('360100', '江西', '南昌市'),
  _CityOption('370100', '山东', '济南市'),
  _CityOption('370200', '山东', '青岛市'),
  _CityOption('410100', '河南', '郑州市'),
  _CityOption('420100', '湖北', '武汉市'),
  _CityOption('430100', '湖南', '长沙市'),
  _CityOption('440100', '广东', '广州市'),
  _CityOption('440300', '广东', '深圳市'),
  _CityOption('440400', '广东', '珠海市'),
  _CityOption('440500', '广东', '汕头市'),
  _CityOption('440600', '广东', '佛山市'),
  _CityOption('441300', '广东', '惠州市'),
  _CityOption('441900', '广东', '东莞市'),
  _CityOption('445100', '广东', '潮州市'),
  _CityOption('445200', '广东', '揭阳市'),
  _CityOption('450100', '广西', '南宁市'),
  _CityOption('460100', '海南', '海口市'),
  _CityOption('460200', '海南', '三亚市'),
  _CityOption('500100', '重庆', '重庆市'),
  _CityOption('510100', '四川', '成都市'),
  _CityOption('520100', '贵州', '贵阳市'),
  _CityOption('530100', '云南', '昆明市'),
  _CityOption('540100', '西藏', '拉萨市'),
  _CityOption('610100', '陕西', '西安市'),
  _CityOption('620100', '甘肃', '兰州市'),
  _CityOption('630100', '青海', '西宁市'),
  _CityOption('640100', '宁夏', '银川市'),
  _CityOption('650100', '新疆', '乌鲁木齐市'),
  _CityOption('810000', '香港', '香港特别行政区'),
  _CityOption('820000', '澳门', '澳门特别行政区'),
  _CityOption('710000', '台湾', '台北市'),
];

class _CityPickerSheet extends StatefulWidget {
  const _CityPickerSheet();

  @override
  State<_CityPickerSheet> createState() => _CityPickerSheetState();
}

class _CityPickerSheetState extends State<_CityPickerSheet> {
  String keyword = '';

  @override
  Widget build(BuildContext context) {
    final filtered = _cityOptions
        .where(
          (item) =>
              keyword.isEmpty ||
              item.name.contains(keyword) ||
              item.province.contains(keyword),
        )
        .toList();
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * .72,
        child: Column(
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 18, 20, 10),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      '选择常住城市',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  Text('仅可从列表选择', style: TextStyle(color: AppColors.muted)),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: TextField(
                onChanged: (value) => setState(() => keyword = value.trim()),
                decoration: const InputDecoration(
                  hintText: '搜索省份或城市',
                  prefixIcon: Icon(LucideIcons.search),
                ),
              ),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: ListView.separated(
                itemCount: filtered.length,
                separatorBuilder: (_, _) => const Divider(height: 1),
                itemBuilder: (_, index) {
                  final item = filtered[index];
                  return ListTile(
                    title: Text(item.name),
                    subtitle: Text(item.province),
                    trailing: const Icon(LucideIcons.chevronRight, size: 18),
                    onTap: () => Navigator.pop(context, item),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
