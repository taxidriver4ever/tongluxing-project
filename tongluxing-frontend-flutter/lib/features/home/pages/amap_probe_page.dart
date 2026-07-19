import 'package:amap_map/amap_map.dart';
import 'package:flutter/material.dart';
import 'package:x_amap_base/x_amap_base.dart';

class AmapProbePage extends StatefulWidget {
  const AmapProbePage({super.key});

  @override
  State<AmapProbePage> createState() => _AmapProbePageState();
}

class _AmapProbePageState extends State<AmapProbePage> {
  String _status = '正在初始化高德地图…';

  static const _privacyStatement = AMapPrivacyStatement(
    hasContains: true,
    hasShow: true,
    hasAgree: true,
  );

  @override
  Widget build(BuildContext context) {
    AMapInitializer.init(context);
    AMapInitializer.updatePrivacyAgree(_privacyStatement);

    return Scaffold(
      appBar: AppBar(title: const Text('高德地图接入诊断')),
      body: Stack(
        children: [
          AMapWidget(onMapCreated: _onMapCreated),
          Positioned(
            left: 16,
            right: 16,
            bottom: 24,
            child: Material(
              elevation: 4,
              borderRadius: BorderRadius.circular(14),
              color: Colors.white,
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Text(_status),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _onMapCreated(AMapController controller) async {
    try {
      final approvalNumber = await controller.getMapContentApprovalNumber();
      if (!mounted) return;
      setState(() {
        _status = approvalNumber.isEmpty
            ? '地图已创建，但未返回审图号，请检查 Key 的包名与 SHA1。'
            : '地图加载成功 · 审图号：$approvalNumber';
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _status = '地图创建后校验失败：$error');
    }
  }
}
