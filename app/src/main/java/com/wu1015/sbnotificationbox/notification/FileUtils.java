package com.wu1015.sbnotificationbox.notification;

import android.annotation.SuppressLint;
import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

// 通知文本文件工具类
public class FileUtils {

    // 获取应用私有文件目录
    public static String getAppFilesDir(Context context) {
        File fileDir = context.getFilesDir();  // 获取应用私有目录
        return fileDir != null ? fileDir.getAbsolutePath() : "Directory not found";
    }

    // 获取指定目录下所有文件的名称和路径
    public static ArrayList<MyNotificationFile> getFilesArrayList(Context context) {
        ArrayList<MyNotificationFile> myNotificationFileArrayList = new ArrayList<>();
        String directoryPath = getAppFilesDir(context);

        // 获取目录文件列表
        File directory = new File(directoryPath);

        // 获取目录下的所有文件
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    // 获取文件名和路径
                    String fileName = file.getName();
                    String filePath = file.getAbsolutePath();

                    myNotificationFileArrayList.add(new MyNotificationFile(fileName, filePath));
                }
            }
        }
        return myNotificationFileArrayList;
    }

    // 删除应用自带目录下的所有文件
    public static boolean delAllFiles(Context context){
        String directoryPath = getAppFilesDir(context);
        File directory = new File(directoryPath);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    // 判断文件是否是 .md 文件
                    if (file.isFile() && file.getName().endsWith(".md")) {
                        boolean deleted = file.delete();
                        if (!deleted) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    // 读取文件内容
    public static ArrayList<MyNotification> readFileContent(String currentDate, Context context) {
        ArrayList<MyNotification> myNotificationArrayList = new ArrayList<>();
        FileInputStream fis = null;
        try {
            String fileName = currentDate + "_notifications_log.md";  // 使用当前日期作为文件名
            File file = new File(context.getFilesDir(), fileName);
            fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

            String line;
            boolean flag = true;
            MyNotification myNotification = new MyNotification();
            while ((line = reader.readLine()) != null) {
                if(line.trim().isEmpty()){
                    continue;
                }
                if (flag) {
                    myNotification.setTitle(line);
                    flag = false;
                } else {
                    myNotification.setText(line);
                    myNotificationArrayList.add(myNotification);
                    myNotification = new MyNotification();
                    flag = true;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return myNotificationArrayList;
    }

    // 读取今日文件内容
    public static ArrayList<MyNotification> readFileContentLast(Context context) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd"); // 文件名格式为：yyyyMMdd
        String currentDate = sdf.format(new Date()); // 获取当前日期
        return readFileContent(currentDate, context);
    }
}
