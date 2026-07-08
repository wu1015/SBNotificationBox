package com.wu1015.sbnotificationbox.notification;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import com.wu1015.sbnotificationbox.mailsend.EmailSender;
import com.wu1015.sbnotificationbox.mailsend.PackageFilterManager;
import com.wu1015.sbnotificationbox.mailsend.SecureEmailPreferences;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyNotificationListenerService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 获取通知的标题和内容
        Notification notification = sbn.getNotification();
        String notificationTitle = notification.extras.getString(Notification.EXTRA_TITLE);
        String rawText = notification.extras.getString(Notification.EXTRA_TEXT);
        String notificationText = rawText != null ? rawText.replace("\r", " ").replace("\n", " ") : "";

        // 只处理非常驻通知
        if ((notification.flags & Notification.FLAG_NO_CLEAR) != 0) {
            return;
        }

        // 获取发送通知的应用包名
        String packageName = sbn.getPackageName();

        // 排除系统通知
        if ("android".equals(packageName)) {
            return;
        }

        // 避免获取到 null
        if (notificationTitle == null || notificationText == null) {
            return;
        }

        // 获取应用名
        String appName = getAppName(packageName);

        // 获取通知发送的时间戳，并格式化为日期时间
        String notificationTime = getNotificationTime(sbn.getPostTime());

        // 整合消息内容
        String title = notificationTime + " " + packageName;
        String text = notificationTitle + ": " + notificationText;

        // 将通知内容写入文件
        appendToFile(title, text);

        // 更新小部件
        NotificationWidgetProvider.addItemToWidget(new MyNotification(title, text));
        NotificationWidgetProvider.updateWidget(getApplicationContext());

        // 检查包名过滤规则，决定是否发送邮件
        if (!PackageFilterManager.shouldForward(getBaseContext(), packageName)) {
            Log.d("NotificationFilter", "Filtered out: " + packageName);
            return;
        }

        // 异步发送邮件通知
        String senderEmail = SecureEmailPreferences.getSenderEmail(getBaseContext());
        String receiverEmail = SecureEmailPreferences.getReceiverEmail(getBaseContext());

        if (senderEmail != null && receiverEmail != null) {
            new Thread(() -> {
                try {
                    boolean success = EmailSender.sendEmail2(
                            senderEmail, receiverEmail,
                            "Notification: " + appName,
                            title + "\n" + text);
                    Log.d("NotificationEmail", "Send result: " + success);
                } catch (Exception e) {
                    Log.e("NotificationEmail", "Failed to send notification email", e);
                }
            }).start();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 处理通知移除的情况（当前不需要处理）
    }

    private void appendToFile(String title, String text) {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String currentDate = sdf.format(new Date());
        String fileName = currentDate + "_notifications_log.md";

        FileOutputStream fos = null;
        OutputStreamWriter writer = null;

        try {
            fos = openFileOutput(fileName, Context.MODE_APPEND);
            writer = new OutputStreamWriter(fos);

            String logEntry = title + "\n" + text + "\n\n";
            writer.write(logEntry);

        } catch (IOException e) {
            Log.e("NotificationLog", "Failed to write log", e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                Log.e("NotificationLog", "Failed to close file", e);
            }
        }
    }

    // 获取应用名称
    private String getAppName(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            if (packageInfo.applicationInfo != null) {
                return packageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (packageName.startsWith("com.android")) {
                return "System App";
            }
        }
        return packageName; // fallback: 返回包名
    }

    // 获取通知发送时间
    private String getNotificationTime(long postTime) {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(postTime));
    }
}
