import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/app_services.dart';

class InvitePage extends StatefulWidget {
  const InvitePage({super.key});
  @override
  State<InvitePage> createState() => _InvitePageState();
}

class _InvitePageState extends State<InvitePage> {
  bool loading = true;
  String? error;
  Map<String, dynamic> code = {};
  Map<String, dynamic> qr = {};
  Map<String, dynamic> summary = {};
  static const _mediaChannel = MethodChannel('com.tongluxing/media');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final service = InviteService(context.read<AppSession>().api);
      final values = await Future.wait([
        service.code(),
        service.qr(),
        service.summary(),
      ]);
      code = values[0];
      qr = values[1];
      summary = values[2];
      error = null;
    } catch (e) {
      error = '$e';
    }
    if (mounted) setState(() => loading = false);
  }

  String get inviteCode =>
      code['inviteCode']?.toString() ?? code['code']?.toString() ?? '—';

  Uint8List? get qrBytes {
    final source = qr['qrImageBase64']?.toString();
    if (source == null || source.isEmpty) return null;
    try {
      return base64Decode(source);
    } catch (_) {
      return null;
    }
  }

  Future<void> saveQr() async {
    final bytes = qrBytes;
    if (bytes == null) return;
    try {
      await _mediaChannel.invokeMethod<String>('savePngToGallery', {
        'bytes': bytes,
        'fileName': '同路行邀请码_$inviteCode.png',
      });
      if (mounted) _message('二维码已保存到相册 Pictures/Tongluxing');
    } on PlatformException catch (exception) {
      if (mounted) _message(exception.message ?? '保存失败');
    }
  }

  Future<void> validateCurrentQr() async {
    final token = qr['qrToken']?.toString() ?? '';
    if (token.isEmpty) return;
    try {
      final result = await InviteService(
        context.read<AppSession>().api,
      ).validateQr(token);
      final status = result['status']?.toString() ?? 'INVALID';
      if (mounted) {
        _message(status == 'VALID' ? '二维码有效，可正常绑定邀请关系' : '二维码状态：$status');
      }
    } catch (exception) {
      if (mounted) _message('$exception');
    }
  }

  void _message(String text) =>
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('邀请好友')),
    body: AsyncPanel(
      loading: loading,
      error: error,
      onRetry: load,
      child: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.primary,
              borderRadius: BorderRadius.circular(26),
            ),
            child: Column(
              children: [
                const Text(
                  '邀请好友一起出发',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  '好友完成注册后，你将获得成长值',
                  style: TextStyle(color: Colors.white70),
                ),
                const SizedBox(height: 22),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: qrBytes == null
                      ? const SizedBox(
                          width: 190,
                          height: 190,
                          child: Center(child: CircularProgressIndicator()),
                        )
                      : Image.memory(qrBytes!, width: 190, height: 190),
                ),
                const SizedBox(height: 14),
                Text(
                  '邀请码  $inviteCode',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    letterSpacing: 2,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  '有效至 ${_displayTime(qr['expiresAt'])} · 每 7 天自动更换',
                  style: const TextStyle(color: Colors.white70, fontSize: 12),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          TlxCard(
            color: const Color(0xFFF8FAFD),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _Metric(
                  label: '有效邀请',
                  value: '${summary['validInviteCount'] ?? 0}',
                ),
                _Metric(
                  label: '下一档还差',
                  value: '${summary['nextRewardNeed'] ?? 0}',
                ),
                _Metric(
                  label: '已获规则',
                  value:
                      '${(summary['grantedRuleCodes'] as List? ?? const []).length}',
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: FilledButton.icon(
                  onPressed: qrBytes == null ? null : saveQr,
                  icon: const Icon(LucideIcons.download),
                  label: const Text('保存二维码'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: () async {
                    await Clipboard.setData(
                      ClipboardData(
                        text: qr['qrContent']?.toString() ?? inviteCode,
                      ),
                    );
                    if (mounted) _message('邀请链接已复制');
                  },
                  icon: const Icon(LucideIcons.copy),
                  label: const Text('复制邀请链接'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          const TlxCard(
            color: Color(0xFFF8FAFD),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(LucideIcons.bookOpenCheck, color: AppColors.primary),
                    SizedBox(width: 10),
                    Text(
                      '邀请奖励使用教程',
                      style: TextStyle(fontWeight: FontWeight.w800),
                    ),
                  ],
                ),
                SizedBox(height: 16),
                _InviteStep(
                  number: '1',
                  title: '保存邀请二维码',
                  detail: '点击上方“保存二维码”，图片会真实保存到手机相册。',
                ),
                _InviteStep(
                  number: '2',
                  title: '把二维码发给好友',
                  detail: '好友可通过相册、聊天软件或扫码入口识别这张二维码。',
                ),
                _InviteStep(
                  number: '3',
                  title: '好友完成注册并设置密码',
                  detail: '好友使用手机号完成注册，并在首次登录流程中设置密码。',
                ),
                _InviteStep(
                  number: '4',
                  title: '系统自动绑定并发放奖励',
                  detail: '好友识别二维码后完成注册，系统会自动绑定邀请关系，无需人工上传截图审核。',
                  last: true,
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),
          TextButton.icon(
            onPressed: validateCurrentQr,
            icon: const Icon(LucideIcons.shieldCheck),
            label: const Text('验证当前二维码是否有效'),
          ),
          const SizedBox(height: 8),
          const Text(
            '安全提示：二维码带有服务端签名。过期、被篡改或已停用的邀请码都会被拒绝。',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.muted, fontSize: 11),
          ),
        ],
      ),
    ),
  );

  String _displayTime(Object? value) {
    final text = value?.toString() ?? '';
    if (text.length >= 16) return text.substring(0, 16).replaceFirst('T', ' ');
    return text.isEmpty ? '—' : text;
  }
}

class _Metric extends StatelessWidget {
  const _Metric({required this.label, required this.value});
  final String label, value;
  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(
        value,
        style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
      ),
      const SizedBox(height: 5),
      Text(label, style: const TextStyle(color: AppColors.muted, fontSize: 12)),
    ],
  );
}


class _InviteStep extends StatelessWidget {
  const _InviteStep({
    required this.number,
    required this.title,
    required this.detail,
    this.last = false,
  });

  final String number;
  final String title;
  final String detail;
  final bool last;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Column(
        children: [
          Container(
            width: 28,
            height: 28,
            alignment: Alignment.center,
            decoration: const BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
            ),
            child: Text(
              number,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          if (!last)
            Container(
              width: 2,
              height: 44,
              color: AppColors.primarySoft,
            ),
        ],
      ),
      const SizedBox(width: 12),
      Expanded(
        child: Padding(
          padding: EdgeInsets.only(bottom: last ? 0 : 14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
              const SizedBox(height: 4),
              Text(
                detail,
                style: const TextStyle(
                  color: AppColors.muted,
                  fontSize: 12,
                  height: 1.45,
                ),
              ),
            ],
          ),
        ),
      ),
    ],
  );
}
