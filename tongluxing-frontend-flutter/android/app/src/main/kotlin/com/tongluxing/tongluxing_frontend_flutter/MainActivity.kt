package com.tongluxing.tongluxing_frontend_flutter

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.content.ContentValues
import android.provider.MediaStore
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
                "getCurrentLocation" -> getCurrentLocation(result)
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
            "com.tongluxing/media",
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "savePngToGallery" -> savePngToGallery(
                    call.argument<ByteArray>("bytes"),
                    call.argument<String>("fileName"),
                    result,
                )
                "saveImageToGallery" -> saveImageToGallery(
                    call.argument<ByteArray>("bytes"),
                    call.argument<String>("mimeType"),
                    result,
                )
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

    private fun saveImageToGallery(bytes: ByteArray?, mimeType: String?, result: MethodChannel.Result) {
        if (bytes == null || bytes.isEmpty()) {
            result.error("EMPTY_IMAGE", "图片内容为空", null)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            result.error("ANDROID_VERSION_UNSUPPORTED", "Android 10 以下暂不支持直接保存", null)
            return
        }
        try {
            val resolvedMimeType = mimeType?.substringBefore(';')?.trim().orEmpty().ifBlank { "image/jpeg" }
            val extension = when (resolvedMimeType.lowercase()) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic", "image/heif" -> "heic"
                else -> "jpg"
            }
            val generatedName = "tongluxing_chat_${System.currentTimeMillis()}.$extension"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, generatedName)
                put(MediaStore.Images.Media.MIME_TYPE, resolvedMimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Tongluxing")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建相册文件")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("无法写入相册文件")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            result.success(uri.toString())
        } catch (exception: Exception) {
            result.error("SAVE_FAILED", exception.message ?: "保存失败", null)
        }
    }

    private fun savePngToGallery(bytes: ByteArray?, fileName: String?, result: MethodChannel.Result) {
        if (bytes == null || bytes.isEmpty()) {
            result.error("EMPTY_IMAGE", "二维码图片为空", null)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            result.error("ANDROID_VERSION_UNSUPPORTED", "Android 10 以下请使用系统截图保存", null)
            return
        }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName ?: "tongluxing_invite.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Tongluxing")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建相册文件")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("无法写入相册文件")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            result.success(uri.toString())
        } catch (exception: Exception) {
            result.error("SAVE_FAILED", exception.message ?: "保存失败", null)
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

    private fun getCurrentLocation(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            result.error("LOCATION_PERMISSION_REQUIRED", "请先授权定位权限", null)
            return
        }
        try {
            val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val latest = manager.getProviders(true)
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .maxByOrNull { location -> location.time }
            if (latest == null) {
                result.error("LOCATION_UNAVAILABLE", "暂未获取到定位，请稍后重试", null)
                return
            }
            result.success(
                mapOf(
                    "longitude" to latest.longitude,
                    "latitude" to latest.latitude,
                    "speed" to latest.speed.toDouble(),
                    "direction" to latest.bearing.toDouble(),
                    "accuracy" to latest.accuracy.toDouble(),
                ),
            )
        } catch (exception: SecurityException) {
            result.error("LOCATION_PERMISSION_REQUIRED", "定位权限不足", null)
        }
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
