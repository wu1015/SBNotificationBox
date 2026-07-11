package com.wu1015.sbnotificationbox.lanforward.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.wu1015.sbnotificationbox.MainActivity;
import com.wu1015.sbnotificationbox.R;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences;

/**
 * 轻量级前台服务，保持 LAN 功能在后台持续运行。
 * 通过持续通知告诉系统这是一个重要进程，降低被 kill 的概率。
 */
public class LanKeepAliveService extends Service {

    private static final String TAG = "LanKeepAlive";
    private static final String CHANNEL_ID = "lan_keep_alive_channel";
    private static final String CHANNEL_NAME = "LAN Keep Alive";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Keep-alive service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SBNotificationBox LAN")
                .setContentText("LAN forwarding is active")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        Log.i(TAG, "Foreground notification started");

        // 仅在用户已开启 LAN 转发时才启动 LanManager。
        // 系统因 START_STICKY 重启 service 时，如果用户已关闭开关则不再启动，
        // 避免后台持续占用资源。
        if (LanPreferences.isLanEnabled(this)) {
            LanManager.getInstance(this).start();
        } else {
            Log.i(TAG, "LAN disabled by user, stopping self");
            stopSelf();
            return START_NOT_STICKY;
        }

        // 如果 service 被 kill，系统会自动重启（粘性模式）
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Keep-alive service destroyed");
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 启动前台服务
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, LanKeepAliveService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * 停止前台服务
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, LanKeepAliveService.class);
        context.stopService(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Persistent notification for LAN keep-alive");
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }
}
