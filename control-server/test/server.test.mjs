import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { createControlServer } from '../src/server.mjs';
import { createPasswordHash } from '../src/security.mjs';

async function read(response) {
  const payload = await response.json();
  return { response, payload };
}

test('administrator can create a profile and pair, bootstrap, then revoke a TV', async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), 'aiotv-control-test-'));
  const app = createControlServer({
    host: '127.0.0.1',
    port: 0,
    publicUrl: 'http://127.0.0.1',
    databasePath: path.join(tempDir, 'test.sqlite'),
    adminPassword: 'correct horse battery staple',
    sessionSecret: 'test-session-secret-with-more-than-32-characters',
    cookieSecure: false,
    pairingTtlSeconds: 900,
    pairingPollSeconds: 2,
  });
  t.after(async () => {
    await app.close();
    rmSync(tempDir, { recursive: true, force: true });
  });
  const address = await app.listen();
  const base = `http://127.0.0.1:${address.port}`;

  const home = await fetch(`${base}/`);
  assert.equal(home.status, 200);
  assert.match(await home.text(), /Pair a TV/);

  const anonymousDashboard = await read(await fetch(`${base}/api/admin/dashboard`));
  assert.equal(anonymousDashboard.response.status, 401);

  const login = await read(await fetch(`${base}/api/admin/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ password: 'correct horse battery staple' }),
  }));
  assert.equal(login.response.status, 200);
  const cookie = login.response.headers.get('set-cookie').split(';', 1)[0];
  const csrf = login.payload.data.csrfToken;

  const adminRequest = (pathname, options = {}) => fetch(`${base}${pathname}`, {
    ...options,
    headers: {
      cookie,
      'x-aiotv-csrf': csrf,
      ...(options.body ? { 'content-type': 'application/json' } : {}),
      ...options.headers,
    },
  });

  const createdUser = await read(await adminRequest('/api/admin/users', {
    method: 'POST',
    body: JSON.stringify({ name: 'Gary' }),
  }));
  assert.equal(createdUser.response.status, 201);
  assert.equal(createdUser.payload.data.name, 'Gary');
  const userId = createdUser.payload.data.id;

  const addon = await read(await adminRequest(`/api/admin/users/${userId}/addons`, {
    method: 'POST',
    body: JSON.stringify({
      name: 'AIOStreams – Gary',
      manifestUrl: 'https://example.com/gary/manifest.json',
    }),
  }));
  assert.equal(addon.response.status, 201);
  assert.equal(addon.payload.data.name, 'AIOStreams – Gary');

  const start = await read(await fetch(`${base}/api/v1/pairings`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      deviceName: 'Nvidia Shield',
      platform: 'android_tv',
      appVersion: '0.8.12-beta',
    }),
  }));
  assert.equal(start.response.status, 201);
  assert.match(start.payload.data.userCode, /^[A-Z2-9]{4}-[A-Z2-9]{4}$/);
  assert.ok(start.payload.data.deviceCode.length >= 40);
  assert.equal('verificationUri' in start.payload.data, false);

  const pending = await read(await fetch(`${base}/api/v1/pairings/token`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ deviceCode: start.payload.data.deviceCode }),
  }));
  assert.equal(pending.payload.data.status, 'pending');

  const resolved = await read(await adminRequest(`/api/admin/pairings/${start.payload.data.userCode}`));
  assert.equal(resolved.response.status, 200);
  assert.equal(resolved.payload.data.requestedName, 'Nvidia Shield');

  const approved = await read(await adminRequest(`/api/admin/pairings/${resolved.payload.data.id}/approve`, {
    method: 'POST',
    body: JSON.stringify({ userId, deviceName: 'Gary’s living room TV' }),
  }));
  assert.equal(approved.response.status, 200);
  assert.equal(approved.payload.data.userName, 'Gary');

  const token = await read(await fetch(`${base}/api/v1/pairings/token`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ deviceCode: start.payload.data.deviceCode }),
  }));
  assert.equal(token.payload.data.status, 'approved');
  assert.equal(token.payload.data.accessToken, start.payload.data.deviceCode);

  const bootstrapResponse = await fetch(`${base}/api/v1/device/bootstrap`, {
    headers: { authorization: `Bearer ${token.payload.data.accessToken}` },
  });
  const etag = bootstrapResponse.headers.get('etag');
  const bootstrap = await read(bootstrapResponse);
  assert.equal(bootstrap.response.status, 200);
  assert.equal(bootstrap.payload.data.profile.name, 'Gary');
  assert.equal(bootstrap.payload.data.device.name, 'Gary’s living room TV');
  assert.equal(bootstrap.payload.data.policy.addons.length, 1);
  assert.equal(bootstrap.payload.data.management.catalogOrder, 'device');
  assert.ok(etag);

  const notModified = await fetch(`${base}/api/v1/device/bootstrap`, {
    headers: {
      authorization: `Bearer ${token.payload.data.accessToken}`,
      'if-none-match': etag,
    },
  });
  assert.equal(notModified.status, 304);

  const secondUser = await read(await adminRequest('/api/admin/users', {
    method: 'POST',
    body: JSON.stringify({ name: 'Living room' }),
  }));
  const reassigned = await read(await adminRequest(`/api/admin/devices/${approved.payload.data.deviceId}`, {
    method: 'PATCH',
    body: JSON.stringify({ userId: secondUser.payload.data.id }),
  }));
  assert.equal(reassigned.response.status, 200);

  const reassignedBootstrapResponse = await fetch(`${base}/api/v1/device/bootstrap`, {
    headers: {
      authorization: `Bearer ${token.payload.data.accessToken}`,
      'if-none-match': etag,
    },
  });
  const reassignedBootstrap = await read(reassignedBootstrapResponse);
  assert.equal(reassignedBootstrap.response.status, 200);
  assert.equal(reassignedBootstrap.payload.data.profile.name, 'Living room');
  assert.notEqual(reassignedBootstrap.response.headers.get('etag'), etag);

  const revoked = await read(await adminRequest(`/api/admin/devices/${approved.payload.data.deviceId}/revoke`, {
    method: 'POST',
  }));
  assert.equal(revoked.response.status, 200);

  const denied = await read(await fetch(`${base}/api/v1/device/bootstrap`, {
    headers: { authorization: `Bearer ${token.payload.data.accessToken}` },
  }));
  assert.equal(denied.response.status, 403);
  assert.equal(denied.payload.error.code, 'device_revoked');
});

test('administrator routes reject missing CSRF and duplicate managed users', async (t) => {
  const tempDir = mkdtempSync(path.join(os.tmpdir(), 'aiotv-control-test-'));
  const app = createControlServer({
    host: '127.0.0.1',
    port: 0,
    publicUrl: 'http://127.0.0.1',
    databasePath: path.join(tempDir, 'test.sqlite'),
    adminPasswordHash: createPasswordHash('correct horse battery staple'),
    sessionSecret: 'another-test-session-secret-more-than-32-characters',
    cookieSecure: false,
  });
  t.after(async () => {
    await app.close();
    rmSync(tempDir, { recursive: true, force: true });
  });
  const address = await app.listen();
  const base = `http://127.0.0.1:${address.port}`;

  const login = await read(await fetch(`${base}/api/admin/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ password: 'correct horse battery staple' }),
  }));
  assert.equal(login.response.status, 200);
  const cookie = login.response.headers.get('set-cookie').split(';', 1)[0];
  const csrf = login.payload.data.csrfToken;

  const missingCsrf = await read(await fetch(`${base}/api/admin/users`, {
    method: 'POST',
    headers: { cookie, 'content-type': 'application/json' },
    body: JSON.stringify({ name: 'Michael' }),
  }));
  assert.equal(missingCsrf.response.status, 403);
  assert.equal(missingCsrf.payload.error.code, 'invalid_csrf');

  const create = () => fetch(`${base}/api/admin/users`, {
    method: 'POST',
    headers: {
      cookie,
      'x-aiotv-csrf': csrf,
      'content-type': 'application/json',
    },
    body: JSON.stringify({ name: 'Michael' }),
  });
  assert.equal((await create()).status, 201);
  const duplicate = await read(await create());
  assert.equal(duplicate.response.status, 409);
  assert.equal(duplicate.payload.error.code, 'duplicate_user');
});
