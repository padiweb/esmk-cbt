package com.esmk.cbt;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final String TARGET_URL = "https://ujian.padiweb.my.id/ujian/login";
    private static final String ALLOWED_DOMAIN = "ujian.padiweb.my.id";
    private static final String PIN_ADMIN = "1234";

    private int backCount = 0;
    private long lastBackTime = 0;
    private static final int BACK_THRESHOLD = 5;
    private static final long BACK_WINDOW_MS = 3000;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Android 14+ — WindowInsetsController untuk full screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        webView = new WebView(this);
        setContentView(webView);

        // Full screen Android 14+ cara baru
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }

        // Setup WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " ESMK-CBT/1.0");
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return !url.contains(ALLOWED_DOMAIN);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                    "document.addEventListener('contextmenu',function(e){e.preventDefault();});",
                    null
                );
            }
        });

        webView.loadUrl(TARGET_URL);

        // Android 14+ — OnBackInvokedCallback (menggantikan onBackPressed yang deprecated)
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            () -> handleBack()
        );

        // Polling immersive setiap 500ms
        new android.os.Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
                new android.os.Handler(getMainLooper()).postDelayed(this, 500);
            }
        }, 500);
    }

    private void handleBack() {
        long now = System.currentTimeMillis();
        if (now - lastBackTime > BACK_WINDOW_MS) {
            backCount = 0;
        }
        lastBackTime = now;
        backCount++;

        if (backCount >= BACK_THRESHOLD) {
            backCount = 0;
            showAdminPin();
        }
    }

    private void showAdminPin() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(
            android.text.InputType.TYPE_CLASS_NUMBER |
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );
        input.setHint("PIN Admin");

        new AlertDialog.Builder(this)
            .setTitle("Akses Admin")
            .setMessage("Masukkan PIN untuk keluar dari mode ujian:")
            .setView(input)
            .setPositiveButton("OK", (dialog, which) -> {
                if (PIN_ADMIN.equals(input.getText().toString())) {
                    finish();
                } else {
                    Toast.makeText(this, "PIN salah!", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_APP_SWITCH:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_ASSIST:
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.evaluateJavascript(
                "if(typeof window.onAppPause==='function') window.onAppPause();",
                null
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }
    }
}
