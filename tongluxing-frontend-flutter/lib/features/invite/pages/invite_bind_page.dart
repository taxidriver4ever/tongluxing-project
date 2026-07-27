import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/api_client.dart';
import '../models/invite_bind_status.dart';
import '../services/invite_service.dart';

class InviteBindPage extends StatefulWidget {
  const InviteBindPage({super.key});

  @override
  State<InviteBindPage> createState() => _InviteBindPageState();
}

class _InviteBindPageState extends State<InviteBindPage> {
  final TextEditingController _controller = TextEditingController();
  InviteBindStatus? _status;
  bool _loading = true;
  bool _submitting = false;
  String? _error;

  InviteBindingService get _service =>
      InviteBindingService(context.read<AppSession>().api);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final status = await _service.bindStatus();
      if (mounted) setState(() => _status = status);
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _normalize(String value) => value.trim().toUpperCase();

  Future<void> _submitCode(
    String rawCode, {
    required String sourceType,
  }) async {
    if (_submitting) return;
    final code = _normalize(rawCode);
    if (!RegExp(r'^[A-Z0-9]{6,16}$').hasMatch(code)) {
      _show('邀请码格式不正确');
      return;
    }
    setState(() => _submitting = true);
    try {
      final preview = await _service.preview(code);
      if (!mounted) return;
      final nickname = preview['inviterNickname']?.toString().trim();
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('确认绑定该邀请人吗？'),
          content: Text(
            '${nickname?.isNotEmpty == true ? '邀请人：$nickname\n\n' : ''}绑定后不可修改。',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('确认绑定'),
            ),
          ],
        ),
      );
      if (confirmed != true || !mounted) return;
      final random = Random.secure().nextInt(1 << 32);
      await _service.bind(
        inviteCode: code,
        sourceType: sourceType,
        requestId:
            'app-${DateTime.now().microsecondsSinceEpoch}-${random.toRadixString(16)}',
      );
      if (!mounted) return;
      _show('邀请绑定成功');
      await _load();
    } catch (error) {
      if (mounted) _show(_message(error));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _scan() async {
    final result = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => const _InviteQrScannerPage()),
    );
    if (result == null || !mounted) return;
    try {
      final parsed = await _parseOfficialQr(result);
      if (!mounted) return;
      _controller.text = parsed;
      await _submitCode(parsed, sourceType: 'QR_CODE');
    } catch (error) {
      if (mounted) _show(_message(error));
    }
  }

  Future<String> _parseOfficialQr(String raw) async {
    final uri = Uri.tryParse(raw.trim());
    if (uri == null) throw const ApiException('无法识别该邀请二维码');
    final customScheme = uri.scheme == 'tongluxing' && uri.host == 'invite';
    const officialHostConfig = String.fromEnvironment(
      'INVITE_OFFICIAL_HOSTS',
      defaultValue: '',
    );
    final officialHosts = officialHostConfig
        .split(',')
        .map((host) => host.trim().toLowerCase())
        .where((host) => host.isNotEmpty)
        .toSet();
    final officialHttps = uri.scheme == 'https' &&
        officialHosts.contains(uri.host.toLowerCase()) &&
        uri.path == '/invite';
    if (!customScheme && !officialHttps) {
      throw const ApiException('无法识别该邀请二维码');
    }
    var code = _normalize(uri.queryParameters['code'] ?? '');
    final token = uri.queryParameters['token']?.trim() ?? '';
    if (token.isNotEmpty) {
      final validation = await _service.validateQrToken(token);
      if (validation['valid'] != true) {
        throw const ApiException('无法识别该邀请二维码');
      }
      code = _normalize(validation['inviteCode']?.toString() ?? code);
    }
    if (!RegExp(r'^[A-Z0-9]{6,16}$').hasMatch(code)) {
      throw const ApiException('无法识别该邀请二维码');
    }
    return code;
  }

  void _show(String text) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(text)));
  }

  String _message(Object error) => error is ApiException
      ? error.message
      : error.toString().replaceFirst('Exception: ', '');

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('输入邀请码')),
    body: AsyncPanel(
      loading: _loading,
      error: _error,
      onRetry: _load,
      child: _buildContent(),
    ),
  );

  Widget _buildContent() {
    final status = _status;
    if (status == null) return const SizedBox.shrink();
    if (status.bound) {
      return _StateCard(
        icon: LucideIcons.circleCheckBig,
        title: '您已经完成邀请绑定了',
        detail: [
          if (status.inviterNickname?.isNotEmpty == true)
            '邀请人：${status.inviterNickname}',
          '邀请关系绑定后不可修改。',
        ].join('\n'),
      );
    }
    if (status.expired || !status.eligible) {
      return const _StateCard(
        icon: LucideIcons.clockAlert,
        title: '邀请码绑定期限已过',
        detail: '仅限注册后 7 天内完成邀请绑定',
      );
    }
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 28),
      children: [
        TlxCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '填写邀请码',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 6),
              const Text(
                '邀请码仅限注册后 7 天内绑定，绑定后不可修改。',
                style: TextStyle(color: AppColors.muted, fontSize: 12),
              ),
              const SizedBox(height: 18),
              TextField(
                controller: _controller,
                enabled: !_submitting,
                textCapitalization: TextCapitalization.characters,
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp('[A-Za-z0-9]')),
                  LengthLimitingTextInputFormatter(16),
                  _UpperCaseFormatter(),
                ],
                decoration: InputDecoration(
                  hintText: '请输入邀请人的邀请码',
                  prefixIcon: const Icon(LucideIcons.ticketCheck),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _submitting
                      ? null
                      : () => _submitCode(
                            _controller.text,
                            sourceType: 'MANUAL_CODE',
                          ),
                  child: _submitting
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('确认绑定'),
                ),
              ),
            ],
          ),
        ),
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 14),
          child: Row(
            children: [
              Expanded(child: Divider()),
              Padding(
                padding: EdgeInsets.symmetric(horizontal: 12),
                child: Text('或者', style: TextStyle(color: AppColors.muted)),
              ),
              Expanded(child: Divider()),
            ],
          ),
        ),
        OutlinedButton.icon(
          onPressed: _submitting ? null : _scan,
          icon: const Icon(LucideIcons.scanQrCode),
          label: const Text('扫描邀请二维码'),
        ),
        const SizedBox(height: 10),
        const Text(
          '相机权限被拒绝时，仍可返回本页手动输入邀请码。',
          textAlign: TextAlign.center,
          style: TextStyle(color: AppColors.muted, fontSize: 11),
        ),
      ],
    );
  }
}

