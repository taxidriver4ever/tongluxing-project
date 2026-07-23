import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/routes.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../home/pages/search_location_page.dart';
import '../widgets/trip_route_preview.dart';

class TripCreatePage extends StatefulWidget {
  const TripCreatePage({this.draft, super.key});
  final TripDraftModel? draft;
  @override
  State<TripCreatePage> createState() => _TripCreatePageState();
}

class _TripCreatePageState extends State<TripCreatePage> {
  final pageController = PageController();
  final title = TextEditingController();
  final description = TextEditingController();
  int step = 0;
  int expectPeople = 5;
  DateTime? startTime;
  LocationSelection? start;
  LocationSelection? end;
  final List<LocationSelection> waypoints = [];
  bool submitting = false;
  String? draftId;
  TripDraftRouteModel? route;
  final List<String> persistedWaypointIds = [];

  @override
  void initState() {
    super.initState();
    final draft = widget.draft;
    if (draft == null) return;
    draftId = draft.id;
    title.text = draft.title;
    description.text = draft.description;
    startTime = DateTime.tryParse(draft.startTime ?? '');
    start = draft.startLocation;
    end = draft.destination;
    expectPeople = draft.expectPeople;
    waypoints.addAll(draft.waypoints.map((e) => e.location));
    persistedWaypointIds.addAll(draft.waypoints.map((e) => e.id));
    route = draft.route;
  }

  @override
  void dispose() {
    pageController.dispose();
    title.dispose();
    description.dispose();
    super.dispose();
  }

  Future<LocationSelection?> _pickLocation() =>
      Navigator.push<LocationSelection>(
        context,
        MaterialPageRoute(builder: (_) => const SearchLocationPage()),
      );

  Future<void> _selectLocation(bool isStart) async {
    final value = await _pickLocation();
    if (value == null || !mounted) return;
    final conflict = isStart
        ? _conflictForStart(value)
        : _conflictForEnd(value);
    if (conflict != null) {
      _showMessage(conflict);
      return;
    }
    setState(() => isStart ? start = value : end = value);
  }

  Future<void> _addWaypoint() async {
    final value = await _pickLocation();
    if (value == null || !mounted) return;
    final conflict = _conflictForWaypoint(value);
    if (conflict != null) {
      _showMessage(conflict);
      return;
    }
    setState(() => waypoints.add(value));
  }

  String? _conflictForStart(LocationSelection value) {
    if (_sameLocation(value, end)) return '起点不能与终点选择同一地点';
    if (waypoints.any((point) => _sameLocation(value, point))) {
      return '起点不能与经停点选择同一地点';
    }
    return null;
  }

  String? _conflictForEnd(LocationSelection value) {
    if (_sameLocation(value, start)) return '终点不能与起点选择同一地点';
    if (waypoints.any((point) => _sameLocation(value, point))) {
      return '终点不能与经停点选择同一地点';
    }
    return null;
  }

  String? _conflictForWaypoint(LocationSelection value) {
    if (_sameLocation(value, start)) return '经停点不能与起点选择同一地点';
    if (_sameLocation(value, end)) return '经停点不能与终点选择同一地点';
    if (waypoints.any((point) => _sameLocation(value, point))) {
      return '经停点不能重复选择';
    }
    return null;
  }

  String? _routeConflictMessage() {
    if (_sameLocation(start, end)) return '起点不能与终点选择同一地点';
    for (var i = 0; i < waypoints.length; i++) {
      final point = waypoints[i];
      if (_sameLocation(point, start)) return '经停点不能与起点选择同一地点';
      if (_sameLocation(point, end)) return '经停点不能与终点选择同一地点';
      for (var j = i + 1; j < waypoints.length; j++) {
        if (_sameLocation(point, waypoints[j])) return '经停点不能重复选择';
      }
    }
    return null;
  }

