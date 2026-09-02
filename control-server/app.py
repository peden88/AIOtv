import hashlib
import os
import secrets
import string
from datetime import datetime, timedelta, timezone
from html import escape
from typing import Optional

from fastapi import FastAPI, Form, Header, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from itsdangerous import BadSignature, URLSafeSerializer
from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text, create_engine, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, relationship

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql+psycopg://aiotv:aiotv@db:5432/aiotv")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "")
SESSION_SECRET = os.getenv("SESSION_SECRET", "")
PUBLIC_URL = os.getenv("PUBLIC_URL", "https://aiocontrol.peden88.stream").rstrip("/")
PAIRING_TTL_MINUTES = int(os.getenv("PAIRING_TTL_MINUTES", "15"))

if not ADMIN_PASSWORD:
    raise RuntimeError("ADMIN_PASSWORD must be set")
if len(SESSION_SECRET) < 32:
    raise RuntimeError("SESSION_SECRET must be at least 32 characters")

engine = create_engine(DATABASE_URL, pool_pre_ping=True)
signer = URLSafeSerializer(SESSION_SECRET, salt="aiotv-admin-session")


class Base(DeclarativeBase):
    pass


class AddonGroup(Base):
    __tablename__ = "addon_groups"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(120), unique=True)
    addons: Mapped[list["Addon"]] = relationship(back_populates="group", cascade="all, delete-orphan")


class Addon(Base):
    __tablename__ = "addons"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    group_id: Mapped[int] = mapped_column(ForeignKey("addon_groups.id", ondelete="CASCADE"))
    name: Mapped[str] = mapped_column(String(160))
    manifest_url: Mapped[str] = mapped_column(Text)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    sort_order: Mapped[int] = mapped_column(Integer, default=0)
    group: Mapped[AddonGroup] = relationship(back_populates="addons")


class ManagedUser(Base):
    __tablename__ = "managed_users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(120), unique=True)
    group_id: Mapped[Optional[int]] = mapped_column(ForeignKey("addon_groups.id"), nullable=True)
    group: Mapped[Optional[AddonGroup]] = relationship()


class Device(Base):
    __tablename__ = "devices"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    device_id: Mapped[str] = mapped_column(String(200), unique=True)
    device_name: Mapped[str] = mapped_column(String(200), default="AIOtv")
    token_hash: Mapped[str] = mapped_column(String(64))
    user_id: Mapped[Optional[int]] = mapped_column(ForeignKey("managed_users.id"), nullable=True)
    paired_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))
    user: Mapped[Optional[ManagedUser]] = relationship()


