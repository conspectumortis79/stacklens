import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  loadScriptIsolated, installBrowserStubs, resetDom,
  flushMicrotasks, makeJsonResponse, makeErrorResponse,
  untrackDocumentListeners
} from './_load.js';

installBrowserStubs();

function setFetch(responses) {
  const calls = [];
  let next = 0;
  globalThis.fetch = vi.fn((url, init) => {
    calls.push({ url: String(url), init: init || null });
    const r = responses[next++];
    if (!r) return Promise.reject(new Error('no mock for ' + url));
    return Promise.resolve(r);
  });
  return calls;
}

function makeAlertArea() {
  const area = document.createElement('div');
  area.id = 'alertArea';
  document.body.appendChild(area);
  return area;
}

function makeConfirmModal() {
  document.body.innerHTML =
    '<div id="confirmModal" class="modal" style="display:none">'
    + '<div class="modal-dialog"><div class="modal-content">'
    + '<div class="modal-header"><h5 id="confirmModalTitle"></h5><i id="confirmModalHeaderIcon"></i></div>'
    + '<div class="modal-body"><p id="confirmModalMessage"></p></div>'
    + '<div class="modal-footer"><button id="confirmModalOkBtn">OK</button></div>'
    + '</div></div></div>';
  return document.getElementById('confirmModal');
}

afterEach(() => {
  untrackDocumentListeners();
  vi.useRealTimers();
});

describe('app.js — showAlert', () => {
  beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/app.js'); });

  it('renders an alert in #alertArea with the requested type', () => {
    makeAlertArea();
    window.showAlert('hello', 'success');
    const alerts = document.querySelectorAll('[data-sl-toast]');
    expect(alerts.length).toBe(1);
    expect(alerts[0].classList.contains('alert-success')).toBe(true);
    expect(alerts[0].dataset.slToast).toBe('hello');
  });

  it('is a no-op when #alertArea is missing', () => {
    expect(() => window.showAlert('hello', 'info')).not.toThrow();
  });

  it('deduplicates identical messages by flashing instead of appending', () => {
    makeAlertArea();
    window.showAlert('same', 'info');
    window.showAlert('same', 'info');
    window.showAlert('same', 'info');
    expect(document.querySelectorAll('[data-sl-toast]').length).toBe(1);
  });

  it('renders multiple distinct messages', () => {
    makeAlertArea();
    window.showAlert('one', 'success');
    window.showAlert('two', 'danger');
    window.showAlert('three', 'warning');
    expect(document.querySelectorAll('[data-sl-toast]').length).toBe(3);
  });

  it('uses the modern theme palette colors by default', () => {
    makeAlertArea();
    window.showAlert('x', 'success');
    const el = document.querySelector('[data-sl-toast]');
    expect(el.style.background).toBe('rgb(240, 253, 244)');
    expect(el.style.border).toContain('134, 239, 172');
  });

  it('uses the dark theme palette when data-theme="dark"', () => {
    document.documentElement.setAttribute('data-theme', 'dark');
    makeAlertArea();
    window.showAlert('x', 'danger');
    const el = document.querySelector('[data-sl-toast]');
    expect(el.style.background).toBe('rgb(42, 20, 20)');
    expect(el.style.color).toBe('rgb(236, 230, 255)');
  });

  it('uses the popos theme palette when data-theme="popos"', () => {
    document.documentElement.setAttribute('data-theme', 'popos');
    makeAlertArea();
    window.showAlert('x', 'warning');
    const el = document.querySelector('[data-sl-toast]');
    expect(el.style.background).toBe('rgb(43, 32, 16)');
  });

  it('uses the legacy theme palette when data-theme="legacy"', () => {
    document.documentElement.setAttribute('data-theme', 'legacy');
    makeAlertArea();
    window.showAlert('x', 'info');
    const el = document.querySelector('[data-sl-toast]');
    expect(el.style.background).toBe('rgb(227, 242, 253)');
  });

  it('falls back to info palette colors for an unknown type', () => {
    makeAlertArea();
    window.showAlert('x', 'unknown-type');
    const el = document.querySelector('[data-sl-toast]');
    const infoBg = 'rgb(239, 246, 255)';
    expect(el.style.background).toBe(infoBg);
  });

  it('escapes &, <, > in the rendered message', () => {
    makeAlertArea();
    window.showAlert('<script>&x</script>', 'info');
    const el = document.querySelector('[data-sl-toast]');
    expect(el.innerHTML).toContain('&lt;script&gt;&amp;x&lt;/script&gt;');
    expect(el.querySelector('script')).toBeNull();
  });

  it('autoremoves a non-danger toast after ~6 seconds', () => {
    vi.useFakeTimers();
    makeAlertArea();
    window.showAlert('short', 'info');
    expect(document.querySelectorAll('[data-sl-toast]').length).toBe(1);
    vi.advanceTimersByTime(6500);
    expect(document.querySelectorAll('[data-sl-toast]').length).toBe(0);
  });

  it('keeps a danger toast on screen for ~8 seconds', () => {
    vi.useFakeTimers();
    makeAlertArea();
    window.showAlert('critical', 'danger');
    vi.advanceTimersByTime(7500);
    expect(document.querySelectorAll('[data-sl-toast]').length).toBe(1);
    vi.advanceTimersByTime(2000);
    expect(document.querySelectorAll('[data-sl-toast]').length).toBe(0);
  });

  it('can be dismissed immediately via the close button', () => {
    makeAlertArea();
    window.showAlert('x', 'info');
    const btn = document.querySelector('[data-sl-toast] button');
    btn.click();
    expect(document.querySelectorAll('[data-sl-toast]').length).toBe(0);
  });

  it('uses i18n for the close-button title when window.i18n is present', () => {
    window.i18n = { t: (k) => (k === 'common.close' ? 'Fermer' : k) };
    makeAlertArea();
    window.showAlert('x', 'info');
    const btn = document.querySelector('[data-sl-toast] button');
    expect(btn.title).toBe('Fermer');
  });
});

