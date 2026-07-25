import 'dart:async';

import 'package:amap_map/amap_map.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';
import 'package:x_amap_base/x_amap_base.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/location_snapshot.dart';
import '../../trip/pages/trip_create_page.dart';
import 'sos_confirm_page.dart';

/// 苹果地图式“全屏地图 + 可拖动底部搜索抽屉”。
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

  final searchController = TextEditingController();
  final searchFocus = FocusNode();
  final sheetController = DraggableScrollableController();
  Timer? debounce;
  LocationService? locationService;

  AMapController? mapController;
  LatLng? lastLocation;
  bool locationEnabled = false;
  bool centerOnNextLocation = false;
  bool trafficEnabled = false;
  bool amapRuntimeSupported = false;
  bool checkingAmapSupport = true;
  bool searchLoading = false;
  String? searchError;
  LocationSelection? selectedLocation;
  List<LocationSelection> searchResults = const [];
  List<LocationSelection> recentLocations = const [];

  @override
  void initState() {
    super.initState();
    _checkAmapSupport();
    searchFocus.addListener(() {
      if (searchFocus.hasFocus) _expandSheet(.88);
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (locationService != null) return;
    locationService = LocationService(context.read<AppSession>().api);
    _loadHistory();
  }

  @override
  void dispose() {
    debounce?.cancel();
    searchController.dispose();
    searchFocus.dispose();
    sheetController.dispose();
    super.dispose();
  }

  Future<void> _checkAmapSupport() async {
    if (!MapHomePage.nativeAmap) {
      if (mounted) setState(() => checkingAmapSupport = false);
      return;
    }
    try {
      final supported =
          await _permissionChannel.invokeMethod<bool>('isAmapSupported') ??
          false;
      if (!mounted) return;
      setState(() {
        amapRuntimeSupported = supported;
        checkingAmapSupport = false;
      });
    } on PlatformException {
      if (mounted) setState(() => checkingAmapSupport = false);
    } on MissingPluginException {
      if (mounted) setState(() => checkingAmapSupport = false);
    }
  }

  Future<void> _loadHistory() async {
    try {
      final rows = await locationService!.history(
        latitude: lastLocation?.latitude,
        longitude: lastLocation?.longitude,
      );
      if (mounted) setState(() => recentLocations = rows);
    } catch (_) {
      // 首页历史加载失败不阻断地图使用。
    }
  }

  Future<void> _locate() async {
    if (!MapHomePage.nativeAmap || !amapRuntimeSupported) {
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
      locationEnabled = true;
      centerOnNextLocation = true;
    });
    if (lastLocation case final position?) _moveToLocation(position);
  }

  void _moveToLocation(LatLng location) {
    mapController?.moveCamera(CameraUpdate.newLatLngZoom(location, 16));
    centerOnNextLocation = false;
  }

  Future<void> _prepareSearchOrigin() async {
    if (lastLocation != null || !amapRuntimeSupported) return;
    try {
      final granted =
          await _permissionChannel.invokeMethod<bool>('requestLocation') ??
          false;
      if (!mounted || !granted) return;
      setState(() => locationEnabled = true);
      for (var attempt = 0; attempt < 10 && lastLocation == null; attempt++) {
        await Future<void>.delayed(const Duration(milliseconds: 200));
      }
    } on PlatformException {
      return;
    } on MissingPluginException {
      return;
    }
  }

  void _toggleTraffic() {
    if (!MapHomePage.nativeAmap || !amapRuntimeSupported) {
      _showMessage('请使用 ARM64 Android 真机体验高德实时路况');
      return;
    }
    setState(() => trafficEnabled = !trafficEnabled);
    _showMessage(trafficEnabled ? '实时路况已开启' : '实时路况已关闭');
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _openSos() async {
    await _prepareSearchOrigin();
    if (!mounted) return;
    final location = lastLocation;
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
          address: selectedLocation?.address ?? '当前地图定位位置',
        ),
      ),
    );
  }

  void _expandSheet(double size) {
    if (!sheetController.isAttached) return;
    sheetController.animateTo(
      size,
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOut,
    );
  }

  void _onKeywordChanged(String value) {
    debounce?.cancel();
    setState(() {
      searchError = null;
      selectedLocation = null;
      if (value.trim().isEmpty) searchResults = const [];
    });
    if (value.trim().isEmpty) return;
    debounce = Timer(
      const Duration(milliseconds: 400),
      () => _search(value.trim()),
    );
  }

  Future<void> _search(String keyword) async {
    final query = keyword.trim();
    if (query.isEmpty) return;
    await _prepareSearchOrigin();
    if (!mounted) return;
    setState(() {
      searchLoading = true;
      searchError = null;
    });
    try {
      final rows = await locationService!.search(
        query,
        latitude: lastLocation?.latitude,
        longitude: lastLocation?.longitude,
      );
      if (!mounted || query != searchController.text.trim()) return;
      setState(() {
        searchResults = rows;
        searchLoading = false;
      });
    } on ApiException catch (error) {
      if (!mounted || query != searchController.text.trim()) return;
      setState(() {
        searchError = error.message;
        searchLoading = false;
      });
    }
  }

  Future<void> _selectLocation(LocationSelection location) async {
    FocusScope.of(context).unfocus();
    try {
      final saved = await locationService!.select(location);
      if (!mounted) return;
      setState(() {
        selectedLocation = saved;
        searchController.text = saved.name;
        searchResults = const [];
      });
      LocationSnapshot.current = LocationSnapshot(
        saved.latitude,
        saved.longitude,
      );
      _moveToLocation(LatLng(saved.latitude, saved.longitude));
      await _loadHistory();
      _expandSheet(.36);
    } on ApiException catch (error) {
      _showMessage(error.message);
    }
  }

  void _clearSearch() {
    debounce?.cancel();
    searchController.clear();
    setState(() {
      selectedLocation = null;
      searchResults = const [];
      searchError = null;
      searchLoading = false;
    });
    searchFocus.requestFocus();
    _expandSheet(.88);
  }

  Future<void> _createTrip({
    LocationSelection? initialStart,
    LocationSelection? initialEnd,
    LocationSelection? initialWaypoint,
  }) async {
    await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => TripCreatePage(
          initialStart: initialStart,
          initialEnd: initialEnd,
          initialWaypoint: initialWaypoint,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Stack(
    children: [
      Positioned.fill(
        child: _MapSurface(
          nativeEnabled: amapRuntimeSupported,
          checkingSupport: checkingAmapSupport,
          trafficEnabled: trafficEnabled,
          locationEnabled: locationEnabled,
          selectedLocation: selectedLocation,
          onMapCreated: (controller) => mapController = controller,
          onLocationChanged: (location) {
            if (!isLocationValid(location)) return;
            lastLocation = location.latLng;
            LocationSnapshot.current = LocationSnapshot(
              location.latLng.latitude,
              location.latLng.longitude,
            );
            if (centerOnNextLocation) _moveToLocation(location.latLng);
          },
        ),
      ),
      Positioned(
        right: 16,
        top: MediaQuery.paddingOf(context).top + 20,
        child: Column(
          children: [
            _MapTool(
              icon: LucideIcons.locateFixed,
              label: '定位',
              active: locationEnabled,
              onTap: _locate,
            ),
            const SizedBox(height: 10),
            _MapTool(
              icon: LucideIcons.layers3,
              label: '路况',
              active: trafficEnabled,
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
      DraggableScrollableSheet(
        controller: sheetController,
        initialChildSize: .29,
        minChildSize: .18,
        maxChildSize: .88,
        snap: true,
        snapSizes: const [.29, .55, .88],
        builder: (context, scrollController) => _SearchSheet(
          controller: scrollController,
          searchController: searchController,
          focusNode: searchFocus,
          selectedLocation: selectedLocation,
          results: searchResults,
          history: recentLocations,
          loading: searchLoading,
          error: searchError,
          onChanged: _onKeywordChanged,
          onSubmitted: _search,
          onClear: _clearSearch,
          onSelect: _selectLocation,
          onCreateTrip: () => _createTrip(),
          onSetStart: selectedLocation == null
              ? null
              : () => _createTrip(initialStart: selectedLocation),
          onSetWaypoint: selectedLocation == null
              ? null
              : () => _createTrip(initialWaypoint: selectedLocation),
          onSetEnd: selectedLocation == null
              ? null
              : () => _createTrip(initialEnd: selectedLocation),
        ),
      ),
    ],
  );
}

class _SearchSheet extends StatelessWidget {
  const _SearchSheet({
    required this.controller,
    required this.searchController,
    required this.focusNode,
    required this.selectedLocation,
    required this.results,
    required this.history,
    required this.loading,
    required this.error,
    required this.onChanged,
    required this.onSubmitted,
    required this.onClear,
    required this.onSelect,
    required this.onCreateTrip,
    required this.onSetStart,
    required this.onSetWaypoint,
    required this.onSetEnd,
  });

  final ScrollController controller;
  final TextEditingController searchController;
  final FocusNode focusNode;
  final LocationSelection? selectedLocation;
  final List<LocationSelection> results;
  final List<LocationSelection> history;
  final bool loading;
  final String? error;
  final ValueChanged<String> onChanged;
  final ValueChanged<String> onSubmitted;
  final VoidCallback onClear;
  final ValueChanged<LocationSelection> onSelect;
  final VoidCallback onCreateTrip;
  final VoidCallback? onSetStart;
  final VoidCallback? onSetWaypoint;
  final VoidCallback? onSetEnd;

  bool get hasKeyword => searchController.text.trim().isNotEmpty;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    elevation: 18,
    shadowColor: const Color(0x33000000),
    borderRadius: const BorderRadius.vertical(top: Radius.circular(26)),
    clipBehavior: Clip.antiAlias,
    child: CustomScrollView(
      controller: controller,
      slivers: [
        SliverToBoxAdapter(
          child: Column(
            children: [
              const SizedBox(height: 9),
              Container(
                width: 42,
                height: 5,
                decoration: BoxDecoration(
                  color: const Color(0xFFD5D9E0),
                  borderRadius: BorderRadius.circular(99),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 10),
                child: TextField(
                  controller: searchController,
                  focusNode: focusNode,
                  onChanged: onChanged,
                  onSubmitted: onSubmitted,
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    hintText: '搜索地点或地址',
                    prefixIcon: const Icon(LucideIcons.search),
                    suffixIcon: hasKeyword
                        ? IconButton(
                            onPressed: onClear,
                            icon: const Icon(LucideIcons.x, size: 19),
                          )
                        : null,
                    filled: true,
                    fillColor: const Color(0xFFF3F5F8),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide.none,
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(16),
                      borderSide: BorderSide.none,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        if (selectedLocation != null)
          SliverToBoxAdapter(
            child: _SelectedPlaceCard(
              location: selectedLocation!,
              onSetStart: onSetStart!,
              onSetWaypoint: onSetWaypoint!,
              onSetEnd: onSetEnd!,
            ),
          )
        else if (!hasKeyword)
          SliverToBoxAdapter(
            child: _QuickCreateCard(onCreateTrip: onCreateTrip),
          ),
        if (loading)
          const SliverFillRemaining(
            hasScrollBody: false,
            child: Center(child: CircularProgressIndicator()),
          )
        else if (error != null)
          SliverFillRemaining(
            hasScrollBody: false,
            child: Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  error!,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.secondaryText),
                ),
              ),
            ),
          )
        else
          _PlaceListSliver(
            title: hasKeyword ? '搜索结果' : '最近搜索',
            rows: hasKeyword ? results : history,
            emptyText: hasKeyword ? '没有找到相关地点' : '暂无最近搜索',
            onSelect: onSelect,
          ),
        const SliverToBoxAdapter(child: SizedBox(height: 32)),
      ],
    ),
  );
}

class _QuickCreateCard extends StatelessWidget {
  const _QuickCreateCard({required this.onCreateTrip});
  final VoidCallback onCreateTrip;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 2, 16, 10),
    child: Container(
      padding: const EdgeInsets.all(15),
      decoration: BoxDecoration(
        color: const Color(0xFFF4F8FF),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          const CircleAvatar(
            backgroundColor: Colors.white,
            child: Icon(LucideIcons.route, color: AppColors.primary),
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('规划下一段旅程', style: TextStyle(fontWeight: FontWeight.w800)),
                Text(
                  '按顺序添加出发点、停靠点和目的地',
                  style: TextStyle(fontSize: 12, color: AppColors.secondaryText),
                ),
              ],
            ),
          ),
          FilledButton(onPressed: onCreateTrip, child: const Text('创建')),
        ],
      ),
    ),
  );
}

