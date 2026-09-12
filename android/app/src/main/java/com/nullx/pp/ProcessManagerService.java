package com.nullx.pp;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException; // Fix: Tambahkan import ini
import org.json.JSONObject;
import java.util.List;

public class ProcessManagerService extends Service {
    private static final String TAG = "CRPT.ProcessManager";
    private ActivityManager activityManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    }
    
    // Fix: Helper method untuk menangani JSONException yang diminta compiler
    private String createErrorJson(String message) {
        try {
            if (message == null) message = "Unknown error";
            return new JSONObject().put("error", message).toString();
        } catch (JSONException e) {
            return "{\"error\": \"json_error\"}";
        }
    }
    
    public String listProcesses() {
        try {
            List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
            JSONArray processesArray = new JSONArray();
            
            if (processes != null) {
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    JSONObject processObj = new JSONObject();
                    processObj.put("name", process.processName);
                    processObj.put("pid", process.pid);
                    processObj.put("importance", process.importance);
                    processesArray.put(processObj);
                }
            }
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("processes", processesArray);
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error listing processes: " + e.getMessage());
            // Fix: Gunakan helper method
            return createErrorJson(e.getMessage());
        }
    }
    
    public String killProcess(int pid) {
        try {
            android.os.Process.killProcess(pid);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("pid", pid);
            return result.toString();
        } catch (Exception e) {
            // Fix: Gunakan helper method
            return createErrorJson(e.getMessage());
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}