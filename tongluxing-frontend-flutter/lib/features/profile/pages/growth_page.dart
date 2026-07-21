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
  List<Map<String, dynamic>> logs = const [];
  Map<String, dynamic> badges = const {};
  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final service = GrowthService(context.read<AppSession>().api);
      final values = await Future.wait([
        service.summary(),
        service.logs(),
        service.badges(),
      ]);
      data = values[0] as Map<String, dynamic>;
      logs = values[1] as List<Map<String, dynamic>>;
      badges = values[2] as Map<String, dynamic>;
      error = null;
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  @override
  Widget build(BuildContext context) {
    final exp = (data['totalPoints'] as num?)?.toInt() ?? 0;
    final next = (data['nextLevelPoints'] as num?)?.toInt() ?? 0;
    final level = data['levelCode']?.toString() ?? 'LV1';
    final progress = exp + next == 0 ? 1.0 : exp / (exp + next);
    final earnedCount = (badges['earned'] as List? ?? const []).length;
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
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              level,
                              style: TextStyle(
                                fontSize: 28,
                                fontWeight: FontWeight.w800,
                                color: AppColors.primary,
                              ),
                            ),
                            const Text(
                              '自驾成长旅程',
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
                  LinearProgressIndicator(
                    value: progress.clamp(0, 1),
                    minHeight: 8,
                    borderRadius: BorderRadius.all(Radius.circular(8)),
                    backgroundColor: AppColors.primarySoft,
                  ),
                  const SizedBox(height: 10),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Text(
                      next == 0 ? '已达到当前最高等级' : '距离下一等级还需要 $next 成长值',
                      style: TextStyle(color: AppColors.muted, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),
            TlxCard(
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
                      _Stat('$exp', '累计成长'),
                      _Stat('${logs.length}', '成长记录'),
                      _Stat('$earnedCount', '获得勋章'),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 18),
            TlxCard(
              color: Color(0xFFF8FAFD),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '成长记录',
                    style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
                  ),
                  if (logs.isEmpty)
                    const Padding(
                      padding: EdgeInsets.only(top: 16),
                      child: Text(
                        '暂无成长记录',
                        style: TextStyle(color: AppColors.muted),
                      ),
                    )
                  else
                    ...logs
                        .take(8)
                        .map(
                          (item) => _GrowthRow(
                            item['remark']?.toString().isNotEmpty == true
                                ? item['remark'].toString()
                                : item['bizType']?.toString() ?? '成长事件',
                            '${(item['pointDelta'] as num?)?.toInt().isNegative == true ? '' : '+'}${item['pointDelta'] ?? 0}',
                          ),
                        ),
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
