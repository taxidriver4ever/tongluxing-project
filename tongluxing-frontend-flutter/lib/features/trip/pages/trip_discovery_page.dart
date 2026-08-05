import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/location_snapshot.dart';
import 'trip_create_page.dart';
import 'trip_discovery_detail_page.dart';

/// 「行程」Tab 中的推荐页面。
///
/// 页面只维护需求规定的三个排序状态：match_rate、distance、time。
/// 用户没有自己的有效行程时，match_rate 在界面上显示为“热度”，后端也会
/// 自动切换到热度排序；前端不再允许用户手动选择推荐基准行程。
class TripDiscoveryPage extends StatefulWidget {
  const TripDiscoveryPage({
    super.key,
    required this.userHasTrip,
    this.onTripCreated,
    this.onApplicationSubmitted,
  });

  final bool userHasTrip;
  final VoidCallback? onTripCreated;
  final VoidCallback? onApplicationSubmitted;

  @override
  State<TripDiscoveryPage> createState() => _TripDiscoveryPageState();
}

class _TripDiscoveryPageState extends State<TripDiscoveryPage> {
  static const int _pageSize = 10;

  final ScrollController _scrollController = ScrollController();
  List<TripRecommendModel> _trips = const [];
  final Set<String> _busyTripIds = <String>{};