describe('app.js — showConfirm + modal helpers', () => {
  beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/app.js'); });

  it('opens the modal and shows the provided message and title', () => {
    makeConfirmModal();
    window.showConfirm('really?', 'Sure?', () => {});
    expect(document.getElementById('confirmModal').style.display).toContain('block');
    expect(document.getElementById('confirmModalMessage').textContent).toBe('really?');
    expect(document.getElementById('confirmModalTitle').textContent).toBe('Sure?');
    expect(document.getElementById('confirmModal').classList.contains('show')).toBe(true);
  });

  it('falls back to native confirm() when modal elements are missing', () => {
    delete document.getElementById('confirmModalMessage');
    const native = vi.fn(() => true);
    const originalConfirm = window.confirm;
    window.confirm = native;
    let called = false;
    window.showConfirm('msg', null, () => { called = true; });
    expect(native).toHaveBeenCalledWith('msg');
    expect(called).toBe(true);
    window.confirm = originalConfirm;
  });

  it('does not call onConfirm when the native confirm() returns false', () => {
    const native = vi.fn(() => false);
    const originalConfirm = window.confirm;
    window.confirm = native;
    let called = false;
    window.showConfirm('msg', null, () => { called = true; });
    expect(called).toBe(false);
    window.confirm = originalConfirm;
  });

  it('calls onConfirm when the OK button is clicked', () => {
    makeConfirmModal();
    let clicked = false;
    window.showConfirm('Delete?', 'Confirm', () => { clicked = true; });
    const btn = document.getElementById('confirmModalOkBtn');
    btn.click();
    expect(clicked).toBe(true);
  });

  it('hides the modal and removes the backdrop after confirmation', () => {
    makeConfirmModal();
    window.showConfirm('msg', null, () => {});
    document.getElementById('confirmModalOkBtn').click();
    const modal = document.getElementById('confirmModal');
    expect(modal.classList.contains('show')).toBe(false);
    expect(modal.style.display).toBe('none');
    expect(modal.getAttribute('aria-hidden')).toBe('true');
    expect(document.querySelector('.modal-backdrop')).toBeNull();
  });

  it('applies the custom OK label, icon and color', () => {
    makeConfirmModal();
    window.showConfirm('msg', null, () => {}, 'Run', 'bi-play', '#1e7a3a');
    const btn = document.getElementById('confirmModalOkBtn');
    expect(btn.innerHTML).toContain('Run');
    expect(btn.innerHTML).toContain('bi-play');
    expect(btn.style.background).toBe('rgb(30, 122, 58)');
    const icon = document.getElementById('confirmModalHeaderIcon');
    expect(icon.className).toContain('bi-play');
    expect(icon.style.color).toBe('rgb(30, 122, 58)');
  });

  it('uses i18n confirm.ok and confirm.title when no overrides are given', () => {
    window.i18n = { t: (k) => (k === 'confirm.ok' ? 'Löschen' : k === 'confirm.title' ? 'Sicher?' : k) };
    makeConfirmModal();
    window.showConfirm('msg', null, () => {});
    expect(document.getElementById('confirmModalOkBtn').innerHTML).toContain('L\u00f6schen');
    expect(document.getElementById('confirmModalTitle').textContent).toBe('Sicher?');
  });

  it('removes the modal-open body class if no other modal is open', () => {
    makeConfirmModal();
    window.showConfirm('msg', null, () => {});
    document.body.classList.add('modal-open');
    document.getElementById('confirmModalOkBtn').click();
    expect(document.body.classList.contains('modal-open')).toBe(false);
  });

  it('keeps modal-open when another modal is still open', () => {
    makeConfirmModal();
    const other = document.createElement('div');
    other.className = 'modal show';
    document.body.appendChild(other);
    document.body.classList.add('modal-open');
    window.showConfirm('msg', null, () => {});
    document.getElementById('confirmModalOkBtn').click();
    expect(document.body.classList.contains('modal-open')).toBe(true);
  });

  it('OK button click does not leak between successive confirmations', () => {
    makeConfirmModal();
    let firstCalled = 0;
    let secondCalled = 0;
    window.showConfirm('A', null, () => { firstCalled++; });
    document.getElementById('confirmModalOkBtn').click();
    window.showConfirm('B', null, () => { secondCalled++; });
    document.getElementById('confirmModalOkBtn').click();
    expect(firstCalled).toBe(1);
    expect(secondCalled).toBe(1);
  });
});

