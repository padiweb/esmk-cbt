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

    private static final String DEFAULT_PIN = "1234";
    private int backPressCount = 0;
    private long lastBackPress = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Fullscreen sebelum setContentView ──────────────────
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );

        setContentView(R.layout.activity_main);

        webView     = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        tvError     = findViewById(R.id.tv_error);

        SharedPreferences prefs = getSharedPreferences("esmk_config", MODE_PRIVATE);
        serverUrl = prefs.getString("server_url", "");

        hideSystemUI();

        // ── POLLING IMMERSIVE — paksa setiap 500ms ─────────────
        // Ini yang paling penting: navigation bar yang muncul karena
        // gesture/swipe akan langsung disembunyikan kembali dalam 500ms
        immersiveRunnable = new Runnable() {
            @Override
            public void run() {
                hideSystemUI();
                handler.postDelayed(this, 500);
            }
        };
        handler.postDelayed(immersiveRunnable, 500);

        setupWebView();
        loadUrl();
    }

    // ── IMMERSIVE MODE ──────────────────────────────────────────
    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.hide(WindowInsetsCompat.Type.navigationBars());
                controller.hide(WindowInsetsCompat.Type.statusBars());
                controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            // Android 8-10
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
        // Penanda APK — dibaca oleh ujian.php untuk skip fullscreen gate
        ws.setUserAgentString(ws.getUserAgentString() + " ESMK-CBT/1.0");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(serverUrl) || url.startsWith(getDomain(serverUrl))) {
                    return false;
                }
                return true; // Blokir URL luar domain
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                tvError.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                hideSystemUI(); // Pulihkan immersive setelah halaman load
            }

            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                progressBar.setVisibility(View.GONE);
                showError();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
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
        tvError.setText("⚠️ Tidak dapat terhubung ke server.\n\nPastikan:\n• HP terhubung ke jaringan sekolah\n• Server ujian aktif\n\nKetuk 5x untuk coba lagi.");
        final int[] tapCount = {0};
        final long[] lastTap = {0};
        tvError.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            tapCount[0] = (now - lastTap[0] < 1000) ? tapCount[0] + 1 : 1;
            lastTap[0] = now;
            if (tapCount[0] >= 5) { tapCount[0] = 0; loadUrl(); }
        });
    }

    // ── BLOKIR SEMUA TOMBOL FISIK & SISTEM ─────────────────────
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
                return true; // Blokir semua
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
                String savedPin = prefs.getString("admin_pin", DEFAULT_PIN);
                if (pin.equals(savedPin)) {
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
                        startActivity(new Intent(this, SettingsActivity.class)); break;
                    case 1:
                        webView.reload(); break;
                    case 2:
                        handler.removeCallbacks(immersiveRunnable);
                        finishAffinity();
                        System.exit(0); break;
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(immersiveRunnable);
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
