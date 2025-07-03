package com.wu1015.sbnotificationbox;

import static com.wu1015.sbnotificationbox.notification.FileUtils.delAllFiles;
import static com.wu1015.sbnotificationbox.notification.FileUtils.getFilesArrayList;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
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

import com.wu1015.sbnotificationbox.mailsend.MailSendActivity;
import com.wu1015.sbnotificationbox.mailsend.MailSessionManager;
import com.wu1015.sbnotificationbox.notification.MyNotificationListenerService;
import com.wu1015.sbnotificationbox.notification.NotificationWidgetProvider;

import java.text.SimpleDateFormat;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

import javax.mail.Session;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);

            Button button = findViewById(R.id.button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 设置为0时传入的参数会被覆盖(与这个无关，因为处理的是同样的intent)
                    // Android12以上PendingIntent需要强制增加FLAG_IMMUTABLE或FLAG_MUTABLE
                    Context context = getBaseContext();
                    NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                    // 创建 NotificationChannel 对象
                    NotificationChannel channel = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        channel = new NotificationChannel("Channel_ID",
                                "chat message", NotificationManager.IMPORTANCE_DEFAULT);
                    }

                    // 创建通知渠道
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mNotificationManager.createNotificationChannel(channel);
                    }
                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, "Channel_ID")
                            .setContentTitle("strTitle")
                            .setContentText("strContext")
                            .setWhen(System.currentTimeMillis())
                            .setSmallIcon(R.drawable.ic_launcher_foreground);
                    // 点击后消失
                    // .setAutoCancel(true);

                    // 发送通知( id唯一,可用于更新通知时对应旧通知; 通过mBuilder.build()拿到notification对象 )
                    mNotificationManager.notify(1, mBuilder.build());
                }
            });

            Button button1 = findViewById(R.id.button2);
            button1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 获取当前日期并读取对应的日志文件内容
                    String currentDate = getCurrentDate();
                    String fileName = currentDate + "_notifications_log.md"; // 文件名为当前日期

                    // 读取文件内容
                    String fileContent = readFile(fileName);

                    // 显示文件内容
                    TextView textView = findViewById(R.id.textview);
                    textView.setText(fileContent);

                    // Toast.makeText(getBaseContext(), getFilesArrayList(getBaseContext()).get(1).getFileName(), Toast.LENGTH_LONG).show();
                    // 返回文件删除结果
                    Toast.makeText(getBaseContext(), String.valueOf(delAllFiles(getBaseContext())), Toast.LENGTH_LONG).show();
                    // 删除小部件内容
                    NotificationWidgetProvider.clearWidgetItems();
                    // 更新小部件
                    NotificationWidgetProvider.updateWidget(getBaseContext());

                }
            });

            Button button2 = findViewById(R.id.button3);
            button2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Thread thread = new Thread() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(getApplicationContext(), MailSendActivity.class);
                            startActivity(intent);
                        }
                    };
                    thread.start();
                }
            });

            return insets;
        });

        TextView mailStatus = findViewById(R.id.textView2);
        Session session = MailSessionManager.getSession();
        if (session == null) {
            mailStatus.setText("mailStatus is false");
        }else{
            mailStatus.setText("mailStatus is true");
        }

    }

    // 获取当前日期，格式为 yyyyMMdd
    private String getCurrentDate() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        return sdf.format(new Date());
    }

    // 读取文件内容
    private String readFile(String fileName) {
        FileInputStream fis = null;
        InputStreamReader reader = null;
        StringBuilder stringBuilder = new StringBuilder();

        try {
            // 获取应用自带的文件目录并打开文件
            fis = openFileInput(fileName);
            reader = new InputStreamReader(fis);

            // 读取文件内容
            int charRead;
            while ((charRead = reader.read()) != -1) {
                stringBuilder.append((char) charRead);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return stringBuilder.toString();  // 返回文件的内容
    }
}