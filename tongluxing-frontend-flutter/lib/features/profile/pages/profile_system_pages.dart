import 'dart:typed_data';

import 'package:flutter/material.dart';
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
  int section = 0;

  bool get isOwner =>
      widget.userId == context.read<AppSession>().userId?.toString();

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final api = context.read<AppSession>().api;
      final values = await Future.wait<dynamic>([
        UserProfileService(api).homepage(widget.userId),
        TripDiscoveryService(api).publicTripsByUser(widget.userId, size: 20),
      ]);
      data = Map<String, dynamic>.from(values[0] as Map);
      final tripsData = Map<String, dynamic>.from(values[1] as Map);
      publicTrips = (tripsData['records'] as List? ?? const [])
          .map(
            (e) => TripDiscoverModel.fromJson(
              Map<String, dynamic>.from(e as Map),
            ),
          )
          .toList();
      final profile = Map<String, dynamic>.from(
        data?['profile'] as Map? ?? const {},
      );
      final avatarKey = profile['avatarImageKey']?.toString() ?? '';
      avatarUrl = avatarKey.isEmpty
          ? ''
          : await StorageUploadService(api).downloadUrlByObjectKey(avatarKey);
      error = null;
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() {});
  }

  Future<void> _toggleFollow() async {
    if (isOwner || followBusy) return;
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
      final service = FollowService(context.read<AppSession>().api);
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
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e')));
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
    return Scaffold(
      backgroundColor: const Color(0xFFF3F5F8),
      body: data == null
          ? Center(
              child: error == null
                  ? const CircularProgressIndicator()
                  : Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(error!, textAlign: TextAlign.center),
                        const SizedBox(height: 10),
                        FilledButton(onPressed: load, child: const Text('重新加载')),
                      ],
                    ),
            )
          : CustomScrollView(
              slivers: [
                SliverToBoxAdapter(
                  child: Container(
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [Color(0xFF075DBB), Color(0xFF0877E5), Color(0xFF2E9AEF)],
                      ),
                    ),
                    child: SafeArea(
                      bottom: false,
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(14, 4, 14, 18),
                        child: Column(
                          children: [
                            SizedBox(
                              height: 42,
                              child: Row(
                                children: [
                                  IconButton(
                                    onPressed: () => Navigator.pop(context),
                                    icon: const Icon(LucideIcons.arrowLeft, color: Colors.white),
                                  ),
                                  const Spacer(),
                                  if (isOwner)
                                    IconButton(
                                      tooltip: '编辑个人资料',
                                      onPressed: _editProfile,
                                      icon: const Icon(LucideIcons.pencil, color: Colors.white),
                                    ),
                                ],
                              ),
                            ),
                            Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                UserAvatar(
                                  nickname: nickname,
                                  avatarImageKey: p['avatarImageKey']?.toString() ?? '',
                                  avatarUrl: avatarUrl,
                                  radius: 35,
                                  onTap: () => _previewAvatar(p),
                                  heroTag: 'profile-avatar-${widget.userId}',
                                ),
                                const SizedBox(width: 14),
                                Expanded(
                                  child: Padding(
                                    padding: const EdgeInsets.only(top: 5),
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Row(
                                          children: [
                                            Expanded(
                                              child: Text(
                                                nickname,
                                                maxLines: 1,
                                                overflow: TextOverflow.ellipsis,
                                                style: const TextStyle(
                                                  color: Colors.white,
                                                  fontSize: 21,
                                                  fontWeight: FontWeight.w900,
                                                ),
                                              ),
                                            ),
                                            if (p['drivingLicenseCertificationStatus'] == 'APPROVED')
                                              const Icon(LucideIcons.badgeCheck, color: Color(0xFF8CF5D1), size: 18),
                                          ],
                                        ),
                                        const SizedBox(height: 5),
                                        Text(
                                          p['cityName']?.toString().isNotEmpty == true
                                              ? '${p['cityName']} · 同路行车友'
                                              : '常驻地暂未填写',
                                          style: const TextStyle(color: Colors.white70, fontSize: 12.5),
                                        ),
                                        const SizedBox(height: 5),
                                        Text(
                                          p['bio']?.toString().isNotEmpty == true
                                              ? p['bio'].toString()
                                              : '愿每一次出发都有同路人',
                                          maxLines: 2,
                                          overflow: TextOverflow.ellipsis,
                                          style: const TextStyle(color: Colors.white, fontSize: 13, height: 1.35),
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 18),
                            Row(
                              children: [
                                _ProfileMetric(value: '${follow['followingCount'] ?? 0}', label: '关注'),
                                _ProfileMetric(value: '${follow['followerCount'] ?? 0}', label: '粉丝'),
                                _ProfileMetric(value: '${p['totalTripCount'] ?? 0}', label: '行程'),
                                _ProfileMetric(
                                  value: '${(((p['totalDistanceMeters'] as num?)?.toDouble() ?? 0) / 1000).round()}',
                                  label: '公里',
                                ),
                                if (!isOwner) ...[
                                  const SizedBox(width: 8),
                                  SizedBox(
                                    height: 36,
                                    child: follow['following'] == true
                                        ? OutlinedButton(
                                            style: OutlinedButton.styleFrom(
                                              foregroundColor: Colors.white,
                                              side: const BorderSide(color: Colors.white70),
                                            ),
                                            onPressed: followBusy ? null : _toggleFollow,
                                            child: Text(_followText(follow)),
                                          )
                                        : FilledButton(
                                            style: FilledButton.styleFrom(
                                              backgroundColor: Colors.white,
                                              foregroundColor: AppColors.primary,
                                            ),
                                            onPressed: followBusy ? null : _toggleFollow,
                                            child: Text(_followText(follow)),
                                          ),
                                  ),
                                  const SizedBox(width: 7),
                                  SizedBox(
                                    height: 36,
                                    child: OutlinedButton.icon(
                                      style: OutlinedButton.styleFrom(
                                        foregroundColor: Colors.white,
                                        side: const BorderSide(color: Colors.white70),
                                        padding: const EdgeInsets.symmetric(horizontal: 10),
                                      ),
                                      onPressed: _startChat,
                                      icon: const Icon(LucideIcons.messageCircle, size: 16),
                                      label: const Text('发起私聊', style: TextStyle(fontSize: 12)),
                                    ),
                                  ),
                                ],
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
                    onChanged: (value) => setState(() => section = value),
                  ),
                ),
                if (section == 0)
                  publicTrips.isEmpty
                      ? const SliverFillRemaining(
                          hasScrollBody: false,
                          child: Center(child: Text('暂时没有公开招募中的行程', style: TextStyle(color: AppColors.muted))),
                        )
                      : SliverPadding(
                          padding: const EdgeInsets.fromLTRB(12, 10, 12, 28),
                          sliver: SliverList(
                            delegate: SliverChildBuilderDelegate(
                              (_, index) {
                                if (index.isOdd) return const SizedBox(height: 8);
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
                              },
                              childCount: publicTrips.length * 2 - 1,
                            ),
                          ),
                        )
                else
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(12, 10, 12, 28),
                    sliver: SliverList(
                      delegate: SliverChildListDelegate([
                        _ProfileInfoCard(
                          icon: LucideIcons.userRound,
                          title: '个人简介',
                          value: p['bio']?.toString().isNotEmpty == true
                              ? p['bio'].toString()
                              : '暂未填写',
                        ),
                        const SizedBox(height: 8),
                        _ProfileInfoCard(
                          icon: LucideIcons.mapPin,
                          title: '常驻地',
                          value: p['cityName']?.toString().isNotEmpty == true
                              ? p['cityName'].toString()
                              : '暂未填写',
                        ),
                        const SizedBox(height: 8),
                        _ProfileInfoCard(
                          icon: LucideIcons.shieldCheck,
                          title: '驾驶认证',
                          value: p['drivingLicenseCertificationStatus'] == 'APPROVED'
                              ? '已通过驾驶证认证'
                              : '暂未认证',
                        ),
                      ]),
                    ),
                  ),
              ],
            ),
    );
  }
}

class _ProfileMetric extends StatelessWidget {
  const _ProfileMetric({required this.value, required this.label});
  final String value;
  final String label;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(right: 14),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(value, style: const TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.w900)),
        Text(label, style: const TextStyle(color: Colors.white70, fontSize: 10.5)),
      ],
    ),
  );
}

