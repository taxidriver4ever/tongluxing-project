import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';

class MerchantOnboardingPage extends StatefulWidget {
  const MerchantOnboardingPage({super.key});
  @override
  State<MerchantOnboardingPage> createState() => _MerchantOnboardingPageState();
}

class _MerchantOnboardingPageState extends State<MerchantOnboardingPage> {
  bool loading = true;
  Map<String, dynamic>? application;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final value = await MerchantOnboardingService(
        context.read<AppSession>().api,
      ).latest();
      if (mounted) {
        setState(() {
          application = value;
          loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => loading = false);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_message(e))));
      }
    }
  }

  void _start() async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => MerchantApplicationForm(existing: application),
      ),
    );
    _load();
  }

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final status = application?['auditStatus']?.toString();
    return Scaffold(
      appBar: AppBar(title: const Text('成为商家')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
        children: [
          if (application == null) ...[
            const _HeroCard(),
            const SizedBox(height: 18),
            const _InfoCard(
              title: '入驻流程',
              lines: [
                '填写主体与门店资料',
                '提交平台后台审核',
                '通过后使用原账号进入商家后台',
                '审核通过后再补充收款账户',
              ],
            ),
            const SizedBox(height: 18),
            FilledButton(onPressed: _start, child: const Text('开始申请')),
          ] else ...[
            _StatusCard(status: status ?? 'PENDING', application: application!),
            const SizedBox(height: 16),
            _ApplicationSummary(application: application!),
            const SizedBox(height: 18),
            if (status == 'REJECTED')
              FilledButton(onPressed: _start, child: const Text('修改资料并重新提交')),
            if (status == 'APPROVED')
              FilledButton.icon(
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const MerchantSettlementPage(),
                  ),
                ),
                icon: const Icon(LucideIcons.landmark, size: 18),
                label: const Text('补充收款账户'),
              ),
          ],
        ],
      ),
    );
  }
}

class MerchantApplicationForm extends StatefulWidget {
  const MerchantApplicationForm({super.key, this.existing});
  final Map<String, dynamic>? existing;
  @override
  State<MerchantApplicationForm> createState() =>
      _MerchantApplicationFormState();
}

class _MerchantApplicationFormState extends State<MerchantApplicationForm> {
  static const _specialCategories = {'餐饮', '酒店', '旅行社', '汽车救援'};
  int step = 0;
  bool submitting = false;
  final formKeys = List.generate(4, (_) => GlobalKey<FormState>());
  late final Map<String, TextEditingController> c;
  final picked = <String>{};
  String category = '餐饮';

  @override
  void initState() {
    super.initState();
    final d = Map<String, dynamic>.from(
      widget.existing?['applicationDetails'] as Map? ?? const {},
    );
    String val(String key, [String fallback = '']) =>
        d[key]?.toString() ?? fallback;
    category = widget.existing?['category']?.toString() ?? '餐饮';
    c = {
      'merchantName': TextEditingController(
        text: widget.existing?['merchantName']?.toString() ?? '',
      ),
      'merchantShortName': TextEditingController(
        text: val('merchantShortName'),
      ),
      'introduction': TextEditingController(text: val('introduction')),
      'contactName': TextEditingController(text: val('contactName')),
      'contactPhone': TextEditingController(text: '13888888888'),
      'contactEmail': TextEditingController(text: val('contactEmail')),
      'contactWechat': TextEditingController(text: val('contactWechat')),
      'storeName': TextEditingController(text: val('storeName')),
      'storeAddress': TextEditingController(text: val('storeAddress')),
      'legalName': TextEditingController(text: val('legalRepresentativeName')),
    };
    for (final key in [
      'logo',
      'license',
      'storefront',
      'interior1',
      'interior2',
      'interior3',
      'idFront',
      'idBack',
    ]) {
      if (widget.existing != null) picked.add(key);
    }
  }

  @override
  void dispose() {
    for (final v in c.values) {
      v.dispose();
    }
    super.dispose();
  }

