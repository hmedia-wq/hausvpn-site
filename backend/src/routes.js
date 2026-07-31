import { Router } from 'express';
import { q } from './db.js';
import {
  loginOrRegister,
  requireAuth,
  requireEntitlement,
  isEntitled,
  now,
} from './auth.js';
import {
  generateKeyPair,
  allocateAddress,
  addPeer,
  removePeer,
  buildClientConfig,
} from './wireguard.js';

export const router = Router();

router.get('/health', (_req, res) => res.json({ ok: true }));

// --- Auth -----------------------------------------------------------------
router.post('/auth/login', (req, res) => {
  const email = String(req.body?.email || '').trim().toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    return res.status(400).json({ error: 'invalid_email' });
  }
  const { token, user } = loginOrRegister(email);
  res.json({
    token,
    plan: user.plan,
    expires_at: user.expires_at,
    entitled: isEntitled(user),
  });
});

// --- Account --------------------------------------------------------------
router.get('/me', requireAuth, (req, res) => {
  const devices = q.devicesForUser.all(req.user.id).map((d) => ({
    id: d.id,
    name: d.name,
    address: d.address,
    created_at: d.created_at,
  }));
  res.json({
    email: req.user.email,
    plan: req.user.plan,
    expires_at: req.user.expires_at,
    entitled: isEntitled(req.user),
    devices,
  });
});

// --- Devices / config provisioning ---------------------------------------
// This is the endpoint the app calls to get a working server config
// automatically — no manual paste.
router.post('/devices', requireAuth, requireEntitlement, (req, res) => {
  try {
    const name = String(req.body?.name || 'device').slice(0, 64);
    const { privateKey, publicKey } = generateKeyPair();
    const address = allocateAddress();

    addPeer(publicKey, address);
    q.insertDevice.run(req.user.id, name, publicKey, address, now());

    // The private key is returned to the client exactly once and never stored.
    const config = buildClientConfig({ privateKey, address });
    res.json({ address, config });
  } catch (err) {
    res.status(500).json({ error: 'provision_failed', detail: String(err.message || err) });
  }
});

router.delete('/devices/:id', requireAuth, (req, res) => {
  const device = q.deviceById.get(Number(req.params.id), req.user.id);
  if (!device) return res.status(404).json({ error: 'not_found' });
  try {
    removePeer(device.public_key);
  } catch {
    /* best effort — still remove from DB */
  }
  q.deleteDevice.run(device.id, req.user.id);
  res.json({ ok: true });
});

// --- Admin: manually grant access until billing is live -------------------
router.post('/admin/activate', (req, res) => {
  const adminToken = process.env.ADMIN_TOKEN;
  if (!adminToken || req.get('x-admin-token') !== adminToken) {
    return res.status(403).json({ error: 'forbidden' });
  }
  const email = String(req.body?.email || '').trim().toLowerCase();
  const days = Number(req.body?.days || 30);
  const user = q.userByEmail.get(email);
  if (!user) return res.status(404).json({ error: 'user_not_found' });
  q.updatePlan.run('active', now() + days * 86400, user.id);
  res.json({ ok: true, email, plan: 'active', days });
});
