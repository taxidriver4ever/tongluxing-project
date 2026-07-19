import 'package:flutter/material.dart';

import '../../../app/theme.dart';
import '../models/login_model.dart';
import 'auth_text_field.dart';

class LoginCard extends StatefulWidget {
  const LoginCard({
    required this.phoneHint,
    required this.loading,
    required this.onLogin,
    required this.onWechatLogin,
    super.key,
  });

  final String phoneHint;
  final bool loading;
  final ValueChanged<LoginCredentials> onLogin;
  final VoidCallback onWechatLogin;

  @override
  State<LoginCard> createState() => _LoginCardState();
}

class _LoginCardState extends State<LoginCard> {
  final _phoneController = TextEditingController();
  final _passwordController = TextEditingController();

  @override
  void dispose() {
    _phoneController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 34),
      decoration: BoxDecoration(
        color: AppColors.card,
        borderRadius: BorderRadius.circular(32),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            '欢迎回来\n开启下一段旅程',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.headlineMedium,
          ),
          const SizedBox(height: 30),
          AuthTextField(
            label: '手机号',
            controller: _phoneController,
            hintText: widget.phoneHint,
            keyboardType: TextInputType.phone,
          ),
          const SizedBox(height: 20),
          AuthTextField(
            label: '密码',
            controller: _passwordController,
            hintText: '••••••••',
            obscureText: true,
          ),
          const SizedBox(height: 22),
          _LoginButton(
            label: '登录',
            loading: widget.loading,
            primary: true,
            onPressed: () => widget.onLogin(
              LoginCredentials(
                phone: _phoneController.text,
                password: _passwordController.text,
              ),
            ),
          ),
          const SizedBox(height: 30),
          const _DividerLabel(),
          const SizedBox(height: 18),
          _LoginButton(
            label: '微信手机号一键登录',
            loading: false,
            primary: false,
            onPressed: widget.loading ? null : widget.onWechatLogin,
          ),
        ],
      ),
    );
  }
}

class _DividerLabel extends StatelessWidget {
  const _DividerLabel();

  @override
  Widget build(BuildContext context) {
    return const Row(
      children: [
        Expanded(child: Divider(color: AppColors.border, height: 1)),
        Padding(
          padding: EdgeInsets.symmetric(horizontal: 16),
          child: Text(
            '或',
            style: TextStyle(color: AppColors.muted, fontSize: 14),
          ),
        ),
        Expanded(child: Divider(color: AppColors.border, height: 1)),
      ],
    );
  }
}

class _LoginButton extends StatelessWidget {
  const _LoginButton({
    required this.label,
    required this.loading,
    required this.primary,
    required this.onPressed,
  });

  final String label;
  final bool loading;
  final bool primary;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 54,
      child: FilledButton(
        onPressed: loading ? null : onPressed,
        style: FilledButton.styleFrom(
          elevation: 0,
          backgroundColor: primary ? AppColors.primary : AppColors.card,
          foregroundColor: primary ? Colors.white : AppColors.secondaryText,
          disabledBackgroundColor: AppColors.primary.withValues(alpha: 0.55),
          shape: StadiumBorder(
            side: primary
                ? BorderSide.none
                : const BorderSide(color: AppColors.border),
          ),
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        ),
        child: loading
            ? const SizedBox.square(
                dimension: 20,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white,
                ),
              )
            : Text(label),
      ),
    );
  }
}