  bool _validateStep() {
    if (!(formKeys[step].currentState?.validate() ?? false)) return false;
    final required = switch (step) {
      0 => ['logo', 'license'],
      2 => ['storefront', 'interior1', 'interior2', 'interior3'],
      3 => ['idFront', 'idBack'],
      _ => <String>[],
    };
    if (required.any((e) => !picked.contains(e))) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请补齐本步骤要求的图片资料')));
      return false;
    }
    return true;
  }

  Future<void> _submit() async {
    if (!_validateStep()) return;
    setState(() => submitting = true);
    final body = <String, dynamic>{
      'merchantName': c['merchantName']!.text.trim(),
      'merchantShortName': c['merchantShortName']!.text.trim(),
      'logoImageKey': 'merchant/app/logo.jpg',
      'introduction': c['introduction']!.text.trim(),
      'category': category,
      'businessLicenseImageKey': 'merchant/app/license.jpg',
      'contactName': c['contactName']!.text.trim(),
      'contactPhone': c['contactPhone']!.text.trim(),
      'contactEmail': c['contactEmail']!.text.trim(),
      'contactWechat': c['contactWechat']!.text.trim(),
      'storeName': c['storeName']!.text.trim(),
      'storeAddress': c['storeAddress']!.text.trim(),
      'longitude': 113.2644,
      'latitude': 23.1291,
      'storefrontImageKey': 'merchant/app/storefront.jpg',
      'interiorImageKeys': [
        'merchant/app/interior-1.jpg',
        'merchant/app/interior-2.jpg',
        'merchant/app/interior-3.jpg',
      ],
      'legalRepresentativeName': c['legalName']!.text.trim(),
      'legalIdFrontImageKey': 'merchant/app/legal-front.jpg',
      'legalIdBackImageKey': 'merchant/app/legal-back.jpg',
      'qualificationImageKeys': _specialCategories.contains(category)
          ? ['merchant/app/industry-license.jpg']
          : <String>[],
    };
    try {
      await MerchantOnboardingService(
        context.read<AppSession>().api,
      ).submit(body);
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('申请已提交，平台将在 1～3 个工作日内审核')));
        Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_message(e))));
      }
    } finally {
      if (mounted) setState(() => submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.existing == null ? '商家入驻申请' : '修改入驻资料')),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
          child: Row(
            children: List.generate(
              4,
              (i) => Expanded(
                child: Container(
                  height: 5,
                  margin: EdgeInsets.only(right: i == 3 ? 0 : 7),
                  decoration: BoxDecoration(
                    color: i <= step ? AppColors.primary : AppColors.border,
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
            ),
          ),
        ),
        Expanded(
          child: GestureDetector(
            onTap: () => FocusScope.of(context).unfocus(),
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 8, 20, 28),
              child: _stepBody(),
            ),
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 10, 20, 14),
            child: Row(
              children: [
                if (step > 0)
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => setState(() => step--),
                      child: const Text('上一步'),
                    ),
                  ),
                if (step > 0) const SizedBox(width: 12),
                Expanded(
                  flex: 2,
                  child: FilledButton(
                    onPressed: submitting
                        ? null
                        : () {
                            if (step < 3) {
                              if (_validateStep()) setState(() => step++);
                            } else {
                              _submit();
                            }
                          },
                    child: Text(
                      step == 3 ? (submitting ? '提交中…' : '确认提交审核') : '下一步',
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    ),
  );

  Widget _stepBody() => Form(
    key: formKeys[step],
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          ['企业信息', '联系信息', '门店信息', '法人及资质'][step],
          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 6),
        Text(
          [
            '营业执照名称须与商家名称一致',
            '用于平台审核人员联系，请确保真实有效',
            '线下门店需提供定位及现场照片',
            '身份资料仅用于审核并加密保存',
          ][step],
          style: const TextStyle(color: AppColors.muted),
        ),
        const SizedBox(height: 22),
        if (step == 0) ...[
          _field('merchantName', '营业执照主体名称 *'),
          _field('merchantShortName', '商家简称 *'),
          DropdownButtonFormField<String>(
            initialValue: category,
            decoration: const InputDecoration(labelText: '经营类目 *'),
            items: [
              '餐饮',
              '酒店',
              '旅行社',
              '汽车救援',
              '景区',
              '零售',
            ].map((e) => DropdownMenuItem(value: e, child: Text(e))).toList(),
            onChanged: (v) => category = v!,
          ),
          const SizedBox(height: 14),
          _field('introduction', '商家介绍 *', maxLines: 4),
          _upload('logo', '商家 Logo *'),
          _upload('license', '营业执照 *'),
        ],
        if (step == 1) ...[
          _field('contactName', '联系人 *'),
          _field('contactPhone', '手机号 *', keyboard: TextInputType.phone),
          _field('contactEmail', '邮箱 *', keyboard: TextInputType.emailAddress),
          _field('contactWechat', '微信（选填）', required: false),
        ],
        if (step == 2) ...[
          _field('storeName', '门店名称 *'),
          _field('storeAddress', '门店地址 *'),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.primarySoft,
              borderRadius: BorderRadius.circular(18),
            ),
            child: const Row(
              children: [
                Icon(LucideIcons.mapPin, color: AppColors.primary),
                SizedBox(width: 12),
                Expanded(child: Text('地图定位：广州市中心 · 可在正式地图选择器中调整')),
              ],
            ),
          ),
          _upload('storefront', '门头照片 *'),
          const Padding(
            padding: EdgeInsets.only(top: 14, bottom: 6),
            child: Text(
              '店内照片（3～6 张）',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
          ),
          Row(
            children: [
              Expanded(child: _upload('interior1', '店内 1')),
              SizedBox(width: 8),
              Expanded(child: _upload('interior2', '店内 2')),
              SizedBox(width: 8),
              Expanded(child: _upload('interior3', '店内 3')),
            ],
          ),
        ],
        if (step == 3) ...[
          _field('legalName', '法人姓名 *'),
          Row(
            children: [
              Expanded(child: _upload('idFront', '身份证正面 *')),
              const SizedBox(width: 10),
              Expanded(child: _upload('idBack', '身份证反面 *')),
            ],
          ),
          const SizedBox(height: 16),
          _upload(
            'qualification',
            _specialCategories.contains(category)
                ? '经营资质（当前类目必填）*'
                : '经营资质（按类目要求）',
            optional: !_specialCategories.contains(category),
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFFFFF7E8),
              borderRadius: BorderRadius.circular(18),
            ),
            child: const Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(LucideIcons.shieldCheck, color: AppColors.warning),
                SizedBox(width: 10),
                Expanded(
                  child: Text('收款账户不在本次申请中填写。审核通过后再进入“补充收款账户”，降低敏感资料暴露。'),
                ),
              ],
            ),
          ),
        ],
      ],
    ),
  );

  Widget _field(
    String key,
    String label, {
    bool required = true,
    int maxLines = 1,
    TextInputType? keyboard,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: TextFormField(
      controller: c[key],
      maxLines: maxLines,
      keyboardType: keyboard,
      decoration: InputDecoration(
        labelText: label,
        alignLabelWithHint: maxLines > 1,
      ),
      validator: (v) =>
          required && (v == null || v.trim().isEmpty) ? '请填写$label' : null,
    ),
  );

  Widget _upload(String key, String label, {bool optional = false}) => Padding(
    padding: const EdgeInsets.only(top: 12),
    child: InkWell(
      onTap: () => setState(() => picked.add(key)),
      borderRadius: BorderRadius.circular(18),
      child: Container(
        height: 76,
        padding: const EdgeInsets.symmetric(horizontal: 14),
        decoration: BoxDecoration(
          color: picked.contains(key) ? AppColors.primarySoft : Colors.white,
          border: Border.all(
            color: picked.contains(key) ? AppColors.primary : AppColors.border,
          ),
          borderRadius: BorderRadius.circular(18),
        ),
        child: Row(
          children: [
            Icon(
              picked.contains(key)
                  ? LucideIcons.circleCheck
                  : LucideIcons.imagePlus,
              color: picked.contains(key) ? AppColors.primary : AppColors.muted,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                picked.contains(key) ? '$label · 已选择' : label,
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
            ),
            Text(
              optional ? '选填' : '上传',
              style: const TextStyle(color: AppColors.primary),
            ),
          ],
        ),
      ),
    ),
  );
}

