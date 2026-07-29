# 高德地图 Android SDK 的 GL native 库通过 JNI 使用完整类名和方法名。
# 这些类在 release 构建中不可被 R8 重命名或删除，否则地图首帧会 SIGABRT。
-keep class com.amap.api.** { *; }
-keep interface com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep interface com.autonavi.** { *; }

# 保留所有 JNI 入口及其声明类，兼容高德和其他原生 SDK。
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
