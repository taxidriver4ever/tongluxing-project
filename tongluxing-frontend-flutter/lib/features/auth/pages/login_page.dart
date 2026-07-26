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
  Widget build(BuildContext context) {
    return Scaffold(
      // 输入框获得焦点时保持整页位置不变，键盘直接覆盖页面底部，
      // 不再因为可用高度变化而把登录卡片整体向上推。
      resizeToAvoidBottomInset: false,
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final compactHeight = constraints.maxHeight < 690;
            return SingleChildScrollView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: EdgeInsets.fromLTRB(24, compactHeight ? 24 : 38, 24, 24),
              child: Center(
                child: SizedBox(
                  width: 370,
                  child: ConstrainedBox(
                    constraints: BoxConstraints(
                      minHeight:
                          constraints.maxHeight - (compactHeight ? 48 : 62),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text(
                          '同路行',
                          style: TextStyle(
                            color: AppColors.primary,
                            fontSize: 30,
                            height: 1.1,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 1,
                          ),
                        ),
                        SizedBox(height: compactHeight ? 38 : 54),
                        LoginCard(
                          phoneHint: '请输入微信注册手机号',
                          loading: _loading,
                          onLogin: _login,
                          onWechatLogin: () =>
                              _message('App 不提供注册，请先在微信小程序完成注册和密码设置'),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
