import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../../data/models/app_models.dart';
import '../widgets/route_map_view.dart';

class TripRouteMapPage extends StatelessWidget {
  const TripRouteMapPage({required this.detail, super.key});

  final TripPublicDetailModel detail;

  @override
  Widget build(BuildContext context) {
    final points = detail.routePoints;
    final stops = points.length < 2
        ? const <LocationSelection>[]
        : [
            LocationSelection(
              name: detail.trip.startName,
              address: '',
              latitude: points.first.latitude,
              longitude: points.first.longitude,
            ),
            LocationSelection(
              name: detail.trip.endName,
              address: '',
              latitude: points.last.latitude,
              longitude: points.last.longitude,
            ),
          ];
    return Scaffold(
      appBar: AppBar(title: const Text('完整路线')),
      body: Stack(
        children: [
          Positioned.fill(
            child: RouteMapView(
              polylinePoints: points,
              stops: stops,
              height: MediaQuery.sizeOf(context).height,
              interactive: true,
            ),
          ),
          Positioned(
            left: 14,
            right: 14,
            bottom: MediaQuery.paddingOf(context).bottom + 14,
            child: Material(
              color: Colors.white.withValues(alpha: .96),
              elevation: 8,
              borderRadius: BorderRadius.circular(18),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(15, 13, 15, 14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(
                          LucideIcons.route,
                          size: 19,
                          color: Color(0xFF1677E8),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            [
                              detail.trip.startName,
                              ...detail.trip.waypoints,
                              detail.trip.endName,
                            ].join(' → '),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontWeight: FontWeight.w800,
                              height: 1.35,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '${(detail.routeDistanceMeters / 1000).toStringAsFixed(1)} km'
                      ' · 预计 ${(detail.routeDurationSeconds / 60).ceil()} 分钟'
                      ' · 可双指缩放地图',
                      style: const TextStyle(
                        color: Color(0xFF667085),
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
