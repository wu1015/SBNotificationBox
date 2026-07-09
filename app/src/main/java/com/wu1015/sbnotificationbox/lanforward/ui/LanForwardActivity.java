package com.wu1015.sbnotificationbox.lanforward.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
import com.google.android.material.textfield.TextInputEditText;
import com.wu1015.sbnotificationbox.R;
import com.wu1015.sbnotificationbox.lanforward.model.LanDevice;
import com.wu1015.sbnotificationbox.lanforward.model.LanMessage;
import com.wu1015.sbnotificationbox.lanforward.network.DiscoveryService;
import com.wu1015.sbnotificationbox.lanforward.network.MessageClient;
import com.wu1015.sbnotificationbox.lanforward.network.MessageServer;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences;
import com.wu1015.sbnotificationbox.notification.MyNotification;
import com.wu1015.sbnotificationbox.notification.NotificationWidgetProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LanForwardActivity extends AppCompatActivity {

    private static final int SERVER_PORT = 9877;

    private ListView listViewDevices;
    private ListView listViewMessages;
    private TextInputEditText editTextIp;
    private TextInputEditText editTextMessage;

    private final List<LanDevice> deviceList = new ArrayList<>();
    private final List<LanMessage> messageList = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;
    private ArrayAdapter<String> messageAdapter;

    private MessageServer messageServer;
    private DiscoveryService discoveryService;
    private LanDevice selectedDevice;

    private String myDeviceName;

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

        btnScan.setOnClickListener(v -> doScan());
        btnConnect.setOnClickListener(v -> connectToIp());
        btnSend.setOnClickListener(v -> sendTextMessage());
        btnAttach.setOnClickListener(v -> pickFile());
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, LanSettingsActivity.class)));

        listViewDevices.setOnItemClickListener((parent, view, pos, id) -> {
            selectedDevice = deviceList.get(pos);
            Toast.makeText(this, "Selected: " + selectedDevice.getDeviceName(),
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void initAdapters() {
        deviceAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_2, android.R.id.text1) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                View view = super.getView(pos, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);
                LanDevice d = deviceList.get(pos);
                text1.setText(d.getDeviceName());
                text2.setText(d.getIpAddress() + ":" + d.getPort() +
                        (d.isOnline() ? " ● Online" : " ○ Offline"));
                text2.setTextColor(d.isOnline() ? 0xFF4CAF50 : 0xFFBDBDBD);
                return view;
            }
        };
        listViewDevices.setAdapter(deviceAdapter);

        messageAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_2, android.R.id.text1) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                View view = super.getView(pos, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);
                LanMessage m = messageList.get(pos);
                String prefix = m.getDirection() == LanMessage.Direction.SENT ? "→ " : "← ";
                text1.setText(prefix + "[" + m.getDeviceName() + "]");
                text2.setText(m.getSummary());
                return view;
            }
        };
        listViewMessages.setAdapter(messageAdapter);
    }

    // === 服务器 ===

    private void startServer() {
        String saveDir = LanPreferences.getStorageDir(this);
        new File(saveDir).mkdirs();

        messageServer = new MessageServer(SERVER_PORT, saveDir, new MessageServer.MessageListener() {
            @Override
            public void onTextMessage(LanMessage message) {
                runOnUiThread(() -> {
                    messageList.add(message);
                    messageAdapter.notifyDataSetChanged();
                    // 文字消息显示在小组件上
                    String widgetText = "LAN:" + message.getDeviceName() + " - " + message.getContent();
                    NotificationWidgetProvider.addItemToWidget(
                            new MyNotification("LAN:" + message.getDeviceName(), message.getContent()));
                    NotificationWidgetProvider.updateWidget(getApplicationContext());
                });
            }

            @Override
            public LanMessage onFileMessageRequest(LanMessage message) {
                // 在 UI 线程等待用户确认（阻塞方式用 CountDownLatch）
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                LanMessage[] result = new LanMessage[1];

                runOnUiThread(() -> {
                    new AlertDialog.Builder(LanForwardActivity.this)
                            .setTitle("Receive File?")
                            .setMessage(message.getDeviceName() + " wants to send:\n"
                                    + message.getFileName() + "\n"
                                    + formatSize(message.getFileSize()))
                            .setPositiveButton("Accept", (d, w) -> {
                                message.setFileReceived(true);
                                result[0] = message;
                                latch.countDown();
                            })
                            .setNegativeButton("Reject", (d, w) -> {
                                result[0] = null;
                                latch.countDown();
                            })
                            .setCancelable(false)
                            .show();
                });

                try { latch.await(); } catch (InterruptedException e) { return null; }
                return result[0];
            }

            @Override
            public void onFileReceived(LanMessage message) {
                runOnUiThread(() -> {
                    messageList.add(message);
                    messageAdapter.notifyDataSetChanged();
                    Toast.makeText(LanForwardActivity.this,
                            "File received: " + message.getFileName(), Toast.LENGTH_LONG).show();
                });
            }
        });
        messageServer.start();
    }

    // === 设备发现 ===

    private void doScan() {
        deviceList.clear();
        deviceAdapter.notifyDataSetChanged();
        Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show();

//        discoveryService = new DiscoveryService(this, myDeviceName, SERVER_PORT,
//                device -> runOnUiThread(() -> {
//                    if (!deviceList.contains(device)) {
//                        deviceList.add(device);
//                        deviceAdapter.notifyDataSetChanged();
//                    }
//                }));
//
//        new Thread(() -> {
//            List<LanDevice> found = discoveryService.discoverOnce();
//            runOnUiThread(() -> {
//                for (LanDevice d : found) {
//                    if (!deviceList.contains(d)) {
//                        deviceList.add(d);
//                    }
//                }
//                deviceAdapter.notifyDataSetChanged();
//                Toast.makeText(this, "Found " + found.size() + " device(s)",
//                        Toast.LENGTH_SHORT).show();
//            });
//        }).start();
    }

    private void connectToIp() {
        String input = editTextIp.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Enter IP:Port", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] parts = input.split(":");
        String ip = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : SERVER_PORT;

        selectedDevice = new LanDevice(ip, ip, port);
        if (!deviceList.contains(selectedDevice)) {
            deviceList.add(selectedDevice);
            deviceAdapter.notifyDataSetChanged();
        }
        Toast.makeText(this, "Connected to " + ip + ":" + port, Toast.LENGTH_SHORT).show();
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
            boolean ok = MessageClient.sendText(myDeviceName,
                    selectedDevice.getIpAddress(), selectedDevice.getPort(), text);
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

        try {
            String fileName = getFileName(uri);
            long fileSize = getFileSize(uri);
            boolean isImage = fileName.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp)$");

            // 拷贝到临时文件
            File tempFile = new File(getCacheDir(), fileName);
            InputStream is = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
            is.close();
            fos.close();

            LanMessage.Type type = isImage ? LanMessage.Type.IMAGE : LanMessage.Type.FILE;
            LanMessage msg = LanMessage.createFile(myDeviceName, fileName,
                    tempFile.length(), type, LanMessage.Direction.SENT);
            messageList.add(msg);
            messageAdapter.notifyDataSetChanged();

            File finalFile = tempFile;
            LanDevice dev = selectedDevice;
            new Thread(() -> {
                boolean ok = MessageClient.sendFile(myDeviceName,
                        dev.getIpAddress(), dev.getPort(), finalFile, type);
                finalFile.delete(); // 清理临时文件
                runOnUiThread(() -> {
                    if (!ok) Toast.makeText(this, "Send failed", Toast.LENGTH_SHORT).show();
                });
            }).start();

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

    private long getFileSize(Uri uri) {
        long size = 0;
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null,
                    null, null, null);
            if (cursor != null) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && cursor.moveToFirst()) {
                    size = cursor.getLong(idx);
                }
                cursor.close();
            }
        } catch (Exception ignored) {}
        return size;
    }

    // === 工具 ===

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageServer != null) messageServer.stop();
        if (discoveryService != null) discoveryService.stop();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
