#!/usr/bin/env bash
#
# HausVPN gateway installer.
# Turns a fresh Ubuntu/Debian VPS into a working HausVPN server:
#   - installs WireGuard + Node.js
#   - generates the server keypair and wg0 interface (with NAT)
#   - installs the control-plane API as a systemd service
#
# Run as root on the VPS:
#   sudo bash install-gateway.sh
#
set -euo pipefail

WG_IFACE="wg0"
WG_PORT="51820"
WG_SUBNET_PREFIX="10.7.0"
WG_SERVER_ADDR="${WG_SUBNET_PREFIX}.1/24"
APP_DIR="/opt/hausvpn/backend"

echo "==> HausVPN gateway install starting"

if [[ $EUID -ne 0 ]]; then
  echo "Please run as root (sudo bash install-gateway.sh)"; exit 1
fi

# --- 1. Packages ----------------------------------------------------------
echo "==> Installing packages (wireguard, node, build tools)"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y wireguard wireguard-tools iptables curl ca-certificates build-essential

if ! command -v node >/dev/null 2>&1; then
  echo "==> Installing Node.js 20 LTS"
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y nodejs
fi

# --- 2. IP forwarding -----------------------------------------------------
echo "==> Enabling IP forwarding"
sed -i '/net.ipv4.ip_forward/d' /etc/sysctl.conf
echo "net.ipv4.ip_forward=1" >> /etc/sysctl.conf
sysctl -p >/dev/null

# --- 3. WireGuard server interface ----------------------------------------
WAN_IFACE="$(ip route show default | awk '/default/ {print $5; exit}')"
echo "==> Detected WAN interface: ${WAN_IFACE}"

if [[ ! -f /etc/wireguard/${WG_IFACE}.conf ]]; then
  echo "==> Generating server keypair and ${WG_IFACE}.conf"
  umask 077
  SERVER_PRIV="$(wg genkey)"
  SERVER_PUB="$(echo "${SERVER_PRIV}" | wg pubkey)"
  cat > /etc/wireguard/${WG_IFACE}.conf <<EOF
[Interface]
Address = ${WG_SERVER_ADDR}
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_PRIV}
PostUp = iptables -A FORWARD -i ${WG_IFACE} -j ACCEPT; iptables -A FORWARD -o ${WG_IFACE} -j ACCEPT; iptables -t nat -A POSTROUTING -o ${WAN_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i ${WG_IFACE} -j ACCEPT; iptables -D FORWARD -o ${WG_IFACE} -j ACCEPT; iptables -t nat -D POSTROUTING -o ${WAN_IFACE} -j MASQUERADE
EOF
else
  echo "==> ${WG_IFACE}.conf already exists, keeping it"
  SERVER_PUB="$(wg show ${WG_IFACE} public-key 2>/dev/null || grep -m1 PrivateKey /etc/wireguard/${WG_IFACE}.conf | awk '{print $3}' | wg pubkey)"
fi

echo "==> Bringing up ${WG_IFACE} and enabling on boot"
systemctl enable wg-quick@${WG_IFACE} >/dev/null 2>&1 || true
systemctl restart wg-quick@${WG_IFACE}

# --- 4. Backend service ---------------------------------------------------
echo "==> Installing backend to ${APP_DIR}"
mkdir -p "${APP_DIR}"
# Copy repo backend/ (this script lives in backend/scripts/).
SRC_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cp -r "${SRC_DIR}/." "${APP_DIR}/"
cd "${APP_DIR}"
npm install --omit=dev

PUBLIC_IP="$(curl -fsS https://api.ipify.org || echo 'YOUR_PUBLIC_IP')"
ADMIN_TOKEN="$(openssl rand -hex 24)"

if [[ ! -f "${APP_DIR}/.env" ]]; then
  echo "==> Writing .env"
  cat > "${APP_DIR}/.env" <<EOF
PORT=8080
HOST=0.0.0.0
DB_PATH=/opt/hausvpn/backend/data/hausvpn.db
WG_APPLY=1
WG_INTERFACE=${WG_IFACE}
SUBNET_PREFIX=${WG_SUBNET_PREFIX}
SERVER_ENDPOINT=${PUBLIC_IP}:${WG_PORT}
SERVER_PUBLIC_KEY=${SERVER_PUB}
CLIENT_DNS=1.1.1.1, 1.0.0.1
TRIAL_DAYS=7
ADMIN_TOKEN=${ADMIN_TOKEN}
EOF
fi

echo "==> Installing systemd service"
cp "${APP_DIR}/scripts/hausvpn.service" /etc/systemd/system/hausvpn.service
systemctl daemon-reload
systemctl enable hausvpn >/dev/null 2>&1 || true
systemctl restart hausvpn

# --- 5. Firewall hint -----------------------------------------------------
echo
echo "============================================================"
echo " HausVPN gateway is up."
echo "   WireGuard:      UDP ${WG_PORT}  (open this in your cloud firewall)"
echo "   API:            TCP 8080        (open this in your cloud firewall)"
echo "   Server pubkey:  ${SERVER_PUB}"
echo "   Public IP:      ${PUBLIC_IP}"
echo "   Admin token:    ${ADMIN_TOKEN}"
echo
echo " Test it:"
echo "   curl http://${PUBLIC_IP}:8080/v1/health"
echo
echo " Point the app's API base at:  http://${PUBLIC_IP}:8080"
echo " (Put a domain + HTTPS in front before public launch.)"
echo "============================================================"
