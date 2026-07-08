package com.wu1015.sbnotificationbox;

import static com.wu1015.sbnotificationbox.utils.FileUtils.delAllFiles;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.wu1015.sbnotificationbox.historymanager.HistoryManagerActivity;
import com.wu1015.sbnotificationbox.mailsend.FilterSettingsActivity;
import com.wu1015.sbnotificationbox.mailsend.MailSendActivity;
import com.wu1015.sbnotificationbox.mailsend.MailSessionManager;
import com.wu1015.sbnotificationbox.notification.NotificationWidgetProvider;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.mail.Session;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_LISTENER = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 检查并请求通知监听权限
        checkAndRequestNotificationListenerPermission();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 按钮：发送测试通知
        Button btnSendNotification = findViewById(R.id.button);
        btnSendNotification.setOnClickListener(v -> sendTestNotification());

        // 按钮：显示文件并删除
        Button btnShowFile = findViewById(R.id.button2);
        btnShowFile.setOnClickListener(v -> showFileAndDelete());

        // 按钮：进入邮件登录
        Button btnMailLogin = findViewById(R.id.button3);
        btnMailLogin.setOnClickListener(v -> navigateTo(MailSendActivity.class));

        // 按钮：进入历史管理
        Button btnHistory = findViewById(R.id.button4);
        btnHistory.setOnClickListener(v -> navigateTo(HistoryManagerActivity.class));

        // 按钮：进入过滤设置
        Button btnFilter = findViewById(R.id.button5);
        btnFilter.setOnClickListener(v -> navigateTo(FilterSettingsActivity.class));

        // 显示邮件会话状态
        updateMailStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次从其他页面返回时刷新邮件状态
        updateMailStatus();
    }

    private void updateMailStatus() {
        TextView mailStatus = findViewById(R.id.textView2);
        Session session = MailSessionManager.getSession();
        if (session == null) {
            mailStatus.setText("Mail: not configured");
        } else {
            String email = MailSessionManager.getCurrentEmail();
            mailStatus.setText(email != null ? "Mail: " + email : "Mail: configured");
        }
    }

    private void sendTestNotification() {
        Context context = getBaseContext();
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 创建 NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("Channel_ID",
                    "chat message", NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, "Channel_ID")
                .setContentTitle("Test Title")
                .setContentText("Test notification content from SBNotificationBox")
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher_foreground);

        mNotificationManager.notify(1, mBuilder.build());
        Toast.makeText(context, "Test notification sent", Toast.LENGTH_SHORT).show();
    }

    private void showFileAndDelete() {
        String currentDate = getCurrentDate();
        String fileName = currentDate + "_notifications_log.md";

        // 读取文件内容
        String fileContent = readFile(fileName);

        // 显示文件内容
        TextView textView = findViewById(R.id.textview);
        textView.setText(fileContent.isEmpty() ? "(no notifications today)" : fileContent);

        // 删除所有 md 文件
        boolean deleted = delAllFiles(getBaseContext());
        Toast.makeText(getBaseContext(),
                deleted ? "Files deleted" : "No files to delete or failed",
                Toast.LENGTH_LONG).show();

        // 清空小部件
        NotificationWidgetProvider.clearWidgetItems();
        NotificationWidgetProvider.updateWidget(getBaseContext());
    }

    private void navigateTo(Class<?> targetActivity) {
        new Thread(() -> {
            Intent intent = new Intent(getApplicationContext(), targetActivity);
            startActivity(intent);
        }).start();
    }

    // 获取当前日期，格式为 yyyyMMdd
    @SuppressLint("SimpleDateFormat")
    private String getCurrentDate() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    // 读取文件内容
    private String readFile(String fileName) {
        FileInputStream fis = null;
        InputStreamReader reader = null;
        StringBuilder stringBuilder = new StringBuilder();

        try {
            fis = openFileInput(fileName);
            reader = new InputStreamReader(fis);

            int charRead;
            while ((charRead = reader.read()) != -1) {
                stringBuilder.append((char) charRead);
            }
        } catch (IOException e) {
            // 文件不存在是正常的（当天还没有通知）
            return "";
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                // 忽略关闭异常
            }
        }

        return stringBuilder.toString();
    }

    private void checkAndRequestNotificationListenerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String enabledListeners = Settings.Secure.getString(
                    getContentResolver(),
                    "enabled_notification_listeners");

            String packageName = getPackageName();
            boolean isEnabled = enabledListeners != null &&
                    enabledListeners.contains(packageName);

            if (!isEnabled) {
                new AlertDialog.Builder(this)
                        .setTitle("Notification Listener Required")
                        .setMessage("This app needs notification listener permission to work. Please enable it in settings.")
                        .setPositiveButton("Open Settings", (dialog, which) -> {
                            Intent intent = new Intent(
                                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                            startActivityForResult(intent, REQUEST_NOTIFICATION_LISTENER);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_NOTIFICATION_LISTENER) {
            String enabledListeners = Settings.Secure.getString(
                    getContentResolver(),
                    "enabled_notification_listeners");

            String packageName = getPackageName();
            boolean isEnabled = enabledListeners != null &&
                    enabledListeners.contains(packageName);

            Toast.makeText(this,
                    isEnabled ? "Notification permission granted" : "Notification permission not granted",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
