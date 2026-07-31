# HausVPN — Backend (control plane + gateway)

This is the real control plane for HausVPN: it manages accounts, checks
subscription/trial status, and — the key part — **hands out working WireGuard
configs automatically** so the app never asks anyone to paste anything.

For a single-server launch it runs **on the New York gateway itself** and
programs peers directly into the live WireGuard interface. That collapses
"control plane" and "gateway" into one $5/month box. Split them later when you
add more regions.

## API

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `GET`  | `/v1/health` | — | Liveness check |
| `POST` | `/v1/auth/login` | — | `{email}` → bearer token (registers on first use, starts a trial) |
| `GET`  | `/v1/me` | Bearer | Account + devices + entitlement |
| `POST` | `/v1/devices` | Bearer + entitled | Provision a device → returns a ready `config` |
| `DELETE` | `/v1/devices/:id` | Bearer | Remove a device / revoke its peer |
| `POST` | `/v1/admin/activate` | `x-admin-token` | Manually grant paid access (`{email, days}`) |

The private key in a provisioned config is generated on the gateway, returned
**once**, and never stored — only the public key and assigned IP are kept.

## Deploy to a New York VPS (~10 minutes)

1. Create a small Ubuntu 22.04+ VPS in a NYC/US-East region (DigitalOcean,
   Vultr, Linode, Hetzner). 1 vCPU / 1 GB is plenty to start.
2. Copy this repo's `backend/` folder up (or `git clone` the repo) and run:
   ```bash
   sudo bash backend/scripts/install-gateway.sh
   ```
   It installs WireGuard + Node, generates the server keys, brings up `wg0`
   with NAT, writes `.env`, and starts the API as a systemd service.
3. Open **UDP 51820** and **TCP 8080** in your cloud provider's firewall.
4. The script prints your **public IP**, **server public key**, and an
   **admin token** — save the admin token.

Verify:
```bash
curl http://YOUR_IP:8080/v1/health          # {"ok":true}
```

## Grant yourself paid access (until billing is live)

```bash
curl -X POST http://YOUR_IP:8080/v1/admin/activate \
  -H "x-admin-token: THE_ADMIN_TOKEN" -H 'content-type: application/json' \
  -d '{"email":"you@example.com","days":365}'
```

## Run locally (no WireGuard, for development)

```bash
cd backend
npm install
npm start          # WG_APPLY unset → API works, returns placeholder configs
```

## Before public launch (the real remaining list)

- **HTTPS + a domain** in front of the API (Caddy or nginx — 5-minute add).
- **Magic-link email** on `/auth/login` instead of returning the token directly.
- **Billing webhooks** (Google Play / App Store) that flip `plan` to `active`
  automatically — the `admin/activate` logic is exactly what those call.
- **Multi-region**: split the control plane from gateways and add a server
  picker.
