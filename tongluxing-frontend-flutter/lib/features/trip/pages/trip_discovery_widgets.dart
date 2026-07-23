import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
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
    borderRadius: BorderRadius.circular(22),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(22),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _Pill(
                  '推荐度 ${trip.matchScore}%',
                  AppColors.primarySoft,
                  AppColors.primaryDark,
                ),
                const Spacer(),
                _Pill(
                  trip.status == 'RECRUITING' ? '招募中' : '公开招募',
                  const Color(0xFFFFF2E8),
                  const Color(0xFFF97316),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              trip.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 7),
            Text(
              route,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.w600, height: 1.35),
            ),
            const SizedBox(height: 11),
            Row(
              children: [
                const Icon(
                  LucideIcons.calendarDays,
                  size: 16,
                  color: AppColors.primary,
                ),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    '${formatDiscoverTime(trip.departureTime)} · 预计${trip.estimatedDays}天',
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 13,
                      color: AppColors.secondaryText,
                    ),
                  ),
                ),
                if (trip.distanceMeters != null)
                  Text(
                    distanceLabel(trip.distanceMeters!),
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.muted,
                    ),
                  ),
              ],
            ),
            if (trip.tags.isNotEmpty) ...[
              const SizedBox(height: 10),
              Wrap(
                spacing: 7,
                runSpacing: 7,
                children: trip.tags
                    .take(3)
                    .map(
                      (tag) => _Pill(
                        tag,
                        const Color(0xFFF1F5FA),
                        AppColors.secondaryText,
                      ),
                    )
                    .toList(),
              ),
            ],
            const SizedBox(height: 14),
            Row(
              children: [
                UserAvatar(
                  nickname: trip.owner.nickname,
                  avatarImageKey: trip.owner.avatarImageKey,
                  radius: 19,
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
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ),
                          const SizedBox(width: 6),
                          Text(
                            trip.owner.levelCode,
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppColors.primary,
                            ),
                          ),
                          if (trip.owner.rating > 0) ...[
                            const SizedBox(width: 5),
                            const Icon(
                              LucideIcons.star,
                              size: 12,
                              color: Color(0xFFF59E0B),
                            ),
                            Text(
                              trip.owner.rating.toStringAsFixed(1),
                              style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.primaryDark,
                              ),
                            ),
                          ],
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${trip.owner.totalTripCount}次行程 · ${activeLabel(trip.owner.lastActiveAt)}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 11,
                          color: AppColors.muted,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      '${trip.joinedVehicleCount}/${trip.maxVehicleCount}辆 · ${trip.memberCount}人',
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.secondaryText,
                      ),
                    ),
                    const SizedBox(height: 5),
                    const Text(
                      '查看详情',
                      style: TextStyle(
                        fontWeight: FontWeight.w700,
                        color: AppColors.primary,
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
      height: 172,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFFF1F8FF), Color(0xFFE7F2FF)],
        ),
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
            final left =
                8 +
                index *
                    (MediaQuery.sizeOf(context).width - 94) /
                    (points.length - 1).clamp(1, 5);
            final top = index.isEven ? 73.0 : 39.0;
            return Positioned(
              left: left.clamp(8, MediaQuery.sizeOf(context).width - 95),
              top: top,
              child: SizedBox(
                width: 58,
                child: Text(
                  points[index],
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            );
          }),
          const Positioned(
            left: 0,
            bottom: 0,
            child: Text(
              '行程参考路线',
              style: TextStyle(fontSize: 11, color: AppColors.muted),
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
      ..color = AppColors.primary
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
        118 -
            (point.dy - minY) /
                (maxY - minY).abs().clamp(.000001, double.infinity) *
                62,
      );
      final first = project(polyline.first);
      path.moveTo(first.dx, first.dy);
      for (final point in polyline.skip(1)) {
        final projected = project(point);
        path.lineTo(projected.dx, projected.dy);
      }
    } else {
      path.moveTo(28, 105);
      for (var i = 1; i < count; i++) {
        final x = 28 + i * (size.width - 56) / (count - 1).clamp(1, 5);
        final y = i.isEven ? 105.0 : 70.0;
        path.quadraticBezierTo(x - 22, y, x, y);
      }
    }
    canvas.drawPath(path, paint);
    final dot = Paint()..color = Colors.white;
    final border = Paint()
      ..color = AppColors.primary
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3;
    for (var i = 0; i < count; i++) {
      final x = 28 + i * (size.width - 56) / (count - 1).clamp(1, 5);
      final y = i.isEven ? 105.0 : 70.0;
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
      borderRadius: BorderRadius.circular(9),
    ),
    child: Text(
      text,
      style: TextStyle(
        fontSize: 11,
        color: foreground,
        fontWeight: FontWeight.w700,
      ),
    ),
  );
}

String formatDiscoverTime(String raw) {
  final value = DateTime.tryParse(raw);
  if (value == null) return raw.isEmpty ? '时间待定' : raw;
  return '${value.month.toString().padLeft(2, '0')}月${value.day.toString().padLeft(2, '0')}日 ${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';
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
