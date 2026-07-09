package com.wu1015.sbnotificationbox.mailsend;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.wu1015.sbnotificationbox.R;

import java.util.ArrayList;
import java.util.List;

public class MailSendActivity extends AppCompatActivity {

    private MaterialAutoCompleteTextView spinnerProvider;
    private TextInputEditText editTextId;
    private TextInputEditText editTextPassword;
    private TextInputEditText editTextToMail;
    private TextInputEditText editTextSmtpHost;
    private TextInputEditText editTextSmtpPort;
    private MaterialCardView cardCustomSmtp;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private TextView textViewStatus;

    private List<EmailSender.Provider> providerList;
    private int selectedProviderPosition = 0; // 追踪当前选中的位置

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
        setupProviderDropdown();
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
        cardCustomSmtp = findViewById(R.id.cardCustomSmtp);
        btnLogin = findViewById(R.id.button);
        progressBar = findViewById(R.id.progressBar);
        textViewStatus = findViewById(R.id.textViewStatus);
    }

    private void setupProviderDropdown() {
        providerList = new ArrayList<>();
        providerList.add(null); // 自动检测

        List<String> displayNames = new ArrayList<>();
        displayNames.add(getString(R.string.provider_auto));

        for (EmailSender.Provider provider : EmailSender.Provider.values()) {
            if (provider != EmailSender.Provider.CUSTOM) {
                providerList.add(provider);
                displayNames.add(provider.getDisplayName());
            }
        }
        providerList.add(EmailSender.Provider.CUSTOM);
        displayNames.add(getString(R.string.provider_custom));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, displayNames);
        spinnerProvider.setAdapter(adapter);
        spinnerProvider.setText(displayNames.get(0), false); // 默认显示 Auto Detect

        spinnerProvider.setOnItemClickListener((parent, view, position, id) -> {
            selectedProviderPosition = position;
            EmailSender.Provider selected = providerList.get(position);
            cardCustomSmtp.setVisibility(
                    selected == EmailSender.Provider.CUSTOM ? View.VISIBLE : View.GONE);
        });
    }

    private void setupEmailAutoDetect() {
        editTextId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString().trim();
                // 仅当当前选择为 Auto Detect 时自动切换
                if (selectedProviderPosition == 0 && email.contains("@")) {
                    EmailSender.Provider detected = EmailSender.detectProvider(email);
                    if (detected != EmailSender.Provider.CUSTOM) {
                        int index = providerList.indexOf(detected);
                        if (index >= 0) {
                            spinnerProvider.setText(
                                    spinnerProvider.getAdapter().getItem(index).toString(), false);
                            cardCustomSmtp.setVisibility(View.GONE);
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

            setLoading(true);

            int selectedPos = selectedProviderPosition;
            EmailSender.Provider selectedProvider = providerList.get(selectedPos);

            new Thread(() -> {
                try {
                    boolean success;

                    if (selectedProvider == null) {
                        success = EmailSender.sendTestEmail(account, password, toMail);
                    } else if (selectedProvider == EmailSender.Provider.CUSTOM) {
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
                        EmailSender.initSession(selectedProvider, account, password);
                        success = EmailSender.sendEmail2(account, toMail,
                                "[SBNotificationBox] Test Email",
                                "Hello! This is a test email from SBNotificationBox.\n"
                                        + "Provider: " + selectedProvider.getDisplayName());
                    }

                    if (success) {
                        SecureEmailPreferences.saveEmail(getBaseContext(), account, toMail);
                        runOnUiThread(() -> {
                            setLoading(false);
                            textViewStatus.setText(R.string.send_success);
                            textViewStatus.setTextColor(ContextCompat.getColor(
                                    MailSendActivity.this, R.color.primary));
                            showToastOnUi(getString(R.string.send_success));
                            findViewById(R.id.main).postDelayed(this::finish, 1000);
                        });
                    } else {
                        runOnUiThread(() -> {
                            setLoading(false);
                            textViewStatus.setText(R.string.send_failed);
                            textViewStatus.setTextColor(ContextCompat.getColor(
                                    MailSendActivity.this, R.color.error));
                            showToastOnUi(getString(R.string.send_failed));
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        textViewStatus.setText(getString(R.string.send_failed) + ": " + e.getMessage());
                        textViewStatus.setTextColor(ContextCompat.getColor(
                                MailSendActivity.this, R.color.error));
                    });
                }
            }).start();
        });
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".") && email.length() > 5;
    }

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
            textViewStatus.setTextColor(ContextCompat.getColor(this, R.color.secondary));
        }
    }

    private void showToastOnUi(String message) {
        runOnUiThread(() -> Toast.makeText(getBaseContext(), message, Toast.LENGTH_LONG).show());
    }
}
