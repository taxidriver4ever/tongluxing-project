import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/app_services.dart';

class GrowthPage extends StatefulWidget {
  const GrowthPage({super.key});
  @override
  State<GrowthPage> createState() => _GrowthPageState();
}

class _GrowthPageState extends State<GrowthPage> {
  bool loading = true;
  String? error;
  Map<String, dynamic> data = {};
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      data = await GrowthService(context.read<AppSession>().api).summary();
      error = null;
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final exp = data['experience'] ?? data['growthValue'] ?? 1280;
    return Scaffold(
      appBar: AppBar(title: const Text('成长中心')),
      body: AsyncPanel(
        loading: loading,
        error: error,
        onRetry: load,
        child: ListView(
          padding: const EdgeInsets.all(22),
          children: [
            TlxCard(
              color: const Color(0xFFF8FAFD),
              child: Column(
                children: [
                  Row(
                    children: [
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Lv.5',
                              style: TextStyle(
                                fontSize: 28,
                                fontWeight: FontWeight.w800,
                                color: AppColors.primary,
                              ),
                            ),
                            Text(
                              '自驾达人',
                              style: TextStyle(color: AppColors.muted),
                            ),
                          ],
                        ),
                      ),
                      Text(
                        '$exp 成长值',
                        style: const TextStyle(fontWeight: FontWeight.w600),
                      ),
                    ],
                  ),
                  const SizedBox(height: 18),
                  const LinearProgressIndicator(
                    value: .65,
                    minHeight: 8,
                    borderRadius: BorderRadius.all(Radius.circular(8)),
                    backgroundColor: AppColors.primarySoft,
                  ),
                  const SizedBox(height: 10),
                  const Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      '距离下一等级还需要 220 成长值',
                      style: TextStyle(color: AppColors.muted, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),
            const TlxCard(
              color: Color(0xFFF8FAFD),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '成长数据',
                    style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                  ),
                  SizedBox(height: 18),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      _Stat('3200', '累计公里'),
                      _Stat('15', '完成行程'),
                      _Stat('8', '获得勋章'),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),
            const TlxCard(
              color: Color(0xFFF8FAFD),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '成长记录',
                    style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                  ),
                  _GrowthRow('完成自驾行程', '+100'),
                  _GrowthRow('累计驾驶50公里', '+20'),
                  _GrowthRow('邀请好友加入', '+50'),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  const _Stat(this.value, this.label);
  final String value, label;
  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(
        value,
        style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
      ),
      Text(label, style: const TextStyle(fontSize: 12, color: AppColors.muted)),
    ],
  );
}

class _GrowthRow extends StatelessWidget {
  const _GrowthRow(this.name, this.value);
  final String name, value;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 13),
    child: Row(
      children: [
        Expanded(
          child: Text(
            name,
            style: const TextStyle(color: AppColors.secondaryText),
          ),
        ),
        Text(
          value,
          style: const TextStyle(
            color: AppColors.primary,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    ),
  );
}
