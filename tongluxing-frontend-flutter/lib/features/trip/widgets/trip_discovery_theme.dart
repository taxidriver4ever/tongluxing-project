import 'package:flutter/material.dart';

/// “发现行程 / 行程详情”高保真页面的局部视觉规范。
///
/// 只服务于行程发现链路，避免调整全局主题后影响地图、消息、商城等既有页面。
abstract final class TripDiscoveryColors {
  static const primary = Color(0xFF0877E5);
  static const primaryDark = Color(0xFF0068D8);
  static const primaryDeep = Color(0xFF075EBE);
  static const sky = Color(0xFF42A9F2);
  static const softBlue = Color(0xFFDDEEFF);
  static const tagBlue = Color(0xFFEAF2FB);
  static const pageBackground = Color(0xFFF2F5F9);
  static const card = Colors.white;
  static const text = Color(0xFF0B1B39);
  static const secondaryText = Color(0xFF31415D);
  static const muted = Color(0xFF7F8A9D);
  static const divider = Color(0xFFEDF0F4);
  static const hot = Color(0xFFFF6B35);

  static const headerGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [primaryDark, primary, sky],
    stops: [0, .55, 1],
  );

  static const buttonGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF1385F4), primaryDark],
  );
}
