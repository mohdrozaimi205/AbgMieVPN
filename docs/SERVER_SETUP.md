# Sediakan server WireGuard anda sendiri

App AbgMieVPN perlukan sekurang-kurangnya **satu server WireGuard milik anda**. Satu server = satu lokasi (negara/bandar) dalam app.

## 1. Dapatkan VPS (pilih satu)

| Pilihan | Kos | Nota |
|---|---|---|
| Oracle Cloud Always Free (Singapore, Tokyo, dll.) | Percuma | Perlu kad untuk daftar. Buka port UDP dalam *Security List*. |
| Google Cloud e2-micro (US sahaja) | Percuma (had trafik keluar) | Trafik internet keluar berbayar selepas had percuma. |
| DigitalOcean / Vultr / Linode / Hetzner | ~RM20–30/bulan | Paling mudah, banyak negara. |
| Router/PC di rumah | Percuma | Perlu IP awam & port forward UDP 51820. |

Pilih **Ubuntu 22.04 atau 24.04**. Semak terma provider — sesetengah melarang penggunaan tertentu.

## 2. Pasang WireGuard (sekali sahaja)

Log masuk ke VPS (butang **SSH** dalam panel provider), kemudian taip:

```bash
curl -fsSLO https://raw.githubusercontent.com/mohdrozaimi205/AbgMieVPN/main/scripts/wireguard-install.sh
curl -fsSLO https://raw.githubusercontent.com/mohdrozaimi205/AbgMieVPN/main/scripts/add-client.sh
sudo bash wireguard-install.sh
```

**Buka port UDP 51820** dalam firewall provider (Oracle: *Networking ▸ VCN ▸ Security List ▸ Add Ingress Rule ▸ UDP 51820*).

## 3. Buat config untuk setiap peranti

Satu config untuk setiap peranti (telefon, tablet, TV):

```bash
sudo bash add-client.sh honor200
sudo bash add-client.sh mibox
sudo cat /etc/wireguard/clients/honor200.conf
```

## 4. Masukkan ke dalam app

- **Telefon/tablet:** salin teks config → AbgMieVPN ▸ **Locations ▸ ⊕** ▸ tampal → pilih negara & bandar server → **Save server**.
- **TV (Mi Box S):** paling mudah guna **HTTPS link** — muat naik fail `.conf` ke tempat peribadi anda (cth. Google Drive "anyone with link" *sementara*), tampal pautan, simpan, kemudian padam fail dari Drive. Atau pindahkan fail ke TV dan guna **Choose .conf file**.

Config mengandungi kunci peribadi — jangan kongsi, jangan commit ke GitHub.

## 5. Tambah lokasi baru

Ulang langkah 1–4 pada VPS di negara lain. App terus tunjuk negara itu sebagai **Connectable** — tiada rebuild APK.

## DNS sendiri (pilihan, privasi terbaik)

Pasang resolver di server (cth. `unbound`) dan dalam app ▸ Settings ▸ DNS ▸ Custom ▸ `10.8.0.1`.

## Keselamatan

- Kunci admin/SSH dan kunci peribadi server **tidak pernah** dimasukkan ke dalam APK.
- Kemas kini server: `sudo apt update && sudo apt upgrade`.
- Buang peranti hilang: padam blok `[Peer]` peranti itu dari `/etc/wireguard/wg0.conf`, kemudian `sudo systemctl restart wg-quick@wg0`.