  bool _sameLocation(LocationSelection? left, LocationSelection? right) {
    if (left == null || right == null) return false;
    final coordinatesMatch =
        (left.latitude - right.latitude).abs() <= 0.000001 &&
        (left.longitude - right.longitude).abs() <= 0.000001;
    if (coordinatesMatch) return true;
    final leftAddress = _normalizeLocationText(left.address);
    final rightAddress = _normalizeLocationText(right.address);
    return _normalizeLocationText(left.name) ==
            _normalizeLocationText(right.name) &&
        leftAddress.isNotEmpty &&
        leftAddress == rightAddress;
  }

  String _normalizeLocationText(String value) =>
      value.trim().replaceAll(RegExp(r'\s+'), '').toLowerCase();

  void _showMessage(String message) => ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(message)));

  Future<void> _pickTime() async {
    final date = await showDatePicker(
      context: context,
      initialDate: DateTime.now().add(const Duration(days: 1)),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 8, minute: 0),
    );
    if (time != null) {
      setState(
        () => startTime = DateTime(
          date.year,
          date.month,
          date.day,
          time.hour,
          time.minute,
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

  Map<String, dynamic> _draftBody({bool includeRoute = false}) => {
    'title': title.text.trim().isEmpty ? null : title.text.trim(),
    'startTime': startTime == null ? null : _formatTime(startTime!),
    if (includeRoute) 'startLocation': start?.toJson(),
    if (includeRoute) 'destination': end?.toJson(),
    'description': description.text.trim(),
    'expectPeople': expectPeople,
    'durationDays': 1,
  };

  bool _validateStep() {
    final message = switch (step) {
      0 when title.text.trim().isEmpty => '请填写行程标题',
      0 when startTime == null => '请选择出发时间',
      1 when start == null => '请选择起点',
      1 when end == null => '请选择终点',
      1 when _routeConflictMessage() != null => _routeConflictMessage(),
      _ => null,
    };
    if (message != null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
      return false;
    }
    return true;
  }

  Future<void> _next() async {
    if (!_validateStep()) return;
    if (step == 0 && draftId == null) {
      setState(() => submitting = true);
      try {
        final draft = await TripService(
          context.read<AppSession>().api,
        ).createDraft(_draftBody());
        draftId = draft.id;
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('$e')));
        }
        setState(() => submitting = false);
        return;
      }
      if (mounted) setState(() => submitting = false);
    }
    if (step == 2) {
      final saved = await _saveDraft(plan: true);
      if (!saved) return;
    }
    if (step < 3) {
      setState(() => step++);
      await pageController.nextPage(
        duration: const Duration(milliseconds: 260),
        curve: Curves.easeOut,
      );
    } else {
      await _publish();
    }
  }

  Future<void> _publish() async {
    final id = draftId;
    if (id == null) return;
    setState(() => submitting = true);
    try {
      final service = TripService(context.read<AppSession>().api);
      if (!await _saveDraft(plan: true, manageSubmitting: false)) return;
      if (!await _confirmTimeConflict(service)) return;
      await service.publishDraft(id);
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('行程发布成功，群聊已创建')));
      Navigator.pushNamedAndRemoveUntil(context, AppRoutes.home, (_) => false);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => submitting = false);
    }
  }

  Future<bool> _confirmTimeConflict(TripService service) async {
    final departure = startTime;
    if (departure == null) return true;
    final result = await service.checkTimeConflict(
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

  Future<bool> _saveDraft({
    bool plan = false,
    bool manageSubmitting = true,
  }) async {
    final id = draftId;
    if (id == null) return false;
    final conflict = _routeConflictMessage();
    if (conflict != null) {
      _showMessage(conflict);
      return false;
    }
    if (manageSubmitting) setState(() => submitting = true);
    try {
      final service = TripService(context.read<AppSession>().api);
      await service.updateDraft(id, _draftBody(includeRoute: true));
      for (final waypointId in persistedWaypointIds) {
        await service.deleteWaypoint(id, waypointId);
      }
      persistedWaypointIds.clear();
      for (var i = 0; i < waypoints.length; i++) {
        final saved = await service.addWaypoint(id, waypoints[i], sort: i + 1);
        persistedWaypointIds.add(saved.id);
      }
      if (plan) {
        final json = await service.planRoute(id);
        route = TripDraftRouteModel.fromJson(json);
      }
      if (mounted) {
        setState(() {});
      }
      return true;
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
      return false;
    } finally {
      if (manageSubmitting && mounted) setState(() => submitting = false);
    }
  }

  Future<void> _saveOnly() async {
    if (!await _saveDraft(plan: true)) return;
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('已存入我的草稿')));
    Navigator.pop(context, true);
  }

  List<LocationSelection> get _routePoints => [?start, ...waypoints, ?end];

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('创建行程')),
    body: Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 22),
          child: Row(
            children: List.generate(
              4,
              (i) => Expanded(
                child: Container(
                  height: 5,
                  margin: EdgeInsets.only(right: i == 3 ? 0 : 8),
                  decoration: BoxDecoration(
                    color: i <= step ? AppColors.primary : AppColors.border,
                    borderRadius: BorderRadius.circular(5),
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
              _StepPage(
                title: '填写基础信息',
                subtitle: '内容会先保存为草稿，下次可以继续编辑',
                children: [
                  TextField(
                    controller: title,
                    maxLength: 128,
                    decoration: const InputDecoration(
                      labelText: '行程标题',
                      hintText: '例如：318川藏线自驾',
                    ),
                  ),
                  const SizedBox(height: 16),
                  _PlaceButton(
                    label: '出发时间',
                    value: startTime == null ? '请选择' : _formatTime(startTime!),
                    icon: LucideIcons.calendar,
                    onTap: _pickTime,
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: description,
                    maxLength: 1000,
                    maxLines: 4,
                    decoration: const InputDecoration(
                      labelText: '行程描述',
                      hintText: '介绍路线、节奏和同行要求',
                      alignLabelWithHint: true,
                      contentPadding: EdgeInsets.fromLTRB(16, 22, 16, 16),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      const Expanded(child: Text('预计人数')),
                      IconButton(
                        onPressed: expectPeople > 1
                            ? () => setState(() => expectPeople--)
                            : null,
                        icon: const Icon(LucideIcons.minus),
                      ),
                      Text('$expectPeople 人'),
                      IconButton(
                        onPressed: expectPeople < 20
                            ? () => setState(() => expectPeople++)
                            : null,
                        icon: const Icon(LucideIcons.plus),
                      ),
                    ],
                  ),
                ],
              ),
              _StepPage(
                title: '规划路线',
                subtitle: '所有地点选择统一进入地图搜索页',
                children: [
                  _PlaceButton(
                    label: '起点',
                    value: start?.name ?? '选择起点',
                    icon: LucideIcons.mapPin,
                    onTap: () => _selectLocation(true),
                  ),
                  const SizedBox(height: 14),
                  _PlaceButton(
                    label: '终点',
                    value: end?.name ?? '选择终点',
                    icon: LucideIcons.flag,
                    onTap: () => _selectLocation(false),
                  ),
                  const SizedBox(height: 22),
                  Container(
                    height: 230,
                    decoration: BoxDecoration(
                      color: AppColors.primarySoft,
                      borderRadius: BorderRadius.circular(22),
                    ),
                    child: const Center(
                      child: Icon(
                        LucideIcons.route,
                        size: 54,
                        color: AppColors.primary,
                      ),
                    ),
                  ),
                ],
              ),
              _StepPage(
                title: '添加经停点',
                subtitle: '可添加集合、休息、住宿和打卡点',
                children: [
                  ...waypoints.asMap().entries.map(
                    (entry) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: _WaypointTile(
                        index: entry.key,
                        location: entry.value,
                        onDelete: () =>
                            setState(() => waypoints.removeAt(entry.key)),
                      ),
                    ),
                  ),
                  OutlinedButton.icon(
                    onPressed: waypoints.length < 5 ? _addWaypoint : null,
                    icon: const Icon(LucideIcons.plus),
                    label: const Text('添加经停点'),
                  ),
                ],
              ),
              _StepPage(
                title: '确认发布',
                subtitle: '可以存入草稿，也可以确认后发布行程',
                children: [
                  TripRoutePreview(points: _routePoints, route: route),
                  const SizedBox(height: 18),
                  _Summary(label: '标题', value: title.text.trim()),
                  _Summary(label: '路线', value: '${start?.name} → ${end?.name}'),
                  _Summary(
                    label: '经停点',
                    value: waypoints.isEmpty
                        ? '无'
                        : waypoints.map((e) => e.name).join('、'),
                  ),
                  _Summary(label: '人数', value: '$expectPeople 人'),
                  _Summary(label: '草稿编号', value: draftId ?? '保存中'),
                ],
              ),
            ],
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(22, 10, 22, 18),
            child: step == 3
                ? Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton(
                          onPressed: submitting ? null : _publish,
                          child: Text(submitting ? '处理中…' : '发布行程'),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Expanded(
                            child: TextButton.icon(
                              onPressed: submitting ? null : _previous,
                              icon: const Icon(LucideIcons.arrowLeft, size: 18),
                              label: const Text('上一步'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: OutlinedButton.icon(
                              onPressed: submitting ? null : _saveOnly,
                              icon: const Icon(
                                LucideIcons.filePenLine,
                                size: 18,
                              ),
                              label: const Text('存入草稿'),
                            ),
                          ),
                        ],
                      ),
                    ],
                  )
                : Row(
                    children: [
                      if (step > 0) ...[
                        Expanded(
                          child: OutlinedButton(
                            onPressed: submitting ? null : _previous,
                            child: const Text('上一步'),
                          ),
                        ),
                        const SizedBox(width: 12),
                      ],
                      Expanded(
                        flex: 2,
                        child: FilledButton(
                          onPressed: submitting ? null : _next,
                          child: Text(submitting ? '处理中…' : '保存并继续'),
                        ),
                      ),
                    ],
                  ),
          ),
        ),
      ],
    ),
  );

  void _previous() {
    setState(() => step--);
    pageController.previousPage(
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOut,
    );
  }
}

