# Troubleshooting

| Masalah | Punca & penyelesaian |
|---|---|
| "No handshake from …" | Server tidak jalan (`sudo wg show`), port UDP 51820 tertutup di firewall provider, atau config peranti belum ditambah (`add-client.sh`). |
| Connected tapi tiada internet | `net.ipv4.ip_forward` tidak aktif atau antara muka rangkaian salah dalam PostUp. Jalankan semula `wireguard-install.sh`. |
| Laman tidak buka, IP ok | Masalah DNS — cuba Settings ▸ DNS ▸ Custom ▸ Cloudflare. |
| Ping "n/a" | Rangkaian/peranti sekat ICMP. Sambungan masih boleh berfungsi. |
| Kill switch tiada pilihan "Block connections" | ROM/TV box tidak sokong. Gunakan Reconnect automatically. |
| Kemas kini gagal pasang | APK lama ditandatangan kunci berbeza. Pasang 4 GitHub Secrets keystore, nyahpasang sekali, kemudian kemas kini seterusnya akan lancar. |
| Google Sign-In butang kelabu | `google-services.json` belum ditambah (docs/FIREBASE_SETUP.md). |
| Google Sign-In gagal "developer error" | SHA-1 dalam Firebase tidak sama dengan kunci tandatangan APK. |
| Build GitHub Actions gagal | Buka tab Actions ▸ run merah ▸ baca baris `What went wrong`. |
| VPN putus bila app lain VPN dibuka | Android hanya benarkan satu VPN pada satu masa. |
