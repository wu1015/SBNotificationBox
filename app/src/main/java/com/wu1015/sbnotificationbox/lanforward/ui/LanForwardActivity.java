package com.wu1015.sbnotificationbox.lanforward.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.wu1015.sbnotificationbox.R;
import com.wu1015.sbnotificationbox.lanforward.model.LanDevice;
import com.wu1015.sbnotificationbox.lanforward.model.LanMessage;
import com.wu1015.sbnotificationbox.lanforward.network.LanManager;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences;
import com.wu1015.sbnotificationbox.notification.MyNotification;
import com.wu1015.sbnotificationbox.notification.NotificationWidgetProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LanForwardActivity extends AppCompatActivity {

    private ListView listViewDevices;
    private ListView listViewMessages;
    private TextInputEditText editTextIp;
    private TextInputEditText editTextMessage;

    private final List<LanDevice> deviceList = new ArrayList<>();
    private final List<LanMessage> messageList = new ArrayList<>();
    private ArrayAdapter<LanDevice> deviceAdapter;
    private ArrayAdapter<LanMessage> messageAdapter;

    private LanManager lanManager;
    private LanManager.LanStatusListener lanListener;
    private LanDevice selectedDevice;

    private String myDeviceName;
    private SwitchMaterial switchLanForward;

    // UI 定期刷新
    private Handler refreshHandler;
    private Runnable refreshTask;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::onFilePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lan_forward);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        myDeviceName = LanPreferences.getDeviceName(this);

        initViews();
        initAdapters();
        startServer();
    }

    // === 初始化 ===

    private void initViews() {
        listViewDevices = findViewById(R.id.listViewDevices);
        listViewMessages = findViewById(R.id.listViewMessages);
        editTextIp = findViewById(R.id.editTextIp);
        editTextMessage = findViewById(R.id.editTextMessage);

        MaterialButton btnScan = findViewById(R.id.btnScan);
        MaterialButton btnConnect = findViewById(R.id.btnConnect);
        MaterialButton btnSend = findViewById(R.id.btnSend);
        MaterialButton btnAttach = findViewById(R.id.btnAttach);
        MaterialButton btnSettings = findViewById(R.id.btnSettings);
        switchLanForward = findViewById(R.id.switchLanForward);

        btnScan.setOnClickListener(v -> doScan());
        btnConnect.setOnClickListener(v -> connectToIp());
        btnSend.setOnClickListener(v -> sendTextMessage());
        btnAttach.setOnClickListener(v -> pickFile());
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, LanSettingsActivity.class)));

        // LAN 转发开关
        switchLanForward.setChecked(LanPreferences.isLanEnabled(this));
        switchLanForward.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LanPreferences.setLanEnabled(LanForwardActivity.this, isChecked);
            if (isChecked) {
                lanManager.start();
                // 刷新设备列表
                refreshDeviceList();
            } else {
                lanManager.stop();
                deviceList.clear();
                deviceAdapter.notifyDataSetChanged();
                if (selectedDevice != null) {
                    selectedDevice = null;
                    Toast.makeText(this, "LAN disabled, deselected device",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        listViewDevices.setOnItemClickListener((parent, view, pos, id) -> {
            selectedDevice = deviceList.get(pos);
            Toast.makeText(this, "Selected: " + selectedDevice.getDeviceName(),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void initAdapters() {
        // 设备列表适配器（使用自定义布局）
        deviceAdapter = new ArrayAdapter<LanDevice>(this,
                R.layout.item_device, deviceList) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_device, parent, false);
                }
                LanDevice d = deviceList.get(pos);

                TextView nameView = convertView.findViewById(R.id.textDeviceName);
                TextView statusView = convertView.findViewById(R.id.textDeviceStatus);
                MaterialButton btnConnect = convertView.findViewById(R.id.btnConnect);
                MaterialButton btnDisconnect = convertView.findViewById(R.id.btnDisconnect);

                nameView.setText(d.getDeviceName());
                String status = d.getIpAddress() + ":" + d.getPort()
                        + (d.isOnline() ? "  ● Online" : "  ○ Offline");
                statusView.setText(status);
                statusView.setTextColor(d.isOnline() ? 0xFF4CAF50 : 0xFFBDBDBD);

                // Connect：选中设备用于发消息。在线表示已连通，离线可尝试探测
                btnConnect.setOnClickListener(v -> connectToDevice(d));
                btnConnect.setEnabled(true);
                btnConnect.setText(d.isOnline() ? "Connected" : "Connect");

                // Disconnect：从列表移除设备
                btnDisconnect.setOnClickListener(v -> disconnectDevice(d));

                return convertView;
            }
        };
        listViewDevices.setAdapter(deviceAdapter);

        // 消息列表适配器
        messageAdapter = new ArrayAdapter<LanMessage>(this,
                android.R.layout.simple_list_item_2, android.R.id.text1, messageList) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                View view = super.getView(pos, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);
                LanMessage m = getItem(pos);
                if (m == null) return view;
                String prefix = m.getDirection() == LanMessage.Direction.SENT ? "→ " : "← ";
                text1.setText(prefix + "[" + m.getDeviceName() + "]");
                text2.setText(m.getSummary());
                return view;
            }
        };
        listViewMessages.setAdapter(messageAdapter);
    }

    // === 服务器 + 持续发现（使用 LanManager 单例） ===

    private void startServer() {
        lanManager = LanManager.getInstance(this);
        // 仅在开关打开时启动
        if (LanPreferences.isLanEnabled(this)) {
            lanManager.start();
        }

        // 注册消息监听器（文字消息显示在 UI + 小组件）
        lanListener = new LanManager.LanStatusListener() {
            @Override
            public void onStatusChanged(boolean running, int deviceCount) {
                // 设备状态变化时刷新列表（心跳/超时触发）
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    deviceList.clear();
                    deviceList.addAll(lanManager.getDevices());
                    deviceAdapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onTextReceived(LanMessage message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    messageList.add(message);
                    messageAdapter.notifyDataSetChanged();
                    // 仅将接收到的消息显示在小组件上。
                    // 转发的系统通知已由 MyNotificationListenerService 记录并显示，
                    // 此处不再重复添加以避免重复显示。
                    if (message.getType() == LanMessage.Type.TEXT
                            && message.getDirection() == LanMessage.Direction.RECEIVED) {
                        NotificationWidgetProvider.addItemToWidget(
                                new MyNotification("LAN:" + message.getDeviceName(),
                                        message.getContent()));
                        NotificationWidgetProvider.updateWidget(getApplicationContext());
                    }
                });
            }
        };
        lanManager.addListener(lanListener);

        // 初始加载设备列表
        refreshDeviceList();

        // 定期刷新设备列表（10 秒间隔，确保 UI 状态与后台同步）
        refreshHandler = new Handler();
        refreshTask = new Runnable() {
            @Override
            public void run() {
                refreshDeviceList();
                refreshHandler.postDelayed(this, 10000);
            }
        };
        refreshHandler.postDelayed(refreshTask, 10000);
    }

    private void refreshDeviceList() {
        if (lanManager != null) {
            deviceList.clear();
            deviceList.addAll(lanManager.getDevices());
            deviceAdapter.notifyDataSetChanged();
        }
    }

    // === 设备发现 ===

    /** 手动扫描：重发广播 + 刷新列表 */
    private void doScan() {
        Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show();

        // 刷新设备列表（从 LanManager 获取）
        refreshDeviceList();

        // 重新发送广播
        lanManager.rescan();

        // 定时刷新列表（等待响应到达）
        findViewById(R.id.main).postDelayed(() -> {
            refreshDeviceList();
            Toast.makeText(LanForwardActivity.this,
                    "Found " + deviceList.size() + " device(s)",
                    Toast.LENGTH_SHORT).show();
        }, 2000);
    }

    // === 设备连接 ===

    private void connectToIp() {
        String input = editTextIp.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Enter IP:Port", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] parts = input.split(":");
        String ip = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : lanManager.getServerPort();

        // 使用有意义的设备名称（而非 IP 地址）
        LanDevice device = new LanDevice("Device at " + ip, ip, port);
        lanManager.addManualDevice(device); // 异步 TCP 探测，初始为离线
        refreshDeviceList();
        selectedDevice = device;
        editTextIp.setText(""); // 清空输入框
        Toast.makeText(this, "Probing " + ip + ":" + port + "...",
                Toast.LENGTH_SHORT).show();

        // 延时刷新列表，等待 TCP 探测完成
        findViewById(R.id.main).postDelayed(() -> {
            refreshDeviceList();
            // 根据探测结果显示最终状态
            for (LanDevice d : deviceList) {
                if (d.getIpAddress().equals(ip) && d.getPort() == port) {
                    if (d.isOnline()) {
                        Toast.makeText(LanForwardActivity.this,
                                "Connected to " + d.getDeviceName(),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(LanForwardActivity.this,
                                "Cannot reach " + ip + ":" + port,
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
            }
        }, 3000);
    }

    private void connectToDevice(LanDevice device) {
        selectedDevice = device;
        // 持久化保存设备并触发 TCP 探测（addManualDevice 内异步执行）
        lanManager.addManualDevice(device);
        refreshDeviceList();

        // 如果设备已经在线（UDP 心跳已验证），直接提示已连接
        if (device.isOnline()) {
            Toast.makeText(this, "Connected to " + device.getDeviceName(),
                    Toast.LENGTH_SHORT).show();
        } else {
            // 设备离线，addManualDevice 会在后台探测，延时刷新确认结果
            Toast.makeText(this, "Probing " + device.getDeviceName() + "...",
                    Toast.LENGTH_SHORT).show();
            findViewById(R.id.main).postDelayed(() -> {
                refreshDeviceList();
                // 探测后再次检查状态并反馈
                for (LanDevice d : deviceList) {
                    if (d.getIpAddress().equals(device.getIpAddress())
                            && d.getPort() == device.getPort()) {
                        if (d.isOnline()) {
                            Toast.makeText(LanForwardActivity.this,
                                    "Connected to " + d.getDeviceName(),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(LanForwardActivity.this,
                                    "Cannot reach " + d.getDeviceName(),
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                    }
                }
            }, 3000);
        }
    }

    private void disconnectDevice(LanDevice device) {
        if (selectedDevice != null && selectedDevice.equals(device)) {
            selectedDevice = null;
        }
        lanManager.removeDevice(device); // 从 LanManager + 持久化中移除
        refreshDeviceList(); // 刷新本地列表
        Toast.makeText(this, "Removed " + device.getDeviceName(),
                Toast.LENGTH_SHORT).show();
    }

    // === 发送消息 ===

    private void sendTextMessage() {
        String text = editTextMessage.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Type a message", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedDevice == null) {
            Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show();
            return;
        }

        LanMessage msg = LanMessage.createText(myDeviceName, text, LanMessage.Direction.SENT);
        messageList.add(msg);
        messageAdapter.notifyDataSetChanged();
        editTextMessage.setText("");

        new Thread(() -> {
            boolean ok = lanManager.sendTextTo(
                    selectedDevice.getIpAddress(), selectedDevice.getPort(), text);
            if (isFinishing() || isDestroyed()) return;
            runOnUiThread(() -> {
                if (!ok) {
                    Toast.makeText(this, "Send failed", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // === 文件选择与发送 ===

    private void pickFile() {
        if (selectedDevice == null) {
            Toast.makeText(this, "Select a device first", Toast.LENGTH_SHORT).show();
            return;
        }
        filePickerLauncher.launch(new String[]{"image/*", "*/*"});
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) return;

        File tempFile = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            String fileName = getFileName(uri);
            // 大小写不敏感检测图片类型
            boolean isImage = fileName.toLowerCase(Locale.ROOT)
                    .matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)$");

            // 拷贝到临时文件
            tempFile = new File(getCacheDir(), fileName);
            is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show();
                return;
            }
            fos = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);

            LanMessage.Type type = isImage ? LanMessage.Type.IMAGE : LanMessage.Type.FILE;
            LanMessage msg = LanMessage.createFile(myDeviceName, fileName,
                    tempFile.length(), type, LanMessage.Direction.SENT);
            messageList.add(msg);
            messageAdapter.notifyDataSetChanged();

            File finalFile = tempFile;
            LanDevice dev = selectedDevice;
            new Thread(() -> {
                boolean ok = lanManager.sendFileTo(
                        dev.getIpAddress(), dev.getPort(), finalFile, type);
                finalFile.delete();
                if (isFinishing() || isDestroyed()) return;
                runOnUiThread(() -> {
                    if (!ok) Toast.makeText(this, "Send failed", Toast.LENGTH_SHORT).show();
                });
            }).start();

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (tempFile != null) tempFile.delete();
        } finally {
            // 安全关闭流
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
        }
    }

    private String getFileName(Uri uri) {
        String name = "file";
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null,
                    null, null, null);
            if (cursor != null) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(idx);
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
        return name;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 同步 LAN 开关状态（可能从主页面被修改）
        if (switchLanForward != null) {
            boolean lanEnabled = LanPreferences.isLanEnabled(this);
            if (switchLanForward.isChecked() != lanEnabled) {
                switchLanForward.setChecked(lanEnabled);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止定期刷新
        if (refreshHandler != null && refreshTask != null) {
            refreshHandler.removeCallbacks(refreshTask);
        }
        // 移除监听器，但不停止 LanManager（它是持久化单例）
        if (lanManager != null && lanListener != null) {
            lanManager.removeListener(lanListener);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
