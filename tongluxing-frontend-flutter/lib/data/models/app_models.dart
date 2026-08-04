import 'dart:convert';

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
    this.id,
    this.historyId,
    this.poiId,
    this.cityCode,
    this.district,
    this.poiType,
    required this.name,
    required this.address,
    required this.latitude,
    required this.longitude,
    this.distanceMeters,
  });
  final String? id;
  final String? historyId;
  final String? poiId;
  final String? cityCode;
  final String? district;
  final String? poiType;
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
        id: (json['waypointId'] ?? json['nodeId'] ?? json['id'])?.toString(),
        historyId: json['historyId']?.toString(),
        poiId: json['poiId']?.toString(),
        cityCode: json['cityCode']?.toString(),
        district: json['district']?.toString(),
        poiType: json['poiType']?.toString(),
        name: (json['name'] ?? json['placeName'])?.toString() ?? '未命名地点',
        address: (json['address'] ?? json['placeAddress'])?.toString() ?? '',
        latitude: _double(json['latitude'] ?? json['lat']),
        longitude: _double(json['longitude'] ?? json['lng']),
        distanceMeters: _int(json['distanceMeters']),
      );

  Map<String, dynamic> toJson() => {
    if (poiId?.isNotEmpty == true) 'poiId': poiId,
    'name': name,
    'address': address,
    'latitude': latitude,
    'longitude': longitude,
    if (cityCode?.isNotEmpty == true) 'cityCode': cityCode,
    if (district?.isNotEmpty == true) 'district': district,
    if (poiType?.isNotEmpty == true) 'poiType': poiType,
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
    this.coverImageKey = '',
    this.expectPeople = 5,
    this.durationDays = 1,
    this.vehicleRequirements = const ['不限'],
    this.budgetDescription = '',
    this.notes = '',
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
  final String coverImageKey;
  final int expectPeople;
  final int durationDays;
  final List<String> vehicleRequirements;
  final String budgetDescription;
  final String notes;
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
    coverImageKey: json['coverImageKey']?.toString() ?? '',
    expectPeople: _int(json['expectPeople']) ?? 5,
    durationDays: _int(json['durationDays']) ?? 1,
    vehicleRequirements: (json['vehicleRequirements'] as List? ?? const ['不限'])
        .map((e) => e.toString())
        .toList(),
    budgetDescription: json['budgetDescription']?.toString() ?? '',
    notes: json['notes']?.toString() ?? '',
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
    this.stayMinutes = 0,
    this.remark = '',
  });
  final String id;
  final LocationSelection location;
  final String type;
  final int stayMinutes;
  final String remark;
  factory TripDraftWaypointModel.fromJson(Map<String, dynamic> json) =>
      TripDraftWaypointModel(
        id: json['waypointId']?.toString() ?? '',
        location: LocationSelection.fromJson(json),
        type: json['type']?.toString() ?? 'NORMAL',
        stayMinutes: _int(json['stayMinutes']) ?? 0,
        remark: json['remark']?.toString() ?? '',
      );
}

class TripDraftRouteModel {
  const TripDraftRouteModel({
    required this.status,
    required this.points,
    this.routePolyline = '',
    this.distanceMeters,
    this.durationMinutes,
    this.providerType,
  });
  final String status;
  final List<LocationSelection> points;
  final String routePolyline;
  final int? distanceMeters;
  final int? durationMinutes;
  final String? providerType;

