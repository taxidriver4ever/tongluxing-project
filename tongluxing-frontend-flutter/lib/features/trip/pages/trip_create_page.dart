import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/routes.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';
import '../../home/pages/search_location_page.dart';
import '../widgets/trip_route_preview.dart';

/// 三步式行程创建：路线、基本信息和同行要求彼此分开，避免表单拥挤。
class TripCreatePage extends StatefulWidget {
  const TripCreatePage({
    this.draft,
    this.initialStart,
    this.initialEnd,
    this.initialWaypoint,
    super.key,
  });

  final TripDraftModel? draft;
  final LocationSelection? initialStart;
  final LocationSelection? initialEnd;
  final LocationSelection? initialWaypoint;

  @override
  State<TripCreatePage> createState() => _TripCreatePageState();
}

class _TripCreatePageState extends State<TripCreatePage> {
  static const _routeDebounce = Duration(milliseconds: 450);
  static const double _routePanelCollapsedSize = .12;
  static const double _routePanelInitialSize = .62;
  static const double _routePanelExpandedSize = .88;
  static const _typeLabels = <String, String>{
    'REST': '休息点',
    'HOTEL': '住宿点',
    'FUEL': '加油点',
    'CHARGING': '充电点',
    'CHECK_IN': '打卡点',
    'NORMAL': '普通途经点',
  };

  final pageController = PageController();
  final title = TextEditingController();
  final description = TextEditingController();
  final budget = TextEditingController();
  final notes = TextEditingController();
  final Set<String> vehicleRequirements = {'不限'};
  final List<_WaypointDraft> waypoints = [];
  final List<String> persistedWaypointIds = [];
  final routePanelController = DraggableScrollableController();
  ScrollController? routePanelScrollController;

  int step = 0;
  int expectPeople = 5;
  DateTime? startTime;
  DateTime? expectedEndDate;
  LocationSelection? start;
  LocationSelection? end;
  String? draftId;
  TripDraftRouteModel? route;
  bool submitting = false;
  bool routePlanning = false;
  bool routePlanQueued = false;
  Timer? routeTimer;
  int routeGeneration = 0;
  String? plannedRouteSignature;

  Uint8List? coverBytes;
  String coverFileName = '';
  String coverImageKey = '';
  String coverDownloadUrl = '';

