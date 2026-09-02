import {
  createHash,
  createHmac,
  randomBytes,
  scryptSync,
  timingSafeEqual,
} from 'node:crypto';

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

export function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

export function randomToken(bytes = 32) {
  return randomBytes(bytes).toString('base64url');
}

export function randomUserCode() {
  let code = '';
  const bytes = randomBytes(8);
  for (const byte of bytes) code += CODE_ALPHABET[byte % CODE_ALPHABET.length];
  return `${code.slice(0, 4)}-${code.slice(4)}`;
}

export function normalizeUserCode(value) {
  return String(value ?? '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
}

export function createPasswordHash(password) {
  const salt = randomBytes(16);
  const derived = scryptSync(password, salt, 64);
  // Dots avoid Docker Compose treating hash separators as environment
  // interpolation markers when the value is stored in a .env file.
  return `scrypt.${salt.toString('base64url')}.${derived.toString('base64url')}`;
}

export function verifyPassword(password, encodedHash, plainPassword = '') {
  if (encodedHash) {
    const separator = encodedHash.startsWith('scrypt.') ? '.' : '$';
    const [algorithm, saltValue, expectedValue] = encodedHash.split(separator);
    if (algorithm !== 'scrypt' || !saltValue || !expectedValue) return false;
    try {
      const salt = Buffer.from(saltValue, 'base64url');
      const expected = Buffer.from(expectedValue, 'base64url');
      const actual = scryptSync(password, salt, expected.length);
      return expected.length === actual.length && timingSafeEqual(expected, actual);
    } catch {
      return false;
    }
  }

  const expected = Buffer.from(plainPassword);
  const actual = Buffer.from(String(password ?? ''));
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

function sign(value, secret) {
  return createHmac('sha256', secret).update(value).digest('base64url');
}

export function createAdminSession(secret, lifetimeSeconds, now = new Date()) {
  const payload = {
    exp: Math.floor(now.getTime() / 1000) + lifetimeSeconds,
    nonce: randomToken(18),
  };
  const encoded = Buffer.from(JSON.stringify(payload)).toString('base64url');
  return `${encoded}.${sign(encoded, secret)}`;
}

export function readAdminSession(token, secret, now = new Date()) {
  if (!token) return null;
  const [encoded, signature] = token.split('.');
  if (!encoded || !signature) return null;
  const expected = Buffer.from(sign(encoded, secret));
  const actual = Buffer.from(signature);
  if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) return null;
  try {
    const payload = JSON.parse(Buffer.from(encoded, 'base64url').toString('utf8'));
    if (!payload.nonce || payload.exp <= Math.floor(now.getTime() / 1000)) return null;
    return payload;
  } catch {
    return null;
  }
}

export function csrfForSession(session, secret) {
  return sign(`csrf:${session.nonce}`, secret);
}

export function parseCookies(header = '') {
  const result = {};
  for (const part of header.split(';')) {
    const separator = part.indexOf('=');
    if (separator < 1) continue;
    const key = part.slice(0, separator).trim();
    const value = part.slice(separator + 1).trim();
    try {
      result[key] = decodeURIComponent(value);
    } catch {
      result[key] = value;
    }
  }
  return result;
}

export function safeEquals(left, right) {
  const a = Buffer.from(String(left ?? ''));
  const b = Buffer.from(String(right ?? ''));
  return a.length === b.length && timingSafeEqual(a, b);
}

export function createRateLimiter() {
  const buckets = new Map();
  return function allow(key, limit, windowMs, nowMs = Date.now()) {
    const current = buckets.get(key);
    if (!current || current.resetAt <= nowMs) {
      buckets.set(key, { count: 1, resetAt: nowMs + windowMs });
      return true;
    }
    current.count += 1;
    if (buckets.size > 10_000) {
      for (const [bucketKey, bucket] of buckets) {
        if (bucket.resetAt <= nowMs) buckets.delete(bucketKey);
      }
    }
    return current.count <= limit;
  };
}
