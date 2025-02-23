package com.wu1015.sbnotificationbox.notification;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.widget.Toast;
import android.content.Context;


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyNotificationListenerService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
//        获取通知的标题和内容
        Notification notification = sbn.getNotification();
        String notificationTitle = notification.extras.getString(Notification.EXTRA_TITLE);
        String notificationText = notification.extras.getString(Notification.EXTRA_TEXT);

//        获取发送通知的应用包名
        String packageName = sbn.getPackageName();
//        获取应用名
        String appName = getAppName(packageName);
//        获取通知发送的时间戳，并格式化为日期时间
        String notificationTime = getNotificationTime(sbn.getPostTime());

//        在收到通知时，显示一个Toast
        showToast(appName, notificationTime, notificationTitle, notificationText);

//        将通知内容追加写入文件
        appendToFile(appName, notificationTime, notificationTitle, notificationText);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 处理通知移除的情况
    }

    private void showToast(String appName, String notificationTime, String title, String text) {
        // 获取应用上下文并显示Toast
        Context context = getApplicationContext();
        String message = appName + "Title: " + title + "\nText: " + text;
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private void appendToFile(String appName, String notificationTime, String title, String text) {
//        获取当前日期并格式化为文件名
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd"); // 文件名格式为：yyyyMMdd
        String currentDate = sdf.format(new Date()); // 获取当前日期
        String fileName = currentDate + "_notifications_log.md";  // 使用当前日期作为文件名

//        获取应用自带的文件目录
        FileOutputStream fos = null;
        OutputStreamWriter writer = null;

        try {
//            获取应用的私有目录（例如：/data/data/<your_app_package>/files）
            fos = openFileOutput(fileName, Context.MODE_APPEND);
            writer = new OutputStreamWriter(fos);

//            追加写入内容
            String logEntry = notificationTime+appName + " Title: " + title + "\nText: " + text + "\n\n";
            writer.write(logEntry);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    // 获取应用名称的方法
    private String getAppName(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            assert packageInfo.applicationInfo != null;
            return packageManager.getApplicationLabel(packageInfo.applicationInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
//            如果无法获取应用名，返回默认值
            return "Unknown App";
        }
    }

    // 获取通知发送时间的方法
    private String getNotificationTime(long postTime) {
//        将时间戳转换为可读的日期格式
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(postTime)); // 格式化为日期时间字符串
    }
}
