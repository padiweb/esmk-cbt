package com.esmk.cbt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText etUrl, etNamaSekolah, etPin, etPinBaru;
    private static final String DEFAULT_PIN = "1234";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_settings);

        etUrl         = findViewById(R.id.et_url);
        etNamaSekolah = findViewById(R.id.et_nama_sekolah);
        etPin         = findViewById(R.id.et_pin_lama);
        etPinBaru     = findViewById(R.id.et_pin_baru);

        // Load nilai yang tersimpan
        SharedPreferences prefs = getSharedPreferences("esmk_config", MODE_PRIVATE);
        etUrl.setText(prefs.getString("server_url", ""));
        etNamaSekolah.setText(prefs.getString("nama_sekolah", "E-SMK"));

        Button btnSimpan = findViewById(R.id.btn_simpan);
        btnSimpan.setOnClickListener(v -> simpanSettings());

        Button btnBatal = findViewById(R.id.btn_batal);
        btnBatal.setOnClickListener(v -> {
            String url = prefs.getString("server_url", "");
            if (!url.isEmpty()) {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
        });
    }

    private void simpanSettings() {
        String url         = etUrl.getText().toString().trim();
        String namaSekolah = etNamaSekolah.getText().toString().trim();
        String pinLama     = etPin.getText().toString().trim();
        String pinBaru     = etPinBaru.getText().toString().trim();

        if (url.isEmpty()) {
            etUrl.setError("URL tidak boleh kosong");
            etUrl.requestFocus();
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            etUrl.setError("URL harus diawali https:// atau http://");
            etUrl.requestFocus();
            return;
        }
        if (namaSekolah.isEmpty()) {
            etNamaSekolah.setError("Nama sekolah tidak boleh kosong");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("esmk_config", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Cek / ubah PIN
        if (!pinBaru.isEmpty()) {
            String savedPin = prefs.getString("admin_pin", DEFAULT_PIN);
            if (!pinLama.equals(savedPin)) {
                etPin.setError("PIN lama salah");
                return;
            }
            if (pinBaru.length() < 4) {
                etPinBaru.setError("PIN minimal 4 digit");
                return;
            }
            editor.putString("admin_pin", pinBaru);
            Toast.makeText(this, "PIN berhasil diubah", Toast.LENGTH_SHORT).show();
        }

        editor.putString("server_url", url);
        editor.putString("nama_sekolah", namaSekolah);
        editor.apply();

        Toast.makeText(this, "✅ Pengaturan tersimpan", Toast.LENGTH_SHORT).show();

        // Langsung masuk ke aplikasi
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Blokir Back — harus lewat tombol Batal
        SharedPreferences prefs = getSharedPreferences("esmk_config", MODE_PRIVATE);
        if (!prefs.getString("server_url", "").isEmpty()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }
}