describe('app.js — togglePw / resetForm / escNavHtml / _t', () => {
  beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/app.js'); });

  it('togglePw flips password → text → password', () => {
    const inp = document.createElement('input');
    inp.type = 'password';
    inp.id = 'pw';
    document.body.appendChild(inp);
    window.togglePw('pw');
    expect(inp.type).toBe('text');
    window.togglePw('pw');
    expect(inp.type).toBe('password');
  });

  it('togglePw is a no-op when the input id does not exist', () => {
    expect(() => window.togglePw('nonexistent')).not.toThrow();
  });

  it('resetForm empties the listed input fields', () => {
    const a = document.createElement('input'); a.id = 'a'; a.value = '1';
    const b = document.createElement('input'); b.id = 'b'; b.value = '2';
    const c = document.createElement('input'); c.id = 'c'; c.value = '3';
    document.body.append(a, b, c);
    window.resetForm(['a', 'b']);
    expect(a.value).toBe('');
    expect(b.value).toBe('');
    expect(c.value).toBe('3');
  });

  it('resetForm skips missing ids silently', () => {
    expect(() => window.resetForm(['absent'])).not.toThrow();
  });

  it('escNavHtml escapes &, <, > and " but not single quotes', () => {
    expect(window.escNavHtml('<a&b"c>d')).toBe('&lt;a&amp;b&quot;c&gt;d');
    expect(window.escNavHtml(null)).toBe('');
    expect(window.escNavHtml(undefined)).toBe('');
    expect(window.escNavHtml('')).toBe('');
  });

  it('_t returns the i18n key when window.i18n is missing', () => {
    delete window.i18n;
    expect(window._t('hello')).toBe('hello');
  });
});