  @override
  void initState() {
    super.initState();
    final draft = widget.draft;
    if (draft != null) {
      draftId = draft.id;
      title.text = draft.title;
      description.text = draft.description;
      budget.text = draft.budgetDescription;
      notes.text = draft.notes;
      vehicleRequirements
        ..clear()
        ..addAll(
          draft.vehicleRequirements.isEmpty
              ? const ['不限']
              : draft.vehicleRequirements,
        );
      startTime = DateTime.tryParse(draft.startTime ?? '');
      if (startTime != null) {
        expectedEndDate = startTime!.add(
          Duration(days: math.max(1, draft.durationDays) - 1),
        );
      }
      start = draft.startLocation;
      end = draft.destination;
      expectPeople = draft.expectPeople;
      waypoints.addAll(
        draft.waypoints.map(
          (item) => _WaypointDraft(
            id: item.id,
            location: item.location,
            type: item.type,
            stayMinutes: item.stayMinutes,
            remark: item.remark,
          ),
        ),
      );
      persistedWaypointIds.addAll(draft.waypoints.map((item) => item.id));
      route = draft.route?.status.toUpperCase() == 'VALID'
          ? draft.route
          : null;
      if (route != null) plannedRouteSignature = _routeSignature();
      coverImageKey = draft.coverImageKey;
      if (coverImageKey.isNotEmpty) {
        WidgetsBinding.instance.addPostFrameCallback((_) => _loadCoverUrl());
      }
    } else {
      start = widget.initialStart;
      end = widget.initialEnd;
      final initialWaypoint = widget.initialWaypoint;
      if (initialWaypoint != null) {
        waypoints.add(_WaypointDraft(location: initialWaypoint));
      }
    }
    if (start != null && end != null && draft == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _scheduleRoutePlan());
    }
  }

  @override
  void dispose() {
    routeTimer?.cancel();
    routePanelController.dispose();
    pageController.dispose();
    title.dispose();
    description.dispose();
    budget.dispose();
    notes.dispose();
    super.dispose();
  }

  TripService get _tripService => TripService(context.read<AppSession>().api);

  Future<LocationSelection?> _pickLocation() =>
      showModalBottomSheet<LocationSelection>(
        context: context,
        isScrollControlled: true,
        useSafeArea: true,
        backgroundColor: Colors.transparent,
        barrierColor: const Color(0x66000000),
        builder: (_) => const FractionallySizedBox(
          heightFactor: .92,
          child: SearchLocationPage(embedded: true, autofocus: true),
        ),
      );

  Future<void> _selectEndpoint({required bool isStart}) async {
    final value = await _pickLocation();
    if (value == null || !mounted) return;
    final conflict = isStart
        ? _conflictForStart(value)
        : _conflictForEnd(value);
    if (conflict != null) {
      _showMessage(conflict);
      return;
    }
    setState(() {
      if (isStart) {
        start = value;
      } else {
        end = value;
      }
      route = null;
    });
    _scheduleRoutePlan();
  }

  Future<void> _addWaypoint() async {
    if (waypoints.length >= 20) {
      _showMessage('经停点最多 20 个');
      return;
    }
    final value = await _pickLocation();
    if (value == null || !mounted) return;
    final conflict = _conflictForWaypoint(value);
    if (conflict != null) {
      _showMessage(conflict);
      return;
    }
    setState(() {
      waypoints.add(_WaypointDraft(location: value));
      route = null;
    });
    _scheduleRoutePlan();
  }

  Future<void> _replaceWaypoint(int index) async {
    final value = await _pickLocation();
    if (value == null || !mounted) return;
    final conflict = _conflictForWaypoint(value, excludedIndex: index);
    if (conflict != null) {
      _showMessage(conflict);
      return;
    }
    setState(() {
      waypoints[index] = waypoints[index].copyWith(location: value);
      route = null;
    });
    _scheduleRoutePlan();
  }

  Future<void> _editWaypoint(int index) async {
    final current = waypoints[index];
    final result = await showModalBottomSheet<_WaypointDraft>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) =>
          _WaypointEditorSheet(current: current, typeLabels: _typeLabels),
    );
    if (result == null || !mounted) return;
    setState(() => waypoints[index] = result);
    await _persistRoute(plan: false, showError: true);
  }

  void _removeWaypoint(int index) {
    setState(() {
      waypoints.removeAt(index);
      route = null;
    });
    _scheduleRoutePlan();
  }

  void _reorderWaypoint(int oldIndex, int newIndex) {
    setState(() {
      final item = waypoints.removeAt(oldIndex);
      waypoints.insert(newIndex, item);
      route = null;
    });
    _scheduleRoutePlan();
  }

  void _scheduleRoutePlan() {
    routeTimer?.cancel();
    final generation = ++routeGeneration;
    if (start == null || end == null || _routeConflictMessage() != null) return;
    if (route?.status.toUpperCase() == 'VALID' &&
        plannedRouteSignature == _routeSignature()) {
      return;
    }
    routeTimer = Timer(_routeDebounce, () async {
      if (!mounted || generation != routeGeneration) return;
      if (routePlanning) {
        routePlanQueued = true;
        return;
      }
      await _persistRoute(plan: true, showError: false, generation: generation);
    });
  }

  String? _conflictForStart(LocationSelection value) {
    if (_sameLocation(value, end)) return '起点不能与目的地相同或距离过近';
    if (waypoints.any((point) => _sameLocation(value, point.location))) {
      return '起点不能与途经点相同或距离过近';
    }
    return null;
  }

  String? _conflictForEnd(LocationSelection value) {
    if (_sameLocation(value, start)) return '目的地不能与起点相同或距离过近';
    if (waypoints.any((point) => _sameLocation(value, point.location))) {
      return '目的地不能与途经点相同或距离过近';
    }
    return null;
  }

  String? _conflictForWaypoint(LocationSelection value, {int? excludedIndex}) {
    if (_sameLocation(value, start)) return '途经点不能与起点相同或距离过近';
    if (_sameLocation(value, end)) return '途经点不能与目的地相同或距离过近';
    for (var i = 0; i < waypoints.length; i++) {
      if (i != excludedIndex && _sameLocation(value, waypoints[i].location)) {
        return '途经点之间不能重复或距离过近';
      }
    }
    return null;
  }

  String? _routeConflictMessage() {
    if (start == null) return '请选择起点';
    if (end == null) return '请选择目的地';
    if (_sameLocation(start, end)) return '起点和目的地不能相同或距离过近';
    for (var i = 0; i < waypoints.length; i++) {
      final point = waypoints[i].location;
      if (_sameLocation(point, start)) return '途经点不能与起点相同或距离过近';
      if (_sameLocation(point, end)) return '途经点不能与目的地相同或距离过近';
      for (var j = i + 1; j < waypoints.length; j++) {
        if (_sameLocation(point, waypoints[j].location)) {
          return '途经点之间不能重复或距离过近';
        }
      }
    }
    return null;
  }

  bool _sameLocation(LocationSelection? left, LocationSelection? right) {
    if (left == null || right == null) return false;
    if (_distanceMeters(left, right) <= 30) return true;
    final leftAddress = _normalizeLocationText(left.address);
    final rightAddress = _normalizeLocationText(right.address);
    return _normalizeLocationText(left.name) ==
            _normalizeLocationText(right.name) &&
        leftAddress.isNotEmpty &&
        leftAddress == rightAddress;
  }

  double _distanceMeters(LocationSelection left, LocationSelection right) {
    const radius = 6371000.0;
    final lat1 = left.latitude * math.pi / 180;
    final lat2 = right.latitude * math.pi / 180;
    final dLat = lat2 - lat1;
    final dLng = (right.longitude - left.longitude) * math.pi / 180;
    final value =
        math.sin(dLat / 2) * math.sin(dLat / 2) +
        math.cos(lat1) *
            math.cos(lat2) *
            math.sin(dLng / 2) *
            math.sin(dLng / 2);
    return radius * 2 * math.atan2(math.sqrt(value), math.sqrt(1 - value));
  }

  String _normalizeLocationText(String value) =>
      value.trim().replaceAll(RegExp(r'\s+'), '').toLowerCase();

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _pickTime() async {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final initialDate = startTime != null && startTime!.isAfter(now)
        ? startTime!
        : now.add(const Duration(days: 1));
    final date = await showDatePicker(
      context: context,
      locale: const Locale('zh', 'CN'),
      helpText: '选择出发日期',
      cancelText: '取消',
      confirmText: '确定',
      initialDate: initialDate,
      firstDate: today,
      lastDate: today.add(const Duration(days: 365)),
    );
    if (date == null || !mounted) return;
    final selectingToday =
        date.year == now.year && date.month == now.month && date.day == now.day;
    final suggestedTime = selectingToday
        ? TimeOfDay.fromDateTime(now.add(const Duration(minutes: 5)))
        : startTime != null &&
              startTime!.year == date.year &&
              startTime!.month == date.month &&
              startTime!.day == date.day
        ? TimeOfDay.fromDateTime(startTime!)
        : const TimeOfDay(hour: 8, minute: 0);
    final time = await showTimePicker(
      context: context,
      helpText: '选择出发时间',
      cancelText: '取消',
      confirmText: '确定',
      initialTime: suggestedTime,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true),
        child: child!,
      ),
    );
    if (time == null || !mounted) return;
    final selected = DateTime(
      date.year,
      date.month,
      date.day,
      time.hour,
      time.minute,
    );
    if (!selected.isAfter(DateTime.now())) {
      _showMessage('出发时间必须晚于当前时间，请重新选择时分');
      return;
    }
    setState(() {
      startTime = selected;
      if (expectedEndDate == null || expectedEndDate!.isBefore(selected)) {
        expectedEndDate = selected;
      }
    });
  }

  Future<void> _pickExpectedEndDate() async {
    final startValue = startTime;
    if (startValue == null) {
      _showMessage('请先选择预计出发时间');
      return;
    }
    final selected = await showDatePicker(
      context: context,
      locale: const Locale('zh', 'CN'),
      helpText: '选择预计结束日期',
      cancelText: '取消',
      confirmText: '确定',
      initialDate: expectedEndDate ?? startValue,
      firstDate: DateTime(startValue.year, startValue.month, startValue.day),
      lastDate: DateTime(startValue.year + 1, startValue.month, startValue.day),
    );
    if (selected != null && mounted) {
      setState(
        () => expectedEndDate = DateTime(
          selected.year,
          selected.month,
          selected.day,
          startValue.hour,
          startValue.minute,
        ),
      );
    }
  }

  String _formatTime(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-'
      '${value.month.toString().padLeft(2, '0')}-'
      '${value.day.toString().padLeft(2, '0')} '
      '${value.hour.toString().padLeft(2, '0')}:'
      '${value.minute.toString().padLeft(2, '0')}:00';

  String _formatChineseTime(DateTime value, {bool dateOnly = false}) =>
      '${value.year}年${value.month}月${value.day}日'
      '${dateOnly ? '' : ' ${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}'}';

  Future<void> _loadCoverUrl() async {
    try {
      final value = await StorageUploadService(
        context.read<AppSession>().api,
      ).downloadUrlByObjectKey(coverImageKey);
      if (mounted) setState(() => coverDownloadUrl = value);
    } catch (_) {
      // 草稿封面加载失败不阻断节点编辑。
    }
  }

  Future<void> _pickCover() async {
    try {
      final image = await ImagePicker().pickImage(
        source: ImageSource.gallery,
        imageQuality: 88,
        maxWidth: 1920,
      );
      if (image == null || !mounted) return;
      final bytes = await image.readAsBytes();
      if (bytes.length > 10 * 1024 * 1024) {
        throw const ApiException('行程封面不能超过 10MB');
      }
      setState(() {
        coverBytes = bytes;
        coverFileName = image.name;
        coverDownloadUrl = '';
      });
    } catch (error) {
      _showMessage('$error');
    }
  }

  Future<void> _ensureCoverUploaded() async {
    final bytes = coverBytes;
    if (bytes == null) return;
    final storageService = StorageUploadService(context.read<AppSession>().api);
    final uploaded = await storageService.upload(
      bizType: 'TRIP_COVER',
      bizId: draftId ?? context.read<AppSession>().userId,
      fileName: coverFileName.isEmpty ? 'trip-cover.jpg' : coverFileName,
      bytes: bytes,
    );
    final uploadedKey = uploaded['objectKey']?.toString() ?? '';
    if (uploadedKey.isEmpty) {
      throw const ApiException('行程封面上传结果缺少 objectKey');
    }
    coverImageKey = uploadedKey;
    coverBytes = null;
    try {
      coverDownloadUrl = await storageService.downloadUrlByObjectKey(
        coverImageKey,
      );
    } catch (_) {
      coverDownloadUrl = '';
    }
  }

  Map<String, dynamic> _draftBody({bool includeRoute = true}) => {
    'title': title.text.trim().isEmpty ? null : title.text.trim(),
    'startTime': startTime == null ? null : _formatTime(startTime!),
    if (includeRoute) 'startLocation': start?.toJson(),
    if (includeRoute) 'destination': end?.toJson(),
    'description': description.text.trim(),
    'coverImageKey': coverImageKey.isEmpty ? null : coverImageKey,
    'expectPeople': expectPeople,
    'durationDays': startTime == null || expectedEndDate == null
        ? 1
        : expectedEndDate!
                  .difference(
                    DateTime(startTime!.year, startTime!.month, startTime!.day),
                  )
                  .inDays +
              1,
    'vehicleRequirements': vehicleRequirements.toList(),
    'budgetDescription': budget.text.trim(),
    'notes': notes.text.trim(),
  };

  Future<String> _ensureDraft() async {
    final existing = draftId;
    if (existing != null) return existing;
    final draft = await _tripService.createDraft(_draftBody());
    draftId = draft.id;
    return draft.id;
  }

  Future<bool> _persistRoute({
    required bool plan,
    required bool showError,
    int? generation,
  }) async {
    final conflict = _routeConflictMessage();
    if (conflict != null) {
      if (showError) _showMessage(conflict);
      return false;
    }
    if (routePlanning) return false;
    if (mounted) setState(() => routePlanning = true);
    try {
      final id = await _ensureDraft();
      await _tripService.updateDraft(id, _draftBody());
      final signature = _routeSignature();
      // 路线节点未变化时复用已经成功的规划结果，避免“下一步”和“发布”
      // 再次提交所有途经点、排序并调用地图服务。
      if (route?.status.toUpperCase() == 'VALID' &&
          plannedRouteSignature == signature) {
        return true;
      }
      final previousIds = persistedWaypointIds.toSet();
      final activeIds = <String>[];
      for (var i = 0; i < waypoints.length; i++) {
        final item = waypoints[i];
        final waypointId = item.id;
        final saved = waypointId != null && previousIds.contains(waypointId)
            ? await _tripService.updateWaypoint(
                id,
                waypointId,
                item.location,
                type: item.type,
                sort: i + 1,
                stayMinutes: item.stayMinutes,
                remark: item.remark,
              )
            : await _tripService.addWaypoint(
                id,
                item.location,
                type: item.type,
                sort: i + 1,
                stayMinutes: item.stayMinutes,
                remark: item.remark,
              );
        activeIds.add(saved.id);
        waypoints[i] = item.copyWith(id: saved.id);
      }
      for (final removedId in previousIds.difference(activeIds.toSet())) {
        await _tripService.deleteWaypoint(id, removedId);
      }
      if (activeIds.isNotEmpty) {
        await _tripService.reorderWaypoints(id, activeIds);
      }
      persistedWaypointIds
        ..clear()
        ..addAll(activeIds);
      if (plan) {
        final json = await _tripService.planRoute(id);
        if (generation == null || generation == routeGeneration) {
          route = TripDraftRouteModel.fromJson(json);
          plannedRouteSignature = signature;
        }
      }
      if (mounted) setState(() {});
      return true;
    } catch (error) {
      if (showError) _showMessage('$error');
      return false;
    } finally {
      if (mounted) {
        setState(() => routePlanning = false);
        if (routePlanQueued) {
          routePlanQueued = false;
          _scheduleRoutePlan();
        }
      }
    }
  }

  Future<bool> _saveAll({bool plan = true}) async {
    setState(() => submitting = true);
    try {
      await _ensureCoverUploaded();
      if (start != null && end != null) {
        return await _persistRoute(plan: plan, showError: true);
      }
      final id = await _ensureDraft();
      await _tripService.updateDraft(id, _draftBody());
      return true;
    } catch (error) {
      _showMessage('$error');
      return false;
    } finally {
      if (mounted) setState(() => submitting = false);
    }
  }

  Future<void> _handlePublishError(Object error) async {
    // P0：所有用户都能发布。这里不再把“无认证车辆”解释为错误，
    // 后端会自动把无车用户的发布识别为乘客出行需求。
    _showMessage('$error');
  }

  Future<void> _next() async {
    final conflict = _routeConflictMessage();
    if (conflict != null) {
      _showMessage(conflict);
      return;
    }
    if (!await _saveAll(plan: true) || !mounted) return;
    setState(() => step = 1);
    await pageController.animateToPage(
      1,
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOut,
    );
  }

  Future<void> _saveOnly() async {
    final saved = await _saveAll(plan: start != null && end != null);
    if (!saved || !mounted) return;
    _showMessage('已保存到我的草稿');
    Navigator.pop(context, true);
  }

  Future<void> _nextToRequirements() async {
    if (title.text.trim().isEmpty) {
      _showMessage('请填写行程标题');
      return;
    }
    if (startTime == null) {
      _showMessage('请选择预计出发时间');
      return;
    }
    setState(() => submitting = true);
    try {
      await _ensureCoverUploaded();
      final id = await _ensureDraft();
      await _tripService.updateDraft(id, _draftBody());
      if (!mounted) return;
      setState(() => step = 2);
      await pageController.animateToPage(
        2,
        duration: const Duration(milliseconds: 260),
        curve: Curves.easeOut,
      );
    } catch (error) {
      _showMessage('$error');
    } finally {
      if (mounted) setState(() => submitting = false);
    }
  }

  Future<void> _publish() async {
    if (title.text.trim().isEmpty) {
      _showMessage('请填写行程标题');
      return;
    }
    if (startTime == null) {
      _showMessage('请选择预计出发时间');
      return;
    }
    final conflict = _routeConflictMessage();
    if (conflict != null) {
      _showMessage(conflict);
      return;
    }
    setState(() => submitting = true);
    try {
      await _ensureCoverUploaded();
      final id = await _ensureDraft();
      if (!await _persistRoute(plan: true, showError: true)) return;
      if (!await _confirmTimeConflict()) return;
      final tripId = await _tripService.publishDraft(id);
      final published = await _tripService.detail(tripId);
      if (!mounted) return;
      _showMessage(
        published.passengerDemand
            ? '出行需求发布成功，已进入匹配池'
            : '车主行程发布成功，已自动成为队长',
      );
      Navigator.pushNamedAndRemoveUntil(context, AppRoutes.home, (_) => false);
    } catch (error) {
      await _handlePublishError(error);
    } finally {
      if (mounted) setState(() => submitting = false);
    }
  }

  String _routeSignature() => jsonEncode({
    'start': start == null ? null : [start!.latitude, start!.longitude],
    'end': end == null ? null : [end!.latitude, end!.longitude],
    'waypoints': waypoints
        .map((item) => [item.location.latitude, item.location.longitude])
        .toList(),
  });

  Future<bool> _confirmTimeConflict() async {
    final departure = startTime;
    if (departure == null) return true;
    final result = await _tripService.checkTimeConflict(
      departureTime: departure,
      estimatedDays: 1,
    );
    if (result['conflict'] != true || !mounted) return true;
    return await showDialog<bool>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            icon: const Icon(LucideIcons.calendarClock),
            title: const Text('行程时间可能冲突'),
            content: Text(
              '${result['message'] ?? '该行程与您已发布的行程时间存在冲突，请调整出发时间。'}\n\n'
              '冲突行程：${result['conflictTitle'] ?? '未命名行程'}\n'
              '预计时间：${result['conflictStartTime'] ?? '-'} 至 ${result['conflictEndTime'] ?? '-'}',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(dialogContext, false),
                child: const Text('返回调整'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(dialogContext, true),
                child: const Text('仍然发布'),
              ),
            ],
          ),
        ) ??
        false;
  }

  void _previous() {
    final target = math.max(0, step - 1);
    setState(() => step = target);
    pageController.animateToPage(
      target,
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOut,
    );
    if (target == 0) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _animateRoutePanel(_routePanelInitialSize);
      });
    }
  }

  Future<void> _animateRoutePanel(double size) async {
    if (!routePanelController.isAttached) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _animateRoutePanel(size);
      });
      return;
    }
    await routePanelController.animateTo(
      size,
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOutCubic,
    );
  }

  void _collapseRoutePanel() {
    if (step != 0 || !mounted) return;
    FocusManager.instance.primaryFocus?.unfocus();
    final scrollController = routePanelScrollController;
    if (scrollController != null && scrollController.hasClients) {
      scrollController.animateTo(
        0,
        duration: const Duration(milliseconds: 180),
        curve: Curves.easeOut,
      );
    }
    _animateRoutePanel(_routePanelCollapsedSize);
  }

  void _toggleRoutePanel() {
    if (step != 0 || !routePanelController.isAttached) return;
    final current = routePanelController.size;
    _animateRoutePanel(
      current <= _routePanelCollapsedSize + .05
          ? _routePanelInitialSize
          : _routePanelCollapsedSize,
    );
  }

  List<LocationSelection> get _routePoints => [
    ?start,
    ...waypoints.map((item) => item.location),
    ?end,
  ];

  String get _distanceText {
    final meters = route?.distanceMeters;
    if (meters == null || meters <= 0) return '等待路线规划';
    return '${(meters / 1000).toStringAsFixed(1)} km';
  }

  String get _durationText {
    final minutes = route?.durationMinutes;
    if (minutes == null || minutes <= 0) return '--';
    final hours = minutes ~/ 60;
    final left = minutes % 60;
    if (hours == 0) return '$left 分钟';
    return left == 0 ? '$hours 小时' : '$hours 小时 $left 分钟';
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(switch (step) {
        0 => '规划行程节点',
        1 => '填写行程信息',
        _ => '设置同行要求',
      }),
    ),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(22, 2, 22, 10),
          child: Row(
            children: List.generate(
              3,
              (index) => Expanded(
                child: Container(
                  height: 5,
                  margin: EdgeInsets.only(right: index == 2 ? 0 : 8),
                  decoration: BoxDecoration(
                    color: index <= step ? AppColors.primary : AppColors.border,
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
              ),
            ),
          ),
        ),
        Expanded(
          child: PageView(
            controller: pageController,
            physics: const NeverScrollableScrollPhysics(),
            children: [
              _buildRouteStep(),
              _buildPublishStep(),
              _buildRequirementsStep(),
            ],
          ),
        ),
        if (step > 0) _buildBottomActions(),
      ],
    ),
  );

  Widget _buildRouteStep() => LayoutBuilder(
    builder: (context, constraints) => Stack(
      children: [
        Positioned.fill(
          child: _routePoints.length >= 2
              ? TripRoutePreview(
                  points: _routePoints,
                  route: route,
                  mapOnly: true,
                  mapHeight: constraints.maxHeight,
                  interactive: true,
                  onMapInteraction: _collapseRoutePanel,
                  simplifiedOverview: true,
                )
              : GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: _collapseRoutePanel,
                  child: Container(
                    color: const Color(0xFFE9EEF5),
                    child: const Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            LucideIcons.mapPinned,
                            size: 50,
                            color: AppColors.primary,
                          ),
                          SizedBox(height: 9),
                          Text(
                            '选择起点和目的地后显示大致路线',
                            style: TextStyle(color: AppColors.secondaryText),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
        ),
        Positioned(
          left: 14,
          top: 12,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: .94),
              borderRadius: BorderRadius.circular(99),
              boxShadow: const [
                BoxShadow(color: Color(0x18000000), blurRadius: 14),
              ],
            ),
            child: const Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(LucideIcons.carFront, size: 17, color: AppColors.primary),
                SizedBox(width: 6),
                Text(
                  '自驾 · 路线仅供参考',
                  style: TextStyle(fontWeight: FontWeight.w700),
                ),
              ],
            ),
          ),
        ),
        if (routePlanning)
          const Positioned(top: 58, left: 14, child: _RoutePlanningBadge()),
        DraggableScrollableSheet(
          controller: routePanelController,
          initialChildSize: _routePanelInitialSize,
          minChildSize: _routePanelCollapsedSize,
          maxChildSize: _routePanelExpandedSize,
          snap: true,
          snapSizes: const [
            _routePanelCollapsedSize,
            _routePanelInitialSize,
            _routePanelExpandedSize,
          ],
          builder: (context, scrollController) {
            routePanelScrollController = scrollController;
            return Material(
              color: const Color(0xFFFAFAFB),
              elevation: 18,
              shadowColor: const Color(0x33000000),
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(28),
              ),
              clipBehavior: Clip.antiAlias,
              child: ListView(
                controller: scrollController,
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                padding: EdgeInsets.fromLTRB(
                  16,
                  0,
                  16,
                  MediaQuery.paddingOf(context).bottom + 22,
                ),
                children: [
                  InkWell(
                    onTap: _toggleRoutePanel,
                    child: Padding(
                      padding: const EdgeInsets.only(top: 9, bottom: 8),
                      child: Column(
                        children: [
                          Container(
                            width: 44,
                            height: 5,
                            decoration: BoxDecoration(
                              color: const Color(0xFFD2D5DA),
                              borderRadius: BorderRadius.circular(99),
                            ),
                          ),
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              const Expanded(
                                child: Text(
                                  '路线',
                                  style: TextStyle(
                                    fontSize: 22,
                                    fontWeight: FontWeight.w900,
                                  ),
                                ),
                              ),
                              IconButton(
                                tooltip: '收起路线卡片',
                                onPressed: _collapseRoutePanel,
                                icon: const Icon(LucideIcons.x, size: 25),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                  _RouteEstimateCard(
                    distance: _distanceText,
                    duration: _durationText,
                    waypointCount: waypoints.length,
                  ),
                  const SizedBox(height: 10),
                  const _ReferenceRouteNotice(),
                  const SizedBox(height: 12),
                  _NodeEndpointTile(
                    title: '出发点',
                    value: start?.name ?? '选择出发点',
                    subtitle: start?.address ?? '',
                    icon: LucideIcons.circleDot,
                    onTap: routePlanning
                        ? null
                        : () => _selectEndpoint(isStart: true),
                  ),
                  const Padding(
                    padding: EdgeInsets.only(left: 25),
                    child: SizedBox(
                      height: 14,
                      child: VerticalDivider(width: 1, thickness: 2),
                    ),
                  ),
                  ReorderableListView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    buildDefaultDragHandles: false,
                    itemCount: waypoints.length,
                    onReorderItem: _reorderWaypoint,
                    itemBuilder: (context, index) {
                      final item = waypoints[index];
                      return Padding(
                        key: ValueKey(item.localKey),
                        padding: const EdgeInsets.only(bottom: 9),
                        child: _WaypointTile(
                          index: index,
                          item: item,
                          typeLabel: _typeLabels[item.type] ?? '普通途经点',
                          onReplace: routePlanning
                              ? null
                              : () => _replaceWaypoint(index),
                          onEdit: routePlanning
                              ? null
                              : () => _editWaypoint(index),
                          onDelete: routePlanning
                              ? null
                              : () => _removeWaypoint(index),
                        ),
                      );
                    },
                  ),
                  OutlinedButton.icon(
                    onPressed: routePlanning || waypoints.length >= 20
                        ? null
                        : _addWaypoint,
                    icon: const Icon(LucideIcons.plus),
                    label: Text(
                      waypoints.length >= 20 ? '已达到 20 个停靠点上限' : '添加停靠点',
                    ),
                  ),
                  const Padding(
                    padding: EdgeInsets.only(left: 25),
                    child: SizedBox(
                      height: 14,
                      child: VerticalDivider(width: 1, thickness: 2),
                    ),
                  ),
                  _NodeEndpointTile(
                    title: '目的地',
                    value: end?.name ?? '选择目的地',
                    subtitle: end?.address ?? '',
                    icon: LucideIcons.flag,
                    onTap: routePlanning
                        ? null
                        : () => _selectEndpoint(isStart: false),
                  ),
                  const SizedBox(height: 18),
                  _buildRouteSheetActions(),
                ],
              ),
            );
          },
        ),
      ],
    ),
  );

  Widget _buildRouteSheetActions() => Row(
    children: [
      Expanded(
        child: OutlinedButton(
          style: OutlinedButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 8),
          ),
          onPressed: submitting || routePlanning ? null : _saveOnly,
          child: const FittedBox(
            fit: BoxFit.scaleDown,
            child: Text('保存草稿', maxLines: 1),
          ),
        ),
      ),
      const SizedBox(width: 12),
      Expanded(
        child: FilledButton(
          onPressed: submitting || routePlanning ? null : _next,
          child: FittedBox(
            fit: BoxFit.scaleDown,
            child: Text(routePlanning ? '正在规划路线…' : '下一步：填写行程信息', maxLines: 1),
          ),
        ),
      ),
    ],
  );

  Widget _buildPublishStep() => ListView(
    padding: const EdgeInsets.fromLTRB(20, 8, 20, 28),
    children: [
      const Text(
        '让其他人看懂这趟行程',
        style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900),
      ),
      const SizedBox(height: 6),
      const Text(
        '路线与发布信息分开保存，但仍属于同一个行程草稿。',
        style: TextStyle(color: AppColors.secondaryText),
      ),
      const SizedBox(height: 20),
      _TripCoverPicker(
        bytes: coverBytes,
        downloadUrl: coverDownloadUrl,
        onTap: submitting ? null : _pickCover,
      ),
      const SizedBox(height: 18),
      TextField(
        controller: title,
        maxLength: 128,
        decoration: const InputDecoration(
          labelText: '行程标题',
          hintText: '例如：广州到桂林周末自驾',
        ),
      ),
      const SizedBox(height: 14),
      _PlaceButton(
        label: '预计出发时间',
        value: startTime == null ? '请选择' : _formatChineseTime(startTime!),
        icon: LucideIcons.calendarClock,
        onTap: submitting ? null : _pickTime,
      ),
      const SizedBox(height: 14),
      _PlaceButton(
        label: '预计结束日期',
        value: expectedEndDate == null
            ? '请选择'
            : _formatChineseTime(expectedEndDate!, dateOnly: true),
        icon: LucideIcons.calendarCheck,
        onTap: submitting ? null : _pickExpectedEndDate,
      ),
      const SizedBox(height: 14),
      Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
        ),
        child: Row(
          children: [
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '车辆/人数上限',
                    style: TextStyle(fontWeight: FontWeight.w700),
                  ),
                  Text(
                    '发布后可在行程管理中继续调整',
                    style: TextStyle(fontSize: 12, color: AppColors.muted),
                  ),
                ],
              ),
            ),
            IconButton(
              onPressed: expectPeople > 1
                  ? () => setState(() => expectPeople--)
                  : null,
              icon: const Icon(LucideIcons.minus),
            ),
            Text(
              '$expectPeople',
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
            IconButton(
              onPressed: expectPeople < 50
                  ? () => setState(() => expectPeople++)
                  : null,
              icon: const Icon(LucideIcons.plus),
            ),
          ],
        ),
      ),
      const SizedBox(height: 14),
      TextField(
        controller: description,
        maxLength: 1000,
        minLines: 4,
        maxLines: 6,
        textAlignVertical: TextAlignVertical.top,
        decoration: const InputDecoration(
          labelText: '行程说明',
          hintText: '介绍路线特色、行程节奏和适合人群',
          alignLabelWithHint: true,
          floatingLabelBehavior: FloatingLabelBehavior.always,
          contentPadding: EdgeInsets.fromLTRB(16, 25, 16, 16),
        ),
      ),
      const SizedBox(height: 14),
      _PublishRouteSummary(
        startName: start?.name ?? '-',
        endName: end?.name ?? '-',
        waypointNames: waypoints.map((item) => item.location.name).toList(),
        distance: _distanceText,
        duration: _durationText,
      ),
    ],
  );

  Widget _buildRequirementsStep() => ListView(
    padding: const EdgeInsets.fromLTRB(20, 8, 20, 28),
    children: [
      const Text(
        '同行车辆与补充说明',
        style: TextStyle(fontSize: 24, fontWeight: FontWeight.w900),
      ),
      const SizedBox(height: 6),
      const Text(
        '车型可多选；选择“不限”后会自动取消其他车型。',
        style: TextStyle(color: AppColors.secondaryText),
      ),
      const SizedBox(height: 22),
      Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Row(
              children: [
                Icon(LucideIcons.carFront, color: AppColors.primary, size: 20),
                SizedBox(width: 8),
                Text('车辆要求', style: TextStyle(fontWeight: FontWeight.w800)),
              ],
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: const ['不限', 'SUV', '轿车', '越野车', '摩托车', 'MPV', '新能源']
                  .map(
                    (value) => FilterChip(
                      label: Text(value),
                      selected: vehicleRequirements.contains(value),
                      onSelected: submitting
                          ? null
                          : (selected) =>
                                _toggleVehicleRequirement(value, selected),
                    ),
                  )
                  .toList(),
            ),
          ],
        ),
      ),
      const SizedBox(height: 16),
      TextField(
        controller: budget,
        maxLength: 128,
        decoration: const InputDecoration(
          labelText: '费用预算（选填）',
          hintText: '例如：约 1500 元/人，油费路费 AA',
          prefixIcon: Icon(LucideIcons.walletCards),
        ),
      ),
      const SizedBox(height: 14),
      TextField(
        controller: notes,
        maxLength: 255,
        minLines: 4,
        maxLines: 6,
        textAlignVertical: TextAlignVertical.top,
        decoration: const InputDecoration(
          labelText: '注意事项（选填）',
          hintText: '例如：请提前检查车况，携带身份证和常用药品',
          alignLabelWithHint: true,
          floatingLabelBehavior: FloatingLabelBehavior.always,
          contentPadding: EdgeInsets.fromLTRB(16, 25, 16, 16),
        ),
      ),
      const SizedBox(height: 14),
      _PublishRouteSummary(
        startName: start?.name ?? '-',
        endName: end?.name ?? '-',
        waypointNames: waypoints.map((item) => item.location.name).toList(),
        distance: _distanceText,
        duration: _durationText,
      ),
    ],
  );

  void _toggleVehicleRequirement(String value, bool selected) {
    setState(() {
      if (value == '不限') {
        vehicleRequirements
          ..clear()
          ..add('不限');
        return;
      }
      vehicleRequirements.remove('不限');
      if (selected) {
        vehicleRequirements.add(value);
      } else {
        vehicleRequirements.remove(value);
      }
      if (vehicleRequirements.isEmpty) vehicleRequirements.add('不限');
    });
  }

  Widget _buildBottomActions() => SafeArea(
    top: false,
    child: Container(
      padding: const EdgeInsets.fromLTRB(18, 10, 18, 14),
      decoration: const BoxDecoration(
        color: Colors.white,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: step == 0
          ? Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                    ),
                    onPressed: submitting || routePlanning ? null : _saveOnly,
                    child: const FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Text('保存草稿', maxLines: 1),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: submitting || routePlanning ? null : _next,
                    child: Text(routePlanning ? '正在规划路线…' : '下一步：填写行程信息'),
                  ),
                ),
              ],
            )
          : Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: submitting || routePlanning
                        ? null
                        : step == 1
                        ? _nextToRequirements
                        : _publish,
                    child: Text(
                      submitting
                          ? '处理中…'
                          : step == 1
                          ? '下一步：设置同行要求'
                          : '发布行程',
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(
                      child: TextButton.icon(
                        onPressed: submitting ? null : _previous,
                        icon: const Icon(LucideIcons.arrowLeft, size: 18),
                        label: Text(step == 1 ? '返回节点规划' : '返回行程信息'),
                      ),
                    ),
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: submitting ? null : _saveOnly,
                        icon: const Icon(LucideIcons.filePenLine, size: 18),
                        label: const FittedBox(
                          fit: BoxFit.scaleDown,
                          child: Text('保存草稿', maxLines: 1),
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

class _WaypointEditorSheet extends StatefulWidget {
  const _WaypointEditorSheet({required this.current, required this.typeLabels});

  final _WaypointDraft current;
  final Map<String, String> typeLabels;

  @override
  State<_WaypointEditorSheet> createState() => _WaypointEditorSheetState();
}

class _WaypointEditorSheetState extends State<_WaypointEditorSheet> {
  late final TextEditingController remarkController;
  late final TextEditingController stayController;
  late String type;

  @override
  void initState() {
    super.initState();
    remarkController = TextEditingController(text: widget.current.remark);
    stayController = TextEditingController(
      text: '${widget.current.stayMinutes}',
    );
    type = widget.typeLabels.containsKey(widget.current.type)
        ? widget.current.type
        : 'NORMAL';
  }

  @override
  void dispose() {
    remarkController.dispose();
    stayController.dispose();
    super.dispose();
  }

  void _save() {
    FocusManager.instance.primaryFocus?.unfocus();
    final minutes = int.tryParse(stayController.text.trim()) ?? 0;
    Navigator.of(context).pop(
      widget.current.copyWith(
        type: type,
        stayMinutes: minutes.clamp(0, 1440).toInt(),
        remark: remarkController.text.trim(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final keyboardHeight = MediaQuery.viewInsetsOf(context).bottom;
    return AnimatedPadding(
      duration: const Duration(milliseconds: 160),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: keyboardHeight),
      child: Material(
        color: const Color(0xFFFAFAFB),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        clipBehavior: Clip.antiAlias,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 42,
                  height: 5,
                  decoration: BoxDecoration(
                    color: const Color(0xFFD2D5DA),
                    borderRadius: BorderRadius.circular(99),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              Text(
                widget.current.location.name,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 20),
              DropdownButtonFormField<String>(
                initialValue: type,
                decoration: const InputDecoration(labelText: '节点类型'),
                items: widget.typeLabels.entries
                    .map(
                      (entry) => DropdownMenuItem(
                        value: entry.key,
                        child: Text(entry.value),
                      ),
                    )
                    .toList(),
                onChanged: (value) {
                  if (value != null) setState(() => type = value);
                },
              ),
              const SizedBox(height: 16),
              TextField(
                controller: stayController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: '预计停留时间（分钟）',
                  hintText: '0 表示不设置',
                  contentPadding: EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 18,
                  ),
                ),
              ),
              const SizedBox(height: 18),
              TextField(
                controller: remarkController,
                maxLength: 255,
                minLines: 3,
                maxLines: 5,
                textAlignVertical: TextAlignVertical.top,
                decoration: const InputDecoration(
                  labelText: '节点备注（选填）',
                  hintText: '例如：在停车场入口集合',
                  alignLabelWithHint: true,
                  floatingLabelBehavior: FloatingLabelBehavior.always,
                  contentPadding: EdgeInsets.fromLTRB(16, 34, 16, 18),
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _save,
                  child: const Text('保存节点设置'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _WaypointDraft {
  _WaypointDraft({
    String? localKey,
    this.id,
    required this.location,
    this.type = 'NORMAL',
    this.stayMinutes = 0,
    this.remark = '',
  }) : localKey = localKey ?? UniqueKey().toString();

  final String localKey;
  final String? id;
  final LocationSelection location;
  final String type;
  final int stayMinutes;
  final String remark;

  _WaypointDraft copyWith({
    String? id,
    LocationSelection? location,
    String? type,
    int? stayMinutes,
    String? remark,
  }) => _WaypointDraft(
    localKey: localKey,
    id: id ?? this.id,
    location: location ?? this.location,
    type: type ?? this.type,
    stayMinutes: stayMinutes ?? this.stayMinutes,
    remark: remark ?? this.remark,
  );
}

class _RoutePlanningBadge extends StatelessWidget {
  const _RoutePlanningBadge();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 7),
    decoration: BoxDecoration(
      color: Colors.white.withValues(alpha: .96),
      borderRadius: BorderRadius.circular(99),
      boxShadow: const [BoxShadow(color: Color(0x18000000), blurRadius: 12)],
    ),
    child: const Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(
          width: 14,
          height: 14,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        SizedBox(width: 7),
        Text('正在重新规划大致路线', style: TextStyle(fontSize: 12)),
      ],
    ),
  );
}

class _ReferenceRouteNotice extends StatelessWidget {
  const _ReferenceRouteNotice();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(12),
    decoration: BoxDecoration(
      color: const Color(0xFFF4F8FF),
      borderRadius: BorderRadius.circular(14),
      border: Border.all(color: const Color(0xFFD9E8FF)),
    ),
    child: const Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(LucideIcons.info, size: 18, color: AppColors.primary),
        SizedBox(width: 9),
        Expanded(
          child: Text(
            '路线仅供参考，实际行驶可根据路况灵活调整，请按顺序到达行程节点。',
            style: TextStyle(fontSize: 12, color: AppColors.secondaryText),
          ),
        ),
      ],
    ),
  );
}

class _RouteEstimateCard extends StatelessWidget {
  const _RouteEstimateCard({
    required this.distance,
    required this.duration,
    required this.waypointCount,
  });

  final String distance;
  final String duration;
  final int waypointCount;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(16),
    ),
    child: Row(
      children: [
        Expanded(
          child: _EstimateItem(label: '预计总里程', value: distance),
        ),
        const SizedBox(height: 36, child: VerticalDivider()),
        Expanded(
          child: _EstimateItem(label: '预计驾驶', value: duration),
        ),
        const SizedBox(height: 36, child: VerticalDivider()),
        Expanded(
          child: _EstimateItem(label: '停靠点', value: '$waypointCount 个'),
        ),
      ],
    ),
  );
}

class _EstimateItem extends StatelessWidget {
  const _EstimateItem({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(label, style: const TextStyle(fontSize: 11, color: AppColors.muted)),
      const SizedBox(height: 3),
      Text(
        value,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(fontWeight: FontWeight.w800),
      ),
    ],
  );
}

class _NodeEndpointTile extends StatelessWidget {
  const _NodeEndpointTile({
    required this.title,
    required this.value,
    required this.subtitle,
    required this.icon,
    required this.onTap,
  });

  final String title;
  final String value;
  final String subtitle;
  final IconData icon;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(17),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(17),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(17),
          border: Border.all(color: AppColors.border),
        ),
        child: Row(
          children: [
            CircleAvatar(
              radius: 20,
              backgroundColor: AppColors.primarySoft,
              child: Icon(icon, size: 20, color: AppColors.primary),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppColors.muted,
                    ),
                  ),
                  Text(
                    value,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  if (subtitle.isNotEmpty)
                    Text(
                      subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 11,
                        color: AppColors.secondaryText,
                      ),
                    ),
                ],
              ),
            ),
            const Icon(LucideIcons.chevronRight, size: 18),
          ],
        ),
      ),
    ),
  );
}

