import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../common/utils/display_text.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../profile/widgets/user_avatar.dart';
import '../widgets/trip_discovery_theme.dart';

class TripDiscoveryCard extends StatelessWidget {
  const TripDiscoveryCard({
    required this.trip,
    required this.onTap,
    this.showMatchScore = true,
    this.showPublishLocation = false,
    super.key,
  });

  final TripDiscoverModel trip;
  final VoidCallback onTap;
  final bool showMatchScore;
  final bool showPublishLocation;

  String get route =>
      [trip.startName, ...trip.waypoints, trip.endName].join(' → ');

  @override
  Widget build(BuildContext context) => Material(
    color: TripDiscoveryColors.card,
    borderRadius: BorderRadius.circular(14),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 11, 12, 11),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                if (showMatchScore && trip.matchScore != null)
                  _Pill(
                    '同路率 ${trip.matchScore}%',
                    TripDiscoveryColors.softBlue,
                    TripDiscoveryColors.primary,
                  ),
                const Spacer(),
                if (showMatchScore && (trip.matchScore ?? 0) >= 90) ...[
                  const Icon(
                    LucideIcons.flame,
                    color: TripDiscoveryColors.hot,
                    size: 13.5,
                  ),
                  const SizedBox(width: 4),
                ],
                Text(
                  trip.passengerDemand
                      ? '出行需求'
                      : const ['RUNNING', 'ONGOING'].contains(trip.status)
                      ? '进行中'
                      : showMatchScore && (trip.matchScore ?? 0) >= 90
                      ? '热门招募中'
                      : '招募中',
                  style: const TextStyle(
                    color: TripDiscoveryColors.primary,
                    fontSize: 11.5,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        trip.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: TripDiscoveryColors.text,
                          fontSize: 17,
                          height: 1.18,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 5),
                      Text(
                        route,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: TripDiscoveryColors.text,
                          fontSize: 12,
                          height: 1.25,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      if (showPublishLocation) ...[
                        const SizedBox(height: 6),
                        Row(
                          children: [
                            const Icon(
                              LucideIcons.mapPin,
                              size: 13,
                              color: TripDiscoveryColors.primary,
                            ),
                            const SizedBox(width: 4),
                            Expanded(
                              child: Text(
                                '发布地点：${trip.startName}',
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  color: TripDiscoveryColors.primary,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                      const SizedBox(height: 9),
                      Row(
                        children: [
                          const Icon(
                            LucideIcons.calendarDays,
                            size: 13.5,
                            color: TripDiscoveryColors.secondaryText,
                          ),
                          const SizedBox(width: 5),
                          Expanded(
                            child: Text(
                              '${formatDiscoverDate(trip.departureTime)} 出发 · 预计${trip.estimatedDays}天',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: TripDiscoveryColors.secondaryText,
                                fontSize: 11,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 9),
                SizedBox(
                  width: 102,
                  height: 80,
                  child: TripCoverImage(
                    imageKey: trip.coverImageKey,
                    score: trip.matchScore ?? 0,
                  ),
                ),
              ],
            ),
            if (trip.tags.isNotEmpty) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                runSpacing: 5,
                children: trip.tags
                    .take(3)
                    .map(
                      (tag) => _Pill(
                        tag,
                        TripDiscoveryColors.tagBlue,
                        const Color(0xFF294669),
                      ),
                    )
                    .toList(),
              ),
            ],
            const SizedBox(height: 10),
            Row(
              children: [
                UserAvatar(
                  nickname: trip.owner.nickname,
                  avatarImageKey: trip.owner.avatarImageKey,
                  radius: 18,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              compactDisplayName(trip.owner.nickname),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: TripDiscoveryColors.text,
                                fontSize: 14,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                          ),
                          const SizedBox(width: 6),
                          _MiniTextTag(
                            trip.owner.levelCode.toUpperCase(),
                            const Color(0xFFDCEBFF),
                            TripDiscoveryColors.primary,
                          ),
                        ],
                      ),
                      const SizedBox(height: 3),
                      Text(
                        '发起行程 · ${activeLabel(trip.owner.lastActiveAt)}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: TripDiscoveryColors.muted,
                          fontSize: 10.5,
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
                          size: 12.5,
                          color: TripDiscoveryColors.secondaryText,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          '${trip.memberCount}人',
                          style: const TextStyle(
                            color: TripDiscoveryColors.secondaryText,
                            fontSize: 10,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 5),
                    Container(
                      width: 92,
                      height: 32,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        gradient: TripDiscoveryColors.buttonGradient,
                        borderRadius: BorderRadius.circular(18),
                      ),
                      child: const Text(
                        '查看详情',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 11.5,
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

class TripCoverImage extends StatefulWidget {
  const TripCoverImage({
    required this.imageKey,
    required this.score,
    this.borderRadius = 11,
    super.key,
  });

  final String imageKey;
  final int score;
  final double borderRadius;

  @override
  State<TripCoverImage> createState() => _TripCoverImageState();
}

class _TripCoverImageState extends State<TripCoverImage> {
  Future<String>? future;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _resolve();
  }

  @override
  void didUpdateWidget(covariant TripCoverImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.imageKey != widget.imageKey) {
      future = null;
      _resolve();
    }
  }

  void _resolve() {
    if (future != null || widget.imageKey.trim().isEmpty) return;
    future = StorageUploadService(
      context.read<AppSession>().api,
    ).downloadUrlByObjectKey(widget.imageKey);
  }

  @override
  Widget build(BuildContext context) {
    final fallback = Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(widget.borderRadius),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: widget.score >= 85
              ? const [Color(0xFF9AD3FA), Color(0xFF246CC7)]
              : const [Color(0xFFBCDDF7), Color(0xFF5D91C7)],
        ),
      ),
      child: const Stack(
        children: [
          Positioned(
            right: -10,
            bottom: -9,
            child: Icon(
              LucideIcons.mountainSnow,
              size: 74,
              color: Color(0x68FFFFFF),
            ),
          ),
          Positioned(
            left: 7,
            bottom: 6,
            child: Row(
              children: [
                Icon(LucideIcons.image, size: 10, color: Colors.white),
                SizedBox(width: 4),
                Text(
                  '行程封面',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 8.5,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
    if (future == null) return fallback;
    return ClipRRect(
      borderRadius: BorderRadius.circular(widget.borderRadius),
      child: FutureBuilder<String>(
        future: future,
        builder: (context, snapshot) {
          final url = snapshot.data ?? '';
          if (url.isEmpty) return fallback;
          return Image.network(
            url,
            fit: BoxFit.cover,
            errorBuilder: (_, _, _) => fallback,
            loadingBuilder: (context, child, progress) =>
                progress == null ? child : fallback,
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
    this.height = 170,
    super.key,
  });

  final String start;
  final String end;
  final List<String> waypoints;
  final String routePolyline;
  final double height;

  @override
  Widget build(BuildContext context) {
    final allPoints = [start, ...waypoints, end];
    final points = allPoints.length <= 5
        ? allPoints
        : [
            allPoints.first,
            allPoints[1],
            allPoints[allPoints.length ~/ 2],
            allPoints[allPoints.length - 2],
            allPoints.last,
          ];
    return Container(
      height: height,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: const Color(0xFFF5FAFF),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0x2B93C9F5)),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final count = points.length;
          final span = (count - 1).clamp(1, 5);
          final routeWidth = constraints.maxWidth - 40;
          double xFor(int index) => 20 + index * routeWidth / span;
          double yFor(int index) => index.isEven
              ? constraints.maxHeight * .67
              : constraints.maxHeight * .40;
          return Stack(
            clipBehavior: Clip.none,
            children: [
              Positioned.fill(
                child: CustomPaint(
                  painter: _MapBackdropPainter(),
                  foregroundPainter: _RoutePainter(
                    count,
                    _parsePolyline(routePolyline),
                  ),
                ),
              ),
              ...List.generate(count, (index) {
                final width = 52.0;
                final left = (xFor(index) - width / 2)
                    .clamp(0.0, constraints.maxWidth - width)
                    .toDouble();
                final labelTop = (yFor(index) - 29)
                    .clamp(0.0, constraints.maxHeight - 54)
                    .toDouble();
                return Positioned(
                  left: left,
                  top: labelTop,
                  child: SizedBox(
                    width: width,
                    child: Text(
                      points[index],
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                      style: const TextStyle(
                        color: TripDiscoveryColors.text,
                        fontSize: 9.5,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                );
              }),
              Positioned(
                left: 4,
                bottom: 2,
                child: Icon(
                  LucideIcons.flag,
                  size: 19,
                  color: TripDiscoveryColors.hot,
                ),
              ),
              Positioned(
                right: 0,
                bottom: 0,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xF4FFFFFF),
                    borderRadius: BorderRadius.circular(99),
                  ),
                  child: const Row(
                    children: [
                      Icon(
                        LucideIcons.route,
                        size: 10.5,
                        color: TripDiscoveryColors.secondaryText,
                      ),
                      SizedBox(width: 4),
                      Text(
                        '查看路线概览',
                        style: TextStyle(
                          color: TripDiscoveryColors.secondaryText,
                          fontSize: 9,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
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

class _MapBackdropPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final road = Paint()
      ..color = const Color(0x2397BDE1)
      ..strokeWidth = 1
      ..style = PaintingStyle.stroke;
    for (var i = 0; i < 5; i++) {
      final y = 8 + i * size.height / 5;
      final path = Path()
        ..moveTo(-12, y)
        ..quadraticBezierTo(size.width * .28, y - 12, size.width * .55, y + 3)
        ..quadraticBezierTo(size.width * .78, y + 12, size.width + 14, y - 5);
      canvas.drawPath(path, road);
    }
    for (var i = 0; i < 4; i++) {
      final x = 10 + i * size.width / 4;
      final path = Path()
        ..moveTo(x, -8)
        ..quadraticBezierTo(x + 18, size.height * .35, x - 6, size.height * .7)
        ..quadraticBezierTo(x - 18, size.height * .85, x + 8, size.height + 8);
      canvas.drawPath(path, road);
    }
  }

  @override
  bool shouldRepaint(covariant _MapBackdropPainter oldDelegate) => false;
}

class _RoutePainter extends CustomPainter {
  const _RoutePainter(this.count, this.polyline);

  final int count;
  final List<Offset> polyline;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = TripDiscoveryColors.primary
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
        size.height * .72 -
            (point.dy - minY) /
                (maxY - minY).abs().clamp(.000001, double.infinity) *
                (size.height * .35),
      );
      final first = project(polyline.first);
      path.moveTo(first.dx, first.dy);
      for (final point in polyline.skip(1)) {
        final projected = project(point);
        path.lineTo(projected.dx, projected.dy);
      }
    } else {
      path.moveTo(20, size.height * .67);
      for (var i = 1; i < count; i++) {
        final x = 20 + i * (size.width - 40) / (count - 1).clamp(1, 5);
        final y = i.isEven ? size.height * .67 : size.height * .40;
        path.quadraticBezierTo(x - 20, y, x, y);
      }
    }
    canvas.drawPath(path, paint);
    final dot = Paint()..color = Colors.white;
    final border = Paint()
      ..color = TripDiscoveryColors.primary
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3;
    for (var i = 0; i < count; i++) {
      final x = 20 + i * (size.width - 40) / (count - 1).clamp(1, 5);
      final y = i.isEven ? size.height * .67 : size.height * .40;
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
        fontSize: 10,
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
        fontSize: 9.5,
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
