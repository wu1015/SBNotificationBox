package com.wu1015.sbnotificationbox.notification;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;
import android.content.Context;


import androidx.annotation.NonNull;

import com.wu1015.sbnotificationbox.R;
import com.wu1015.sbnotificationbox.mailsend.EmailSender;
import com.wu1015.sbnotificationbox.mailsend.SecureEmailPreferences;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class MyNotificationListenerService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 获取通知的标题和内容
        Notification notification = sbn.getNotification();
        String notificationTitle = notification.extras.getString(Notification.EXTRA_TITLE);
        String notificationText = notification.extras.getString(Notification.EXTRA_TEXT);


        // 只处理非常驻通知
        if ((notification.flags & Notification.FLAG_NO_CLEAR) == 0) {
            // 获取发送通知的应用包名
            String packageName = sbn.getPackageName();
            // 获取应用名
            String appName = getAppName(packageName);
            // todo 暂时先用包名，因部分应用名获取有问题

            // 排除系统通知（A11测试，系统通知包名为android）
            if (packageName.equals("android")) {
                return;
            }

            // 避免获取到null
            if (notificationTitle == null || notificationText == null){
                return;
            }


            // 获取通知发送的时间戳，并格式化为日期时间
            String notificationTime = getNotificationTime(sbn.getPostTime());

            // 整合消息内容
            String title = notificationTime + " " + packageName;
            String text = notificationTitle + ": " + notificationText;

            // 在收到通知时，显示一个Toast
            // todo 测试用，注意删除或注释
            // showToast(title, text);

            // 将通知内容追加写入文件
            appendToFile(title, text);

            // todo 加入过滤，只转发重要通知
            // 250703 暂时先注释，目前只需要每天发送存储的附件
            new Thread(() -> {
                try {
                    boolean f = EmailSender.sendEmail2(SecureEmailPreferences.getSenderEmail(getBaseContext()), SecureEmailPreferences.getReceiverEmail(getBaseContext()),"Notification Mi6", title+"\n"+text);
                    Log.d("TAG", "onNotificationPosted: "+ f);
                } catch (Exception e) {
                    e.printStackTrace();
                    onDestroy();
                }
            }).start();

            // 更新内容
            NotificationWidgetProvider.addItemToWidget(new MyNotification(title, text));
            // 刷新小部件
            NotificationWidgetProvider.updateWidget(getApplicationContext());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 处理通知移除的情况
    }

    private void showToast(String title, String text) {
        // 获取应用上下文并显示Toast
        Context context = getApplicationContext();
        String message = title + "\n" + text;
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private void appendToFile(String title, String text) {
        // 获取当前日期并格式化为文件名
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd"); // 文件名格式为：yyyyMMdd
        String currentDate = sdf.format(new Date()); // 获取当前日期
        String fileName = currentDate + "_notifications_log.md";  // 使用当前日期作为文件名

        // 获取应用自带的文件目录
        FileOutputStream fos = null;
        OutputStreamWriter writer = null;

        try {
            // 获取应用的私有目录
            fos = openFileOutput(fileName, Context.MODE_APPEND);
            writer = new OutputStreamWriter(fos);

            // 追加写入内容
            String logEntry = title + "\n" + text + "\n\n";
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
            if (packageName.startsWith("com.android")) {
                return "System App";  // 处理系统应用
            }
            e.printStackTrace();
            // 如果无法获取应用名，返回默认值
            return "Unknown App";
        }
    }

    // 获取通知发送时间
    private String getNotificationTime(long postTime) {
//        将时间戳转换为可读的日期格式
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(postTime)); // 格式化为日期时间字符串
    }
}
