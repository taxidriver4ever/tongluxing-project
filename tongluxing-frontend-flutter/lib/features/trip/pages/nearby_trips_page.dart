import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/location_snapshot.dart';
import 'companion_discovery_page.dart';

class NearbyTripsPage extends StatefulWidget {
  const NearbyTripsPage({super.key});

  @override
  State<NearbyTripsPage> createState() => _NearbyTripsPageState();
}

class _NearbyTripsPageState extends State<NearbyTripsPage> {
  bool loading = true;
  String? error;
  List<CompanionMatchModel> trips = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    final point = LocationSnapshot.current ?? LocationSnapshot.demoFallback;
    try {
      trips = await CompanionMatchService(
        context.read<AppSession>().api,
      ).nearbyTrips(latitude: point.latitude, longitude: point.longitude);
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('附近招募行程'),
      actions: [
        IconButton(onPressed: load, icon: const Icon(LucideIcons.refreshCw)),
      ],
    ),
    body: loading
        ? const Center(child: CircularProgressIndicator())
        : error != null
        ? Center(child: Text(error!))
        : trips.isEmpty
        ? const _EmptyNearby()
        : RefreshIndicator(
            onRefresh: load,
            child: ListView.separated(
              padding: const EdgeInsets.fromLTRB(20, 10, 20, 32),
              itemCount: trips.length,
              separatorBuilder: (_, _) => const SizedBox(height: 12),
              itemBuilder: (_, index) => _NearbyTripTile(trip: trips[index]),
            ),
          ),
  );
}

class _NearbyTripTile extends StatelessWidget {
  const _NearbyTripTile({required this.trip});
  final CompanionMatchModel trip;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    borderRadius: BorderRadius.circular(22),
    child: InkWell(
      borderRadius: BorderRadius.circular(22),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => CompanionMatchDetailPage(matchId: '', initial: trip),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Row(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                color: const Color(0xFFEAF3FF),
                borderRadius: BorderRadius.circular(17),
              ),
              child: const Icon(LucideIcons.route, color: AppColors.primary),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    trip.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 5),
                  Text(
                    '${trip.startName} → ${trip.endName}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: AppColors.secondaryText),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    trip.departureTime,
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.muted,
                    ),
                  ),
                ],
              ),
            ),
            const Icon(LucideIcons.chevronRight, color: AppColors.muted),
          ],
        ),
      ),
    ),
  );
}

class _EmptyNearby extends StatelessWidget {
  const _EmptyNearby();

  @override
  Widget build(BuildContext context) => const Center(
    child: Padding(
      padding: EdgeInsets.all(32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(LucideIcons.mapPinned, size: 44, color: AppColors.muted),
          SizedBox(height: 12),
          Text('附近暂时没有招募中的公开行程'),
          SizedBox(height: 6),
          Text('稍后刷新看看，或创建一段新行程', style: TextStyle(color: AppColors.muted)),
        ],
      ),
    ),
  );
}
