import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/services/app_services.dart';

class CustomerSupportPage extends StatefulWidget {
  const CustomerSupportPage({super.key});
  @override
  State<CustomerSupportPage> createState() => _CustomerSupportPageState();
}

class _CustomerSupportPageState extends State<CustomerSupportPage> {
  bool loading = true;
  String? error;
  List<Map<String, dynamic>> tickets = const [];
  List<Map<String, dynamic>> notices = const [];

  CustomerSupportService get service =>
      CustomerSupportService(context.read<AppSession>().api);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      final values = await Future.wait([
        service.tickets(),
        service.notifications(),
      ]);
      tickets = values[0];
      notices = values[1];
      error = null;
    } catch (e) {
      error = '$e';
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> create(bool complaint) async {
    final title = TextEditingController();
    final content = TextEditingController();
    final submitted = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => AnimatedPadding(
        duration: const Duration(milliseconds: 180),
        padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(ctx).bottom),
        child: SafeArea(
          top: false,
          child: Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(ctx).height * .78,
            ),
            decoration: const BoxDecoration(
              color: Color(0xFFF8FAFF),
              borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
            ),
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Center(
                    child: Container(
                      width: 42,
                      height: 4,
                      decoration: BoxDecoration(
                        color: const Color(0xFFD4D9E2),
                        borderRadius: BorderRadius.circular(99),
                      ),
                    ),
                  ),
                  const SizedBox(height: 18),
                  Text(
                    complaint ? '提交投诉' : '联系客服',
                    style: const TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 18),
                  TextField(
                    controller: title,
                    textInputAction: TextInputAction.next,
                    decoration: const InputDecoration(
                      labelText: '问题标题',
                      contentPadding: EdgeInsets.fromLTRB(16, 18, 16, 16),
                    ),
                  ),
                  const SizedBox(height: 14),
                  TextField(
                    controller: content,
                    minLines: 4,
                    maxLines: 7,
                    textAlignVertical: TextAlignVertical.top,
                    decoration: const InputDecoration(
                      labelText: '问题描述',
                      alignLabelWithHint: true,
                      floatingLabelBehavior: FloatingLabelBehavior.always,
                      contentPadding: EdgeInsets.fromLTRB(16, 24, 16, 16),
                    ),
                  ),
                  const SizedBox(height: 18),
                  SizedBox(
                    width: double.infinity,
                    height: 52,
                    child: FilledButton(
                      onPressed: () => Navigator.pop(ctx, true),
                      child: const Text('提交工单'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    if (submitted != true ||
        title.text.trim().isEmpty ||
        content.text.trim().isEmpty) {
      return;
    }
    await service.createTicket(
      title: title.text.trim(),
      content: content.text.trim(),
      complaint: complaint,
    );
    if (mounted) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('工单已提交')));
    }
    await load();
  }

  String status(String value) => switch (value) {
    'OPEN' => '待领取',
    'PROCESSING' => '处理中',
    'CLOSED' => '已完成',
    _ => value,
  };

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('客服与投诉')),
    body: AsyncPanel(
      loading: loading,
      error: error,
      onRetry: load,
      child: RefreshIndicator(
        onRefresh: load,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 36),
          children: [
            Row(
              children: [
                Expanded(
                  child: SizedBox(
                    height: 50,
                    child: FilledButton.icon(
                      style: FilledButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                      ),
                      onPressed: () => create(false),
                      icon: const Icon(LucideIcons.headphones, size: 19),
                      label: const Text('联系客服'),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: SizedBox(
                    height: 50,
                    child: OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        side: const BorderSide(color: AppColors.primary),
                        foregroundColor: AppColors.primary,
                      ),
                      onPressed: () => create(true),
                      icon: const Icon(LucideIcons.triangleAlert, size: 19),
                      label: const Text('提交投诉'),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 22),
            const Text(
              '我的工单',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 10),
            if (tickets.isEmpty)
              const TlxCard(
                child: Text('暂无工单', style: TextStyle(color: AppColors.muted)),
              ),
            ...tickets.map(
              (ticket) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: TlxCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              ticket['title']?.toString() ?? '客服工单',
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                          Text(
                            status(ticket['ticketStatus']?.toString() ?? ''),
                            style: const TextStyle(color: AppColors.primary),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        ticket['content']?.toString() ?? '',
                        style: const TextStyle(color: AppColors.secondaryText),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '消息 ${((ticket['messages'] as List?) ?? const []).length} 条 · #${ticket['id']}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.muted,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 14),
            const Text(
              '处理通知',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 10),
            if (notices.isEmpty)
              const TlxCard(
                child: Text('暂无客服通知', style: TextStyle(color: AppColors.muted)),
              ),
            ...notices.map(
              (notice) => ListTile(
                contentPadding: const EdgeInsets.symmetric(horizontal: 4),
                leading: CircleAvatar(
                  backgroundColor: AppColors.primarySoft,
                  child: Icon(
                    notice['readStatus'] == 'READ'
                        ? LucideIcons.mailOpen
                        : LucideIcons.mail,
                    color: AppColors.primary,
                  ),
                ),
                title: Text(notice['title']?.toString() ?? '客服通知'),
                subtitle: Text(notice['content']?.toString() ?? ''),
                onTap: notice['readStatus'] == 'READ'
                    ? null
                    : () async {
                        await service.markRead(notice['id']!);
                        await load();
                      },
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
