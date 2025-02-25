package com.wu1015.sbnotificationbox.notification;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViewsService;

import com.wu1015.sbnotificationbox.R;

public class WidgetRemoteViewsService extends RemoteViewsService {
    private static WidgetRemoteViewsFactory factory;
    @Override
    public WidgetRemoteViewsFactory onGetViewFactory(Intent intent) {
        if (factory == null) {
            factory = new WidgetRemoteViewsFactory(this.getApplicationContext(), intent);
        }
        return factory;
    }

    // 静态方法，用于添加数据
    public static void addItem(MyNotification item) {
        if (factory != null) {
            factory.addItem(item);
        }
    }

    // 静态方法，用于清空数据
    public static void clearItems() {
        if (factory != null) {
            factory.clearItems();
        }
    }
}