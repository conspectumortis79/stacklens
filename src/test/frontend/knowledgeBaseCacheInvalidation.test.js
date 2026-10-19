import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { makeWindow, runInWindow, extractFunction } from './support/fragmentLoader.js';
import { readKnowledgeBundle } from './support/knowledgeModules.js';

const source = readKnowledgeBundle();

function boot() {
  const win = makeWindow(`
    <button id="kbResetConfirmBtn"></button>
    <div id="kbResetConfirmBox"></div>
    <div id="kbReindexResult"></div>
    <span id="kbMetricBases">0</span>
    <span id="kbMetricSources">0</span>
    <div id="kbMetricProvider"></div>
    <div id="kbMetricModel"></div>
    <span id="kbSourceCount">0</span>
    <table><tbody id="kbSourceRows"></tbody></table>
    <span id="kbBaseCount">0</span>
    <div id="kbBaseRows"></div>
  `);
  runInWindow(win, `
    window.i18n = { t: function (k, fallback) { return fallback || k; } };
    function esc(s) { return String(s == null ? '' : s); }
    function t(k, fallback) { return (window.i18n && window.i18n.t) ? window.i18n.t(k, fallback) : (fallback || k); }
    function kbNotify() {}
    function kbCancelReset() {}
    function kbLoadOverview() {}
    function kbLoadSources() {}
    function kbLoadBases() {}
    var bootstrap = { Modal: { getInstance: function () { return null; } } };
  `);
  win.fetch = vi.fn();
  runInWindow(win, extractFunction(source, 'kbInvalidateBasesCache'));
  runInWindow(win, extractFunction(source, 'kbConfirmReset'));
  runInWindow(win, extractFunction(source, 'kbReindexAll'));
  return win;
}

async function flushMicrotasks(win, n = 20) {
  let p = Promise.resolve();
  for (let i = 0; i < n; i++) p = p.then(() => {});
  await p;
}

describe('Wissensdatenbank cache invalidation after server-side mutations', () => {
  let win;
  afterEach(() => { if (win) try { win.close(); } catch (_) {} });

  it('kbConfirmReset invalidates kbBasesCache after a successful reset', async () => {
    win = boot();
    win.kbBasesCache = [{ id: 99, name: 'Stale-Base-That-Should-Not-Reappear' }];
    win.kbBasesInflight = null;
    win.fetch = vi.fn(() => Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ ok: true, resetAt: '2026-01-01T00:00:00Z' }),
    }));

    win.kbConfirmReset();
    await flushMicrotasks(win);

    expect(win.kbBasesCache, 'kbBasesCache must be null after a successful reset').toBe(null);
  });

  it('kbConfirmReset refreshes the Bases tab by calling kbLoadBases after a successful reset', async () => {
    win = boot();
    win.kbBasesCache = [{ id: 99, name: 'Stale-Base' }];
    win.kbBasesInflight = null;
    var loadBasesCalls = 0;
    win.kbLoadBases = function () { loadBasesCalls++; };
    win.fetch = vi.fn(() => Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ ok: true, resetAt: '2026-01-01T00:00:00Z' }),
    }));

    win.kbConfirmReset();
    await flushMicrotasks(win);

    expect(loadBasesCalls, 'kbLoadBases must be called once after a successful reset').toBe(1);
  });

  it('kbReindexAll invalidates kbBasesCache after a successful reindex', async () => {
    win = boot();
    win.kbBasesCache = [{ id: 99, name: 'Stale-Base' }];
    win.kbBasesInflight = null;
    win.fetch = vi.fn(() => Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ ok: true, baseCount: 2, totalChunks: 6, results: { '41': 6 } }),
    }));

    win.kbReindexAll();
    await flushMicrotasks(win);

    expect(win.kbBasesCache, 'kbBasesCache must be null after a successful reindex').toBe(null);
  });

  it('kbConfirmReset does NOT invalidate the cache if the server returns an error', async () => {
    win = boot();
    win.kbBasesCache = [{ id: 99, name: 'Stale-Base' }];
    win.kbBasesInflight = null;
    win.fetch = vi.fn(() => Promise.resolve({
      ok: false,
      text: () => Promise.resolve('HTTP 500'),
    }));

    win.kbConfirmReset();
    await flushMicrotasks(win);

    expect(win.kbBasesCache, 'kbBasesCache must remain intact when the reset fails').not.toBe(null);
    expect(win.kbBasesCache[0].name).toBe('Stale-Base');
  });
});
