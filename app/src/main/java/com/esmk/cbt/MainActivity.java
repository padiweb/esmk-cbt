package com.esmk.cbt;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvError;
    private String serverUrl;
    private final Handler handler = new Handler();
    private Runnable immersiveRunnable;
    private BroadcastReceiver violationReceiver;
    private boolean isExamActive = false;

    private static final String DEFAULT_PIN = "1234";
    private int backPressCount = 0;
    private long lastBackPress = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // ── FLAG_SECURE: Blokir screenshot & screen recording ───
        // Ini yang paling penting — layar akan hitam saat di-screenshot
        // atau saat di-record oleh aplikasi manapun
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
            WindowManager.LayoutParams.FLAG_SECURE
        );

        setContentView(R.layout.activity_main);

        webView     = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        tvError     = findViewById(R.id.tv_error);

        SharedPreferences prefs = getSharedPreferences("esmk_config", MODE_PRIVATE);
        serverUrl = prefs.getString("server_url", "");

        hideSystemUI();
        startImmersivePolling();
        registerViolationReceiver();
        startGuardService();
        muteNotifications();
        setupWebView();
        loadUrl();

        isExamActive = true;
    }

    // ── IMMERSIVE POLLING ────────────────────────────────────────
    private void startImmersivePolling() {
        immersiveRunnable = new Runnable() {
            @Override
            public void run() {
                hideSystemUI();
                handler.postDelayed(this, 500);
            }
        };
        handler.postDelayed(immersiveRunnable, 500);
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (ctrl != null) {
                ctrl.hide(WindowInsetsCompat.Type.systemBars());
                ctrl.hide(WindowInsetsCompat.Type.navigationBars());
                ctrl.hide(WindowInsetsCompat.Type.statusBars());
                ctrl.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    // ── GUARD SERVICE ────────────────────────────────────────────
    private void startGuardService() {
        Intent serviceIntent = new Intent(this, GuardService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void stopGuardService() {
        stopService(new Intent(this, GuardService.class));
    }

    // ── VIOLATION RECEIVER ───────────────────────────────────────
    // Menerima broadcast dari GuardService saat ada pelanggaran
    private void registerViolationReceiver() {
        violationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String reason = intent.getStringExtra("reason");
                if (reason != null) {
                    // Kirim ke web ujian sebagai pelanggaran
                    reportViolationToWeb(reason);
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.esmk.cbt.VIOLATION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(violationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(violationReceiver, filter);
        }
    }

    // Injeksi JavaScript ke WebView untuk melaporkan pelanggaran ke server
    private void reportViolationToWeb(String reason) {
        if (webView == null || !isExamActive) return;
        String safeReason = reason.replace("'", "\\'").replace("\n", " ");
        String js = "javascript:(function(){" +
            "if(typeof triggerViolation === 'function'){" +
            "  triggerViolation('" + safeReason + "');" +
            "}" +
            "})();";
        runOnUiThread(() -> webView.loadUrl(js));
    }

    // ── MUTE NOTIFIKASI ──────────────────────────────────────────
    private void muteNotifications() {
        try {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
            }
            // Matikan suara & getar
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            }
        } catch (Exception e) {
            // Ignore jika tidak ada izin
        }
    }

    private void restoreNotifications() {
        try {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setUserAgentString(ws.getUserAgentString() + " ESMK-CBT/1.0");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(serverUrl) || url.startsWith(getDomain(serverUrl))) {
                    return false;
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                tvError.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                hideSystemUI();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                progressBar.setVisibility(View.GONE);
                showError();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           SslError error) {
                handler.proceed();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void loadUrl() {
        if (serverUrl.isEmpty()) { showError(); return; }
        tvError.setVisibility(View.GONE);
        webView.loadUrl(serverUrl);
    }

    private void showError() {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText("⚠️ Tidak dapat terhubung ke server.\n\n" +
            "Pastikan:\n• HP terhubung ke jaringan sekolah\n• Server ujian aktif\n\n" +
            "Ketuk 5x untuk coba lagi.");
        final int[] tapCount = {0};
        final long[] lastTap = {0};
        tvError.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            tapCount[0] = (now - lastTap[0] < 1000) ? tapCount[0] + 1 : 1;
            lastTap[0] = now;
            if (tapCount[0] >= 5) { tapCount[0] = 0; loadUrl(); }
        });
    }

    // ── DETEKSI KELUAR HALAMAN (onPause) ─────────────────────────
    // Dipanggil saat: Home ditekan, Recent Apps, notifikasi, dll
    @Override
    protected void onPause() {
        super.onPause();
        if (isExamActive) {
            // Laporkan pelanggaran ke web
            reportViolationToWeb("Keluar Halaman Ujian (Tombol Home/Recent)");
        }
    }

    // ── TOMBOL FISIK ─────────────────────────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                handleBackPress();
                return true;
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_ASSIST:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_POWER:
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_ASSIST:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private void handleBackPress() {
        long now = System.currentTimeMillis();
        backPressCount = (now - lastBackPress < 3000) ? backPressCount + 1 : 1;
        lastBackPress = now;
        if (backPressCount >= 5) { backPressCount = 0; showAdminPinDialog(); }
    }

    private void showAdminPinDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                           android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Masukkan PIN Pengawas");
        input.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(this)
            .setTitle("🔐 Akses Pengawas")
            .setMessage("Masukkan PIN untuk mengakses menu pengawas.")
            .setView(input)
            .setPositiveButton("Masuk", (dialog, which) -> {
                String pin = input.getText().toString();
                SharedPreferences prefs = getSharedPreferences("esmk_config", MODE_PRIVATE);
                if (pin.equals(prefs.getString("admin_pin", DEFAULT_PIN))) {
                    showAdminMenu();
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("❌ PIN Salah")
                        .setMessage("PIN tidak sesuai.")
                        .setPositiveButton("OK", null).show();
                }
            })
            .setNegativeButton("Batal", null).show();
    }

    private void showAdminMenu() {
        String[] menu = {
            "⚙️ Ubah Pengaturan (URL, Nama, PIN)",
            "🔄 Reload Halaman",
            "🚪 Keluar Aplikasi"
        };
        new AlertDialog.Builder(this)
            .setTitle("Menu Pengawas")
            .setItems(menu, (dialog, which) -> {
                switch (which) {
                    case 0:
                        isExamActive = false;
                        startActivity(new Intent(this, SettingsActivity.class));
                        break;
                    case 1:
                        webView.reload();
                        break;
                    case 2:
                        isExamActive = false;
                        restoreNotifications();
                        stopGuardService();
                        handler.removeCallbacks(immersiveRunnable);
                        finishAffinity();
                        System.exit(0);
                        break;
                }
            }).show();
    }

    // ── LIFECYCLE ────────────────────────────────────────────────
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        handler.removeCallbacks(immersiveRunnable);
        handler.postDelayed(immersiveRunnable, 500);
        isExamActive = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isExamActive = false;
        handler.removeCallbacks(immersiveRunnable);
        try {
            unregisterReceiver(violationReceiver);
        } catch (Exception e) { /* ignore */ }
        if (webView != null) webView.destroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (webView != null) webView.restoreState(savedInstanceState);
    }

    private String getDomain(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) { return url; }
    }
}
