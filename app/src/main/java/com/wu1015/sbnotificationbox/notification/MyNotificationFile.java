package com.wu1015.sbnotificationbox.notification;

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
