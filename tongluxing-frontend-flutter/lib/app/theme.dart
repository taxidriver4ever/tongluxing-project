import 'package:flutter/material.dart';

abstract final class AppColors {
  static const primary = Color(0xFF3A86FF);
  static const primaryDark = Color(0xFF246ED8);
  static const primarySoft = Color(0xFFEAF3FF);
  static const text = Color(0xFF111827);
  static const secondaryText = Color(0xFF344054);
  static const muted = Color(0xFF98A2B3);
  static const success = Color(0xFF34C759);
  static const warning = Color(0xFFFF9A1F);
  static const danger = Color(0xFFFF3B30);
  static const background = Color(0xFFF7F9FE);
  static const input = Color(0xFFF2F4F8);
  static const border = Color(0xFFE5E7EB);
  static const card = Colors.white;
}

abstract final class AppTheme {
  static ThemeData get light {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: AppColors.primary,
      brightness: Brightness.light,
      primary: AppColors.primary,
      surface: AppColors.card,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.background,
      fontFamilyFallback: const [
        'PingFang SC',
        'Microsoft YaHei',
        'sans-serif',
      ],
      textTheme: const TextTheme(
        headlineMedium: TextStyle(
          color: AppColors.text,
          fontSize: 26,
          height: 1.35,
          fontWeight: FontWeight.w700,
        ),
        bodyMedium: TextStyle(color: AppColors.secondaryText, fontSize: 14),
      ),
      appBarTheme: const AppBarTheme(
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        backgroundColor: AppColors.background,
        foregroundColor: AppColors.text,
        titleTextStyle: TextStyle(
          color: AppColors.text,
          fontSize: 20,
          fontWeight: FontWeight.w700,
        ),
      ),
      cardTheme: CardThemeData(
        color: AppColors.card,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(50),
          backgroundColor: AppColors.primary,
          shape: const StadiumBorder(),
          textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.input,
        hintStyle: const TextStyle(color: AppColors.muted, fontSize: 14),
        contentPadding: const EdgeInsets.symmetric(horizontal: 20),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(18),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(18),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(18),
          borderSide: const BorderSide(color: AppColors.primary, width: 1.5),
        ),
      ),
    );
  }
}
