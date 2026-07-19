import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/routes.dart';
import '../../../app/theme.dart';
import '../../../data/services/api_client.dart';
import '../models/login_model.dart';
import '../widgets/login_card.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});
  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  bool _loading = false;
  Future<void> _login(LoginCredentials credentials) async {
    if (!credentials.isComplete) return _message('请输入手机号和密码');
    setState(() => _loading = true);
    try {
      await context.read<AppSession>().login(
        credentials.phone.trim(),
        credentials.password,
      );
      if (!mounted) return;
      Navigator.pushNamedAndRemoveUntil(context, AppRoutes.home, (_) => false);
    } on ApiException catch (e) {
      if (mounted) _message(e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _message(String value) => ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(value)));

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(32, 28, 32, 32),
        child: Column(
          children: [
            const Text(
              '同路行',
              style: TextStyle(
                color: AppColors.primary,
                fontSize: 32,
                fontWeight: FontWeight.w700,
                letterSpacing: 1,
              ),
            ),
            const SizedBox(height: 88),
            LoginCard(
              phoneHint: '请输入微信注册手机号',
              loading: _loading,
              onLogin: _login,
              onWechatLogin: () => _message('App 不提供注册，请先在微信小程序完成注册和密码设置'),
            ),
            const SizedBox(height: 34),
            const Text(
              '联调账号：13888888888  密码：12345678',
              style: TextStyle(color: AppColors.muted, fontSize: 13),
            ),
          ],
        ),
      ),
    ),
  );
}
