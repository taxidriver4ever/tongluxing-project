import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../home/pages/search_location_page.dart';

class TripDiscoveryFilter {
  const TripDiscoveryFilter({
    this.startCity = '',
    this.destination = '',
    this.departureFrom,
    this.departureTo,
    this.vehicleType = '',
    this.minimumRemainingSeats = 1,
  });
  final String startCity;
  final String destination;
  final DateTime? departureFrom;
  final DateTime? departureTo;
  final String vehicleType;
  final int minimumRemainingSeats;

  bool get active =>
      startCity.isNotEmpty ||
      destination.isNotEmpty ||
      departureFrom != null ||
      departureTo != null ||
      vehicleType.isNotEmpty ||
      minimumRemainingSeats > 1;
}

class TripDiscoveryFilterSheet extends StatefulWidget {
  const TripDiscoveryFilterSheet({required this.initial, super.key});
  final TripDiscoveryFilter initial;

  static Future<TripDiscoveryFilter?> show(
    BuildContext context,
    TripDiscoveryFilter initial,
  ) => showModalBottomSheet<TripDiscoveryFilter>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (_) => TripDiscoveryFilterSheet(initial: initial),
  );

  @override
  State<TripDiscoveryFilterSheet> createState() =>
      _TripDiscoveryFilterSheetState();
}

class _TripDiscoveryFilterSheetState extends State<TripDiscoveryFilterSheet> {
  late final TextEditingController start;
  late final TextEditingController end;
  late DateTime? from = widget.initial.departureFrom;
  late DateTime? to = widget.initial.departureTo;
  late String vehicle = widget.initial.vehicleType;
  late int seats = widget.initial.minimumRemainingSeats;

  @override
  void initState() {
    super.initState();
    start = TextEditingController(text: widget.initial.startCity);
    end = TextEditingController(text: widget.initial.destination);
  }

  @override
  void dispose() {
    start.dispose();
    end.dispose();
    super.dispose();
  }

  Future<void> pick(bool first) async {
    final value = await showDatePicker(
      context: context,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)),
      initialDate: (first ? from : to) ?? DateTime.now(),
    );
    if (value != null) setState(() => first ? from = value : to = value);
  }

  Future<void> pickLocation(bool first) async {
    FocusScope.of(context).unfocus();
    final value = await showModalBottomSheet<LocationSelection>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => const FractionallySizedBox(
        heightFactor: 0.9,
        child: SearchLocationPage(embedded: true, autofocus: true),
      ),
    );
    if (value == null || !mounted) return;
    setState(() => (first ? start : end).text = value.name);
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
    child: Container(
      padding: const EdgeInsets.fromLTRB(22, 12, 22, 26),
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: const Color(0xFFD7DFEA),
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              const Text(
                '筛选行程',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: start,
                      readOnly: true,
                      showCursor: false,
                      onTap: () => pickLocation(true),
                      decoration: const InputDecoration(
                        labelText: '出发城市',
                        prefixIcon: Icon(LucideIcons.mapPin),
                        suffixIcon: Icon(LucideIcons.search, size: 18),
                        hintText: '搜索选择',
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextField(
                      controller: end,
                      readOnly: true,
                      showCursor: false,
                      onTap: () => pickLocation(false),
                      decoration: const InputDecoration(
                        labelText: '目的地',
                        prefixIcon: Icon(LucideIcons.flag),
                        suffixIcon: Icon(LucideIcons.search, size: 18),
                        hintText: '搜索选择',
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(child: _DateField('最早出发', from, () => pick(true))),
                  const SizedBox(width: 12),
                  Expanded(child: _DateField('最晚出发', to, () => pick(false))),
                ],
              ),
              const SizedBox(height: 18),
              const Text('车辆要求', style: TextStyle(fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: ['', 'SUV', '轿车', '越野车']
                    .map(
                      (value) => ChoiceChip(
                        label: Text(value.isEmpty ? '不限' : value),
                        selected: vehicle == value,
                        onSelected: (_) => setState(() => vehicle = value),
                      ),
                    )
                    .toList(),
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      '至少剩余名额',
                      style: TextStyle(fontWeight: FontWeight.w700),
                    ),
                  ),
                  IconButton(
                    onPressed: seats > 1 ? () => setState(() => seats--) : null,
                    icon: const Icon(LucideIcons.minus),
                  ),
                  Text(
                    '$seats',
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  IconButton(
                    onPressed: seats < 9 ? () => setState(() => seats++) : null,
                    icon: const Icon(LucideIcons.plus),
                  ),
                ],
              ),
              const SizedBox(height: 18),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () =>
                          Navigator.pop(context, const TripDiscoveryFilter()),
                      child: const Text('重置'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton(
                      onPressed: () => Navigator.pop(
                        context,
                        TripDiscoveryFilter(
                          startCity: start.text.trim(),
                          destination: end.text.trim(),
                          departureFrom: from,
                          departureTo: to,
                          vehicleType: vehicle,
                          minimumRemainingSeats: seats,
                        ),
                      ),
                      child: const Text('查看匹配行程'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _DateField extends StatelessWidget {
  const _DateField(this.label, this.value, this.onTap);
  final String label;
  final DateTime? value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(16),
    child: Ink(
      height: 58,
      padding: const EdgeInsets.symmetric(horizontal: 14),
      decoration: BoxDecoration(
        color: AppColors.primarySoft,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          const Icon(
            LucideIcons.calendarDays,
            size: 18,
            color: AppColors.primary,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              value == null ? label : '${value!.month}月${value!.day}日',
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    ),
  );
}
