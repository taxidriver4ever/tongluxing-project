import 'dart:math' as math;

import 'package:amap_map/amap_map.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:x_amap_base/x_amap_base.dart';

import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';

/// 使用真实高德底图绘制完整路线；不支持原生地图的平台自动使用同一 polyline 进行静态预览。
class RouteMapView extends StatefulWidget {
  const RouteMapView({
    required this.polylinePoints,
    this.stops = const [],
    this.height = 220,
    this.showMyLocation = false,
    this.interactive = true,
    this.trafficEnabled = false,
    this.drawPolyline = true,
    this.onLocationChanged,
    this.onMapInteraction,
    super.key,
  });

  final List<LocationSelection> polylinePoints;
  final List<LocationSelection> stops;
  final double height;
  final bool showMyLocation;
  final bool interactive;
  final bool trafficEnabled;
  final bool drawPolyline;
  final ValueChanged<AMapLocation>? onLocationChanged;
  final VoidCallback? onMapInteraction;

  @override
  State<RouteMapView> createState() => _RouteMapViewState();
}

class _RouteMapViewState extends State<RouteMapView> {
  static const _permissionChannel = MethodChannel('com.tongluxing/permissions');
  static const _privacy = AMapPrivacyStatement(
    hasContains: true,
    hasShow: true,
    hasAgree: true,
  );

  bool _nativeSupported = false;
  bool _checking = true;

  bool get _nativeRequested =>
      !kIsWeb &&
      defaultTargetPlatform == TargetPlatform.android &&
      const bool.fromEnvironment('AMAP_NATIVE', defaultValue: true);

  @override
  void initState() {
    super.initState();
    _checkNativeSupport();
  }

