import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';

class TripRoutePreview extends StatelessWidget {
  const TripRoutePreview({required this.points, this.route, super.key});
  final List<LocationSelection> points;
  final TripDraftRouteModel? route;

  @override
  Widget build(BuildContext context) {
    if (points.length < 2) {
      return const SizedBox(
        height: 180,
        child: Center(child: Text('请选择起点和终点')),
      );
    }
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFF3F7FF),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: const Color(0xFFD8E5FF)),
      ),
      child: Column(
        children: [
          SizedBox(
            height: 190,
            child: CustomPaint(painter: _RoutePainter(points)),
          ),
          const SizedBox(height: 10),
          ...points.asMap().entries.map(
            (entry) => Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Row(
                children: [
                  Container(
                    width: 24,
                    height: 24,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: entry.key == 0 || entry.key == points.length - 1
                          ? AppColors.primary
                          : const Color(0xFFFFA928),
                      shape: BoxShape.circle,
                    ),
                    child: Text(
                      '${entry.key + 1}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      entry.value.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (route != null) ...[
            const SizedBox(height: 10),
            Text(
              '${((route!.distanceMeters ?? 0) / 1000).toStringAsFixed(1)} km · 约 ${route!.durationMinutes ?? 0} 分钟 · ${route!.providerType ?? ''}',
              style: const TextStyle(
                color: AppColors.secondaryText,
                fontSize: 12,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _RoutePainter extends CustomPainter {
  const _RoutePainter(this.points);
  final List<LocationSelection> points;
  @override
  void paint(Canvas canvas, Size size) {
    final minLat = points.map((e) => e.latitude).reduce(math.min);
    final maxLat = points.map((e) => e.latitude).reduce(math.max);
    final minLng = points.map((e) => e.longitude).reduce(math.min);
    final maxLng = points.map((e) => e.longitude).reduce(math.max);
    const pad = 24.0;
    Offset project(LocationSelection p) {
      final x =
          pad +
          (p.longitude - minLng) /
              math.max(maxLng - minLng, .000001) *
              (size.width - pad * 2);
      final y =
          pad +
          (maxLat - p.latitude) /
              math.max(maxLat - minLat, .000001) *
              (size.height - pad * 2);
      return Offset(x, y);
    }

    final path = Path()
      ..moveTo(project(points.first).dx, project(points.first).dy);
    for (final point in points.skip(1)) {
      path.lineTo(project(point).dx, project(point).dy);
    }
    canvas.drawPath(
      path,
      Paint()
        ..color = AppColors.primary
        ..strokeWidth = 6
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
    for (var i = 0; i < points.length; i++) {
      final center = project(points[i]);
      canvas.drawCircle(
        center,
        9,
        Paint()
          ..color = i == 0 || i == points.length - 1
              ? AppColors.primary
              : const Color(0xFFFFA928),
      );
      canvas.drawCircle(center, 4, Paint()..color = Colors.white);
    }
  }

  @override
  bool shouldRepaint(covariant _RoutePainter oldDelegate) =>
      oldDelegate.points != points;
}
