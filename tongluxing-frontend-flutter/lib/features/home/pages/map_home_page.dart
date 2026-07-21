import 'package:amap_map/amap_map.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import 'package:x_amap_base/x_amap_base.dart';

import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../trip/pages/trip_create_page.dart';
import 'search_location_page.dart';
import 'sos_confirm_page.dart';

class MapHomePage extends StatefulWidget {
  const MapHomePage({super.key});

  static bool get nativeAmap =>
      !kIsWeb &&
      defaultTargetPlatform == TargetPlatform.android &&
      const bool.fromEnvironment('AMAP_NATIVE', defaultValue: true);

  @override
  State<MapHomePage> createState() => _MapHomePageState();
}

class _MapHomePageState extends State<MapHomePage> {
  static const _permissionChannel = MethodChannel('com.tongluxing/permissions');

  AMapController? _mapController;
  LatLng? _lastLocation;
  bool _locationEnabled = false;
  bool _centerOnNextLocation = false;
  bool _trafficEnabled = false;
  bool _amapRuntimeSupported = false;
  bool _checkingAmapSupport = true;
  LocationSelection? _selectedLocation;

  @override
  void initState() {
    super.initState();
    _checkAmapSupport();
  }

  Future<void> _checkAmapSupport() async {
    if (!MapHomePage.nativeAmap) {
      if (mounted) setState(() => _checkingAmapSupport = false);
      return;
    }

    try {
      final supported =
          await _permissionChannel.invokeMethod<bool>('isAmapSupported') ??
          false;
      if (!mounted) return;
      setState(() {
        _amapRuntimeSupported = supported;
        _checkingAmapSupport = false;
      });
    } on PlatformException {
      if (mounted) setState(() => _checkingAmapSupport = false);
    } on MissingPluginException {
      if (mounted) setState(() => _checkingAmapSupport = false);
    }
  }

  Future<void> _locate() async {
    if (!MapHomePage.nativeAmap || !_amapRuntimeSupported) {
      _showMessage('请使用 ARM64 Android 真机体验高德定位');
      return;
    }

    final granted =
        await _permissionChannel.invokeMethod<bool>('requestLocation') ?? false;
    if (!mounted) return;
    if (!granted) {
      _showMessage('需要允许定位权限，才能显示当前位置');
      return;
    }

    setState(() {
      _locationEnabled = true;
      _centerOnNextLocation = true;
    });
    if (_lastLocation case final location?) {
      _moveToLocation(location);
    }
  }

  void _moveToLocation(LatLng location) {
    _mapController?.moveCamera(CameraUpdate.newLatLngZoom(location, 16));
    _centerOnNextLocation = false;
  }

  Future<void> _openLocationSearch() async {
    await _prepareSearchOrigin();
    if (!mounted) return;
    final origin = _lastLocation;
    final selected = await Navigator.push<LocationSelection>(
      context,
      MaterialPageRoute(
        builder: (_) => SearchLocationPage(
          originLatitude: origin?.latitude,
          originLongitude: origin?.longitude,
        ),
      ),
    );
    if (!mounted || selected == null) return;
    setState(() => _selectedLocation = selected);
    _moveToLocation(LatLng(selected.latitude, selected.longitude));
    _showMessage('已定位到 ${selected.name}');
  }

  Future<void> _prepareSearchOrigin() async {
    if (_lastLocation != null || !_amapRuntimeSupported) return;
    try {
      final granted =
          await _permissionChannel.invokeMethod<bool>('requestLocation') ??
          false;
      if (!mounted || !granted) return;
      setState(() => _locationEnabled = true);
      for (var attempt = 0; attempt < 10 && _lastLocation == null; attempt++) {
        await Future<void>.delayed(const Duration(milliseconds: 200));
      }
    } on PlatformException {
      return;
    } on MissingPluginException {
      return;
    }
  }

  void _toggleTraffic() {
    if (!MapHomePage.nativeAmap || !_amapRuntimeSupported) {
      _showMessage('请使用 ARM64 Android 真机体验高德实时路况');
      return;
    }
    setState(() => _trafficEnabled = !_trafficEnabled);
    _showMessage(_trafficEnabled ? '实时路况已开启' : '实时路况已关闭');
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _openCreateTrip() async {
    try {
      final state = await TripService(
        context.read<AppSession>().api,
      ).activeState();
      if (!mounted) return;
      if (state['active'] == true) {
        _showMessage(state['message']?.toString() ?? '一次只能进行一个行程');
        return;
      }
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => const TripCreatePage()),
      );
    } catch (e) {
      if (mounted) _showMessage('$e');
    }
  }

  Future<void> _openSos() async {
    await _prepareSearchOrigin();
    if (!mounted) return;
    final location = _lastLocation;
    if (location == null) {
      _showMessage('暂未获取当前位置，请允许定位后重试');
      return;
    }
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => SosConfirmPage(
          api: context.read<AppSession>().api,
          latitude: location.latitude,
          longitude: location.longitude,
          address: _selectedLocation?.address ?? '当前地图定位位置',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Stack(
    children: [
      Positioned.fill(
        child: _MapSurface(
          nativeEnabled: _amapRuntimeSupported,
          checkingSupport: _checkingAmapSupport,
          trafficEnabled: _trafficEnabled,
          locationEnabled: _locationEnabled,
          selectedLocation: _selectedLocation,
          onMapCreated: (controller) => _mapController = controller,
          onLocationChanged: (location) {
            if (!isLocationValid(location)) return;
            _lastLocation = location.latLng;
            if (_centerOnNextLocation) _moveToLocation(location.latLng);
          },
        ),
      ),
      SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: GestureDetector(
            onTap: _openLocationSearch,
            child: Container(
              height: 52,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(18),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x16000000),
                    blurRadius: 18,
                    offset: Offset(0, 6),
                  ),
                ],
              ),
              child: Row(
                children: [
                  const Icon(LucideIcons.search, color: AppColors.muted),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      _selectedLocation?.name ?? '搜索地点、路线或车队',
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: _selectedLocation == null
                            ? AppColors.muted
                            : AppColors.text,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
      Positioned(
        right: 18,
        top: 132,
        child: Column(
          children: [
            _MapTool(
              icon: LucideIcons.locateFixed,
              label: '定位',
              active: _locationEnabled,
              onTap: _locate,
            ),
            const SizedBox(height: 10),
            _MapTool(
              icon: LucideIcons.layers3,
              label: '路况',
              active: _trafficEnabled,
              onTap: _toggleTraffic,
            ),
            const SizedBox(height: 10),
            _MapTool(
              icon: LucideIcons.siren,
              label: 'SOS',
              danger: true,
              onTap: _openSos,
            ),
          ],
        ),
      ),
      Positioned(
        left: 16,
        right: 16,
        bottom: 18,
        child: Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(26),
            boxShadow: const [
              BoxShadow(
                color: Color(0x22000000),
                blurRadius: 28,
                offset: Offset(0, 10),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(
                children: [
                  Icon(LucideIcons.route, color: AppColors.primary),
                  SizedBox(width: 10),
                  Text(
                    '规划下一段旅程',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                  ),
                ],
              ),
              const SizedBox(height: 7),
              const Text(
                '创建路线、管理经停点，准备开始自驾',
                style: TextStyle(color: AppColors.secondaryText, fontSize: 13),
              ),
              const SizedBox(height: 12),
              FilledButton(
                onPressed: _openCreateTrip,
                child: const Text('创建行程'),
              ),
            ],
          ),
        ),
      ),
    ],
  );
}

