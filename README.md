# Wall-E Notch (Infinix GT 10 Pro Edition)

Aplikasi **Dynamic Island** berbasis **Kotlin + Jetpack Compose** dengan identitas visual **Wall-E & EVE**, dirancang khusus untuk layar 6.67" AMOLED 120Hz dan posisi kamera depan (*center punch-hole*) **Infinix GT 10 Pro**.

---

## Fitur Utama

1. **EVE Standby (Smart Idle Pill)**
   - Menampilkan sepasang mata biru LED EVE (`#00E5FF`) yang berkedip halus di sisi kiri kamera dan indikator bar surya Wall-E (`#F5B041`) di sisi kanan.
   - Otomatis menyusut ke ukuran lubang kamera saat layar *Landscape Fullscreen* (menonton video/bermain game).
2. **Kontrol Musik & Visualizer (`MediaSessionManager`)**
   - Terhubung otomatis ke Spotify, YouTube Music, dan pemutar musik lainnya.
   - Menampilkan visualizer 3-bar, durasi lagu, serta tombol *Previous*, *Play/Pause*, dan *Next*.
3. **Wall-E Solar Charge Meter (45W & Bypass Charging)**
   - Muncul otomatis saat *charger* dicolokkan, menampilkan 8 bar indikator panel surya khas dada Wall-E, persentase baterai, suhu (`°C`), dan voltase (`mV`).
4. **Notifikasi Masuk & Balas Cepat (`RemoteInput` WhatsApp/Telegram)**
   - Menampilkan pesan masuk di kiri-kanan kamera.
   - Sentuh kapsul sekali (*Single Tap*) untuk membuka kolom **Balas Cepat (*Quick Reply*)** atau memilih chip balasan instan langsung dari *notch*.
5. **Panggilan Masuk & Split Pill Timer**
   - Mendukung **Split Pill** (kapsul utama + gelembung *timer* terpisah di kanan).
6. **Infinix GT 10 Pro Gaming HUD**
   - Memantau *Refresh Rate* (`120 FPS/Hz`), suhu CPU/Baterai (`°C`), dan penggunaan RAM secara *real-time*, dilengkapi pengunci *Blokir Pop-up Notifikasi* saat bermain game.

---

## Cara Build APK Tanpa Install Android Studio (GitHub Actions)

Proyek ini sudah dilengkapi workflow otomatis di [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml). Kamu tidak perlu menginstal Android Studio atau Java SDK di PC:

1. Buat repositori baru di GitHub (boleh *Public* atau *Private*).
2. Jalankan perintah berikut di terminal folder proyek ini (`d:\2. Project\Wall-E Notch`):
   ```powershell
   git init
   git add .
   git commit -m "Initial commit: Wall-E Notch for Infinix GT 10 Pro"
   git branch -M main
   git remote add origin https://github.com/USERNAME_KAMU/NAMA_REPO_KAMU.git
   git push -u origin main
   ```
3. Buka tab **Actions** di repositori GitHub kamu.
4. Tunggu sekitar **2–3 menit** hingga proses **Build Wall-E Notch APK (Infinix GT 10 Pro)** selesai (centang hijau).
5. Klik hasil *build* tersebut, gulir ke bagian **Artifacts**, lalu unduh **`Wall-E-Notch-GT10Pro-Debug-APK`** dan pasang file `.apk`-nya di Infinix GT 10 Pro kamu!

---

## Panduan Pengaturan di Infinix GT 10 Pro (XOS)

1. Buka aplikasi **Wall-E Notch**.
2. Pada bagian **1. Aktifkan Layanan Sistem**:
   - Tekan **Buka Aksesibilitas**, lalu aktifkan **Wall-E Notch Overlay (Infinix GT 10 Pro)**. *(Jika di Android 13/14 muncul "Pengaturan Terbatas / Restricted Setting", buka Pengaturan HP -> Manajemen Aplikasi -> Wall-E Notch -> klik titik tiga di kanan atas -> "Izinkan pengaturan terbatas")*.
   - Tekan **Buka Izin Notif**, lalu aktifkan **Wall-E Notch**.
3. Pada bagian **3. Kalibrasi Kamera Infinix GT 10 Pro**:
   - Aktifkan sakelar **Tampilkan Garis Bantu Kamera (X-Ray)** untuk mengecek kesejajaran dengan lubang kamera depan, lalu sesuaikan `Offset Y` dan `Lebar Kapsul Saat Idle` bila diperlukan.
4. **Agar Tidak Ditutup Sistem XOS Saat Main Game**:
   - Kunci aplikasi Wall-E Notch di *Recent Apps*, dan matikan pembatasan baterai (*Unrestricted Battery*) di Pengaturan Aplikasi XOS.
