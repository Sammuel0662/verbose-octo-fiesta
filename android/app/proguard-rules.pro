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

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.scenebox.** { *** Companion; }
-keepclasseswithmembers class app.scenebox.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.scenebox.**$$serializer { *; }

# Shell 反射调用 Shizuku.newProcess，不能被裁剪
-keep class rikka.shizuku.Shizuku { *; }
