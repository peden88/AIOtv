# AIOtv Control

AIOtv Control is the standalone administrator dashboard and device API for the managed AIOtv distribution. It deliberately has no AIOStreams account dependency.

## First-build capabilities

- Eight-character, single-use TV pairing codes with a separate 256-bit device credential.
- Administrator-created users assigned to reusable addon groups.
- Ordered group resources containing addon manifests and uploaded AIOtv collection JSON files.
- Automatic enforcement of addon membership, addon order and collections on paired TVs.
- Reassignment and deletion controls for users, groups and individual resources.
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

SQLite data is kept in the named `aiotv-control-data` Docker volume so container upgrades do not discard users or pairings. Back up that volume before a host migration.

## Required environment

| Variable | Purpose |
| --- | --- |
| `AIOTV_PUBLIC_URL` | Exact external HTTPS origin, without a trailing slash. |
| `AIOTV_ADMIN_PASSWORD_HASH` | Scrypt hash produced by `npm run hash-password`. |
| `AIOTV_SESSION_SECRET` | Random value of at least 32 characters used to sign administrator sessions. |
| `AIOTV_DATABASE_PATH` | SQLite path; defaults to `/app/data/aiotv-control.sqlite` in Compose. |
| `AIOTV_COOKIE_SECURE` | Keep `true` behind Pangolin HTTPS. |
| `AIOTV_ALLOW_HTTP_ADDONS` | Development-only escape hatch; keep `false` in production. |

## Android build

The production fallback is `https://aiocontrol.peden88.stream`. Local builds can override it with `AIOTV_CONTROL_URL` in `local.properties`; GitHub Actions reads the repository variable of the same name. The setting is compiled into the APK and should not include a trailing slash.

## Dockhand deployment

Use `compose.dockhand.yaml` as a new Dockhand stack and keep **Re-pull images** enabled. It deploys `ghcr.io/peden88/aiotv-control:test`, persists SQLite in the named `aiotv-control-data` volume, joins the existing `pangolin_frontend` network, and exposes a loopback-only diagnostic port at `127.0.0.1:3010`.

Before creating the stack, generate the two values that are intentionally absent from Compose. Generate the password hash on a trusted machine with Node.js 24 and this repository checked out:

```bash
read -rsp 'AIOtv Control admin password: ' AIOTV_SETUP_PASSWORD; echo
printf '%s' "$AIOTV_SETUP_PASSWORD" | npm --prefix control-server run hash-password -- --stdin
unset AIOTV_SETUP_PASSWORD
openssl rand -base64 48
```

Add the first command's `scrypt.…` output to the Dockhand stack environment as `AIOTV_ADMIN_PASSWORD_HASH`. Add the random second output as `AIOTV_SESSION_SECRET`. Do not put the plain administrator password in Compose.

In Pangolin, create the `aiocontrol.peden88.stream` resource with target `http://aiotv-control:3000`. Do not enable whole-host Pangolin SSO: the TV-facing `/api/v1/*` routes must remain reachable without a browser login. The dashboard still requires its own signed administrator session.

After deployment, confirm the container is healthy and verify:

```bash
curl --fail https://aiocontrol.peden88.stream/health
```

The response should report `aiotv-control` with status `ok`. Then open the public URL, sign in, create an addon group, add its resources, create a managed user and assign the group before installing the TV build.

## API summary

### TV

- `POST /api/v1/pairings`
- `POST /api/v1/pairings/token`
- `GET /api/v1/device/bootstrap`

### Administrator

- `POST /api/admin/login`
- `GET /api/admin/dashboard`
- `POST /api/admin/users`
- `GET/PATCH/DELETE /api/admin/users/:id`
- `POST /api/admin/groups`
- `GET/PATCH/DELETE /api/admin/groups/:id`
- `POST /api/admin/groups/:id/addons`
- `POST /api/admin/groups/:id/collections`
- `PUT /api/admin/groups/:id/resources/order`
- `DELETE /api/admin/groups/:id/resources/:resourceId`
- `GET/POST /api/admin/pairings/:code|:id/approve`
- `PATCH /api/admin/devices/:id`
- `POST /api/admin/devices/:id/revoke`

Run the integration tests with `npm test`.
