package com.wu1015.sbnotificationbox.notification;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.wu1015.sbnotificationbox.mailsend.EmailSender;
import com.wu1015.sbnotificationbox.mailsend.PackageFilterManager;
import com.wu1015.sbnotificationbox.mailsend.SecureEmailPreferences;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MyNotificationListenerService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();

        // 只处理非常驻通知
        if ((notification.flags & Notification.FLAG_NO_CLEAR) != 0) {
            return;
        }

        // 排除系统通知
        String packageName = sbn.getPackageName();
        if ("android".equals(packageName)) {
            return;
        }

        // 提取通知内容
        String notificationTitle = notification.extras.getString(Notification.EXTRA_TITLE);
        String rawText = notification.extras.getString(Notification.EXTRA_TEXT);
        String notificationText = rawText != null
                ? rawText.replace("\r", " ").replace("\n", " ") : "";

        if (notificationTitle == null || notificationText == null) {
            return;
        }

        // 构建日志内容
        String appName = getAppName(packageName);
        String notificationTime = getNotificationTime(sbn.getPostTime());
        String title = notificationTime + " " + packageName;
        String text = notificationTitle + ": " + notificationText;

        // 写入日志文件
        appendToFile(title, text);

        // 更新小部件
        NotificationWidgetProvider.addItemToWidget(new MyNotification(title, text));
        NotificationWidgetProvider.updateWidget(getApplicationContext());

        // 检查包名过滤规则
        if (!PackageFilterManager.shouldForward(getBaseContext(), packageName)) {
            Log.d("NotificationFilter", "Filtered out: " + packageName);
            return;
        }

        // 异步发送邮件通知
        String senderEmail = SecureEmailPreferences.getSenderEmail(getBaseContext());
        String receiverEmail = SecureEmailPreferences.getReceiverEmail(getBaseContext());

        if (senderEmail != null && receiverEmail != null) {
            // 捕获局部变量供线程使用
            String finalAppName = appName;
            String finalTitle = title;
            String finalText = text;
            new Thread(() -> {
                try {
                    boolean success = EmailSender.sendEmail2(
                            senderEmail, receiverEmail,
                            "Notification: " + finalAppName,
                            finalTitle + "\n" + finalText);
                    Log.d("NotificationEmail", "Send result: " + success);
                } catch (Exception e) {
                    Log.e("NotificationEmail", "Failed to send notification email", e);
                }
            }).start();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 当前不需要处理通知移除
    }

    private void appendToFile(String title, String text) {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
        String currentDate = sdf.format(new Date());
        String fileName = currentDate + "_notifications_log.md";

        FileOutputStream fos = null;
        OutputStreamWriter writer = null;

        try {
            fos = openFileOutput(fileName, Context.MODE_APPEND);
            writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(title + "\n" + text + "\n\n");
        } catch (IOException e) {
            Log.e("NotificationLog", "Failed to write log", e);
        } finally {
            try {
                if (writer != null) writer.close();
                if (fos != null) fos.close();
            } catch (IOException e) {
                Log.e("NotificationLog", "Failed to close file", e);
            }
        }
    }

    private String getAppName(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            if (packageInfo.applicationInfo != null) {
                return packageManager.getApplicationLabel(
                        packageInfo.applicationInfo).toString();
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (packageName.startsWith("com.android")) {
                return "System App";
            }
        }
        return packageName;
    }

    @SuppressLint("SimpleDateFormat")
    private String getNotificationTime(long postTime) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date(postTime));
    }
}
