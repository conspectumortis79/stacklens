import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
export const PROJECT_ROOT = resolve(here, '..', '..', '..');
export const STATIC_DIR = resolve(PROJECT_ROOT, 'src', 'main', 'resources', 'static');

export function readSource(relativePath) {
  const abs = resolve(STATIC_DIR, relativePath);
  return readFileSync(abs, 'utf8');
}

const FRAGMENT_DEPENDENCIES = {
  'js/fragments/pipeline-analysis.js': ['js/page-storage.js'],
  'js/fragments/ticket-refinement.js': ['js/page-storage.js'],
};

export function loadScript(window, relativePath) {
  const dependencies = FRAGMENT_DEPENDENCIES[relativePath];
  if (dependencies) {
    for (const dependency of dependencies) {
      if (!window.PageStorage) loadScript(window, dependency);
    }
  }
  let code = readSource(relativePath);
  code += '\n';
  const seen = new Set();
  for (const m of code.matchAll(/^\s*function\s+([A-Za-z_$][\w$]*)\s*\(/gm)) {
    seen.add(m[1]);
  }
  for (const m of code.matchAll(/^\s*(?:var|let|const)\s+([A-Za-z_$][\w$]*)\s*=\s*function\b/gm)) {
    seen.add(m[1]);
  }
  for (const m of code.matchAll(/^\s*(?:var|let|const)\s+([A-Za-z_$][\w$]*)\s*=[^;]*;\s*$/gm)) {
    seen.add(m[1]);
  }
  for (const name of seen) {
    code += `try { if (typeof ${name} !== 'undefined') window.${name} = ${name}; } catch (_) {}\n`;
  }
  window.eval(code);
}

const _trackedListeners = new Map();
let _realAdd = null;
let _realRemove = null;
let _trackingActive = false;

export function trackDocumentListeners() {
  if (_trackingActive) return;
  _realAdd = document.addEventListener.bind(document);
  _realRemove = document.removeEventListener.bind(document);
  document.addEventListener = function (type, handler, opts) {
    if (!_trackedListeners.has(type)) _trackedListeners.set(type, new Set());
    _trackedListeners.get(type).add(handler);
    return _realAdd(type, handler, opts);
  };
  document.removeEventListener = function (type, handler, opts) {
    const set = _trackedListeners.get(type);
    if (set) set.delete(handler);
    return _realRemove(type, handler, opts);
  };
  _trackingActive = true;
}

export function removeAllTrackedListeners(types) {
  const list = Array.isArray(types) ? types : (types ? [types] : ['DOMContentLoaded', 'app:navigate']);
  for (const t of list) {
    const set = _trackedListeners.get(t);
    if (!set) continue;
    for (const h of Array.from(set)) {
      try { _realRemove ? _realRemove(t, h) : document.removeEventListener(t, h); } catch (_) { void 0; }
    }
    set.clear();
  }
}

export function untrackDocumentListeners() {
  if (!_trackingActive) return;
  removeAllTrackedListeners(['DOMContentLoaded', 'app:navigate', 'click', 'keydown', 'popstate']);
  document.addEventListener = _realAdd;
  document.removeEventListener = _realRemove;
  _realAdd = null;
  _realRemove = null;
  _trackedListeners.clear();
  _trackingActive = false;
}

export function loadScriptIsolated(window, relativePath) {
  trackDocumentListeners();
  removeAllTrackedListeners(['DOMContentLoaded', 'app:navigate']);
  loadScript(window, relativePath);
}

export function installBrowserStubs() {
  if (typeof globalThis.requestAnimationFrame !== 'function') {
    globalThis.requestAnimationFrame = (cb) => setTimeout(() => cb(Date.now()), 0);
    globalThis.cancelAnimationFrame = (id) => clearTimeout(id);
  }
  if (typeof globalThis.fetch !== 'function' && typeof fetch !== 'function') {
    globalThis.fetch = () => Promise.reject(new Error('fetch not stubbed'));
  }
  if (typeof process !== 'undefined' && process.on && !process.__slUnhandledInstalled) {
    process.__slUnhandledInstalled = true;
    process.on('unhandledRejection', () => {});
  }
}

export function resetDom() {
  if (typeof globalThis.localStorage === 'undefined') {
    if (typeof window !== 'undefined' && window.localStorage) {
      globalThis.localStorage = window.localStorage;
    } else if (typeof window !== 'undefined' && window.sessionStorage) {
      globalThis.localStorage = window.sessionStorage;
    } else {
      const store = new Map();
      globalThis.localStorage = {
        getItem: (k) => (store.has(k) ? store.get(k) : null),
        setItem: (k, v) => { store.set(k, String(v)); },
        removeItem: (k) => { store.delete(k); },
        clear: () => { store.clear(); },
        key: (i) => Array.from(store.keys())[i] || null,
        get length() { return store.size; },
      };
    }
  }
  document.documentElement.removeAttribute('lang');
  document.documentElement.removeAttribute('data-theme');
  document.head.innerHTML = '';
  document.body.innerHTML = '';
  if (window.localStorage && window.localStorage.clear) window.localStorage.clear();
  try { if (window.sessionStorage && window.sessionStorage.clear) window.sessionStorage.clear(); } catch (_) { }
  if (window._appVisible === false) window._appVisible = true;
  if (window.__kbActionDelegationWired) delete window.__kbActionDelegationWired;
  if (window.__kbBootstrapCalls) delete window.__kbBootstrapCalls;
  if (window.bootstrap) delete window.bootstrap;
  try {
    if (window.history && window.history.state !== null && window.location && window.location.pathname !== '/') {
      window.history.replaceState(null, '', '/');
    }
  } catch (_) { }
}

export function makeJsonResponse(body, init = {}) {
  const status = init.status ?? 200;
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: init.statusText || 'OK',
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(typeof body === 'string' ? body : JSON.stringify(body))
  };
}

export function makeErrorResponse(status, body = '', statusText = '') {
  return {
    ok: false,
    status,
    statusText: statusText || `HTTP ${status}`,
    json: () => Promise.reject(new Error('no json')),
    text: () => Promise.resolve(body)
  };
}

export async function flushMicrotasks(n = 64) {
  for (let i = 0; i < n; i++) await Promise.resolve();
}

export async function flushUntil(predicate, maxTicks = 200) {
  for (let i = 0; i < maxTicks; i++) {
    if (predicate()) return true;
    await Promise.resolve();
  }
  return predicate();
}