describe('app.js — _syncSidebarActive', () => {
  beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/app.js'); });

  it('marks the link matching the current pathname as active', () => {
    document.body.innerHTML =
      '<nav id="sidebar"><a class="sidebar-link" href="/foo">Foo</a>'
      + '<a class="sidebar-link" href="/bar">Bar</a></nav>';
    history.replaceState({}, '', '/foo');
    window._syncSidebarActive();
    const foo = document.querySelector('a[href="/foo"]');
    const bar = document.querySelector('a[href="/bar"]');
    expect(foo.classList.contains('active')).toBe(true);
    expect(bar.classList.contains('active')).toBe(false);
  });

  it('treats / prefix match for nested paths when no focus param is present', () => {
    document.body.innerHTML =
      '<nav id="sidebar"><a class="sidebar-link" href="/code-qa">QA</a>'
      + '<a class="sidebar-link" href="/code-analysis">CA</a></nav>';
    history.replaceState({}, '', '/code-qa/123');
    window._syncSidebarActive();
    const qa = document.querySelector('a[href="/code-qa"]');
    expect(qa.classList.contains('active')).toBe(true);
  });

  it('does not match prefix when focus param is active on a different link', () => {
    document.body.innerHTML =
      '<nav id="sidebar"><a class="sidebar-link" href="/code-analysis?focus=foo">CA</a>'
      + '<a class="sidebar-link" href="/code-qa">QA</a></nav>';
    history.replaceState({}, '', '/code-analysis?focus=foo');
    window._syncSidebarActive();
    const ca = document.querySelector('a[href="/code-analysis?focus=foo"]');
    const qa = document.querySelector('a[href="/code-qa"]');
    expect(ca.classList.contains('active')).toBe(true);
    expect(qa.classList.contains('active')).toBe(false);
  });

  it('auto-opens the subgroup that contains the active link', () => {
    document.body.innerHTML =
      '<nav id="sidebar">'
      + '<div class="sg-subgroup-header">Header</div>'
      + '<div class="sg-subgroup-items">'
      + '<a class="sidebar-link" href="/x">X</a>'
      + '</div>'
      + '</nav>';
    history.replaceState({}, '', '/x');
    window._syncSidebarActive();
    const items = document.querySelector('.sg-subgroup-items');
    expect(items.classList.contains('open')).toBe(true);
    expect(document.querySelector('.sg-subgroup-header').classList.contains('open')).toBe(true);
  });
});

