package com.wu1015.sbnotificationbox.lanforward.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;

/**
 * 局域网功能偏好设置。
 * 存储设备名称、文件保存目录等。
 */
public class LanPreferences {

    private static final String PREF_NAME = "lan_forward_prefs";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_STORAGE_DIR = "storage_dir";
    private static final String KEY_LAN_ENABLED = "lan_enabled";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // === 设备名称 ===

    public static String getDeviceName(Context context) {
        String name = getPrefs(context).getString(KEY_DEVICE_NAME, "");
        if (name.isEmpty()) {
            // 默认使用设备型号
            name = android.os.Build.MODEL;
        }
        return name;
    }

    public static void setDeviceName(Context context, String name) {
        getPrefs(context).edit().putString(KEY_DEVICE_NAME, name).apply();
    }

    // === 文件保存目录 ===

    public static String getStorageDir(Context context) {
        String dir = getPrefs(context).getString(KEY_STORAGE_DIR, "");
        if (dir.isEmpty()) {
            // 默认目录：外部存储/SBNotificationBox/LAN
            File defaultDir = new File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    "LAN_Received");
            dir = defaultDir.getAbsolutePath();
        }
        return dir;
    }

    public static void setStorageDir(Context context, String dir) {
        getPrefs(context).edit().putString(KEY_STORAGE_DIR, dir).apply();
    }

    // === 局域网转发开关 ===

    public static boolean isLanEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_LAN_ENABLED, true); // 默认开启
    }

    public static void setLanEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_LAN_ENABLED, enabled).apply();
    }
}
