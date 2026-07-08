package com.wu1015.sbnotificationbox.mailsend;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 包名过滤管理器。
 * 支持白名单模式和黑名单模式，用于控制哪些应用的通知需要邮件转发。
 *
 * 白名单模式：仅转发列表中包名的通知
 * 黑名单模式：转发除了列表中包名之外的所有通知
 * 禁用模式：不过滤，转发所有通知（默认）
 */
public class PackageFilterManager {

    private static final String PREF_NAME = "package_filter_prefs";
    private static final String KEY_FILTER_MODE = "filter_mode";
    private static final String KEY_WHITELIST = "whitelist_packages";
    private static final String KEY_BLACKLIST = "blacklist_packages";

    public enum FilterMode {
        DISABLED,   // 不过滤
        WHITELIST,  // 白名单模式：仅转发列表中的包
        BLACKLIST   // 黑名单模式：排除列表中的包
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ==================== 过滤模式 ====================

    public static FilterMode getFilterMode(Context context) {
        String mode = getPrefs(context).getString(KEY_FILTER_MODE, FilterMode.DISABLED.name());
        try {
            return FilterMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            return FilterMode.DISABLED;
        }
    }

    public static void setFilterMode(Context context, FilterMode mode) {
        getPrefs(context).edit().putString(KEY_FILTER_MODE, mode.name()).apply();
    }

    // ==================== 白名单 ====================

    public static Set<String> getWhitelist(Context context) {
        return getPrefs(context).getStringSet(KEY_WHITELIST, new HashSet<>());
    }

    public static void addToWhitelist(Context context, String packageName) {
        Set<String> list = new HashSet<>(getWhitelist(context));
        list.add(packageName);
        getPrefs(context).edit().putStringSet(KEY_WHITELIST, list).apply();
    }

    public static void removeFromWhitelist(Context context, String packageName) {
        Set<String> list = new HashSet<>(getWhitelist(context));
        list.remove(packageName);
        getPrefs(context).edit().putStringSet(KEY_WHITELIST, list).apply();
    }

    public static List<String> getWhitelistSorted(Context context) {
        List<String> list = new ArrayList<>(getWhitelist(context));
        Collections.sort(list);
        return list;
    }

    // ==================== 黑名单 ====================

    public static Set<String> getBlacklist(Context context) {
        return getPrefs(context).getStringSet(KEY_BLACKLIST, new HashSet<>());
    }

    public static void addToBlacklist(Context context, String packageName) {
        Set<String> list = new HashSet<>(getBlacklist(context));
        list.add(packageName);
        getPrefs(context).edit().putStringSet(KEY_BLACKLIST, list).apply();
    }

    public static void removeFromBlacklist(Context context, String packageName) {
        Set<String> list = new HashSet<>(getBlacklist(context));
        list.remove(packageName);
        getPrefs(context).edit().putStringSet(KEY_BLACKLIST, list).apply();
    }

    public static List<String> getBlacklistSorted(Context context) {
        List<String> list = new ArrayList<>(getBlacklist(context));
        Collections.sort(list);
        return list;
    }

    // ==================== 过滤判断 ====================

    /**
     * 判断某个包名的通知是否应该被转发。
     *
     * @param context     Context
     * @param packageName 通知来源包名
     * @return true = 应该转发, false = 应该过滤掉
     */
    public static boolean shouldForward(Context context, String packageName) {
        FilterMode mode = getFilterMode(context);

        switch (mode) {
            case WHITELIST:
                // 白名单模式：只有列表中的包才转发
                return getWhitelist(context).contains(packageName);

            case BLACKLIST:
                // 黑名单模式：列表中的包不转发
                return !getBlacklist(context).contains(packageName);

            case DISABLED:
            default:
                // 不过滤，全部转发
                return true;
        }
    }
}
