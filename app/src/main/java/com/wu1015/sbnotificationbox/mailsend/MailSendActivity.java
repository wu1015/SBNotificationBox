package com.wu1015.sbnotificationbox.mailsend;

import static com.wu1015.sbnotificationbox.notification.FileUtils.delAllFiles;

import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.wu1015.sbnotificationbox.R;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MailSendActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_mail_send);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);


            EditText editTextId = findViewById(R.id.editTextText);
            EditText editTextPassword = findViewById(R.id.editTextTextPassword);
            EditText editTextToMail = findViewById(R.id.editTextText2);
            Button btnLogin = findViewById(R.id.button);

            btnLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String account = editTextId.getText().toString();
                    String password = editTextPassword.getText().toString();
                    String toMail = editTextToMail.getText().toString();

                    new Thread(() -> {
                        try {
                            EmailSender sender = new EmailSender();
                            // todo 暂时只支持outlook，后续添加其他邮箱登录的判断逻辑
                            sender.outlookSender(account, password);
                            boolean flag = EmailSender.sendEmail2(account, toMail, "Test Email", "Hello from Android!");
                            // todo 无法在线程里使用Toast，需要另外处理
                            // 返回验证邮箱成功与否
                            // Toast.makeText(getBaseContext(), String.valueOf(flag), Toast.LENGTH_LONG).show();

                            // 初始化message信息
                            Message message = new MimeMessage(MailSessionManager.getSession());
                            message.setFrom(new InternetAddress(account));
                            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toMail));
                            PersistentTransport.setMessage(message);

                            if(flag){
                                finish();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            });
            return insets;
        });
    }
}