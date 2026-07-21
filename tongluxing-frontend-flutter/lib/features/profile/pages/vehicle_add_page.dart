import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/api_client.dart';
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
  final picker = ImagePicker();
  final selectedMaterials = <String, _SelectedImage>{};
  bool saving = false;
  String savingLabel = '';

  static const materialBizTypes = <String, String>{
    '行驶证主页': 'VEHICLE_LICENSE_FRONT',
    '行驶证副页': 'VEHICLE_LICENSE_BACK',
    '车辆正面照': 'VEHICLE_PHOTO_FRONT',
    '车辆侧面照': 'VEHICLE_PHOTO_SIDE',
    '车辆后方照': 'VEHICLE_PHOTO_REAR',
  };

  static const requiredMaterials = <String>{
    '行驶证主页',
    '行驶证副页',
    '车辆正面照',
  };

  bool get materialsComplete =>
      requiredMaterials.every(selectedMaterials.containsKey);

  @override
  void dispose() {
    plate.dispose();
    brand.dispose();
    model.dispose();
    color.dispose();
    super.dispose();
  }

  Future<void> pickMaterial(String label) async {
    try {
      final file = await picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 88,
        maxWidth: 2200,
      );
      if (file == null) return;
      final bytes = await file.readAsBytes();
      if (bytes.isEmpty) throw const ApiException('无法读取所选图片');
      if (bytes.length > 10 * 1024 * 1024) {
        throw const ApiException('单张图片不能超过 10MB');
      }
      if (!mounted) return;
      setState(() {
        selectedMaterials[label] = _SelectedImage(
          fileName: file.name,
          bytes: bytes,
          contentType: file.mimeType ??
              StorageUploadService.imageContentType(file.name),
        );
      });
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$error')));
      }
    }
  }

  Future<void> submit() async {
    if (!formKey.currentState!.validate()) return;
    if (!materialsComplete) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请先从相册上传全部 3 项认证材料')));
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      saving = true;
      savingLabel = '检查车辆信息…';
    });
    try {
      final api = context.read<AppSession>().api;
      final vehicleService = VehicleService(api);
      final storageService = StorageUploadService(api);
      final normalizedPlate = plate.text
          .trim()
          .replaceAll(' ', '')
          .toUpperCase();
      final eligibility = await vehicleService.authEligibility(normalizedPlate);
      if (eligibility['eligible'] != true) {
        throw ApiException(
          eligibility['reason']?.toString() ?? '该车辆已经认证通过，不能再次提交',
        );
      }

      final uploadedKeys = <String, String>{};
      for (final entry in materialBizTypes.entries) {
        final image = selectedMaterials[entry.key];
        if (image == null) continue;
        if (!mounted) return;
        setState(() => savingLabel = '正在上传${entry.key}…');
        final uploaded = await storageService.upload(
          bizType: entry.value,
          bizId: normalizedPlate,
          fileName: image.fileName,
          bytes: image.bytes,
          contentType: image.contentType,
        );
        uploadedKeys[entry.key] = uploaded['objectKey'].toString();
      }

      if (mounted) setState(() => savingLabel = '正在提交认证…');
      await vehicleService.submitAuth(
        plateNumber: normalizedPlate,
        vehicleBrand: brand.text.trim(),
        vehicleModel: model.text.trim(),
        vehicleColor: color.text.trim(),
        registrationLicenseImages: [
          uploadedKeys['行驶证主页']!,
          uploadedKeys['行驶证副页']!,
        ],
        vehicleImages: [
          uploadedKeys['车辆正面照']!,
          if (uploadedKeys['车辆侧面照'] case final key?) key,
          if (uploadedKeys['车辆后方照'] case final key?) key,
        ],
      );
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('图片已上传，认证材料已提交')));
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) {
        setState(() {
          saving = false;
          savingLabel = '';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('车辆认证')),
    body: Form(
      key: formKey,
      child: ListView(
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        padding: const EdgeInsets.all(22),
        children: [
          const Text(
            '提交车辆与证件材料',
            style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 7),
          const Text(
            '从手机相册选择真实图片，App 会先向后端获取 MinIO 上传签名，再直传图片并提交审核。',
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
          _materials(
            '车辆照片',
            '正面照必传，侧面和后方照片可选，需能清楚识别车辆',
            ['车辆正面照', '车辆侧面照', '车辆后方照'],
          ),
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
            label: Text(saving ? savingLabel : '上传并提交认证'),
          ),
          const SizedBox(height: 14),
          Center(
            child: Text(
              '已选择 ${selectedMaterials.length} 项图片 · 必传材料 ${materialsComplete ? '已完整' : '未完整'}',
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
            final selected = selectedMaterials[item];
            return Expanded(
              child: Padding(
                padding: EdgeInsets.only(right: item == items.last ? 0 : 10),
                child: InkWell(
                  borderRadius: BorderRadius.circular(16),
                  onTap: saving ? null : () => pickMaterial(item),
                  child: Container(
                    height: 116,
                    clipBehavior: Clip.antiAlias,
                    decoration: BoxDecoration(
                      color: selected == null
                          ? const Color(0xFFF8FAFD)
                          : const Color(0xFFEEF3FF),
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: selected == null
                            ? const Color(0xFFDDE3ED)
                            : AppColors.primary,
                      ),
                    ),
                    child: selected == null
                        ? Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(
                                LucideIcons.imagePlus,
                                color: AppColors.muted,
                              ),
                              const SizedBox(height: 9),
                              Text(
                                item,
                                style: const TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                  color: AppColors.secondaryText,
                                ),
                              ),
                              const SizedBox(height: 3),
                              const Text(
                                '从相册选择',
                                style: TextStyle(
                                  fontSize: 10,
                                  color: AppColors.muted,
                                ),
                              ),
                            ],
                          )
                        : Stack(
                            fit: StackFit.expand,
                            children: [
                              Image.memory(selected.bytes, fit: BoxFit.cover),
                              const DecoratedBox(
                                decoration: BoxDecoration(
                                  gradient: LinearGradient(
                                    begin: Alignment.topCenter,
                                    end: Alignment.bottomCenter,
                                    colors: [Colors.transparent, Color(0xAA000000)],
                                  ),
                                ),
                              ),
                              Positioned(
                                left: 8,
                                right: 8,
                                bottom: 8,
                                child: Text(
                                  '$item · 点击更换',
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 11,
                                    fontWeight: FontWeight.w700,
                                  ),
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

class _SelectedImage {
  const _SelectedImage({
    required this.fileName,
    required this.bytes,
    required this.contentType,
  });

  final String fileName;
  final Uint8List bytes;
  final String contentType;
}
