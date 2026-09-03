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

    CREATE TABLE IF NOT EXISTS managed_groups (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL COLLATE NOCASE,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    );

    CREATE UNIQUE INDEX IF NOT EXISTS managed_groups_name_uq
      ON managed_groups(name COLLATE NOCASE);

    CREATE TABLE IF NOT EXISTS managed_resources (
      id TEXT PRIMARY KEY,
      group_id TEXT NOT NULL REFERENCES managed_groups(id) ON DELETE CASCADE,
      resource_type TEXT NOT NULL CHECK (resource_type IN ('addon', 'collection')),
      name TEXT NOT NULL,
      manifest_url TEXT,
      canonical_key TEXT NOT NULL,
      collection_json TEXT,
      position INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      UNIQUE(group_id, resource_type, canonical_key)
    );

    CREATE INDEX IF NOT EXISTS managed_resources_group_idx
      ON managed_resources(group_id, position);

    CREATE TABLE IF NOT EXISTS managed_metadata_profiles (
      group_id TEXT PRIMARY KEY REFERENCES managed_groups(id) ON DELETE CASCADE,
      provider TEXT NOT NULL DEFAULT 'aiometadata' CHECK (provider = 'aiometadata'),
      name TEXT NOT NULL,
      manifest_url TEXT NOT NULL,
      canonical_url TEXT NOT NULL,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
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

    CREATE TABLE IF NOT EXISTS prefetch_events (
      id TEXT PRIMARY KEY,
      request_id TEXT NOT NULL,
      device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
      user_id TEXT NOT NULL REFERENCES managed_users(id) ON DELETE CASCADE,
      stage TEXT NOT NULL CHECK (stage IN ('started', 'completed', 'empty', 'failed', 'consumed')),
      content_type TEXT NOT NULL,
      video_id TEXT NOT NULL,
      season INTEGER,
      episode INTEGER,
      addon_count INTEGER,
      stream_count INTEGER,
      duration_ms INTEGER,
      cache_hit INTEGER CHECK (cache_hit IS NULL OR cache_hit IN (0, 1)),
      detail TEXT,
      created_at TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS prefetch_events_created_idx
      ON prefetch_events(created_at DESC);
    CREATE INDEX IF NOT EXISTS prefetch_events_request_idx
      ON prefetch_events(device_id, request_id, created_at);
  `);

  const userColumns = db.prepare('PRAGMA table_info(managed_users)').all();
  const hadGroupColumn = userColumns.some((column) => column.name === 'group_id');
  if (!hadGroupColumn) {
    db.exec('ALTER TABLE managed_users ADD COLUMN group_id TEXT REFERENCES managed_groups(id) ON DELETE SET NULL');
  }

  // Upgrade the original MVP's implicit per-user addon lists into explicit,
  // reusable groups without discarding an existing deployment's assignments.
  const usersWithoutGroups = hadGroupColumn ? [] : db.prepare(`
    SELECT id, name, created_at AS createdAt FROM managed_users WHERE group_id IS NULL
  `).all();
  for (const user of usersWithoutGroups) {
    const groupId = randomUUID();
    let groupName = `${user.name} group`;
    let suffix = 2;
    while (db.prepare('SELECT 1 FROM managed_groups WHERE name = ? COLLATE NOCASE').get(groupName)) {
      groupName = `${user.name} group ${suffix}`;
      suffix += 1;
    }
    db.prepare(`
      INSERT INTO managed_groups (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)
    `).run(groupId, groupName, user.createdAt, user.createdAt);
    db.prepare('UPDATE managed_users SET group_id = ? WHERE id = ?').run(groupId, user.id);
  }

  db.prepare(`
    INSERT OR IGNORE INTO managed_resources
      (id, group_id, resource_type, name, manifest_url, canonical_key, position, created_at)
    SELECT a.id, u.group_id, 'addon', a.name, a.manifest_url, a.canonical_url, a.position, a.created_at
    FROM managed_addons a
    JOIN managed_users u ON u.id = a.user_id
    WHERE u.group_id IS NOT NULL
  `).run();

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

  const getGroup = (id, includeContent = false) => {
    const group = db.prepare(`
      SELECT g.id, g.name, g.created_at AS createdAt, g.updated_at AS updatedAt,
             COUNT(DISTINCT u.id) AS userCount
      FROM managed_groups g
      LEFT JOIN managed_users u ON u.group_id = g.id
      WHERE g.id = ?
      GROUP BY g.id
    `).get(id);
    if (!group) return null;
    const resources = rowsToPlain(db.prepare(`
      SELECT id, resource_type AS type, name, manifest_url AS manifestUrl,
             collection_json AS collectionJson, position, created_at AS createdAt
      FROM managed_resources WHERE group_id = ? ORDER BY position, created_at
    `).all(id)).map((resource) => {
      if (resource.type !== 'collection') return resource;
      const collectionCount = (() => {
        try { return JSON.parse(resource.collectionJson).length; } catch { return 0; }
      })();
      return {
        ...resource,
        collectionCount,
        byteSize: Buffer.byteLength(resource.collectionJson ?? ''),
        ...(!includeContent ? { collectionJson: undefined } : {}),
      };
    });
    const metadata = db.prepare(`
      SELECT provider, name, manifest_url AS manifestUrl,
             created_at AS createdAt, updated_at AS updatedAt
      FROM managed_metadata_profiles WHERE group_id = ?
    `).get(id);
    return { ...group, resources, metadata: metadata ? { ...metadata } : null };
  };

  const getUser = (id) => {
    const user = db.prepare(`
      SELECT id, name, enabled, policy_revision AS policyRevision,
             group_id AS groupId, created_at AS createdAt, updated_at AS updatedAt
      FROM managed_users WHERE id = ?
    `).get(id);
    if (!user) return null;
    const group = user.groupId ? getGroup(user.groupId) : null;
    return {
      ...user,
      enabled: Boolean(user.enabled),
      group,
      addons: group?.resources.filter((resource) => resource.type === 'addon') ?? [],
      collections: group?.resources.filter((resource) => resource.type === 'collection') ?? [],
      devices: rowsToPlain(db.prepare(`
        SELECT id, name, platform, app_version AS appVersion, paired_at AS pairedAt,
               last_seen_at AS lastSeenAt, revoked_at AS revokedAt
        FROM devices WHERE user_id = ? ORDER BY revoked_at IS NOT NULL, paired_at DESC
      `).all(id)),
    };
  };

  return {
    close: () => db.close(),

    createUser(name, groupId = null, now = new Date()) {
      if (groupId && !getGroup(groupId)) return null;
      const id = randomUUID();
      const timestamp = iso(now);
      db.prepare(`
        INSERT INTO managed_users (id, name, group_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
      `).run(id, name, groupId, timestamp, timestamp);
      audit('user.created', `Created managed user ${name}`, 'user', id, now);
      return getUser(id);
    },

    listUsers() {
      return rowsToPlain(db.prepare(`
        SELECT u.id, u.name, u.enabled, u.policy_revision AS policyRevision,
               u.group_id AS groupId, g.name AS groupName,
               u.updated_at AS updatedAt,
               MAX(CASE WHEN m.group_id IS NULL THEN 0 ELSE 1 END) AS hasMetadata,
               COUNT(DISTINCT CASE WHEN r.resource_type = 'addon' THEN r.id END) AS addonCount,
               COUNT(DISTINCT CASE WHEN r.resource_type = 'collection' THEN r.id END) AS collectionCount,
               COUNT(DISTINCT CASE WHEN d.revoked_at IS NULL THEN d.id END) AS deviceCount
        FROM managed_users u
        LEFT JOIN managed_groups g ON g.id = u.group_id
        LEFT JOIN managed_metadata_profiles m ON m.group_id = u.group_id
        LEFT JOIN managed_resources r ON r.group_id = u.group_id
        LEFT JOIN devices d ON d.user_id = u.id
        GROUP BY u.id
        ORDER BY u.name COLLATE NOCASE
      `).all()).map((user) => ({
        ...user,
        enabled: Boolean(user.enabled),
        hasMetadata: Boolean(user.hasMetadata),
      }));
    },

    getUser,

    updateUser(id, changes, now = new Date()) {
      const current = getUser(id);
      if (!current) return null;
      const name = changes.name ?? current.name;
      const enabled = changes.enabled == null ? current.enabled : Boolean(changes.enabled);
      const groupId = Object.hasOwn(changes, 'groupId') ? (changes.groupId || null) : current.groupId;
      if (groupId && !getGroup(groupId)) return null;
      db.prepare(`
        UPDATE managed_users
        SET name = ?, enabled = ?, group_id = ?, policy_revision = policy_revision + 1, updated_at = ?
        WHERE id = ?
      `).run(name, enabled ? 1 : 0, groupId, iso(now), id);
      audit('user.updated', `Updated managed user ${name}`, 'user', id, now);
      return getUser(id);
    },

    deleteUser(id, now = new Date()) {
      return transaction(() => {
        const user = getUser(id);
        if (!user) return false;
        db.prepare(`
          UPDATE pairing_requests
          SET status = 'expired', assigned_user_id = NULL, device_id = NULL
          WHERE assigned_user_id = ? OR device_id IN (SELECT id FROM devices WHERE user_id = ?)
        `).run(id, id);
        db.prepare('DELETE FROM devices WHERE user_id = ?').run(id);
        db.prepare('DELETE FROM managed_users WHERE id = ?').run(id);
        audit('user.deleted', `Deleted managed user ${user.name}`, 'user', id, now);
        return true;
      });
    },

    createGroup(name, now = new Date()) {
      const id = randomUUID();
      const timestamp = iso(now);
      db.prepare(`
        INSERT INTO managed_groups (id, name, created_at, updated_at) VALUES (?, ?, ?, ?)
      `).run(id, name, timestamp, timestamp);
      audit('group.created', `Created addon group ${name}`, 'group', id, now);
      return getGroup(id);
    },

    listGroups() {
      return rowsToPlain(db.prepare(`
        SELECT g.id, g.name, g.created_at AS createdAt, g.updated_at AS updatedAt,
               COUNT(DISTINCT u.id) AS userCount,
               MAX(CASE WHEN m.group_id IS NULL THEN 0 ELSE 1 END) AS hasMetadata,
               COUNT(DISTINCT CASE WHEN r.resource_type = 'addon' THEN r.id END) AS addonCount,
               COUNT(DISTINCT CASE WHEN r.resource_type = 'collection' THEN r.id END) AS collectionCount
        FROM managed_groups g
        LEFT JOIN managed_users u ON u.group_id = g.id
        LEFT JOIN managed_metadata_profiles m ON m.group_id = g.id
        LEFT JOIN managed_resources r ON r.group_id = g.id
        GROUP BY g.id
        ORDER BY g.name COLLATE NOCASE
      `).all()).map((group) => ({ ...group, hasMetadata: Boolean(group.hasMetadata) }));
    },

    getGroup,

    updateGroup(id, changes, now = new Date()) {
      const group = getGroup(id);
      if (!group) return null;
      const name = changes.name ?? group.name;
      db.prepare('UPDATE managed_groups SET name = ?, updated_at = ? WHERE id = ?')
        .run(name, iso(now), id);
      audit('group.updated', `Updated addon group ${name}`, 'group', id, now);
      return getGroup(id);
    },

    deleteGroup(id, now = new Date()) {
      return transaction(() => {
        const group = getGroup(id);
        if (!group) return false;
        db.prepare(`
          UPDATE managed_users
          SET group_id = NULL, policy_revision = policy_revision + 1, updated_at = ?
          WHERE group_id = ?
        `).run(iso(now), id);
        db.prepare('DELETE FROM managed_groups WHERE id = ?').run(id);
        audit('group.deleted', `Deleted addon group ${group.name}`, 'group', id, now);
        return true;
      });
    },

    addResource(groupId, resource, now = new Date()) {
      return transaction(() => {
        const group = getGroup(groupId);
        if (!group) return null;
        const id = randomUUID();
        const nextPosition = db.prepare(`
          SELECT COALESCE(MAX(position), -1) + 1 AS position
          FROM managed_resources WHERE group_id = ?
        `).get(groupId).position;
        db.prepare(`
          INSERT INTO managed_resources
            (id, group_id, resource_type, name, manifest_url, canonical_key,
             collection_json, position, created_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        `).run(
          id,
          groupId,
          resource.type,
          resource.name,
          resource.manifestUrl ?? null,
          resource.canonicalKey,
          resource.collectionJson ?? null,
          nextPosition,
          iso(now),
        );
        db.prepare('UPDATE managed_groups SET updated_at = ? WHERE id = ?').run(iso(now), groupId);
        db.prepare(`
          UPDATE managed_users
          SET policy_revision = policy_revision + 1, updated_at = ?
          WHERE group_id = ?
        `).run(iso(now), groupId);
        audit(
          `${resource.type}.added`,
          `Added ${resource.name} to ${group.name}`,
          'group',
          groupId,
          now,
        );
        return getGroup(groupId).resources.find((item) => item.id === id);
      });
    },

    removeResource(groupId, resourceId, now = new Date()) {
      return transaction(() => {
        const resource = db.prepare(`
          SELECT r.name, r.resource_type AS type, g.name AS groupName
          FROM managed_resources r JOIN managed_groups g ON g.id = r.group_id
          WHERE r.id = ? AND r.group_id = ?
        `).get(resourceId, groupId);
        if (!resource) return false;
        db.prepare('DELETE FROM managed_resources WHERE id = ? AND group_id = ?').run(resourceId, groupId);
        const remaining = db.prepare(`
          SELECT id FROM managed_resources WHERE group_id = ? ORDER BY position, created_at
        `).all(groupId);
        remaining.forEach((item, index) => {
          db.prepare('UPDATE managed_resources SET position = ? WHERE id = ?').run(index, item.id);
        });
        db.prepare('UPDATE managed_groups SET updated_at = ? WHERE id = ?').run(iso(now), groupId);
        db.prepare(`
          UPDATE managed_users
          SET policy_revision = policy_revision + 1, updated_at = ?
          WHERE group_id = ?
        `).run(iso(now), groupId);
        audit(
          `${resource.type}.removed`,
          `Removed ${resource.name} from ${resource.groupName}`,
          'group',
          groupId,
          now,
        );
        return true;
      });
    },

    reorderResources(groupId, resourceIds, now = new Date()) {
      return transaction(() => {
        const group = getGroup(groupId);
        if (!group) return null;
        const existingIds = group.resources.map((resource) => resource.id);
        if (resourceIds.length !== existingIds.length ||
            new Set(resourceIds).size !== resourceIds.length ||
            resourceIds.some((id) => !existingIds.includes(id))) {
          throw Object.assign(new Error('Resource order must include every group resource exactly once'), { statusCode: 400 });
        }
        resourceIds.forEach((id, position) => {
          db.prepare('UPDATE managed_resources SET position = ? WHERE id = ? AND group_id = ?')
            .run(position, id, groupId);
        });
        db.prepare('UPDATE managed_groups SET updated_at = ? WHERE id = ?').run(iso(now), groupId);
        db.prepare(`
          UPDATE managed_users
          SET policy_revision = policy_revision + 1, updated_at = ?
          WHERE group_id = ?
        `).run(iso(now), groupId);
        audit('group.reordered', `Reordered resources in ${group.name}`, 'group', groupId, now);
        return getGroup(groupId);
      });
    },

    setGroupMetadata(groupId, metadata, now = new Date()) {
      return transaction(() => {
        const group = getGroup(groupId);
        if (!group) return null;
        const timestamp = iso(now);
        db.prepare(`
          INSERT INTO managed_metadata_profiles
            (group_id, provider, name, manifest_url, canonical_url, created_at, updated_at)
          VALUES (?, 'aiometadata', ?, ?, ?, ?, ?)
          ON CONFLICT(group_id) DO UPDATE SET
            name = excluded.name,
            manifest_url = excluded.manifest_url,
            canonical_url = excluded.canonical_url,
            updated_at = excluded.updated_at
        `).run(
          groupId,
          metadata.name,
          metadata.manifestUrl,
          metadata.canonicalUrl,
          timestamp,
          timestamp,
        );
        db.prepare('UPDATE managed_groups SET updated_at = ? WHERE id = ?').run(timestamp, groupId);
        db.prepare(`
          UPDATE managed_users
          SET policy_revision = policy_revision + 1, updated_at = ?
          WHERE group_id = ?
        `).run(timestamp, groupId);
        audit(
          'metadata.configured',
          `Configured AIOmetadata for ${group.name}`,
          'group',
          groupId,
          now,
        );
        return getGroup(groupId).metadata;
      });
    },

    clearGroupMetadata(groupId, now = new Date()) {
      return transaction(() => {
        const group = getGroup(groupId);
        if (!group) return null;
        if (!group.metadata) return false;
        db.prepare('DELETE FROM managed_metadata_profiles WHERE group_id = ?').run(groupId);
        const timestamp = iso(now);
        db.prepare('UPDATE managed_groups SET updated_at = ? WHERE id = ?').run(timestamp, groupId);
        db.prepare(`
          UPDATE managed_users
          SET policy_revision = policy_revision + 1, updated_at = ?
          WHERE group_id = ?
        `).run(timestamp, groupId);
        audit(
          'metadata.removed',
          `Removed AIOmetadata from ${group.name}`,
          'group',
          groupId,
          now,
        );
        return true;
      });
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
        if (!user.groupId) throw Object.assign(new Error('Managed user has no addon group'), { statusCode: 409 });
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
               u.group_id AS groupId, u.policy_revision AS policyRevision,
               u.updated_at AS policyUpdatedAt
        FROM devices d JOIN managed_users u ON u.id = d.user_id
        WHERE d.token_hash = ?
      `).get(tokenHash);
      if (!device) return null;
      if (device.revokedAt || !device.userEnabled) return { revoked: true };
      db.prepare('UPDATE devices SET last_seen_at = ? WHERE id = ?').run(iso(now), device.id);
      const resources = device.groupId ? rowsToPlain(db.prepare(`
        SELECT id, resource_type AS type, name, manifest_url AS manifestUrl,
               collection_json AS collectionJson
        FROM managed_resources WHERE group_id = ? ORDER BY position, created_at
      `).all(device.groupId)) : [];
      const addons = resources
        .filter((resource) => resource.type === 'addon')
        .map(({ id, name, manifestUrl }) => ({ id, name, manifestUrl }));
      const collections = resources
        .filter((resource) => resource.type === 'collection')
        .map(({ id, name, collectionJson }) => ({ id, name, json: collectionJson }));
      const metadata = device.groupId ? db.prepare(`
        SELECT provider, name, manifest_url AS manifestUrl
        FROM managed_metadata_profiles WHERE group_id = ?
      `).get(device.groupId) : null;
      return {
        revoked: false,
        device: { id: device.id, name: device.name },
        profile: { id: device.userId, name: device.userName },
        policy: {
          revision: device.policyRevision,
          updatedAt: device.policyUpdatedAt,
          addons,
          collections,
          metadata: metadata ? { ...metadata } : null,
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

    recordPrefetchEvent(deviceId, event, now = new Date(), retentionDays = 14, maximumRows = 5_000) {
      const device = db.prepare(`
        SELECT d.id, d.user_id AS userId
        FROM devices d JOIN managed_users u ON u.id = d.user_id
        WHERE d.id = ? AND d.revoked_at IS NULL AND u.enabled = 1
      `).get(deviceId);
      if (!device) return null;
      const timestamp = iso(now);
      db.prepare(`
        INSERT INTO prefetch_events
          (id, request_id, device_id, user_id, stage, content_type, video_id,
           season, episode, addon_count, stream_count, duration_ms, cache_hit,
           detail, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(
        randomUUID(),
        event.requestId,
        device.id,
        device.userId,
        event.stage,
        event.contentType,
        event.videoId,
        event.season ?? null,
        event.episode ?? null,
        event.addonCount ?? null,
        event.streamCount ?? null,
        event.durationMs ?? null,
        event.cacheHit == null ? null : (event.cacheHit ? 1 : 0),
        event.detail ?? null,
        timestamp,
      );

      const cutoff = new Date(now.getTime() - retentionDays * 24 * 60 * 60 * 1000);
      db.prepare('DELETE FROM prefetch_events WHERE created_at < ?').run(iso(cutoff));
      db.prepare(`
        DELETE FROM prefetch_events WHERE id IN (
          SELECT id FROM prefetch_events
          ORDER BY created_at DESC LIMIT -1 OFFSET ?
        )
      `).run(maximumRows);
      return { acceptedAt: timestamp };
    },

    prefetchDashboard(now = new Date(), limit = 80) {
      const since = iso(new Date(now.getTime() - 24 * 60 * 60 * 1000));
      const summary = db.prepare(`
        SELECT
          SUM(CASE WHEN stage = 'started' THEN 1 ELSE 0 END) AS started,
          SUM(CASE WHEN stage = 'completed' THEN 1 ELSE 0 END) AS completed,
          SUM(CASE WHEN stage = 'empty' THEN 1 ELSE 0 END) AS empty,
          SUM(CASE WHEN stage = 'failed' THEN 1 ELSE 0 END) AS failed,
          SUM(CASE WHEN stage = 'consumed' THEN 1 ELSE 0 END) AS consumed,
          ROUND(AVG(CASE WHEN stage IN ('completed', 'empty') THEN duration_ms END)) AS averageDurationMs,
          MAX(created_at) AS lastEventAt
        FROM prefetch_events WHERE created_at >= ?
      `).get(since);
      const recent = rowsToPlain(db.prepare(`
        SELECT p.id, p.request_id AS requestId, p.stage,
               p.content_type AS contentType, p.video_id AS videoId,
               p.season, p.episode, p.addon_count AS addonCount,
               p.stream_count AS streamCount, p.duration_ms AS durationMs,
               p.cache_hit AS cacheHit, p.detail, p.created_at AS createdAt,
               d.name AS deviceName, u.name AS userName
        FROM prefetch_events p
        JOIN devices d ON d.id = p.device_id
        JOIN managed_users u ON u.id = p.user_id
        ORDER BY p.created_at DESC LIMIT ?
      `).all(limit)).map((event) => ({
        ...event,
        cacheHit: event.cacheHit == null ? null : Boolean(event.cacheHit),
      }));
      return {
        windowHours: 24,
        summary: {
          started: Number(summary.started ?? 0),
          completed: Number(summary.completed ?? 0),
          empty: Number(summary.empty ?? 0),
          failed: Number(summary.failed ?? 0),
          consumed: Number(summary.consumed ?? 0),
          averageDurationMs: summary.averageDurationMs == null
            ? null
            : Number(summary.averageDurationMs),
          lastEventAt: summary.lastEventAt ?? null,
        },
        recent,
      };
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
