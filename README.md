# Downloader

App Android (Kotlin + Jetpack Compose, UI liquid glass) untuk mengunduh video, foto, dan audio dari link, memakai library [scrapr](https://github.com/coflyn/scrapr).

## Cara kerja

- `server/` berisi server Node.js kecil yang membungkus scrapr. Server jalan di **Termux** di HP pengguna (`127.0.0.1:8787`).
- App menyalakan server lewat fitur `RUN_COMMAND` Termux saat dibuka, dan server mati sendiri saat app ditutup/dihapus dari recent (detak tiap 5 detik, batas 20 detik).
- Setup sekali: app menampilkan perintah untuk ditempel di Termux:

```
curl -fsSL https://raw.githubusercontent.com/vyz-handsome/downloader/main/server/setup.sh | bash
```

> Repo harus **public** supaya perintah di atas bisa mengunduh `server.js`.

## Build

APK debug dibuild otomatis lewat GitHub Actions (`.github/workflows/build.yml`), hasilnya ada di Artifacts.

Minimal Android 12.
