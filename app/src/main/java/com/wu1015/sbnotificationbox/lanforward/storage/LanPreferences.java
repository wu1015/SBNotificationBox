package com.wu1015.sbnotificationbox.lanforward.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.wu1015.sbnotificationbox.lanforward.model.LanDevice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 局域网功能偏好设置。
 * 存储设备名称、文件保存目录、已配对设备列表等。
 */
public class LanPreferences {

    /**
     * LAN 收发方向模式。
     */
    public enum LanMode {
        BOTH("Send & Receive"),
        SEND_ONLY("Send Only"),
        RECEIVE_ONLY("Receive Only");

        private final String displayName;

        LanMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean canSend() {
            return this == BOTH || this == SEND_ONLY;
        }

        public boolean canReceive() {
            return this == BOTH || this == RECEIVE_ONLY;
        }

        public static LanMode fromString(String s) {
            if (s == null) return BOTH;
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return BOTH;
            }
        }
    }

    private static final String TAG = "LanPreferences";
    private static final String PREF_NAME = "lan_forward_prefs";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_STORAGE_DIR = "storage_dir";
    private static final String KEY_LAN_ENABLED = "lan_enabled";
    private static final String KEY_LAN_MODE = "lan_mode";
    private static final String KEY_CONNECTION_SECRET = "connection_secret";
    private static final String KEY_SAVED_DEVICES = "saved_devices";

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

    // === 局域网收发方向 ===

    /** 获取 LAN 收发模式（默认双向） */
    public static LanMode getLanMode(Context context) {
        String s = getPrefs(context).getString(KEY_LAN_MODE, LanMode.BOTH.name());
        return LanMode.fromString(s);
    }

    /** 设置 LAN 收发模式 */
    public static void setLanMode(Context context, LanMode mode) {
        getPrefs(context).edit().putString(KEY_LAN_MODE, mode.name()).apply();
    }

    // === 连接密钥 ===

    /** 获取连接密钥（空字符串 = 不验证，兼容模式） */
    public static String getConnectionSecret(Context context) {
        return getPrefs(context).getString(KEY_CONNECTION_SECRET, "");
    }

    /** 设置连接密钥 */
    public static void setConnectionSecret(Context context, String secret) {
        getPrefs(context).edit().putString(KEY_CONNECTION_SECRET,
                secret != null ? secret : "").apply();
    }

    // === 已配对设备列表（持久化） ===

    /** 保存设备列表到 SharedPreferences（JSON 数组） */
    public static void saveDevices(Context context, List<LanDevice> devices) {
        try {
            JSONArray arr = new JSONArray();
            for (LanDevice d : devices) {
                JSONObject obj = new JSONObject();
                obj.put("name", d.getDeviceName());
                obj.put("ip", d.getIpAddress());
                obj.put("port", d.getPort());
                arr.put(obj);
            }
            getPrefs(context).edit().putString(KEY_SAVED_DEVICES, arr.toString()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Failed to save devices", e);
        }
    }

    /** 从 SharedPreferences 加载已保存的设备列表 */
    public static List<LanDevice> loadDevices(Context context) {
        List<LanDevice> devices = new ArrayList<>();
        try {
            String json = getPrefs(context).getString(KEY_SAVED_DEVICES, "");
            if (json.isEmpty()) return devices;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String name = obj.optString("name", "Unknown");
                String ip = obj.optString("ip", "");
                int port = obj.optInt("port", 9877);
                if (!ip.isEmpty()) {
                    LanDevice d = new LanDevice(name, ip, port);
                    d.setOnline(false); // 启动时默认离线，等心跳更新
                    devices.add(d);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load devices", e);
        }
        return devices;
    }

    /** 添加单个设备并持久化 */
    public static void addSavedDevice(Context context, LanDevice device) {
        List<LanDevice> devices = loadDevices(context);
        // 去重：已存在则跳过
        for (LanDevice d : devices) {
            if (d.getIpAddress().equals(device.getIpAddress())
                    && d.getPort() == device.getPort()) {
                return; // 已存在，不重复添加
            }
        }
        devices.add(device);
        saveDevices(context, devices);
    }

    /** 移除单个设备并持久化 */
    public static void removeSavedDevice(Context context, LanDevice device) {
        List<LanDevice> devices = loadDevices(context);
        devices.removeIf(d ->
                d.getIpAddress().equals(device.getIpAddress())
                        && d.getPort() == device.getPort());
        saveDevices(context, devices);
    }
}