class _StateCard extends StatelessWidget {
  const _StateCard({
    required this.icon,
    required this.title,
    required this.detail,
  });

  final IconData icon;
  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) => Center(
    child: SingleChildScrollView(
      padding: const EdgeInsets.all(22),
      child: TlxCard(
        child: Column(
          children: [
            Container(
              width: 58,
              height: 58,
              decoration: const BoxDecoration(
                color: Color(0xFFE8F2FF),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: AppColors.primary, size: 30),
            ),
            const SizedBox(height: 18),
            Text(
              title,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 9),
            Text(
              detail,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.muted, height: 1.6),
            ),
          ],
        ),
      ),
    ),
  );
}

class _InviteQrScannerPage extends StatefulWidget {
  const _InviteQrScannerPage();

  @override
  State<_InviteQrScannerPage> createState() => _InviteQrScannerPageState();
}

class _InviteQrScannerPageState extends State<_InviteQrScannerPage> {
  final MobileScannerController _controller = MobileScannerController(
    detectionSpeed: DetectionSpeed.noDuplicates,
  );
  bool _handled = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Colors.black,
    appBar: AppBar(
      title: const Text('扫描邀请二维码'),
      backgroundColor: Colors.black,
      foregroundColor: Colors.white,
    ),
    body: Stack(
      fit: StackFit.expand,
      children: [
        MobileScanner(
          controller: _controller,
          onDetect: (capture) {
            if (_handled || capture.barcodes.isEmpty) return;
            final raw = capture.barcodes.first.rawValue;
            if (raw == null || raw.isEmpty) return;
            _handled = true;
            Navigator.pop(context, raw);
          },
          errorBuilder: (context, error) => Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(
                '无法开启相机，请返回后手动输入邀请码。\n$error',
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white),
              ),
            ),
          ),
        ),
        Center(
          child: Container(
            width: 240,
            height: 240,
            decoration: BoxDecoration(
              border: Border.all(color: Colors.white, width: 2),
              borderRadius: BorderRadius.circular(20),
            ),
          ),
        ),
        const Positioned(
          left: 24,
          right: 24,
          bottom: 54,
          child: Text(
            '请将同路行官方邀请二维码放入框内',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white, fontSize: 14),
          ),
        ),
      ],
    ),
  );
}

class _UpperCaseFormatter extends TextInputFormatter {
  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) => newValue.copyWith(text: newValue.text.toUpperCase());
}
