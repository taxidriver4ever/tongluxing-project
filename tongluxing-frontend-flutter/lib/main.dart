import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'app/app.dart';
import 'app/app_session.dart';
import 'data/services/api_client.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final session = AppSession(ApiClient());
  await session.initialize();
  runApp(
    ChangeNotifierProvider.value(value: session, child: const TongLuXingApp()),
  );
}
