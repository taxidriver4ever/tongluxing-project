import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../profile/widgets/user_avatar.dart';

class TripDiscoveryCard extends StatelessWidget {
  const TripDiscoveryCard({required this.trip, required this.onTap, super.key});

  final TripDiscoverModel trip;
  final VoidCallback onTap;

  String get route =>
      [trip.startName, ...trip.waypoints, trip.endName].join(' → ');

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(20),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(15, 13, 15, 13),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _Pill(
                  '顺路度 ${trip.matchScore}%',
                  const Color(0xFFDDEEFF),
                  AppColors.primaryDark,
                ),
                const Spacer(),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (trip.matchScore >= 90) ...[
                      const Icon(
                        LucideIcons.flame,
                        color: Color(0xFFFF6B35),
                        size: 14,
                      ),
                      const SizedBox(width: 3),
                    ],
                    Text(
                      trip.matchScore >= 90 ? '热门招募中' : '招募中',
                      style: const TextStyle(
                        color: AppColors.primary,
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 9),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        trip.title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppColors.text,
                          fontSize: 18,
                          height: 1.2,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        route,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppColors.secondaryText,
                          fontSize: 13,
                          height: 1.3,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          const Icon(
                            LucideIcons.calendarDays,
                            size: 14,
                            color: AppColors.secondaryText,
                          ),
                          const SizedBox(width: 5),
                          Expanded(
                            child: Text(
                              '${formatDiscoverDate(trip.departureTime)} 出发 · 预计${trip.estimatedDays}天',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: AppColors.secondaryText,
                                fontSize: 12,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                SizedBox(
                  width: 122,
                  height: 94,
                  child: _TripCover(
                    imageKey: trip.coverImageKey,
                    score: trip.matchScore,
                  ),
                ),
              ],
            ),
            if (trip.tags.isNotEmpty) ...[
              const SizedBox(height: 9),
              Wrap(
                spacing: 7,
                runSpacing: 6,
                children: trip.tags
                    .take(3)
                    .map(
                      (tag) => _Pill(
                        tag,
                        const Color(0xFFF0F4FA),
                        AppColors.secondaryText,
                      ),
                    )
                    .toList(),
              ),
            ],
            const SizedBox(height: 12),
            Row(
              children: [
                UserAvatar(
                  nickname: trip.owner.nickname,
                  avatarImageKey: trip.owner.avatarImageKey,
                  radius: 18,
                ),
                const SizedBox(width: 9),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              trip.owner.nickname,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: AppColors.text,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                          const SizedBox(width: 6),
                          _MiniTextTag(
                            trip.owner.levelCode.toUpperCase(),
                            const Color(0xFFDCEBFF),
                            AppColors.primaryDark,
                          ),
                          if (trip.owner.rating > 0) ...[
                            const SizedBox(width: 5),
                            const Icon(
                              LucideIcons.star,
                              size: 12,
                              color: AppColors.primary,
                            ),
                            const SizedBox(width: 2),
                            Text(
                              trip.owner.rating.toStringAsFixed(1),
                              style: const TextStyle(
                                color: AppColors.primary,
                                fontSize: 11,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 3),
                      Text(
                        '发起行程 · ${activeLabel(trip.owner.lastActiveAt)}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: AppColors.muted,
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          LucideIcons.users,
                          size: 14,
                          color: AppColors.secondaryText,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${trip.joinedVehicleCount}/${trip.maxVehicleCount}辆车 · ${trip.memberCount}人',
                          style: const TextStyle(
                            color: AppColors.secondaryText,
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 18,
                        vertical: 8,
                      ),
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [AppColors.primary, AppColors.primaryDark],
                        ),
                        borderRadius: BorderRadius.circular(99),
                      ),
                      child: const Text(
                        '查看详情',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

class _TripCover extends StatefulWidget {
  const _TripCover({required this.imageKey, required this.score});

  final String imageKey;
  final int score;

  @override
  State<_TripCover> createState() => _TripCoverState();
}

class _TripCoverState extends State<_TripCover> {
  Future<String>? future;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (future == null && widget.imageKey.trim().isNotEmpty) {
      future = StorageUploadService(
        context.read<AppSession>().api,
      ).downloadUrlByObjectKey(widget.imageKey);
    }
  }

  @override
  Widget build(BuildContext context) {
    final fallback = Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(14),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: widget.score >= 85
              ? const [Color(0xFF90CAF9), Color(0xFF266CC5)]
              : const [Color(0xFFB3D7F7), Color(0xFF5B91CC)],
        ),
      ),
      child: const Stack(
        children: [
          Positioned(
            right: -8,
            bottom: -5,
            child: Icon(
              LucideIcons.mountainSnow,
              size: 82,
              color: Color(0x66FFFFFF),
            ),
          ),
          Positioned(
            left: 9,
            bottom: 8,
            child: Row(
              children: [
                Icon(LucideIcons.images, size: 12, color: Colors.white),
                SizedBox(width: 4),
                Text(
                  '行程风景',
                  style: TextStyle(color: Colors.white, fontSize: 10),
                ),
              ],
            ),
          ),
        ],
      ),
    );
    if (future == null) return fallback;
    return ClipRRect(
      borderRadius: BorderRadius.circular(14),
      child: FutureBuilder<String>(
        future: future,
        builder: (context, snapshot) {
          final url = snapshot.data ?? '';
          if (url.isEmpty) return fallback;
          return Image.network(
            url,
            fit: BoxFit.cover,
            errorBuilder: (_, _, _) => fallback,
          );
        },
      ),
    );
  }
}

class RouteSketch extends StatelessWidget {
  const RouteSketch({
    required this.start,
    required this.end,
    this.waypoints = const [],
    this.routePolyline = '',
    super.key,
  });

  final String start;
  final String end;
  final List<String> waypoints;
  final String routePolyline;

  @override
  Widget build(BuildContext context) {
    final points = [start, ...waypoints, end];
    return Container(
      height: 190,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: const Color(0xFFF2F8FF),
        borderRadius: BorderRadius.circular(22),
      ),
      child: Stack(
        children: [
          Positioned.fill(
            child: CustomPaint(
              painter: _RoutePainter(
                points.length,
                _parsePolyline(routePolyline),
              ),
            ),
          ),
          ...List.generate(points.length, (index) {
            final usableWidth = MediaQuery.sizeOf(context).width - 104;
            final left = 4 + index * usableWidth / (points.length - 1).clamp(1, 5);
            final top = index.isEven ? 74.0 : 39.0;
            return Positioned(
              left: left.clamp(4, MediaQuery.sizeOf(context).width - 103),
              top: top,
              child: SizedBox(
                width: 62,
                child: Text(
                  points[index],
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
            );
          }),
          Positioned(
            right: 0,
            bottom: 0,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(99),
              ),
              child: const Row(
                children: [
                  Icon(LucideIcons.route, size: 13),
                  SizedBox(width: 5),
                  Text(
                    '查看完整路线',
                    style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  List<Offset> _parsePolyline(String value) {
    if (value.trim().isEmpty) return const [];
    return value
        .split(';')
        .map((part) => part.split(','))
        .where((part) => part.length >= 2)
        .map(
          (part) => Offset(
            double.tryParse(part[0].trim()) ?? 0,
            double.tryParse(part[1].trim()) ?? 0,
          ),
        )
        .where((point) => point.dx != 0 && point.dy != 0)
        .toList();
  }
}

class _RoutePainter extends CustomPainter {
  const _RoutePainter(this.count, this.polyline);

  final int count;
  final List<Offset> polyline;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = AppColors.primaryDark
      ..strokeWidth = 4
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    final path = Path();
    if (polyline.length >= 2) {
      final minX = polyline.map((e) => e.dx).reduce((a, b) => a < b ? a : b);
      final maxX = polyline.map((e) => e.dx).reduce((a, b) => a > b ? a : b);
      final minY = polyline.map((e) => e.dy).reduce((a, b) => a < b ? a : b);
      final maxY = polyline.map((e) => e.dy).reduce((a, b) => a > b ? a : b);
      Offset project(Offset point) => Offset(
        28 +
            (point.dx - minX) /
                (maxX - minX).abs().clamp(.000001, double.infinity) *
                (size.width - 56),
        126 -
            (point.dy - minY) /
                (maxY - minY).abs().clamp(.000001, double.infinity) *
                66,
      );
      final first = project(polyline.first);
      path.moveTo(first.dx, first.dy);
      for (final point in polyline.skip(1)) {
        final projected = project(point);
        path.lineTo(projected.dx, projected.dy);
      }
    } else {
      path.moveTo(28, 112);
      for (var i = 1; i < count; i++) {
        final x = 28 + i * (size.width - 56) / (count - 1).clamp(1, 5);
        final y = i.isEven ? 112.0 : 76.0;
        path.quadraticBezierTo(x - 22, y, x, y);
      }
    }
    canvas.drawPath(path, paint);
    final dot = Paint()..color = Colors.white;
    final border = Paint()
      ..color = AppColors.primaryDark
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3;
    for (var i = 0; i < count; i++) {
      final x = 28 + i * (size.width - 56) / (count - 1).clamp(1, 5);
      final y = i.isEven ? 112.0 : 76.0;
      canvas.drawCircle(Offset(x, y), 6, dot);
      canvas.drawCircle(Offset(x, y), 6, border);
    }
  }

  @override
  bool shouldRepaint(covariant _RoutePainter oldDelegate) =>
      oldDelegate.count != count || oldDelegate.polyline != polyline;
}

class _Pill extends StatelessWidget {
  const _Pill(this.text, this.background, this.foreground);

  final String text;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
    decoration: BoxDecoration(
      color: background,
      borderRadius: BorderRadius.circular(8),
    ),
    child: Text(
      text,
      style: TextStyle(
        color: foreground,
        fontSize: 11,
        fontWeight: FontWeight.w800,
      ),
    ),
  );
}

class _MiniTextTag extends StatelessWidget {
  const _MiniTextTag(this.text, this.background, this.foreground);

  final String text;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
    decoration: BoxDecoration(
      color: background,
      borderRadius: BorderRadius.circular(7),
    ),
    child: Text(
      text,
      style: TextStyle(
        color: foreground,
        fontSize: 10,
        fontWeight: FontWeight.w800,
      ),
    ),
  );
}

String formatDiscoverDate(String raw) {
  final value = DateTime.tryParse(raw);
  if (value == null) return raw.isEmpty ? '时间待定' : raw;
  return '${value.month.toString().padLeft(2, '0')}月${value.day.toString().padLeft(2, '0')}日';
}

String formatDiscoverTime(String raw) {
  final value = DateTime.tryParse(raw);
  if (value == null) return raw.isEmpty ? '时间待定' : raw;
  return '${formatDiscoverDate(raw)} ${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';
}

String distanceLabel(int meters) =>
    meters < 1000 ? '${meters}m' : '${(meters / 1000).toStringAsFixed(1)}km';

String activeLabel(String raw) {
  final value = DateTime.tryParse(raw);
  if (value == null) return '近期活跃';
  final minutes = DateTime.now().difference(value).inMinutes;
  if (minutes < 5) return '刚刚活跃';
  if (minutes < 60) return '$minutes分钟前活跃';
  if (minutes < 1440) return '${minutes ~/ 60}小时前活跃';
  return '${minutes ~/ 1440}天前活跃';
}
