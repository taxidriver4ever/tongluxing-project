package com.tongluxing.tongluxing_frontend_flutter

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.content.ContentValues
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.RenderMode
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.Locale

class MainActivity : FlutterActivity() {
    // 高德地图属于 Android PlatformView。使用 Texture 模式后，Flutter 的搜索抽屉、
    // 路线卡片和输入框可以稳定绘制在地图上层，避免“发现”页只剩空白底板。
    override fun getRenderMode(): RenderMode = RenderMode.texture

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
            val fallback = findBestRecentLocation(manager)
            val provider = fallback?.provider ?: manager.getProviders(true).firstOrNull()
            if (provider == null) {
                result.error("LOCATION_UNAVAILABLE", "当前没有可用的定位服务", null)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(provider, null, mainExecutor) { current ->
                    val location = current ?: findBestRecentLocation(manager)
                    if (location == null) {
                        result.error("LOCATION_UNAVAILABLE", "暂未获取到有效定位，请稍后重试", null)
                    } else {
                        sendLocationResult(location, result)
                    }
                }
                return
            }
            if (fallback == null) {
                result.error("LOCATION_UNAVAILABLE", "暂未获取到有效定位，请稍后重试", null)
                return
            }
            sendLocationResult(fallback, result)
        } catch (exception: SecurityException) {
            result.error("LOCATION_PERMISSION_REQUIRED", "定位权限不足", null)
        } catch (exception: Exception) {
            result.error("LOCATION_UNAVAILABLE", exception.message ?: "定位暂不可用", null)
        }
    }

    private fun findBestRecentLocation(manager: LocationManager): Location? {
        val now = System.currentTimeMillis()
        return manager.getProviders(true)
            .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
            .filter { location ->
                location.accuracy.isFinite() && location.accuracy > 0f &&
                    now - location.time <= 120_000L
            }
            .minByOrNull { location ->
                val ageSeconds = ((now - location.time).coerceAtLeast(0L) / 1000.0)
                ageSeconds + location.accuracy.toDouble()
            }
    }

    private fun sendLocationResult(location: Location, result: MethodChannel.Result) {
        if (!location.accuracy.isFinite() || location.accuracy <= 0f) {
            result.error("LOCATION_ACCURACY_INVALID", "当前定位精度不可用", null)
            return
        }
        val converted = wgs84ToGcj02(location.latitude, location.longitude)
        result.success(
            mapOf(
                "longitude" to converted.second,
                "latitude" to converted.first,
                "altitude" to if (location.hasAltitude()) location.altitude else null,
                "speed" to location.speed.toDouble(),
                "direction" to location.bearing.toDouble(),
                "accuracy" to location.accuracy.toDouble(),
                "provider" to location.provider,
                "locationTimeMillis" to location.time,
                "coordinateSystem" to "GCJ02",
                "isMock" to (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 &&
                        location.isFromMockProvider
                    ),
            ),
        )
    }

    private fun wgs84ToGcj02(latitude: Double, longitude: Double): Pair<Double, Double> {
        if (isOutsideChina(latitude, longitude)) {
            return Pair(latitude, longitude)
        }
        val earthRadius = 6_378_245.0
        val eccentricity = 0.00669342162296594323
        var latitudeOffset = transformLatitude(longitude - 105.0, latitude - 35.0)
        var longitudeOffset = transformLongitude(longitude - 105.0, latitude - 35.0)
        val radianLatitude = latitude / 180.0 * Math.PI
        var magic = Math.sin(radianLatitude)
        magic = 1 - eccentricity * magic * magic
        val sqrtMagic = Math.sqrt(magic)
        latitudeOffset = latitudeOffset * 180.0 /
            ((earthRadius * (1 - eccentricity)) / (magic * sqrtMagic) * Math.PI)
        longitudeOffset = longitudeOffset * 180.0 /
            (earthRadius / sqrtMagic * Math.cos(radianLatitude) * Math.PI)
        return Pair(latitude + latitudeOffset, longitude + longitudeOffset)
    }

    private fun isOutsideChina(latitude: Double, longitude: Double): Boolean =
        longitude < 72.004 || longitude > 137.8347 ||
            latitude < 0.8293 || latitude > 55.8271

    private fun transformLatitude(x: Double, y: Double): Double {
        var result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y +
            0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
        result += (20.0 * Math.sin(6.0 * x * Math.PI) +
            20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        result += (20.0 * Math.sin(y * Math.PI) +
            40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        result += (160.0 * Math.sin(y / 12.0 * Math.PI) +
            320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return result
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var result = 300.0 + x + 2.0 * y + 0.1 * x * x +
            0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
        result += (20.0 * Math.sin(6.0 * x * Math.PI) +
            20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        result += (20.0 * Math.sin(x * Math.PI) +
            40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        result += (150.0 * Math.sin(x / 12.0 * Math.PI) +
            300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return result
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