class _WaypointTile extends StatelessWidget {
  const _WaypointTile({
    required this.index,
    required this.item,
    required this.typeLabel,
    required this.onReplace,
    required this.onEdit,
    required this.onDelete,
  });

  final int index;
  final _WaypointDraft item;
  final String typeLabel;
  final VoidCallback? onReplace;
  final VoidCallback? onEdit;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) => Container(
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(17),
      border: Border.all(color: AppColors.border),
    ),
    child: Row(
      children: [
        ReorderableDragStartListener(
          index: index,
          child: const Padding(
            padding: EdgeInsets.fromLTRB(14, 18, 8, 18),
            child: Icon(LucideIcons.gripVertical, color: AppColors.muted),
          ),
        ),
        Expanded(
          child: InkWell(
            onTap: onReplace,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.location.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    [
                      typeLabel,
                      if (item.stayMinutes > 0) '停留 ${item.stayMinutes} 分钟',
                      if (item.remark.isNotEmpty) item.remark,
                    ].join(' · '),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppColors.secondaryText,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        IconButton(
          tooltip: '节点设置',
          onPressed: onEdit,
          icon: const Icon(LucideIcons.settings2, size: 19),
        ),
        IconButton(
          tooltip: '删除节点',
          onPressed: onDelete,
          icon: const Icon(LucideIcons.trash2, size: 19),
        ),
      ],
    ),
  );
}

