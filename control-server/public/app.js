const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

const state = {
  csrfToken: '',
  dashboard: { users: [], pendingPairingCount: 0, recentActivity: [] },
  currentUser: null,
  pendingPairing: null,
};

class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function api(path, options = {}) {
  const method = options.method ?? 'GET';
  const headers = new Headers(options.headers ?? {});
  if (options.body != null) headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD'].includes(method) && state.csrfToken) {
    headers.set('X-AIOtv-CSRF', state.csrfToken);
  }
  const response = await fetch(path, {
    ...options,
    method,
    headers,
    credentials: 'same-origin',
    body: options.body == null ? undefined : JSON.stringify(options.body),
  });
  const payload = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok || payload?.success === false) {
    if (response.status === 401 && path !== '/api/admin/login') showLogin();
    throw new ApiError(
      payload?.error?.message ?? `Request failed with HTTP ${response.status}`,
      response.status,
      payload?.error?.code,
    );
  }
  return payload?.data ?? {};
}

function showLogin() {
  $('#app-view').hidden = true;
  $('#login-view').hidden = false;
  state.csrfToken = '';
  queueMicrotask(() => $('#admin-password').focus());
}

function showApp() {
  $('#login-view').hidden = true;
  $('#app-view').hidden = false;
}

function setBusy(form, busy) {
  $$('button, input, select', form).forEach((control) => { control.disabled = busy; });
}

function setError(target, message = '') {
  target.textContent = message;
}

function toast(message) {
  const item = document.createElement('div');
  item.className = 'toast';
  item.textContent = message;
  $('#toast-region').append(item);
  window.setTimeout(() => item.remove(), 3600);
}

