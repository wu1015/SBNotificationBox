package com.wu1015.sbnotificationbox.historymanager;

import android.app.AlertDialog;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HistoryManagerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView textViewEmpty;
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

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        textViewEmpty = findViewById(R.id.textViewEmpty);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadFileList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFileList();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void loadFileList() {
        fileList = FileUtils.getFilesArrayList(this);

        // 处理空状态
        if (fileList.isEmpty()) {
            textViewEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            textViewEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        if (adapter == null) {
            adapter = new FileAdapter(fileList);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateFiles(fileList);
        }
    }

    private void openFile(MyNotificationFile file) {
        try {
            String datePart = file.getFileName().replace("_notifications_log.md", "");
            ArrayList<MyNotification> notifications = FileUtils.readFileContent(datePart, this);

            if (notifications.isEmpty()) {
                Toast.makeText(this, "File is empty", Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder content = new StringBuilder();
            for (MyNotification notification : notifications) {
                content.append(notification.getTitle()).append("\n");
                content.append(notification.getText()).append("\n\n");
            }

            new AlertDialog.Builder(this)
                    .setTitle(file.getFileName())
                    .setMessage(content.toString())
                    .setPositiveButton("Close", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile(MyNotificationFile file) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Delete")
                .setMessage("Delete " + file.getFileName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    File fileToDelete = new File(file.getFilePath());
                    if (fileToDelete.delete()) {
                        Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show();
                        loadFileList();
                        NotificationWidgetProvider.clearWidgetItems();
                        NotificationWidgetProvider.updateWidget(getBaseContext());
                    } else {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== RecyclerView Adapter ====================

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {
        private ArrayList<MyNotificationFile> files;
        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        public FileAdapter(ArrayList<MyNotificationFile> files) {
            this.files = files;
        }

        public void updateFiles(ArrayList<MyNotificationFile> newFiles) {
            this.files = newFiles;
            notifyDataSetChanged();
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

            File fileObj = new File(file.getFilePath());
            long fileSize = fileObj.length();
            String sizeText;
            if (fileSize < 1024) {
                sizeText = fileSize + " B";
            } else if (fileSize < 1024 * 1024) {
                sizeText = String.format(Locale.US, "%.1f KB", fileSize / 1024.0);
            } else {
                sizeText = String.format(Locale.US, "%.1f MB", fileSize / (1024.0 * 1024));
            }
            holder.fileSize.setText(sizeText);

            long lastModified = fileObj.lastModified();
            holder.fileDate.setText(sdf.format(new Date(lastModified)));

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
