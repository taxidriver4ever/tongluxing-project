package com.tongluxing.tongluxing_frontend_flutter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.Locale

class MainActivity : FlutterActivity() {
    private val permissionChannel = "com.tongluxing/permissions"
    private val locationPermissionRequest = 1001
    private var pendingLocationResult: MethodChannel.Result? = null
    private var textToSpeech: TextToSpeech? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            permissionChannel,
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "requestLocation" -> requestLocationPermission(result)
                "isAmapSupported" -> {
                    val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
                    result.success(
                        primaryAbi == "arm64-v8a" || primaryAbi == "armeabi-v7a",
                    )
                }
                else -> result.notImplemented()
            }
        }
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.tongluxing/navigation_voice",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "speak" -> speak(call.argument<String>("text").orEmpty(), result)
                "stop" -> { textToSpeech?.stop(); result.success(true) }
                else -> result.notImplemented()
            }
        }
    }

    private fun speak(text: String, result: MethodChannel.Result) {
        if (text.isBlank()) { result.success(false); return }
        val ready = textToSpeech
        if (ready != null) {
            ready.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tongluxing-navigation")
            result.success(true)
            return
        }
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.SIMPLIFIED_CHINESE
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tongluxing-navigation")
                result.success(true)
            } else result.error("TTS_INIT_FAILED", "系统语音引擎初始化失败", null)
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    private fun requestLocationPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            result.success(true)
            return
        }

        if (pendingLocationResult != null) {
            result.error("REQUEST_IN_PROGRESS", "Location permission is being requested", null)
            return
        }

        pendingLocationResult = result
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            locationPermissionRequest,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != locationPermissionRequest) return

        val granted = grantResults.isNotEmpty() &&
            grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        pendingLocationResult?.success(granted)
        pendingLocationResult = null
    }
}
