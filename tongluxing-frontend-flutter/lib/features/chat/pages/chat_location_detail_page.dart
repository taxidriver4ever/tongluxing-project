import 'package:amap_map/amap_map.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:x_amap_base/x_amap_base.dart';

import '../../../app/theme.dart';

/// 聊天位置消息的地图详情页。经纬度只用于地图定位，不直接展示给用户。
class ChatLocationDetailPage extends StatefulWidget {
  const ChatLocationDetailPage({
    required this.latitude,
    required this.longitude,
    required this.title,
    this.address = '',
    super.key,
  });

  final double latitude;
  final double longitude;
  final String title;
  final String address;

  @override
  State<ChatLocationDetailPage> createState() => _ChatLocationDetailPageState();
}

class _ChatLocationDetailPageState extends State<ChatLocationDetailPage> {
  static const _channel = MethodChannel('com.tongluxing/permissions');
  static const _privacy = AMapPrivacyStatement(
    hasContains: true,
    hasShow: true,
    hasAgree: true,
  );

  AMapController? _controller;
  bool _checking = true;
  bool _nativeSupported = false;
  double _zoom = 16;

  LatLng get _target => LatLng(widget.latitude, widget.longitude);

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
          await _channel.invokeMethod<bool>('isAmapSupported') ?? false;
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

  void _changeZoom(double delta) {
    _zoom = (_zoom + delta).clamp(3, 20);
    _controller?.moveCamera(CameraUpdate.newLatLngZoom(_target, _zoom));
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F6F8),
      appBar: AppBar(centerTitle: true, title: const Text('位置详情')),
      body: Stack(
        children: [
          Positioned.fill(child: _buildMap(context)),
          Positioned(
            left: 16,
            right: 16,
            bottom: 18,
            child: SafeArea(
              top: false,
              child: Container(
                padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: const [
                    BoxShadow(
                      color: Color(0x22000000),
                      blurRadius: 18,
                      offset: Offset(0, 6),
                    ),
                  ],
                ),
                child: Row(
                  children: [
                    const Icon(
                      LucideIcons.mapPin,
                      color: AppColors.primary,
                      size: 24,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            widget.title.trim().isEmpty ? '共享位置' : widget.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                          if (widget.address.trim().isNotEmpty &&
                              widget.address.trim() != widget.title.trim()) ...[
                            const SizedBox(height: 3),
                            Text(
                              widget.address,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: AppColors.muted,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          Positioned(
            top: 16,
            right: 16,
            child: SafeArea(
              child: Material(
                color: Colors.white,
                elevation: 3,
                borderRadius: BorderRadius.circular(12),
                clipBehavior: Clip.antiAlias,
                child: Column(
                  children: [
                    IconButton(
                      tooltip: '放大',
                      onPressed: _zoom >= 20 ? null : () => _changeZoom(1),
                      icon: const Icon(LucideIcons.plus),
                    ),
                    const SizedBox(width: 32, child: Divider(height: 1)),
                    IconButton(
                      tooltip: '缩小',
                      onPressed: _zoom <= 3 ? null : () => _changeZoom(-1),
                      icon: const Icon(LucideIcons.minus),
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

  Widget _buildMap(BuildContext context) {
    if (_checking) {
      return const Center(child: CircularProgressIndicator());
    }
    if (!_nativeSupported) {
      return ColoredBox(
        color: const Color(0xFFE8F0F8),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                LucideIcons.mapPinned,
                size: 64,
                color: AppColors.primary,
              ),
              const SizedBox(height: 14),
              Text(
                widget.title.trim().isEmpty ? '共享位置' : widget.title,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                '请在支持高德地图的 Android 真机中查看和缩放地图',
                style: TextStyle(color: AppColors.muted),
              ),
            ],
          ),
        ),
      );
    }
    AMapInitializer.init(context);
    AMapInitializer.updatePrivacyAgree(_privacy);
    return AMapWidget(
      initialCameraPosition: CameraPosition(target: _target, zoom: _zoom),
      markers: {
        Marker(
          position: _target,
          infoWindow: InfoWindow(title: widget.title, snippet: widget.address),
        ),
      },
      rotateGesturesEnabled: true,
      scrollGesturesEnabled: true,
      tiltGesturesEnabled: true,
      zoomGesturesEnabled: true,
      touchPoiEnabled: true,
      onMapCreated: (controller) => _controller = controller,
      onCameraMove: (position) => _zoom = position.zoom,
    );
  }
}