function formatRelativeTime(value) {
  if (!value) return 'Never connected';
  const difference = Date.now() - Date.parse(value);
  const minutes = Math.max(0, Math.floor(difference / 60_000));
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

function makeStatus(enabled, activeLabel = 'Active', disabledLabel = 'Disabled') {
  const status = document.createElement('span');
  status.className = `status-pill${enabled ? '' : ' status-disabled'}`;
  status.textContent = enabled ? activeLabel : disabledLabel;
  return status;
}

function renderDashboard() {
  const { users, pendingPairingCount, recentActivity } = state.dashboard;
  const deviceCount = users.reduce((sum, user) => sum + Number(user.deviceCount), 0);
  const addonCount = users.reduce((sum, user) => sum + Number(user.addonCount), 0);
  $('#user-count').textContent = users.length;
  $('#device-count').textContent = deviceCount;
  $('#addon-count').textContent = addonCount;
  $('#pending-count').textContent = pendingPairingCount;

  const grid = $('#users-grid');
  grid.replaceChildren();
  $('#users-empty').hidden = users.length > 0;
  grid.hidden = users.length === 0;

  for (const user of users) {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'user-card';
    card.dataset.userId = user.id;
    card.setAttribute('aria-label', `Open managed user ${user.name}`);

    const top = document.createElement('div');
    top.className = 'user-card-top';
    const avatar = document.createElement('span');
    avatar.className = 'avatar';
    avatar.textContent = user.name.slice(0, 1).toUpperCase();
    top.append(avatar, makeStatus(user.enabled));

    const heading = document.createElement('h3');
    heading.textContent = user.name;
    const revision = document.createElement('p');
    revision.className = 'muted';
    revision.textContent = `Policy revision ${user.policyRevision}`;

    const meta = document.createElement('div');
    meta.className = 'card-meta';
    const addons = document.createElement('span');
    addons.textContent = `${user.addonCount} addon${Number(user.addonCount) === 1 ? '' : 's'}`;
    const devices = document.createElement('span');
    devices.textContent = `${user.deviceCount} TV${Number(user.deviceCount) === 1 ? '' : 's'}`;
    meta.append(addons, devices);
    card.append(top, heading, revision, meta);
    grid.append(card);
  }

  const activityList = $('#activity-list');
  activityList.replaceChildren();
  $('#activity-empty').hidden = recentActivity.length > 0;
  for (const event of recentActivity) {
    const item = document.createElement('li');
    item.className = 'activity-item';
    const summary = document.createElement('p');
    summary.textContent = event.summary;
    const time = document.createElement('time');
    time.dateTime = event.createdAt;
    time.textContent = formatRelativeTime(event.createdAt);
    item.append(summary, time);
    activityList.append(item);
  }
}

async function loadDashboard() {
  try {
    state.dashboard = await api('/api/admin/dashboard');
    renderDashboard();
    $('#connection-status').replaceChildren(Object.assign(document.createElement('span'), { className: 'status-dot' }), 'Connected');
  } catch (error) {
    if (error.status !== 401) {
      $('#connection-status').textContent = 'Connection problem';
      $('#connection-status').classList.add('status-disabled');
      toast(error.message);
    }
  }
}

function renderUser(user) {
  state.currentUser = user;
  $('#profile-name').textContent = user.name;
  $('#profile-revision').textContent = `Policy revision ${user.policyRevision}`;
  $('#profile-status').replaceWith(Object.assign(makeStatus(user.enabled), { id: 'profile-status' }));
  $('#toggle-user-button').textContent = user.enabled ? 'Disable user' : 'Enable user';

  const addons = $('#profile-addons');
  addons.replaceChildren();
  if (!user.addons.length) {
    const empty = document.createElement('p');
    empty.className = 'managed-empty';
    empty.textContent = 'No addons assigned yet.';
    addons.append(empty);
  }
  for (const addon of user.addons) {
    const row = document.createElement('div');
    row.className = 'managed-row';
    const details = document.createElement('div');
    const name = document.createElement('strong');
    name.textContent = addon.name;
    const url = document.createElement('span');
    url.textContent = addon.manifestUrl;
    url.title = addon.manifestUrl;
    details.append(name, url);
    const remove = document.createElement('button');
    remove.className = 'button button-danger button-small';
    remove.type = 'button';
    remove.dataset.removeAddon = addon.id;
    remove.textContent = 'Remove';
    row.append(details, remove);
    addons.append(row);
  }

  const devices = $('#profile-devices');
  devices.replaceChildren();
  if (!user.devices.length) {
    const empty = document.createElement('p');
    empty.className = 'managed-empty';
    empty.textContent = 'No TVs are paired with this user.';
    devices.append(empty);
  }
  for (const device of user.devices) {
    const row = document.createElement('div');
    row.className = 'managed-row';
    const details = document.createElement('div');
    const name = document.createElement('strong');
    name.textContent = device.name;
    const meta = document.createElement('span');
    meta.textContent = device.revokedAt
      ? `Revoked · ${device.appVersion || 'Unknown app version'}`
      : `${formatRelativeTime(device.lastSeenAt)} · ${device.appVersion || 'Unknown app version'}`;
    details.append(name, meta);
    row.append(details);
    if (!device.revokedAt) {
      const revoke = document.createElement('button');
      revoke.className = 'button button-danger button-small';
      revoke.type = 'button';
      revoke.dataset.revokeDevice = device.id;
      revoke.textContent = 'Revoke';
      row.append(revoke);
    } else {
      row.append(makeStatus(false, '', 'Revoked'));
    }
    devices.append(row);
  }
}

async function openUser(userId) {
  try {
    const user = await api(`/api/admin/users/${userId}`);
    renderUser(user);
    $('#user-dialog').showModal();
  } catch (error) {
    toast(error.message);
  }
}

function formatCode(value) {
  const clean = value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
  return clean.length > 4 ? `${clean.slice(0, 4)}-${clean.slice(4)}` : clean;
}

$('#pair-code').addEventListener('input', (event) => {
  event.target.value = formatCode(event.target.value);
});

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  setError($('#login-error'));
  setBusy(form, true);
  try {
    const session = await api('/api/admin/login', {
      method: 'POST',
      body: { password: new FormData(form).get('password') },
    });
    state.csrfToken = session.csrfToken;
    form.reset();
    showApp();
    await loadDashboard();
  } catch (error) {
    setError($('#login-error'), error.message);
  } finally {
    setBusy(form, false);
  }
});

$('#logout-button').addEventListener('click', async () => {
  try { await api('/api/admin/logout', { method: 'POST' }); } catch {}
  showLogin();
});

function openNewUserDialog() {
  const dialog = $('#new-user-dialog');
  $('#new-user-form').reset();
  setError($('[data-form-error]', dialog));
  dialog.showModal();
  queueMicrotask(() => $('#new-user-name').focus());
}

$('#new-user-button').addEventListener('click', openNewUserDialog);
document.addEventListener('click', (event) => {
  if (event.target.closest('[data-action="new-user"]')) openNewUserDialog();
  const userCard = event.target.closest('[data-user-id]');
  if (userCard) openUser(userCard.dataset.userId);
  const closeButton = event.target.closest('[data-close-dialog]');
  if (closeButton) closeButton.closest('dialog').close();
});

$('#new-user-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const errorTarget = $('[data-form-error]', form);
  setError(errorTarget);
  setBusy(form, true);
  try {
    const user = await api('/api/admin/users', {
      method: 'POST',
      body: { name: new FormData(form).get('name') },
    });
    form.closest('dialog').close();
    toast(`${user.name} was created.`);
    await loadDashboard();
    await openUser(user.id);
  } catch (error) {
    setError(errorTarget, error.message);
  } finally {
    setBusy(form, false);
  }
});

