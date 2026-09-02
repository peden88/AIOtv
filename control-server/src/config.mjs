import { fileURLToPath } from 'node:url';
import path from 'node:path';

const moduleDir = path.dirname(fileURLToPath(import.meta.url));

function asBoolean(value, fallback = false) {
  if (value == null || value === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(String(value).trim().toLowerCase());
}

function asInteger(value, fallback, minimum, maximum) {
  const parsed = Number.parseInt(value ?? '', 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(maximum, Math.max(minimum, parsed));
}

export function loadConfig(env = process.env, overrides = {}) {
  const publicUrl = String(overrides.publicUrl ?? env.AIOTV_PUBLIC_URL ?? 'http://localhost:3000').replace(/\/$/, '');
  const config = {
    host: overrides.host ?? env.AIOTV_HOST ?? '0.0.0.0',
    port: overrides.port ?? asInteger(env.AIOTV_PORT, 3000, 0, 65535),
    publicUrl,
    databasePath: overrides.databasePath ?? env.AIOTV_DATABASE_PATH ?? path.resolve(moduleDir, '../data/aiotv-control.sqlite'),
    staticDir: overrides.staticDir ?? path.resolve(moduleDir, '../public'),
    adminPassword: overrides.adminPassword ?? env.AIOTV_ADMIN_PASSWORD ?? '',
    adminPasswordHash: overrides.adminPasswordHash ?? env.AIOTV_ADMIN_PASSWORD_HASH ?? '',
    sessionSecret: overrides.sessionSecret ?? env.AIOTV_SESSION_SECRET ?? '',
    cookieSecure: overrides.cookieSecure ?? asBoolean(env.AIOTV_COOKIE_SECURE, publicUrl.startsWith('https://')),
    allowHttpAddons: overrides.allowHttpAddons ?? asBoolean(env.AIOTV_ALLOW_HTTP_ADDONS, false),
    pairingTtlSeconds: overrides.pairingTtlSeconds ?? asInteger(env.AIOTV_PAIRING_TTL_SECONDS, 900, 120, 3600),
    pairingPollSeconds: overrides.pairingPollSeconds ?? asInteger(env.AIOTV_PAIRING_POLL_SECONDS, 3, 2, 30),
    adminSessionSeconds: overrides.adminSessionSeconds ?? asInteger(env.AIOTV_ADMIN_SESSION_SECONDS, 43200, 900, 604800),
    now: overrides.now ?? (() => new Date()),
  };

  if (!config.adminPassword && !config.adminPasswordHash) {
    throw new Error('Set AIOTV_ADMIN_PASSWORD_HASH (recommended) or AIOTV_ADMIN_PASSWORD');
  }
  if (config.adminPassword && config.adminPassword.length < 12) {
    throw new Error('AIOTV_ADMIN_PASSWORD must contain at least 12 characters');
  }
  if (config.sessionSecret.length < 32) {
    throw new Error('AIOTV_SESSION_SECRET must contain at least 32 characters');
  }

  return config;
}