class _SelectedPlaceCard extends StatelessWidget {
  const _SelectedPlaceCard({
    required this.location,
    required this.onSetStart,
    required this.onSetWaypoint,
    required this.onSetEnd,
  });

  final LocationSelection location;
  final VoidCallback onSetStart;
  final VoidCallback onSetWaypoint;
  final VoidCallback onSetEnd;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 2, 16, 12),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          location.name,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900),
        ),
        const SizedBox(height: 5),
        Text(
          location.address,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(color: AppColors.secondaryText),
        ),
        if (location.distanceLabel != null) ...[
          const SizedBox(height: 4),
          Text(
            '距当前位置约 ${location.distanceLabel}',
            style: const TextStyle(fontSize: 12, color: AppColors.primary),
          ),
        ],
        const SizedBox(height: 13),
        Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: onSetStart,
                child: const Text('设为出发点'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: OutlinedButton(
                onPressed: onSetWaypoint,
                child: const Text('设为途经点'),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: FilledButton(
                onPressed: onSetEnd,
                child: const Text('设为目的地'),
              ),
            ),
          ],
        ),
      ],
    ),
  );
}

class _PlaceListSliver extends StatelessWidget {
  const _PlaceListSliver({
    required this.title,
    required this.rows,
    required this.emptyText,
    required this.onSelect,
  });

