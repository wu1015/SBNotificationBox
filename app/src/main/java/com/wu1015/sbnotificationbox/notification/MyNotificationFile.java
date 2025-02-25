package com.wu1015.sbnotificationbox.notification;

// 通知文件列表实体类
public class MyNotificationFile {
    String fileName;
    String filePath;

    public MyNotificationFile(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFilePath() {
        return filePath;
    }
}
