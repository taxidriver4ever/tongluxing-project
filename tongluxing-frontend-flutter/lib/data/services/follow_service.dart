import 'api_client.dart';

class FollowService {
  const FollowService(this.api);
  final ApiClient api;

  Future<Map<String, dynamic>> status(String userId) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/users/$userId/follow-status') as Map,
      );

  Future<Map<String, dynamic>> follow(String userId) async =>
      Map<String, dynamic>.from(
        await api.post('/v1/users/$userId/follow') as Map,
      );

  Future<Map<String, dynamic>> unfollow(String userId) async =>
      Map<String, dynamic>.from(
        await api.delete('/v1/users/$userId/follow') as Map,
      );

  Future<List<Map<String, dynamic>>> followers(
    String userId, {
    int page = 1,
    int size = 20,
  }) async {
    final data = await api.get(
      '/v1/users/$userId/followers',
      query: {'page': '$page', 'size': '$size'},
    );
    return (data as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  Future<List<Map<String, dynamic>>> following(
    String userId, {
    int page = 1,
    int size = 20,
  }) async {
    final data = await api.get(
      '/v1/users/$userId/following',
      query: {'page': '$page', 'size': '$size'},
    );
    return (data as List? ?? const [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }
}
