import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/services/app_services.dart';

class PublicProfilePage extends StatefulWidget {
  const PublicProfilePage({required this.userId, super.key});
  final String userId;
  @override
  State<PublicProfilePage> createState() => _PublicProfilePageState();
}

class _PublicProfilePageState extends State<PublicProfilePage> {
  Map<String, dynamic>? data;
  String? error;
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      data = await UserProfileService(
        context.read<AppSession>().api,
      ).homepage(widget.userId);
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() {});
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
      appBar: AppBar(title: const Text('个人主页')),
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
                      CircleAvatar(
                        radius: 42,
                        backgroundColor: AppColors.primarySoft,
                        child: const Icon(
                          LucideIcons.userRound,
                          size: 38,
                          color: AppColors.primary,
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
  final nickname = TextEditingController(),
      city = TextEditingController(),
      bio = TextEditingController();
  int gender = 0;
  bool loading = true, saving = false;
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final p = await UserProfileService(context.read<AppSession>().api).me();
    nickname.text = p['nickname']?.toString() ?? '';
    city.text = p['cityName']?.toString() ?? '';
    bio.text = p['bio']?.toString() ?? '';
    gender = (p['gender'] as num?)?.toInt() ?? 0;
    if (mounted) setState(() => loading = false);
  }

  Future<void> save() async {
    setState(() => saving = true);
    try {
      await UserProfileService(context.read<AppSession>().api).update({
        'nickname': nickname.text.trim(),
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
    } finally {
      if (mounted) setState(() => saving = false);
    }
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
                child: Stack(
                  children: [
                    const CircleAvatar(
                      radius: 46,
                      backgroundColor: AppColors.primarySoft,
                      child: Icon(
                        LucideIcons.userRound,
                        size: 40,
                        color: AppColors.primary,
                      ),
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
                child: Text(saving ? '保存中...' : '保存资料'),
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