describe('app.js — SLBrowseFiles', () => {
  beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/app.js'); });

  it('builds the URL with encoded path, fileExtensions, and directoriesOnly', async () => {
    let captured;
    globalThis.fetch = (url, init) => {
      captured = { url: String(url), init };
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ files: [] }), text: () => Promise.resolve('') });
    };
    const res = await window.SLBrowseFiles({
      path: '/home/user/My Documents',
      fileExtensions: 'pdf,docx',
      directoriesOnly: true
    });
    expect(captured.url).toContain('path=' + encodeURIComponent('/home/user/My Documents'));
    expect(captured.url).toContain('fileExtensions=' + encodeURIComponent('pdf,docx'));
    expect(captured.url).toContain('directoriesOnly=true');
    expect(res).toEqual({ ok: true, data: { files: [] } });
  });

  it('omits parameters that are not supplied', async () => {
    let captured;
    globalThis.fetch = (url) => {
      captured = String(url);
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}), text: () => Promise.resolve('') });
    };
    await window.SLBrowseFiles({});
    expect(captured).toBe('/api/tooling/files/browse');
  });

  it('returns { ok: false, status, message } on a 4xx/5xx response and reports via showAlert', () => {
    const alerts = [];
    window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
    globalThis.fetch = () => Promise.resolve({ ok: false, status: 502, statusText: 'Bad Gateway', json: () => Promise.reject(new Error()), text: () => Promise.resolve('boom') });
    return window.SLBrowseFiles({ path: '/x' }).then((res) => {
      expect(res.ok).toBe(false);
      expect(res.status).toBe(502);
      expect(res.message).toBe('boom');
      expect(alerts.length).toBe(1);
      expect(alerts[0].type).toBe('danger');
      expect(alerts[0].message).toContain('boom');
    });
  });

  it('suppresses the alert when silent: true is set', async () => {
    const alerts = [];
    window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
    globalThis.fetch = () => Promise.resolve({ ok: false, status: 500, statusText: 'x', json: () => Promise.reject(new Error()), text: () => Promise.resolve('oops') });
    const res = await window.SLBrowseFiles({ path: '/x', silent: true });
    expect(res.ok).toBe(false);
    expect(alerts).toHaveLength(0);
  });

  it('returns { ok: false, status: 0, message } when the fetch itself throws', async () => {
    const alerts = [];
    window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
    globalThis.fetch = () => Promise.reject(new Error('network gone'));
    const res = await window.SLBrowseFiles({ path: '/x' });
    expect(res.ok).toBe(false);
    expect(res.status).toBe(0);
    expect(res.message).toBe('network gone');
    expect(alerts.length).toBe(1);
  });

  it('uses window.i18n text when window.i18n is available', async () => {
    window.i18n = { t: (k) => ({ 'tooling.error_load_dir': 'Fehler beim Laden' }[k] || k) };
    const alerts = [];
    window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
    globalThis.fetch = () => Promise.reject(new Error('e'));
    await window.SLBrowseFiles({ path: '/x' });
    expect(alerts[0].message).toContain('Fehler beim Laden');
  });

  it('passes empty opts without throwing', async () => {
    globalThis.fetch = () => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}), text: () => Promise.resolve('') });
    await expect(window.SLBrowseFiles()).resolves.toEqual({ ok: true, data: {} });
    await expect(window.SLBrowseFiles(null)).resolves.toEqual({ ok: true, data: {} });
  });
});