  String _recommendSort = 'match_rate';
  late bool _userHasTrip;
  int _page = 1;
  int _total = 0;
  int _requestSerial = 0;
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _userHasTrip = widget.userHasTrip;
    _scrollController.addListener(_onScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) => _load(reset: true));
  }

  @override
  void didUpdateWidget(covariant TripDiscoveryPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.userHasTrip != widget.userHasTrip) {
      _userHasTrip = widget.userHasTrip;
      _load(reset: true);
    }
  }

  @override
  void dispose() {
    _scrollController
      ..removeListener(_onScroll)
      ..dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.extentAfter < 360) {
      _load(reset: false);
    }
  }

  Future<void> _load({required bool reset}) async {
    if (reset) {
      setState(() {
        _loading = true;
        _loadingMore = false;
        _error = null;
      });
    } else {
      if (_loading || _loadingMore || _trips.length >= _total) return;
      setState(() => _loadingMore = true);
    }

    final int serial = ++_requestSerial;
    final int targetPage = reset ? 1 : _page + 1;
    final location = LocationSnapshot.current;

    // 无自有行程时，需求规定距离必须以当前位置为基准。定位尚未成功时不使用
    // 演示坐标伪造推荐，而是明确提示用户开启定位或先发布行程。
    if (!_userHasTrip && location == null) {
      if (!mounted || serial != _requestSerial) return;
      setState(() {
        _loading = false;
        _loadingMore = false;
        _trips = const [];
        _total = 0;
        _error = '需要获取当前位置，才能按 100km 范围筛选推荐行程';
      });
      return;
    }

    try {
      final result = await TripDiscoveryService(
        context.read<AppSession>().api,
      ).recommend(
        sortBy: _recommendSort,
        userHasTrip: _userHasTrip,
        latitude: location?.latitude,
        longitude: location?.longitude,
        page: targetPage,
        pageSize: _pageSize,
      );
      if (!mounted || serial != _requestSerial) return;

      final values = (result['list'] as List? ?? const [])
          .whereType<Map>()
          .map(
            (value) => TripRecommendModel.fromJson(
              Map<String, dynamic>.from(value),
            ),
          )
          .toList();
      final actualUserHasTrip = result['userHasTrip'] == true;
      setState(() {
        _userHasTrip = actualUserHasTrip;
        _trips = reset ? values : [..._trips, ...values];
        _page = targetPage;
        _total = (result['total'] as num?)?.toInt() ?? _trips.length;
        _error = null;
      });
    } catch (error) {
      if (!mounted || serial != _requestSerial) return;
      setState(() => _error = error.toString());
    } finally {
      if (mounted && serial == _requestSerial) {
        setState(() {
          _loading = false;
          _loadingMore = false;
        });
      }
    }
  }

  Future<void> _changeSort(String value) async {
    if (value == _recommendSort) return;
    setState(() => _recommendSort = value);
    await _load(reset: true);
  }

  Future<void> _openCreateTrip() async {
    final created = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const TripCreatePage()),
    );
    if (!mounted || created != true) return;
    widget.onTripCreated?.call();
    // 后端会重新判断是否已有可作为推荐基准的行程，因此这里先刷新列表，
    // 不依赖本地猜测发布后的业务状态。
    await _load(reset: true);
  }

  Future<void> _openDetail(TripRecommendModel trip) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TripDiscoveryDetailPage(
          tripId: trip.tripId,
          initial: trip.toDiscoverModel(),
        ),
      ),
    );
    if (mounted) await _load(reset: true);
  }

  Future<void> _greet(TripRecommendModel trip) async {
    if (_busyTripIds.contains(trip.tripId)) return;
    setState(() => _busyTripIds.add(trip.tripId));
    try {
      await TripDiscoveryService(context.read<AppSession>().api).greet(
        trip.tripId,
      );
      if (!mounted) return;
      final index = _trips.indexWhere((value) => value.tripId == trip.tripId);
      if (index >= 0) {
        final next = [..._trips];
        next[index] = next[index].copyWith(allowGreeting: false);
        setState(() => _trips = next);
      }
      _showMessage('已向队长打招呼');
    } catch (error) {
      if (mounted) _showMessage(error.toString(), error: true);
    } finally {
      if (mounted) setState(() => _busyTripIds.remove(trip.tripId));
    }
  }

  Future<void> _apply(TripRecommendModel trip) async {
    if (_busyTripIds.contains(trip.tripId) || !trip.allowApply) return;
    setState(() => _busyTripIds.add(trip.tripId));
    try {
      await TripDiscoveryService(context.read<AppSession>().api).apply(
        trip.tripId,
        message: '通过推荐行程申请加入队伍',
        selfDrive: false,
      );
      if (!mounted) return;
      final index = _trips.indexWhere((value) => value.tripId == trip.tripId);
      if (index >= 0) {
        final next = [..._trips];
        next[index] = next[index].copyWith(
          relationshipStatus: 'PENDING',
          allowApply: false,
        );
        setState(() => _trips = next);
      }
      widget.onApplicationSubmitted?.call();
      _showMessage('申请已提交，可在“我的行程-待出发”查看待审批状态');
    } catch (error) {
      if (mounted) _showMessage(error.toString(), error: true);
    } finally {
      if (mounted) setState(() => _busyTripIds.remove(trip.tripId));
    }
  }

  void _showMessage(String message, {bool error = false}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: error ? AppColors.danger : null,
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      _RecommendSortBar(
        userHasTrip: _userHasTrip,
        selected: _recommendSort,
        onSelected: _changeSort,
      ),
      if (!_userHasTrip)
        _NoTripGuide(onPublish: _openCreateTrip),
      Expanded(child: _buildBody()),
    ],
  );

  Widget _buildBody() {
    if (_loading && _trips.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_trips.isEmpty && _error != null) {
      return _RecommendState(
        icon: LucideIcons.mapPinOff,
        title: '暂时无法加载推荐',
        subtitle: _error!,
        actionText: '重新加载',
        onAction: () => _load(reset: true),
      );
    }
    if (_trips.isEmpty) {
      return _RecommendState(
        icon: LucideIcons.routeOff,
        title: '暂无符合条件的推荐行程',
        subtitle: '系统已过滤距离超过100km、时间差超过3天、满员或低评分队伍',
        actionText: _userHasTrip ? '刷新推荐' : '立即发布',
        onAction: _userHasTrip
            ? () => _load(reset: true)
            : _openCreateTrip,
      );
    }

    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      color: AppColors.primary,
      child: ListView.separated(
        controller: _scrollController,
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 28),
        itemCount: _trips.length + (_loadingMore ? 1 : 0),
        separatorBuilder: (_, _) => const SizedBox(height: 10),
        itemBuilder: (context, index) {
          if (index == _trips.length) {
            return const Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          final trip = _trips[index];
          return _RecommendTripCard(
            trip: trip,
            rank: !_userHasTrip && _recommendSort == 'match_rate'
                ? index + 1
                : null,
            userHasTrip: _userHasTrip,
            busy: _busyTripIds.contains(trip.tripId),
            onTap: () => _openDetail(trip),
            onGreeting: trip.allowGreeting ? () => _greet(trip) : null,
            onApply: trip.allowApply ? () => _apply(trip) : null,
          );
        },
      ),
    );
  }
}

class _RecommendSortBar extends StatelessWidget {
  const _RecommendSortBar({
    required this.userHasTrip,
    required this.selected,
    required this.onSelected,
  });

  final bool userHasTrip;
  final String selected;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) {
    final items = <({String value, String label})>[
      (value: 'match_rate', label: userHasTrip ? '顺路率' : '热度'),
      (value: 'distance', label: '距离'),
      (value: 'time', label: '时间'),
    ];
    return Container(
      height: 48,
      color: Colors.white,
      child: Row(
        children: items
            .map(
              (item) => Expanded(
                child: InkWell(
                  onTap: () => onSelected(item.value),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      Text(
                        item.label,
                        style: TextStyle(
                          color: selected == item.value
                              ? AppColors.primary
                              : AppColors.secondaryText,
                          fontSize: 14,
                          fontWeight: selected == item.value
                              ? FontWeight.w800
                              : FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 9),
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 160),
                        width: 38,
                        height: 3,
                        decoration: BoxDecoration(
                          color: selected == item.value
                              ? AppColors.primary
                              : Colors.transparent,
                          borderRadius: BorderRadius.circular(99),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            )
            .toList(),
      ),
    );
  }
}

class _NoTripGuide extends StatelessWidget {
  const _NoTripGuide({required this.onPublish});

  final VoidCallback onPublish;

  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.fromLTRB(12, 10, 12, 0),
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      gradient: const LinearGradient(
        colors: [Color(0xFFEAF3FF), Color(0xFFF7FAFF)],
      ),
      borderRadius: BorderRadius.circular(14),
      border: Border.all(color: const Color(0xFFCFE2FF)),
    ),
    child: Row(
      children: [
        const Icon(LucideIcons.sparkles, color: AppColors.primary),
        const SizedBox(width: 10),
        const Expanded(
          child: Text(
            '发布你的行程，即可获得基于顺路率的个性化推荐。',
            style: TextStyle(fontSize: 13, height: 1.4),
          ),
        ),
        const SizedBox(width: 8),
        FilledButton(
          onPressed: onPublish,
          style: FilledButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            minimumSize: const Size(0, 36),
          ),
          child: const Text('立即发布'),
        ),
      ],
    ),
  );
}

