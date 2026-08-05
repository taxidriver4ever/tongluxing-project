import 'dart:async';

/// 本地 UI 演示数据。后端联调前由 [ApiClient] 直接返回这些结构。
class DemoApi {
  DemoApi._();

  static int _draftSeed = 3;
  static final List<Map<String, dynamic>> _tripSearchHistory = [
    {
      'historyId': 'history-1',
      'keyword': '大理',
      'searchType': 'DESTINATION',
      'updatedAt': '2026-08-05 17:30:00',
    },
    {
      'historyId': 'history-2',
      'keyword': '广州',
      'searchType': 'ORIGIN',
      'updatedAt': '2026-08-05 16:30:00',
    },
  ];

  static Future<dynamic> request(
    String method,
    String path, {
    Object? body,
  }) async {
    await Future<void>.delayed(const Duration(milliseconds: 180));

    if (path == '/v1/auth/password-login') {
      return {
        'token': 'demo-access-token',
        'refreshToken': 'demo-refresh-token',
        'userId': '10086',
      };
    }
    if (path == '/v1/auth/logout') return null;

    if (path == '/v1/trips/me/active-state') {
      final active = _trips.where(
        (trip) => trip['status'] == 'PUBLISHED' || trip['status'] == 'RUNNING',
      );
      return {
        'active': active.isNotEmpty,
        'tripId': active.isEmpty ? null : active.first['tripId'],
        'message': active.isEmpty ? '' : '你已有一个未结束的行程，请先处理当前行程',
      };
    }
    if (path == '/v1/trips/me/dashboard') {
      return {
        'currentTrip': _trips.first,
        'publishedCurrentTrip': _trips.first,
        'joinedCurrentTrip': null,
        'upcomingTrips': [_trips.first],
        'recentTrips': [_trips.last],
        'activeCount': 1,
        'historyCount': 1,
      };
    }
    if (path == '/v1/trips/me') return {'trips': _trips};
    if (path == '/trips/recommend') {
      return {
        'total': 2,
        'userHasTrip': true,
        'effectiveSort': 'MATCH_RATE',
        'referenceTripId': 'trip-318',
        'list': [
          {
            'tripId': 'trip-northwest',
            'teamId': 'team-northwest',
            'tripName': '青甘大环线车队',
            'startLocation': '西宁',
            'endLocation': '青海湖',
            'departureTime': '2026-10-01 08:00:00',
            'currentVehicleCount': 2,
            'vehicleLimit': 4,
            'matchRate': 92,
            'heat': 91,
            'distanceMeters': 3000,
            'distance': 3.0,
            'timeGapMinutes': 120,
            'leaderRating': 4.9,
            'ownerUserId': '20001',
            'ownerNickname': '西北领队',
            'ownerAvatarImageKey': '',
            'relationshipStatus': 'NONE',
            'allowGreeting': true,
            'allowApply': true,
            'status': 'PUBLISHED',
          },
        ],
      };
    }
    if (path == '/v1/trips/search-history' && method == 'GET') {
      return List<Map<String, dynamic>>.from(_tripSearchHistory);
    }
    if (path == '/v1/trips/search-history' && method == 'POST') {
      final keyword = _value(body, 'keyword')?.toString() ?? '';
      final searchType = _value(body, 'searchType')?.toString() ?? 'DESTINATION';
      _tripSearchHistory.removeWhere(
        (item) => item['keyword'] == keyword && item['searchType'] == searchType,
      );
      final row = {
        'historyId': 'history-${DateTime.now().microsecondsSinceEpoch}',
        'keyword': keyword,
        'searchType': searchType,
        'updatedAt': DateTime.now().toIso8601String(),
      };
      _tripSearchHistory.insert(0, row);
      return row;
    }
    if (path == '/v1/trips/search-history' && method == 'DELETE') {
      final count = _tripSearchHistory.length;
      _tripSearchHistory.clear();
      return count;
    }
    if (path.startsWith('/v1/trips/search-history/') && method == 'DELETE') {
      final id = path.split('/').last;
      final before = _tripSearchHistory.length;
      _tripSearchHistory.removeWhere((item) => item['historyId'] == id);
      return _tripSearchHistory.length < before;
    }
    if (path == '/v1/trips/favorites') {
      return {
        'page': 1,
        'size': 20,
        'total': 1,
        'records': [
          {
            ..._trips.first,
            'owner': {
              'userId': '20001',
              'nickname': '越野阿杰',
              'levelCode': 'LV5',
            },
            'joinedVehicleCount': 2,
            'maxVehicleCount': 4,
            'remainingSeats': 2,
            'favorited': true,
          },
        ],
      };
    }
    if (path == '/v1/trips/applications/my') return <dynamic>[];
    if (path == '/v1/trips/discover') {
      return {
        'page': 1,
        'size': 10,
        'total': 1,
        'records': [
          {
            ..._trips.first,
            'owner': {
              'userId': '20001',
              'nickname': '越野阿杰',
              'levelCode': 'LV5',
            },
            'joinedVehicleCount': 2,
            'maxVehicleCount': 4,
            'remainingSeats': 2,
          },
        ],
      };
    }
    if (path == '/v1/trips/public') return {'trips': _trips};
    if (path == '/v1/trips/driving/current') return null;
    if (path == '/v1/trip/draft' && method == 'GET') {
      return [
        {'draftId': 'draft-1', 'title': '皖南川藏线周末计划', 'status': 'DRAFT'},
        {'draftId': 'draft-2', 'title': '川西小环线秋日之旅', 'status': 'DRAFT'},
      ];
    }
    if (path == '/v1/trip/draft' && method == 'POST') {
      return {
        'draftId': 'draft-${_draftSeed++}',
        'title': _value(body, 'title') ?? '新行程',
        'status': 'DRAFT',
      };
    }
    if (path.startsWith('/v1/trip/draft/') && method == 'PUT') {
      return {
        'draftId': path.split('/').last,
        'title': _value(body, 'title') ?? '新行程',
        'status': 'DRAFT',
      };
    }
    if (path.contains('/route/plan')) {
      return {'distanceMeters': 368000, 'durationSeconds': 18400};
    }
    if (path.endsWith('/publish')) return {'tripId': 'trip-demo-new'};
    if (path.startsWith('/v1/trip/')) return null;
    if (path.startsWith('/v1/trips/')) {
      final found = _trips.firstWhere(
        (e) => path.contains(e['tripId'].toString()),
        orElse: () => _trips.first,
      );
      if (path.endsWith('/start')) return {...found, 'status': 'RUNNING'};
      if (path.endsWith('/end')) return {...found, 'status': 'COMPLETED'};
      return found;
    }

    if (path.startsWith('/v1/chats/trips/') &&
        path.endsWith('/conversation')) {
      return {
        'conversationId': 'chat-318',
        'conversationName': '318 川藏线行程群',
        'bizType': 'TRIP',
        'bizId': 'trip-318',
        'status': 'ACTIVE',
      };
    }
    if (path == '/v1/chats/conversations') {
      return {'conversations': _conversations};
    }
    if (path.contains('/messages') && method == 'GET') {
      return {'messages': _messages};
    }
    if (path.contains('/messages')) return null;

    if (path == '/v1/vehicle/auth/eligibility') {
      return {'eligible': true, 'status': 'UNSUBMITTED', 'reason': ''};
    }
    if (path == '/v1/vehicles/me') return {'vehicles': _vehicles};
    if (path == '/v1/vehicles' && method == 'POST') {
      return {
        'vehicleId': 'vehicle-new',
        'brand': _value(body, 'brand') ?? '新车辆',
        'model': _value(body, 'model') ?? '',
        'plateNoMask': _value(body, 'plateNo') ?? '待录入',
        'color': _value(body, 'color') ?? '',
        'certificationStatus': 'UNSUBMITTED',
      };
    }
    if (path.startsWith('/v1/vehicles/')) return null;

    if (path == '/v1/growth/me') {
      return {'experience': 1280, 'level': 5, 'levelName': '自驾达人'};
    }
    if (path == '/v1/invites/code') {
      return {
        'inviteCode': 'TLX2026',
        'inviteUrl': 'https://demo.tongluxing.cn/i/TLX2026',
      };
    }
    if (path == '/v1/invites/me/summary') {
      return {'inviteCount': 12, 'rewardAmount': 260, 'pendingCount': 2};
    }
    if (path == '/v1/invites/bind-status') {
      return {
        'bound': false,
        'eligible': true,
        'expired': false,
        'registeredAt': '2026-07-27T10:00:00',
        'expireAt': '2026-08-03T10:00:00',
        'remainingSeconds': 604800,
        'inviter': null,
      };
    }
    if (path == '/v1/invites/preview') {
      return {
        'valid': true,
        'inviterNickname': '同路好友',
        'inviterAvatarUrl': null,
      };
    }
    if (path == '/v1/invites/bind') {
      return {
        'bound': true,
        'relationId': 'demo-relation',
        'boundAt': DateTime.now().toIso8601String(),
        'reward': {
          'inviterGrowthValue': 50,
          'inviteeGrowthValue': 0,
          'newUserCouponTriggered': false,
        },
      };
    }
    if (path == '/v1/invites/qr/validate') {
      return {'valid': true, 'inviteCode': 'TLX2026'};
    }
    if (path == '/v1/driver-tracks/points' && method == 'POST') {
      return {
        'trackId': 'demo-track',
        'distanceFromPrev': 0,
        'totalDistance': 0,
        'settledStages': 0,
        'grantedPoints': 0,
        'pointStatus': 'ACCEPTED',
        'riskScore': 0,
        'riskLevel': 'LOW',
        'reviewRequired': false,
        'message': '',
        'accepted': true,
      };
    }
    return null;
  }

