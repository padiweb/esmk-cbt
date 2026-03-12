package com.esmk.cbt;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import java.util.Arrays;
import java.util.List;

/**
 * GuardService — foreground service yang berjalan selama ujian.
 * Tugas:
 * 1. Deteksi aplikasi berbahaya yang running di foreground (floating window)
 * 2. Paksa kembali ke E-SMK jika aplikasi lain muncul di depan
 * 3. Kirim sinyal pelanggaran ke MainActivity
 */
public class GuardService extends Service {

    private static final String CHANNEL_ID = "esmk_guard";
    private static final int NOTIF_ID = 1001;
    private static final int CHECK_INTERVAL = 800; // ms

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable guardRunnable;
    private ActivityManager activityManager;

    // Daftar package aplikasi yang DIIZINKAN tampil di foreground
    // Semua aplikasi lain akan dianggap pelanggaran
    private static final List<String> WHITELIST = Arrays.asList(
        "com.esmk.cbt",
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.inputmethod",
        "com.samsung.android.inputmethod",
        "com.sohu.inputplus",          // Gboard
        "com.google.android.gms"
    );

    // Package AI yang dikenal — dicatat sebagai pelanggaran khusus
    private static final List<String> AI_PACKAGES = Arrays.asList(
        "com.infinix.xgenie",           // Infinix XOS Genie AI
        "com.itel.aispeaker",
        "com.tecno.aiassistant",
        "com.samsung.android.bixby",    // Samsung Bixby
        "com.samsung.android.bixbyvision",
        "com.google.android.googlequicksearchbox", // Google Assistant
        "com.microsoft.cortana",        // Cortana
        "com.huawei.vassistant",        // Huawei AI
        "com.xiaomi.aiassistant",       // Xiaomi AI
        "com.oppo.voiceassistant",      // Oppo AI
        "com.vivo.assistant",           // Vivo AI
        "com.realme.voiceassistant"     // Realme AI
    );

    public static boolean isRunning = false;
    public static String lastViolation = null;

    @Override
    public void onCreate() {
        super.onCreate();
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        isRunning = true;
        startForegroundWithNotification();
        startGuard();
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "E-SMK Guard", NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Layanan keamanan ujian aktif");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔒 Ujian Sedang Berlangsung")
            .setContentText("Sistem keamanan aktif. Jangan tutup aplikasi ini.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();

        startForeground(NOTIF_ID, notif);
    }

    private void startGuard() {
        guardRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                checkForegroundApp();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.postDelayed(guardRunnable, CHECK_INTERVAL);
    }

    private void checkForegroundApp() {
        try {
            // Dapatkan aplikasi yang sedang di foreground
            List<ActivityManager.RunningAppProcessInfo> processes =
                activityManager.getRunningAppProcesses();

            if (processes == null) return;

            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.importance ==
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {

                    String pkg = process.processName;

                    // Skip jika package ada di whitelist
                    if (isWhitelisted(pkg)) continue;

                    // Deteksi AI assistant
                    if (isAIPackage(pkg)) {
                        triggerViolation("AI Assistant Terdeteksi: " + getAppLabel(pkg));
                        bringESMKToFront();
                        return;
                    }

                    // Aplikasi lain di foreground
                    if (!pkg.startsWith("com.esmk.cbt")) {
                        triggerViolation("Aplikasi Lain Dibuka: " + getAppLabel(pkg));
                        bringESMKToFront();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private boolean isWhitelisted(String pkg) {
        for (String w : WHITELIST) {
            if (pkg.contains(w) || pkg.startsWith(w)) return true;
        }
        // Keyboard & system UI selalu diizinkan
        return pkg.contains("inputmethod") || pkg.contains("keyboard") ||
               pkg.contains("systemui") || pkg.contains("launcher") ||
               pkg.contains("android.process");
    }

    private boolean isAIPackage(String pkg) {
        for (String ai : AI_PACKAGES) {
            if (pkg.contains(ai) || pkg.startsWith(ai)) return true;
        }
        return pkg.contains("genie") || pkg.contains("assistant") ||
               pkg.contains("bixby") || pkg.contains("cortana");
    }

    private String getAppLabel(String pkg) {
        // Kembalikan nama yang lebih ramah
        if (pkg.contains("calculator") || pkg.contains("kalkulator")) return "Kalkulator";
        if (pkg.contains("browser") || pkg.contains("chrome")) return "Browser";
        if (pkg.contains("whatsapp")) return "WhatsApp";
        if (pkg.contains("telegram")) return "Telegram";
        if (pkg.contains("tiktok")) return "TikTok";
        if (pkg.contains("bixby")) return "Samsung Bixby";
        if (pkg.contains("genie") || pkg.contains("xgenie")) return "Infinix AI";
        if (pkg.contains("assistant")) return "Google Assistant";
        // Ambil nama dari package
        String[] parts = pkg.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : pkg;
    }

    private void triggerViolation(String reason) {
        lastViolation = reason;
        // Kirim broadcast ke MainActivity
        Intent intent = new Intent("com.esmk.cbt.VIOLATION");
        intent.putExtra("reason", reason);
        sendBroadcast(intent);
    }

    private void bringESMKToFront() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                           Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                           Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Restart otomatis jika terbunuh sistem
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacks(guardRunnable);
        // Restart diri sendiri jika dibunuh
        Intent restartIntent = new Intent(this, GuardService.class);
        startService(restartIntent);
    }
}
