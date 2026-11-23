# Google Sign-In (pilihan)

Tanpa langkah ini, app tetap berfungsi penuh dalam **mod tempatan** (VPN tidak bergantung pada akaun).

1. Pergi ke https://console.firebase.google.com → **Add project** → nama `AbgMieVPN`.
2. **Add app ▸ Android** → package name `com.abgmie.vpn`.
3. Masukkan **SHA-1** kunci tandatangan anda (dari keystore release; lihat README / arahan keystore). Tanpa SHA-1 yang betul, Google Sign-In akan gagal.
4. Muat turun `google-services.json`.
5. Firebase ▸ **Authentication ▸ Sign-in method ▸ Google ▸ Enable**.
6. Letak fail:
   - Android Studio: salin ke folder `app/`.
   - GitHub Actions: repo ▸ Settings ▸ Secrets and variables ▸ Actions ▸ **New repository secret** ▸ nama `GOOGLE_SERVICES_JSON`, nilai = seluruh isi fail.
7. Push / bina semula.

Data yang disimpan Firebase: nama akaun Google, email, URL gambar profil dan user ID. Kata laluan Google tidak pernah dilihat atau disimpan. Cloud sync (Firestore) belum diaktifkan — config server & statistik tidak keluar dari peranti.
