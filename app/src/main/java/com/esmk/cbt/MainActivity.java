package com.esmk.cbt;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView tvError;
    private String serverUrl;
    private final Handler handler = new Handler();
    private Runnable immersiveRunnable;
    private boolean isExamActive = false;
    private long lastViolTime = 0;

    private static final String DEFAULT_PIN = "1234";
    private int backPressCount = 0;
    private long lastBackPress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // FLAG_SECURE: blokir screenshot & screen recording
        // FLAG_KEEP_SCREEN_ON: layar tidak mati saat ujian
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
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
        setupWebView();
        loadUrl();
    }

    // ── IMMERSIVE MODE ──────────────────────────────────────────
    // Nav bar & status bar tersembunyi, polling setiap 500ms
    // agar langsung hilang jika sempat muncul
    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void startImmersivePolling() {
        immersiveRunnable = new Runnable() {
            @Override public void run() {
                hideSystemUI();
                handler.postDelayed(this, 500);
            }
        };
        handler.postDelayed(immersiveRunnable, 500);
    }

    // ── WEBVIEW ─────────────────────────────────────────────────
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
        // Penanda APK → ujian.php skip fullscreen gate
        ws.setUserAgentString(ws.getUserAgentString() + " ESMK-CBT/1.0");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith(serverUrl) || url.startsWith(getDomain(serverUrl)))
                    return false;
                return true; // Blokir URL di luar domain ujian
            }
            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                progressBar.setVisibility(View.VISIBLE);
                tvError.setVisibility(View.GONE);
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                hideSystemUI();
                isExamActive = true;
            }
            @Override
            public void onReceivedError(WebView v, int code, String desc, String url) {
                progressBar.setVisibility(View.GONE);
                showError();
            }
            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.proceed(); // Izinkan self-signed cert (server lokal)
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p);
                if (p == 100) progressBar.setVisibility(View.GONE);
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
        tvError.setText("⚠️ Tidak dapat terhubung ke server.\n\n"
            + "Pastikan:\n• HP terhubung ke jaringan sekolah\n"
            + "• Server ujian aktif\n\nKetuk 5x untuk coba lagi.");
        final int[] tap = {0};
        final long[] lt = {0};
        tvError.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            tap[0] = (now - lt[0] < 1000) ? tap[0] + 1 : 1;
            lt[0] = now;
            if (tap[0] >= 5) { tap[0] = 0; loadUrl(); }
        });
    }

    // ── KIRIM PELANGGARAN KE SERVER ─────────────────────────────
    private void reportViolation(String reason) {
        if (!isExamActive || webView == null) return;
        long now = System.currentTimeMillis();
        if (now - lastViolTime < 3000) return;
        lastViolTime = now;
        String safe = reason.replace("'", "\\'");
        runOnUiThread(() -> webView.loadUrl(
            "javascript:(function(){if(typeof triggerViolation==='function')" +
            "{triggerViolation('" + safe + "');}})();"
        ));
    }

    // ── TOMBOL FISIK ─────────────────────────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                handleBackPress(); return true;
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_ASSIST:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_CAMERA:
                return true;
            default: return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_ASSIST:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                return true;
            default: return super.onKeyUp(keyCode, event);
        }
    }

    private void handleBackPress() {
        long now = System.currentTimeMillis();
        backPressCount = (now - lastBackPress < 3000) ? backPressCount + 1 : 1;
        lastBackPress  = now;
        if (backPressCount >= 5) { backPressCount = 0; showAdminPinDialog(); }
        else { reportViolation("Tombol Kembali Ditekan"); }
    }

    // ── DETEKSI KELUAR HALAMAN ───────────────────────────────────
    @Override
    protected void onPause() {
        super.onPause();
        if (isExamActive) reportViolation("Keluar Halaman Ujian");
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        handler.removeCallbacks(immersiveRunnable);
        handler.postDelayed(immersiveRunnable, 500);
    }

    // ── ADMIN PIN & MENU ─────────────────────────────────────────
    private void showAdminPinDialog() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint("PIN Pengawas");
        et.setPadding(48, 24, 48, 24);

        new AlertDialog.Builder(this)
            .setTitle("🔐 Akses Pengawas")
            .setMessage("Masukkan PIN untuk mengakses menu pengawas.")
            .setView(et)
            .setPositiveButton("Masuk", (d, w) -> {
                String pin = et.getText().toString();
                SharedPreferences p = getSharedPreferences("esmk_config", MODE_PRIVATE);
                if (pin.equals(p.getString("admin_pin", DEFAULT_PIN))) showAdminMenu();
                else new AlertDialog.Builder(this)
                    .setTitle("❌ PIN Salah").setMessage("PIN tidak sesuai.")
                    .setPositiveButton("OK", null).show();
            })
            .setNegativeButton("Batal", null).show();
    }

    private void showAdminMenu() {
        new AlertDialog.Builder(this)
            .setTitle("Menu Pengawas")
            .setItems(new String[]{
                "⚙️ Ubah Pengaturan",
                "🔄 Reload Halaman",
                "🚪 Keluar Aplikasi"
            }, (d, w) -> {
                switch (w) {
                    case 0:
                        isExamActive = false;
                        startActivity(new Intent(this, SettingsActivity.class)); break;
                    case 1:
                        webView.reload(); break;
                    case 2:
                        isExamActive = false;
                        handler.removeCallbacks(immersiveRunnable);
                        finishAffinity(); System.exit(0); break;
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
    protected void onDestroy() {
        super.onDestroy();
        isExamActive = false;
        handler.removeCallbacks(immersiveRunnable);
        if (webView != null) { webView.destroy(); webView = null; }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (webView != null) webView.saveState(out);
    }

    @Override
    protected void onRestoreInstanceState(Bundle saved) {
        super.onRestoreInstanceState(saved);
        if (webView != null) webView.restoreState(saved);
    }

    private String getDomain(String url) {
        try { java.net.URL u = new java.net.URL(url); return u.getProtocol()+"://"+u.getHost(); }
        catch (Exception e) { return url; }
    }
}
