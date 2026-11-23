#!/usr/bin/env bash
# Create a WireGuard client config for AbgMieVPN.   sudo bash add-client.sh <name> [dns]
# Output: /etc/wireguard/clients/<name>.conf  (import it in AbgMieVPN -> Locations -> +)
set -euo pipefail
NAME="${1:?usage: add-client.sh <name> [dns]}"
DNS="${2:-1.1.1.1, 1.0.0.1}"
WG_IF="wg0"
cd /etc/wireguard
umask 077
ENDPOINT=$(cat endpoint); PORT=$(cat port)
USED=$(grep -ho 'AllowedIPs = 10.8.0.[0-9]*' ${WG_IF}.conf | awk -F. '{print $4}' | sort -n | tail -1)
NEXT=$(( ${USED:-1} + 1 ))
[ "$NEXT" -lt 255 ] || { echo "No free addresses"; exit 1; }
CKEY=$(wg genkey); CPUB=$(echo "$CKEY" | wg pubkey); PSK=$(wg genpsk)

cat >> ${WG_IF}.conf <<CONF

# client: ${NAME}
[Peer]
PublicKey = ${CPUB}
PresharedKey = ${PSK}
AllowedIPs = 10.8.0.${NEXT}/32
CONF

cat > clients/${NAME}.conf <<CONF
[Interface]
PrivateKey = ${CKEY}
Address = 10.8.0.${NEXT}/32
DNS = ${DNS}

[Peer]
PublicKey = $(cat server.pub)
PresharedKey = ${PSK}
Endpoint = ${ENDPOINT}:${PORT}
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
CONF

wg syncconf ${WG_IF} <(wg-quick strip ${WG_IF})
echo "Client config: /etc/wireguard/clients/${NAME}.conf"
echo "Show it with:  sudo cat /etc/wireguard/clients/${NAME}.conf"
qrencode -t ansiutf8 < clients/${NAME}.conf || true
echo "Keep this file private - it contains the client private key."
