package com.wu1015.sbnotificationbox.notification;

// 通知显示实体类
public class MyNotification {
    String title;
    String text;

    public MyNotification(String title, String text) {
        this.title = title;
        this.text = text;
    }

    public MyNotification() {
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setText(String text) {
        this.text = text;
    }
}
