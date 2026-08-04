import 'dart:typed_data';

import '../models/app_models.dart';
import 'api_client.dart';

class AuthService {
  const AuthService(this.api);
  final ApiClient api;
  Future<LoginSession> passwordLogin(
    String phone,
    String password, {
    required String deviceId,
  }) async {
    final data = await api.post(
      '/v1/auth/password-login',
      body: {
        'phone': phone,
        'password': password,
        'deviceId': deviceId,
        'clientType': 'APP_DRIVER',
      },
    );
    return LoginSession.fromJson(Map<String, dynamic>.from(data as Map));
  }

  Future<void> logout() => api.post('/v1/auth/logout');

  Future<Map<String, dynamic>> currentUser() async =>
      Map<String, dynamic>.from(await api.get('/v1/auth/me') as Map);

  Future<TokenRefreshSession> refresh(String refreshToken) async {
    final data = await api.post(
      '/v1/auth/refresh-token',
      body: {'refreshToken': refreshToken},
    );
    return TokenRefreshSession.fromJson(Map<String, dynamic>.from(data as Map));
  }
}

class MerchantOnboardingService {
  const MerchantOnboardingService(this.api);
  final ApiClient api;

  Future<Map<String, dynamic>?> latest() async {
    try {
      final data = await api.get('/v1/merchants/applications/me/latest');
      return data is Map ? Map<String, dynamic>.from(data) : null;
    } on ApiException catch (e) {
      if (e.statusCode == 404 || e.message.contains('尚未入驻')) return null;
      rethrow;
    }
  }

  Future<Map<String, dynamic>> submit(Map<String, dynamic> body) async =>
      Map<String, dynamic>.from(
        await api.post('/v1/merchants/applications', body: body) as Map,
      );

  Future<void> saveSettlement(Map<String, dynamic> body) =>
      api.put('/v1/merchants/me/settlement-account', body: body);
}

class MerchantEcosystemService {
  const MerchantEcosystemService(this.api);
  final ApiClient api;

  // App 只消费已审核的商家优惠。门店、优惠券与拼单规则的
  // 经营管理已统一迁移到商家 Web，不在移动端暴露写接口。
  Future<List<Map<String, dynamic>>> marketplace() async {
    final data = await api.get(
      '/v1/merchant-market/coupons',
      query: {'page': '1', 'size': '50'},
    );
    return data is Map ? _maps(data['records']) : const [];
  }

  Future<Map<String, dynamic>> marketDetail(String id) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/merchant-market/coupons/$id') as Map,
      );
  List<Map<String, dynamic>> _maps(dynamic data) => (data as List? ?? const [])
      .whereType<Map>()
      .map((e) => Map<String, dynamic>.from(e))
      .toList();
}

class CouponWalletService {
  const CouponWalletService(this.api);
  final ApiClient api;

  Future<List<Map<String, dynamic>>> mine(String status) async {
    final data = await api.get(
      '/v1/coupons/me',
      query: {'status': status, 'page': '1', 'size': '100'},
    );
    return data is Map ? _maps(data['records']) : const [];
  }

  Future<List<Map<String, dynamic>>> claimable() async =>
      _maps(await api.get('/v1/coupons/templates/claimable'));

  Future<void> claim(String templateId) =>
      api.post('/v1/coupons/templates/$templateId/claim');

  Future<Map<String, dynamic>> detail(String id) async =>
      Map<String, dynamic>.from(await api.get('/v1/coupons/me/$id') as Map);

  Future<Map<String, dynamic>> createVerificationCode(
    Map<String, dynamic> body,
  ) async => Map<String, dynamic>.from(
    await api.post('/v1/verifications/codes', body: body) as Map,
  );

  List<Map<String, dynamic>> _maps(dynamic data) => (data as List? ?? const [])
      .whereType<Map>()
      .map((item) => Map<String, dynamic>.from(item))
      .toList();
}

class GroupbuyService {
  const GroupbuyService(this.api);
  final ApiClient api;

  Future<List<Map<String, dynamic>>> activities({String status = ''}) async {
    final data = await api.get(
      '/v1/groupbuys',
      query: {'status': status, 'page': '1', 'size': '50'},
    );
    return _records(data);
  }

  Future<List<Map<String, dynamic>>> mine() async {
    final data = await api.get(
      '/v1/groupbuys/me',
      query: {'page': '1', 'size': '50'},
    );
    return _records(data);
  }

  Future<Map<String, dynamic>> create(String couponId) async =>
      Map<String, dynamic>.from(
        await api.post(
              '/v1/groupbuys',
              body: {
                'couponId': couponId,
                'requestId':
                    'APP-GROUP-CREATE-${DateTime.now().microsecondsSinceEpoch}',
              },
            )
            as Map,
      );

  Future<Map<String, dynamic>> join(String activityId) async =>
      Map<String, dynamic>.from(
        await api.post(
              '/v1/groupbuys/$activityId/join',
              body: {
                'requestId':
                    'APP-GROUP-JOIN-${DateTime.now().microsecondsSinceEpoch}',
              },
            )
            as Map,
      );

  Future<Map<String, dynamic>> detail(String activityId) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/groupbuys/$activityId') as Map,
      );

  List<Map<String, dynamic>> _records(dynamic data) =>
      (data is Map ? data['records'] as List? : const [])
          ?.whereType<Map>()
          .map((e) => Map<String, dynamic>.from(e))
          .toList() ??
      const [];
}

class LocationService {
  const LocationService(this.api);
  final ApiClient api;

  Future<List<LocationSelection>> search(
    String keyword, {
    double? latitude,
    double? longitude,
  }) async {
    final data = await api.get(
      '/v1/map/locations/search',
      query: {
        'keyword': keyword,
        'limit': '20',
        if (latitude != null) 'latitude': latitude.toString(),
        if (longitude != null) 'longitude': longitude.toString(),
      },
    );
    return _locations(data);
  }

