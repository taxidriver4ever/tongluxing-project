class LoginCredentials {
  const LoginCredentials({required this.phone, required this.password});

  final String phone;
  final String password;

  bool get isComplete => phone.trim().isNotEmpty && password.isNotEmpty;
}

class LoginResult {
  const LoginResult({required this.success, required this.message});

  final bool success;
  final String message;
}
