package com.esmk.cbt;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
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

    // Kode PIN untuk akses Settings (bisa diubah per deployment)
    private static final String ADMIN_PIN = "1234";
    private int backPressCount = 0;
    private long lastBackPress = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── KIOSK: Fullscreen + Keep Screen On ─────────────────
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Sembunyikan navigation bar & status bar (Immersive Sticky)
        hideSystemUI();

        setContentView(R.layout.activity_main);

        webView     = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        tvError     = findViewById(R.id.tv_error);

        // Ambil URL dari settings
        SharedPreferences prefs = getSharedPreferences("esmk_config", MODE_PRIVATE);
        serverUrl = prefs.getString("server_url", "");

        setupWebView();
        loadUrl();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = webView.getSettings();

        // JavaScript & storage
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);

        // Tampilan
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);

        // Cache
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        // User agent — identifikasi sebagai aplikasi E-SMK
        ws.setUserAgentString(ws.getUserAgentString() + " ESMK-CBT/1.0");

        // Blokir navigasi keluar dari domain ujian
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Izinkan hanya URL yang masih dalam domain server ujian
                if (url.startsWith(serverUrl) || url.startsWith(getDomain(serverUrl))) {
                    return false; // biarkan WebView handle
                }
                // URL di luar domain → blokir
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
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                progressBar.setVisibility(View.GONE);
                showError();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Untuk server lokal dengan self-signed cert — tetap lanjut
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
        if (serverUrl.isEmpty()) {
            showError();
            return;
        }
        tvError.setVisibility(View.GONE);
        webView.loadUrl(serverUrl);
    }

    private void showError() {
        tvError.setVisibility(View.VISIBLE);
        tvError.setText("⚠️ Tidak dapat terhubung ke server.\n\nPastikan:\n• HP terhubung ke jaringan sekolah\n• Server ujian aktif\n\nHubungi pengawas atau ketuk 5x untuk coba lagi.");
        tvError.setOnClickListener(null);

        // Ketuk 5x untuk reload
        final int[] tapCount = {0};
        final long[] lastTap = {0};
        tvError.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastTap[0] < 1000) {
                tapCount[0]++;
            } else {
                tapCount[0] = 1;
            }
            lastTap[0] = now;
            if (tapCount[0] >= 5) {
                tapCount[0] = 0;
                loadUrl();
            }
        });
    }

    // ── KIOSK: Blokir semua tombol fisik ───────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // Tombol Back: jika siswa tekan 5x berturut-turut dalam 3 detik
                // → tampilkan dialog PIN untuk pengawas akses Settings
                handleBackPress();
                return true;

            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:  // Recents/Multitasking
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_POWER:
                // Blokir semua tombol sistem
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void handleBackPress() {
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 3000) {
            backPressCount++;
        } else {
            backPressCount = 1;
        }
        lastBackPress = now;

        if (backPressCount >= 5) {
            backPressCount = 0;
            showAdminPinDialog();
        }
    }

    private void showAdminPinDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                           android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Masukkan PIN Pengawas");

        new AlertDialog.Builder(this)
            .setTitle("🔐 Akses Pengawas")
            .setMessage("Masukkan PIN untuk keluar atau mengubah pengaturan.")
            .setView(input)
            .setPositiveButton("Masuk", (dialog, which) -> {
                String pin = input.getText().toString();
                SharedPreferences prefs = getSharedPreferences("esmk_config", MODE_PRIVATE);
                String savedPin = prefs.getString("admin_pin", ADMIN_PIN);
                if (pin.equals(savedPin)) {
                    showAdminMenu();
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("❌ PIN Salah")
                        .setMessage("PIN tidak sesuai.")
                        .setPositiveButton("OK", null)
                        .show();
                }
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void showAdminMenu() {
        String[] menu = {"⚙️ Ubah Pengaturan (URL, Nama, PIN)", "🔄 Reload Halaman", "🚪 Keluar Aplikasi"};
        new AlertDialog.Builder(this)
            .setTitle("Menu Pengawas")
            .setItems(menu, (dialog, which) -> {
                switch (which) {
                    case 0:
                        startActivity(new Intent(this, SettingsActivity.class));
                        break;
                    case 1:
                        webView.reload();
                        break;
                    case 2:
                        finishAffinity();
                        System.exit(0);
                        break;
                }
            })
            .show();
    }

    // ── Immersive Sticky Mode ────────────────────────────────
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().getInsetsController().hide(
                android.view.WindowInsets.Type.statusBars() |
                android.view.WindowInsets.Type.navigationBars()
            );
            getWindow().getInsetsController().setSystemBarsBehavior(
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        } else {
            decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI(); // Pulihkan kiosk mode jika sempat keluar
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    // ── Utility ──────────────────────────────────────────────
    private String getDomain(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (Exception e) {
            return url;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }
}