  Future<List<LocationSelection>> history({
    double? latitude,
    double? longitude,
  }) async => _locations(
    await api.get(
      '/v1/map/locations/history',
      query: {
        'limit': '10',
        if (latitude != null) 'latitude': latitude.toString(),
        if (longitude != null) 'longitude': longitude.toString(),
      },
    ),
  );

  Future<int> deleteHistory(String historyId) async {
    final data = await api.delete('/v1/map/locations/history/$historyId');
    return (data as num?)?.toInt() ?? 0;
  }

  Future<int> clearHistory() async {
    final data = await api.delete('/v1/map/locations/history');
    return (data as num?)?.toInt() ?? 0;
  }

  Future<LocationSelection> select(LocationSelection location) async {
    final data = await api.post(
      '/v1/map/locations/resolve',
      body: location.toJson(),
    );
    return LocationSelection.fromJson(Map<String, dynamic>.from(data as Map));
  }

  List<LocationSelection> _locations(dynamic data) {
    final list = data is List ? data : const [];
    return list
        .map(
          (item) => LocationSelection.fromJson(
            Map<String, dynamic>.from(item as Map),
          ),
        )
        .toList();
  }
}

class TripService {
  const TripService(this.api);
  final ApiClient api;
  Future<List<TripModel>> mine({String scope = 'active'}) async {
    final data = await api.get('/v1/trips/me', query: {'scope': scope});
    return _tripList(data);
  }

  Future<Map<String, dynamic>> activeState() async => Map<String, dynamic>.from(
    await api.get('/v1/trips/me/active-state') as Map,
  );

  Future<Map<String, dynamic>> dashboard() async =>
      Map<String, dynamic>.from(await api.get('/v1/trips/me/dashboard') as Map);

  Future<Map<String, dynamic>> checkTimeConflict({
    required DateTime departureTime,
    int estimatedDays = 1,
    String? excludeTripId,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/trips/time-conflicts/check',
          body: {
            'departureTime': departureTime.toIso8601String(),
            'estimatedDays': estimatedDays,
            'excludeTripId': excludeTripId,
          },
        )
        as Map,
  );

  Future<List<TripModel>> publicTrips() async =>
      _tripList(await api.get('/v1/trips/public'));
  Future<TripModel?> current() async {
    final data = await api.get('/v1/trips/driving/current');
    return data == null
        ? null
        : TripModel.fromJson(Map<String, dynamic>.from(data as Map));
  }

  Future<TripModel> detail(String id) async => TripModel.fromJson(
    Map<String, dynamic>.from(await api.get('/v1/trips/$id') as Map),
  );

  Future<Map<String, dynamic>> rawDetail(String id) async =>
      Map<String, dynamic>.from(await api.get('/v1/trips/$id') as Map);

  Future<Map<String, dynamic>> updateFromRaw(
    String id,
    Map<String, dynamic> raw, {
    required String title,
    required String description,
    required String departureTime,
    required int maxVehicleCount,
    int? expectedPeople,
    int? estimatedDays,
    List<String>? vehicleRequirements,
    String? remark,
    Map<String, dynamic>? startLocation,
    Map<String, dynamic>? endLocation,
    List<Map<String, dynamic>>? waypoints,
  }) async => Map<String, dynamic>.from(
    await api.put(
          '/v1/trips/$id',
          body: {
            'vehicleId': raw['vehicleId'],
            'title': title,
            'description': description,
            'coverImageKey': raw['coverImageKey'],
            'expectedPeople': expectedPeople ?? raw['expectedPeople'],
            'startLocation': startLocation ?? raw['startLocation'],
            'endLocation': endLocation ?? raw['endLocation'],
            'routeSummary': raw['routeSummary'],
            'departureTime': departureTime,
            'estimatedDays': estimatedDays ?? raw['estimatedDays'],
            'routeDistance': raw['routeDistance'],
            'routeDuration': raw['routeDuration'],
            'routePolyline': raw['routePolyline'],
            'maxVehicleCount': maxVehicleCount,
            'travelDepth': raw['travelDepth'] ?? 'LIGHT',
            'publicFlag': raw['publicFlag'] != false,
            'vehicleRequirements':
                vehicleRequirements ??
                raw['vehicleRequirements'] ??
                const ['不限'],
            'budgetDescription': raw['budgetDescription'],
            'remark': remark ?? raw['remark'],
            'waypoints': waypoints ?? raw['waypoints'] ?? const [],
          },
        )
        as Map,
  );
  Future<TripModel> start(
    String id, {
    required double latitude,
    required double longitude,
    double? accuracy,
  }) async => TripModel.fromJson(
    Map<String, dynamic>.from(
      await api.post(
            '/v1/trips/$id/start',
            body: {
              'latitude': latitude,
              'longitude': longitude,
              'accuracy': accuracy,
            },
          )
          as Map,
    ),
  );
  Future<TripModel> end(String id) async => TripModel.fromJson(
    Map<String, dynamic>.from(await api.post('/v1/trips/$id/end') as Map),
  );
  Future<TripModel> cancel(String id) async => TripModel.fromJson(
    Map<String, dynamic>.from(await api.post('/v1/trips/$id/cancel') as Map),
  );

  Future<Map<String, dynamic>> uploadTrackPoint({
    required String tripId,
    required double longitude,
    required double latitude,
    required DateTime recordTime,
    required double accuracy,
    required int sequenceNo,
    double? altitude,
    double? speed,
    double? direction,
    DateTime? clientSendTime,
    String? deviceId,
    bool mockLocation = false,
    String provider = 'fused',
    double? batteryLevel,
    String appState = 'foreground',
  }) => uploadTrackPayload({
    'tripId': tripId,
    'longitude': longitude,
    'latitude': latitude,
    'altitude': altitude,
    'speed': speed,
    'direction': direction,
    'accuracy': accuracy,
    'recordTime': recordTime.toIso8601String(),
    'clientSendTime': (clientSendTime ?? DateTime.now()).toIso8601String(),
    'deviceId': deviceId,
    'sequenceNo': sequenceNo,
    'mockLocation': mockLocation,
    'provider': provider,
    'batteryLevel': batteryLevel,
    'appState': appState,
  });

