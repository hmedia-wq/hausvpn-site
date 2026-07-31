import { execFileSync } from 'node:child_process';
import { q } from './db.js';

// --- Configuration (from environment) -------------------------------------
const WG_INTERFACE = process.env.WG_INTERFACE || 'wg0';
const SUBNET_PREFIX = process.env.SUBNET_PREFIX || '10.7.0'; // /24; .1 is the server
const SERVER_ENDPOINT = process.env.SERVER_ENDPOINT || '';   // e.g. 203.0.113.10:51820
const CLIENT_DNS = process.env.CLIENT_DNS || '1.1.1.1, 1.0.0.1';
// When true, actually run `wg`/`wg-quick`. Off in local/dev so the API is testable
// without a live WireGuard interface.
const APPLY = process.env.WG_APPLY === '1';

function run(cmd, args) {
  return execFileSync(cmd, args, { encoding: 'utf8' }).trim();
}

/** Generate a fresh WireGuard keypair. Returns { privateKey, publicKey }. */
export function generateKeyPair() {
  if (!APPLY) {
    // Deterministic-looking fake keys for local development only.
    const rnd = () => Buffer.from(crypto.getRandomValues(new Uint8Array(32))).toString('base64');
    return { privateKey: rnd(), publicKey: rnd() };
  }
  const privateKey = run('wg', ['genkey']);
  const publicKey = execFileSync('wg', ['pubkey'], { input: privateKey, encoding: 'utf8' }).trim();
  return { privateKey, publicKey };
}

/** The gateway's own public key (what clients peer with). */
export function serverPublicKey() {
  if (process.env.SERVER_PUBLIC_KEY) return process.env.SERVER_PUBLIC_KEY;
  if (!APPLY) return 'SERVER_PUBLIC_KEY_PLACEHOLDER';
  return run('wg', ['show', WG_INTERFACE, 'public-key']);
}

/** Pick the next free address in the subnet (.2 .. .254). */
export function allocateAddress() {
  const used = new Set(q.allAddresses.all().map((r) => r.address));
  for (let host = 2; host <= 254; host++) {
    const addr = `${SUBNET_PREFIX}.${host}`;
    if (!used.has(addr)) return addr;
  }
  throw new Error('Address pool exhausted');
}

/** Add a peer to the live interface and persist it across reboots. */
export function addPeer(publicKey, address) {
  if (!APPLY) return;
  run('wg', ['set', WG_INTERFACE, 'peer', publicKey, 'allowed-ips', `${address}/32`]);
  run('wg-quick', ['save', WG_INTERFACE]);
}

/** Remove a peer from the live interface. */
export function removePeer(publicKey) {
  if (!APPLY) return;
  run('wg', ['set', WG_INTERFACE, 'peer', publicKey, 'remove']);
  run('wg-quick', ['save', WG_INTERFACE]);
}

/** Build the wg-quick client config the app will import. */
export function buildClientConfig({ privateKey, address }) {
  if (!SERVER_ENDPOINT && APPLY) {
    throw new Error('SERVER_ENDPOINT is not configured');
  }
  const endpoint = SERVER_ENDPOINT || 'YOUR_SERVER_IP:51820';
  return [
    '[Interface]',
    `PrivateKey = ${privateKey}`,
    `Address = ${address}/32`,
    `DNS = ${CLIENT_DNS}`,
    '',
    '[Peer]',
    `PublicKey = ${serverPublicKey()}`,
    `Endpoint = ${endpoint}`,
    'AllowedIPs = 0.0.0.0/0, ::/0',
    'PersistentKeepalive = 25',
    '',
  ].join('\n');
}
