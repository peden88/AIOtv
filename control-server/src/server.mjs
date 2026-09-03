import { promises as dns } from 'node:dns';
import { readFileSync } from 'node:fs';
import http from 'node:http';
import net from 'node:net';
import path from 'node:path';
import {
  createAdminSession,
  createRateLimiter,
  csrfForSession,
  normalizeUserCode,
  parseCookies,
  randomToken,
  randomUserCode,
  readAdminSession,
  safeEquals,
  sha256,
  verifyPassword,
} from './security.mjs';
import { loadConfig } from './config.mjs';
import { openDatabase } from './database.mjs';

const COOKIE_NAME = 'aiotv_admin';
const MAX_BODY_BYTES = 10 * 1024 * 1024;
const MAX_COLLECTION_BYTES = 10_000_000;

function cleanText(value, maximum = 100) {
  return String(value ?? '').trim().replace(/\s+/g, ' ').slice(0, maximum);
}

function canonicalizeManifestUrl(value, allowHttp = false) {
  const url = new URL(String(value ?? '').trim());
  if (url.protocol !== 'https:' && !(allowHttp && url.protocol === 'http:')) {
    throw Object.assign(new Error('Addon manifest URLs must use HTTPS'), { statusCode: 400 });
  }
  if (url.username || url.password) {
    throw Object.assign(new Error('Addon URLs cannot contain URL credentials'), { statusCode: 400 });
  }
  url.hash = '';
  const cleanPath = url.pathname.replace(/\/+$/, '').replace(/\/manifest\.json$/i, '');
  return {
    manifestUrl: url.toString(),
    // URL normalisation lower-cases scheme/host for us. Preserve path and query
    // casing because configured addon tokens are commonly case-sensitive.
    canonicalUrl: `${url.protocol}//${url.host.toLowerCase()}${cleanPath}${url.search}`,
  };
}

function isPrivateAddress(address) {
  if (net.isIPv4(address)) {
    const parts = address.split('.').map(Number);
    return parts[0] === 10 ||
      parts[0] === 127 ||
      (parts[0] === 169 && parts[1] === 254) ||
      (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) ||
      (parts[0] === 192 && parts[1] === 168) ||
      parts[0] === 0 || parts[0] >= 224;
  }
  if (net.isIPv6(address)) {
    const normalized = address.toLowerCase();
    return normalized === '::1' || normalized === '::' || normalized.startsWith('fc') ||
      normalized.startsWith('fd') || normalized.startsWith('fe8') ||
      normalized.startsWith('fe9') || normalized.startsWith('fea') || normalized.startsWith('feb');
  }
  return true;
}

async function assertPublicHost(hostname) {
  const addresses = await dns.lookup(hostname, { all: true });
  if (!addresses.length || addresses.some(({ address }) => isPrivateAddress(address))) {
    throw Object.assign(new Error('The addon host does not resolve to a public address'), { statusCode: 400 });
  }
}

async function inspectManifest(manifestUrl, allowHttp) {
  let current = new URL(manifestUrl);
  for (let redirect = 0; redirect < 4; redirect += 1) {
    if (current.protocol !== 'https:' && !(allowHttp && current.protocol === 'http:')) {
      throw Object.assign(new Error('Addon manifest redirects must use HTTPS'), { statusCode: 400 });
    }
    await assertPublicHost(current.hostname);
    const response = await fetch(current, {
      redirect: 'manual',
      signal: AbortSignal.timeout(8_000),
      headers: { Accept: 'application/json', 'User-Agent': 'AIOtv-Control/0.1' },
    });
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      const location = response.headers.get('location');
      if (!location) throw Object.assign(new Error('Addon returned an invalid redirect'), { statusCode: 400 });
      current = new URL(location, current);
      continue;
    }
    if (!response.ok) {
      throw Object.assign(new Error(`Addon manifest returned HTTP ${response.status}`), { statusCode: 400 });
    }
    const declaredLength = Number(response.headers.get('content-length') ?? 0);
    if (declaredLength > 1_000_000) {
      throw Object.assign(new Error('Addon manifest is too large'), { statusCode: 400 });
    }
    const text = await response.text();
    if (text.length > 1_000_000) {
      throw Object.assign(new Error('Addon manifest is too large'), { statusCode: 400 });
    }
    let manifest;
    try {
      manifest = JSON.parse(text);
    } catch {
      throw Object.assign(new Error('Addon did not return a valid JSON manifest'), { statusCode: 400 });
    }
    const name = cleanText(manifest.name, 120);
    if (!name) throw Object.assign(new Error('Addon manifest has no name'), { statusCode: 400 });
    return { name, manifestUrl: current.toString() };
  }
  throw Object.assign(new Error('Addon manifest redirected too many times'), { statusCode: 400 });
}

