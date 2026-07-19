import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../app/theme.dart';

class TlxCard extends StatelessWidget {
  const TlxCard({
    required this.child,
    super.key,
    this.padding = const EdgeInsets.all(18),
    this.color,
  });
  final Widget child;
  final EdgeInsets padding;
  final Color? color;
  @override
  Widget build(BuildContext context) => SizedBox(
    width: double.infinity,
    child: Material(
      color: color ?? Colors.white,
      borderRadius: BorderRadius.circular(22),
      clipBehavior: Clip.antiAlias,
      child: Padding(padding: padding, child: child),
    ),
  );
}

class PageHeading extends StatelessWidget {
  const PageHeading(this.title, {super.key, this.subtitle, this.action});
  final String title;
  final String? subtitle;
  final Widget? action;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(22, 24, 22, 18),
    child: Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.w800,
                  color: AppColors.text,
                ),
              ),
              if (subtitle != null) ...[
                const SizedBox(height: 6),
                Text(
                  subtitle!,
                  style: const TextStyle(color: AppColors.secondaryText),
                ),
              ],
            ],
          ),
        ),
        // ignore: use_null_aware_elements
        if (action != null) action!,
      ],
    ),
  );
}

class AsyncPanel extends StatelessWidget {
  const AsyncPanel({
    required this.loading,
    required this.error,
    required this.child,
    required this.onRetry,
    super.key,
  });
  final bool loading;
  final String? error;
  final Widget child;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                LucideIcons.cloudOff,
                size: 34,
                color: AppColors.muted,
              ),
              const SizedBox(height: 12),
              Text(error!, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              OutlinedButton(onPressed: onRetry, child: const Text('重新加载')),
            ],
          ),
        ),
      );
    }
    return child;
  }
}

String tripStatusLabel(String status) => switch (status) {
  'DRAFT' => '草稿',
  'PUBLISHED' => '招募中',
  'READY' => '待开启',
  'RUNNING' => '进行中',
  'FINISHED' => '已完成',
  'SETTLED' => '已结算',
  'COMPLETED' => '已完成',
  'CANCELLED' => '已取消',
  _ => status,
};