  Future<Map<String, dynamic>> uploadTrackPayload(
    Map<String, dynamic> point,
  ) async {
    final body = Map<String, dynamic>.from(point);
    return Map<String, dynamic>.from(
      await api.post('/v1/driver-tracks/points', body: body) as Map,
    );
  }

  Future<Map<String, dynamic>> mockDeviation(
    String tripId, {
    int status = 1,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/driver-tracks/trips/$tripId/mock-deviation',
          body: {'deviationStatus': status},
        )
        as Map,
  );

  Future<Map<String, dynamic>> teammateDistanceState(String tripId) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/driver-tracks/trips/$tripId/deviation') as Map,
      );

  Future<TripSettlementModel> settle(String id) async =>
      TripSettlementModel.fromJson(
        Map<String, dynamic>.from(
          await api.post('/v1/trips/$id/settle') as Map,
        ),
      );

  Future<TripDraftRouteModel> planRoadRoute({
    required LocationSelection start,
    required LocationSelection end,
    List<LocationSelection> waypoints = const [],
  }) async => TripDraftRouteModel.fromJson(
    Map<String, dynamic>.from(
      await api.post(
            '/v1/map/routes/plan',
            body: {
              'startLocation': start.toJson(),
              'endLocation': end.toJson(),
              'waypoints': waypoints.map((point) => point.toJson()).toList(),
            },
          )
          as Map,
    ),
  );

  Future<TripDraftModel> createDraft(Map<String, dynamic> body) async =>
      TripDraftModel.fromJson(
        Map<String, dynamic>.from(
          await api.post('/v1/trip/draft', body: body) as Map,
        ),
      );

  Future<TripDraftModel> updateDraft(
    String draftId,
    Map<String, dynamic> body,
  ) async => TripDraftModel.fromJson(
    Map<String, dynamic>.from(
      await api.put('/v1/trip/draft/$draftId', body: body) as Map,
    ),
  );

  Future<TripDraftModel> draftDetail(String draftId) async =>
      TripDraftModel.fromJson(
        Map<String, dynamic>.from(
          await api.get('/v1/trip/draft/$draftId') as Map,
        ),
      );

  Future<void> deleteDraft(String draftId) =>
      api.delete('/v1/trip/draft/$draftId');

  Future<TripDraftWaypointModel> addWaypoint(
    String draftId,
    LocationSelection location, {
    String type = 'NORMAL',
    int sort = 1,
    int stayMinutes = 0,
    String remark = '',
  }) async => TripDraftWaypointModel.fromJson(
    Map<String, dynamic>.from(
      await api.post(
            '/v1/trip/$draftId/waypoint',
            body: {
              'name': location.name,
              'address': location.address,
              'longitude': location.longitude,
              'latitude': location.latitude,
              'type': type,
              'sort': sort,
              'stayMinutes': stayMinutes,
              'remark': remark.trim(),
            },
          )
          as Map,
    ),
  );

  Future<TripDraftWaypointModel> updateWaypoint(
    String draftId,
    String waypointId,
    LocationSelection location, {
    String type = 'NORMAL',
    int sort = 1,
    int stayMinutes = 0,
    String remark = '',
  }) async => TripDraftWaypointModel.fromJson(
    Map<String, dynamic>.from(
      await api.put(
            '/v1/trip/$draftId/waypoint/$waypointId',
            body: {
              'name': location.name,
              'address': location.address,
              'longitude': location.longitude,
              'latitude': location.latitude,
              'type': type,
              'sort': sort,
              'stayMinutes': stayMinutes,
              'remark': remark.trim(),
            },
          )
          as Map,
    ),
  );

  Future<void> deleteWaypoint(String draftId, String waypointId) =>
      api.delete('/v1/trip/$draftId/waypoint/$waypointId');

  Future<List<TripDraftWaypointModel>> reorderWaypoints(
    String draftId,
    List<String> waypointIds,
  ) async {
    final data = await api.put(
      '/v1/trip/$draftId/waypoints/order',
      body: {'waypointIds': waypointIds},
    );
    return (data as List? ?? const [])
        .whereType<Map>()
        .map(
          (e) => TripDraftWaypointModel.fromJson(Map<String, dynamic>.from(e)),
        )
        .toList();
  }

  Future<Map<String, dynamic>> planRoute(String draftId) async =>
      Map<String, dynamic>.from(
        await api.post('/v1/trip/draft/$draftId/route/plan') as Map,
      );

  Future<TripDraftRouteModel?> draftRoute(String draftId) async {
    final data = await api.get('/v1/trip/$draftId/route');
    return data is Map
        ? TripDraftRouteModel.fromJson(Map<String, dynamic>.from(data))
        : null;
  }

  Future<String> publishDraft(String draftId) async {
    final data = Map<String, dynamic>.from(
      await api.post('/v1/trip/draft/$draftId/publish') as Map,
    );
    return data['tripId']?.toString() ?? '';
  }

  Future<List<TripDraftModel>> drafts() async {
    final data = await api.get('/v1/trip/draft');
    final list = data is List ? data : const [];
    return list
        .map(
          (e) => TripDraftModel.fromJson(Map<String, dynamic>.from(e as Map)),
        )
        .toList();
  }

  List<TripModel> _tripList(dynamic data) {
    final list = data is Map ? (data['trips'] as List? ?? const []) : const [];
    return list
        .map((e) => TripModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }
}