  final String title;
  final List<LocationSelection> rows;
  final String emptyText;
  final ValueChanged<LocationSelection> onSelect;

  @override
  Widget build(BuildContext context) {
    if (rows.isEmpty) {
      return SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.all(30),
          child: Center(
            child: Text(emptyText, style: const TextStyle(color: AppColors.muted)),
          ),
        ),
      );
    }
    return SliverMainAxisGroup(
      slivers: [
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(18, 10, 18, 6),
            child: Text(title, style: const TextStyle(fontWeight: FontWeight.w900)),
          ),
        ),
        SliverList.separated(
          itemCount: rows.length,
          separatorBuilder: (_, _) => const Divider(height: 1, indent: 58),
          itemBuilder: (context, index) {
            final row = rows[index];
            return ListTile(
              onTap: () => onSelect(row),
              leading: const CircleAvatar(
                backgroundColor: Color(0xFFF0F5FF),
                child: Icon(LucideIcons.mapPin, size: 18, color: AppColors.primary),
              ),
              title: Text(
                row.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              subtitle: Text(
                row.address,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              trailing: row.distanceLabel == null
                  ? null
                  : Text(
                      row.distanceLabel!,
                      style: const TextStyle(fontSize: 12, color: AppColors.muted),
                    ),
            );
          },
        ),
      ],
    );
  }
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