class MerchantSettlementPage extends StatefulWidget {
  const MerchantSettlementPage({super.key});
  @override
  State<MerchantSettlementPage> createState() => _MerchantSettlementPageState();
}

class _MerchantSettlementPageState extends State<MerchantSettlementPage> {
  final key = GlobalKey<FormState>();
  final name = TextEditingController(),
      no = TextEditingController(),
      bank = TextEditingController();
  String type = 'CORPORATE';
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('收款账户')),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const _InfoCard(
          title: '审核通过后填写',
          lines: ['支持对公账户或法人银行卡', '账户信息将加密保存', '用于后续活动补贴与结算'],
        ),
        const SizedBox(height: 18),
        Form(
          key: key,
          child: Column(
            children: [
              DropdownButtonFormField(
                initialValue: type,
                decoration: const InputDecoration(labelText: '账户类型'),
                items: const [
                  DropdownMenuItem(value: 'CORPORATE', child: Text('对公账户')),
                  DropdownMenuItem(value: 'LEGAL_PERSON', child: Text('法人银行卡')),
                ],
                onChanged: (v) => type = v!,
              ),
              const SizedBox(height: 14),
              _input(name, '账户名称'),
              const SizedBox(height: 14),
              _input(no, '收款账号'),
              const SizedBox(height: 14),
              _input(bank, '开户行'),
              const SizedBox(height: 22),
              FilledButton(onPressed: _save, child: const Text('保存收款账户')),
            ],
          ),
        ),
      ],
    ),
  );
  Widget _input(TextEditingController c, String label) => TextFormField(
    controller: c,
    decoration: InputDecoration(labelText: label),
    validator: (v) => v == null || v.trim().isEmpty ? '请填写$label' : null,
  );
  Future<void> _save() async {
    if (!(key.currentState?.validate() ?? false)) return;
    try {
      await MerchantOnboardingService(
        context.read<AppSession>().api,
      ).saveSettlement({
        'accountType': type,
        'accountName': name.text.trim(),
        'accountNo': no.text.trim(),
        'bankName': bank.text.trim(),
      });
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('收款账户已安全保存')));
        Navigator.pop(context);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(_message(e))));
      }
    }
  }
}