class CompanionMatchService {
  const CompanionMatchService(this.api);
  final ApiClient api;

  Future<List<CompanionMatchModel>> recommendations(String tripId) async {
    final data = await api.get('/v1/matches/trips/$tripId/recommendations');
    final list = data is Map ? (data['trips'] as List? ?? const []) : const [];
    return list
        .map(
          (e) =>
              CompanionMatchModel.fromJson(Map<String, dynamic>.from(e as Map)),
        )
        .toList();
  }

  Future<CompanionMatchModel> detail(String matchId) async =>
      CompanionMatchModel.fromJson(
        Map<String, dynamic>.from(
          await api.get('/v1/matches/recommendations/$matchId') as Map,
        ),
      );

  Future<Map<String, dynamic>> apply(String matchId, {String? message}) async =>
      Map<String, dynamic>.from(
        await api.post(
              '/v1/matches/recommendations/$matchId/apply',
              body: {'message': message ?? '路线很合适，希望一起出发'},
            )
            as Map,
      );

  Future<List<CompanionMatchModel>> nearbyTrips({
    required double latitude,
    required double longitude,
    int radiusMeters = 50000,
  }) async {
    final data = await api.get(
      '/v1/matches/nearby-trips',
      query: {
        'latitude': latitude.toString(),
        'longitude': longitude.toString(),
        'radiusMeters': radiusMeters.toString(),
        'limit': '20',
      },
    );
    final list = data is Map ? (data['trips'] as List? ?? const []) : const [];
    return list
        .map(
          (e) =>
              CompanionMatchModel.fromJson(Map<String, dynamic>.from(e as Map)),
        )
        .toList();
  }

  Future<Map<String, dynamic>> applyNearbyTrip(String tripId) async =>
      Map<String, dynamic>.from(
        await api.post(
              '/v1/matches/nearby-trips/$tripId/apply',
              body: {'message': '从附近招募行程看到你的路线，希望一起出发'},
            )
            as Map,
      );
}

class TripDiscoveryService {
  const TripDiscoveryService(this.api);
  final ApiClient api;

  Future<Map<String, dynamic>> discover({
    String? keyword,
    String? searchType,
    String sort = 'RECOMMENDED',
    double? latitude,
    double? longitude,
    String? referenceTripId,
    String? startCity,
    String? destination,
    DateTime? departureDateFrom,
    DateTime? departureDateTo,
    String? vehicleType,
    int? minimumRemainingSeats,
    int page = 1,
    int size = 10,
    int? refreshSeed,
  }) async => Map<String, dynamic>.from(
    await api.get(
          '/v1/trips/discover',
          query: {
            if (keyword?.trim().isNotEmpty == true) 'keyword': keyword!.trim(),
            if (searchType?.trim().isNotEmpty == true)
              'searchType': searchType!.trim(),
            'sort': sort,
            if (latitude != null) 'latitude': latitude.toString(),
            if (longitude != null) 'longitude': longitude.toString(),
            if (referenceTripId?.isNotEmpty == true)
              'referenceTripId': referenceTripId!,
            if (startCity?.trim().isNotEmpty == true)
              'startCity': startCity!.trim(),
            if (destination?.trim().isNotEmpty == true)
              'destination': destination!.trim(),
            if (departureDateFrom != null)
              'departureDateFrom': departureDateFrom
                  .toIso8601String()
                  .substring(0, 10),
            if (departureDateTo != null)
              'departureDateTo': departureDateTo.toIso8601String().substring(
                0,
                10,
              ),
            if (vehicleType?.isNotEmpty == true) 'vehicleType': vehicleType!,
            if (minimumRemainingSeats != null)
              'minimumRemainingSeats': minimumRemainingSeats.toString(),
            'page': page.toString(),
            'size': size.toString(),
            if (refreshSeed != null) 'refreshSeed': refreshSeed.toString(),
          },
        )
        as Map,
  );

  Future<Map<String, dynamic>> publicTripsByUser(
    String userId, {
    int page = 1,
    int size = 10,
  }) async => Map<String, dynamic>.from(
    await api.get(
          '/v1/trips/users/$userId/public',
          query: {'page': '$page', 'size': '$size'},
        )
        as Map,
  );

  Future<TripPublicDetailModel> publicDetail(String tripId) async {
    final data = await api
        .get('/v1/trips/$tripId/public-detail')
        .timeout(const Duration(seconds: 8));
    return TripPublicDetailModel.fromJson(
      Map<String, dynamic>.from(data as Map),
    );
  }

  Future<bool> favorite(String tripId) async =>
      await api.post('/v1/trips/$tripId/favorite') == true;

  Future<bool> unfavorite(String tripId) async =>
      await api.delete('/v1/trips/$tripId/favorite') == true;

  Future<Map<String, dynamic>> consult(
    String tripId, {
    required String content,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/trips/$tripId/consultations',
          body: {'content': content},
        )
        as Map,
  );

  Future<Map<String, dynamic>> search({
    required LocationSelection start,
    required LocationSelection end,
    required List<LocationSelection> waypoints,
    required DateTime departureStart,
    required DateTime departureEnd,
    int radiusMeters = 50000,
    int timeToleranceMinutes = 180,
    int minimumRemainingSeats = 1,
    String? vehicleType,
    bool carpoolAllowed = false,
    bool driverVerified = false,
    String sortBy = 'MATCH_SCORE',
    int page = 1,
    int size = 20,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/trips/search',
          body: {
            'startName': start.name,
            'startLatitude': start.latitude,
            'startLongitude': start.longitude,
            'endName': end.name,
            'endLatitude': end.latitude,
            'endLongitude': end.longitude,
            'waypoints': waypoints
                .map(
                  (point) => {
                    'name': point.name,
                    'latitude': point.latitude,
                    'longitude': point.longitude,
                  },
                )
                .toList(),
            'departureStart': departureStart.toIso8601String(),
            'departureEnd': departureEnd.toIso8601String(),
            'radiusMeters': radiusMeters,
            'timeToleranceMinutes': timeToleranceMinutes,
            'minimumRemainingSeats': minimumRemainingSeats,
            if (vehicleType?.isNotEmpty == true) 'vehicleType': vehicleType,
            'carpoolAllowed': carpoolAllowed,
            'driverVerified': driverVerified,
            'sortBy': sortBy,
            'page': page,
            'size': size,
          },
        )
        as Map,
  );

