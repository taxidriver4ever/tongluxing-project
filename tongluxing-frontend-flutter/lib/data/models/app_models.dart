class LoginSession {
  const LoginSession({required this.token, this.refreshToken, this.userId});
  final String token;
  final String? refreshToken;
  final String? userId;
  factory LoginSession.fromJson(Map<String, dynamic> json) => LoginSession(
    token: json['token']?.toString() ?? '',
    refreshToken: json['refreshToken']?.toString(),
    userId: json['userId']?.toString(),
  );
}

class TokenRefreshSession {
  const TokenRefreshSession({required this.token, required this.refreshToken});

  final String token;
  final String refreshToken;

  factory TokenRefreshSession.fromJson(Map<String, dynamic> json) =>
      TokenRefreshSession(
        token: json['token']?.toString() ?? '',
        refreshToken: json['refreshToken']?.toString() ?? '',
      );
}

class LocationSelection {
  const LocationSelection({
    this.historyId,
    required this.name,
    required this.address,
    required this.latitude,
    required this.longitude,
    this.distanceMeters,
  });
  final String? historyId;
  final String name;
  final String address;
  final double latitude;
  final double longitude;
  final int? distanceMeters;

  String? get distanceLabel {
    final meters = distanceMeters;
    if (meters == null) return null;
    if (meters < 1000) return '$meters m';
    final kilometers = meters / 1000;
    return '${kilometers < 10 ? kilometers.toStringAsFixed(1) : kilometers.round()} km';
  }

  factory LocationSelection.fromJson(Map<String, dynamic> json) =>
      LocationSelection(
        historyId: json['historyId']?.toString(),
        name: json['name']?.toString() ?? '未命名地点',
        address: json['address']?.toString() ?? '',
        latitude: _double(json['latitude']),
        longitude: _double(json['longitude']),
        distanceMeters: _int(json['distanceMeters']),
      );

  Map<String, dynamic> toJson() => {
    'name': name,
    'address': address,
    'latitude': latitude,
    'longitude': longitude,
  };
}

class TripDraftModel {
  const TripDraftModel({
    required this.id,
    this.title = '',
    this.status = 'DRAFT',
    this.startTime,
    this.startLocation,
    this.destination,
    this.description = '',
    this.expectPeople = 5,
    this.durationDays = 1,
    this.updatedAt,
    this.waypoints = const [],
    this.route,
  });
  final String id;
  final String title;
  final String status;
  final String? startTime;
  final LocationSelection? startLocation;
  final LocationSelection? destination;
  final String description;
  final int expectPeople;
  final int durationDays;
  final String? updatedAt;
  final List<TripDraftWaypointModel> waypoints;
  final TripDraftRouteModel? route;

  factory TripDraftModel.fromJson(Map<String, dynamic> json) => TripDraftModel(
    id: json['draftId']?.toString() ?? '',
    title: json['title']?.toString() ?? '',
    status: json['status']?.toString() ?? 'DRAFT',
    startTime: json['startTime']?.toString(),
    startLocation: json['startLocation'] is Map
        ? LocationSelection.fromJson(
            Map<String, dynamic>.from(json['startLocation'] as Map),
          )
        : null,
    destination: json['destination'] is Map
        ? LocationSelection.fromJson(
            Map<String, dynamic>.from(json['destination'] as Map),
          )
        : null,
    description: json['description']?.toString() ?? '',
    expectPeople: _int(json['expectPeople']) ?? 5,
    durationDays: _int(json['durationDays']) ?? 1,
    updatedAt: json['updatedAt']?.toString(),
    waypoints: (json['waypoints'] as List? ?? const [])
        .whereType<Map>()
        .map(
          (e) => TripDraftWaypointModel.fromJson(Map<String, dynamic>.from(e)),
        )
        .toList(),
    route: json['route'] is Map
        ? TripDraftRouteModel.fromJson(
            Map<String, dynamic>.from(json['route'] as Map),
          )
        : null,
  );
}

class TripDraftWaypointModel {
  const TripDraftWaypointModel({
    required this.id,
    required this.location,
    required this.type,
  });
  final String id;
  final LocationSelection location;
  final String type;
  factory TripDraftWaypointModel.fromJson(Map<String, dynamic> json) =>
      TripDraftWaypointModel(
        id: json['waypointId']?.toString() ?? '',
        location: LocationSelection.fromJson(json),
        type: json['type']?.toString() ?? 'REST',
      );
}

