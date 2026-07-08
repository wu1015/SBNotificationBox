package com.wu1015.sbnotificationbox.notification;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.wu1015.sbnotificationbox.utils.FileUtils;
import com.wu1015.sbnotificationbox.R;

import java.util.ArrayList;
import java.util.List;

public class WidgetRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private final Context context;
    private final List<MyNotification> itemList;

    public WidgetRemoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        this.itemList = new ArrayList<>();
        // 从今日日志加载已有数据
        List<MyNotification> savedItems = FileUtils.readFileContentLast(context);
        if (!savedItems.isEmpty()) {
            itemList.addAll(savedItems);
        } else {
            // 没有数据时显示占位提示
            itemList.add(new MyNotification("No notifications yet", "New notifications will appear here"));
        }
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= itemList.size()) {
            return null;
        }

        @SuppressLint("RemoteViewLayout")
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item);
        views.setTextViewText(R.id.widget_item_title, itemList.get(position).getTitle());
        views.setTextViewText(R.id.widget_item_text, itemList.get(position).getText());
        return views;
    }

    @Override
    public void onCreate() {
        // 初始化（当前不需要额外操作）
    }

    @Override
    public void onDataSetChanged() {
        // 数据更新时回调（当前由外部直接操作 itemList）
    }

    @Override
    public void onDestroy() {
        // 清理资源
        itemList.clear();
    }

    @Override
    public int getCount() {
        return itemList.size();
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    // ==================== 数据操作方法 ====================

    public void addItem(MyNotification item) {
        itemList.add(item);
    }

    public void clearItems() {
        itemList.clear();
        itemList.add(new MyNotification("No notifications yet", "New notifications will appear here"));
    }
}
