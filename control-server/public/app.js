const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const MAX_COLLECTION_BYTES = 10_000_000;

const state = {
  csrfToken: '',
  dashboard: { users: [], groups: [], pendingPairingCount: 0, recentActivity: [] },
  currentUser: null,
  currentGroup: null,
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
  if (!['GET', 'HEAD'].includes(method) && state.csrfToken) headers.set('X-AIOtv-CSRF', state.csrfToken);
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
  // Disabled inputs and selects are excluded from FormData. Only lock the
  // submit controls so every form can still read its values while awaiting
  // the request.
  form.querySelectorAll('button').forEach((control) => { control.disabled = busy; });
  form.setAttribute('aria-busy', String(busy));
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
  return `${Math.floor(hours / 24)}d ago`;
}

function truncateMiddle(value, maximum = 72) {
  if (value.length <= maximum) return value;
  const side = Math.floor((maximum - 1) / 2);
  return `${value.slice(0, side)}…${value.slice(-side)}`;
}

function makeStatus(enabled, activeLabel = 'Active', disabledLabel = 'Disabled') {
  const status = document.createElement('span');
  status.className = `status-pill${enabled ? '' : ' status-disabled'}`;
  status.textContent = enabled ? activeLabel : disabledLabel;
  return status;
}

function fillGroupSelect(select, selectedId = '', includeUnassigned = true) {
  select.replaceChildren();
  if (includeUnassigned) {
    const option = document.createElement('option');
    option.value = '';
    option.textContent = 'No addon group';
    select.append(option);
  }
  for (const group of state.dashboard.groups) {
    const option = document.createElement('option');
    option.value = group.id;
    option.textContent = group.name;
    option.selected = group.id === selectedId;
    select.append(option);
  }
}

function renderUsers(users) {
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
    const group = document.createElement('p');
    group.className = 'muted card-group-name';
    group.textContent = user.groupName || 'No addon group';

    const meta = document.createElement('div');
    meta.className = 'card-meta';
    const resources = document.createElement('span');
    resources.textContent = `${Number(user.addonCount) + Number(user.collectionCount)} resources`;
    const devices = document.createElement('span');
    devices.textContent = `${user.deviceCount} TV${Number(user.deviceCount) === 1 ? '' : 's'}`;
    meta.append(resources, devices);
    card.append(top, heading, group, meta);
    grid.append(card);
  }
}

function renderGroups(groups) {
  const grid = $('#groups-grid');
  grid.replaceChildren();
  $('#groups-empty').hidden = groups.length > 0;
  grid.hidden = groups.length === 0;
  for (const group of groups) {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'user-card group-card';
    card.dataset.groupId = group.id;
    card.setAttribute('aria-label', `Open addon group ${group.name}`);

    const top = document.createElement('div');
    top.className = 'user-card-top';
    const avatar = document.createElement('span');
    avatar.className = 'avatar';
    avatar.textContent = 'G';
    top.append(avatar, makeStatus(Number(group.userCount) > 0, `${group.userCount} assigned`, 'Unassigned'));

    const heading = document.createElement('h3');
    heading.textContent = group.name;
    const summary = document.createElement('p');
    summary.className = 'muted';
    summary.textContent = `${group.addonCount} addon${Number(group.addonCount) === 1 ? '' : 's'} · ${group.collectionCount} collection file${Number(group.collectionCount) === 1 ? '' : 's'}`;
    const meta = document.createElement('div');
    meta.className = 'card-meta';
    meta.textContent = `Updated ${formatRelativeTime(group.updatedAt)}`;
    card.append(top, heading, summary, meta);
    grid.append(card);
  }
}

