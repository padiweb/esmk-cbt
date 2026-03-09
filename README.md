# E-SMK — Aplikasi CBT Android (Kiosk Mode)

Aplikasi Android untuk Ujian Berbasis Komputer (CBT) dengan **Kiosk Mode penuh** — tombol Back, Home, dan Recents diblokir selama ujian berlangsung.

---

## Fitur Utama

- **Kiosk Mode** — tombol Back, Home, Recents tidak berfungsi saat ujian
- **Immersive Fullscreen** — navigation bar & status bar tersembunyi
- **Konfigurasi per sekolah** — nama sekolah, URL server, PIN pengawas bisa diubah
- **PIN Pengawas** — untuk keluar atau ubah pengaturan (default: `1234`)
- **Blokir URL luar** — siswa tidak bisa navigasi ke situs lain
- **WebView** — membungkus web ujian yang sudah ada, tidak perlu ubah server

---

## Cara Compile APK (Tanpa Android Studio)

### Langkah 1 — Buat Akun GitHub (gratis)
Buka https://github.com dan daftar akun baru jika belum punya.

### Langkah 2 — Upload Project ke GitHub

1. Di GitHub, klik tombol **"+"** (pojok kanan atas) → **"New repository"**
2. Isi nama repo: `esmk-cbt`
3. Pilih **Public** (agar Actions gratis)
4. Klik **"Create repository"**
5. Di halaman repo yang baru dibuat, klik **"uploading an existing file"**
6. **Drag & drop seluruh folder project ini** (atau upload satu per satu)
7. Klik **"Commit changes"**

### Langkah 3 — Tunggu Build Otomatis

Setelah upload selesai:
1. Klik tab **"Actions"** di repository
2. Lihat workflow **"Build E-SMK APK"** berjalan (🟡 kuning = proses, ✅ hijau = selesai)
3. Proses sekitar **3-5 menit**

### Langkah 4 — Download APK

1. Setelah build ✅ hijau, klik nama workflow tersebut
2. Scroll ke bawah ke bagian **"Artifacts"**
3. Klik **"E-SMK-APK"** untuk download file ZIP
4. Extract ZIP → dapat file **`app-debug.apk`**

---

## Cara Install APK di HP Siswa

### Persiapan HP (sekali saja)
1. Buka **Pengaturan** → **Keamanan** (atau **Privasi**)
2. Aktifkan **"Sumber tidak dikenal"** / **"Install aplikasi tidak dikenal"**
3. Di HP tertentu: Pengaturan → **Aplikasi** → **Chrome/File Manager** → aktifkan "Pasang aplikasi tidak dikenal"

### Install APK
1. Kirim file `app-debug.apk` ke HP siswa via WhatsApp / USB / Google Drive
2. Buka file APK di HP → ketuk **"Install"**
3. Tunggu instalasi selesai → ketuk **"Buka"**

### Konfigurasi Pertama (wajib sebelum ujian)
1. Saat pertama buka, aplikasi langsung masuk **halaman Pengaturan**
2. Isi **URL Server**: `https://ujian.namasekolah.sch.id` (sesuai server ujian)
3. Isi **Nama Sekolah**: nama yang akan tampil di splash screen
4. **Ubah PIN** dari default `1234` ke PIN yang hanya diketahui pengawas
5. Ketuk **"Simpan & Masuk"**

---

## Cara Mengubah Nama Aplikasi per Sekolah

Edit file `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">NAMA SEKOLAH CBT</string>
```
Kemudian upload ulang ke GitHub → build ulang → APK baru dengan nama berbeda.

---

## Cara Akses Menu Pengawas (saat aplikasi berjalan)

Tekan tombol **Back 5 kali berturut-turut** dalam 3 detik → muncul dialog PIN.

Masukkan PIN pengawas → pilih menu:
- **Ubah Pengaturan** — ubah URL, nama sekolah, atau PIN
- **Reload Halaman** — muat ulang halaman ujian
- **Keluar Aplikasi** — tutup aplikasi E-SMK

---

## Struktur Project

```
esmk-cbt/
├── .github/workflows/build.yml     ← GitHub Actions (compile otomatis)
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/esmk/cbt/
│       │   ├── SplashActivity.java  ← Layar pembuka
│       │   ├── MainActivity.java    ← Kiosk WebView utama
│       │   └── SettingsActivity.java← Pengaturan admin
│       └── res/
│           ├── layout/             ← Tampilan XML
│           ├── values/             ← Warna, string, tema
│           └── drawable/           ← Ikon & shape
├── build.gradle
├── settings.gradle
└── gradlew
```

---

## Pertanyaan Umum

**Q: Siswa bisa keluar dari aplikasi tidak?**
A: Tidak bisa lewat tombol Back. Tombol Home masih bisa (keterbatasan OS Android), tapi saat kembali ke aplikasi, kiosk mode aktif kembali. Di web ujian sudah ada deteksi `visibilitychange` yang mencatat pelanggaran.

**Q: Bagaimana jika server ujian menggunakan HTTP (bukan HTTPS)?**
A: Sudah diizinkan di `AndroidManifest.xml` (`android:usesCleartextTraffic="true"`). Bisa digunakan untuk server lokal (LAN sekolah).

**Q: Bisa ganti logo/ikon aplikasi?**
A: Bisa. Ganti file di `app/src/main/res/drawable/ic_launcher_fg.xml` dengan SVG icon baru, lalu build ulang.

**Q: Apakah perlu bayar untuk menggunakan GitHub Actions?**
A: Tidak, untuk repository Public gratis tanpa batas. Repository Private mendapat 2000 menit/bulan gratis.
