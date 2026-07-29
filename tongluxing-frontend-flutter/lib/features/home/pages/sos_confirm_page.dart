import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import '../../../app/theme.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';

class SosConfirmPage extends StatefulWidget {
  const SosConfirmPage({
    super.key,
    required this.api,
    required this.latitude,
    required this.longitude,
    required this.address,
  });
  final ApiClient api;
  final double latitude;
  final double longitude;
  final String address;
  @override
  State<SosConfirmPage> createState() => _SosConfirmPageState();
}

class _SosConfirmPageState extends State<SosConfirmPage> {
  final message = TextEditingController();
  bool sending = false;
  @override
  void dispose() {
    message.dispose();
    super.dispose();
  }

  Future<void> submit() async {
    setState(() => sending = true);
    try {
      final result = await SosService(widget.api).create(
        requestId: 'android-${DateTime.now().microsecondsSinceEpoch}',
        latitude: widget.latitude,
        longitude: widget.longitude,
        address: widget.address,
        message: message.text.trim().isEmpty
            ? '用户从地图页发起紧急求助'
            : message.text.trim(),
      );
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (context) => AlertDialog(
          icon: const Icon(
            LucideIcons.circleCheck,
            color: AppColors.success,
            size: 38,
          ),
          title: const Text('已通知平台运营人员'),
          content: Text(
            'SOS 编号：${result['id']}\n平台已收到你的位置。当前版本不会自动拨打 110，如有生命危险请立即自行拨打报警电话。',
          ),
          actions: [
            FilledButton(
              onPressed: () {
                Navigator.pop(context);
                Navigator.pop(context);
              },
              child: const Text('知道了'),
            ),
          ],
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(e.toString())));
      }
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFFFFF8F7),
    appBar: AppBar(
      title: const Text('确认发送 SOS'),
      backgroundColor: const Color(0xFFFFF8F7),
    ),
    body: SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(22),
        children: [
          Container(
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(color: const Color(0xFFFFD7D2)),
            ),
            child: const Column(
              children: [
                CircleAvatar(
                  radius: 34,
                  backgroundColor: Color(0xFFFFE8E5),
                  child: Icon(
                    LucideIcons.siren,
                    color: AppColors.danger,
                    size: 34,
                  ),
                ),
                SizedBox(height: 16),
                Text(
                  '是否在当前地点发送 SOS？',
                  style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800),
                ),
                SizedBox(height: 8),
                Text(
                  '提交后，平台运营后台会立即看到求助位置并进行受理。',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: AppColors.secondaryText),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          ListTile(
            tileColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(18),
            ),
            leading: const Icon(LucideIcons.mapPin, color: AppColors.danger),
            title: Text(widget.address),
            subtitle: Text(
              '${widget.latitude.toStringAsFixed(6)}, ${widget.longitude.toStringAsFixed(6)}',
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: message,
            maxLength: 500,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: '补充求助情况（选填）',
              hintText: '例如：车辆故障、人员受伤、需要道路救援',
              alignLabelWithHint: true,
              contentPadding: EdgeInsets.fromLTRB(16, 18, 16, 14),
            ),
          ),
          const ListTile(
            contentPadding: EdgeInsets.zero,
            leading: Icon(LucideIcons.triangleAlert, color: Color(0xFFE58B00)),
            title: Text('报警功能当前为 Mock'),
            subtitle: Text('本次会真实上报平台，但不会自动联系警方；紧急危险请立即拨打 110/120。'),
          ),
          const SizedBox(height: 18),
          FilledButton.icon(
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.danger,
              minimumSize: const Size.fromHeight(52),
            ),
            onPressed: sending ? null : submit,
            icon: sending
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(LucideIcons.siren),
            label: Text(sending ? '正在发送…' : '确认发送 SOS'),
          ),
          TextButton(
            onPressed: sending ? null : () => Navigator.pop(context),
            child: const Text('暂不发送'),
          ),
        ],
      ),
    ),
  );
}
