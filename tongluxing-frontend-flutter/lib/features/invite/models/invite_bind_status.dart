class InviteBindStatus {
  const InviteBindStatus({
    required this.bound,
    required this.eligible,
    required this.expired,
    required this.registeredAt,
    required this.expireAt,
    required this.remainingSeconds,
    this.boundAt,
    this.inviterNickname,
    this.inviterAvatarUrl,
  });

  final bool bound;
  final bool eligible;
  final bool expired;
  final DateTime? registeredAt;
  final DateTime? expireAt;
  final int remainingSeconds;
  final DateTime? boundAt;
  final String? inviterNickname;
  final String? inviterAvatarUrl;

  factory InviteBindStatus.fromJson(Map<String, dynamic> json) {
    final inviter = json['inviter'] is Map
        ? Map<String, dynamic>.from(json['inviter'] as Map)
        : const <String, dynamic>{};
    return InviteBindStatus(
      bound: json['bound'] == true,
      eligible: json['eligible'] == true,
      expired: json['expired'] == true,
      registeredAt: DateTime.tryParse(json['registeredAt']?.toString() ?? ''),
      expireAt: DateTime.tryParse(json['expireAt']?.toString() ?? ''),
      remainingSeconds: (json['remainingSeconds'] as num?)?.toInt() ?? 0,
      boundAt: DateTime.tryParse(json['boundAt']?.toString() ?? ''),
      inviterNickname: inviter['nickname']?.toString(),
      inviterAvatarUrl: inviter['avatarUrl']?.toString(),
    );
  }

  String get profileSubtitle {
    if (bound) return '已完成邀请绑定';
    if (expired) return '绑定期限已过';
    return '注册 7 天内可填写';
  }
}