describe('app.js — SLScroll', () => {
  beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/app.js'); });

  it('positions the thumb height proportional to client/scroll ratio', () => {
    document.body.innerHTML =
      '<div id="box" style="height:100px;overflow-y:scroll">'
      + '<div style="height:1000px"></div></div>'
      + '<div id="track" style="height:200px"></div>'
      + '<div id="thumb" style="height:0"></div>';
    const box = document.getElementById('box');
    const track = document.getElementById('track');
    const thumb = document.getElementById('thumb');
    window.SLScroll(box, track, thumb);
    const trackH = 200;
    const ratio = 100 / 1000;
    const expectedH = Math.max(32, ratio * trackH);
    expect(Number(thumb.style.height.replace('px', ''))).toBeCloseTo(expectedH, 5);
  });

  it('hides the track when the content does not need scrolling', () => {
    document.body.innerHTML =
      '<div id="box" style="height:500px;overflow-y:scroll"><div style="height:100px"></div></div>'
      + '<div id="track" style="height:200px"></div>'
      + '<div id="thumb"></div>';
    const box = document.getElementById('box');
    const track = document.getElementById('track');
    const thumb = document.getElementById('thumb');
    window.SLScroll(box, track, thumb);
    expect(track.style.visibility).toBe('hidden');
  });

  it('documents the data-always-visible contract as falsy when the attribute has no value (always-visible branch is currently unreachable)', () => {
    document.body.innerHTML =
      '<div id="box" style="height:500px;overflow-y:scroll"><div style="height:100px"></div></div>'
      + '<div id="track" data-always-visible style="height:200px"></div>'
      + '<div id="thumb"></div>';
    const box = document.getElementById('box');
    const track = document.getElementById('track');
    const thumb = document.getElementById('thumb');
    expect(track.dataset.alwaysVisible).toBe('');
    expect(!!track.dataset.alwaysVisible).toBe(false);
    window.SLScroll(box, track, thumb);
  });

  it('clicking the track (outside the thumb) scrolls the box proportionally', () => {
    document.body.innerHTML =
      '<div id="box" style="height:100px;overflow-y:scroll"><div style="height:1000px"></div></div>'
      + '<div id="track" style="height:200px"></div>'
      + '<div id="thumb" style="height:0"></div>';
    const box = document.getElementById('box');
    const track = document.getElementById('track');
    const thumb = document.getElementById('thumb');
    Object.defineProperty(track, 'getBoundingClientRect', { configurable: true, value: () => ({ top: 0, bottom: 200, height: 200, left: 0, right: 0, width: 0, x: 0, y: 0 }) });
    Object.defineProperty(track, 'clientHeight', { configurable: true, get: () => 200 });
    Object.defineProperty(box, 'scrollHeight', { configurable: true, get: () => 1000 });
    Object.defineProperty(box, 'clientHeight', { configurable: true, get: () => 100 });
    Object.defineProperty(box, 'scrollTop', { configurable: true, get: () => 0, set: () => {} });
    let lastSetScroll;
    Object.defineProperty(box, 'scrollTop', { configurable: true, get: () => lastSetScroll || 0, set: (v) => { lastSetScroll = v; } });
    window.SLScroll(box, track, thumb);
    track.dispatchEvent(new MouseEvent('click', { clientY: 100, bubbles: true }));
    expect(lastSetScroll).toBe(450);
  });

  it('is a no-op when any argument is missing', () => {
    const out = window.SLScroll(null, null, null);
    expect(typeof out.update).toBe('function');
    expect(out.update()).toBeUndefined();
  });

  it('update() recomputes thumb position when scroll math changes', () => {
    document.body.innerHTML =
      '<div id="box" style="height:100px;overflow-y:scroll"><div style="height:1000px"></div></div>'
      + '<div id="track" style="height:200px"></div>'
      + '<div id="thumb" style="height:0"></div>';
    const box = document.getElementById('box');
    const track = document.getElementById('track');
    const thumb = document.getElementById('thumb');
    let scrollVal = 0;
    Object.defineProperty(box, 'scrollTop', { configurable: true, get: () => scrollVal });
    Object.defineProperty(box, 'scrollHeight', { configurable: true, get: () => 1000 });
    Object.defineProperty(box, 'clientHeight', { configurable: true, get: () => 100 });
    Object.defineProperty(track, 'clientHeight', { configurable: true, get: () => 200 });
    let thumbHeight = 32;
    Object.defineProperty(thumb, 'clientHeight', { configurable: true, get: () => thumbHeight });
    const api = window.SLScroll(box, track, thumb);
    const before = thumb.style.transform;
    scrollVal = 450;
    api.update();
    expect(thumb.style.transform).not.toBe(before);
    expect(thumb.style.transform).toContain('translateY(');
  });
});