  Future<Map<String, dynamic>> detail(String tripId) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/trips/$tripId/discovery-detail') as Map,
      );

  Future<TripApplicationModel> apply(
    String tripId, {
    required String message,
    required bool selfDrive,
    String? vehicleId,
  }) async {
    final data = Map<String, dynamic>.from(
      await api.post(
            '/v1/trips/$tripId/applications',
            body: {
              'message': message,
              'selfDrive': selfDrive,
              'applicantVehicleId': int.tryParse(vehicleId ?? ''),
            },
          )
          as Map,
    );
    return TripApplicationModel(
      applicationId: data['applicationId']?.toString() ?? '',
      tripId: data['tripId']?.toString() ?? tripId,
      applicantUserId: '',
      status: data['status']?.toString() ?? 'PENDING',
      message: message,
      vehicleId: vehicleId,
    );
  }

  Future<List<TripApplicationModel>> myApplications() async {
    final data = await api.get('/v1/trips/applications/my');
    return (data as List? ?? const [])
        .map(
          (e) => TripApplicationModel.fromJson(
            Map<String, dynamic>.from(e as Map),
          ),
        )
        .toList();
  }

  Future<List<TripApplicationModel>> tripApplications(String tripId) async {
    final data = await api.get('/v1/trips/$tripId/applications');
    return (data as List? ?? const [])
        .map(
          (e) => TripApplicationModel.fromJson(
            Map<String, dynamic>.from(e as Map),
          ),
        )
        .toList();
  }

  Future<TripApplicationModel> review(
    String applicationId, {
    required bool approve,
    String? reason,
  }) async => TripApplicationModel.fromJson(
    Map<String, dynamic>.from(
      await api.post(
            '/v1/trips/applications/$applicationId/'
            '${approve ? 'approve' : 'reject'}',
            body: {'reason': reason ?? ''},
          )
          as Map,
    ),
  );
}

class SosService {
  const SosService(this.api);
  final ApiClient api;

  Future<Map<String, dynamic>> create({
    required String requestId,
    required double latitude,
    required double longitude,
    required String address,
    double? accuracy,
    String? message,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/sos/events',
          body: {
            'requestId': requestId,
            'latitude': latitude,
            'longitude': longitude,
            'locationAccuracyMeters': accuracy,
            'address': address,
            'message': message,
          },
        )
        as Map,
  );
}

class ChatService {
  const ChatService(this.api);
  final ApiClient api;

  /// 获取 App 所需的同路行业务会话绑定。
  ///
  /// 最后一条消息、未读、置顶和免打扰不以这里的值为准，Flutter 会使用
  /// [TencentImClient] 从腾讯 IM SDK 获取后覆盖。该接口只保留本地
  /// conversationId、tripId/teamId、群权限以及腾讯 IM 会话 ID 的对应关系。
  Future<List<ConversationModel>> imBindings() async {
    final data = await api.get('/v1/chats/im/bindings');
    final list = data is Map
        ? (data['conversations'] as List? ?? const [])
        : const [];
    return list
        .map(
          (e) =>
              ConversationModel.fromJson(Map<String, dynamic>.from(e as Map)),
        )
        .toList();
  }

  @Deprecated('Flutter App should use imBindings + TencentImClient.conversations')
  Future<List<ConversationModel>> conversations({String? title}) async {
    final keyword = title?.trim() ?? '';
    final data = await api.get(
      '/v1/chats/conversations',
      query: keyword.isEmpty ? null : {'title': keyword},
    );
    final list = data is Map
        ? (data['conversations'] as List? ?? const [])
        : const [];
    return list
        .map(
          (e) =>
              ConversationModel.fromJson(Map<String, dynamic>.from(e as Map)),
        )
        .toList();
  }

  Future<List<Map<String, dynamic>>> messages(String id) async {
    final data = await api.get('/v1/chats/conversations/$id/messages');
    final list = data is Map
        ? (data['messages'] as List? ?? const [])
        : const [];
    return list.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }

  Future<ConversationModel> tripConversation(String tripId) async =>
      ConversationModel.fromJson(
        Map<String, dynamic>.from(
          await api.get('/v1/chats/trips/$tripId/conversation') as Map,
        ),
      );

  Future<Map<String, dynamic>> send(
    String id,
    String content, {
    String type = 'TEXT',
    Map<String, dynamic>? payload,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/chats/conversations/$id/messages',
          body: {
            'messageType': type,
            'content': content,
            'payload': payload ?? <String, dynamic>{},
          },
        )
        as Map,
  );