function inspectCollectionJson(value) {
  const text = typeof value === 'string' ? value.trim() : JSON.stringify(value ?? null);
  if (!text || Buffer.byteLength(text) > MAX_COLLECTION_BYTES) {
    throw Object.assign(new Error('Collection file is empty or larger than 10 MB'), { statusCode: 400 });
  }
  let collections;
  try {
    collections = JSON.parse(text);
  } catch {
    throw Object.assign(new Error('Collection file must contain valid JSON'), { statusCode: 400 });
  }
  if (!Array.isArray(collections) || collections.length === 0) {
    throw Object.assign(new Error('Collection file must contain a non-empty JSON array'), { statusCode: 400 });
  }
  for (const [index, collection] of collections.entries()) {
    if (!collection || typeof collection !== 'object' || Array.isArray(collection)) {
      throw Object.assign(new Error(`Collection ${index + 1} is not a JSON object`), { statusCode: 400 });
    }
    if (!cleanText(collection.id, 200) || !cleanText(collection.title, 200) || !Array.isArray(collection.folders)) {
      throw Object.assign(
        new Error(`Collection ${index + 1} must include id, title, and a folders array`),
        { statusCode: 400 },
      );
    }
  }
  return {
    json: JSON.stringify(collections),
    count: collections.length,
    suggestedName: collections.length === 1
      ? cleanText(collections[0].title, 120)
      : `${collections.length} collections`,
  };
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      throw Object.assign(new Error('Request body is too large'), { statusCode: 413 });
    }
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw Object.assign(new Error('Request body must be valid JSON'), { statusCode: 400 });
  }
}

function applyHeaders(response) {
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.setHeader('X-Frame-Options', 'DENY');
  response.setHeader('Referrer-Policy', 'no-referrer');
  response.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  response.setHeader(
    'Content-Security-Policy',
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; " +
      "connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'",
  );
}

function sendJson(response, statusCode, payload, headers = {}) {
  const body = JSON.stringify(payload);
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'Content-Length': Buffer.byteLength(body),
    ...headers,
  });
  response.end(body);
}

function success(response, data = {}, statusCode = 200, headers = {}) {
  sendJson(response, statusCode, { success: true, data }, headers);
}

function failure(response, statusCode, code, message) {
  sendJson(response, statusCode, { success: false, error: { code, message } });
}

function cookieHeader(value, config, maxAge = config.adminSessionSeconds) {
  const attributes = [
    `${COOKIE_NAME}=${encodeURIComponent(value)}`,
    'Path=/',
    'HttpOnly',
    'SameSite=Strict',
    `Max-Age=${maxAge}`,
  ];
  if (config.cookieSecure) attributes.push('Secure');
  return attributes.join('; ');
}

function requestIp(request) {
  const forwarded = String(request.headers['x-forwarded-for'] ?? '').split(',')[0].trim();
  return forwarded || request.socket.remoteAddress || 'unknown';
}

function bearerToken(request) {
  const match = /^Bearer\s+(.+)$/i.exec(String(request.headers.authorization ?? ''));
  return match?.[1]?.trim() || '';
}

function originMatches(request, config) {
  const origin = request.headers.origin;
  if (!origin) return true;
  try {
    return new URL(origin).origin === new URL(config.publicUrl).origin;
  } catch {
    return false;
  }
}