function renderDashboard() {
  const { users, groups, pendingPairingCount, recentActivity } = state.dashboard;
  const deviceCount = users.reduce((sum, user) => sum + Number(user.deviceCount), 0);
  const resourceCount = groups.reduce(
    (sum, group) => sum + Number(group.addonCount) + Number(group.collectionCount),
    0,
  );
  $('#user-count').textContent = users.length;
  $('#device-count').textContent = deviceCount;
  $('#group-count').textContent = groups.length;
  $('#resource-count').textContent = resourceCount;
  $('#pending-count').textContent = pendingPairingCount;
  renderUsers(users);
  renderGroups(groups);

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
    $('#connection-status').replaceChildren(
      Object.assign(document.createElement('span'), { className: 'status-dot' }),
      'Connected',
    );
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
  fillGroupSelect($('#profile-group'), user.groupId);

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
    details.className = 'managed-details';
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

function renderGroup(group) {
  state.currentGroup = group;
  $('#group-name').textContent = group.name;
  $('#group-user-count').textContent = `${group.userCount} user${Number(group.userCount) === 1 ? '' : 's'}`;
  $('#group-resource-count').textContent = `${group.resources.length} managed resource${group.resources.length === 1 ? '' : 's'}`;

  const list = $('#group-resources');
  list.replaceChildren();
  if (!group.resources.length) {
    const empty = document.createElement('p');
    empty.className = 'managed-empty';
    empty.textContent = 'No addons or collections are assigned yet.';
    list.append(empty);
  }
  group.resources.forEach((resource, index) => {
    const row = document.createElement('div');
    row.className = 'managed-row resource-row';
    const details = document.createElement('div');
    details.className = 'managed-details';
    const heading = document.createElement('div');
    heading.className = 'resource-heading';
    const badge = document.createElement('span');
    badge.className = `resource-badge resource-badge-${resource.type}`;
    badge.textContent = resource.type === 'addon' ? 'Addon' : 'Collections';
    const name = document.createElement('strong');
    name.textContent = resource.name;
    heading.append(badge, name);
    const meta = document.createElement('span');
    if (resource.type === 'addon') {
      meta.textContent = truncateMiddle(resource.manifestUrl);
      meta.title = resource.manifestUrl;
    } else {
      meta.textContent = `${resource.collectionCount} collection${resource.collectionCount === 1 ? '' : 's'} · ${Math.max(1, Math.round(resource.byteSize / 1024))} KB`;
    }
    details.append(heading, meta);

    const actions = document.createElement('div');
    actions.className = 'resource-actions';
    const up = document.createElement('button');
    up.type = 'button';
    up.className = 'button button-quiet button-small';
    up.dataset.moveResource = resource.id;
    up.dataset.direction = 'up';
    up.textContent = '↑';
    up.title = 'Move up';
    up.disabled = index === 0;
    const down = document.createElement('button');
    down.type = 'button';
    down.className = 'button button-quiet button-small';
    down.dataset.moveResource = resource.id;
    down.dataset.direction = 'down';
    down.textContent = '↓';
    down.title = 'Move down';
    down.disabled = index === group.resources.length - 1;
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'button button-danger button-small';
    remove.dataset.removeResource = resource.id;
    remove.textContent = 'Delete';
    actions.append(up, down, remove);
    row.append(details, actions);
    list.append(row);
  });
}

async function openGroup(groupId) {
  try {
    const group = await api(`/api/admin/groups/${groupId}`);
    renderGroup(group);
    $('#group-dialog').showModal();
  } catch (error) {
    toast(error.message);
  }
}

function formatCode(value) {
  const clean = value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8);
  return clean.length > 4 ? `${clean.slice(0, 4)}-${clean.slice(4)}` : clean;
}

function openNewUserDialog() {
  const dialog = $('#new-user-dialog');
  $('#new-user-form').reset();
  fillGroupSelect($('#new-user-group'));
  setError($('[data-form-error]', dialog));
  dialog.showModal();
  queueMicrotask(() => $('#new-user-name').focus());
}

function openNewGroupDialog() {
  const dialog = $('#new-group-dialog');
  $('#new-group-form').reset();
  setError($('[data-form-error]', dialog));
  dialog.showModal();
  queueMicrotask(() => $('#new-group-name').focus());
}

function showPage(targetId) {
  $$('.dashboard-page').forEach((page) => { page.hidden = page.id !== targetId; });
  $$('[data-page-target]').forEach((button) => {
    button.classList.toggle('is-active', button.dataset.pageTarget === targetId);
  });
}

$('#pair-code').addEventListener('input', (event) => { event.target.value = formatCode(event.target.value); });
$('#new-user-button').addEventListener('click', openNewUserDialog);
$('#new-group-button').addEventListener('click', openNewGroupDialog);

