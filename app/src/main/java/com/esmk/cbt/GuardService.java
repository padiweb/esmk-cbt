package com.esmk.cbt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

public class GuardService extends Service {

    private static final String CHANNEL_ID = "esmk_guard";
    private static final int NOTIF_ID = 1001;
    private static final int CHECK_INTERVAL = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable guardRunnable;
    public static boolean isRunning = false;
    public static String lastViolation = null;

    @Override
    public void onCreate() {
        super.onCreate();
        // Buat notifikasi SEBELUM apapun — wajib di Android 8+
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        isRunning = true;
        startGuard();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "E-SMK Keamanan Ujian",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Layanan keamanan ujian aktif");
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ujian Sedang Berlangsung")
            .setContentText("Sistem keamanan aktif")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }

    private void startGuard() {
        guardRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                try {
                    checkForegroundApp();
                } catch (Exception e) {
                    // Jangan crash — tangkap semua exception
                }
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.postDelayed(guardRunnable, CHECK_INTERVAL);
    }

    private void checkForegroundApp() {
        android.app.ActivityManager am =
            (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return;

        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs =
            am.getRunningAppProcesses();
        if (procs == null) return;

        for (android.app.ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.importance ==
                android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {

                String pkg = p.processName;
                if (pkg == null || pkg.startsWith("com.esmk.cbt")) continue;
                if (isSystemOrKeyboard(pkg)) continue;

                // Deteksi AI
                String label = getLabel(pkg);
                if (isAI(pkg)) {
                    triggerViolation("AI Terdeteksi: " + label);
                } else {
                    triggerViolation("Aplikasi Lain: " + label);
                }
                bringToFront();
                return;
            }
        }
    }

    private boolean isSystemOrKeyboard(String pkg) {
        return pkg.contains("systemui") || pkg.contains("launcher") ||
               pkg.contains("inputmethod") || pkg.contains("keyboard") ||
               pkg.contains("android.process") || pkg.contains("com.android") ||
               pkg.contains("com.google.android.gms") ||
               pkg.contains("com.miui.home") || pkg.contains("com.miui") ||
               pkg.contains("com.samsung.android.honeyboard") ||
               pkg.contains("com.sec.android") || pkg.contains("com.oppo") ||
               pkg.contains("com.coloros") || pkg.contains("com.realme");
    }

    private boolean isAI(String pkg) {
        return pkg.contains("genie") || pkg.contains("bixby") ||
               pkg.contains("assistant") || pkg.contains("cortana") ||
               pkg.contains("vassistant") || pkg.contains("aiassistant") ||
               pkg.contains("xgenie") || pkg.contains("voiceassistant");
    }

    private String getLabel(String pkg) {
        if (pkg.contains("calculator")) return "Kalkulator";
        if (pkg.contains("browser") || pkg.contains("chrome")) return "Browser";
        if (pkg.contains("whatsapp")) return "WhatsApp";
        if (pkg.contains("bixby")) return "Bixby AI";
        if (pkg.contains("genie") || pkg.contains("xgenie")) return "Infinix AI";
        if (pkg.contains("assistant")) return "Google Assistant";
        String[] p = pkg.split("\\.");
        return p.length > 0 ? p[p.length - 1] : pkg;
    }

    private void triggerViolation(String reason) {
        lastViolation = reason;
        Intent intent = new Intent("com.esmk.cbt.VIOLATION");
        intent.putExtra("reason", reason);
        sendBroadcast(intent);
    }

    private void bringToFront() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                           Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                           Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) { /* ignore */ }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (guardRunnable != null) handler.removeCallbacks(guardRunnable);
        // Auto-restart
        try {
            startService(new Intent(this, GuardService.class));
        } catch (Exception e) { /* ignore */ }
    }
}
