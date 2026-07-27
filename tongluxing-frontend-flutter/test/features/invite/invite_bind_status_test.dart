import 'package:flutter_test/flutter_test.dart';
import 'package:tongluxing_frontend_flutter/features/invite/models/invite_bind_status.dart';

void main() {
  test('bound state has highest display priority', () {
    final status = InviteBindStatus.fromJson({
      'bound': true,
      'eligible': false,
      'expired': false,
      'remainingSeconds': 0,
      'inviter': {'nickname': '同路好友'},
    });
    expect(status.profileSubtitle, '已完成邀请绑定');
    expect(status.inviterNickname, '同路好友');
  });

  test('expired state is rendered from server result', () {
    final status = InviteBindStatus.fromJson({
      'bound': false,
      'eligible': false,
      'expired': true,
      'remainingSeconds': 0,
    });
    expect(status.profileSubtitle, '绑定期限已过');
  });
}