  Future<List<Map<String, dynamic>>> members(String id) async {
    final data = await api.get('/v1/chats/conversations/$id/members');
    return (data as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  Future<Map<String, dynamic>> settings(String id) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/chats/conversations/$id/settings') as Map,
      );

  Future<Map<String, dynamic>> updateSettings(
    String id, {
    required bool muted,
    required bool pinned,
  }) async => Map<String, dynamic>.from(
    await api.put(
          '/v1/chats/conversations/$id/settings',
          body: {'muted': muted, 'pinned': pinned},
        )
        as Map,
  );

  Future<List<Map<String, dynamic>>> joinApplications({
    String status = 'PENDING',
  }) async {
    final data = await api.get(
      '/v1/chats/join-applications',
      query: {'status': status},
    );
    return (data as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  Future<List<Map<String, dynamic>>> receivedTeamApplications({
    String status = 'PENDING',
  }) async {
    final data = await api.get(
      '/v1/teams/applications/received',
      query: {'status': status},
    );
    return (data as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  Future<Map<String, dynamic>> reviewTeamApplication(
    String applicationId,
    String decision,
  ) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/teams/applications/$applicationId/review',
          body: {'reviewAction': decision, 'reviewMessage': ''},
        )
        as Map,
  );

  Future<Map<String, dynamic>> reviewJoinApplication(
    String id,
    String decision,
  ) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/chats/join-applications/$id/review',
          body: {'decision': decision},
        )
        as Map,
  );

  Future<Map<String, dynamic>> privatePermission(String userId) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/chats/private/permission/$userId') as Map,
      );

  Future<ConversationModel> startPrivate(String userId) async =>
      ConversationModel.fromJson(
        Map<String, dynamic>.from(
          await api.post('/v1/chats/private/$userId/start') as Map,
        ),
      );

  Future<Map<String, dynamic>> privateConversationPermission(String id) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/chats/conversations/$id/private-permission') as Map,
      );

  Future<void> hideConversation(String id) async {
    await api.delete('/v1/chats/conversations/$id/visibility/me');
  }

  @Deprecated('Use hideConversation; message history is no longer cleared')
  Future<void> clearLocalMessages(String id) => hideConversation(id);

  Future<Map<String, dynamic>> groupWorkspace(String id) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/chats/conversations/$id/group') as Map,
      );

  Future<Map<String, dynamic>> createGroupItem(
    String id, {
    required String type,
    required String title,
    String? content,
    Map<String, dynamic>? payload,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/chats/conversations/$id/group/items',
          body: {
            'itemType': type,
            'title': title,
            'content': content,
            'payload': payload ?? <String, dynamic>{},
          },
        )
        as Map,
  );

  Future<Map<String, dynamic>> updateGroupItem(
    String id,
    String itemId, {
    required String type,
    required String title,
    String? content,
    Map<String, dynamic>? payload,
  }) async => Map<String, dynamic>.from(
    await api.put(
          '/v1/chats/conversations/$id/group/items/$itemId',
          body: {
            'itemType': type,
            'title': title,
            'content': content,
            'payload': payload ?? <String, dynamic>{},
          },
        )
        as Map,
  );

  Future<List<Map<String, dynamic>>> vote(
    String id,
    String pollId,
    String optionKey,
  ) async {
    final data = await api.post(
      '/v1/chats/conversations/$id/group/polls/$pollId/vote',
      body: {'optionKey': optionKey},
    );
    return (data as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  Future<Map<String, dynamic>> groupItem(String id, String itemId) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/chats/conversations/$id/group/items/$itemId') as Map,
      );

  Future<Map<String, dynamic>> closePoll(String id, String itemId) async =>
      Map<String, dynamic>.from(
        await api.post('/v1/chats/conversations/$id/group/polls/$itemId/close')
            as Map,
      );

  Future<List<Map<String, dynamic>>> shareLocation(
    String id, {
    required double latitude,
    required double longitude,
    double? speed,
    bool sharing = true,
  }) async {
    final data = await api.post(
      '/v1/chats/conversations/$id/group/location',
      body: {
        'latitude': latitude,
        'longitude': longitude,
        'speed': speed,
        'sharing': sharing,
      },
    );
    return (data as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  Future<Map<String, dynamic>> report(
    String id, {
    required String targetType,
    required String targetId,
    required String reportType,
    required String reason,
    Map<String, dynamic>? evidence,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/chats/conversations/$id/group/reports',
          body: {
            'targetType': targetType,
            'targetId': targetId,
            'reportType': reportType,
            'reason': reason,
            'evidence': evidence ?? <String, dynamic>{},
          },
        )
        as Map,
  );

  /// 邀请用户加入群聊。后端会校验操作者是否为当前有效群成员。
  Future<void> addGroupMember(String id, String userId) => api.post(
    '/v1/chats/groups/$id/members',
    body: {'userId': int.parse(userId)},
  );

  /// 队长移除群成员，并同步腾讯 IM 群成员状态。
  Future<void> removeMember(String id, String userId) =>
      api.delete('/v1/chats/groups/$id/members/$userId');

  Future<void> updateMemberRole(String id, String userId, String role) =>
      api.put(
        '/v1/chats/conversations/$id/group/members/$userId/role',
        body: {'role': role},
      );

  Future<Map<String, dynamic>> createTripConfirmation(String id) async =>
      Map<String, dynamic>.from(
        await api.post('/v1/chats/conversations/$id/group/trip-confirmations')
            as Map,
      );

  Future<Map<String, dynamic>> tripConfirmation(
    String id,
    String confirmationId,
  ) async => Map<String, dynamic>.from(
    await api.get(
          '/v1/chats/conversations/$id/group/trip-confirmations/$confirmationId',
        )
        as Map,
  );

  Future<Map<String, dynamic>> respondTripConfirmation(
    String id,
    String confirmationId,
    String status, {
    String? reason,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/chats/conversations/$id/group/trip-confirmations/$confirmationId/respond',
          body: {'status': status, 'reason': reason},
        )
        as Map,
  );

  Future<Map<String, dynamic>> startConfirmedTrip(
    String id,
    String confirmationId,
  ) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/chats/conversations/$id/group/trip-confirmations/$confirmationId/start',
        )
        as Map,
  );

  Future<Map<String, dynamic>> renameGroup(String id, String name) async =>
      Map<String, dynamic>.from(
        await api.patch(
              '/v1/chats/groups/$id',
              body: {'name': name},
            )
            as Map,
      );

  Future<void> closeGroup(String id) =>
      api.post('/v1/chats/groups/$id/dissolve');

  Future<void> exitGroup(String id) =>
      api.delete('/v1/chats/groups/$id/members/me');
}