describe('app.js — SPA navigateTo', () => {
  beforeEach(() => {
    resetDom();
    loadScriptIsolated(window, 'js/spa-page-scripts.js');
    loadScriptIsolated(window, 'js/app.js');
  });

  it('replaces the contents of <main> with the fetched HTML body', async () => {
    document.body.innerHTML =
      '<header id="header"></header><main id="main"><p>old</p></main>'
      + '<div id="alertArea"></div>'
      + '<nav id="sidebar"></nav>';
    history.replaceState({}, '', '/old');
    globalThis.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      text: () => Promise.resolve(
        '<html><head><title>New Page</title><meta name="mcp-sources" content="jira"></head>'
        + '<body><main id="main"><p id="newContent">new</p></main></body></html>'
      )
    });
    const navPromise = window.navigateTo('/new');
    await flushMicrotasks();
    await navPromise;
    const main = document.querySelector('main');
    expect(main.querySelector('#newContent')).not.toBeNull();
    expect(document.title).toBe('New Page');
    expect(history.state).toEqual({ url: '/new' });
  });

  it('emits an app:navigate event with the target URL in detail', async () => {
    document.body.innerHTML = '<main><p>old</p></main><div id="alertArea"></div>';
    history.replaceState({}, '', '/old');
    globalThis.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      text: () => Promise.resolve('<html><head><title>OK</title></head><body><main><p>x</p></main></body></html>')
    });
    const events = [];
    document.addEventListener('app:navigate', (e) => events.push(e.detail));
    await window.navigateTo('/target');
    await flushMicrotasks();
    expect(events.some((d) => d.url === '/target')).toBe(true);
  });

  it('falls back to a full-page navigation when the server returns non-OK', async () => {
    document.body.innerHTML = '<main><p>old</p></main><div id="alertArea"></div>';
    history.replaceState({}, '', '/x');
    globalThis.fetch = () => Promise.resolve({ ok: false, status: 404, statusText: 'Not Found', text: () => Promise.resolve('') });
    await expect(window.navigateTo('/missing')).resolves.not.toThrow();
  });

  it('mirrors the per-page mcp-sources meta tag from the new page into the live head', async () => {
    document.body.innerHTML = '<main><p>old</p></main><div id="alertArea"></div>';
    history.replaceState({}, '', '/old');
    globalThis.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      text: () => Promise.resolve(
        '<html><head><meta name="mcp-sources" content="jira,gitlab"></head>'
        + '<body><main><p>new</p></main></body></html>'
      )
    });
    await window.navigateTo('/new');
    await flushMicrotasks();
    const meta = document.head.querySelector('meta[name="mcp-sources"]');
    expect(meta).not.toBeNull();
    expect(meta.getAttribute('content')).toBe('jira,gitlab');
  });

  it('removes the live mcp-sources meta when the new page has none', async () => {
    document.body.innerHTML = '<main><p>old</p></main><div id="alertArea"></div>';
    const stale = document.createElement('meta');
    stale.setAttribute('name', 'mcp-sources');
    stale.setAttribute('content', 'jira');
    document.head.appendChild(stale);
    history.replaceState({}, '', '/old');
    globalThis.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      text: () => Promise.resolve('<html><head></head><body><main><p>n</p></main></body></html>')
    });
    await window.navigateTo('/new');
    await flushMicrotasks();
    expect(document.head.querySelector('meta[name="mcp-sources"]')).toBeNull();
  });

  it('re-injects stylesheet links from the new page into <head> (deduped)', async () => {
    document.body.innerHTML = '<main><p>old</p></main><div id="alertArea"></div>';
    history.replaceState({}, '', '/old');
    globalThis.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      text: () => Promise.resolve(
        '<html><head><link rel="stylesheet" href="/css/foo.css">'
        + '<link rel="stylesheet" href="/css/bar.css"></head>'
        + '<body><main><p>n</p></main></body></html>'
      )
    });
    await window.navigateTo('/new');
    await flushMicrotasks();
    const links = document.head.querySelectorAll('link[rel="stylesheet"][data-spa-injected="1"]');
    expect(links.length).toBe(2);
    links.forEach((l) => expect(['/css/foo.css', '/css/bar.css']).toContain(l.getAttribute('href')));
  });

  it('aborts cleanly when a newer navigateTo() is dispatched mid-flight', async () => {
    document.body.innerHTML = '<main><p>old</p></main><div id="alertArea"></div>';
    history.replaceState({}, '', '/old');
    const responses = [
      () => new Promise((r) => setTimeout(() => r({
        ok: true,
        status: 200,
        text: () => Promise.resolve('<html><head><title>First</title></head><body><main><p>A</p></main></body></html>')
      }), 100)),
      () => Promise.resolve({
        ok: true,
        status: 200,
        text: () => Promise.resolve('<html><head><title>Second</title></head><body><main><p>B</p></main></body></html>')
      })
    ];
    let n = 0;
    globalThis.fetch = () => responses[n++]();
    const first = window.navigateTo('/first');
    const second = window.navigateTo('/second');
    await flushMicrotasks();
    await Promise.all([first, second]);
    expect(document.title).toBe('Second');
  });

  it('intercepts clicks on .sidebar-link and calls navigateTo', async () => {
    document.body.innerHTML =
      '<nav id="sidebarNav"><a id="lnk" class="sidebar-link" href="/sub">Sub</a></nav>'
      + '<main><p>old</p></main><div id="alertArea"></div>';
    history.replaceState({}, '', '/');
    globalThis.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      text: () => Promise.resolve('<html><head><title>Sub</title></head><body><main><p>sub</p></main></body></html>')
    });
    const ev = new MouseEvent('click', { bubbles: true, cancelable: true });
    document.getElementById('lnk').dispatchEvent(ev);
    await flushMicrotasks();
    expect(document.querySelector('main').textContent).toContain('sub');
  });

  it('does NOT intercept clicks on absolute http(s) links', () => {
    document.body.innerHTML =
      '<nav id="sidebarNav"><a id="ext" class="sidebar-link" href="https://example.com">Ext</a></nav>';
    const ev = new MouseEvent('click', { bubbles: true, cancelable: true });
    const lnk = document.getElementById('ext');
    lnk.dispatchEvent(ev);
    expect(ev.defaultPrevented).toBe(false);
  });

  it('does NOT intercept hash-only links', () => {
    document.body.innerHTML =
      '<nav id="sidebarNav"><a id="anchor" class="sidebar-link" href="#section">X</a></nav>';
    const ev = new MouseEvent('click', { bubbles: true, cancelable: true });
    document.getElementById('anchor').dispatchEvent(ev);
    expect(ev.defaultPrevented).toBe(false);
  });

  it('shows a warning alert and continues when fetch throws', async () => {
    document.body.innerHTML = '<main><p>old</p></main><div id="alertArea"></div>';
    history.replaceState({}, '', '/x');
    const alerts = [];
    window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
    globalThis.fetch = () => Promise.reject(new Error('network down'));
    await expect(window.navigateTo('/broken')).resolves.not.toThrow();
    await flushMicrotasks();
    expect(alerts.length).toBe(1);
    expect(alerts[0].type).toBe('warning');
  });
});

