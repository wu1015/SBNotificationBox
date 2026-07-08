package com.wu1015.sbnotificationbox.mailsend;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.wu1015.sbnotificationbox.R;

import java.util.ArrayList;
import java.util.List;

public class MailSendActivity extends AppCompatActivity {

    private Spinner spinnerProvider;
    private EditText editTextId;
    private EditText editTextPassword;
    private EditText editTextToMail;
    private EditText editTextSmtpHost;
    private EditText editTextSmtpPort;
    private Button btnLogin;
    private ProgressBar progressBar;
    private TextView textViewStatus;

    private List<EmailSender.Provider> providerList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_mail_send);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupProviderSpinner();
        setupEmailAutoDetect();
        setupLoginButton();
    }

    private void initViews() {
        spinnerProvider = findViewById(R.id.spinnerProvider);
        editTextId = findViewById(R.id.editTextText);
        editTextPassword = findViewById(R.id.editTextTextPassword);
        editTextToMail = findViewById(R.id.editTextText2);
        editTextSmtpHost = findViewById(R.id.editTextSmtpHost);
        editTextSmtpPort = findViewById(R.id.editTextSmtpPort);
        btnLogin = findViewById(R.id.button);
        progressBar = findViewById(R.id.progressBar);
        textViewStatus = findViewById(R.id.textViewStatus);
    }

    private void setupProviderSpinner() {
        // 构建提供商列表（Auto Detect + 所有内置提供商 + Custom）
        providerList = new ArrayList<>();
        providerList.add(null); // null 代表自动检测

        List<String> displayNames = new ArrayList<>();
        displayNames.add(getString(R.string.provider_auto));

        for (EmailSender.Provider provider : EmailSender.Provider.values()) {
            if (provider != EmailSender.Provider.CUSTOM) {
                providerList.add(provider);
                displayNames.add(provider.getDisplayName() + " (" + provider.getHost() + ":" + provider.getPort() + ")");
            }
        }
        // 最后加入自定义选项
        providerList.add(EmailSender.Provider.CUSTOM);
        displayNames.add(getString(R.string.provider_custom));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvider.setAdapter(adapter);

        spinnerProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EmailSender.Provider selected = providerList.get(position);
                if (selected == EmailSender.Provider.CUSTOM) {
                    // 显示自定义 SMTP 配置
                    editTextSmtpHost.setVisibility(View.VISIBLE);
                    editTextSmtpPort.setVisibility(View.VISIBLE);
                } else {
                    editTextSmtpHost.setVisibility(View.GONE);
                    editTextSmtpPort.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不做任何操作
            }
        });
    }

    /**
     * 当用户在邮箱地址栏输入时，自动检测并选择对应的提供商
     */
    private void setupEmailAutoDetect() {
        editTextId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString().trim();
                // 仅当 spinner 当前为"Auto Detect"时才自动切换
                if (spinnerProvider.getSelectedItemPosition() == 0 && email.contains("@")) {
                    EmailSender.Provider detected = EmailSender.detectProvider(email);
                    if (detected != EmailSender.Provider.CUSTOM) {
                        int index = providerList.indexOf(detected);
                        if (index >= 0) {
                            spinnerProvider.setSelection(index);
                        }
                    }
                }
            }
        });
    }

    private void setupLoginButton() {
        btnLogin.setOnClickListener(v -> {
            String account = editTextId.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();
            String toMail = editTextToMail.getText().toString().trim();

            // 输入验证
            if (!isValidEmail(account)) {
                showToastOnUi(getString(R.string.invalid_email));
                return;
            }
            if (password.isEmpty()) {
                showToastOnUi(getString(R.string.invalid_password));
                return;
            }
            if (!isValidEmail(toMail)) {
                showToastOnUi(getString(R.string.invalid_recipient));
                return;
            }

            // 显示加载状态
            setLoading(true);

            // 获取选中的提供商
            int selectedPos = spinnerProvider.getSelectedItemPosition();
            EmailSender.Provider selectedProvider = providerList.get(selectedPos);

            new Thread(() -> {
                try {
                    boolean success;

                    if (selectedProvider == null) {
                        // 自动检测
                        success = EmailSender.sendTestEmail(account, password, toMail);
                    } else if (selectedProvider == EmailSender.Provider.CUSTOM) {
                        // 自定义 SMTP
                        String smtpHost = editTextSmtpHost.getText().toString().trim();
                        String smtpPort = editTextSmtpPort.getText().toString().trim();
                        if (smtpHost.isEmpty() || smtpPort.isEmpty()) {
                            runOnUiThread(() -> {
                                setLoading(false);
                                showToastOnUi("Please enter SMTP host and port");
                            });
                            return;
                        }
                        EmailSender.initSession(smtpHost, smtpPort, account, password);
                        success = EmailSender.sendEmail2(account, toMail,
                                "[SBNotificationBox] Test Email",
                                "Hello! This is a test email from SBNotificationBox.");
                    } else {
                        // 使用指定提供商
                        EmailSender.initSession(selectedProvider, account, password);
                        success = EmailSender.sendEmail2(account, toMail,
                                "[SBNotificationBox] Test Email",
                                "Hello! This is a test email from SBNotificationBox.\n"
                                        + "Provider: " + selectedProvider.getDisplayName());
                    }

                    if (success) {
                        // 存储邮箱配置
                        SecureEmailPreferences.saveEmail(getBaseContext(), account, toMail);
                        runOnUiThread(() -> {
                            setLoading(false);
                            textViewStatus.setText(R.string.send_success);
                            textViewStatus.setTextColor(getColor(android.R.color.holo_green_dark));
                            showToastOnUi(getString(R.string.send_success));
                            // 延迟返回主界面
                            findViewById(R.id.main).postDelayed(this::finish, 1000);
                        });
                    } else {
                        runOnUiThread(() -> {
                            setLoading(false);
                            textViewStatus.setText(R.string.send_failed);
                            textViewStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                            showToastOnUi(getString(R.string.send_failed));
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        textViewStatus.setText(getString(R.string.send_failed) + ": " + e.getMessage());
                        textViewStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                    });
                }
            }).start();
        });
    }

    /**
     * 简单的邮箱格式验证
     */
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".") && email.length() > 5;
    }

    /**
     * 设置加载状态（禁用输入，显示进度条）
     */
    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        editTextId.setEnabled(!loading);
        editTextPassword.setEnabled(!loading);
        editTextToMail.setEnabled(!loading);
        spinnerProvider.setEnabled(!loading);
        editTextSmtpHost.setEnabled(!loading);
        editTextSmtpPort.setEnabled(!loading);
        if (loading) {
            textViewStatus.setText(R.string.sending);
            textViewStatus.setTextColor(getColor(android.R.color.holo_blue_dark));
        }
    }

    /**
     * 在主线程显示 Toast（可从后台线程安全调用）
     */
    private void showToastOnUi(String message) {
        runOnUiThread(() -> Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show());
    }
}
