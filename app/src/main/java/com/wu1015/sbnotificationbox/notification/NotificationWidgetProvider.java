package com.wu1015.sbnotificationbox.notification;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.wu1015.sbnotificationbox.R;

public class NotificationWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        for (int appWidgetId : appWidgetIds) {
            // 获取保存的通知记录
            String notificationText = getNotificationText(context);

            // 创建 RemoteViews 并设置通知文本
            @SuppressLint("RemoteViewLayout") RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
            views.setTextViewText(R.id.notification_text, notificationText);


            // 更新小部件
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    // 模拟获取通知记录的功能
    private String getNotificationText(Context context) {
        // 可以从文件、数据库或其他存储中获取通知记录
        // 此处使用静态文本作为示例
        return "这是一条长通知记录，内容会自动滚动显示，若超过屏幕大小将无法完全显示，但可以滚动查看。";
    }
}