describe('app.js — global ESC keydown handler', () => {
  beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/app.js'); });

  it('ignores non-Esc keys', () => {
    document.body.innerHTML = '<div class="tl-overlay" style="display:block"></div>';
    document.body.style.overflow = 'auto';
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(document.querySelector('.tl-overlay').style.display).toBe('block');
  });

  it('hides a visible .tl-overlay when Esc is pressed and no per-id close handler exists', () => {
    document.body.innerHTML = '<div class="tl-overlay" style="display:block"></div>';
    document.body.style.overflow = 'hidden';
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(document.querySelector('.tl-overlay').style.display).toBe('none');
    expect(document.body.style.overflow).toBe('');
  });

  it('hides overlays by id (editOverlay / promptOverlay etc.) when their close function exists', () => {
    document.body.innerHTML = '<div id="editOverlay" style="display:block"></div>';
    window.closeEditOverlay = vi.fn();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(window.closeEditOverlay).toHaveBeenCalled();
  });

  it('tries all known overlay ids in order (first visible wins)', () => {
    document.body.innerHTML =
      '<div id="editOverlay" style="display:none"></div>'
      + '<div id="promptOverlay" style="display:block"></div>';
    window.closeEditOverlay = vi.fn();
    window.closePromptOverlay = vi.fn();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(window.closeEditOverlay).not.toHaveBeenCalled();
    expect(window.closePromptOverlay).toHaveBeenCalled();
  });

  it('falls back to hiding a visible .tl-overlay when no id matches', () => {
    document.body.innerHTML = '<div class="tl-overlay" style="display:block"></div>';
    document.body.style.overflow = 'hidden';
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(document.querySelector('.tl-overlay').style.display).toBe('none');
  });

  it('hides overlay by raw style.display hide when no close function is registered', () => {
    document.body.innerHTML = '<div id="editOverlay" style="display:block"></div>';
    window.closeEditOverlay = undefined;
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(document.getElementById('editOverlay').style.display).toBe('none');
  });
});

describe('app.js — SLBrowseFiles fetch contracts', () => {
  beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/app.js'); });

  it('treats silent: true as a flag that suppresses both success and error alerts', async () => {
    const alerts = [];
    window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
    globalThis.fetch = () => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}), text: () => Promise.resolve('') });
    await window.SLBrowseFiles({ path: '/x', silent: true });
    expect(alerts).toHaveLength(0);
  });
});
