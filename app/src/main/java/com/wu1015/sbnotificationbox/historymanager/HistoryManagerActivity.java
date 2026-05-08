package com.wu1015.sbnotificationbox.historymanager;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wu1015.sbnotificationbox.R;
import com.wu1015.sbnotificationbox.notification.MyNotification;
import com.wu1015.sbnotificationbox.notification.MyNotificationFile;
import com.wu1015.sbnotificationbox.notification.NotificationWidgetProvider;
import com.wu1015.sbnotificationbox.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HistoryManagerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private ArrayList<MyNotificationFile> fileList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history_manager);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 加载文件列表
        loadFileList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回Activity时重新加载文件列表
        loadFileList();
    }

    private void loadFileList() {
        // 使用FileUtils获取文件列表
        fileList = FileUtils.getFilesArrayList(this);

        // 设置适配器
        adapter = new FileAdapter(fileList);
        recyclerView.setAdapter(adapter);
    }

    private void openFile(MyNotificationFile file) {
        try {
            // 使用FileUtils读取文件内容
            ArrayList<MyNotification> notifications = FileUtils.readFileContent(
                    file.getFileName().replace("_notifications_log.md", ""), this);

            if (notifications.isEmpty()) {
                Toast.makeText(this, "文件内容为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 创建一个StringBuilder来构建显示内容
            StringBuilder content = new StringBuilder();
            for (MyNotification notification : notifications) {
                content.append(notification.getTitle()).append("\n");
                content.append(notification.getText()).append("\n\n");
            }

            // 创建并显示对话框
            new AlertDialog.Builder(this)
                    .setTitle(file.getFileName())
                    .setMessage(content.toString())
                    .setPositiveButton("关闭", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile(MyNotificationFile file) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除 " + file.getFileName() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    File fileToDelete = new File(file.getFilePath());
                    if (fileToDelete.delete()) {
                        Toast.makeText(this, "文件已删除", Toast.LENGTH_SHORT).show();
                        loadFileList();
                        // 删除小部件内容
                        NotificationWidgetProvider.clearWidgetItems();
                        // 更新小部件
                        NotificationWidgetProvider.updateWidget(getBaseContext());
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();

    }

    // 文件适配器
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {
        private ArrayList<MyNotificationFile> files;

        public FileAdapter(ArrayList<MyNotificationFile> files) {
            this.files = files;
        }

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file, parent, false);
            return new FileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            MyNotificationFile file = files.get(position);
            holder.fileName.setText(file.getFileName());

            // 获取文件大小
            File fileObj = new File(file.getFilePath());
            long fileSize = fileObj.length();
            String sizeText;
            if (fileSize < 1024) {
                sizeText = fileSize + " B";
            } else if (fileSize < 1024 * 1024) {
                sizeText = String.format("%.1f KB", fileSize / 1024.0);
            } else {
                sizeText = String.format("%.1f MB", fileSize / (1024.0 * 1024));
            }
            holder.fileSize.setText(sizeText);

            // 获取文件修改时间
            long lastModified = fileObj.lastModified();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            holder.fileDate.setText(sdf.format(new java.util.Date(lastModified)));

            // 设置点击事件
            holder.itemView.setOnClickListener(v -> openFile(file));
            holder.itemView.setOnLongClickListener(v -> {
                deleteFile(file);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class FileViewHolder extends RecyclerView.ViewHolder {
            TextView fileName;
            TextView fileSize;
            TextView fileDate;

            public FileViewHolder(@NonNull View itemView) {
                super(itemView);
                fileName = itemView.findViewById(R.id.fileName);
                fileSize = itemView.findViewById(R.id.fileSize);
                fileDate = itemView.findViewById(R.id.fileDate);
            }
        }
    }
}
