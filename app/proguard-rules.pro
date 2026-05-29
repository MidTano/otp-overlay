# SPDX-License-Identifier: MIT
# R8 rules for :app. Keeps only what the OS resolves by name
# (Activities, Services, Receivers, custom views inflated from XML),
# Parcelable CREATORs, and the static API surface the
# accessibility-paste path uses across components. R8 is free to
# rename / inline / remove everything else, including the entire
# extractor / data / overlay / system packages.

-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ── Manifest-declared entry points ─────────────────────────────────────
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application
-keep public class * extends android.service.notification.NotificationListenerService
-keep public class * extends android.accessibilityservice.AccessibilityService

-keepclassmembers class * extends android.app.Activity { public <init>(...); }
-keepclassmembers class * extends android.app.Service { public <init>(...); }
-keepclassmembers class * extends android.content.BroadcastReceiver { public <init>(...); }
-keepclassmembers class * extends android.app.Application { public <init>(...); }
-keepclassmembers class * extends android.service.notification.NotificationListenerService {
    public <init>(...);
}
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService {
    public <init>(...);
}

-keepclassmembers class * extends android.service.notification.NotificationListenerService {
    public void onListenerConnected();
    public void onNotificationPosted(android.service.notification.StatusBarNotification);
    public void onNotificationRemoved(android.service.notification.StatusBarNotification);
}
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService {
    public void onServiceConnected();
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent);
    public void onInterrupt();
    public void onDestroy();
}

-keep class com.midtano.otp.BuildConfig { *; }

# ── Custom views inflated from XML ─────────────────────────────────────
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keepclasseswithmembers class * extends android.view.ViewGroup {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keepclassmembers class * extends android.view.View {
    void set*(***);
    *** get*();
}

# ── Third-party libs ───────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**
-keep class androidx.dynamicanimation.animation.** { *; }
-keep class androidx.palette.graphics.** { *; }
-dontwarn androidx.recyclerview.**
-dontwarn androidx.constraintlayout.**

-dontwarn javax.annotation.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.checkerframework.**

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ── Static reflection entry points used inside the app ────────────────
-keepclassmembers class com.midtano.otp.service.OtpAccessibilityService {
    public static com.midtano.otp.service.OtpAccessibilityService peekInstance();
    public static void setPendingOtp(java.lang.String);
    public static java.lang.String peekPendingOtp();
    public com.midtano.otp.service.PasteResult pasteNow(java.lang.String);
}

# Kotlin metadata required for reflection-driven extensions to resolve
# at runtime; harmless on classes that never use it.
-keep class kotlin.Metadata { *; }
