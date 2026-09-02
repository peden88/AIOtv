import { randomUUID } from 'node:crypto';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';

function iso(value) {
  return value instanceof Date ? value.toISOString() : new Date(value).toISOString();
}

function rowsToPlain(rows) {
  return rows.map((row) => ({ ...row }));
}

export function openDatabase(databasePath) {
  if (databasePath !== ':memory:') mkdirSync(path.dirname(databasePath), { recursive: true });
  const db = new DatabaseSync(databasePath);
  db.exec(`
    PRAGMA foreign_keys = ON;
    PRAGMA journal_mode = WAL;
    PRAGMA synchronous = NORMAL;
    PRAGMA busy_timeout = 5000;

    CREATE TABLE IF NOT EXISTS managed_users (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL COLLATE NOCASE,
      enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
      policy_revision INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE UNIQUE INDEX IF NOT EXISTS managed_users_name_uq
      ON managed_users(name COLLATE NOCASE);

    CREATE TABLE IF NOT EXISTS managed_addons (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES managed_users(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      manifest_url TEXT NOT NULL,
      canonical_url TEXT NOT NULL,
      position INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      UNIQUE(user_id, canonical_url)
    );

    CREATE TABLE IF NOT EXISTS devices (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES managed_users(id),
      token_hash TEXT NOT NULL UNIQUE,
      name TEXT NOT NULL,
      platform TEXT NOT NULL DEFAULT 'android_tv',
      app_version TEXT,
      paired_at TEXT NOT NULL,
      last_seen_at TEXT,
      revoked_at TEXT
    );

    CREATE TABLE IF NOT EXISTS pairing_requests (
      id TEXT PRIMARY KEY,
      user_code_hash TEXT NOT NULL UNIQUE,
      token_hash TEXT NOT NULL UNIQUE,
      requested_name TEXT,
      platform TEXT NOT NULL DEFAULT 'android_tv',
      app_version TEXT,
      status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'expired')),
      requested_at TEXT NOT NULL,
      expires_at TEXT NOT NULL,
      approved_at TEXT,
      assigned_user_id TEXT REFERENCES managed_users(id),
      device_id TEXT REFERENCES devices(id)
    );

    CREATE INDEX IF NOT EXISTS pairing_requests_token_idx ON pairing_requests(token_hash);
    CREATE INDEX IF NOT EXISTS pairing_requests_expiry_idx ON pairing_requests(expires_at);
    CREATE INDEX IF NOT EXISTS devices_user_idx ON devices(user_id);

    CREATE TABLE IF NOT EXISTS audit_events (
      id TEXT PRIMARY KEY,
      event_type TEXT NOT NULL,
      summary TEXT NOT NULL,
      target_type TEXT,
      target_id TEXT,
      created_at TEXT NOT NULL
    );
  `);

  const transaction = (operation) => {
    db.exec('BEGIN IMMEDIATE');
    try {
      const result = operation();
      db.exec('COMMIT');
      return result;
    } catch (error) {
      db.exec('ROLLBACK');
      throw error;
    }
  };

  const audit = (eventType, summary, targetType = null, targetId = null, now = new Date()) => {
    db.prepare(`
      INSERT INTO audit_events (id, event_type, summary, target_type, target_id, created_at)
      VALUES (?, ?, ?, ?, ?, ?)
    `).run(randomUUID(), eventType, summary, targetType, targetId, iso(now));
  };

  const getUser = (id) => {
    const user = db.prepare(`
      SELECT id, name, enabled, policy_revision AS policyRevision,
             created_at AS createdAt, updated_at AS updatedAt
      FROM managed_users WHERE id = ?
    `).get(id);
    if (!user) return null;
    return {
      ...user,
      enabled: Boolean(user.enabled),
      addons: rowsToPlain(db.prepare(`
        SELECT id, name, manifest_url AS manifestUrl, position, created_at AS createdAt
        FROM managed_addons WHERE user_id = ? ORDER BY position, created_at
      `).all(id)),
      devices: rowsToPlain(db.prepare(`
        SELECT id, name, platform, app_version AS appVersion, paired_at AS pairedAt,
               last_seen_at AS lastSeenAt, revoked_at AS revokedAt
        FROM devices WHERE user_id = ? ORDER BY revoked_at IS NOT NULL, paired_at DESC
      `).all(id)),
    };
  };

  return {
    close: () => db.close(),

    createUser(name, now = new Date()) {
      const id = randomUUID();
      const timestamp = iso(now);
      db.prepare(`
        INSERT INTO managed_users (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)
      `).run(id, name, timestamp, timestamp);
      audit('user.created', `Created managed user ${name}`, 'user', id, now);
      return getUser(id);
    },

    listUsers() {
      return rowsToPlain(db.prepare(`
        SELECT u.id, u.name, u.enabled, u.policy_revision AS policyRevision,
               u.updated_at AS updatedAt,
               COUNT(DISTINCT a.id) AS addonCount,
               COUNT(DISTINCT CASE WHEN d.revoked_at IS NULL THEN d.id END) AS deviceCount
        FROM managed_users u
        LEFT JOIN managed_addons a ON a.user_id = u.id
        LEFT JOIN devices d ON d.user_id = u.id
        GROUP BY u.id
        ORDER BY u.name COLLATE NOCASE
      `).all()).map((user) => ({ ...user, enabled: Boolean(user.enabled) }));
    },

    getUser,

    updateUser(id, changes, now = new Date()) {
      const current = getUser(id);
      if (!current) return null;
      const name = changes.name ?? current.name;
      const enabled = changes.enabled == null ? current.enabled : Boolean(changes.enabled);
      db.prepare(`
        UPDATE managed_users
        SET name = ?, enabled = ?, policy_revision = policy_revision + 1, updated_at = ?
        WHERE id = ?
      `).run(name, enabled ? 1 : 0, iso(now), id);
      audit('user.updated', `Updated managed user ${name}`, 'user', id, now);
      return getUser(id);
    },

    addAddon(userId, addon, now = new Date()) {
      return transaction(() => {
        const user = getUser(userId);
        if (!user) return null;
        const id = randomUUID();
        const nextPosition = db.prepare(`
          SELECT COALESCE(MAX(position), -1) + 1 AS position FROM managed_addons WHERE user_id = ?
        `).get(userId).position;
        db.prepare(`
          INSERT INTO managed_addons
            (id, user_id, name, manifest_url, canonical_url, position, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?)
        `).run(id, userId, addon.name, addon.manifestUrl, addon.canonicalUrl, nextPosition, iso(now));
        db.prepare(`
          UPDATE managed_users SET policy_revision = policy_revision + 1, updated_at = ? WHERE id = ?
        `).run(iso(now), userId);
        audit('addon.added', `Added ${addon.name} to ${user.name}`, 'user', userId, now);
        return getUser(userId).addons.find((item) => item.id === id);
      });
    },

    removeAddon(userId, addonId, now = new Date()) {
      return transaction(() => {
        const addon = db.prepare(`
          SELECT a.name, u.name AS userName FROM managed_addons a
          JOIN managed_users u ON u.id = a.user_id
          WHERE a.id = ? AND a.user_id = ?
        `).get(addonId, userId);
        if (!addon) return false;
        db.prepare('DELETE FROM managed_addons WHERE id = ? AND user_id = ?').run(addonId, userId);
        db.prepare(`
          UPDATE managed_users SET policy_revision = policy_revision + 1, updated_at = ? WHERE id = ?
        `).run(iso(now), userId);
        audit('addon.removed', `Removed ${addon.name} from ${addon.userName}`, 'user', userId, now);
        return true;
      });
    },

    createPairing(pairing, now = new Date()) {
      const id = randomUUID();
      db.prepare(`
        INSERT INTO pairing_requests
          (id, user_code_hash, token_hash, requested_name, platform, app_version,
           requested_at, expires_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `).run(
        id,
        pairing.userCodeHash,
        pairing.tokenHash,
        pairing.requestedName,
        pairing.platform,
        pairing.appVersion,
        iso(now),
        iso(pairing.expiresAt),
      );
      return { id };
    },

    getPairingByCodeHash(codeHash, now = new Date()) {
      const pairing = db.prepare(`
        SELECT id, requested_name AS requestedName, platform, app_version AS appVersion,
               status, requested_at AS requestedAt, expires_at AS expiresAt,
               assigned_user_id AS assignedUserId, device_id AS deviceId
        FROM pairing_requests WHERE user_code_hash = ?
      `).get(codeHash);
      if (!pairing) return null;
      if (pairing.status === 'pending' && Date.parse(pairing.expiresAt) <= now.getTime()) {
        db.prepare("UPDATE pairing_requests SET status = 'expired' WHERE id = ?").run(pairing.id);
        pairing.status = 'expired';
      }
      return { ...pairing };
    },

    getPairingByTokenHash(tokenHash, now = new Date()) {
      const pairing = db.prepare(`
        SELECT id, status, expires_at AS expiresAt, assigned_user_id AS assignedUserId,
               device_id AS deviceId
        FROM pairing_requests WHERE token_hash = ?
      `).get(tokenHash);
      if (!pairing) return null;
      if (pairing.status === 'pending' && Date.parse(pairing.expiresAt) <= now.getTime()) {
        db.prepare("UPDATE pairing_requests SET status = 'expired' WHERE id = ?").run(pairing.id);
        pairing.status = 'expired';
      }
      return { ...pairing };
    },

    approvePairing(pairingId, userId, deviceName, now = new Date()) {
      return transaction(() => {
        const pairing = db.prepare('SELECT * FROM pairing_requests WHERE id = ?').get(pairingId);
        const user = getUser(userId);
        if (!pairing || !user) return null;
        if (!user.enabled) throw Object.assign(new Error('Managed user is disabled'), { statusCode: 409 });
        if (pairing.status !== 'pending') {
          throw Object.assign(new Error('Pairing request is no longer pending'), { statusCode: 409 });
        }
        if (Date.parse(pairing.expires_at) <= now.getTime()) {
          db.prepare("UPDATE pairing_requests SET status = 'expired' WHERE id = ?").run(pairingId);
          throw Object.assign(new Error('Pairing code has expired'), { statusCode: 410 });
        }

        const deviceId = randomUUID();
        const resolvedName = deviceName || pairing.requested_name || 'AIOtv device';
        db.prepare(`
          INSERT INTO devices
            (id, user_id, token_hash, name, platform, app_version, paired_at, last_seen_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `).run(
          deviceId,
          userId,
          pairing.token_hash,
          resolvedName,
          pairing.platform,
          pairing.app_version,
          iso(now),
          iso(now),
        );
        db.prepare(`
          UPDATE pairing_requests
          SET status = 'approved', approved_at = ?, assigned_user_id = ?, device_id = ?
          WHERE id = ?
        `).run(iso(now), userId, deviceId, pairingId);
        audit('device.paired', `Paired ${resolvedName} with ${user.name}`, 'device', deviceId, now);
        return { deviceId, userId, userName: user.name, deviceName: resolvedName };
      });
    },

    getBootstrap(tokenHash, now = new Date()) {
      const device = db.prepare(`
        SELECT d.id, d.user_id AS userId, d.name, d.platform, d.app_version AS appVersion,
               d.paired_at AS pairedAt, d.last_seen_at AS lastSeenAt, d.revoked_at AS revokedAt,
               u.name AS userName, u.enabled AS userEnabled,
               u.policy_revision AS policyRevision, u.updated_at AS policyUpdatedAt
        FROM devices d JOIN managed_users u ON u.id = d.user_id
        WHERE d.token_hash = ?
      `).get(tokenHash);
      if (!device) return null;
      if (device.revokedAt || !device.userEnabled) return { revoked: true };
      db.prepare('UPDATE devices SET last_seen_at = ? WHERE id = ?').run(iso(now), device.id);
      const addons = rowsToPlain(db.prepare(`
        SELECT id, name, manifest_url AS manifestUrl
        FROM managed_addons WHERE user_id = ? ORDER BY position, created_at
      `).all(device.userId));
      return {
        revoked: false,
        device: { id: device.id, name: device.name },
        profile: { id: device.userId, name: device.userName },
        policy: {
          revision: device.policyRevision,
          updatedAt: device.policyUpdatedAt,
          addons,
        },
      };
    },

    updateDevice(id, changes, now = new Date()) {
      const current = db.prepare(`
        SELECT id, name, user_id AS userId, revoked_at AS revokedAt FROM devices WHERE id = ?
      `).get(id);
      if (!current) return null;
      const nextUserId = changes.userId ?? current.userId;
      const user = getUser(nextUserId);
      if (!user) return null;
      const nextName = changes.name ?? current.name;
      db.prepare('UPDATE devices SET name = ?, user_id = ? WHERE id = ?').run(nextName, nextUserId, id);
      audit('device.updated', `Updated ${nextName}; assigned to ${user.name}`, 'device', id, now);
      return db.prepare(`
        SELECT id, name, user_id AS userId, platform, app_version AS appVersion,
               paired_at AS pairedAt, last_seen_at AS lastSeenAt, revoked_at AS revokedAt
        FROM devices WHERE id = ?
      `).get(id);
    },

    revokeDevice(id, now = new Date()) {
      const device = db.prepare('SELECT id, name, revoked_at AS revokedAt FROM devices WHERE id = ?').get(id);
      if (!device) return null;
      if (!device.revokedAt) {
        db.prepare('UPDATE devices SET revoked_at = ? WHERE id = ?').run(iso(now), id);
        audit('device.revoked', `Revoked ${device.name}`, 'device', id, now);
      }
      return true;
    },

    recentAudit(limit = 12) {
      return rowsToPlain(db.prepare(`
        SELECT id, event_type AS eventType, summary, target_type AS targetType,
               target_id AS targetId, created_at AS createdAt
        FROM audit_events ORDER BY created_at DESC LIMIT ?
      `).all(limit));
    },

    countPendingPairings(now = new Date()) {
      db.prepare(`
        UPDATE pairing_requests SET status = 'expired'
        WHERE status = 'pending' AND expires_at <= ?
      `).run(iso(now));
      return db.prepare("SELECT COUNT(*) AS count FROM pairing_requests WHERE status = 'pending'").get().count;
    },
  };
}
