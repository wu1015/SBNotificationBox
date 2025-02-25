package com.wu1015.sbnotificationbox.notification;

import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.wu1015.sbnotificationbox.R;

public class NotificationWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        for (int appWidgetId : appWidgetIds) {
            // 初始化小组件
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    // 初始化小组件
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // 更新小部件
        appWidgetManager.updateAppWidget(appWidgetId, getRemoteViews(context));
    }

    @NonNull
    private static RemoteViews getRemoteViews(Context context) {
        // 创建 RemoteViews 对象
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        // 设置 ListView 使用 RemoteViewsService 来填充数据
        Intent intent = new Intent(context, WidgetRemoteViewsService.class);
        views.setRemoteAdapter(R.id.widget_listview, intent);
        return views;
    }

    // 刷新小部件
    public static void updateWidget(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, NotificationWidgetProvider.class);

        // 更新小部件的内容
        RemoteViews views = getRemoteViews(context);

        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        // 通知 Widget 更新
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_listview);

        // 更新小部件
        appWidgetManager.updateAppWidget(thisWidget, views);
    }

    // 添加数据
    public static void addItemToWidget(MyNotification item) {
        WidgetRemoteViewsService.addItem(item);
    }

    // 清空数据
    public static void clearWidgetItems() {
        WidgetRemoteViewsService.clearItems();
    }
}
