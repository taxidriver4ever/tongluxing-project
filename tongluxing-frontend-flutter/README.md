# tongluxing_frontend_flutter

同路行自驾社交平台 Flutter 客户端

## 本地运行

App 默认连接通过 `adb reverse` 转发的真实后端，开发默认地址为
`http://127.0.0.1:18080/api`：

> Android SDK 的 `aapt` 工具无法稳定读取包含中文的绝对路径。Windows 开发时请从已建立的纯英文目录联接 `D:\tlx-flutter-app` 启动，源文件仍是当前项目中的同一份文件。

```powershell
cd D:\tlx-flutter-app
flutter run
```

使用 Android 模拟器时需要显式传入模拟器宿主机地址：

```powershell
flutter run -d emulator-5554 --dart-define=API_BASE_URL=http://10.0.2.2:18080/api
```

组件测试和离线演示可以显式传入 `--dart-define=API_DEMO=true`，生产及普通开发构建不会自动启用 Mock。

## Android USB 真机联调

先在手机上开启“USB 调试”并允许当前电脑，然后启动本地 Spring Boot。项目根目录脚本会自动读取相邻后端项目 `.env` 中的 `SERVER_PORT` 和 `SERVER_SERVLET_CONTEXT_PATH`，执行 `adb reverse`，并通过编译参数把真机 API 地址设置为 `127.0.0.1`：

```powershell
cd D:\tlx-flutter-app
.\run_android_usb.ps1
```

连接了多个真机时可以指定序列号：

```powershell
.\run_android_usb.ps1 -DeviceId Z9XG7X7L59PBPF7H
```

如果只想建立并检查转发、不立即启动 Flutter，可追加 `-ConfigureOnly`。

本项目当前环境策略：

- USB 真机：默认使用 `127.0.0.1`，配合 `adb reverse`。
- 开发模拟器：显式传入 `10.0.2.2` 地址。
- USB 真机：脚本注入 `--dart-define=API_BASE_URL=http://127.0.0.1:<端口>/<上下文>`。
- 测试：测试代码显式构造 `ApiClient(useDemo: true)`。
- 生产：发布时显式传入 HTTPS 地址，例如 `--dart-define=API_BASE_URL=https://api.example.com/api`；脚本和 debug 清单不会改变 release 配置。

转发在手机重启、拔线或 ADB 重启后可能失效，重新执行脚本即可。也可以手工执行：

```powershell
adb -s <真机序列号> reverse tcp:18080 tcp:18080
flutter run -d <真机序列号> --dart-define=API_BASE_URL=http://127.0.0.1:18080/api
```

## 验证

```powershell
flutter analyze
flutter test
flutter build apk --debug
```
