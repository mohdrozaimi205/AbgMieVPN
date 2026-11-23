#!/usr/bin/env bash
# AbgMieVPN - WireGuard server installer (Ubuntu 22.04 / 24.04, Debian 12)
# Run on YOUR VPS as root:   sudo bash wireguard-install.sh
# Then create a client:      sudo bash add-client.sh phone
set -euo pipefail

WG_PORT="${WG_PORT:-51820}"
WG_NET="10.8.0"
WG_IF="wg0"

[ "$(id -u)" -eq 0 ] || { echo "Run as root (sudo)."; exit 1; }

apt-get update
apt-get install -y wireguard qrencode iptables curl

PUB_IF=$(ip route show default | awk '/default/ {print $5; exit}')
PUB_IP=$(curl -4 -fsS https://api.ipify.org || hostname -I | awk '{print $1}')

mkdir -p /etc/wireguard/clients
chmod 700 /etc/wireguard
cd /etc/wireguard
umask 077
[ -f server.key ] || wg genkey | tee server.key | wg pubkey > server.pub

cat > /etc/wireguard/${WG_IF}.conf <<CONF
[Interface]
Address = ${WG_NET}.1/24
ListenPort = ${WG_PORT}
PrivateKey = $(cat server.key)
PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -A FORWARD -o %i -j ACCEPT; iptables -t nat -A POSTROUTING -o ${PUB_IF} -j MASQUERADE
PostDown = iptables -D FORWARD -i %i -j ACCEPT; iptables -D FORWARD -o %i -j ACCEPT; iptables -t nat -D POSTROUTING -o ${PUB_IF} -j MASQUERADE
CONF

echo "net.ipv4.ip_forward=1" > /etc/sysctl.d/99-wireguard.conf
echo "net.ipv6.conf.all.forwarding=1" >> /etc/sysctl.d/99-wireguard.conf
sysctl --system >/dev/null

echo "${PUB_IP}" > /etc/wireguard/endpoint
echo "${WG_PORT}" > /etc/wireguard/port

if command -v ufw >/dev/null && ufw status | grep -q active; then ufw allow ${WG_PORT}/udp; fi

systemctl enable --now wg-quick@${WG_IF}
echo
echo "WireGuard server running on ${PUB_IP}:${WG_PORT}/udp"
echo "IMPORTANT: also open UDP ${WG_PORT} in your cloud provider firewall / security list."
echo "Next: sudo bash add-client.sh phone"