class _RecommendTripCard extends StatelessWidget {
  const _RecommendTripCard({
    required this.trip,
    required this.userHasTrip,
    required this.busy,
    required this.onTap,
    required this.onGreeting,
    required this.onApply,
    this.rank,
  });

  final TripRecommendModel trip;
  final bool userHasTrip;
  final bool busy;
  final int? rank;
  final VoidCallback onTap;
  final VoidCallback? onGreeting;
  final VoidCallback? onApply;

  String get dateText {
    final value = DateTime.tryParse(trip.departureTime.replaceFirst(' ', 'T'));
    if (value == null) return trip.departureTime;
    return '${value.month}月${value.day}日';
  }

  String get relationshipText => switch (trip.relationshipStatus) {
    'PENDING' => '待审批',
    'JOINED' => '已加入',
    'OWNER' => '我的队伍',
    _ => '申请入队',
  };

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(16),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFE6EBF2)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x0D0F172A),
              blurRadius: 10,
              offset: Offset(0, 3),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                if (rank != null) ...[
                  Text(
                    'No.$rank',
                    style: const TextStyle(
                      color: Color(0xFFF59E0B),
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                Expanded(
                  child: Text(
                    trip.tripName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
                const Icon(
                  LucideIcons.chevronRight,
                  size: 18,
                  color: AppColors.muted,
                ),
              ],
            ),
            const SizedBox(height: 9),
            Text(
              '${trip.startLocation} → ${trip.endLocation} · $dateText · '
              '${trip.currentVehicleCount}/${trip.vehicleLimit} 车',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: AppColors.secondaryText,
                fontSize: 13,
                height: 1.45,
              ),
            ),
            const SizedBox(height: 10),
            Wrap(
              spacing: 12,
              runSpacing: 6,
              children: [
                _MetricText(
                  icon: userHasTrip
                      ? LucideIcons.route
                      : LucideIcons.flame,
                  text: userHasTrip
                      ? '顺路率 ${trip.matchRate ?? 0}%'
                      : '热度 ${trip.heat ?? 0}',
                  emphasized: true,
                ),
                _MetricText(
                  icon: LucideIcons.mapPin,
                  text: '距你 ${_distanceText(trip.distance)}',
                ),
                _MetricText(
                  icon: LucideIcons.star,
                  text: '评分 ${trip.leaderRating.toStringAsFixed(1)}',
                ),
              ],
            ),
            const SizedBox(height: 13),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: busy ? null : onGreeting,
                    icon: const Icon(LucideIcons.messageCircle, size: 17),
                    label: Text(onGreeting == null ? '已打招呼' : '打招呼'),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: busy ? null : onApply,
                    icon: busy
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(LucideIcons.userPlus, size: 17),
                    label: Text(relationshipText),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );

  static String _distanceText(double distance) {
    if (distance < 1) return '${(distance * 1000).round()}m';
    return '${distance.toStringAsFixed(distance >= 10 ? 0 : 1)}km';
  }
}

class _MetricText extends StatelessWidget {
  const _MetricText({
    required this.icon,
    required this.text,
    this.emphasized = false,
  });

  final IconData icon;
  final String text;
  final bool emphasized;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Icon(
        icon,
        size: 15,
        color: emphasized ? AppColors.primary : AppColors.muted,
      ),
      const SizedBox(width: 4),
      Text(
        text,
        style: TextStyle(
          color: emphasized ? AppColors.primary : AppColors.secondaryText,
          fontSize: 12.5,
          fontWeight: emphasized ? FontWeight.w800 : FontWeight.w600,
        ),
      ),
    ],
  );
}

class _RecommendState extends StatelessWidget {
  const _RecommendState({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.actionText,
    required this.onAction,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final String actionText;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 48, color: AppColors.muted),
          const SizedBox(height: 14),
          Text(
            title,
            style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: AppColors.secondaryText,
              height: 1.5,
            ),
          ),
          const SizedBox(height: 18),
          FilledButton(onPressed: onAction, child: Text(actionText)),
        ],
      ),
    ),
  );
}