class ChatAttachmentService {
  const ChatAttachmentService(this.api);
  final ApiClient api;

  Future<Map<String, dynamic>> upload({
    required String conversationId,
    required String fileName,
    required String contentType,
    required Uint8List bytes,
    required bool image,
  }) async {
    if (bytes.isEmpty) throw const ApiException('附件内容为空');
    final maxBytes = image ? 10 * 1024 * 1024 : 20 * 1024 * 1024;
    if (bytes.length > maxBytes) {
      throw ApiException(image ? '单张图片不能超过 10MB' : '聊天附件不能超过 20MB');
    }
    final bizType = image ? 'CHAT_IMAGE' : 'CHAT_FILE';
    final presign = Map<String, dynamic>.from(
      await api.post(
            '/v1/storage/presign-upload',
            body: {
              'fileName': fileName,
              'contentType': contentType,
              'fileSize': bytes.length,
              'bizType': bizType,
              'bizId': conversationId,
            },
          )
          as Map,
    );
    await api.putBytes(
      presign['uploadUrl'].toString(),
      bytes,
      contentType: contentType,
    );
    final confirmed = Map<String, dynamic>.from(
      await api.post(
            '/v1/storage/confirm-upload',
            body: {
              'bucket': presign['bucket'],
              'objectKey': presign['objectKey'],
              'fileName': fileName,
              'contentType': contentType,
              'fileSize': bytes.length,
              'bizType': bizType,
              'bizId': conversationId,
            },
          )
          as Map,
    );
    return {...confirmed, 'fileName': fileName, 'contentType': contentType};
  }

  Future<String> downloadUrl(Object fileId) async {
    final data = Map<String, dynamic>.from(
      await api.get('/v1/chats/media/${fileId.toString()}/access-url')
          as Map,
    );
    return data['downloadUrl'].toString();
  }
}

class StorageUploadService {
  const StorageUploadService(this.api);
  final ApiClient api;

  Future<Map<String, dynamic>> upload({
    required String bizType,
    required String fileName,
    required Uint8List bytes,
    String? bizId,
    String? contentType,
  }) async {
    if (bytes.isEmpty) throw const ApiException('图片内容为空');
    if (bytes.length > 10 * 1024 * 1024) {
      throw const ApiException('单张图片不能超过 10MB');
    }
    final resolvedContentType = contentType ?? imageContentType(fileName);
    final presign = Map<String, dynamic>.from(
      await api.post(
            '/v1/storage/presign-upload',
            body: {
              'fileName': fileName,
              'contentType': resolvedContentType,
              'fileSize': bytes.length,
              'bizType': bizType,
              'bizId': bizId,
            },
          )
          as Map,
    );
    await api.putBytes(
      presign['uploadUrl'].toString(),
      bytes,
      contentType: resolvedContentType,
    );
    final confirmed = Map<String, dynamic>.from(
      await api.post(
            '/v1/storage/confirm-upload',
            body: {
              'bucket': presign['bucket'],
              'objectKey': presign['objectKey'],
              'fileName': fileName,
              'contentType': resolvedContentType,
              'fileSize': bytes.length,
              'bizType': bizType,
              'bizId': bizId,
            },
          )
          as Map,
    );
    return confirmed;
  }

  Future<String> downloadUrlByObjectKey(String objectKey) async {
    if (objectKey.trim().isEmpty || objectKey.startsWith('data:')) return '';
    final data = Map<String, dynamic>.from(
      await api.get(
            '/v1/storage/presign-download',
            query: {'objectKey': objectKey},
          )
          as Map,
    );
    return data['downloadUrl']?.toString() ?? '';
  }

  static String imageContentType(String fileName) {
    final lower = fileName.toLowerCase();
    if (lower.endsWith('.png')) return 'image/png';
    if (lower.endsWith('.webp')) return 'image/webp';
    if (lower.endsWith('.heic')) return 'image/heic';
    if (lower.endsWith('.heif')) return 'image/heif';
    return 'image/jpeg';
  }
}

class UserDiscoveryService {
  const UserDiscoveryService(this.api);
  final ApiClient api;

  Future<List<Map<String, dynamic>>> search({
    String keyword = '',
    int page = 1,
    int size = 20,
  }) async {
    final data = await api.get(
      '/v1/users/search',
      query: {'keyword': keyword.trim(), 'page': '$page', 'size': '$size'},
    );
    return (data as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }
}

class UserProfileService {
  const UserProfileService(this.api);
  final ApiClient api;

  Future<Map<String, dynamic>> me() async =>
      Map<String, dynamic>.from(await api.get('/v1/users/me') as Map);

  Future<Map<String, dynamic>> update(Map<String, dynamic> values) async =>
      Map<String, dynamic>.from(
        await api.put('/v1/users/me/profile', body: values) as Map,
      );

  Future<Map<String, dynamic>> publicProfile(String userId) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/users/$userId/public-profile') as Map,
      );

  Future<Map<String, dynamic>> homepage(String userId) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/users/$userId/homepage') as Map,
      );

  Future<Map<String, dynamic>> privacySettings() async =>
      Map<String, dynamic>.from(
        await api.get('/v1/users/me/privacy-settings') as Map,
      );

  Future<Map<String, dynamic>> updatePrivacySettings(
    Map<String, dynamic> values,
  ) async => Map<String, dynamic>.from(
    await api.put('/v1/users/me/privacy-settings', body: values) as Map,
  );

  Future<Map<String, dynamic>> account() async =>
      Map<String, dynamic>.from(await api.get('/v1/auth/me') as Map);
}

class DrivingLicenseService {
  const DrivingLicenseService(this.api);
  final ApiClient api;