  static dynamic _value(Object? body, String key) =>
      body is Map<String, dynamic> ? body[key] : null;

  static const _trips = [
    {
      'tripId': 'trip-318',
      'title': '318 川藏线自驾 · 成都到拉萨',
      'startName': '成都天府广场',
      'endName': '拉萨布达拉宫',
      'status': 'PUBLISHED',
      'departureTime': '2026-08-16 08:00:00',
      'totalDistanceMeters': 2142000,
      'joinedVehicleCount': 4,
      'maxVehicleCount': 8,
      'description': '沿川藏南线前行，穿越雪山、草原与峡谷。',
    },
    {
      'tripId': 'trip-northwest',
      'title': '青甘大环线 8 日计划',
      'startName': '西宁站',
      'endName': '青海湖二郎剑景区',
      'status': 'COMPLETED',
      'departureTime': '2026-06-20 09:30:00',
      'totalDistanceMeters': 1680000,
      'joinedVehicleCount': 6,
      'maxVehicleCount': 6,
    },
  ];

  static const _conversations = [
    {
      'conversationId': 'chat-318',
      'conversationName': '318 川藏线行程群',
      'lastMessagePreview': '领队：明早 7:30 酒店停车场集合',
      'lastMessageAt': '09:42',
      'unreadCount': 3,
    },
    {
      'conversationId': 'chat-team',
      'conversationName': '杭州自驾联盟',
      'lastMessagePreview': '小林：周末皖南线路还有两个车位',
      'lastMessageAt': '昨天',
      'unreadCount': 1,
    },
    {
      'conversationId': 'chat-service',
      'conversationName': '同路行服务通知',
      'lastMessagePreview': '您的车辆认证已通过',
      'lastMessageAt': '周一',
      'unreadCount': 0,
    },
  ];

  static const _messages = [
    {'senderName': '领队阿哲', 'content': '明早 7:30 酒店停车场集合', 'isMine': false},
    {'senderName': '我', 'content': '收到，我会提前加满油。', 'isMine': true},
    {'senderName': '小林', 'content': '对讲机频道还是 16 吗？', 'isMine': false},
  ];

  static const _vehicles = [
    {
      'vehicleId': 'vehicle-1',
      'brand': '坦克',
      'model': '300 城市版',
      'plateNoMask': '浙A·8***6',
      'color': '黑色',
      'certificationStatus': 'APPROVED',
      'isDefault': true,
    },
    {
      'vehicleId': 'vehicle-2',
      'brand': '比亚迪',
      'model': '唐 DM-p',
      'plateNoMask': '浙A·D***9',
      'color': '白色',
      'certificationStatus': 'PENDING',
      'isDefault': false,
    },
  ];
}
