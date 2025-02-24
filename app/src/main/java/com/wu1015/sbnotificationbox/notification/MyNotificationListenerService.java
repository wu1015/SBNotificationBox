package com.wu1015.sbnotificationbox.notification;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.widget.RemoteViews;
import android.widget.Toast;
import android.content.Context;


import com.wu1015.sbnotificationbox.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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


//        只处理非常驻通知
        if ((notification.flags & Notification.FLAG_NO_CLEAR) == 0){
    //        获取发送通知的应用包名
            String packageName = sbn.getPackageName();
    //        获取应用名
            String appName = getAppName(packageName);

//            暂时先用包名
            appName = packageName;

//            排除系统通知（A11测试，系统通知包名为android）
            if(packageName.equals("android")){
                return;
            }

    //        获取通知发送的时间戳，并格式化为日期时间
            String notificationTime = getNotificationTime(sbn.getPostTime());

    //        在收到通知时，显示一个Toast
            showToast(appName, notificationTime, notificationTitle, notificationText);

    //        将通知内容追加写入文件
            appendToFile(appName, notificationTime, notificationTitle, notificationText);

    //        收到通知后更新小部件
            updateWidget(getApplicationContext(), readFileContent());
        }
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
            String logEntry = notificationTime + " " + appName + " Title: " + title + "\nText: " + text + "\n\n";
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
//            如果无法获取应用名，返回默认值
            return "Unknown App";
        }
    }

//    获取通知发送时间
    private String getNotificationTime(long postTime) {
//        将时间戳转换为可读的日期格式
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(postTime)); // 格式化为日期时间字符串
    }

//    收到通知更新小部件
    private void updateWidget(Context context, String text) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, NotificationWidgetProvider.class);

        // 更新小部件的内容
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        views.setTextViewText(R.id.notification_text, text);  // 设置文本内容

        appWidgetManager.updateAppWidget(thisWidget, views);  // 更新小部件
    }

    private String readFileContent() {
        String content = "";
        FileInputStream fis = null;
        try {
            @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd"); // 文件名格式为：yyyyMMdd
            String currentDate = sdf.format(new Date()); // 获取当前日期
            String fileName = currentDate + "_notifications_log.md";  // 使用当前日期作为文件名
            File file = new File(getApplicationContext().getFilesDir(), fileName);
            fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }

            content = stringBuilder.toString();
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return content;
    }
}