class PairingRequest(Base):
    __tablename__ = "pairing_requests"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    code: Mapped[str] = mapped_column(String(8), unique=True, index=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id", ondelete="CASCADE"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    completed: Mapped[bool] = mapped_column(Boolean, default=False)
    device: Mapped[Device] = relationship()


Base.metadata.create_all(engine)
app = FastAPI(title="AIOtv Control Server", version="0.1.0")


def now() -> datetime:
    return datetime.now(timezone.utc)


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def make_code() -> str:
    alphabet = stringABCDEFGHJKLMNPQRSTUVWXYZ23456789
    with Session(engine) as db:
        for _ in range(30):
            code = "".join(secrets.choice(alphabet) for _ in range(6))
            if not db.scalar(select(PairingRequest).where(PairingRequest.code == code)):
                return code
    raise RuntimeError("Could not allocate pairing code")


def admin_ok(request: Request) -> bool:
    raw = request.cookies.get("aiotv_admin")
    if not raw:
        return False
    try:
        return signer.loads(raw) == "admin"
    except BadSignature:
        return False


def require_admin(request: Request):
    if not admin_ok(request):
        raise HTTPException(status_code=401)


def card(title: str, body: str) -> str:
    return f'<section class="card"><h2>{escape(title)}</h2>{body}</section>'


def page(title: str, body: str, request: Optional[Request] = None) -> HTMLResponse:
    nav = ""
    if request is not None and admin_ok(request):
        nav = '<nav><a href="/admin">Dashboard</a><a href="/admin/users">Users</a><a href="/admin/groups">Addon groups</a><a href="/admin/devices">Devices</a><a href="/admin/logout">Log out</a></nav>'
    html = f'''<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>{escape(title)} · AIOtv Control</title><style>
    body{{font-family:system-ui,-apple-system,sans-serif;background:#0b0d12;color:#f4f6fb;max-width:1100px;margin:0 auto;padding:24px}}a{{color:#78a9ff}}nav{{display:flex;gap:18px;flex-wrap:wrap;margin:12px 0 24px}}.card{{background:#151922;border:1px solid #252b38;border-radius:14px;padding:18px;margin:14px 0}}input,select,button{{font:inherit;padding:10px 12px;border-radius:9px;border:1px solid #394253;background:#0f131a;color:#fff}}button{{cursor:pointer;background:#2864dc;border-color:#2864dc}}table{{width:100%;border-collapse:collapse}}th,td{{text-align:left;padding:10px;border-bottom:1px solid #2b3240;vertical-align:top}}.muted{{color:#9ba6ba}}.code{{font-size:1.5rem;font-weight:700;letter-spacing:.12em}}form.inline{{display:inline}}label{{display:block;margin:.65rem 0 .25rem}}h1{{margin-bottom:.3rem}}
    </style></head><body><h1>{escape(title)}</h1>{nav}{body}</body></html>'''
    return HTMLResponse(html)


@app.get("/health")
def health():
    with Session(engine) as db:
        db.execute(select(ManagedUser).limit(1))
    return {"status": "ok", "service": "aiotv-control", "version": "0.1.0"}


@app.get("/", response_class=HTMLResponse)
def root(request: Request):
    if admin_ok(request):
        return RedirectResponse("/admin", status_code=303)
    return RedirectResponse("/admin/login", status_code=303)


@app.get("/admin/login", response_class=HTMLResponse)
def login_page(request: Request):
    return page("AIOtv Control", card("Admin sign in", '<form method="post"><label>Password</label><input type="password" name="password" autofocus required> <button type="submit">Sign in</button></form>'))


@app.post("/admin/login")
def login(password: str = Form(...)):
    if not secrets.compare_digest(password, ADMIN_PASSWORD):
        return page("AIOtv Control", card("Sign in failed", '<p>Incorrect password.</p><a href="/admin/login">Try again</a>'))
    response = RedirectResponse("/admin", status_code=303)
    response.set_cookie("aiotv_admin", signer.dumps("admin"), httponly=True, secure=True, samesite="lax", max_age=86400)
    return response


@app.get("/admin/logout")
def logout():
    response = RedirectResponse("/admin/login", status_code=303)
    response.delete_cookie("aiotv_admin")
    return response


@app.get("/admin", response_class=HTMLResponse)
def dashboard(request: Request):
    require_admin(request)
    with Session(engine) as db:
        pending = db.scalars(select(PairingRequest).where(PairingRequest.completed == False, PairingRequest.expires_at > now()).order_by(PairingRequest.created_at.desc())).all()
        users = db.scalars(select(ManagedUser).order_by(ManagedUser.name)).all()
        devices = db.scalars(select(Device).order_by(Device.last_seen_at.desc())).all()
        rows = ""
        for p in pending:
            choices = ''.join(f'<option value="{u.id}">{escape(u.name)}</option>' for u in users)
            rows += f'<tr><td class="code">{escape(p.code)}</td><td>{escape(p.device.device_name)}</td><td>{escape(p.device.device_id)}</td><td><form method="post" action="/admin/pair/{p.id}"><select name="user_id" required><option value="">Choose user…</option>{choices}</select> <button>Assign</button></form></td></tr>'
        if not rows:
            rows = '<tr><td colspan="4" class="muted">No pending pairing codes.</td></tr>'
        body = card("Pending TV logins", f'<table><tr><th>Code</th><th>Device</th><th>Device ID</th><th>Assign to</th></tr>{rows}</table>')
        body += card("Status", f'<p>{len(users)} managed users · {len(devices)} devices</p><p class="muted">TV clients pair through {escape(PUBLIC_URL)}. Addon configuration remains controlled here.</p>')
        return page("Dashboard", body, request)


@app.get("/admin/users", response_class=HTMLResponse)
def users_page(request: Request):
    require_admin(request)
    with Session(engine) as db:
        users = db.scalars(select(ManagedUser).order_by(ManagedUser.name)).all()
        groups = db.scalars(select(AddonGroup).order_by(AddonGroup.name)).all()
        group_opts = ''.join(f'<option value="{g.id}">{escape(g.name)}</option>' for g in groups)
        rows = ""
        for u in users:
            opts = '<option value="">No group</option>' + ''.join(f'<option value="{g.id}" {"selected" if u.group_id == g.id else ""}>{escape(g.name)}</option>' for g in groups)
            rows += f'<tr><td>{escape(u.name)}</td><td><form method="post" action="/admin/users/{u.id}/group"><select name="group_id">{opts}</select> <button>Save</button></form></td></tr>'
        body = card("Create managed user", f'<form method="post"><label>Name</label><input name="name" required> <label>Initial addon group</label><select name="group_id"><option value="">No group</option>{group_opts}</select> <button>Create user</button></form>')
        body += card("Managed users", f'<table><tr><th>User</th><th>Addon group</th></tr>{rows or "<tr><td colspan=2 class=muted>No users yet.</td></tr>"}</table>')
        return page("Users", body, request)


@app.post("/admin/users")
def create_user(request: Request, name: str = Form(...), group_id: str = Form("")):
    require_admin(request)
    with Session(engine) as db:
        db.add(ManagedUser(name=name.strip(), group_id=int(group_id) if group_id else None))
        db.commit()
    return RedirectResponse("/admin/users", status_code=303)


@app.post("/admin/users/{user_id}/group")
def set_user_group(user_id: int, request: Request, group_id: str = Form("")):
    require_admin(request)
    with Session(engine) as db:
        user = db.get(ManagedUser, user_id)
        if not user:
            raise HTTPException(404)
        user.group_id = int(group_id) if group_id else None
        db.commit()
    return RedirectResponse("/admin/users", status_code=303)


@app.get("/admin/groups", response_class=HTMLResponse)
def groups_page(request: Request):
    require_admin(request)
    with Session(engine) as db:
        groups = db.scalars(select(AddonGroup).order_by(AddonGroup.name)).all()
        body = card("Create addon group", '<form method="post"><label>Group name</label><input name="name" required> <button>Create group</button></form>')
        for g in groups:
            addons = ''.join(f'<tr><td>{escape(a.name)}</td><td>{escape(a.manifest_url)}</td><td>{"Yes" if a.enabled else "No"}</td></tr>' for a in sorted(g.addons, key=lambda x: x.sort_order))
            form = f'<form method="post" action="/admin/groups/{g.id}/addons"><label>Addon name</label><input name="name" required><label>Manifest URL</label><input name="manifest_url" style="width:min(680px,95%)" placeholder="https://…/manifest.json" required><label>Order</label><input type="number" name="sort_order" value="0"><br><br><button>Add addon</button></form>'
            body += card(g.name, f'<table><tr><th>Addon</th><th>Manifest URL</th><th>Enabled</th></tr>{addons or "<tr><td colspan=3 class=muted>No addons yet.</td></tr>"}</table>{form}')
        return page("Addon groups", body, request)


@app.post("/admin/groups")
def create_group(request: Request, name: str = Form(...)):
    require_admin(request)
    with Session(engine) as db:
        db.add(AddonGroup(name=name.strip()))
        db.commit()
    return RedirectResponse("/admin/groups", status_code=303)


@app.post("/admin/groups/{group_id}/addons")
def add_addon(group_id: int, request: Request, name: str = Form(...), manifest_url: str = Form(...), sort_order: int = Form(0)):
    require_admin(request)
    with Session(engine) as db:
        if not db.get(AddonGroup, group_id):
            raise HTTPException(404)
        db.add(Addon(group_id=group_id, name=name.strip(), manifest_url=manifest_url.strip(), sort_order=sort_order))
        db.commit()
    return RedirectResponse("/admin/groups", status_code=303)


@app.get("/admin/devices", response_class=HTMLResponse)
def devices_page(request: Request):
    require_admin(request)
    with Session(engine) as db:
        devices = db.scalars(select(Device).order_by(Device.last_seen_at.desc())).all()
        rows = ''.join(f'<tr><td>{escape(d.device_name)}</td><td>{escape(d.device_id)}</td><td>{escape(d.user.name) if d.user else "Unpaired"}</td><td>{d.last_seen_at.strftime("%Y-%m-%d %H:%M UTC")}</td></tr>' for d in devices)
        return page("Devices", card("Registered TVs", f'<table><tr><th>Name</th><th>Device ID</th><th>User</th><th>Last seen</th></tr>{rows or "<tr><td colspan=4 class=muted>No devices yet.</td></tr>"}</table>'), request)


@app.post("/admin/pair/{pairing_id}")
def pair_device(pairing_id: int, request: Request, user_id: int = Form(...)):
    require_admin(request)
    with Session(engine) as db:
        pairing = db.get(PairingRequest, pairing_id)
        user = db.get(ManagedUser, user_id)
        if not pairing or not user or pairing.completed or pairing.expires_at <= now():
            raise HTTPException(400, "Pairing request is invalid or expired")
        pairing.device.user_id = user.id
        pairing.device.paired_at = now()
        pairing.completed = True
        db.commit()
    return RedirectResponse("/admin", status_code=303)


def bearer_token(authorization: Optional[str]) -> str:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(401, "Bearer token required")
    return authorization.split(" ", 1)[1].strip()


def authenticate_device(db: Session, device_id: str, authorization: Optional[str]) -> Device:
    token = bearer_token(authorization)
    device = db.scalar(select(Device).where(Device.device_id == device_id))
    if not device or not secrets.compare_digest(device.token_hash, token_hash(token)):
        raise HTTPException(401, "Invalid device credentials")
    device.last_seen_at = now()
    return device


@app.post("/api/v1/pairing/request")
def pairing_request(payload: dict):
    device_id = str(payload.get("device_id", "")).strip()
    device_name = str(payload.get("device_name", "AIOtv")).strip() or "AIOtv"
    if not device_id:
        raise HTTPException(422, "device_id is required")
    token = secrets.token_urlsafe(32)
    code = make_code()
    expiry = now() + timedelta(minutes=PAIRING_TTL_MINUTES)
    with Session(engine) as db:
        device = db.scalar(select(Device).where(Device.device_id == device_id))
        if not device:
            device = Device(device_id=device_id, device_name=device_name, token_hash=token_hash(token))
            db.add(device)
            db.flush()
        else:
            device.device_name = device_name
            device.token_hash = token_hash(token)
            device.user_id = None
            device.paired_at = None
        old = db.scalars(select(PairingRequest).where(PairingRequest.device_id == device.id, PairingRequest.completed == False)).all()
        for item in old:
            item.completed = True
        db.add(PairingRequest(code=code, device_id=device.id, expires_at=expiry))
        db.commit()
    return {"pairing_code": code, "device_token": token, "expires_in": PAIRING_TTL_MINUTES * 60, "status_url": f"{PUBLIC_URL}/api/v1/pairing/status/{code}"}


@app.get("/api/v1/pairing/status/{code}")
def pairing_status(code: str, device_id: str, authorization: Optional[str] = Header(None)):
    with Session(engine) as db:
        device = authenticate_device(db, device_id, authorization)
        pairing = db.scalar(select(PairingRequest).where(PairingRequest.code == code.upper(), PairingRequest.device_id == device.id))
        if not pairing:
            raise HTTPException(404, "Pairing code not found")
        db.commit()
        if pairing.expires_at <= now() and not pairing.completed:
            return {"status": "expired"}
        if pairing.completed and device.user:
            return {"status": "paired", "user": device.user.name, "config_url": f"{PUBLIC_URL}/api/v1/device/config?device_id={device.device_id}"}
        return {"status": "pending", "expires_at": pairing.expires_at.isoformat()}


@app.get("/api/v1/device/config")
def device_config(device_id: str, authorization: Optional[str] = Header(None)):
    with Session(engine) as db:
        device = authenticate_device(db, device_id, authorization)
        if not device.user:
            raise HTTPException(403, "Device is not paired")
        group = device.user.group
        addons = []
        if group:
            addons = [
                {"id": a.id, "name": a.name, "manifest_url": a.manifest_url, "enabled": a.enabled, "sort_order": a.sort_order}
                for a in sorted(group.addons, key=lambda x: x.sort_order)
                if a.enabled
            ]
        db.commit()
        return {"user": {"id": device.user.id, "name": device.user.name}, "addon_group": {"id": group.id, "name": group.name} if group else None, "addons": addons, "managed": True}


@app.exception_handler(401)
def unauthorized_handler(request: Request, exc):
    if request.url.path.startswith("/admin"):
        return RedirectResponse("/admin/login", status_code=303)
    return JSONResponse({"detail": "Unauthorized"}, status_code=401)
