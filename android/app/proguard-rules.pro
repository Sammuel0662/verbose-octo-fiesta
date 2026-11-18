# 保留 Shizuku 相关（AIDL + 反射调用）
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# kotlinx.serialization
-keepclassmembers class app.scenebox.sbox.** {
    *** Companion;
}
-keepclasseswithmembers class app.scenebox.sbox.** {
    kotlinx.serialization.KSerializer serializer(...);
}
