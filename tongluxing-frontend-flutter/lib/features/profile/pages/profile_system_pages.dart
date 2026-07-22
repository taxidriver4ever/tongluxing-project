import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import '../widgets/user_avatar.dart';

class PublicProfilePage extends StatefulWidget {
  const PublicProfilePage({required this.userId, super.key});
  final String userId;

  @override
  State<PublicProfilePage> createState() => _PublicProfilePageState();
}

class _PublicProfilePageState extends State<PublicProfilePage> {
  Map<String, dynamic>? data;
  String? error;
  String avatarUrl = '';

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
      data = await UserProfileService(api).homepage(widget.userId);
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

  @override
  Widget build(BuildContext context) {
    final p = Map<String, dynamic>.from(data?['profile'] as Map? ?? const {});
    final g = Map<String, dynamic>.from(data?['growth'] as Map? ?? const {});
    final badges = Map<String, dynamic>.from(
      data?['badges'] as Map? ?? const {},
    );
    final earned = badges['earned'] as List? ?? const [];
    return Scaffold(
      appBar: AppBar(
        title: const Text('个人主页'),
        actions: [
          if (isOwner)
            IconButton(
              tooltip: '编辑个人资料',
              onPressed: _editProfile,
              icon: const Icon(LucideIcons.pencil),
            ),
        ],
      ),
      body: data == null
          ? Center(
              child: error == null
                  ? const CircularProgressIndicator()
                  : Text(error!),
            )
          : ListView(
              padding: const EdgeInsets.fromLTRB(22, 12, 22, 36),
              children: [
                Container(
                  padding: const EdgeInsets.all(22),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFFEAF2FF), Colors.white],
                    ),
                    borderRadius: BorderRadius.circular(28),
                  ),
                  child: Column(
                    children: [
                      UserAvatar(
                        nickname: p['nickname']?.toString() ?? '同路行用户',
                        avatarImageKey: p['avatarImageKey']?.toString() ?? '',
                        avatarUrl: avatarUrl,
                        radius: 42,
                        onTap: () => _previewAvatar(p),
                        heroTag: 'profile-avatar-${widget.userId}',
                      ),
                      const SizedBox(height: 8),
                      Text(
                        isOwner ? '点击头像可预览并修改' : '点击头像可查看大图',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.muted,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        (p['nickname']?.toString().isNotEmpty ?? false)
                            ? p['nickname'].toString()
                            : '同路行用户',
                        style: const TextStyle(
                          fontSize: 22,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        p['bio']?.toString().isNotEmpty == true
                            ? p['bio'].toString()
                            : '愿每一次出发都有同路人',
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: AppColors.muted),
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 8,
                        children: [
                          _Tag('${g['levelCode'] ?? 'LV1'}'),
                          _Tag(
                            p['drivingLicenseCertificationStatus'] == 'APPROVED'
                                ? '驾驶认证'
                                : '资料已公开',
                          ),
                          if (p['cityName']?.toString().isNotEmpty == true)
                            _Tag(p['cityName'].toString()),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    _Stat('出行次数', '${p['totalTripCount'] ?? 0}', '次'),
                    _Stat(
                      '累计里程',
                      (((p['totalDistanceMeters'] as num?) ?? 0) / 1000)
                          .toStringAsFixed(1),
                      'km',
                    ),
                    _Stat('经停点', '${p['completedWaypointCount'] ?? 0}', '个'),
                  ],
                ),
                const SizedBox(height: 18),
                Container(
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(22),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        LucideIcons.sparkles,
                        color: Color(0xFFF59E0B),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              '成长与荣誉',
                              style: TextStyle(fontWeight: FontWeight.w800),
                            ),
                            const SizedBox(height: 5),
                            Text(
                              '${g['totalPoints'] ?? 0} 成长值 · ${earned.length} 枚勋章',
                              style: const TextStyle(color: AppColors.muted),
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
  }
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
                decoration: const InputDecoration(labelText: '昵称'),
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
                decoration: const InputDecoration(labelText: '常住城市'),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: bio,
                maxLines: 4,
                decoration: const InputDecoration(
                  labelText: '个人简介',
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
