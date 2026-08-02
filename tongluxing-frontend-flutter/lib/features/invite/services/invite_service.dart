import '../../../data/services/api_client.dart';
import '../models/invite_bind_status.dart';

class InviteBindingService {
  const InviteBindingService(this.api);

  final ApiClient api;

  Future<InviteBindStatus> bindStatus() async => InviteBindStatus.fromJson(
    Map<String, dynamic>.from(await api.get('/v1/invites/bind-status') as Map),
  );

  Future<Map<String, dynamic>> preview(String inviteCode) async =>
      Map<String, dynamic>.from(
        await api.post('/v1/invites/preview', body: {'inviteCode': inviteCode})
            as Map,
      );

  Future<Map<String, dynamic>> bind({
    required String inviteCode,
    required String sourceType,
    required String requestId,
  }) async => Map<String, dynamic>.from(
    await api.post(
          '/v1/invites/bind',
          body: {
            'inviteCode': inviteCode,
            'sourceType': sourceType,
            'requestId': requestId,
          },
        )
        as Map,
  );

  Future<Map<String, dynamic>> validateQrToken(String token) async =>
      Map<String, dynamic>.from(
        await api.get('/v1/invites/qr/validate', query: {'token': token})
            as Map,
      );
}