class TripDraftRouteModel {
  const TripDraftRouteModel({
    required this.status,
    required this.points,
    this.distanceMeters,
    this.durationMinutes,
    this.providerType,
  });
  final String status;
  final List<LocationSelection> points;
  final int? distanceMeters;
  final int? durationMinutes;
  final String? providerType;
  factory TripDraftRouteModel.fromJson(Map<String, dynamic> json) {
    final points = <LocationSelection>[];
    if (json['startLocation'] is Map) {
      points.add(
        LocationSelection.fromJson(
          Map<String, dynamic>.from(json['startLocation'] as Map),
        ),
      );
    }
    points.addAll(
      (json['waypoints'] as List? ?? const []).whereType<Map>().map(
        (e) => LocationSelection.fromJson(Map<String, dynamic>.from(e)),
      ),
    );
    if (json['destination'] is Map) {
      points.add(
        LocationSelection.fromJson(
          Map<String, dynamic>.from(json['destination'] as Map),
        ),
      );
    }
    return TripDraftRouteModel(
      status: json['status']?.toString() ?? 'STALE',
      points: points,
      distanceMeters: _int(json['totalDistance']),
      durationMinutes: _int(json['estimatedDuration']),
      providerType: json['providerType']?.toString(),
    );
  }
}

class TripModel {
  const TripModel({
    required this.id,
    required this.title,
    required this.startName,
    required this.endName,
    required this.status,
    this.ownerUserId,
    this.departureTime,
    this.distanceMeters,
    this.joinedVehicles = 0,
    this.maxVehicles = 0,
    this.description,
    this.waypoints = const [],
    this.startLocation,
    this.endLocation,
    this.routePolyline,
  });
  final String id;
  final String title;
  final String startName;
  final String endName;
  final String status;
  final String? ownerUserId;
  final String? departureTime;
  final int? distanceMeters;
  final int joinedVehicles;
  final int maxVehicles;
  final String? description;
  final List<LocationSelection> waypoints;
  final LocationSelection? startLocation;
  final LocationSelection? endLocation;
  final String? routePolyline;
  factory TripModel.fromJson(Map<String, dynamic> json) => TripModel(
    id: json['tripId']?.toString() ?? json['id']?.toString() ?? '',
    title: json['title']?.toString() ?? '未命名行程',
    startName:
        json['startName']?.toString() ??
        json['startLocation']?['name']?.toString() ??
        '起点',
    endName:
        json['endName']?.toString() ??
        json['endLocation']?['name']?.toString() ??
        '终点',
    status: json['status']?.toString() ?? 'PUBLISHED',
    ownerUserId: json['userId']?.toString() ?? json['ownerUserId']?.toString(),
    departureTime: json['departureTime']?.toString(),
    distanceMeters: _int(json['totalDistanceMeters'] ?? json['routeDistance']),
    joinedVehicles: _int(json['joinedVehicleCount']) ?? 0,
    maxVehicles: _int(json['maxVehicleCount']) ?? 0,
    description: json['description']?.toString(),
    waypoints: (json['waypoints'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => LocationSelection.fromJson(Map<String, dynamic>.from(e)))
        .toList(),
    startLocation: json['startLocation'] is Map
        ? LocationSelection.fromJson(
            Map<String, dynamic>.from(json['startLocation'] as Map),
          )
        : null,
    endLocation: json['endLocation'] is Map
        ? LocationSelection.fromJson(
            Map<String, dynamic>.from(json['endLocation'] as Map),
          )
        : null,
    routePolyline: json['routePolyline']?.toString(),
  );
}

class TripSettlementModel {
  const TripSettlementModel({
    required this.tripId,
    required this.status,
    required this.memberCount,
    required this.pointsPerMember,
    required this.totalPoints,
    required this.duplicate,
    required this.settledAt,
  });

  final String tripId;
  final String status;
  final int memberCount;
  final int pointsPerMember;
  final int totalPoints;
  final bool duplicate;
  final String settledAt;

  factory TripSettlementModel.fromJson(Map<String, dynamic> json) =>
      TripSettlementModel(
        tripId: json['tripId']?.toString() ?? '',
        status: json['status']?.toString() ?? 'SETTLED',
        memberCount: _int(json['memberCount']) ?? 0,
        pointsPerMember: _int(json['pointsPerMember']) ?? 0,
        totalPoints: _int(json['totalPoints']) ?? 0,
        duplicate: json['duplicate'] == true,
        settledAt: json['settledAt']?.toString() ?? '',
      );
}

class CompanionMatchModel {
  const CompanionMatchModel({
    required this.matchId,
    required this.tripId,
    required this.userId,
    required this.title,
    required this.startName,
    required this.endName,
    required this.departureTime,
    required this.status,
    required this.matchScore,
    required this.overlapRate,
    required this.departureGapMinutes,
    required this.joinable,
    this.teamId,
    this.currentMembers,
    this.maxMembers,
  });
  final String matchId;
  final String tripId;
  final String userId;
  final String title;
  final String startName;
  final String endName;
  final String departureTime;
  final String status;
  final int matchScore;
  final int overlapRate;
  final int departureGapMinutes;
  final bool joinable;
  final String? teamId;
  final int? currentMembers;
  final int? maxMembers;

