import Database from 'better-sqlite3';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

const DB_PATH = process.env.DB_PATH || './data/hausvpn.db';
mkdirSync(dirname(DB_PATH), { recursive: true });

export const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    email       TEXT UNIQUE NOT NULL,
    token_hash  TEXT NOT NULL,
    plan        TEXT NOT NULL DEFAULT 'trial',   -- trial | active | expired
    expires_at  INTEGER,                          -- unix seconds
    created_at  INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS devices (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL,
    name        TEXT,
    public_key  TEXT UNIQUE NOT NULL,             -- client public key (private key never stored)
    address     TEXT UNIQUE NOT NULL,             -- assigned tunnel IP, e.g. 10.7.0.5
    created_at  INTEGER NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  );
`);

export const q = {
  userByEmail: db.prepare('SELECT * FROM users WHERE email = ?'),
  userByToken: db.prepare('SELECT * FROM users WHERE token_hash = ?'),
  insertUser: db.prepare(
    'INSERT INTO users (email, token_hash, plan, expires_at, created_at) VALUES (?, ?, ?, ?, ?)'
  ),
  updateToken: db.prepare('UPDATE users SET token_hash = ? WHERE id = ?'),
  updatePlan: db.prepare('UPDATE users SET plan = ?, expires_at = ? WHERE id = ?'),

  devicesForUser: db.prepare('SELECT * FROM devices WHERE user_id = ? ORDER BY created_at DESC'),
  deviceById: db.prepare('SELECT * FROM devices WHERE id = ? AND user_id = ?'),
  insertDevice: db.prepare(
    'INSERT INTO devices (user_id, name, public_key, address, created_at) VALUES (?, ?, ?, ?, ?)'
  ),
  deleteDevice: db.prepare('DELETE FROM devices WHERE id = ? AND user_id = ?'),
  allAddresses: db.prepare('SELECT address FROM devices'),
};
