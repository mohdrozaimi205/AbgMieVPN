# AbgMieVPN

**One World. One Connection.**

VPN peribadi WireGuard untuk Android — telefon, tablet, Android TV, Google TV dan TV box (cth. Xiaomi Mi Box S). Satu projek Android Studio, satu APK, tiga pengalaman UI (telefon / tablet / TV) yang berkongsi enjin VPN yang sama.

Tiada langganan, tiada iklan, tiada kuota, tiada had laju dari app. Kelajuan & data sebenar bergantung pada server anda sendiri (kapasiti VPS, polisi hosting, kualiti rangkaian).

## Status ciri

| Ciri | Status |
|---|---|
| Sambungan VPN WireGuard sebenar (GoBackend + VpnService) | ✅ |
| "Connected" hanya selepas handshake WireGuard disahkan | ✅ |
| Auto reconnect, pemantauan kesihatan tunnel, tukar Wi-Fi ↔ data | ✅ |
| Kill switch (Always-on VPN + Block connections Android) | ✅ (lihat had di bawah) |
| DNS automatik / custom (IPv4 + IPv6), dalam tunnel | ✅ |
| Pangkalan data 249 negara ikut benua (+ Antartika maklumat sahaja) | ✅ |
| Registry server (import .conf / tampal / pautan HTTPS), tanpa rebuild | ✅ |
| Ping sebenar, Auto Connect server terbaik, kegemaran, terkini | ✅ |
| Statistik sebenar (laju, jumlah, harian/mingguan/bulanan, sejarah) | ✅ |
| Google Sign-In (Firebase + Credential Manager), mod tempatan | ✅ (perlu `google-services.json`) |
| Tema Light / Dark / System dengan peralihan lancar | ✅ |
| Bottom nav kapsul biru menggelongsor (telefon) | ✅ |
| UI TV khas: sidebar kiri, D-pad, Quick Connect, banner Leanback | ✅ |
| Kemas kini dari GitHub Releases + semakan SHA-256 | ✅ |
| GitHub Actions bina APK → Releases | ✅ |
| VPN Gate public relays (OpenVPN) via app pembantu "OpenVPN for Android" | ✅ |
| Firestore cloud sync | ⏳ belum (pilihan) — tiada data keluar dari peranti |
| Beban (load %) server | Hanya dipaparkan jika ada sumber sebenar; jika tidak "unknown" |

## Dua jenis server

1. **VPN Gate (percuma, awam)** – senarai sebenar dari vpngate.net, terus dalam tab Locations. Perlu pasang sekali app percuma **OpenVPN for Android** (Play Store); AbgMieVPN mengawalnya melalui API rasmi. Server sukarelawan – boleh log sambungan anda; jangan guna untuk perbankan.
2. **Server WireGuard sendiri (paling peribadi & laju)** – ikut [docs/SERVER_SETUP.md](docs/SERVER_SETUP.md).

Tiada IP, kunci atau server palsu. Negara tanpa server tunjuk **Coming Soon**.

## Bina APK

- **Automatik:** setiap push ke `main` → GitHub Actions → tab **Releases** → muat turun `AbgMieVPN-vX.apk`.
- **Android Studio:** buka folder projek → tunggu Gradle sync → **Build ▸ Build APK(s)**.

Tandatangan tetap (supaya kemas kini dalam app boleh pasang atas versi lama) — tambah 4 GitHub Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Struktur projek

```
app/src/main/java/com/abgmie/vpn/
  core/        tema, warna jenama, format, CryptoBox (Android Keystore), jenis peranti
  data/        Room (servers, sessions), DataStore settings, countries (249 negara)
  domain/      model: VpnStatus, VpnState, AppSettings
  vpn/         VpnManager – enjin WireGuard (connect/disconnect/reconnect/health/kill switch)
  servers/     ServerRepository – import, validasi, DNS, ping, Auto Connect
  auth/        AuthRepository – Firebase + Credential Manager
  statistics/  StatsRepository
  updates/     UpdateRepository – GitHub Releases + SHA-256 + installer rasmi
  ui/          ViewModel & komponen kongsi (stats, settings, DNS, updates, import)
  mobile/      UI telefon & tablet (bottom nav menggelongsor)
  tv/          UI Android TV (sidebar, D-pad, Quick Connect)
docs/          SERVER_SETUP, FIREBASE_SETUP, TROUBLESHOOTING
scripts/       wireguard-install.sh, add-client.sh, gen_countries.py
.github/workflows/build-apk.yml
```
Satu modul Gradle (`app`) dengan pakej mengikut lapisan — lebih mudah dibina oleh pemula, dan senang dipecah jadi modul Gradle kemudian.

## Had Android yang perlu tahu

- **Kill switch:** Android tidak benarkan app sekat internet sendiri. Kill switch sebenar = *Settings ▸ Network ▸ VPN ▸ ⚙ AbgMieVPN ▸ Always-on VPN + Block connections without VPN*. Sesetengah TV box / ROM sembunyikan pilihan ini.
- **Ping:** diukur guna ICMP. Sesetengah rangkaian/peranti sekat ping → dipaparkan "n/a" (bukan nombor rekaan).
- **Google Sign-In pada TV:** sesetengah TV box tiada Credential Manager/Google Play Services penuh — guna mod tempatan.
- **Kemas kini:** Android sentiasa minta pengesahan pengguna; perlu benarkan "Install unknown apps" untuk AbgMieVPN.
- VPN tidak menjadikan anda 100% anonim.

## Privasi

Tiada log pelayaran, tiada log DNS, tiada SDK iklan/analitik. Config server disulitkan (AES-256-GCM, Android Keystore). Lihat Settings ▸ Privacy dalam app.

Lesen: MIT
