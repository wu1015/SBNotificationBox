package com.wu1015.sbnotificationbox.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.wu1015.sbnotificationbox.notification.MyNotification;
import com.wu1015.sbnotificationbox.notification.MyNotificationFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

// 通知文本文件工具类
public class FileUtils {

    // 获取应用私有文件目录
    public static String getAppFilesDir(Context context) {
        File fileDir = context.getFilesDir();
        return fileDir != null ? fileDir.getAbsolutePath() : "Directory not found";
    }

    // 获取指定目录下所有文件的名称和路径
    public static ArrayList<MyNotificationFile> getFilesArrayList(Context context) {
        ArrayList<MyNotificationFile> myNotificationFileArrayList = new ArrayList<>();
        String directoryPath = getAppFilesDir(context);

        File directory = new File(directoryPath);
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String fileName = file.getName();
                    String filePath = file.getAbsolutePath();
                    myNotificationFileArrayList.add(new MyNotificationFile(fileName, filePath));
                }
            }
        }
        return myNotificationFileArrayList;
    }

    // 删除应用自带目录下的所有 .md 文件
    public static boolean delAllFiles(Context context) {
        String directoryPath = getAppFilesDir(context);
        File directory = new File(directoryPath);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                boolean allDeleted = true;
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".md")) {
                        if (!file.delete()) {
                            allDeleted = false;
                            Log.w("FileUtils", "Failed to delete: " + file.getName());
                        }
                    }
                }
                return allDeleted;
            }
        }
        return false;
    }

    // 读取指定日期的文件内容
    public static ArrayList<MyNotification> readFileContent(String currentDate, Context context) {
        ArrayList<MyNotification> myNotificationArrayList = new ArrayList<>();
        FileInputStream fis = null;
        BufferedReader reader = null;

        try {
            String fileName = currentDate + "_notifications_log.md";
            File file = new File(context.getFilesDir(), fileName);

            if (!file.exists()) {
                return myNotificationArrayList; // 文件不存在，返回空列表
            }

            fis = new FileInputStream(file);
            reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));

            String line;
            boolean isTitle = true;
            MyNotification myNotification = new MyNotification();

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (isTitle) {
                    myNotification.setTitle(line);
                    isTitle = false;
                } else {
                    myNotification.setText(line);
                    myNotificationArrayList.add(myNotification);
                    myNotification = new MyNotification();
                    isTitle = true;
                }
            }
        } catch (IOException e) {
            Log.e("FileUtils", "Failed to read file: " + currentDate, e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                Log.e("FileUtils", "Failed to close file", e);
            }
        }
        return myNotificationArrayList;
    }

    // 读取今日文件内容
    @SuppressLint("SimpleDateFormat")
    public static ArrayList<MyNotification> readFileContentLast(Context context) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
        String currentDate = sdf.format(new Date());
        return readFileContent(currentDate, context);
    }
}
