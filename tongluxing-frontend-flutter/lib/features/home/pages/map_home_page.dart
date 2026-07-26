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

/// 发现页：全屏高德地图 + Apple Maps 风格可拖动搜索抽屉。
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
  // 初始状态保留约 2/3 地图、1/3 操作卡。
  static const double _collapsedSheetSize = .33;
  static const double _keyboardMinSheetSize = .36;
  static const double _halfSheetSize = .48;
  static const double _expandedSheetSize = .76;
  static const double _keyboardMaxSheetSize = .92;

  final searchController = TextEditingController();
  final searchFocus = FocusNode();
  final searchSheetController = DraggableScrollableController();
  ScrollController? searchSheetScrollController;
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
      if (!mounted) return;
      setState(() {});
      if (searchFocus.hasFocus) {
        _animateSearchSheet(_expandedSheetSize);
      }
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
    searchSheetController.dispose();
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
      // 最近搜索失败不阻断地图首页。
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

  Future<void> _animateSearchSheet(double size) async {
    if (!searchSheetController.isAttached) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _animateSearchSheet(size);
      });
      return;
    }
    await searchSheetController.animateTo(
      size,
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOutCubic,
    );
  }

  void _collapseSearchSheet() {
    FocusManager.instance.primaryFocus?.unfocus();
    final scrollController = searchSheetScrollController;
    if (scrollController != null && scrollController.hasClients) {
      scrollController.animateTo(
        0,
        duration: const Duration(milliseconds: 180),
        curve: Curves.easeOut,
      );
    }
    _animateSearchSheet(_collapsedSheetSize);
  }

  void _toggleSearchSheet() {
    if (!searchSheetController.isAttached) return;
    final current = searchSheetController.size;
    _animateSearchSheet(
      current > _halfSheetSize - .05 ? _collapsedSheetSize : _halfSheetSize,
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
      await _animateSearchSheet(.36);
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
    _animateSearchSheet(_expandedSheetSize);
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
  Widget build(BuildContext context) {
    final keyboardVisible = MediaQuery.viewInsetsOf(context).bottom > 0;
    final minSheetSize = keyboardVisible
        ? _keyboardMinSheetSize
        : _collapsedSheetSize;
    final maxSheetSize = keyboardVisible
        ? _keyboardMaxSheetSize
        : _expandedSheetSize;

    return Stack(
      children: [
        Positioned.fill(
          child: _MapSurface(
            nativeEnabled: amapRuntimeSupported,
            checkingSupport: checkingAmapSupport,
            trafficEnabled: trafficEnabled,
            locationEnabled: locationEnabled,
            selectedLocation: selectedLocation,
            onMapCreated: (controller) => mapController = controller,
            onMapTap: _collapseSearchSheet,
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
          top: MediaQuery.paddingOf(context).top + 18,
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
        NotificationListener<DraggableScrollableNotification>(
          onNotification: (_) => false,
          child: DraggableScrollableSheet(
            controller: searchSheetController,
            initialChildSize: minSheetSize,
            minChildSize: minSheetSize,
            maxChildSize: maxSheetSize,
            snap: true,
            snapSizes: keyboardVisible
                ? const [
                    _keyboardMinSheetSize,
                    _halfSheetSize,
                    _expandedSheetSize,
                    _keyboardMaxSheetSize,
                  ]
                : const [
                    _collapsedSheetSize,
                    _halfSheetSize,
                    _expandedSheetSize,
                  ],
            builder: (context, scrollController) {
              searchSheetScrollController = scrollController;
              return _SearchSheet(
                controller: scrollController,
                searchController: searchController,
                focusNode: searchFocus,
                selectedLocation: selectedLocation,
                results: searchResults,
                history: recentLocations,
                loading: searchLoading,
                error: searchError,
                onHandleTap: _toggleSearchSheet,
                onChanged: _onKeywordChanged,
                onSubmitted: _search,
                onClear: _clearSearch,
                onSelect: _selectLocation,
                onSetStart: selectedLocation == null
                    ? null
                    : () => _createTrip(initialStart: selectedLocation),
                onSetWaypoint: selectedLocation == null
                    ? null
                    : () => _createTrip(initialWaypoint: selectedLocation),
                onSetEnd: selectedLocation == null
                    ? null
                    : () => _createTrip(initialEnd: selectedLocation),
              );
            },
          ),
        ),
      ],
    );
  }
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
    required this.onHandleTap,
    required this.onChanged,
    required this.onSubmitted,
    required this.onClear,
    required this.onSelect,
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
  final VoidCallback onHandleTap;
  final ValueChanged<String> onChanged;
  final ValueChanged<String> onSubmitted;
  final VoidCallback onClear;
  final ValueChanged<LocationSelection> onSelect;
  final VoidCallback? onSetStart;
  final VoidCallback? onSetWaypoint;
  final VoidCallback? onSetEnd;

  bool get hasKeyword => searchController.text.trim().isNotEmpty;

  @override
  Widget build(BuildContext context) => Container(
    decoration: const BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      boxShadow: [
        BoxShadow(
          color: Color(0x26000000),
          blurRadius: 18,
          offset: Offset(0, -3),
        ),
      ],
    ),
    clipBehavior: Clip.antiAlias,
    child: Column(
      children: [
        // 头部固定，避免抽屉滚动后搜索框被带出可视区域。
        InkWell(
          onTap: onHandleTap,
          child: const Padding(
            padding: EdgeInsets.symmetric(vertical: 5),
            child: SizedBox(
              width: 32,
              height: 4,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: Color(0xFFD0D5DD),
                  borderRadius: BorderRadius.all(Radius.circular(99)),
                ),
              ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 6),
          child: Container(
            height: 44,
            decoration: BoxDecoration(
              color: const Color(0xFFF4F6F8),
              borderRadius: BorderRadius.circular(22),
              border: Border.all(color: const Color(0xFFE4E7EC)),
            ),
            child: TextField(
              controller: searchController,
              focusNode: focusNode,
              onChanged: onChanged,
              onSubmitted: onSubmitted,
              textInputAction: TextInputAction.search,
              style: const TextStyle(color: Color(0xFF1D2939), fontSize: 15),
              cursorColor: AppColors.primary,
              decoration: InputDecoration(
                hintText: '搜索地点或地址',
                hintStyle: const TextStyle(color: Color(0xFF98A2B3)),
                prefixIcon: const Icon(
                  LucideIcons.search,
                  size: 20,
                  color: Color(0xFF344054),
                ),
                prefixIconConstraints: const BoxConstraints(
                  minWidth: 40,
                  minHeight: 40,
                ),
                suffixIcon: hasKeyword
                    ? IconButton(
                        tooltip: '清空',
                        onPressed: onClear,
                        icon: const Icon(
                          LucideIcons.x,
                          size: 19,
                          color: Color(0xFF667085),
                        ),
                      )
                    : null,
                suffixIconConstraints: const BoxConstraints(
                  minWidth: 42,
                  minHeight: 42,
                ),
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                filled: false,
                isDense: true,
                contentPadding: const EdgeInsets.symmetric(vertical: 12),
              ),
            ),
          ),
        ),
        Expanded(
          child: CustomScrollView(
            controller: controller,
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            slivers: [
              if (selectedLocation != null)
                SliverToBoxAdapter(
                  child: _SelectedPlaceCard(
                    location: selectedLocation!,
                    onSetStart: onSetStart!,
                    onSetWaypoint: onSetWaypoint!,
                    onSetEnd: onSetEnd!,
                  ),
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
                      padding: const EdgeInsets.all(20),
                      child: Text(
                        error!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: Color(0xFF667085)),
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
              const SliverToBoxAdapter(child: SizedBox(height: 20)),
            ],
          ),
        ),
      ],
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
    padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
    child: Container(
      padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFC),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE4E7EC)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            location.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(
              color: Color(0xFF101828),
              fontSize: 18,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 3),
          Text(
            location.address,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: Color(0xFF667085), fontSize: 13),
          ),
          if (location.distanceLabel != null) ...[
            const SizedBox(height: 3),
            Text(
              '距当前位置约 ${location.distanceLabel}',
              style: const TextStyle(fontSize: 12, color: AppColors.primary),
            ),
          ],
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 38,
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      textStyle: const TextStyle(fontSize: 13),
                    ),
                    onPressed: onSetStart,
                    child: const Text('设为起点'),
                  ),
                ),
              ),
              const SizedBox(width: 7),
              Expanded(
                child: SizedBox(
                  height: 38,
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      textStyle: const TextStyle(fontSize: 13),
                    ),
                    onPressed: onSetWaypoint,
                    child: const Text('设为停靠点'),
                  ),
                ),
              ),
              const SizedBox(width: 7),
              Expanded(
                child: SizedBox(
                  height: 38,
                  child: FilledButton(
                    style: FilledButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      textStyle: const TextStyle(fontSize: 13),
                    ),
                    onPressed: onSetEnd,
                    child: const Text('设为目的地'),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
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
          padding: const EdgeInsets.fromLTRB(20, 10, 20, 18),
          child: Center(
            child: Text(
              emptyText,
              style: const TextStyle(color: Color(0xFF98A2B3)),
            ),
          ),
        ),
      );
    }
    return SliverMainAxisGroup(
      slivers: [
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 5),
            child: Text(
              title,
              style: const TextStyle(
                color: Color(0xFF101828),
                fontSize: 16,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          sliver: SliverList.separated(
            itemCount: rows.length,
            separatorBuilder: (_, _) => const Divider(
              height: 1,
              indent: 52,
              endIndent: 4,
              color: Color(0xFFEAECF0),
            ),
            itemBuilder: (context, index) {
              final row = rows[index];
              return ListTile(
                dense: true,
                visualDensity: const VisualDensity(vertical: -2),
                contentPadding: const EdgeInsets.symmetric(horizontal: 4),
                tileColor: Colors.white,
                onTap: () => onSelect(row),
                leading: Container(
                  width: 36,
                  height: 36,
                  decoration: const BoxDecoration(
                    color: Color(0xFFEEF4FF),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    LucideIcons.mapPin,
                    size: 17,
                    color: AppColors.primary,
                  ),
                ),
                title: Text(
                  row.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Color(0xFF101828),
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                subtitle: Text(
                  row.address,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Color(0xFF667085),
                    fontSize: 12.5,
                  ),
                ),
                trailing: row.distanceLabel == null
                    ? null
                    : Text(
                        row.distanceLabel!,
                        style: const TextStyle(
                          fontSize: 12,
                          color: Color(0xFF98A2B3),
                        ),
                      ),
              );
            },
          ),
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
    required this.onMapTap,
    required this.onLocationChanged,
  });

  final bool nativeEnabled;
  final bool checkingSupport;
  final bool trafficEnabled;
  final bool locationEnabled;
  final LocationSelection? selectedLocation;
  final ValueChanged<AMapController> onMapCreated;
  final VoidCallback onMapTap;
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
        onTap: (_) => onMapTap(),
        onLocationChanged: onLocationChanged,
      );
    }
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onMapTap,
      child: Stack(
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
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 12,
                ),
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
      ),
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
    borderRadius: BorderRadius.circular(18),
    elevation: 4,
    shadowColor: const Color(0x24000000),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(18),
      child: SizedBox(
        width: 58,
        height: 58,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 21,
              color: danger ? AppColors.danger : AppColors.primary,
            ),
            const SizedBox(height: 2),
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