  Future<void> _checkNativeSupport() async {
    if (!_nativeRequested) {
      if (mounted) setState(() => _checking = false);
      return;
    }
    try {
      final supported =
          await _permissionChannel.invokeMethod<bool>('isAmapSupported') ??
          false;
      if (!mounted) return;
      setState(() {
        _nativeSupported = supported;
        _checking = false;
      });
    } on PlatformException {
      if (mounted) setState(() => _checking = false);
    } on MissingPluginException {
      if (mounted) setState(() => _checking = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final route = _samplePolyline(widget.polylinePoints);
    if (route.isEmpty) {
      return SizedBox(
        height: widget.height,
        child: const Center(child: Text('暂无可展示的路线')),
      );
    }
    return SizedBox(
      height: widget.height,
      width: double.infinity,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(18),
        clipBehavior: Clip.hardEdge,
        child: ClipRect(
          child: Listener(
            behavior: HitTestBehavior.translucent,
            // Listener 只旁听移动/缩放事件，不参与手势竞争，
            // 因此不会阻断高德地图的拖动和双指缩放。
            onPointerMove: widget.interactive
                ? (_) => widget.onMapInteraction?.call()
                : null,
            onPointerSignal: widget.interactive
                ? (_) => widget.onMapInteraction?.call()
                : null,
            onPointerUp: widget.interactive
                ? (_) => widget.onMapInteraction?.call()
                : null,
            child: SizedBox.expand(
              child: _nativeSupported
                  ? _buildNativeMap(context, route)
                  : _buildFallback(route),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNativeMap(
    BuildContext context,
    List<LocationSelection> route,
  ) {
    AMapInitializer.init(context);
    AMapInitializer.updatePrivacyAgree(_privacy);
    final camera = _cameraFor(route);
    final stops = widget.stops.isEmpty
        ? <LocationSelection>[route.first, route.last]
        : widget.stops;
    return AMapWidget(
      initialCameraPosition: camera,
      trafficEnabled: widget.trafficEnabled,
      rotateGesturesEnabled: widget.interactive,
      scrollGesturesEnabled: widget.interactive,
      tiltGesturesEnabled: widget.interactive,
      zoomGesturesEnabled: widget.interactive,
      touchPoiEnabled: widget.interactive,
      myLocationStyleOptions: MyLocationStyleOptions(widget.showMyLocation),
      polylines: widget.drawPolyline && route.length >= 2
          ? {
              Polyline(
                points: route
                    .map((point) => LatLng(point.latitude, point.longitude))
                    .toList(growable: false),
                width: 8,
                color: AppColors.primary,
                capType: CapType.round,
                joinType: JoinType.round,
              ),
            }
          : const <Polyline>{},
      markers: _markers(stops),
      // 收起控件由外层 Listener 在真实点击、拖动或缩放时触发。
      // 不再使用 onCameraMove，避免地图惯性移动导致“展开”后立刻再次收起。
      onTap: (_) => widget.onMapInteraction?.call(),
      onLocationChanged: widget.onLocationChanged,
    );
  }

  Set<Marker> _markers(List<LocationSelection> stops) {
    if (stops.isEmpty) return const <Marker>{};
    final visible = <LocationSelection>[
      stops.first,
      ...stops.skip(1).take(stops.length > 2 ? stops.length - 2 : 0),
      if (stops.length > 1) stops.last,
    ];
    return visible.asMap().entries.map((entry) {
      final location = entry.value;
      final fallbackName = entry.key == 0
          ? '起点'
          : entry.key == visible.length - 1
          ? '终点'
          : '经停点${entry.key}';
      return Marker(
        position: LatLng(location.latitude, location.longitude),
        infoWindow: InfoWindow(
          title: location.name.trim().isEmpty ? fallbackName : location.name,
          snippet: location.address,
        ),
      );
    }).toSet();
  }

  Widget _buildFallback(List<LocationSelection> route) => InteractiveViewer(
    panEnabled: widget.interactive,
    scaleEnabled: widget.interactive,
    minScale: 1,
    maxScale: 5,
    boundaryMargin: const EdgeInsets.all(200),
    clipBehavior: Clip.none,
    child: Stack(
      fit: StackFit.expand,
      children: [
        ColoredBox(
          color: const Color(0xFFEFF4FA),
          child: CustomPaint(
            painter: const _MapBackdropPainter(),
            foregroundPainter: _PolylinePainter(
              route,
              widget.stops.isEmpty
                  ? <LocationSelection>[route.first, route.last]
                  : widget.stops,
              drawPolyline: widget.drawPolyline,
            ),
          ),
        ),
        if (_nativeRequested && !_checking)
          Positioned(
            right: 10,
            bottom: 9,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.92),
                borderRadius: BorderRadius.circular(99),
              ),
              child: const Text(
                '真机显示高德底图',
                style: TextStyle(fontSize: 10, color: AppColors.secondaryText),
              ),
            ),
          ),
      ],
    ),
  );

  CameraPosition _cameraFor(List<LocationSelection> points) {
    final minLat = points.map((e) => e.latitude).reduce(math.min);
    final maxLat = points.map((e) => e.latitude).reduce(math.max);
    final minLng = points.map((e) => e.longitude).reduce(math.min);
    final maxLng = points.map((e) => e.longitude).reduce(math.max);
    final center = LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2);
    final latitudeFactor = math.cos(center.latitude * math.pi / 180).abs();
    final span = math.max(maxLat - minLat, (maxLng - minLng) * latitudeFactor);
    final zoom = switch (span) {
      <= 0.002 => 16.0,
      <= 0.006 => 15.0,
      <= 0.015 => 13.5,
      <= 0.04 => 12.0,
      <= 0.10 => 10.5,
      <= 0.25 => 9.0,
      <= 0.60 => 7.5,
      <= 1.50 => 6.0,
      <= 4.00 => 4.8,
      _ => 3.6,
    };
    return CameraPosition(target: center, zoom: zoom);
  }

  List<LocationSelection> _samplePolyline(List<LocationSelection> points) {
    const maxPoints = 2500;
    if (points.length <= maxPoints) return points;
    final step = (points.length / maxPoints).ceil();
    return [
      points.first,
      for (var index = step; index < points.length - 1; index += step)
        points[index],
      points.last,
    ];
  }
}

class _MapBackdropPainter extends CustomPainter {
  const _MapBackdropPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final minor = Paint()
      ..color = const Color(0xFFDCE4ED)
      ..strokeWidth = 1.1;
    final major = Paint()
      ..color = const Color(0xFFD2DCE8)
      ..strokeWidth = 5
      ..strokeCap = StrokeCap.round;
    canvas.drawLine(
      Offset(-20, size.height * .28),
      Offset(size.width + 30, size.height * .55),
      major,
    );
    canvas.drawLine(
      Offset(size.width * .18, -20),
      Offset(size.width * .72, size.height + 20),
      major,
    );
    for (var index = 1; index < 7; index++) {
      final y = size.height * index / 7;
      canvas.drawLine(Offset(0, y), Offset(size.width, y - 24), minor);
    }
    for (var index = 1; index < 7; index++) {
      final x = size.width * index / 7;
      canvas.drawLine(Offset(x, 0), Offset(x - 20, size.height), minor);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _PolylinePainter extends CustomPainter {
  const _PolylinePainter(
    this.route,
    this.stops, {
    required this.drawPolyline,
  });

  final List<LocationSelection> route;
  final List<LocationSelection> stops;
  final bool drawPolyline;

  @override
  void paint(Canvas canvas, Size size) {
    final minLat = route.map((e) => e.latitude).reduce(math.min);
    final maxLat = route.map((e) => e.latitude).reduce(math.max);
    final minLng = route.map((e) => e.longitude).reduce(math.min);
    final maxLng = route.map((e) => e.longitude).reduce(math.max);
    const pad = 22.0;
    Offset project(LocationSelection point) => Offset(
      pad +
          (point.longitude - minLng) /
              math.max(maxLng - minLng, .000001) *
              (size.width - pad * 2),
      pad +
          (maxLat - point.latitude) /
              math.max(maxLat - minLat, .000001) *
              (size.height - pad * 2),
    );

    if (drawPolyline && route.length >= 2) {
      final path = Path()
        ..moveTo(project(route.first).dx, project(route.first).dy);
      for (final point in route.skip(1)) {
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
    }
    for (var index = 0; index < stops.length; index++) {
      final center = project(stops[index]);
      canvas.drawCircle(
        center,
        8,
        Paint()
          ..color = index == 0 || index == stops.length - 1
              ? AppColors.primary
              : const Color(0xFFFFA928),
      );
      canvas.drawCircle(center, 3.5, Paint()..color = Colors.white);
    }
  }

  @override
  bool shouldRepaint(covariant _PolylinePainter oldDelegate) =>
      oldDelegate.route != route ||
      oldDelegate.stops != stops ||
      oldDelegate.drawPolyline != drawPolyline;
}
