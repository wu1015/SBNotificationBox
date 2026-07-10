package com.wu1015.sbnotificationbox.lanforward.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.wu1015.sbnotificationbox.R;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences.LanMode;

import java.io.File;

public class LanSettingsActivity extends AppCompatActivity {

    private TextInputEditText editTextDeviceName;
    private TextInputEditText editTextStorageDir;
    private TextInputEditText editTextSecret;
    private MaterialButtonToggleGroup toggleLanMode;
    private MaterialButton btnModeSendReceive;
    private MaterialButton btnModeSendOnly;
    private MaterialButton btnModeReceiveOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lan_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initViews();
        loadSettings();
    }

    private void initViews() {
        editTextDeviceName = findViewById(R.id.editTextDeviceName);
        editTextStorageDir = findViewById(R.id.editTextStorageDir);
        editTextSecret = findViewById(R.id.editTextSecret);
        toggleLanMode = findViewById(R.id.toggleLanMode);
        btnModeSendReceive = findViewById(R.id.btnModeSendReceive);
        btnModeSendOnly = findViewById(R.id.btnModeSendOnly);
        btnModeReceiveOnly = findViewById(R.id.btnModeReceiveOnly);
        MaterialButton btnBrowse = findViewById(R.id.btnBrowseFolder);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        btnBrowse.setOnClickListener(v -> showFolderPicker());
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        editTextDeviceName.setText(LanPreferences.getDeviceName(this));
        editTextStorageDir.setText(LanPreferences.getStorageDir(this));
        editTextSecret.setText(LanPreferences.getConnectionSecret(this));

        // 加载收发模式
        LanMode mode = LanPreferences.getLanMode(this);
        switch (mode) {
            case SEND_ONLY:
                toggleLanMode.check(btnModeSendOnly.getId());
                break;
            case RECEIVE_ONLY:
                toggleLanMode.check(btnModeReceiveOnly.getId());
                break;
            case BOTH:
            default:
                toggleLanMode.check(btnModeSendReceive.getId());
                break;
        }
    }

    private void saveSettings() {
        String name = editTextDeviceName.getText().toString().trim();
        String dir = editTextStorageDir.getText().toString().trim();
        String secret = editTextSecret.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Device name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        LanPreferences.setDeviceName(this, name);
        LanPreferences.setConnectionSecret(this, secret);

        if (!dir.isEmpty()) {
            File f = new File(dir);
            if (!f.exists()) f.mkdirs();
            LanPreferences.setStorageDir(this, dir);
        }

        // 保存收发模式
        int checkedId = toggleLanMode.getCheckedButtonId();
        LanMode mode;
        if (checkedId == btnModeSendOnly.getId()) {
            mode = LanMode.SEND_ONLY;
        } else if (checkedId == btnModeReceiveOnly.getId()) {
            mode = LanMode.RECEIVE_ONLY;
        } else {
            mode = LanMode.BOTH;
        }
        LanPreferences.setLanMode(this, mode);

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showFolderPicker() {
        // 简化版：列出外部存储常见目录
        File base = Environment.getExternalStorageDirectory();
        String[] commonDirs = {"Download", "Documents", "DCIM", "Pictures", "Music"};
        String[] paths = new String[commonDirs.length + 2];
        paths[0] = base.getAbsolutePath() + "/SBNotificationBox/LAN";
        paths[1] = base.getAbsolutePath() + "/Download";
        for (int i = 0; i < commonDirs.length; i++) {
            paths[i + 2] = base.getAbsolutePath() + "/" + commonDirs[i];
        }

        new AlertDialog.Builder(this)
                .setTitle("Choose Folder")
                .setItems(paths, (dialog, which) -> {
                    editTextStorageDir.setText(paths[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
