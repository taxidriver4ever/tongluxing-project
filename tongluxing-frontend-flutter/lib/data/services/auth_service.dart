import '../../features/auth/models/login_model.dart';
import '../mock/mock_user.dart';

abstract interface class AuthService {
  Future<LoginResult> login(LoginCredentials credentials);

  Future<LoginResult> loginWithWechatPhone();
}

class MockAuthService implements AuthService {
  const MockAuthService();

  @override
  Future<LoginResult> login(LoginCredentials credentials) async {
    await Future<void>.delayed(const Duration(milliseconds: 500));
    if (!credentials.isComplete) {
      return const LoginResult(success: false, message: '请输入手机号和密码');
    }
    return const LoginResult(success: true, message: '登录成功');
  }

  @override
  Future<LoginResult> loginWithWechatPhone() async {
    await Future<void>.delayed(const Duration(milliseconds: 500));
    return const LoginResult(
      success: true,
      message: '${MockUserData.displayName}，微信登录成功',
    );
  }
}
