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

                // 创建RemoteViews
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

                // 关键优化：不要在这里重新设置 Adapter (setRemoteAdapter)
                // 只要 Adapter ID 没变，系统会保留当前的 Adapter 和数据状态
                // Intent serviceIntent = new Intent(context, WidgetRemoteViewsService.class);
                // views.setRemoteAdapter(R.id.widget_listview, serviceIntent);

                // 设置空视图 (通常不需要每次都设置，除非空视图状态变了)
                // views.setEmptyView(R.id.widget_listview, R.id.empty_view);

                // 重新设置点击事件 (如果PendingIntent没变，其实也可以不设，但为了保险起见保留)
                Intent scrollIntent = new Intent(context, NotificationWidgetProvider.class);
                scrollIntent.setAction("com.wu1015.sbnotificationbox.SCROLL_TO_BOTTOM");
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, scrollIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.btn_scroll_bottom, pendingIntent);

                // 获取数据总数
                int count = WidgetRemoteViewsService.getItemCount();

                // 执行滚动
                if (count > 0) {
                    // 使用 smoothScrollToPosition 效果更好，但RemoteViews只支持 setScrollPosition
                    // 直接滚动到最后一个位置
                    views.setScrollPosition(R.id.widget_listview, count - 1);
                }

                // 更新小部件
                appWidgetManager.updateAppWidget(appWidgetIds, views);

                // 关键修改：注释掉这行！不要触发数据刷新
                // appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_listview);
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

        // 通知 Widget 更新 (这里保留，因为这是外部显式调用的刷新，说明数据确实变了)
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