  Future<Map<String, dynamic>> latest() async => Map<String, dynamic>.from(
    await api.get('/v1/users/me/certifications/latest') as Map,
  );

  Future<Map<String, dynamic>> submit({
    required String holderName,
    required String licenseNo,
    required String vehicleClass,
    required String licenseFrontImageKey,
    required String licenseBackImageKey,
    String issuingAuthority = '',
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/users/me/certifications',
          body: {
            'holderName': holderName,
            'licenseNo': licenseNo,
            'vehicleClass': vehicleClass,
            'issuingAuthority': issuingAuthority,
            'licenseFrontImageKey': licenseFrontImageKey,
            'licenseBackImageKey': licenseBackImageKey,
            'recognitionSource': 'MANUAL_UPLOAD',
          },
        )
        as Map,
  );
}

class VehicleService {
  const VehicleService(this.api);
  final ApiClient api;
  Future<List<VehicleModel>> mine() async {
    final data = await api.get('/v1/vehicles/me');
    final list = data is Map
        ? (data['vehicles'] as List? ?? const [])
        : const [];
    return list
        .map((e) => VehicleModel.fromJson(Map<String, dynamic>.from(e as Map)))
        .toList();
  }

  Future<void> setDefault(String id) => api.put('/v1/vehicles/$id/default');

  Future<void> remove(String id) => api.delete('/v1/vehicles/$id');

  Future<void> deleteRejectedHistory(String id) =>
      api.delete('/v1/vehicles/$id/rejected-certification-history');

  Future<Map<String, dynamic>> authEligibility(String plateNumber) async =>
      Map<String, dynamic>.from(
        await api.get(
              '/v1/vehicle/auth/eligibility',
              query: {'plateNumber': plateNumber},
            )
            as Map,
      );

  Future<VehicleAuthStatusModel> authStatus() async =>
      VehicleAuthStatusModel.fromJson(
        Map<String, dynamic>.from(
          await api.get('/v1/vehicle/auth/status') as Map,
        ),
      );

  Future<VehicleAuthStatusModel> submitAuth({
    required String plateNumber,
    required String vehicleBrand,
    required String vehicleModel,
    required String vehicleColor,
    required List<String> registrationLicenseImages,
    required List<String> vehicleImages,
  }) async => VehicleAuthStatusModel.fromJson(
    Map<String, dynamic>.from(
      await api.post(
            '/v1/vehicle/auth/submit',
            body: {
              'plateNumber': plateNumber,
              'vehicleBrand': vehicleBrand,
              'vehicleModel': vehicleModel,
              'vehicleColor': vehicleColor,
              'registrationLicenseImages': registrationLicenseImages,
              'vehicleImages': vehicleImages,
            },
          )
          as Map,
    ),
  );

  Future<VehicleModel> create({
    required String plateNo,
    required String brand,
    required String model,
    required String color,
  }) async => VehicleModel.fromJson(
    Map<String, dynamic>.from(
      await api.post(
            '/v1/vehicles',
            body: {
              'plateNo': plateNo,
              'brand': brand,
              'model': model,
              'vehicleType': 'SUV',
              'color': color,
              'seatCount': 5,
              'energyType': '汽油',
            },
          )
          as Map,
    ),
  );
}

class GrowthService {
  const GrowthService(this.api);
  final ApiClient api;
  Future<Map<String, dynamic>> summary() async =>
      Map<String, dynamic>.from(await api.get('/v1/growth/me') as Map);
  Future<List<Map<String, dynamic>>> logs() async {
    final data = Map<String, dynamic>.from(
      await api.get('/v1/growth/me/logs', query: {'page': '1', 'size': '20'})
          as Map,
    );
    return (data['records'] as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  Future<Map<String, dynamic>> badges() async =>
      Map<String, dynamic>.from(await api.get('/v1/growth/me/badges') as Map);
}

class InviteService {
  const InviteService(this.api);
  final ApiClient api;
  Future<Map<String, dynamic>> code() async =>
      Map<String, dynamic>.from(await api.get('/v1/invites/code') as Map);
  Future<Map<String, dynamic>> summary() async =>
      Map<String, dynamic>.from(await api.get('/v1/invites/me/summary') as Map);
  Future<Map<String, dynamic>> qr() async =>
      Map<String, dynamic>.from(await api.get('/v1/invites/me/qr') as Map);
  Future<Map<String, dynamic>> validateQr(String token) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/invites/qr/validate', query: {'token': token})
            as Map,
      );
}

class CustomerSupportService {
  const CustomerSupportService(this.api);
  final ApiClient api;

  Future<Map<String, dynamic>> createTicket({
    required String title,
    required String content,
    bool complaint = false,
  }) async => Map<String, dynamic>.from(
    await api.post(
          complaint
              ? '/v1/customer-service/complaints'
              : '/v1/customer-service/tickets',
          body: {
            'scene': complaint ? 'COMPLAINT' : 'GENERAL',
            'targetType': '',
            'targetId': '',
            'title': title,
            'content': content,
            'imageKeys': const <String>[],
            'requestId': 'APP-CS-${DateTime.now().microsecondsSinceEpoch}',
          },
        )
        as Map,
  );

  Future<List<Map<String, dynamic>>> tickets() async {
    final data = Map<String, dynamic>.from(
      await api.get(
            '/v1/customer-service/tickets/me',
            query: {'page': '1', 'size': '30'},
          )
          as Map,
    );
    return (data['records'] as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  Future<List<Map<String, dynamic>>> notifications() async {
    final data = Map<String, dynamic>.from(
      await api.get(
            '/v1/notifications/me',
            query: {'scene': 'CUSTOMER_SERVICE', 'page': '1', 'size': '30'},
          )
          as Map,
    );
    return (data['records'] as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  Future<void> markRead(Object id) => api.post('/v1/notifications/$id/read');
}
