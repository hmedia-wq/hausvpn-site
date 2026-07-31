import { randomBytes, createHash } from 'node:crypto';
import { q } from './db.js';

const TRIAL_DAYS = Number(process.env.TRIAL_DAYS || 7);

export function now() {
  return Math.floor(Date.now() / 1000);
}

export function hashToken(token) {
  return createHash('sha256').update(token).digest('hex');
}

/** Issue a fresh opaque bearer token for a user id, storing only its hash. */
export function issueToken(userId) {
  const token = randomBytes(32).toString('hex');
  q.updateToken.run(hashToken(token), userId);
  return token;
}

/**
 * Log in (or register) by email. This is the MVP path: it returns a bearer
 * token directly. Swap in a magic-link email step here before public launch.
 */
export function loginOrRegister(email) {
  let user = q.userByEmail.get(email);
  if (!user) {
    const expires = now() + TRIAL_DAYS * 86400;
    const info = q.insertUser.run(email, 'pending', 'trial', expires, now());
    user = { id: info.lastInsertRowid, email, plan: 'trial', expires_at: expires };
  }
  const token = issueToken(user.id);
  return { token, user: q.userByEmail.get(email) };
}

/** Express middleware: resolves req.user from the Authorization: Bearer token. */
export function requireAuth(req, res, next) {
  const header = req.get('authorization') || '';
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) return res.status(401).json({ error: 'missing_token' });
  const user = q.userByToken.get(hashToken(match[1]));
  if (!user) return res.status(401).json({ error: 'invalid_token' });
  req.user = user;
  next();
}

/** True when the user is entitled to connect right now. */
export function isEntitled(user) {
  if (user.plan === 'active') return true;
  if (user.plan === 'trial' && user.expires_at && user.expires_at > now()) return true;
  return false;
}

/** Express middleware: 402 unless the subscription/trial is valid. */
export function requireEntitlement(req, res, next) {
  if (!isEntitled(req.user)) {
    return res.status(402).json({ error: 'subscription_required' });
  }
  next();
}
