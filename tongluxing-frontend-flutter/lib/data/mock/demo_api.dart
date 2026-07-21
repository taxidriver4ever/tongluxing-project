import 'dart:async';

/// 本地 UI 演示数据。后端联调前由 [ApiClient] 直接返回这些结构。
class DemoApi {
  DemoApi._();

  static int _draftSeed = 3;

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

    if (path == '/v1/trips/me') return {'trips': _trips};
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

    if (path == '/v1/chats/conversations') {
      return {'conversations': _conversations};
    }
    if (path.contains('/messages') && method == 'GET') {
      return {'messages': _messages};
    }
    if (path.contains('/messages')) return null;

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
