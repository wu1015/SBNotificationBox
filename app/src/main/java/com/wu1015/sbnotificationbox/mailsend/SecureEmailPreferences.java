package com.wu1015.sbnotificationbox.mailsend;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class SecureEmailPreferences {

    private static final String PREF_NAME = "email_preferences";
    private static final String KEY_SENDER_EMAIL = "sender_email";
    private static final String KEY_RECEIVER_EMAIL = "receiver_email";

    // 获取加密的 SharedPreferences 实例
    private static SharedPreferences getEncryptedPreferences(Context context) throws Exception {
        // 创建一个主密钥（用于加密和解密 SharedPreferences）
        MasterKey mainKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        // 创建并返回加密的 SharedPreferences 实例
        return EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                mainKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // 使用 AES256_SIV
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // 使用 AES256_GCM
        );
    }

    // 存储发件人和收件人的邮箱
    public static void saveEmail(Context context, String senderEmail, String receiverEmail) {
        try {
            SharedPreferences sharedPreferences = getEncryptedPreferences(context);
            sharedPreferences.edit()
                    .putString(KEY_SENDER_EMAIL, senderEmail)
                    .putString(KEY_RECEIVER_EMAIL, receiverEmail)
                    .apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 获取发件人邮箱
    public static String getSenderEmail(Context context) {
        try {
            SharedPreferences sharedPreferences = getEncryptedPreferences(context);
            return sharedPreferences.getString(KEY_SENDER_EMAIL, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 获取收件人邮箱
    public static String getReceiverEmail(Context context) {
        try {
            SharedPreferences sharedPreferences = getEncryptedPreferences(context);
            return sharedPreferences.getString(KEY_RECEIVER_EMAIL, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