class _HeroCard extends StatelessWidget {
  const _HeroCard();
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      gradient: const LinearGradient(
        colors: [Color(0xFF3A86FF), Color(0xFF65A8FF)],
      ),
      borderRadius: BorderRadius.circular(26),
    ),
    child: const Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(LucideIcons.store, color: Colors.white, size: 34),
        SizedBox(height: 22),
        Text(
          '让更多自驾用户发现你的门店',
          style: TextStyle(
            color: Colors.white,
            fontSize: 22,
            fontWeight: FontWeight.w800,
          ),
        ),
        SizedBox(height: 8),
        Text(
          '入驻审核通过后，可使用当前同路行账号登录商家后台。',
          style: TextStyle(color: Color(0xFFEAF3FF), height: 1.5),
        ),
      ],
    ),
  );
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({required this.title, required this.lines});
  final String title;
  final List<String> lines;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(22),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 12),
        ...lines.map(
          (e) => Padding(
            padding: const EdgeInsets.only(bottom: 9),
            child: Row(
              children: [
                const Icon(
                  LucideIcons.circleCheck,
                  size: 17,
                  color: AppColors.success,
                ),
                const SizedBox(width: 9),
                Expanded(child: Text(e)),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.status, required this.application});
  final String status;
  final Map<String, dynamic> application;
  @override
  Widget build(BuildContext context) {
    final approved = status == 'APPROVED', rejected = status == 'REJECTED';
    final color = approved
        ? AppColors.success
        : rejected
        ? AppColors.danger
        : AppColors.warning;
    final title = approved
        ? '审核已通过'
        : rejected
        ? '审核未通过'
        : '资料审核中';
    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .09),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Column(
        children: [
          Icon(
            approved
                ? LucideIcons.badgeCheck
                : rejected
                ? LucideIcons.circleX
                : LucideIcons.clock3,
            color: color,
            size: 42,
          ),
          const SizedBox(height: 12),
          Text(
            title,
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w800,
              color: color,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            rejected
                ? (application['rejectReason']?.toString().isNotEmpty == true
                      ? application['rejectReason'].toString()
                      : '请修改资料后重新提交')
                : (approved ? '商家权限已开通，可使用原账号登录商家后台' : '平台预计 1～3 个工作日完成审核'),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

class _ApplicationSummary extends StatelessWidget {
  const _ApplicationSummary({required this.application});
  final Map<String, dynamic> application;
  @override
  Widget build(BuildContext context) {
    final d = Map<String, dynamic>.from(
      application['applicationDetails'] as Map? ?? const {},
    );
    return _InfoCard(
      title: '申请资料',
      lines: [
        '${application['merchantName'] ?? ''} · ${application['category'] ?? ''}',
        '门店：${d['storeName'] ?? '—'}',
        '地址：${d['storeAddress'] ?? '—'}',
        '联系人：${d['contactName'] ?? '—'} ${d['contactPhoneMask'] ?? ''}',
      ],
    );
  }
}

String _message(Object e) => e is ApiException
    ? e.message
    : e.toString().replaceFirst('Exception: ', '');
