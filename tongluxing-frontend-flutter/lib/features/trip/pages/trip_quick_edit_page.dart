import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../home/pages/search_location_page.dart';

/// 行程群内的编辑页，可重新选择起终点并增删、调整途经点。
class TripQuickEditPage extends StatefulWidget {
  const TripQuickEditPage({required this.tripId, super.key});
  final String tripId;

  @override
  State<TripQuickEditPage> createState() => _TripQuickEditPageState();
}

class _TripQuickEditPageState extends State<TripQuickEditPage> {
  final title = TextEditingController();
  final description = TextEditingController();
  final notes = TextEditingController();
  final requirements = TextEditingController();
  Map<String, dynamic>? raw;
  DateTime? departureTime;
  DateTime? expectedEndDate;
  LocationSelection? startLocation;
  LocationSelection? endLocation;
  List<LocationSelection> waypoints = const [];
  int maxVehicleCount = 5;
  int expectedPeople = 5;
  bool loading = true;
  bool saving = false;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    title.dispose();
    description.dispose();
    notes.dispose();
    requirements.dispose();
    super.dispose();
  }

  Future<void> load() async {
    try {
      final value = await TripService(
        context.read<AppSession>().api,
      ).rawDetail(widget.tripId);
      raw = value;
      title.text = value['title']?.toString() ?? '';
      description.text = value['description']?.toString() ?? '';
      departureTime = DateTime.tryParse(
        value['departureTime']?.toString() ?? '',
      );
      final estimatedDays = (value['estimatedDays'] as num?)?.toInt() ?? 1;
      if (departureTime != null) {
        expectedEndDate = departureTime!.add(Duration(days: estimatedDays - 1));
      }
      expectedPeople = (value['expectedPeople'] as num?)?.toInt() ?? 5;
      notes.text = value['remark']?.toString() ?? '';
      requirements.text =
          (value['vehicleRequirements'] as List? ?? const ['不限'])
              .map((item) => item.toString())
              .join('、');
      maxVehicleCount = (value['maxVehicleCount'] as num?)?.toInt() ?? 5;
      final rawStart = value['startLocation'];
      final rawEnd = value['endLocation'];
      startLocation = rawStart is Map
          ? LocationSelection.fromJson(Map<String, dynamic>.from(rawStart))
          : null;
      endLocation = rawEnd is Map
          ? LocationSelection.fromJson(Map<String, dynamic>.from(rawEnd))
          : null;
      waypoints = (value['waypoints'] as List? ?? const [])
          .whereType<Map>()
          .map(
            (item) =>
                LocationSelection.fromJson(Map<String, dynamic>.from(item)),
          )
          .toList();
      error = null;
    } catch (e) {
      error = '$e';
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> pickTime() async {
    final current =
        departureTime ?? DateTime.now().add(const Duration(hours: 1));
    final date = await showDatePicker(
      context: context,
      initialDate: current,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
    );
    if (date == null || !mounted) return;
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(current),
    );
    if (time == null) return;
    setState(
      () => departureTime = DateTime(
        date.year,
        date.month,
        date.day,
        time.hour,
        time.minute,
      ),
    );
  }

  Future<void> pickExpectedEndDate() async {
    final start = departureTime;
    if (start == null) {
      message('请先选择预计出发时间');
      return;
    }
    final value = await showDatePicker(
      context: context,
      helpText: '选择预计结束日期',
      cancelText: '取消',
      confirmText: '确定',
      initialDate: expectedEndDate ?? start,
      firstDate: DateTime(start.year, start.month, start.day),
      lastDate: DateTime(start.year + 1, start.month, start.day),
    );
    if (value != null && mounted) setState(() => expectedEndDate = value);
  }

  Future<LocationSelection?> pickLocation() =>
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

  Future<void> selectEndpoint({required bool start}) async {
    final selected = await pickLocation();
    if (selected == null || !mounted) return;
    final other = start ? endLocation : startLocation;
    if (other != null && sameLocation(selected, other)) {
      message('起点和目的地不能相同');
      return;
    }
    if (waypoints.any((item) => sameLocation(item, selected))) {
      message('该地点已经是途经点，请先删除对应途经点');
      return;
    }
    setState(() {
      if (start) {
        startLocation = selected;
      } else {
        endLocation = selected;
      }
    });
  }

  Future<void> addWaypoint() async {
    if (waypoints.length >= 20) {
      message('途经点最多 20 个');
      return;
    }
    final selected = await pickLocation();
    if (selected == null || !mounted) return;
    if ((startLocation != null && sameLocation(selected, startLocation!)) ||
        (endLocation != null && sameLocation(selected, endLocation!)) ||
        waypoints.any((item) => sameLocation(item, selected))) {
      message('路线节点不能重复');
      return;
    }
    setState(() => waypoints = [...waypoints, selected]);
  }

  Future<void> replaceWaypoint(int index) async {
    final selected = await pickLocation();
    if (selected == null || !mounted) return;
    final duplicate =
        (startLocation != null && sameLocation(selected, startLocation!)) ||
        (endLocation != null && sameLocation(selected, endLocation!)) ||
        waypoints.asMap().entries.any(
          (entry) => entry.key != index && sameLocation(entry.value, selected),
        );
    if (duplicate) {
      message('路线节点不能重复');
      return;
    }
    final next = [...waypoints]..[index] = selected;
    setState(() => waypoints = next);
  }

  bool sameLocation(LocationSelection left, LocationSelection right) {
    if (left.poiId?.isNotEmpty == true && left.poiId == right.poiId) {
      return true;
    }
    return (left.latitude - right.latitude).abs() < 0.00001 &&
        (left.longitude - right.longitude).abs() < 0.00001;
  }

  void message(String value) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value)));
  }

  Future<void> save() async {
    if (raw == null ||
        title.text.trim().isEmpty ||
        departureTime == null ||
        startLocation == null ||
        endLocation == null) {
      message('请完整填写标题、时间、起点和目的地');
      return;
    }
    setState(() => saving = true);
    try {
      await TripService(context.read<AppSession>().api).updateFromRaw(
        widget.tripId,
        raw!,
        title: title.text.trim(),
        description: description.text.trim(),
        departureTime: departureTime!.toIso8601String(),
        maxVehicleCount: maxVehicleCount,
        expectedPeople: expectedPeople,
        estimatedDays: expectedEndDate == null
            ? 1
            : expectedEndDate!
                      .difference(
                        DateTime(
                          departureTime!.year,
                          departureTime!.month,
                          departureTime!.day,
                        ),
                      )
                      .inDays +
                  1,
        vehicleRequirements: requirements.text
            .split(RegExp(r'[,，、]'))
            .map((item) => item.trim())
            .where((item) => item.isNotEmpty)
            .toList(),
        remark: notes.text.trim(),
        startLocation: startLocation!.toJson(),
        endLocation: endLocation!.toJson(),
        waypoints: waypoints
            .asMap()
            .entries
            .map(
              (entry) => {...entry.value.toJson(), 'sortOrder': entry.key + 1},
            )
            .toList(),
      );
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  String get timeText {
    final value = departureTime;
    if (value == null) return '请选择出发时间';
    String two(int v) => v.toString().padLeft(2, '0');
    return '${value.year}年${value.month}月${value.day}日 ${two(value.hour)}:${two(value.minute)}';
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('修改行程')),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : error != null
        ? Center(
            child: FilledButton(onPressed: load, child: const Text('重新加载')),
          )
        : ListView(
            padding: const EdgeInsets.all(18),
            children: [
              TextField(
                controller: title,
                maxLength: 128,
                decoration: const InputDecoration(labelText: '行程标题'),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: description,
                minLines: 4,
                maxLines: 7,
                maxLength: 1000,
                textAlignVertical: TextAlignVertical.top,
                decoration: const InputDecoration(
                  labelText: '行程说明',
                  alignLabelWithHint: true,
                  floatingLabelBehavior: FloatingLabelBehavior.always,
                  contentPadding: EdgeInsets.fromLTRB(16, 25, 16, 16),
                ),
              ),
              const SizedBox(height: 14),
              const Text(
                '路线规划',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 10),
              _RouteLocationTile(
                label: '起点',
                location: startLocation,
                color: const Color(0xFF2EBD85),
                onTap: () => selectEndpoint(start: true),
              ),
              const SizedBox(height: 10),
              for (final entry in waypoints.asMap().entries) ...[
                _RouteLocationTile(
                  label: '途经点 ${entry.key + 1}',
                  location: entry.value,
                  color: const Color(0xFFFFA928),
                  onTap: () => replaceWaypoint(entry.key),
                  onDelete: () {
                    final next = [...waypoints]..removeAt(entry.key);
                    setState(() => waypoints = next);
                  },
                ),
                const SizedBox(height: 10),
              ],
              if (waypoints.length < 20)
                OutlinedButton.icon(
                  onPressed: addWaypoint,
                  icon: const Icon(Icons.add_location_alt_outlined),
                  label: const Text('添加途经点'),
                ),
              const SizedBox(height: 10),
              _RouteLocationTile(
                label: '目的地',
                location: endLocation,
                color: AppColors.primary,
                onTap: () => selectEndpoint(start: false),
              ),
              const SizedBox(height: 18),
              ListTile(
                tileColor: const Color(0xFFF5F8FF),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                title: const Text('预计出发时间'),
                subtitle: Text(timeText),
                trailing: const Icon(Icons.chevron_right),
                onTap: pickTime,
              ),
              const SizedBox(height: 14),
              ListTile(
                tileColor: const Color(0xFFF5F8FF),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14),
                ),
                title: const Text('预计结束日期'),
                subtitle: Text(
                  expectedEndDate == null
                      ? '请选择'
                      : '${expectedEndDate!.year}年${expectedEndDate!.month}月${expectedEndDate!.day}日',
                ),
                trailing: const Icon(Icons.chevron_right),
                onTap: pickExpectedEndDate,
              ),
              const SizedBox(height: 14),
              TextField(
                controller: requirements,
                maxLength: 128,
                decoration: const InputDecoration(
                  labelText: '同行要求',
                  hintText: '多个要求可用顿号分隔',
                ),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: notes,
                maxLength: 255,
                decoration: const InputDecoration(labelText: '行程备注'),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      '车辆 / 人数上限',
                      style: TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ),
                  IconButton(
                    onPressed: maxVehicleCount > 1
                        ? () => setState(() => maxVehicleCount--)
                        : null,
                    icon: const Icon(Icons.remove),
                  ),
                  Text(
                    '$maxVehicleCount',
                    style: const TextStyle(fontWeight: FontWeight.w900),
                  ),
                  IconButton(
                    onPressed: maxVehicleCount < 50
                        ? () => setState(() => maxVehicleCount++)
                        : null,
                    icon: const Icon(Icons.add),
                  ),
                ],
              ),
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      '预计同行人数',
                      style: TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ),
                  IconButton(
                    onPressed: expectedPeople > 1
                        ? () => setState(() => expectedPeople--)
                        : null,
                    icon: const Icon(Icons.remove),
                  ),
                  Text(
                    '$expectedPeople',
                    style: const TextStyle(fontWeight: FontWeight.w900),
                  ),
                  IconButton(
                    onPressed: expectedPeople < 50
                        ? () => setState(() => expectedPeople++)
                        : null,
                    icon: const Icon(Icons.add),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              const Text(
                '保存时会根据新的起点、目的地和途经点重新规划路线，并在群内发送更新通知。',
                style: TextStyle(
                  color: AppColors.muted,
                  fontSize: 12,
                  height: 1.5,
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                height: 52,
                child: FilledButton(
                  onPressed: saving ? null : save,
                  child: Text(saving ? '保存中…' : '保存修改'),
                ),
              ),
            ],
          ),
  );
}

class _RouteLocationTile extends StatelessWidget {
  const _RouteLocationTile({
    required this.label,
    required this.location,
    required this.color,
    required this.onTap,
    this.onDelete,
  });

  final String label;
  final LocationSelection? location;
  final Color color;
  final VoidCallback onTap;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) => Material(
    color: const Color(0xFFF5F8FF),
    borderRadius: BorderRadius.circular(14),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 8, 12),
        child: Row(
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(color: color, shape: BoxShape.circle),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: const TextStyle(
                      color: AppColors.muted,
                      fontSize: 11,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    location?.name ?? '请选择地点',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  if (location?.address.trim().isNotEmpty == true)
                    Text(
                      location!.address,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AppColors.muted,
                        fontSize: 11,
                      ),
                    ),
                ],
              ),
            ),
            if (onDelete != null)
              IconButton(
                tooltip: '删除途经点',
                onPressed: onDelete,
                icon: const Icon(Icons.close, size: 19),
              )
            else
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 8),
                child: Icon(Icons.chevron_right, color: AppColors.muted),
              ),
          ],
        ),
      ),
    ),
  );
}