export function createControlServer(overrides = {}) {
  const config = loadConfig(process.env, overrides);
  const database = openDatabase(config.databasePath);
  const allowRate = createRateLimiter();
  const staticFiles = new Map([
    ['/', { type: 'text/html; charset=utf-8', content: readFileSync(path.join(config.staticDir, 'index.html')) }],
    ['/styles.css', { type: 'text/css; charset=utf-8', content: readFileSync(path.join(config.staticDir, 'styles.css')) }],
    ['/app.js', { type: 'text/javascript; charset=utf-8', content: readFileSync(path.join(config.staticDir, 'app.js')) }],
  ]);

  const server = http.createServer(async (request, response) => {
    applyHeaders(response);
    const now = config.now();
    let url;
    try {
      url = new URL(request.url ?? '/', config.publicUrl);
      const method = request.method ?? 'GET';
      const pathname = url.pathname;

      if (method === 'GET' && pathname === '/health') {
        return success(response, { status: 'ok', service: 'aiotv-control' });
      }

      if (method === 'POST' && pathname === '/api/v1/pairings') {
        const ip = requestIp(request);
        if (!allowRate(`pair-start:${ip}`, 20, 60 * 60 * 1000, now.getTime())) {
          return failure(response, 429, 'rate_limited', 'Too many pairing requests');
        }
        const body = await readJson(request);
        const deviceToken = randomToken(32);
        const expiresAt = new Date(now.getTime() + config.pairingTtlSeconds * 1000);
        let userCode;
        let created = false;
        for (let attempt = 0; attempt < 8 && !created; attempt += 1) {
          userCode = randomUserCode();
          try {
            database.createPairing({
              userCodeHash: sha256(normalizeUserCode(userCode)),
              tokenHash: sha256(deviceToken),
              requestedName: cleanText(body.deviceName, 80) || null,
              platform: cleanText(body.platform, 40) || 'android_tv',
              appVersion: cleanText(body.appVersion, 40) || null,
              expiresAt,
            }, now);
            created = true;
          } catch (error) {
            if (!String(error.message).includes('UNIQUE')) throw error;
          }
        }
        if (!created) throw new Error('Could not allocate a unique pairing code');
        return success(response, {
          deviceCode: deviceToken,
          userCode,
          expiresIn: config.pairingTtlSeconds,
          interval: config.pairingPollSeconds,
        }, 201);
      }

      if (method === 'POST' && pathname === '/api/v1/pairings/token') {
        const body = await readJson(request);
        const deviceCode = String(body.deviceCode ?? '').trim();
        if (!deviceCode || deviceCode.length > 200) {
          return failure(response, 400, 'invalid_device_code', 'Device code is missing or invalid');
        }
        const ip = requestIp(request);
        const maximumPolls = Math.ceil(config.pairingTtlSeconds / config.pairingPollSeconds) + 30;
        if (!allowRate(
          `pair-poll:${ip}:${sha256(deviceCode).slice(0, 12)}`,
          maximumPolls,
          (config.pairingTtlSeconds + 60) * 1000,
          now.getTime(),
        )) {
          return failure(response, 429, 'slow_down', 'Pairing checks are too frequent');
        }
        const pairing = database.getPairingByTokenHash(sha256(deviceCode), now);
        if (!pairing) return failure(response, 400, 'invalid_device_code', 'Device code is invalid');
        if (pairing.status === 'expired') {
          return failure(response, 410, 'expired_code', 'Pairing code has expired');
        }
        if (pairing.status === 'pending') {
          return success(response, { status: 'pending', interval: config.pairingPollSeconds });
        }
        return success(response, {
          status: 'approved',
          accessToken: deviceCode,
          tokenType: 'Bearer',
          deviceId: pairing.deviceId,
        });
      }

      if (method === 'GET' && pathname === '/api/v1/device/bootstrap') {
        const token = bearerToken(request);
        if (!token) return failure(response, 401, 'missing_token', 'Device token is required');
        const bootstrap = database.getBootstrap(sha256(token), now);
        if (!bootstrap) return failure(response, 401, 'invalid_token', 'Device token is invalid');
        if (bootstrap.revoked) return failure(response, 403, 'device_revoked', 'This TV is no longer authorised');
        const etagValue = sha256([
          bootstrap.device.id,
          bootstrap.device.name,
          bootstrap.profile.id,
          bootstrap.policy.revision,
        ].join('\0')).slice(0, 32);
        const etag = `W/\"${etagValue}\"`;
        if (request.headers['if-none-match'] === etag) {
          response.writeHead(304, { ETag: etag, 'Cache-Control': 'no-cache' });
          return response.end();
        }
        return success(response, {
          device: bootstrap.device,
          profile: bootstrap.profile,
          policy: bootstrap.policy,
          management: {
            addonMembership: 'administrator',
            catalogOrder: 'administrator',
          },
        }, 200, { ETag: etag });
      }

      if (method === 'POST' && pathname === '/api/admin/login') {
        const ip = requestIp(request);
        if (!allowRate(`admin-login:${ip}`, 8, 15 * 60 * 1000, now.getTime())) {
          return failure(response, 429, 'rate_limited', 'Too many login attempts');
        }
        const body = await readJson(request);
        const valid = verifyPassword(
          String(body.password ?? ''),
          config.adminPasswordHash,
          config.adminPassword,
        );
        if (!valid) return failure(response, 401, 'invalid_credentials', 'Incorrect administrator password');
        const token = createAdminSession(config.sessionSecret, config.adminSessionSeconds, now);
        const session = readAdminSession(token, config.sessionSecret, now);
        return success(response, {
          csrfToken: csrfForSession(session, config.sessionSecret),
          expiresAt: new Date(session.exp * 1000).toISOString(),
        }, 200, { 'Set-Cookie': cookieHeader(token, config) });
      }

      const isAdminApi = pathname.startsWith('/api/admin/');
      let adminSession = null;
      if (isAdminApi) {
        const cookies = parseCookies(request.headers.cookie);
        adminSession = readAdminSession(cookies[COOKIE_NAME], config.sessionSecret, now);
        if (!adminSession) return failure(response, 401, 'admin_login_required', 'Administrator login required');
        if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
          if (!originMatches(request, config)) {
            return failure(response, 403, 'invalid_origin', 'Request origin is not allowed');
          }
          const expectedCsrf = csrfForSession(adminSession, config.sessionSecret);
          if (!safeEquals(request.headers['x-aiotv-csrf'], expectedCsrf)) {
            return failure(response, 403, 'invalid_csrf', 'Security token is missing or invalid');
          }
        }
      }

      if (method === 'GET' && pathname === '/api/admin/session') {
        return success(response, {
          csrfToken: csrfForSession(adminSession, config.sessionSecret),
          expiresAt: new Date(adminSession.exp * 1000).toISOString(),
        });
      }

      if (method === 'POST' && pathname === '/api/admin/logout') {
        return success(response, {}, 200, { 'Set-Cookie': cookieHeader('', config, 0) });
      }

      if (method === 'GET' && pathname === '/api/admin/dashboard') {
        return success(response, {
          users: database.listUsers(),
          groups: database.listGroups(),
          pendingPairingCount: database.countPendingPairings(now),
          recentActivity: database.recentAudit(),
        });
      }

      if (method === 'POST' && pathname === '/api/admin/users') {
        const body = await readJson(request);
        const name = cleanText(body.name, 80);
        const groupId = cleanText(body.groupId, 80) || null;
        if (name.length < 2) return failure(response, 400, 'invalid_name', 'User name must contain at least two characters');
        try {
          const user = database.createUser(name, groupId, now);
          return user
            ? success(response, user, 201)
            : failure(response, 400, 'group_not_found', 'Selected addon group was not found');
        } catch (error) {
          if (String(error.message).includes('UNIQUE')) {
            return failure(response, 409, 'duplicate_user', 'A managed user with that name already exists');
          }
          throw error;
        }
      }

      const userMatch = /^\/api\/admin\/users\/([0-9a-f-]+)$/i.exec(pathname);
      if (userMatch && method === 'GET') {
        const user = database.getUser(userMatch[1]);
        return user ? success(response, user) : failure(response, 404, 'user_not_found', 'Managed user was not found');
      }
      if (userMatch && method === 'PATCH') {
        const body = await readJson(request);
        const changes = {};
        if (body.name != null) {
          changes.name = cleanText(body.name, 80);
          if (changes.name.length < 2) return failure(response, 400, 'invalid_name', 'User name must contain at least two characters');
        }
        if (body.enabled != null) changes.enabled = Boolean(body.enabled);
        if (body.groupId !== undefined) changes.groupId = cleanText(body.groupId, 80) || null;
        try {
          const user = database.updateUser(userMatch[1], changes, now);
          return user ? success(response, user) : failure(response, 404, 'user_not_found', 'Managed user was not found');
        } catch (error) {
          if (String(error.message).includes('UNIQUE')) {
            return failure(response, 409, 'duplicate_user', 'A managed user with that name already exists');
          }
          throw error;
        }
      }

      if (userMatch && method === 'DELETE') {
        const removed = database.deleteUser(userMatch[1], now);
        return removed ? success(response) : failure(response, 404, 'user_not_found', 'Managed user was not found');
      }

      if (method === 'POST' && pathname === '/api/admin/groups') {
        const body = await readJson(request);
        const name = cleanText(body.name, 80);
        if (name.length < 2) return failure(response, 400, 'invalid_name', 'Group name must contain at least two characters');
        try {
          return success(response, database.createGroup(name, now), 201);
        } catch (error) {
          if (String(error.message).includes('UNIQUE')) {
            return failure(response, 409, 'duplicate_group', 'An addon group with that name already exists');
          }
          throw error;
        }
      }

      const groupMatch = /^\/api\/admin\/groups\/([0-9a-f-]+)$/i.exec(pathname);
      if (groupMatch && method === 'GET') {
        const group = database.getGroup(groupMatch[1]);
        return group ? success(response, group) : failure(response, 404, 'group_not_found', 'Addon group was not found');
      }
      if (groupMatch && method === 'PATCH') {
        const body = await readJson(request);
        const name = cleanText(body.name, 80);
        if (name.length < 2) return failure(response, 400, 'invalid_name', 'Group name must contain at least two characters');
        try {
          const group = database.updateGroup(groupMatch[1], { name }, now);
          return group ? success(response, group) : failure(response, 404, 'group_not_found', 'Addon group was not found');
        } catch (error) {
          if (String(error.message).includes('UNIQUE')) {
            return failure(response, 409, 'duplicate_group', 'An addon group with that name already exists');
          }
          throw error;
        }
      }
      if (groupMatch && method === 'DELETE') {
        const removed = database.deleteGroup(groupMatch[1], now);
        return removed ? success(response) : failure(response, 404, 'group_not_found', 'Addon group was not found');
      }

      const groupAddonsMatch = /^\/api\/admin\/groups\/([0-9a-f-]+)\/addons$/i.exec(pathname);
      if (groupAddonsMatch && method === 'POST') {
        const body = await readJson(request);
        let parsed;
        try {
          parsed = canonicalizeManifestUrl(body.manifestUrl, config.allowHttpAddons);
        } catch (error) {
          return failure(response, error.statusCode ?? 400, 'invalid_manifest_url', error.message);
        }
        let name = cleanText(body.name, 120);
        if (!name) {
          try {
            const inspected = await inspectManifest(parsed.manifestUrl, config.allowHttpAddons);
            name = inspected.name;
            parsed = canonicalizeManifestUrl(inspected.manifestUrl, config.allowHttpAddons);
          } catch (error) {
            return failure(response, error.statusCode ?? 400, 'manifest_unavailable', error.message);
          }
        }
        try {
          const addon = database.addResource(groupAddonsMatch[1], {
            type: 'addon',
            name,
            ...parsed,
            canonicalKey: parsed.canonicalUrl,
          }, now);
          return addon ? success(response, addon, 201) : failure(response, 404, 'group_not_found', 'Addon group was not found');
        } catch (error) {
          if (String(error.message).includes('UNIQUE')) {
            return failure(response, 409, 'duplicate_addon', 'That addon is already assigned to this group');
          }
          throw error;
        }
      }

      const groupCollectionsMatch = /^\/api\/admin\/groups\/([0-9a-f-]+)\/collections$/i.exec(pathname);
      if (groupCollectionsMatch && method === 'POST') {
        const body = await readJson(request);
        let inspected;
        try {
          inspected = inspectCollectionJson(body.collectionJson);
        } catch (error) {
          return failure(response, error.statusCode ?? 400, 'invalid_collection', error.message);
        }
        const name = cleanText(body.name, 120) || inspected.suggestedName;
        try {
          const collection = database.addResource(groupCollectionsMatch[1], {
            type: 'collection',
            name,
            collectionJson: inspected.json,
            canonicalKey: sha256(inspected.json),
          }, now);
          return collection
            ? success(response, collection, 201)
            : failure(response, 404, 'group_not_found', 'Addon group was not found');
        } catch (error) {
          if (String(error.message).includes('UNIQUE')) {
            return failure(response, 409, 'duplicate_collection', 'That collection file is already assigned to this group');
          }
          throw error;
        }
      }

      const groupOrderMatch = /^\/api\/admin\/groups\/([0-9a-f-]+)\/resources\/order$/i.exec(pathname);
      if (groupOrderMatch && method === 'PUT') {
        const body = await readJson(request);
        const resourceIds = Array.isArray(body.resourceIds)
          ? body.resourceIds.map((id) => cleanText(id, 80))
          : [];
        try {
          const group = database.reorderResources(groupOrderMatch[1], resourceIds, now);
          return group ? success(response, group) : failure(response, 404, 'group_not_found', 'Addon group was not found');
        } catch (error) {
          return failure(response, error.statusCode ?? 400, 'invalid_resource_order', error.message);
        }
      }

      const groupResourceMatch = /^\/api\/admin\/groups\/([0-9a-f-]+)\/resources\/([0-9a-f-]+)$/i.exec(pathname);
      if (groupResourceMatch && method === 'DELETE') {
        const removed = database.removeResource(groupResourceMatch[1], groupResourceMatch[2], now);
        return removed ? success(response) : failure(response, 404, 'resource_not_found', 'Managed resource was not found');
      }

      const pairingCodeMatch = /^\/api\/admin\/pairings\/([A-Za-z0-9-]+)$/i.exec(pathname);
      if (pairingCodeMatch && method === 'GET') {
        const normalized = normalizeUserCode(pairingCodeMatch[1]);
        if (normalized.length !== 8) return failure(response, 400, 'invalid_pairing_code', 'Enter the eight-character TV code');
        const pairing = database.getPairingByCodeHash(sha256(normalized), now);
        if (!pairing) return failure(response, 404, 'pairing_not_found', 'No pending TV matches that code');
        if (pairing.status === 'expired') return failure(response, 410, 'pairing_expired', 'That TV code has expired');
        if (pairing.status !== 'pending') return failure(response, 409, 'pairing_used', 'That TV has already been paired');
        return success(response, pairing);
      }

      const pairingApproveMatch = /^\/api\/admin\/pairings\/([0-9a-f-]+)\/approve$/i.exec(pathname);
      if (pairingApproveMatch && method === 'POST') {
        const body = await readJson(request);
        const userId = cleanText(body.userId, 80);
        if (!userId) return failure(response, 400, 'user_required', 'Select a managed user');
        try {
          const result = database.approvePairing(
            pairingApproveMatch[1],
            userId,
            cleanText(body.deviceName, 80),
            now,
          );
          return result ? success(response, result) : failure(response, 404, 'pairing_not_found', 'Pairing request or user was not found');
        } catch (error) {
          return failure(response, error.statusCode ?? 409, 'pairing_not_available', error.message);
        }
      }

      const deviceMatch = /^\/api\/admin\/devices\/([0-9a-f-]+)$/i.exec(pathname);
      if (deviceMatch && method === 'PATCH') {
        const body = await readJson(request);
        const changes = {};
        if (body.name != null) {
          changes.name = cleanText(body.name, 80);
          if (!changes.name) return failure(response, 400, 'invalid_name', 'Device name cannot be empty');
        }
        if (body.userId != null) changes.userId = cleanText(body.userId, 80);
        const device = database.updateDevice(deviceMatch[1], changes, now);
        return device ? success(response, device) : failure(response, 404, 'device_not_found', 'Device or managed user was not found');
      }

      const revokeMatch = /^\/api\/admin\/devices\/([0-9a-f-]+)\/revoke$/i.exec(pathname);
      if (revokeMatch && method === 'POST') {
        const revoked = database.revokeDevice(revokeMatch[1], now);
        return revoked ? success(response) : failure(response, 404, 'device_not_found', 'Device was not found');
      }

      if (method === 'GET' && staticFiles.has(pathname)) {
        const file = staticFiles.get(pathname);
        response.writeHead(200, {
          'Content-Type': file.type,
          'Cache-Control': pathname === '/' ? 'no-cache' : 'public, max-age=300',
          'Content-Length': file.content.length,
        });
        return response.end(file.content);
      }

      if (pathname.startsWith('/api/')) return failure(response, 404, 'not_found', 'API route was not found');
      response.writeHead(302, { Location: '/' });
      return response.end();
    } catch (error) {
      const statusCode = error.statusCode ?? 500;
      if (statusCode >= 500) console.error('AIOtv Control request failed:', error);
      if (!response.headersSent) {
        return failure(
          response,
          statusCode,
          statusCode >= 500 ? 'internal_error' : 'invalid_request',
          statusCode >= 500 ? 'The server could not complete the request' : error.message,
        );
      }
      response.end();
    }
  });

  return {
    config,
    database,
    server,
    async listen() {
      await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(config.port, config.host, resolve);
      });
      return server.address();
    },
    async close() {
      if (server.listening) await new Promise((resolve) => server.close(resolve));
      database.close();
    },
  };
}
