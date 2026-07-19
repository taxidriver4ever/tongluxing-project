import 'package:flutter/material.dart';

import '../../../app/theme.dart';

class AuthTextField extends StatelessWidget {
  const AuthTextField({
    required this.label,
    required this.controller,
    required this.hintText,
    this.obscureText = false,
    this.keyboardType,
    super.key,
  });

  final String label;
  final TextEditingController controller;
  final String hintText;
  final bool obscureText;
  final TextInputType? keyboardType;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(color: AppColors.secondaryText, fontSize: 14),
        ),
        const SizedBox(height: 10),
        SizedBox(
          height: 52,
          child: TextField(
            controller: controller,
            obscureText: obscureText,
            keyboardType: keyboardType,
            style: const TextStyle(color: AppColors.text, fontSize: 14),
            decoration: InputDecoration(hintText: hintText),
          ),
        ),
      ],
    );
  }
}