$('#pair-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  setError($('#pair-error'));
  if (!state.dashboard.users.some((user) => user.enabled)) {
    return setError($('#pair-error'), 'Create and enable a managed user before pairing a TV.');
  }
  setBusy(form, true);
  try {
    const code = formatCode(new FormData(form).get('code'));
    const pairing = await api(`/api/admin/pairings/${encodeURIComponent(code)}`);
    state.pendingPairing = pairing;
    $('#pair-request-id').value = pairing.id;
    $('#pair-device-name').value = pairing.requestedName || 'AIOtv device';

    const select = $('#pair-user');
    select.replaceChildren();
    for (const user of state.dashboard.users.filter((item) => item.enabled)) {
      const option = document.createElement('option');
      option.value = user.id;
      option.textContent = user.name;
      select.append(option);
    }

    const summary = $('#pair-device-summary');
    const icon = document.createElement('span');
    icon.className = 'device-summary-icon';
    icon.textContent = 'TV';
    const details = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = pairing.requestedName || 'New AIOtv device';
    const meta = document.createElement('span');
    meta.textContent = `${pairing.platform.replaceAll('_', ' ')} · ${pairing.appVersion || 'Unknown app version'} · requested ${formatRelativeTime(pairing.requestedAt)}`;
    details.append(title, meta);
    summary.replaceChildren(icon, details);
    setError($('[data-form-error]', $('#pair-dialog')));
    $('#pair-dialog').showModal();
  } catch (error) {
    setError($('#pair-error'), error.message);
  } finally {
    setBusy(form, false);
  }
});

$('#pair-approval-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const errorTarget = $('[data-form-error]', form);
  setError(errorTarget);
  setBusy(form, true);
  try {
    const fields = new FormData(form);
    const result = await api(`/api/admin/pairings/${fields.get('pairingId')}/approve`, {
      method: 'POST',
      body: { userId: fields.get('userId'), deviceName: fields.get('deviceName') },
    });
    form.closest('dialog').close();
    $('#pair-form').reset();
    toast(`${result.deviceName} is now assigned to ${result.userName}.`);
    await loadDashboard();
  } catch (error) {
    setError(errorTarget, error.message);
  } finally {
    setBusy(form, false);
  }
});

$('#add-addon-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  setError($('#addon-error'));
  setBusy(form, true);
  try {
    const fields = new FormData(form);
    await api(`/api/admin/users/${state.currentUser.id}/addons`, {
      method: 'POST',
      body: { manifestUrl: fields.get('manifestUrl'), name: fields.get('name') },
    });
    form.reset();
    toast('Addon assigned.');
    const user = await api(`/api/admin/users/${state.currentUser.id}`);
    renderUser(user);
    await loadDashboard();
  } catch (error) {
    setError($('#addon-error'), error.message);
  } finally {
    setBusy(form, false);
  }
});

$('#profile-addons').addEventListener('click', async (event) => {
  const button = event.target.closest('[data-remove-addon]');
  if (!button) return;
  if (!window.confirm('Remove this addon from every TV assigned to this user?')) return;
  button.disabled = true;
  try {
    await api(`/api/admin/users/${state.currentUser.id}/addons/${button.dataset.removeAddon}`, { method: 'DELETE' });
    toast('Addon removed.');
    const user = await api(`/api/admin/users/${state.currentUser.id}`);
    renderUser(user);
    await loadDashboard();
  } catch (error) {
    toast(error.message);
    button.disabled = false;
  }
});

$('#profile-devices').addEventListener('click', async (event) => {
  const button = event.target.closest('[data-revoke-device]');
  if (!button) return;
  if (!window.confirm('Revoke this TV? It will require a new pairing code.')) return;
  button.disabled = true;
  try {
    await api(`/api/admin/devices/${button.dataset.revokeDevice}/revoke`, { method: 'POST' });
    toast('TV access revoked.');
    const user = await api(`/api/admin/users/${state.currentUser.id}`);
    renderUser(user);
    await loadDashboard();
  } catch (error) {
    toast(error.message);
    button.disabled = false;
  }
});

$('#toggle-user-button').addEventListener('click', async (event) => {
  const button = event.currentTarget;
  button.disabled = true;
  try {
    const user = await api(`/api/admin/users/${state.currentUser.id}`, {
      method: 'PATCH',
      body: { enabled: !state.currentUser.enabled },
    });
    renderUser(user);
    toast(`${user.name} is now ${user.enabled ? 'enabled' : 'disabled'}.`);
    await loadDashboard();
  } catch (error) {
    toast(error.message);
  } finally {
    button.disabled = false;
  }
});

async function initialise() {
  try {
    const session = await api('/api/admin/session');
    state.csrfToken = session.csrfToken;
    showApp();
    await loadDashboard();
  } catch (error) {
    if (error.status !== 401) setError($('#login-error'), 'AIOtv Control is currently unavailable.');
    showLogin();
  }
}

initialise();
