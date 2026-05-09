package com.faisalmalik.nexusnetmonitor;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.List;

public class CellMonitorService extends Service {

    private static final String TAG = "NexusCellService";
    private static final String CHANNEL_ID = "nexus_monitor_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long SCAN_INTERVAL = 5000;

    private Handler handler;
    private Runnable scanRunnable;
    private TelephonyManager telephonyManager;
    private int cellCount = 0;
    private String lastOperator = "Scanning...";

    @Override
    public void onCreate() {
        super.onCreate();
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("NEXUS active · scanning all operators..."));
        startScanning();
        Log.d(TAG, "CellMonitorService started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("MissingPermission")
    private void startScanning() {
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    List<CellInfo> cells = telephonyManager.getAllCellInfo();
                    cellCount = cells != null ? cells.size() : 0;
                    lastOperator = telephonyManager.getNetworkOperatorName();
                    if (lastOperator == null || lastOperator.isEmpty()) lastOperator = "MCC:410";

                    String status = String.format(
                        "📡 %s · %d cells · %s",
                        lastOperator,
                        cellCount,
                        getNetworkTypeShort()
                    );
                    updateNotification(status);
                } catch (Exception e) {
                    Log.e(TAG, "Scan error: " + e.getMessage());
                }
                handler.postDelayed(this, SCAN_INTERVAL);
            }
        };
        handler.post(scanRunnable);
    }

    @SuppressLint("MissingPermission")
    private String getNetworkTypeShort() {
        try {
            int type = telephonyManager.getDataNetworkType();
            switch (type) {
                case TelephonyManager.NETWORK_TYPE_LTE: return "4G LTE";
                case TelephonyManager.NETWORK_TYPE_NR:  return "5G NR";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_HSPAP: return "3G";
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:  return "2G";
                default: return "N/A";
            }
        } catch (Exception e) { return "N/A"; }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Nexus Net Monitor",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Continuous cell tower monitoring");
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String content) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, intent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("NEXUS Net Monitor")
            .setContentText(content)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    private void updateNotification(String content) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(content));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }
        Log.d(TAG, "CellMonitorService stopped");
    }
}
