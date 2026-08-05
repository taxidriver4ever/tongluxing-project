import 'package:flutter/material.dart';

import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import 'route_map_view.dart';

class TripRoutePreview extends StatelessWidget {
  const TripRoutePreview({
    required this.points,
    this.route,
    this.mapOnly = false,
    this.mapHeight = 190,
    this.interactive = true,
    this.onMapInteraction,
    this.performanceLabel = 'trip_route_preview',
    this.simplifiedOverview = true,
    super.key,
  });

  final List<LocationSelection> points;
  final TripDraftRouteModel? route;
  final bool mapOnly;
  final double mapHeight;
  final bool interactive;
  final VoidCallback? onMapInteraction;
  final String performanceLabel;
  final bool simplifiedOverview;

  @override
  Widget build(BuildContext context) {
    if (points.length < 2) {
      return const SizedBox(
        height: 180,
        child: Center(child: Text('请选择起点和终点')),
      );
    }
    final polyline = simplifiedOverview
        ? buildSmoothOverviewRoute(points)
        : route?.polylinePoints ?? points;
    final map = RouteMapView(
      polylinePoints: polyline,
      stops: points,
      height: mapHeight,
      interactive: interactive,
      performanceLabel: performanceLabel,
      onMapInteraction: onMapInteraction,
    );
    if (mapOnly) return map;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFF3F7FF),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: const Color(0xFFD8E5FF)),
      ),
      child: Column(
        children: [
          map,
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
              '${((route!.distanceMeters ?? 0) / 1000).toStringAsFixed(1)} km · '
              '约 ${route!.durationMinutes ?? 0} 分钟 · ${route!.providerType ?? ''}',
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

/// 仅供列表和详情概览展示的 Catmull-Rom 平滑曲线。
/// 输入只有起点、途经点和终点，不读取也不改写真实导航 routePolyline。
List<LocationSelection> buildSmoothOverviewRoute(
  List<LocationSelection> nodes, {
  int samplesPerSegment = 12,
}) {
  if (nodes.length < 2) return nodes;
  final result = <LocationSelection>[];
  for (var segment = 0; segment < nodes.length - 1; segment++) {
    final p0 = nodes[segment == 0 ? segment : segment - 1];
    final p1 = nodes[segment];
    final p2 = nodes[segment + 1];
    final p3 = nodes[segment + 2 < nodes.length ? segment + 2 : segment + 1];
    for (var sample = 0; sample < samplesPerSegment; sample++) {
      final t = sample / samplesPerSegment;
      final t2 = t * t;
      final t3 = t2 * t;
      double value(double a, double b, double c, double d) =>
          .5 *
          ((2 * b) +
              (-a + c) * t +
              (2 * a - 5 * b + 4 * c - d) * t2 +
              (-a + 3 * b - 3 * c + d) * t3);
      result.add(
        LocationSelection(
          name: p1.name,
          address: p1.address,
          latitude: value(p0.latitude, p1.latitude, p2.latitude, p3.latitude),
          longitude: value(
            p0.longitude,
            p1.longitude,
            p2.longitude,
            p3.longitude,
          ),
        ),
      );
    }
  }
  result.add(nodes.last);
  return List.unmodifiable(result);
}