class _ProfileTabHeader extends SliverPersistentHeaderDelegate {
  const _ProfileTabHeader({required this.section, required this.onChanged});
  final int section;
  final ValueChanged<int> onChanged;

  @override
  double get minExtent => 48;
  @override
  double get maxExtent => 48;

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlapsContent) => Material(
    color: Colors.white,
    elevation: overlapsContent ? 1 : 0,
    child: Row(
      children: [
        _ProfileTab(label: '公开行程', active: section == 0, onTap: () => onChanged(0)),
        _ProfileTab(label: '资料', active: section == 1, onTap: () => onChanged(1)),
      ],
    ),
  );

  @override
  bool shouldRebuild(covariant _ProfileTabHeader oldDelegate) => oldDelegate.section != section;
}

class _ProfileTab extends StatelessWidget {
  const _ProfileTab({required this.label, required this.active, required this.onTap});
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
              fontSize: 14,
              color: active ? AppColors.primary : AppColors.muted,
              fontWeight: active ? FontWeight.w900 : FontWeight.w600,
            ),
          ),
          const SizedBox(height: 9),
          Container(
            width: 28,
            height: 3,
            decoration: BoxDecoration(
              color: active ? AppColors.primary : Colors.transparent,
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
    borderRadius: BorderRadius.circular(12),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(12),
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
                    style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w900),
                  ),
                ),
                const Text('招募中', style: TextStyle(color: AppColors.primary, fontSize: 11.5, fontWeight: FontWeight.w800)),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              '${trip.startName} → ${trip.endName}',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12.5, color: AppColors.secondaryText),
            ),
            const SizedBox(height: 7),
            Row(
              children: [
                const Icon(LucideIcons.calendar, size: 14, color: AppColors.muted),
                const SizedBox(width: 5),
                Expanded(
                  child: Text(
                    trip.departureTime,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 11.5, color: AppColors.muted),
                  ),
                ),
                Text('${trip.memberCount}/${trip.maxMemberCount} 人', style: const TextStyle(fontSize: 11.5, color: AppColors.muted)),
                const SizedBox(width: 5),
                const Icon(LucideIcons.chevronRight, size: 16, color: AppColors.muted),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

class _ProfileInfoCard extends StatelessWidget {
  const _ProfileInfoCard({required this.icon, required this.title, required this.value});
  final IconData icon;
  final String title;
  final String value;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12)),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 19, color: AppColors.primary),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
              const SizedBox(height: 4),
              Text(value, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, height: 1.4)),
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

class _Tag extends StatelessWidget {
  const _Tag(this.text);
  final String text;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
    decoration: BoxDecoration(
      color: AppColors.primarySoft,
      borderRadius: BorderRadius.circular(14),
    ),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 12,
        color: AppColors.primary,
        fontWeight: FontWeight.w700,
      ),
    ),
  );
}

class _Stat extends StatelessWidget {
  const _Stat(this.label, this.value, this.unit);
  final String label, value, unit;
  @override
  Widget build(BuildContext context) => Expanded(
    child: Column(
      children: [
        Text.rich(
          TextSpan(
            text: value,
            style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800),
            children: [
              TextSpan(
                text: ' $unit',
                style: const TextStyle(fontSize: 11, color: AppColors.muted),
              ),
            ],
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(fontSize: 12, color: AppColors.muted),
        ),
      ],
    ),
  );
}
