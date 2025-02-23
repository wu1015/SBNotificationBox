package com.wu1015.sbnotificationbox.notification;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;

public class FileUtils {

//    获取应用私有文件目录
    public static String getAppFilesDir(Context context) {
        File fileDir = context.getFilesDir();  // 获取应用私有目录
        return fileDir != null ? fileDir.getAbsolutePath() : "Directory not found";
    }

//    获取指定目录下所有文件的名称和路径
    public static ArrayList<MyNotificationFile> getFilesArrayList(Context context) {
        ArrayList<MyNotificationFile> myNotificationFileArrayList = new ArrayList<>();
        String directoryPath = getAppFilesDir(context);

//        获取目录文件列表
        File directory = new File(directoryPath);

//        获取目录下的所有文件
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
//                    获取文件名和路径
                    String fileName = file.getName();
                    String filePath = file.getAbsolutePath();

                    myNotificationFileArrayList.add(new MyNotificationFile(fileName, filePath));
                }
            }
        }
        return myNotificationFileArrayList;
    }

//    删除应用自带目录下的所有文件
    public static boolean delAllFiles(Context context){
        String directoryPath = getAppFilesDir(context);
        File directory = new File(directoryPath);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
//                    判断文件是否是 .md 文件
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

}
