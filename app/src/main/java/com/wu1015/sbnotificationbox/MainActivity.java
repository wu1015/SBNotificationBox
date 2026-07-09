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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.wu1015.sbnotificationbox.historymanager.HistoryManagerActivity;
import com.wu1015.sbnotificationbox.lanforward.network.LanManager;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences;
import com.wu1015.sbnotificationbox.lanforward.ui.LanForwardActivity;
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

    private MaterialCardView cardLog;
    private TextView textviewLog;
    private TextView textViewMailStatus;
    private TextView textViewLanStatus;
    private SwitchMaterial switchLan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();

        // 检查并请求通知监听权限
        checkAndRequestNotificationListenerPermission();
    }

    private void initViews() {
        cardLog = findViewById(R.id.cardLog);
        textviewLog = findViewById(R.id.textview);
        textViewMailStatus = findViewById(R.id.textView2);
        textViewLanStatus = findViewById(R.id.textViewLanStatus);
        switchLan = findViewById(R.id.switchLan);

        // LAN 转发开关
        boolean lanEnabled = LanPreferences.isLanEnabled(this);
        switchLan.setChecked(lanEnabled);
        switchLan.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LanPreferences.setLanEnabled(MainActivity.this, isChecked);
            if (isChecked) {
                LanManager.getInstance(MainActivity.this).start();
            } else {
                LanManager.getInstance(MainActivity.this).stop();
            }
            updateMailStatus(); // 刷新状态显示
        });

        // 如果开关已打开，自动启动 LAN 服务
        if (lanEnabled) {
            LanManager.getInstance(this).start();
        }

        // 按钮：发送测试通知
        MaterialButton btnSendNotification = findViewById(R.id.button);
        btnSendNotification.setOnClickListener(v -> sendTestNotification());

        // 按钮：显示文件并删除
        MaterialButton btnShowFile = findViewById(R.id.button2);
        btnShowFile.setOnClickListener(v -> showFileAndDelete());

        // 按钮：邮件登录
        MaterialButton btnMailLogin = findViewById(R.id.button3);
        btnMailLogin.setOnClickListener(v -> navigateTo(MailSendActivity.class));

        // 按钮：历史管理
        MaterialButton btnHistory = findViewById(R.id.button4);
        btnHistory.setOnClickListener(v -> navigateTo(HistoryManagerActivity.class));

        // 按钮：过滤设置
        MaterialButton btnFilter = findViewById(R.id.button5);
        btnFilter.setOnClickListener(v -> navigateTo(FilterSettingsActivity.class));

        // 按钮：局域网转发
        MaterialButton btnLan = findViewById(R.id.button6);
        btnLan.setOnClickListener(v -> navigateTo(LanForwardActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMailStatus();
    }

    private void updateMailStatus() {
        Session session = MailSessionManager.getSession();
        if (session == null) {
            textViewMailStatus.setText("Not configured");
            textViewMailStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
        } else {
            String email = MailSessionManager.getCurrentEmail();
            textViewMailStatus.setText(email != null ? email : "Configured");
            textViewMailStatus.setTextColor(ContextCompat.getColor(this, R.color.primary));
        }

        // 局域网转发状态
        boolean lanEnabled = LanPreferences.isLanEnabled(this);
        if (!lanEnabled) {
            textViewLanStatus.setText("Disabled");
            textViewLanStatus.setTextColor(ContextCompat.getColor(this,
                    R.color.md_theme_light_on_surface_variant));
        } else {
            LanManager lan = LanManager.getInstance(this);
            if (lan.isRunning()) {
                int count = lan.getDeviceCount();
                textViewLanStatus.setText(count > 0
                        ? count + " device" + (count != 1 ? "s" : "")
                        : "No devices");
                textViewLanStatus.setTextColor(ContextCompat.getColor(this, R.color.primary));
            } else {
                textViewLanStatus.setText("Stopped");
                textViewLanStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
            }
        }
    }

    private void sendTestNotification() {
        Context context = getBaseContext();
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

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

        String fileContent = readFile(fileName);

        // 显示日志卡片
        cardLog.setVisibility(View.VISIBLE);
        textviewLog.setText(fileContent.isEmpty() ? "(no notifications today)" : fileContent);

        // 删除所有 md 文件
        boolean deleted = delAllFiles(getBaseContext());
        Toast.makeText(getBaseContext(),
                deleted ? "Files deleted" : "No files to delete or failed",
                Toast.LENGTH_LONG).show();

        // 清空并更新小部件
        NotificationWidgetProvider.clearWidgetItems();
        NotificationWidgetProvider.updateWidget(getBaseContext());
    }

    /**
     * 页面导航（不需要后台线程）
     */
    private void navigateTo(Class<?> targetActivity) {
        startActivity(new Intent(this, targetActivity));
    }

    @SuppressLint("SimpleDateFormat")
    private String getCurrentDate() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

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
            return "";
        } finally {
            try {
                if (reader != null) reader.close();
                if (fis != null) fis.close();
            } catch (IOException e) {
                // ignore
            }
        }

        return stringBuilder.toString();
    }

    private void checkAndRequestNotificationListenerPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            String enabledListeners = Settings.Secure.getString(
                    getContentResolver(), "enabled_notification_listeners");

            boolean isEnabled = enabledListeners != null &&
                    enabledListeners.contains(getPackageName());

            if (!isEnabled) {
                new AlertDialog.Builder(this)
                        .setTitle("Notification Listener Required")
                        .setMessage("This app needs notification listener permission to work. Please enable it in settings.")
                        .setPositiveButton("Open Settings", (dialog, which) ->
                                startActivityForResult(new Intent(
                                        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                                        REQUEST_NOTIFICATION_LISTENER))
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
                    getContentResolver(), "enabled_notification_listeners");

            Toast.makeText(this,
                    enabledListeners != null && enabledListeners.contains(getPackageName())
                            ? "Notification permission granted"
                            : "Notification permission not granted",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
