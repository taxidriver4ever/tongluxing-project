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
  // 初始只露出一小段白色面板和灰色把手；点击或上滑后才显示搜索框。
  static const double _collapsedSheetSize = .04;
  static const double _contentRevealSheetSize = .14;
  // 展开档位需容纳把手、搜索框和至少一屏历史记录；低于 .54 时小屏设备
  // 在展开动画末尾可能出现内容溢出。
  static const double _halfSheetSize = .56;
  static const double _expandedSheetSize = .76;

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
  bool searchContentVisible = false;
  bool searchOriginWarmupStarted = false;
  int searchGeneration = 0;
  int sheetMoveGeneration = 0;
  double searchSheetSize = _collapsedSheetSize;
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
      if (searchFocus.hasFocus) {
        setState(() => searchContentVisible = true);
        unawaited(_moveSearchSheet(_expandedSheetSize));
      } else {
        setState(() {});
      }
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (locationService != null) return;
    // 独立组件预览和 Widget 测试可能没有注入会话；正式 App 始终由根节点注入。
    // 缺少会话时跳过服务端历史记录，不影响地图、定位与底部面板本身。
    try {
      locationService = LocationService(context.read<AppSession>().api);
    } on ProviderNotFoundException {
      return;
    }
    _loadHistory();
  }

  @override
  void dispose() {
    searchGeneration++;
    sheetMoveGeneration++;
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
      if (supported) {
        WidgetsBinding.instance.addPostFrameCallback((_) => _locate());
      }
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
    bool granted;
    try {
      granted =
          await _permissionChannel.invokeMethod<bool>('requestLocation') ??
          false;
    } on PlatformException {
      _showMessage('定位服务暂不可用，请检查系统定位设置后重试');
      return;
    } on MissingPluginException {
      _showMessage('当前设备暂不支持定位，仍可使用右侧定位按钮重试');
      return;
    }
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

  /// 搜索不能等待定位结果，否则首次输入可能额外卡住约两秒。
  /// 这里只在后台预热一次；本次搜索立即使用已有坐标或无坐标搜索。
  void _warmSearchOriginInBackground() {
    if (searchOriginWarmupStarted || lastLocation != null) return;
    searchOriginWarmupStarted = true;
    unawaited(_prepareSearchOrigin());
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

  /// 只允许最后一次面板操作决定终点。
  ///
  /// 聚焦搜索框会触发展开，失焦又会触发收起。用户快速点击时，两个
  /// animateTo 可能交叉执行，导致最终停在不稳定的中间高度。每次新操作
  /// 先用 jumpTo 取消旧动画，再在完成后校准到统一的 snap 尺寸。
  Future<void> _moveSearchSheet(double size) async {
    final generation = ++sheetMoveGeneration;
    if (!searchSheetController.isAttached) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && generation == sheetMoveGeneration) {
          unawaited(_moveSearchSheet(size));
        }
      });
      return;
    }
    try {
      // jumpTo(当前位置) 不会产生视觉跳动，但会终止上一个动画。
      searchSheetController.jumpTo(searchSheetController.size);
      await searchSheetController.animateTo(
        size,
        duration: const Duration(milliseconds: 240),
        curve: Curves.easeOutCubic,
      );
    } catch (_) {
      // 用户在程序动画过程中直接拖动时，旧动画可能被取消；以手势位置为准。
    } finally {
      // animateTo 可能被 snap 或失焦过程中断。只要期间没有新操作，
      // 仍强制落在目标尺寸，保证“初始收起”与“搜索后收起”完全一致。
      if (mounted &&
          generation == sheetMoveGeneration &&
          searchSheetController.isAttached) {
        searchSheetController.jumpTo(size);
      }
    }
  }

  void _collapseSearchSheet() {
    FocusManager.instance.primaryFocus?.unfocus();
    if (mounted) {
      setState(() {
        searchContentVisible = false;
        searchSheetSize = _collapsedSheetSize;
      });
    }
    final scrollController = searchSheetScrollController;
    if (scrollController != null && scrollController.hasClients) {
      scrollController.animateTo(
        0,
        duration: const Duration(milliseconds: 180),
        curve: Curves.easeOut,
      );
    }
    // 收起使用面板原生 reset，确保与首次进入时的
    // initialChildSize 是同一个值，不再受键盘失焦或 snap 误差影响。
    sheetMoveGeneration++;
    if (searchSheetController.isAttached) {
      searchSheetController.reset();
    }
  }

  void _toggleSearchSheet() {
    // 点击行为以“内容是否可见”为准，不依赖动画中瞬时高度。
    // 否则聚焦展开尚未结束时点击把手，会被误判为再次展开。
    if (searchContentVisible) {
      _collapseSearchSheet();
      return;
    }
    setState(() => searchContentVisible = true);
    unawaited(_moveSearchSheet(_halfSheetSize));
  }

  void _dragSearchHandle(DragUpdateDetails details) {
    if (!searchSheetController.isAttached) return;
    sheetMoveGeneration++;
    final delta = details.primaryDelta ?? 0;
    final screenHeight = MediaQuery.sizeOf(context).height;
    final next = (searchSheetController.size - delta / screenHeight)
        .clamp(_collapsedSheetSize, _expandedSheetSize)
        .toDouble();
    final showContent = next >= _contentRevealSheetSize;
    if (showContent != searchContentVisible) {
      setState(() => searchContentVisible = showContent);
    }
    searchSheetController.jumpTo(next);
  }

  void _startSearchHandleDrag(DragStartDetails details) {
    sheetMoveGeneration++;
    if (!searchContentVisible) {
      setState(() => searchContentVisible = true);
    }
  }

  void _endSearchHandleDrag(DragEndDetails details) {
    if (!searchSheetController.isAttached) return;
    final velocity = details.primaryVelocity ?? 0;
    final current = searchSheetController.size;
    final target = velocity < -450
        ? (current >= _halfSheetSize ? _expandedSheetSize : _halfSheetSize)
        : velocity > 450
        ? _collapsedSheetSize
        : current < (_collapsedSheetSize + _halfSheetSize) / 2
        ? _collapsedSheetSize
        : current < (_halfSheetSize + _expandedSheetSize) / 2
        ? _halfSheetSize
        : _expandedSheetSize;
    if (target == _collapsedSheetSize) {
      _collapseSearchSheet();
    } else {
      setState(() => searchContentVisible = true);
      unawaited(_moveSearchSheet(target));
    }
  }

  void _onKeywordChanged(String value) {
    debounce?.cancel();
    final query = value.trim();
    final generation = ++searchGeneration;
    setState(() {
      searchError = null;
      selectedLocation = null;
      searchResults = const [];
      searchLoading = query.isNotEmpty;
    });
    if (query.isEmpty) return;
    debounce = Timer(
      const Duration(milliseconds: 250),
      () => _search(query, generation: generation),
    );
  }

  void _submitSearch(String keyword) {
    debounce?.cancel();
    final generation = ++searchGeneration;
    unawaited(_search(keyword, generation: generation));
  }

  Future<void> _search(String keyword, {required int generation}) async {
    final query = keyword.trim();
    if (query.isEmpty || generation != searchGeneration) return;
    _warmSearchOriginInBackground();
    final service = locationService;
    if (service == null) {
      if (mounted && generation == searchGeneration) {
        setState(() {
          searchError = '搜索服务尚未准备好，请稍后重试';
          searchLoading = false;
        });
      }
      return;
    }
    setState(() {
      searchLoading = true;
      searchError = null;
    });
    try {
      final rows = await service
          .search(
            query,
            latitude: lastLocation?.latitude,
            longitude: lastLocation?.longitude,
          )
          .timeout(const Duration(seconds: 8));
      if (!mounted ||
          generation != searchGeneration ||
          query != searchController.text.trim()) {
        return;
      }
      setState(() {
        searchResults = rows;
        searchLoading = false;
      });
    } on ApiException catch (error) {
      if (!mounted || generation != searchGeneration) return;
      setState(() {
        searchError = error.message;
        searchLoading = false;
      });
    } catch (_) {
      if (!mounted || generation != searchGeneration) return;
      setState(() {
        searchError = '搜索暂时不可用，请稍后重试';
        searchLoading = false;
      });
    }
  }

  Future<void> _selectLocation(LocationSelection location) async {
    debounce?.cancel();
    searchGeneration++;
    FocusScope.of(context).unfocus();
    try {
      final saved = await locationService!.select(location);
      if (!mounted) return;
      setState(() {
        selectedLocation = saved;
        searchController.text = saved.name;
        searchResults = const [];
        searchLoading = false;
        searchError = null;
      });
      LocationSnapshot.current = LocationSnapshot(
        saved.latitude,
        saved.longitude,
      );
      _moveToLocation(LatLng(saved.latitude, saved.longitude));
      await _loadHistory();
      await _moveSearchSheet(_halfSheetSize);
    } on ApiException catch (error) {
      _showMessage(error.message);
    }
  }

  void _clearSearch() {
    debounce?.cancel();
    searchGeneration++;
    searchController.clear();
    setState(() {
      selectedLocation = null;
      searchResults = const [];
      searchError = null;
      searchLoading = false;
    });
    searchFocus.requestFocus();
    unawaited(_moveSearchSheet(_expandedSheetSize));
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
          onNotification: (notification) {
            final nextSize = notification.extent;
            var nextContentVisible = searchContentVisible;
            if (nextSize >= _contentRevealSheetSize) {
              nextContentVisible = true;
            } else if (nextSize <= _collapsedSheetSize + .015) {
              nextContentVisible = false;
            }
            if ((searchSheetSize - nextSize).abs() > .005 ||
                nextContentVisible != searchContentVisible) {
              setState(() {
                searchSheetSize = nextSize;
                searchContentVisible = nextContentVisible;
              });
            }
            if (nextSize <= _collapsedSheetSize + .015 &&
                searchFocus.hasFocus) {
              searchFocus.unfocus();
            }
            return false;
          },
          child: DraggableScrollableSheet(
            controller: searchSheetController,
            initialChildSize: _collapsedSheetSize,
            minChildSize: _collapsedSheetSize,
            maxChildSize: _expandedSheetSize,
            snap: true,
            snapSizes: const [
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
                collapsed: !searchContentVisible,
                onHandleTap: _toggleSearchSheet,
                onHandleDragStart: _startSearchHandleDrag,
                onHandleDragUpdate: _dragSearchHandle,
                onHandleDragEnd: _endSearchHandleDrag,
                onChanged: _onKeywordChanged,
                onSubmitted: _submitSearch,
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
    required this.collapsed,
    required this.onHandleTap,
    required this.onHandleDragStart,
    required this.onHandleDragUpdate,
    required this.onHandleDragEnd,
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
  final bool collapsed;
  final VoidCallback onHandleTap;
  final GestureDragStartCallback onHandleDragStart;
  final GestureDragUpdateCallback onHandleDragUpdate;
  final GestureDragEndCallback onHandleDragEnd;
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
    key: const ValueKey('map-search-sheet-surface'),
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
    child: Material(
      color: Colors.transparent,
      child: LayoutBuilder(
        builder: (context, constraints) => Stack(
          children: [
            // 头部固定，避免抽屉滚动后搜索框被带出可视区域。
            Positioned(
              left: 0,
              right: 0,
              top: 0,
              child: GestureDetector(
                key: const ValueKey('map-search-sheet-handle'),
                behavior: HitTestBehavior.opaque,
                onVerticalDragStart: onHandleDragStart,
                onVerticalDragUpdate: onHandleDragUpdate,
                onVerticalDragEnd: onHandleDragEnd,
                child: InkWell(
                  onTap: onHandleTap,
                  child: const Padding(
                    padding: EdgeInsets.symmetric(vertical: 7),
                    child: Center(
                      child: SizedBox(
                        width: 52,
                        height: 6,
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            color: Color(0xFFD0D5DD),
                            borderRadius: BorderRadius.all(Radius.circular(99)),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            if (!collapsed)
              Positioned(
                left: 12,
                right: 12,
                top: 20,
                height: 50,
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Container(
                    height: 44,
                    decoration: BoxDecoration(
                      color: const Color(0xFFF4F6F8),
                      borderRadius: BorderRadius.circular(22),
                      border: Border.all(color: const Color(0xFFE4E7EC)),
                    ),
                    child: TextField(
                      key: const ValueKey('map-place-search-field'),
                      controller: searchController,
                      focusNode: focusNode,
                      onChanged: onChanged,
                      onSubmitted: onSubmitted,
                      textInputAction: TextInputAction.search,
                      style: const TextStyle(
                        color: Color(0xFF1D2939),
                        fontSize: 15,
                      ),
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
                        contentPadding: const EdgeInsets.symmetric(
                          vertical: 12,
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            if (!collapsed)
              Positioned(
                left: 0,
                right: 0,
                top: 70,
                bottom: 0,
                child: CustomScrollView(
                  controller: controller,
                  keyboardDismissBehavior:
                      ScrollViewKeyboardDismissBehavior.onDrag,
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
                      const SliverToBoxAdapter(
                        child: LinearProgressIndicator(
                          minHeight: 2,
                          color: AppColors.primary,
                          backgroundColor: Color(0xFFE4E7EC),
                        ),
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
                        title: hasKeyword ? '猜你要找' : '最近搜索',
                        rows: hasKeyword ? results : history,
                        emptyText: hasKeyword ? '没有找到相关地点' : '暂无最近搜索',
                        onSelect: onSelect,
                      ),
                    SliverToBoxAdapter(
                      child: SizedBox(
                        height: MediaQuery.viewInsetsOf(context).bottom + 20,
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ),
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
