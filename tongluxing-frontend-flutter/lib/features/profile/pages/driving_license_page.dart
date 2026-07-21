import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/app_services.dart';

class DrivingLicensePage extends StatefulWidget {
  const DrivingLicensePage({super.key});

  @override
  State<DrivingLicensePage> createState() => _DrivingLicensePageState();
}

class _DrivingLicensePageState extends State<DrivingLicensePage> {
  static const _frontImage =
      'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="640" height="400"%3E%3Crect width="100%25" height="100%25" fill="%23eef3ff"/%3E%3Ctext x="50%25" y="50%25" text-anchor="middle" fill="%23285cff" font-size="38"%3EDRIVING LICENSE FRONT%3C/text%3E%3C/svg%3E';
  static const _backImage =
      'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="640" height="400"%3E%3Crect width="100%25" height="100%25" fill="%23f4f7ff"/%3E%3Ctext x="50%25" y="50%25" text-anchor="middle" fill="%23285cff" font-size="38"%3EDRIVING LICENSE BACK%3C/text%3E%3C/svg%3E';

  final _formKey = GlobalKey<FormState>();
  final _holderName = TextEditingController();
  final _licenseNo = TextEditingController();
  final _vehicleClass = TextEditingController(text: 'C1');
  final _authority = TextEditingController();
  final _selectedImages = <String>{};
  Map<String, dynamic>? _status;
  bool _loading = true;
  bool _submitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await DrivingLicenseService(
        context.read<AppSession>().api,
      ).latest();
      if (mounted) setState(() => _status = result);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedImages.length != 2) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请上传驾驶证正面和背面')));
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() => _submitting = true);
    try {
      final result = await DrivingLicenseService(context.read<AppSession>().api)
          .submit(
            holderName: _holderName.text.trim(),
            licenseNo: _licenseNo.text.trim(),
            vehicleClass: _vehicleClass.text.trim().toUpperCase(),
            issuingAuthority: _authority.text.trim(),
            licenseFrontImageKey: _frontImage,
            licenseBackImageKey: _backImage,
          );
      if (!mounted) return;
      setState(() => _status = result);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('驾驶证认证已提交，等待平台审核')));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  void dispose() {
    _holderName.dispose();
    _licenseNo.dispose();
    _vehicleClass.dispose();
    _authority.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => GestureDetector(
    onTap: () => FocusScope.of(context).unfocus(),
    child: Scaffold(
      appBar: AppBar(title: const Text('驾驶证认证')),
      body: AsyncPanel(
        loading: _loading,
        error: _error,
        onRetry: _load,
        child: RefreshIndicator(
          onRefresh: _load,
          child: ListView(
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(22),
            children: [
              _statusCard(),
              if (_canSubmit) ...[const SizedBox(height: 18), _form()],
            ],
          ),
        ),
      ),
    ),
  );

  bool get _canSubmit => const {
    'UNSUBMITTED',
    'REJECTED',
  }.contains(_status?['status'] ?? 'UNSUBMITTED');

  Widget _statusCard() {
    final status = _status?['status']?.toString() ?? 'UNSUBMITTED';
    final meta = switch (status) {
      'PENDING' => (
        '认证审核中',
        '材料已提交，请等待平台审核',
        LucideIcons.clock3,
        AppColors.warning,
      ),
      'APPROVED' => (
        '驾驶证已认证',
        '认证结果已同步到个人资料',
        LucideIcons.badgeCheck,
        AppColors.success,
      ),
      'REJECTED' => (
        '认证被驳回',
        _status?['rejectReason']?.toString() ?? '请修改材料后重新提交',
        LucideIcons.circleX,
        AppColors.danger,
      ),
      _ => (
        '尚未认证驾驶证',
        '驾驶证认证与车辆认证相互独立',
        LucideIcons.contact,
        AppColors.primary,
      ),
    };
    return TlxCard(
      color: meta.$4.withValues(alpha: .08),
      child: Row(
        children: [
          CircleAvatar(
            radius: 26,
            backgroundColor: meta.$4.withValues(alpha: .14),
            child: Icon(meta.$3, color: meta.$4),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  meta.$1,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  meta.$2,
                  style: const TextStyle(color: AppColors.muted, height: 1.4),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _form() => Form(
    key: _formKey,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          '填写驾驶证信息',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 12),
        TlxCard(
          color: const Color(0xFFF8FAFD),
          child: Column(
            children: [
              _field(_holderName, '持证人姓名', LucideIcons.userRound),
              const SizedBox(height: 12),
              _field(_licenseNo, '驾驶证号', LucideIcons.badge),
              const SizedBox(height: 12),
              _field(_vehicleClass, '准驾车型', LucideIcons.carFront),
              const SizedBox(height: 12),
              TextFormField(
                controller: _authority,
                decoration: const InputDecoration(
                  labelText: '发证机关（选填）',
                  prefixIcon: Icon(LucideIcons.landmark),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        TlxCard(
          color: Colors.white,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                '驾驶证照片',
                style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 4),
              const Text(
                '请上传清晰完整的正面和背面',
                style: TextStyle(color: AppColors.muted, fontSize: 12),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  _imageButton('正面'),
                  const SizedBox(width: 10),
                  _imageButton('背面'),
                ],
              ),
            ],
          ),
        ),
        const SizedBox(height: 22),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            onPressed: _submitting ? null : _submit,
            icon: _submitting
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(LucideIcons.shieldCheck),
            label: Text(_submitting ? '提交中…' : '提交驾驶证认证'),
          ),
        ),
      ],
    ),
  );

  Widget _imageButton(String label) {
    final selected = _selectedImages.contains(label);
    return Expanded(
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => setState(
          () => selected
              ? _selectedImages.remove(label)
              : _selectedImages.add(label),
        ),
        child: Container(
          height: 104,
          decoration: BoxDecoration(
            color: selected ? AppColors.primarySoft : const Color(0xFFF8FAFD),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: selected ? AppColors.primary : const Color(0xFFDDE3ED),
            ),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                selected ? LucideIcons.circleCheck : LucideIcons.imagePlus,
                color: selected ? AppColors.primary : AppColors.muted,
              ),
              const SizedBox(height: 8),
              Text(
                '驾驶证$label',
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ],
          ),
        ),
      ),
    );
  }

  TextFormField _field(
    TextEditingController controller,
    String label,
    IconData icon,
  ) => TextFormField(
    controller: controller,
    decoration: InputDecoration(labelText: label, prefixIcon: Icon(icon)),
    validator: (value) {
      final text = value?.trim() ?? '';
      if (text.isEmpty) return '请填写$label';
      if (label == '驾驶证号' && !RegExp(r'^[0-9A-Za-z]{6,32}$').hasMatch(text)) {
        return '请输入 6–32 位数字或字母';
      }
      return null;
    },
  );
}