  factory CompanionMatchModel.fromJson(Map<String, dynamic> json) =>
      CompanionMatchModel(
        matchId: json['matchId']?.toString() ?? '',
        tripId: json['tripId']?.toString() ?? '',
        userId: json['userId']?.toString() ?? '',
        title: json['title']?.toString() ?? '同行行程',
        startName: json['startName']?.toString() ?? '起点',
        endName: json['endName']?.toString() ?? '终点',
        departureTime: json['departureTime']?.toString() ?? '',
        status: json['status']?.toString() ?? 'PUBLISHED',
        matchScore: _int(json['matchScore']) ?? 0,
        overlapRate: _int(json['overlapRate']) ?? 0,
        departureGapMinutes: _int(json['departureGapMinutes']) ?? 0,
        joinable: json['joinable'] == true,
        teamId: json['teamId']?.toString(),
        currentMembers: _int(json['currentMemberCount']),
        maxMembers: _int(json['maxMemberCount']),
      );
}

class ConversationModel {
  const ConversationModel({
    required this.id,
    required this.name,
    this.preview = '',
    this.time = '',
    this.unread = 0,
    this.bizType = '',
    this.bizId = '',
    this.status = 'ACTIVE',
    this.providerType = 'LOCAL',
    this.pinned = false,
    this.muted = false,
  });
  final String id;
  final String name;
  final String preview;
  final String time;
  final int unread;
  final String bizType;
  final String bizId;
  final String status;
  final String providerType;
  final bool pinned;
  final bool muted;
  factory ConversationModel.fromJson(Map<String, dynamic> json) =>
      ConversationModel(
        id: json['conversationId']?.toString() ?? '',
        name: json['conversationName']?.toString() ?? '车队会话',
        preview: json['lastMessagePreview']?.toString() ?? '',
        time: json['lastMessageAt']?.toString() ?? '',
        unread: _int(json['unreadCount']) ?? 0,
        bizType: json['bizType']?.toString() ?? '',
        bizId: json['bizId']?.toString() ?? '',
        status: json['conversationStatus']?.toString() ?? 'ACTIVE',
        providerType: json['providerType']?.toString() ?? 'LOCAL',
        pinned: json['pinned'] == true,
        muted: json['muted'] == true,
      );
}

class VehicleModel {
  const VehicleModel({
    required this.id,
    required this.brand,
    required this.model,
    required this.plate,
    this.color = '',
    this.status = 'UNSUBMITTED',
    this.isDefault = false,
    this.certificationId,
    this.rejectReason = '',
    this.certificationSubmittedAt,
    this.certificationReviewedAt,
  });
  final String id;
  final String brand;
  final String model;
  final String plate;
  final String color;
  final String status;
  final bool isDefault;
  final String? certificationId;
  final String rejectReason;
  final DateTime? certificationSubmittedAt;
  final DateTime? certificationReviewedAt;
  factory VehicleModel.fromJson(Map<String, dynamic> json) => VehicleModel(
    id: json['vehicleId']?.toString() ?? '',
    brand: json['brand']?.toString() ?? '车辆',
    model: json['model']?.toString() ?? '',
    plate: json['plateNoMask']?.toString() ?? '未设置车牌',
    color: json['color']?.toString() ?? '',
    status: json['certificationStatus']?.toString() ?? 'UNSUBMITTED',
    isDefault: json['isDefault'] == true,
    certificationId: json['certificationId']?.toString(),
    rejectReason: json['rejectReason']?.toString() ?? '',
    certificationSubmittedAt: DateTime.tryParse(
      json['certificationSubmittedAt']?.toString() ?? '',
    ),
    certificationReviewedAt: DateTime.tryParse(
      json['certificationReviewedAt']?.toString() ?? '',
    ),
  );
}

class VehicleAuthStatusModel {
  const VehicleAuthStatusModel({
    this.applyId,
    this.vehicleId,
    required this.status,
    this.reason = '',
    this.submitTime,
    this.auditTime,
  });

  final String? applyId;
  final String? vehicleId;
  final String status;
  final String reason;
  final DateTime? submitTime;
  final DateTime? auditTime;

  bool get submitted => status != 'UNSUBMITTED';

  factory VehicleAuthStatusModel.fromJson(Map<String, dynamic> json) =>
      VehicleAuthStatusModel(
        applyId: json['applyId']?.toString(),
        vehicleId: json['vehicleId']?.toString(),
        status: json['status']?.toString() ?? 'UNSUBMITTED',
        reason: json['reason']?.toString() ?? '',
        submitTime: DateTime.tryParse(json['submitTime']?.toString() ?? ''),
        auditTime: DateTime.tryParse(json['auditTime']?.toString() ?? ''),
      );
}

int? _int(dynamic value) =>
    value is int ? value : int.tryParse(value?.toString() ?? '');

double _double(dynamic value) =>
    value is num ? value.toDouble() : double.tryParse('$value') ?? 0;
