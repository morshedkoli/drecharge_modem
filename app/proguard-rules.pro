# ─────────────────────────────────────────────────────────────
# dRecharge Modem — ProGuard / R8 rules
# ─────────────────────────────────────────────────────────────
# Keep line numbers in crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Accessibility Service ─────────────────────────────────────
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class com.dRecharge.modem.ussd.** { *; }

# ── Retrofit + OkHttp ─────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Gson / JSON models ────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep class com.dRecharge.modem.apimodel.** { *; }
-keep class com.dRecharge.modem.licenseapimodel.** { *; }
-keepattributes SerializedName

# ── Data Binding ──────────────────────────────────────────────
-keep class androidx.databinding.** { *; }

# ── App components ────────────────────────────────────────────
-keep class com.dRecharge.modem.receiver.** { *; }
-keep class com.dRecharge.modem.service.** { *; }
-keep class com.dRecharge.modem.MainActivity { *; }
-keep class com.dRecharge.modem.SplashActivity { *; }
-keep class com.dRecharge.modem.PermissionActivity { *; }
-keep class com.dRecharge.modem.SettingsActivity { *; }
-keep class com.dRecharge.modem.helper.Session { *; }
-keep class com.dRecharge.modem.helper.Constant { *; }
-keep class com.dRecharge.modem.server.** { *; }
-keep class com.dRecharge.modem.subscription.** { *; }

# ── Lottie ────────────────────────────────────────────────────
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ── AndroidX Security (EncryptedSharedPreferences) ────────────
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keep class com.dRecharge.modem.BuildConfig { *; }
-dontwarn com.google.crypto.tink.**

# ── Suppress common warnings ──────────────────────────────────
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
