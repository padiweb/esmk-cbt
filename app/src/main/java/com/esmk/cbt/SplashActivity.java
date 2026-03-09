package com.esmk.cbt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen sejak splash
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_splash);

        // Tampilkan nama sekolah dari settings
        SharedPreferences prefs = getSharedPreferences("esmk_config", MODE_PRIVATE);
        String namaSekolah = prefs.getString("nama_sekolah", "E-SMK");
        String serverUrl   = prefs.getString("server_url", "");

        TextView tvNama = findViewById(R.id.tv_nama_sekolah);
        tvNama.setText(namaSekolah);

        // Cek apakah URL sudah dikonfigurasi
        if (serverUrl.isEmpty()) {
            // Belum dikonfigurasi → langsung ke Settings
            new Handler().postDelayed(() -> {
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
            }, 1500);
        } else {
            // Sudah ada URL → masuk ke ujian setelah 2 detik
            new Handler().postDelayed(() -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            }, 2000);
        }
    }

    @Override
    public void onBackPressed() {
        // Blokir Back di splash
    }
}