document.addEventListener('click', (event) => {
  const target = event.target.closest('[data-page-target]');
  if (target) showPage(target.dataset.pageTarget);
  if (event.target.closest('[data-action="new-user"]')) openNewUserDialog();
  if (event.target.closest('[data-action="new-group"]')) openNewGroupDialog();
  const userCard = event.target.closest('[data-user-id]');
  if (userCard) openUser(userCard.dataset.userId);
  const groupCard = event.target.closest('[data-group-id]');
  if (groupCard) openGroup(groupCard.dataset.groupId);
  const closeButton = event.target.closest('[data-close-dialog]');
  if (closeButton) closeButton.closest('dialog').close();
});

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const password = new FormData(form).get('password');
  setError($('#login-error'));
  setBusy(form, true);
  try {
    const session = await api('/api/admin/login', {
      method: 'POST',
      body: { password },
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

$('#new-user-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const errorTarget = $('[data-form-error]', form);
  setError(errorTarget);
  setBusy(form, true);
  try {
    const fields = new FormData(form);
    const user = await api('/api/admin/users', {
      method: 'POST',
      body: { name: fields.get('name'), groupId: fields.get('groupId') },
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

$('#new-group-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const errorTarget = $('[data-form-error]', form);
  setError(errorTarget);
  setBusy(form, true);
  try {
    const group = await api('/api/admin/groups', {
      method: 'POST',
      body: { name: new FormData(form).get('name') },
    });
    form.closest('dialog').close();
    toast(`${group.name} was created.`);
    await loadDashboard();
    showPage('groups-page');
    await openGroup(group.id);
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
  const availableUsers = state.dashboard.users.filter((user) => user.enabled && user.groupId);
  if (!availableUsers.length) {
    return setError($('#pair-error'), 'Create an enabled user and assign an addon group before pairing a TV.');
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
    for (const user of availableUsers) {
      const option = document.createElement('option');
      option.value = user.id;
      option.textContent = `${user.name} · ${user.groupName}`;
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

$('#assign-group-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  setError($('#assign-group-error'));
  setBusy(form, true);
  try {
    const user = await api(`/api/admin/users/${state.currentUser.id}`, {
      method: 'PATCH',
      body: { groupId: new FormData(form).get('groupId') },
    });
    renderUser(user);
    toast(`${user.name}'s addon group was updated.`);
    await loadDashboard();
  } catch (error) {
    setError($('#assign-group-error'), error.message);
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
    await api(`/api/admin/groups/${state.currentGroup.id}/addons`, {
      method: 'POST',
      body: { manifestUrl: fields.get('manifestUrl'), name: fields.get('name') },
    });
    form.reset();
    toast('Addon added to the group.');
    await loadDashboard();
    renderGroup(await api(`/api/admin/groups/${state.currentGroup.id}`));
  } catch (error) {
    setError($('#addon-error'), error.message);
  } finally {
    setBusy(form, false);
  }
});

$('#add-collection-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  setError($('#collection-error'));
  setBusy(form, true);
  try {
    const fields = new FormData(form);
    const file = fields.get('collectionFile');
    if (!(file instanceof File) || !file.name.toLowerCase().endsWith('.json')) {
      throw new Error('Choose a .json collections file.');
    }
    if (file.size > MAX_COLLECTION_BYTES) throw new Error('Collection file must be 10 MB or smaller.');
    let collectionJson;
    try {
      collectionJson = JSON.parse(await file.text());
    } catch {
      throw new Error('Collection file must contain valid JSON.');
    }
    const fallbackName = file.name.replace(/\.json$/i, '').replaceAll(/[-_]+/g, ' ');
    await api(`/api/admin/groups/${state.currentGroup.id}/collections`, {
      method: 'POST',
      body: {
        name: String(fields.get('name') || fallbackName).trim(),
        collectionJson,
      },
    });
    form.reset();
    toast('Collection file added to the group.');
    await loadDashboard();
    renderGroup(await api(`/api/admin/groups/${state.currentGroup.id}`));
  } catch (error) {
    setError($('#collection-error'), error.message);
  } finally {
    setBusy(form, false);
  }
});

$('#group-resources').addEventListener('click', async (event) => {
  const remove = event.target.closest('[data-remove-resource]');
  const move = event.target.closest('[data-move-resource]');
  if (!remove && !move) return;
  if (remove) {
    if (!window.confirm('Delete this managed resource from every user assigned to the group?')) return;
    remove.disabled = true;
    try {
      await api(`/api/admin/groups/${state.currentGroup.id}/resources/${remove.dataset.removeResource}`, { method: 'DELETE' });
      toast('Managed resource deleted.');
      await loadDashboard();
      renderGroup(await api(`/api/admin/groups/${state.currentGroup.id}`));
    } catch (error) {
      toast(error.message);
      remove.disabled = false;
    }
    return;
  }

  const resources = [...state.currentGroup.resources];
  const index = resources.findIndex((resource) => resource.id === move.dataset.moveResource);
  const nextIndex = move.dataset.direction === 'up' ? index - 1 : index + 1;
  if (index < 0 || nextIndex < 0 || nextIndex >= resources.length) return;
  [resources[index], resources[nextIndex]] = [resources[nextIndex], resources[index]];
  move.disabled = true;
  try {
    const group = await api(`/api/admin/groups/${state.currentGroup.id}/resources/order`, {
      method: 'PUT',
      body: { resourceIds: resources.map((resource) => resource.id) },
    });
    renderGroup(group);
    toast('Resource order updated.');
    await loadDashboard();
  } catch (error) {
    toast(error.message);
    move.disabled = false;
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
    renderUser(await api(`/api/admin/users/${state.currentUser.id}`));
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

$('#delete-user-button').addEventListener('click', async () => {
  const user = state.currentUser;
  if (!window.confirm(`Delete ${user.name} and revoke all of their paired TVs? This cannot be undone.`)) return;
  try {
    await api(`/api/admin/users/${user.id}`, { method: 'DELETE' });
    $('#user-dialog').close();
    toast(`${user.name} was deleted.`);
    await loadDashboard();
  } catch (error) {
    toast(error.message);
  }
});

$('#delete-group-button').addEventListener('click', async () => {
  const group = state.currentGroup;
  const warning = Number(group.userCount) > 0
    ? `Delete ${group.name}? ${group.userCount} assigned user(s) will be left without managed resources.`
    : `Delete ${group.name} and all of its managed resources?`;
  if (!window.confirm(warning)) return;
  try {
    await api(`/api/admin/groups/${group.id}`, { method: 'DELETE' });
    $('#group-dialog').close();
    toast(`${group.name} was deleted.`);
    await loadDashboard();
  } catch (error) {
    toast(error.message);
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
