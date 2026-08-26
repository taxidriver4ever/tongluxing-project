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
  final Future<void> Function()? onTripCreated;
  final Future<void> Function()? onApplicationSubmitted;

  @override
  State<TripDiscoveryPage> createState() => _TripDiscoveryPageState();
}

class _TripDiscoveryPageState extends State<TripDiscoveryPage> {
  static const int _pageSize = 10;

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

  bool _onScrollNotification(ScrollNotification notification) {
    if (notification.metrics.axis == Axis.vertical &&
        notification.metrics.extentAfter < 360) {
      _load(reset: false);
    }
    return false;
  }

  Future<void> _load({required bool reset}) async {
    if (!mounted) return;
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
      final result = await TripDiscoveryService(context.read<AppSession>().api)
          .recommend(
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
            (value) =>
                TripRecommendModel.fromJson(Map<String, dynamic>.from(value)),
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
    final callback = widget.onTripCreated;
    if (callback != null) {
      await callback();
      return;
    }
    // 独立使用该页面时仍由自身重新请求；嵌入行程首页时交给父页面统一刷新
    // 当前行程与推荐列表，避免父级重建后继续操作已经销毁的 State。
    if (mounted) await _load(reset: true);
  }

  Future<void> _openDetail(
    TripRecommendModel trip, {
    bool openApply = false,
  }) async {
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TripDiscoveryDetailPage(
          tripId: trip.tripId,
          initial: trip.toDiscoverModel(),
          openApplyOnLoad: openApply,
          onApplicationSubmitted: widget.onApplicationSubmitted,
        ),
      ),
    );
    if (mounted) await _load(reset: true);
  }

  Future<void> _greet(TripRecommendModel trip) async {
    if (_busyTripIds.contains(trip.tripId)) return;
    setState(() => _busyTripIds.add(trip.tripId));
    try {
      await TripDiscoveryService(
        context.read<AppSession>().api,
      ).greet(trip.tripId);
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
      if (!_userHasTrip) _NoTripGuide(onPublish: _openCreateTrip),
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
        subtitle: _userHasTrip
            ? '系统已过滤顺路率低于20%、距离超过100km、时间差超过3天或已满员队伍'
            : '系统已过滤距离超过100km、时间差超过3天或已满员队伍',
        actionText: _userHasTrip ? '刷新推荐' : '立即发布',
        onAction: _userHasTrip ? () => _load(reset: true) : _openCreateTrip,
      );
    }

    return NotificationListener<ScrollNotification>(
      onNotification: _onScrollNotification,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 28),
        itemCount: 1 + _trips.length + (_loadingMore ? 1 : 0),
        separatorBuilder: (_, _) => const SizedBox(height: 10),
        itemBuilder: (context, index) {
          if (index == 0) {
            return _RecommendContentTitle(userHasTrip: _userHasTrip);
          }
          final tripIndex = index - 1;
          if (tripIndex == _trips.length) {
            return const Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          final trip = _trips[tripIndex];
          return _RecommendTripCard(
            trip: trip,
            rank: tripIndex + 1,
            userHasTrip: _userHasTrip,
            busy: _busyTripIds.contains(trip.tripId),
            onTap: () => _openDetail(trip),
            onGreeting: trip.allowGreeting ? () => _greet(trip) : null,
            // 推荐列表可能命中旧缓存，申请资格以进入详情后的实时结果为准。
            // 非终态关系允许点击进入申请表单，服务端仍负责最终业务校验。
            onApply:
                const {
                  'OWNER',
                  'JOINED',
                  'ACTIVE',
                  'PENDING',
                }.contains(trip.relationshipStatus)
                ? null
                : () => _openDetail(trip, openApply: true),
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
      color: Colors.white,
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 9),
      child: Row(
        children: [
          for (var index = 0; index < items.length; index++) ...[
            if (index > 0) const SizedBox(width: 8),
            Expanded(
              child: _RecommendSortSegment(
                label: items[index].label,
                selected: selected == items[index].value,
                onTap: () => onSelected(items[index].value),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _RecommendSortSegment extends StatelessWidget {
  const _RecommendSortSegment({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Material(
    color: selected ? AppColors.primary : const Color(0xFFF2F4F8),
    borderRadius: BorderRadius.circular(999),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(999),
      child: SizedBox(
        height: 36,
        child: Center(
          child: Text(
            label,
            style: TextStyle(
              color: selected ? Colors.white : AppColors.secondaryText,
              fontSize: 12.5,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ),
    ),
  );
}

class _NoTripGuide extends StatelessWidget {
  const _NoTripGuide({required this.onPublish});

  final VoidCallback onPublish;

  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.fromLTRB(12, 10, 12, 0),
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: AppColors.primarySoft,
      borderRadius: BorderRadius.circular(18),
      border: Border.all(color: const Color(0xFFCFE2FF)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Row(
          children: [
            Icon(LucideIcons.sparkles, color: AppColors.primary, size: 18),
            SizedBox(width: 7),
            Expanded(
              child: Text(
                '发布行程后可获得个性化顺路推荐',
                style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w900),
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        const Text(
          '当前暂无基准行程，因此先按热度、距离和时间展示。',
          style: TextStyle(
            color: AppColors.secondaryText,
            fontSize: 12,
            height: 1.45,
          ),
        ),
        const SizedBox(height: 9),
        FilledButton.icon(
          onPressed: onPublish,
          style: FilledButton.styleFrom(
            minimumSize: const Size(0, 35),
            padding: const EdgeInsets.symmetric(horizontal: 14),
          ),
          icon: const Icon(LucideIcons.plus, size: 16),
          label: const Text('立即发布'),
        ),
      ],
    ),
  );
}

class _RecommendContentTitle extends StatelessWidget {
  const _RecommendContentTitle({required this.userHasTrip});

  final bool userHasTrip;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(2, 1, 2, 2),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Text(
          userHasTrip ? '为你推荐' : '热门行程',
          style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w900),
        ),
        const Spacer(),
        Text(
          userHasTrip ? '按综合顺路率排序' : '近期高热度',
          style: const TextStyle(color: AppColors.muted, fontSize: 11),
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
    required this.rank,
  });

  final TripRecommendModel trip;
  final bool userHasTrip;
  final bool busy;
  final int rank;
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
    'REJECTED' || 'CANCELLED' || 'CANCELED' => '重新申请',
    _ => '申请入队',
  };

  String get ownerInitial {
    final value = trip.ownerNickname.trim();
    return value.isEmpty ? '友' : value.substring(0, 1);
  }

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(22),
    clipBehavior: Clip.antiAlias,
    child: InkWell(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(22),
          border: Border.all(color: const Color(0xFFE5E7EB)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x0E1D3969),
              blurRadius: 17,
              offset: Offset(0, 5),
            ),
          ],
        ),
        child: Stack(
          children: [
            Positioned(
              left: 0,
              top: 0,
              bottom: 0,
              child: Container(width: 4, color: const Color(0xFFD5E5FF)),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 13, 13, 13),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 31,
                        height: 31,
                        decoration: BoxDecoration(
                          color: rank == 1
                              ? const Color(0xFFFFF4D9)
                              : const Color(0xFFF2F4F7),
                          borderRadius: BorderRadius.circular(11),
                        ),
                        alignment: Alignment.center,
                        child: Text(
                          '$rank',
                          style: TextStyle(
                            color: rank == 1
                                ? const Color(0xFFA96200)
                                : const Color(0xFF667085),
                            fontSize: 13,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                      ),
                      const SizedBox(width: 9),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              trip.tripName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 15,
                                fontWeight: FontWeight.w900,
                                height: 1.35,
                              ),
                            ),
                            const SizedBox(height: 3),
                            Text(
                              '${trip.startLocation} → ${trip.endLocation} · $dateText出发',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: AppColors.secondaryText,
                                fontSize: 11,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 7),
                      _RecommendBadge(
                        label: userHasTrip
                            ? '顺路率 ${trip.matchRate ?? 0}%'
                            : '热度 ${trip.heat ?? 0}',
                        heat: !userHasTrip,
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: [
                      _RecommendMetric(
                        icon: LucideIcons.navigation,
                        label: '距你 ${_distanceText(trip.distance)}',
                      ),
                      _RecommendMetric(
                        icon: LucideIcons.users,
                        label:
                            '车辆 ${trip.currentVehicleCount}/${trip.vehicleLimit}',
                      ),
                    ],
                  ),
                  const SizedBox(height: 11),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: busy ? null : onGreeting,
                          style: OutlinedButton.styleFrom(
                            minimumSize: const Size(0, 35),
                            side: const BorderSide(color: Color(0xFFBFD7FF)),
                            shape: const StadiumBorder(),
                          ),
                          icon: const Icon(LucideIcons.messageCircle, size: 16),
                          label: Text(onGreeting == null ? '已打招呼' : '打招呼'),
                        ),
                      ),
                      const SizedBox(width: 7),
                      Expanded(
                        child: FilledButton.icon(
                          onPressed: busy ? null : onApply,
                          style: FilledButton.styleFrom(
                            minimumSize: const Size(0, 35),
                          ),
                          icon: busy
                              ? const SizedBox(
                                  width: 15,
                                  height: 15,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Icon(LucideIcons.userPlus, size: 16),
                          label: Text(relationshipText),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 11),
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 17,
                        backgroundColor: AppColors.primarySoft,
                        child: Text(
                          ownerInitial,
                          style: const TextStyle(
                            color: AppColors.primaryDark,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                      ),
                      const SizedBox(width: 9),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              trip.ownerNickname,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 12,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                            const SizedBox(height: 2),
                            const Text(
                              '队长',
                              style: TextStyle(
                                color: AppColors.muted,
                                fontSize: 10,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
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

class _RecommendBadge extends StatelessWidget {
  const _RecommendBadge({required this.label, required this.heat});

  final String label;
  final bool heat;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
    decoration: BoxDecoration(
      color: heat ? const Color(0xFFFFF2DC) : AppColors.primarySoft,
      borderRadius: BorderRadius.circular(999),
    ),
    child: Text(
      label,
      style: TextStyle(
        color: heat ? const Color(0xFFC66B00) : AppColors.primaryDark,
        fontSize: 10,
        fontWeight: FontWeight.w900,
      ),
    ),
  );
}

class _RecommendMetric extends StatelessWidget {
  const _RecommendMetric({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 6),
    decoration: BoxDecoration(
      color: const Color(0xFFF5F7FA),
      borderRadius: BorderRadius.circular(10),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 12, color: AppColors.muted),
        const SizedBox(width: 4),
        Text(
          label,
          style: const TextStyle(
            color: AppColors.secondaryText,
            fontSize: 10,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    ),
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
            style: const TextStyle(color: AppColors.secondaryText, height: 1.5),
          ),
          const SizedBox(height: 18),
          FilledButton(onPressed: onAction, child: Text(actionText)),
        ],
      ),
    ),
  );
}