class _StepPage extends StatelessWidget {
  const _StepPage({
    required this.title,
    required this.subtitle,
    required this.children,
  });
  final String title, subtitle;
  final List<Widget> children;
  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(22),
    children: [
      Text(
        title,
        style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800),
      ),
      const SizedBox(height: 6),
      Text(subtitle, style: const TextStyle(color: AppColors.secondaryText)),
      const SizedBox(height: 28),
      ...children,
    ],
  );
}

class _PlaceButton extends StatelessWidget {
  const _PlaceButton({
    required this.label,
    required this.value,
    required this.icon,
    required this.onTap,
  });
  final String label, value;
  final IconData icon;
  final VoidCallback onTap;
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

class _WaypointTile extends StatelessWidget {
  const _WaypointTile({
    required this.index,
    required this.location,
    required this.onDelete,
  });
  final int index;
  final LocationSelection location;
  final VoidCallback onDelete;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(18),
    ),
    child: Row(
      children: [
        const Icon(LucideIcons.gripVertical, color: AppColors.muted),
        const SizedBox(width: 8),
        Expanded(child: Text('${index + 1}. ${location.name} · 休息点')),
        IconButton(onPressed: onDelete, icon: const Icon(LucideIcons.trash2)),
      ],
    ),
  );
}

class _Summary extends StatelessWidget {
  const _Summary({required this.label, required this.value});
  final String label, value;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(vertical: 16),
    decoration: const BoxDecoration(
      border: Border(bottom: BorderSide(color: AppColors.border)),
    ),
    child: Row(
      children: [
        SizedBox(
          width: 76,
          child: Text(label, style: const TextStyle(color: AppColors.muted)),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
        ),
      ],
    ),
  );
}