class _PublishRouteSummary extends StatelessWidget {
  const _PublishRouteSummary({
    required this.startName,
    required this.endName,
    required this.waypointNames,
    required this.distance,
    required this.duration,
  });

  final String startName;
  final String endName;
  final List<String> waypointNames;
  final String distance;
  final String duration;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(18),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('路线摘要', style: TextStyle(fontWeight: FontWeight.w900)),
        const SizedBox(height: 10),
        Text('$startName → $endName'),
        if (waypointNames.isNotEmpty) ...[
          const SizedBox(height: 5),
          Text(
            '途经：${waypointNames.join('、')}',
            style: const TextStyle(
              fontSize: 12,
              color: AppColors.secondaryText,
            ),
          ),
        ],
        const SizedBox(height: 8),
        Text(
          '预计 $distance · $duration',
          style: const TextStyle(fontSize: 12, color: AppColors.primary),
        ),
        const SizedBox(height: 10),
        const _ReferenceRouteNotice(),
      ],
    ),
  );
}

class _TripCoverPicker extends StatelessWidget {
  const _TripCoverPicker({
    required this.bytes,
    required this.downloadUrl,
    required this.onTap,
  });

  final Uint8List? bytes;
  final String downloadUrl;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final image = bytes != null
        ? Image.memory(bytes!, fit: BoxFit.cover)
        : downloadUrl.isNotEmpty
        ? Image.network(
            downloadUrl,
            fit: BoxFit.cover,
            errorBuilder: (_, _, _) => _placeholder(),
          )
        : _placeholder();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Row(
          children: [
            Text(
              '行程封面',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
            ),
            SizedBox(width: 6),
            Text('选填', style: TextStyle(color: AppColors.muted, fontSize: 12)),
          ],
        ),
        const SizedBox(height: 10),
        Material(
          color: Colors.transparent,
          borderRadius: BorderRadius.circular(20),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onTap,
            child: SizedBox(
              width: double.infinity,
              height: 176,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  image,
                  if (onTap != null)
                    Positioned(
                      right: 12,
                      bottom: 12,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 13,
                          vertical: 8,
                        ),
                        decoration: BoxDecoration(
                          color: const Color(0xCC10294F),
                          borderRadius: BorderRadius.circular(99),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            const Icon(
                              LucideIcons.imagePlus,
                              color: Colors.white,
                              size: 16,
                            ),
                            const SizedBox(width: 6),
                            Text(
                              bytes == null && downloadUrl.isEmpty
                                  ? '上传封面'
                                  : '更换封面',
                              style: const TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _placeholder() => Container(
    decoration: const BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFF90CAF9), Color(0xFF246ED8)],
      ),
    ),
    child: const Stack(
      children: [
        Positioned(
          right: -16,
          bottom: -20,
          child: Icon(
            LucideIcons.mountainSnow,
            size: 180,
            color: Color(0x50FFFFFF),
          ),
        ),
        Center(
          child: Text(
            '让同行先看见这段旅程',
            style: TextStyle(
              color: Colors.white,
              fontSize: 17,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ],
    ),
  );
}

class _PlaceButton extends StatelessWidget {
  const _PlaceButton({
    required this.label,
    required this.value,
    required this.icon,
    required this.onTap,
  });

  final String label;
  final String value;
  final IconData icon;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(18),
    child: Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: [
          Icon(icon, color: AppColors.primary),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(fontSize: 12, color: AppColors.muted),
                ),
                Text(
                  value,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ),
          const Icon(LucideIcons.chevronRight),
        ],
      ),
    ),
  );
}
