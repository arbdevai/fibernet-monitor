# FiberNet Monitor

Aplikasi Android berbasis Jetpack Compose dan Material 3 untuk memantau serta mengelola router dan jaringan FiberNet secara real-time.

## Fitur Utama

- **Dashboard Real-Time:** Menampilkan status koneksi, informasi WAN/PON, kekuatan sinyal optik (Rx/Tx power), dan statistik lalu lintas jaringan.
- **Daftar Perangkat Terhubung:** Pemantauan perangkat WiFi dan LAN aktif beserta detail IP, MAC address, dan tipe koneksi.
- **Kontrol WiFi:** Pengaturan SSID (2.4 GHz & 5 GHz), manajemen kata sandi WiFi, dan visibilitas jaringan.
- **Manajemen Keamanan:** Pemblokiran MAC address (Blacklist/Whitelist), kontrol akses perangkat, dan pemantauan ancaman keamanan.
- **Network Tools:** Diagnostik jaringan termasuk Ping test, Traceroute, dan Speed test lokal.
- **Konfigurasi & Pengaturan:** Pengaturan kredensial gateway/router, mode tema (Terang/Gelap/Sistem), dan opsi sinkronisasi latar belakang.

## Arsitektur & Teknologi

- **Bahasa:** Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Arsitektur:** Clean Architecture dengan MVVM (`StateFlow`)
- **Networking:** Retrofit + OkHttp dengan `HttpLoggingInterceptor`
- **Serialisasi:** Kotlinx Serialization
- **Penyimpanan Lokal:** Jetpack DataStore Preferences
- **Background Work:** AndroidX WorkManager
- **CI/CD:** GitHub Actions (Automated Signed APK Release)

## Alur Rilis CI/CD

Proses build dan rilis APK dilakukan secara otomatis melalui GitHub Actions:
Setiap commit ke branch `main` dengan pesan berawalan `Release v...` akan memicu workflow CI untuk mengompilasi APK bertanda tangan (signed release APK) dan mempublikasikannya langsung ke GitHub Releases.