class _MapSurface extends StatelessWidget {
  const _MapSurface({
    required this.nativeEnabled,
    required this.checkingSupport,
    required this.trafficEnabled,
    required this.locationEnabled,
    required this.selectedLocation,
    required this.onMapCreated,
    required this.onLocationChanged,
  });

  final bool nativeEnabled;
  final bool checkingSupport;
  final bool trafficEnabled;
  final bool locationEnabled;
  final LocationSelection? selectedLocation;
  final ValueChanged<AMapController> onMapCreated;
  final ValueChanged<AMapLocation> onLocationChanged;

  static const privacy = AMapPrivacyStatement(
    hasContains: true,
    hasShow: true,
    hasAgree: true,
  );
  @override
  Widget build(BuildContext context) {
    if (nativeEnabled) {
      AMapInitializer.init(context);
      AMapInitializer.updatePrivacyAgree(privacy);
      return AMapWidget(
        initialCameraPosition: const CameraPosition(
          target: LatLng(30.2741, 120.1551),
          zoom: 12,
        ),
        trafficEnabled: trafficEnabled,
        myLocationStyleOptions: MyLocationStyleOptions(
          locationEnabled,
          circleFillColor: const Color(0x223B82F6),
          circleStrokeColor: AppColors.primary,
          circleStrokeWidth: 1,
        ),
        markers: selectedLocation == null
            ? const <Marker>{}
            : {
                Marker(
                  position: LatLng(
                    selectedLocation!.latitude,
                    selectedLocation!.longitude,
                  ),
                  infoWindow: InfoWindow(
                    title: selectedLocation!.name,
                    snippet: selectedLocation!.address,
                  ),
                ),
              },
        onMapCreated: onMapCreated,
        onLocationChanged: onLocationChanged,
      );
    }
    return Stack(
      fit: StackFit.expand,
      children: [
        Container(
          color: const Color(0xFFF2F6FC),
          child: CustomPaint(painter: _RoadPainter()),
        ),
        if (MapHomePage.nativeAmap && !checkingSupport)
          Center(
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 68),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.92),
                borderRadius: BorderRadius.circular(14),
                boxShadow: const [
                  BoxShadow(color: Color(0x16000000), blurRadius: 16),
                ],
              ),
              child: const Text(
                '当前 x86_64 模拟器不支持高德原生地图\n请使用 ARM64 Android 真机预览',
                textAlign: TextAlign.center,
                style: TextStyle(color: AppColors.secondaryText, height: 1.5),
              ),
            ),
          ),
      ],
    );
  }
}

class _RoadPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final road = Paint()
      ..color = const Color(0xFFDCEBFF)
      ..strokeWidth = 15
      ..strokeCap = StrokeCap.round;
    canvas.drawLine(
      Offset(-20, size.height * .36),
      Offset(size.width + 30, size.height * .20),
      road,
    );
    canvas.drawLine(
      Offset(20, size.height * .68),
      Offset(size.width + 10, size.height * .52),
      road,
    );
    canvas.drawLine(
      Offset(size.width * .25, -20),
      Offset(size.width * .68, size.height),
      road,
    );
    final dot = Paint()..color = AppColors.primary;
    canvas.drawCircle(Offset(size.width * .48, size.height * .44), 12, dot);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _MapTool extends StatelessWidget {
  const _MapTool({
    required this.icon,
    required this.label,
    required this.onTap,
    this.danger = false,
    this.active = false,
  });
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool danger;
  final bool active;
  @override
  Widget build(BuildContext context) => Material(
    color: active ? const Color(0xFFEAF2FF) : Colors.white,
    borderRadius: BorderRadius.circular(16),
    elevation: 3,
    shadowColor: const Color(0x14000000),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: SizedBox(
        width: 54,
        height: 54,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 20,
              color: danger ? AppColors.danger : AppColors.primary,
            ),
            Text(
              label,
              style: TextStyle(
                fontSize: 10,
                color: danger ? AppColors.danger : AppColors.primary,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
