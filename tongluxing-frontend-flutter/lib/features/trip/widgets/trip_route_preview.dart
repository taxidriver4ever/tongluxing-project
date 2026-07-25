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
    super.key,
  });

  final List<LocationSelection> points;
  final TripDraftRouteModel? route;
  final bool mapOnly;
  final double mapHeight;
  final bool interactive;
  final VoidCallback? onMapInteraction;

  @override
  Widget build(BuildContext context) {
    if (points.length < 2) {
      return const SizedBox(
        height: 180,
        child: Center(child: Text('请选择起点和终点')),
      );
    }
    final polyline = route?.polylinePoints ?? points;
    final map = RouteMapView(
      polylinePoints: polyline,
      stops: points,
      height: mapHeight,
      interactive: interactive,
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
