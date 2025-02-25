package com.wu1015.sbnotificationbox.notification;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.wu1015.sbnotificationbox.R;

import java.util.ArrayList;
import java.util.List;

public class WidgetRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private Context context;
    private List<MyNotification> itemList;


    public WidgetRemoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        itemList = new ArrayList<>();
        // 初始化数据
        itemList.addAll(FileUtils.readFileContentLast(context));
        if(itemList.isEmpty()){
            itemList.add(new MyNotification("new","new"));
        }
    }

    // todo 测试用，记得删除
    private void loadNotificationData() {
        Log.d("Widget", "Data are share");
    }

    @Override
    public RemoteViews getViewAt(int position) {
        @SuppressLint("RemoteViewLayout") RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_item);
        views.setTextViewText(R.id.widget_item_title, itemList.get(position).getTitle());
        views.setTextViewText(R.id.widget_item_text, itemList.get(position).getText());
        Log.d("Widget", "Data is being set."+itemList.get(position));
        return views;
    }

    @Override
    public void onCreate() {

    }

    @Override
    public void onDataSetChanged() {
        // 数据更新时可以进行处理
        // loadNotificationData();
    }

    @Override
    public void onDestroy() {

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

    // 添加数据的方法
    public void addItem(MyNotification item) {
        itemList.add(item);
    }

    // 清空数据的方法
    public void clearItems() {
        itemList.clear();
        itemList.add(new MyNotification("new","new"));
    }
}