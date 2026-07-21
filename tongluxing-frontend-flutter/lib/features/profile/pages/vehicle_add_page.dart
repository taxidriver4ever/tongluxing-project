import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/app_services.dart';

class VehicleAddPage extends StatefulWidget {
  const VehicleAddPage({super.key});
  @override
  State<VehicleAddPage> createState() => _VehicleAddPageState();
}

class _VehicleAddPageState extends State<VehicleAddPage> {
  final formKey = GlobalKey<FormState>();
  final plate = TextEditingController();
  final brand = TextEditingController();
  final model = TextEditingController();
  final color = TextEditingController();
  final selectedMaterials = <String>{};
  bool saving = false;

  static const materialUrls = <String, String>{
    '行驶证主页':
        'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="640" height="400"%3E%3Crect width="100%25" height="100%25" fill="%23edfaf3"/%3E%3Ctext x="50%25" y="50%25" text-anchor="middle" fill="%23059269" font-size="38"%3EREGISTRATION A%3C/text%3E%3C/svg%3E',
    '行驶证副页':
        'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="640" height="400"%3E%3Crect width="100%25" height="100%25" fill="%23f2fbf6"/%3E%3Ctext x="50%25" y="50%25" text-anchor="middle" fill="%23059269" font-size="38"%3EREGISTRATION B%3C/text%3E%3C/svg%3E',
    '车辆正面照':
        'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="640" height="400"%3E%3Crect width="100%25" height="100%25" fill="%23fff7e8"/%3E%3Ctext x="50%25" y="50%25" text-anchor="middle" fill="%23d97706" font-size="38"%3EVEHICLE PHOTO%3C/text%3E%3C/svg%3E',
  };

  bool get materialsComplete => selectedMaterials.length == materialUrls.length;

  @override
  void dispose() {
    plate.dispose();
    brand.dispose();
    model.dispose();
    color.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    if (!formKey.currentState!.validate()) return;
    if (!materialsComplete) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请先选择全部 3 项认证材料')));
      return;
    }
    setState(() => saving = true);
    try {
      final service = VehicleService(context.read<AppSession>().api);
      final normalizedPlate = plate.text
          .trim()
          .replaceAll(' ', '')
          .toUpperCase();
      final eligibility = await service.authEligibility(normalizedPlate);
      if (eligibility['eligible'] != true) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                eligibility['reason']?.toString() ??
                    '该车辆已经认证通过，不能再次提交',
              ),
            ),
          );
        }
        return;
      }
      await service.submitAuth(
        plateNumber: normalizedPlate,
        vehicleBrand: brand.text.trim(),
        vehicleModel: model.text.trim(),
        vehicleColor: color.text.trim(),
        registrationLicenseImages: [
          materialUrls['行驶证主页']!,
          materialUrls['行驶证副页']!,
        ],
        vehicleImages: [materialUrls['车辆正面照']!],
      );
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('认证材料已提交，进入办理中')));
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('车辆认证')),
    body: Form(
      key: formKey,
      child: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          const Text(
            '提交车辆与证件材料',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 7),
          const Text(
            '资料仅用于平台审核，提交后可在认证进度中查看结果',
            style: TextStyle(color: AppColors.muted, height: 1.5),
          ),
          const SizedBox(height: 22),
          TlxCard(
            color: const Color(0xFFF8FAFD),
            child: Column(
              children: [
                _field(plate, '车牌号', LucideIcons.badge),
                const SizedBox(height: 13),
                _field(brand, '车辆品牌', LucideIcons.tags),
                const SizedBox(height: 13),
                _field(model, '车型名称', LucideIcons.car),
                const SizedBox(height: 13),
                _field(color, '车辆颜色', LucideIcons.palette),
              ],
            ),
          ),
          const SizedBox(height: 18),
          _materials('车辆行驶证', '主页与副页均需清晰完整', ['行驶证主页', '行驶证副页']),
          const SizedBox(height: 14),
          _materials('车辆照片', '需能看清车辆正面及车牌', ['车辆正面照']),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: saving ? null : submit,
            icon: saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Icon(LucideIcons.shieldCheck),
            label: Text(saving ? '提交中…' : '提交认证审核'),
          ),
          const SizedBox(height: 14),
          Center(
            child: Text(
              '已选择 ${selectedMaterials.length} / ${materialUrls.length} 项材料',
              style: TextStyle(
                color: materialsComplete ? AppColors.success : AppColors.muted,
                fontSize: 12,
              ),
            ),
          ),
        ],
      ),
    ),
  );

  Widget _materials(
    String title,
    String subtitle,
    List<String> items,
  ) => TlxCard(
    color: Colors.white,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 4),
        Text(
          subtitle,
          style: const TextStyle(color: AppColors.muted, fontSize: 12),
        ),
        const SizedBox(height: 14),
        Row(
          children: items.map((item) {
            final selected = selectedMaterials.contains(item);
            return Expanded(
              child: Padding(
                padding: EdgeInsets.only(right: item == items.last ? 0 : 10),
                child: InkWell(
                  borderRadius: BorderRadius.circular(16),
                  onTap: () => setState(
                    () => selected
                        ? selectedMaterials.remove(item)
                        : selectedMaterials.add(item),
                  ),
                  child: Container(
                    height: 104,
                    decoration: BoxDecoration(
                      color: selected
                          ? const Color(0xFFEEF3FF)
                          : const Color(0xFFF8FAFD),
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: selected
                            ? AppColors.primary
                            : const Color(0xFFDDE3ED),
                      ),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          selected
                              ? LucideIcons.circleCheck
                              : LucideIcons.imagePlus,
                          color: selected ? AppColors.primary : AppColors.muted,
                        ),
                        const SizedBox(height: 9),
                        Text(
                          item,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                            color: selected
                                ? AppColors.primary
                                : AppColors.secondaryText,
                          ),
                        ),
                        const SizedBox(height: 3),
                        Text(
                          selected ? '已选择' : '点击选择',
                          style: const TextStyle(
                            fontSize: 10,
                            color: AppColors.muted,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ],
    ),
  );

  TextFormField _field(
    TextEditingController controller,
    String label,
    IconData icon,
  ) => TextFormField(
    controller: controller,
    decoration: InputDecoration(labelText: label, prefixIcon: Icon(icon)),
    validator: (value) =>
        value == null || value.trim().isEmpty ? '请填写$label' : null,
  );
}
