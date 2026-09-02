# AIOtv Control

AIOtv Control is the standalone administrator dashboard and device API for the managed AIOtv distribution. It deliberately has no AIOStreams account dependency.

## First-build capabilities

- Eight-character, single-use TV pairing codes with a separate 256-bit device credential.
- Administrator-created managed users and per-user addon groups.
- Multiple independently revocable TVs per managed user.
- Authoritative bootstrap policies with revisions and ETags.
- Automatic addon manifest name lookup, with an administrator override.
- SQLite/WAL persistence, audit events, rate limiting, CSRF protection and secure administrator cookies.
- A responsive dashboard served by the same container as the API.

The visible pairing code never authorises a device by itself. The TV retains the private device credential and polls until an administrator assigns the pending request.

## Local run

Requires Node.js 24 or later; the project has no third-party runtime packages.

```bash
cp .env.example .env
npm run hash-password -- "choose a long administrator password"
```

Place the resulting hash and a random session secret of at least 32 characters in `.env`, then export the values or use Docker Compose.

```bash
docker compose up --build -d
```

The Compose example joins `pangolin_frontend`, so Pangolin can target `http://aiotv-control:3000`. It also binds `127.0.0.1:3010` for local diagnostics. Keep the device API and dashboard on the same HTTPS origin. Dashboard routes enforce their own administrator session; device routes accept only pairing or device credentials.

## Required environment

| Variable | Purpose |
| --- | --- |
| `AIOTV_PUBLIC_URL` | Exact external HTTPS origin, without a trailing slash. |
| `AIOTV_ADMIN_PASSWORD_HASH` | Scrypt hash produced by `npm run hash-password`. |
| `AIOTV_SESSION_SECRET` | Random value of at least 32 characters used to sign administrator sessions. |
| `AIOTV_DATABASE_PATH` | SQLite path; defaults to `/app/data/aiotv-control.sqlite` in Compose. |
| `AIOTV_COOKIE_SECURE` | Keep `true` behind Pangolin HTTPS. |
| `AIOTV_ALLOW_HTTP_ADDONS` | Development-only escape hatch; keep `false` in production. |

## API summary

### TV

- `POST /api/v1/pairings`
- `POST /api/v1/pairings/token`
- `GET /api/v1/device/bootstrap`

### Administrator

- `POST /api/admin/login`
- `GET /api/admin/dashboard`
- `POST/PATCH /api/admin/users`
- `POST/DELETE /api/admin/users/:id/addons`
- `GET/POST /api/admin/pairings/:code|:id/approve`
- `PATCH /api/admin/devices/:id`
- `POST /api/admin/devices/:id/revoke`

Run the integration tests with `npm test`.