  List<LocationSelection> get polylinePoints {
    final parsed = parseRoutePolyline(routePolyline);
    return parsed.length >= 2 ? parsed : points;
  }

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
    final durationSeconds = _int(
      json['estimatedDuration'] ?? json['routeDuration'],
    );
    return TripDraftRouteModel(
      status: (json['status'] ?? json['planStatus'])?.toString() ?? 'STALE',
      points: points,
      routePolyline:
          (json['polyline'] ?? json['routePolyline'])?.toString() ?? '',
      distanceMeters: _int(json['totalDistance'] ?? json['routeDistance']),
      durationMinutes: durationSeconds == null
          ? null
          : (durationSeconds / 60).ceil(),
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
    this.coverImageKey = '',
    this.waypoints = const [],
    this.startLocation,
    this.endLocation,
    this.routePolyline,
    this.vehicleRequirements = const ['不限'],
    this.budgetDescription = '',
    this.notes = '',
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
  final String coverImageKey;
  final List<LocationSelection> waypoints;
  final LocationSelection? startLocation;
  final LocationSelection? endLocation;
  final String? routePolyline;
  final List<String> vehicleRequirements;
  final String budgetDescription;
  final String notes;

  List<LocationSelection> get routePoints {
    final parsed = parseRoutePolyline(routePolyline);
    if (parsed.length >= 2) return parsed;
    return [?startLocation, ...waypoints, ?endLocation];
  }

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
    coverImageKey: json['coverImageKey']?.toString() ?? '',
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
    vehicleRequirements: (json['vehicleRequirements'] as List? ?? const ['不限'])
        .map((e) => e.toString())
        .toList(),
    budgetDescription: json['budgetDescription']?.toString() ?? '',
    notes: json['remark']?.toString() ?? '',
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

class TripSearchResultModel {
  const TripSearchResultModel({
    required this.tripId,
    required this.ownerUserId,
    required this.ownerNickname,
    required this.title,
    required this.startName,
    required this.endName,
    required this.departureTime,
    required this.matchScore,
    required this.currentMembers,
    required this.maxMembers,
    required this.remainingSeats,
    required this.status,
    required this.joinable,
    this.ownerAvatarImageKey = '',
    this.waypoints = const [],
    this.estimatedEndTime,
    this.vehicleSummary = '未公开车辆',
    this.startDistanceMeters = 0,
    this.endDistanceMeters = 0,
  });

  final String tripId;
  final String ownerUserId;
  final String ownerNickname;
  final String ownerAvatarImageKey;
  final String title;
  final String startName;
  final String endName;
  final List<String> waypoints;
  final String departureTime;
  final String? estimatedEndTime;
  final String vehicleSummary;
  final int matchScore;
  final int currentMembers;
  final int maxMembers;
  final int remainingSeats;
  final int startDistanceMeters;
  final int endDistanceMeters;
  final String status;
  final bool joinable;

  factory TripSearchResultModel.fromJson(Map<String, dynamic> json) =>
      TripSearchResultModel(
        tripId: json['tripId']?.toString() ?? '',
        ownerUserId: json['ownerUserId']?.toString() ?? '',
        ownerNickname: json['ownerNickname']?.toString() ?? '同路行车友',
        ownerAvatarImageKey: json['ownerAvatarImageKey']?.toString() ?? '',
        title: json['title']?.toString() ?? '同行行程',
        startName: json['startName']?.toString() ?? '起点',
        endName: json['endName']?.toString() ?? '终点',
        waypoints: (json['waypoints'] as List? ?? const [])
            .map((e) => e.toString())
            .toList(),
        departureTime: json['departureTime']?.toString() ?? '',
        estimatedEndTime: json['estimatedEndTime']?.toString(),
        vehicleSummary: json['vehicleSummary']?.toString() ?? '未公开车辆',
        matchScore: _int(json['matchScore']) ?? 0,
        currentMembers: _int(json['currentMemberCount']) ?? 0,
        maxMembers: _int(json['maxMemberCount']) ?? 0,
        remainingSeats: _int(json['remainingSeats']) ?? 0,
        startDistanceMeters: _int(json['startDistanceMeters']) ?? 0,
        endDistanceMeters: _int(json['endDistanceMeters']) ?? 0,
        status: json['status']?.toString() ?? 'PUBLISHED',
        joinable: json['joinable'] == true,
      );
}

class DiscoverOwnerModel {
  const DiscoverOwnerModel({
    required this.userId,
    required this.nickname,
    this.avatarImageKey = '',
    this.levelCode = 'LV1',
    this.certificationStatus = 'UNVERIFIED',
    this.rating = 0,
    this.totalTripCount = 0,
    this.totalDistanceMeters = 0,
    this.lastActiveAt = '',
    this.badgeCount = 0,
    this.followed = false,
  });

  final String userId;
  final String nickname;
  final String avatarImageKey;
  final String levelCode;
  final String certificationStatus;
  final double rating;
  final int totalTripCount;
  final int totalDistanceMeters;
  final String lastActiveAt;
  final int badgeCount;
  final bool followed;

  factory DiscoverOwnerModel.fromJson(Map<String, dynamic> json) =>
      DiscoverOwnerModel(
        userId: json['userId']?.toString() ?? '',
        nickname: json['nickname']?.toString() ?? '同路行车友',
        avatarImageKey: json['avatarImageKey']?.toString() ?? '',
        levelCode: json['levelCode']?.toString() ?? 'LV1',
        certificationStatus:
            json['certificationStatus']?.toString() ?? 'UNVERIFIED',
        rating: (json['rating'] as num?)?.toDouble() ?? 0,
        totalTripCount: _int(json['totalTripCount']) ?? 0,
        totalDistanceMeters: _int(json['totalDistanceMeters']) ?? 0,
        lastActiveAt: json['lastActiveAt']?.toString() ?? '',
        badgeCount: _int(json['badgeCount']) ?? 0,
        followed: json['followed'] == true,
      );
}

class TripDiscoverModel {
  const TripDiscoverModel({
    required this.tripId,
    required this.title,
    required this.status,
    required this.startName,
    required this.endName,
    required this.departureTime,
    required this.owner,
    this.waypoints = const [],
    this.estimatedDays = 1,
    this.description = '',
    this.joinedVehicleCount = 0,
    this.maxVehicleCount = 0,
    this.memberCount = 0,
    this.maxMemberCount = 0,
    this.remainingSeats = 0,
    this.matchScore,
    this.distanceMeters,
    this.tags = const [],
    this.coverImageKey = '',
    this.relationshipStatus = 'NONE',
  });

  final String tripId;
  final String title;
  final String status;
  final String startName;
  final List<String> waypoints;
  final String endName;
  final String departureTime;
  final int estimatedDays;
  final String description;
  final int joinedVehicleCount;
  final int maxVehicleCount;
  final int memberCount;
  final int maxMemberCount;
  final int remainingSeats;
  final int? matchScore;
  final int? distanceMeters;
  final List<String> tags;
  final String coverImageKey;
  final String relationshipStatus;
  final DiscoverOwnerModel owner;

  factory TripDiscoverModel.fromJson(Map<String, dynamic> json) =>
      TripDiscoverModel(
        tripId: json['tripId']?.toString() ?? '',
        title: json['title']?.toString() ?? '同行行程',
        status: json['status']?.toString() ?? 'PUBLISHED',
        startName: json['startName']?.toString() ?? '起点',
        waypoints: (json['waypoints'] as List? ?? const [])
            .map((e) => e.toString())
            .toList(),
        endName: json['endName']?.toString() ?? '终点',
        departureTime: json['departureTime']?.toString() ?? '',
        estimatedDays: _int(json['estimatedDays']) ?? 1,
        description: json['description']?.toString() ?? '',
        joinedVehicleCount: _int(json['joinedVehicleCount']) ?? 0,
        maxVehicleCount: _int(json['maxVehicleCount']) ?? 0,
        memberCount: _int(json['memberCount']) ?? 0,
        maxMemberCount: _int(json['maxMemberCount']) ?? 0,
        remainingSeats: _int(json['remainingSeats']) ?? 0,
        matchScore: _int(json['matchScore']),
        distanceMeters: _int(json['distanceMeters']),
        tags: (json['tags'] as List? ?? const [])
            .map((e) => e.toString())
            .toList(),
        coverImageKey: json['coverImageKey']?.toString() ?? '',
        relationshipStatus: json['relationshipStatus']?.toString() ?? 'NONE',
        owner: DiscoverOwnerModel.fromJson(
          Map<String, dynamic>.from(json['owner'] as Map? ?? const {}),
        ),
      );
}

class TripPublicMemberModel {
  const TripPublicMemberModel({
    required this.userId,
    required this.nickname,
    this.avatarImageKey = '',
    this.role = 'MEMBER',
    this.certificationStatus = 'UNVERIFIED',
    this.totalTripCount = 0,
    this.totalDistanceMeters = 0,
  });
  final String userId;
  final String nickname;
  final String avatarImageKey;
  final String role;
  final String certificationStatus;
  final int totalTripCount;
  final int totalDistanceMeters;

  factory TripPublicMemberModel.fromJson(Map<String, dynamic> json) =>
      TripPublicMemberModel(
        userId: json['userId']?.toString() ?? '',
        nickname: json['nickname']?.toString() ?? '车队成员',
        avatarImageKey: json['avatarImageKey']?.toString() ?? '',
        role: json['role']?.toString() ?? 'MEMBER',
        certificationStatus:
            json['certificationStatus']?.toString() ?? 'UNVERIFIED',
        totalTripCount: _int(json['totalTripCount']) ?? 0,
        totalDistanceMeters: _int(json['totalDistanceMeters']) ?? 0,
      );
}

class TripPublicDetailModel {
  const TripPublicDetailModel({
    required this.trip,
    required this.owner,
    required this.members,
    this.routePolyline = '',
    this.startLatitude,
    this.startLongitude,
    this.endLatitude,
    this.endLongitude,
    this.routeDistanceMeters = 0,
    this.routeDurationSeconds = 0,
    this.vehicleRequirements = const ['不限'],
    this.budgetDescription = '',
    this.notes = '',
    this.announcement = '',
    this.joinRequirement = '',
    this.ownerTrip = false,
    this.allowConsultation = false,
    this.allowApply = false,
    this.joinable = false,
    this.favorited = false,
  });
  final TripDiscoverModel trip;
  final DiscoverOwnerModel owner;
  final List<TripPublicMemberModel> members;
  final String routePolyline;
  final double? startLatitude;
  final double? startLongitude;
  final double? endLatitude;
  final double? endLongitude;
  final int routeDistanceMeters;
  final int routeDurationSeconds;
  final List<String> vehicleRequirements;
  final String budgetDescription;
  final String notes;
  final String announcement;
  final String joinRequirement;
  final bool ownerTrip;
  final bool allowConsultation;
  final bool allowApply;
  final bool joinable;
  final bool favorited;

  List<LocationSelection> get routePoints => parseRoutePolyline(routePolyline);

  /// 公开详情地图只使用起终点定位，不解析或展示真实道路折线。
  List<LocationSelection> get mapPoints {
    final startLat = startLatitude;
    final startLng = startLongitude;
    final endLat = endLatitude;
    final endLng = endLongitude;
    if (startLat == null ||
        startLng == null ||
        endLat == null ||
        endLng == null) {
      return const [];
    }
    return [
      LocationSelection(
        name: trip.startName,
        address: '',
        latitude: startLat,
        longitude: startLng,
      ),
      LocationSelection(
        name: trip.endName,
        address: '',
        latitude: endLat,
        longitude: endLng,
      ),
    ];
  }

  factory TripPublicDetailModel.fromJson(Map<String, dynamic> json) {
    final owner = DiscoverOwnerModel.fromJson(
      Map<String, dynamic>.from(json['owner'] as Map? ?? const {}),
    );
    return TripPublicDetailModel(
      trip: TripDiscoverModel.fromJson({...json, 'owner': json['owner']}),
      owner: owner,
      members: (json['members'] as List? ?? const [])
          .map(
            (e) => TripPublicMemberModel.fromJson(
              Map<String, dynamic>.from(e as Map),
            ),
          )
          .toList(),
      // 公开详情从不消费真实道路折线。即使连接的是尚未升级的旧后端，也不把
      // 大字符串继续保留和传入地图；导航与顺路率使用各自的专用接口数据。
      routePolyline: '',
      startLatitude: (json['startLatitude'] as num?)?.toDouble(),
      startLongitude: (json['startLongitude'] as num?)?.toDouble(),
      endLatitude: (json['endLatitude'] as num?)?.toDouble(),
      endLongitude: (json['endLongitude'] as num?)?.toDouble(),
      routeDistanceMeters: _int(json['routeDistanceMeters']) ?? 0,
      routeDurationSeconds: _int(json['routeDurationSeconds']) ?? 0,
      vehicleRequirements:
          (json['vehicleRequirements'] as List? ?? const ['不限'])
              .map((e) => e.toString())
              .toList(),
      budgetDescription: json['budgetDescription']?.toString() ?? '',
      notes: json['notes']?.toString() ?? '',
      announcement: json['announcement']?.toString() ?? '',
      joinRequirement: json['joinRequirement']?.toString() ?? '',
      ownerTrip: json['ownerTrip'] == true,
      allowConsultation: json['allowConsultation'] == true,
      allowApply: json['allowApply'] == true,
      joinable: json['joinable'] == true,
      favorited: json['favorited'] == true,
    );
  }
}

class TripApplicationModel {
  const TripApplicationModel({
    required this.applicationId,
    required this.tripId,
    required this.applicantUserId,
    required this.status,
    required this.message,
    this.reviewMessage,
    this.vehicleId,
    this.createdAt = '',
  });
  final String applicationId;
  final String tripId;
  final String applicantUserId;
  final String status;
  final String message;
  final String? reviewMessage;
  final String? vehicleId;
  final String createdAt;

  factory TripApplicationModel.fromJson(Map<String, dynamic> json) =>
      TripApplicationModel(
        applicationId: json['applicationId']?.toString() ?? '',
        tripId: json['tripId']?.toString() ?? '',
        applicantUserId: json['applicantUserId']?.toString() ?? '',
        status: json['applicationStatus']?.toString() ?? 'PENDING',
        message: json['applyMessage']?.toString() ?? '',
        reviewMessage: json['reviewMessage']?.toString(),
        vehicleId: json['applicantVehicleId']?.toString(),
        createdAt: json['createdAt']?.toString() ?? '',
      );
}

/// 同路行业务会话与腾讯 IM 会话的合并模型。
///
/// [id]、[bizType]、[bizId] 等字段来自后端业务绑定；[preview]、[unread]、
/// [pinned]、[muted] 等聊天状态由腾讯 IM SDK 覆盖。这样既不重复维护聊天数据，
/// 又能保留行程、车队和权限等同路行自己的业务关系。
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
    this.providerConversationKey = '',
    this.imConversationId = '',
    this.pinned = false,
    this.muted = false,
    this.avatarImageKey = '',
    this.avatarUrl = '',
    this.peerUserId = '',
    this.relationType = 'GROUP',
    this.remainingTextMessages = 0,
    this.canSendMedia = true,
  });

  /// 后端本地业务会话 ID，群管理、举报、行程关联仍使用该值。
  final String id;

  /// SDK 群名或单聊对方昵称；SDK 暂无数据时回退到后端业务名称。
  final String name;
  final String preview;
  final String time;
  final int unread;
  final String bizType;
  final String bizId;
  final String status;
  final String providerType;

  /// 腾讯 IM 原始 GroupId；私聊通常为空。
  final String providerConversationKey;

  /// 腾讯 IM SDK 会话 ID，例如 group_trip_100 或 c2c_u_200。
  final String imConversationId;
  final bool pinned;
  final bool muted;
  final String avatarImageKey;
  final String avatarUrl;
  final String peerUserId;
  final String relationType;
  final int remainingTextMessages;
  final bool canSendMedia;

  bool get isPrivate => bizType == 'PRIVATE';

  /// 群聊调用 SDK 时使用原始 GroupId，而不是带 group_ 前缀的会话 ID。
  String get imGroupId => isPrivate ? '' : providerConversationKey;

  /// 单聊调用 SDK 时使用 u_<userId>，优先从后端返回的会话 ID 中提取。
  String get imPeerUserId {
    if (!isPrivate) return '';
    if (imConversationId.startsWith('c2c_')) {
      return imConversationId.substring('c2c_'.length);
    }
    return peerUserId.isEmpty ? '' : 'u_$peerUserId';
  }

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
        providerConversationKey:
            json['providerConversationKey']?.toString() ?? '',
        imConversationId: json['imConversationId']?.toString() ?? '',
        pinned: json['pinned'] == true,
        muted: json['muted'] == true,
        avatarImageKey: json['avatarImageKey']?.toString() ?? '',
        avatarUrl: json['avatarUrl']?.toString() ?? '',
        peerUserId: json['peerUserId']?.toString() ?? '',
        relationType: json['relationType']?.toString() ?? 'GROUP',
        remainingTextMessages: _int(json['remainingTextMessages']) ?? 0,
        canSendMedia: json['canSendMedia'] != false,
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

List<LocationSelection> parseRoutePolyline(String? raw) {
  final source = raw?.trim() ?? '';
  if (source.isEmpty) return const [];
  try {
    final decoded = jsonDecode(source);
    if (decoded is List) {
      return decoded
          .whereType<Map>()
          .map(
            (value) =>
                LocationSelection.fromJson(Map<String, dynamic>.from(value)),
          )
          .where((point) => point.latitude != 0 || point.longitude != 0)
          .toList(growable: false);
    }
  } catch (_) {
    // 兼容历史上可能保存的 "lng,lat;lng,lat" 紧凑格式。
  }
  final points = <LocationSelection>[];
  for (final pair in source.split(';')) {
    final values = pair.split(',');
    if (values.length != 2) continue;
    final longitude = double.tryParse(values[0].trim());
    final latitude = double.tryParse(values[1].trim());
    if (latitude == null || longitude == null) continue;
    points.add(
      LocationSelection(
        name: '',
        address: '',
        latitude: latitude,
        longitude: longitude,
      ),
    );
  }
  return points;
}

int? _int(dynamic value) =>
    value is int ? value : int.tryParse(value?.toString() ?? '');

double _double(dynamic value) =>
    value is num ? value.toDouble() : double.tryParse('$value') ?? 0;
