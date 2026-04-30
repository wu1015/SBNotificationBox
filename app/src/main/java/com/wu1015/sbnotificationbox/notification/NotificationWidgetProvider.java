package com.wu1015.sbnotificationbox.notification;

import android.app.PendingIntent;
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

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        try {
            if (intent != null && "com.wu1015.sbnotificationbox.SCROLL_TO_BOTTOM".equals(intent.getAction())) {
                // 获取AppWidgetManager
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                ComponentName thisWidget = new ComponentName(context, NotificationWidgetProvider.class);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

                // 获取数据总数
                int count = WidgetRemoteViewsService.getItemCount();

                // 创建RemoteViews
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
                Intent serviceIntent = new Intent(context, WidgetRemoteViewsService.class);
                views.setRemoteAdapter(R.id.widget_listview, serviceIntent);

                // 设置空视图
                views.setEmptyView(R.id.widget_listview, R.id.empty_view);

                // 重新设置点击事件
                Intent scrollIntent = new Intent(context, NotificationWidgetProvider.class);
                scrollIntent.setAction("com.wu1015.sbnotificationbox.SCROLL_TO_BOTTOM");
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, scrollIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.btn_scroll_bottom, pendingIntent);

                // 结合使用setScrollPosition和setRelativeScrollPosition方法
                if (count > 0) {
                    // 先设置绝对滚动位置
//                    views.setScrollPosition(R.id.widget_listview, (int) Float.MAX_VALUE);
                    // 再设置相对滚动位置
//                    views.setRelativeScrollPosition(R.id.widget_listview, (int) -Float.MAX_VALUE);
                    // 设置绝对滚动最大也就是为列表末尾
                    views.setScrollPosition(R.id.widget_listview, Integer.MAX_VALUE);
                }
                // 更新小部件
                appWidgetManager.updateAppWidget(appWidgetIds, views);

                // 通知数据更新
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_listview);
            }
        } catch (Exception e) {
            Log.e("NotificationWidget", "Error handling scroll to bottom", e);
        }
    }


    // 初始化小组件
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // 更新小部件
        appWidgetManager.updateAppWidget(appWidgetId, getRemoteViews(context));
    }

    @NonNull
    private static RemoteViews getRemoteViews(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        Intent intent = new Intent(context, WidgetRemoteViewsService.class);
        views.setRemoteAdapter(R.id.widget_listview, intent);

        // 添加滚动到底部按钮的点击事件
        Intent scrollIntent = new Intent(context, NotificationWidgetProvider.class);
        scrollIntent.setAction("com.wu1015.sbnotificationbox.SCROLL_TO_BOTTOM");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, scrollIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_scroll_bottom, pendingIntent);

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
