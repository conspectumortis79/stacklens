import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  installBrowserStubs, resetDom, readSource, loadScript,
} from './_load.js';

installBrowserStubs();

function ensureLocalStorage() {
  if (typeof window.localStorage !== 'undefined' && window.localStorage && typeof window.localStorage.getItem === 'function') {
    return window.localStorage;
  }
  const store = new Map();
  const stub = {
    getItem: function(k) { return store.has(k) ? store.get(k) : null; },
    setItem: function(k, v) { store.set(String(k), String(v)); },
    removeItem: function(k) { store.delete(k); },
    clear: function() { store.clear(); },
    key: function(i) { return Array.from(store.keys())[i] || null; },
    get length() { return store.size; }
  };
  try {
    Object.defineProperty(window, 'localStorage', { value: stub, configurable: true });
  } catch (_) {
    window.localStorage = stub;
  }
  return stub;
}
ensureLocalStorage();

const HISTORY_KEY = 'stacklens.logging.history';

function loadHistoryScripts() {
  loadScript(window, 'js/shared-helpers.js');
  loadScript(window, 'js/history-list.js');
  loadScript(window, 'js/logging-analysis.js');
  const html = readSource('../templates/fragments/logging/scripts-history.html');
  const re = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi;
  const blocks = [];
  let m;
  while ((m = re.exec(html)) !== null) blocks.push(m[1]);
  const code = blocks.join('\n');
  window.eval(code);
}

function setDom() {
  document.body.innerHTML = [
    '<div id="hxCard-logging" class="hx-card"></div>',
    '<div id="lgRestoreToast"></div>',
  ].join('\n');
  window.i18n = { t: function(k) { return k; } };
  try {
    window.confirm = function() { return true; };
  } catch (_) {}
  window.slConfirm = null;
  window.lgAnalyze = function() {};
}

function card() { return document.getElementById('hxCard-logging'); }
function rows() { return card().querySelectorAll('tbody tr.hx-row'); }

describe('logging history (localStorage, unified Verlauf-Baustein)', () => {
  beforeEach(() => {
    resetDom();
    setDom();
    loadHistoryScripts();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  it('exposes history helpers on window', () => {
    expect(typeof window.lgReadHistory).toBe('function');
    expect(typeof window.lgWriteHistory).toBe('function');
    expect(typeof window.lgSaveToHistory).toBe('function');
    expect(typeof window.lgRenderHistory).toBe('function');
    expect(typeof window.lgRestoreFromHistory).toBe('function');
    expect(typeof window.lgDeleteHistoryItem).toBe('function');
    expect(typeof window.lgDeleteAllHistory).toBe('function');
  });

  it('mounts the shared history card and shows the empty state', () => {
    expect(window.lgReadHistory()).toEqual([]);
    window.lgRenderHistory();
    expect(card().classList.contains('hx-card')).toBe(true);
    expect(card().querySelector('[data-hx-empty]').hidden).toBe(false);
    expect(card().querySelector('table').hidden).toBe(true);
    expect(card().querySelector('[data-hx-delall]').hidden).toBe(true);
  });

  it('saves an analysis to history and renders a unified row', () => {
    const result = window.loggingLogScanner.analyzeLogs('2025-01-15 10:00:00 ERROR boom\n2025-01-15 10:00:01 INFO ok');
    window.lgSaveToHistory('2025-01-15 10:00:00 ERROR boom\n2025-01-15 10:00:01 INFO ok', result);
    const list = window.lgReadHistory();
    expect(list).toHaveLength(1);
    expect(list[0].input).toContain('ERROR boom');
    expect(list[0].totalLines).toBe(2);
    expect(list[0].issueLines).toBe(1);
    expect(list[0].bySeverity.error).toBe(1);
    expect(list[0].createdAt).toMatch(/T/);
    expect(rows()).toHaveLength(1);
    expect(card().querySelector('[data-hx-delall]').hidden).toBe(false);
    expect(card().querySelector('.hx-badge').getAttribute('data-status')).toBe('COMPLETED');
  });

  it('keeps most-recent-first ordering and caps at 50 entries', () => {
    for (let i = 0; i < 60; i++) {
      window.lgWriteHistory(window.lgReadHistory().concat([{
        id: 'e-' + i,
        createdAt: new Date(Date.now() + i * 1000).toISOString(),
        input: 'line ' + i,
        totalLines: 1,
        issueLines: 0,
        bySeverity: { critical: 0, error: 0, warn: 0, info: 1 },
        byCategory: {}
      }]));
    }
    const result = window.loggingLogScanner.analyzeLogs('line newest');
    window.lgSaveToHistory('line newest', result);
    const list = window.lgReadHistory();
    expect(list).toHaveLength(50);
    expect(list[0].input).toBe('line newest');
  });

  it('renders the title cell trimmed to roughly 90 chars', () => {
    const longLine = '2025-01-15 ERROR ' + 'x'.repeat(200);
    const result = window.loggingLogScanner.analyzeLogs(longLine);
    window.lgSaveToHistory(longLine, result);
    const title = card().querySelector('.hx-title-text');
    expect(title).toBeTruthy();
    expect(title.textContent.length).toBeLessThanOrEqual(91);
    expect(title.textContent.endsWith('…')).toBe(true);
  });

  it('marks critical severities with a tag and shows the issue count as metric', () => {
    const clean = window.loggingLogScanner.analyzeLogs('INFO a\nINFO b');
    window.lgSaveToHistory('INFO a\nINFO b', clean);
    expect(card().querySelector('.hx-tag')).toBeFalsy();
    expect(card().querySelector('.hx-cell-metric').textContent).toBe('0');

    const crit = window.loggingLogScanner.analyzeLogs('FATAL unrecoverable');
    window.lgSaveToHistory('FATAL unrecoverable', crit);
    const tag = card().querySelector('.hx-tag');
    expect(tag).toBeTruthy();
    expect(tag.textContent).toContain('CRITICAL');
  });

  it('collapses and expands the card through the shared header', () => {
    const result = window.loggingLogScanner.analyzeLogs('ERROR boom');
    window.lgSaveToHistory('ERROR boom', result);
    const head = card().querySelector('[data-hx-toggle]');
    expect(card().classList.contains('is-collapsed')).toBe(false);
    head.dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(card().classList.contains('is-collapsed')).toBe(true);
    expect(head.getAttribute('aria-expanded')).toBe('false');
    head.dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(card().classList.contains('is-collapsed')).toBe(false);
    expect(head.getAttribute('aria-expanded')).toBe('true');
  });

  it('deletes a single history entry', () => {
    const r1 = window.loggingLogScanner.analyzeLogs('ERROR a');
    const r2 = window.loggingLogScanner.analyzeLogs('ERROR b');
    window.lgSaveToHistory('ERROR a', r1);
    window.lgSaveToHistory('ERROR b', r2);
    const firstId = window.lgReadHistory()[1].id;
    window.lgDeleteHistoryItem(firstId);
    const list = window.lgReadHistory();
    expect(list).toHaveLength(1);
    expect(list[0].input).toBe('ERROR b');
  });

  it('clear-all empties the history without confirmation', () => {
    const r = window.loggingLogScanner.analyzeLogs('ERROR boom');
    window.lgSaveToHistory('ERROR boom', r);
    const confirmSpy = vi.spyOn(window, 'confirm');
    const slSpy = vi.fn();
    window.slConfirm = function(_msg, ok) { slSpy(_msg); ok(); };
    window.lgDeleteAllHistory();
    expect(slSpy).not.toHaveBeenCalled();
    expect(confirmSpy).not.toHaveBeenCalled();
    expect(window.lgReadHistory()).toEqual([]);
    expect(rows()).toHaveLength(0);
    confirmSpy.mockRestore();
  });

  it('restoring an entry copies the input back and re-runs analyze', () => {
    const r = window.loggingLogScanner.analyzeLogs('ERROR boom\nINFO ok');
    window.lgSaveToHistory('ERROR boom\nINFO ok', r);
    const id = window.lgReadHistory()[0].id;
    document.getElementById('lgInput') && document.getElementById('lgInput').remove();
    const ta = document.createElement('textarea');
    ta.id = 'lgInput';
    document.body.appendChild(ta);
    window.lgRestoreFromHistory(id);
    expect(ta.value).toBe('ERROR boom\nINFO ok');
  });

  it('opening a row through the shared action triggers the restore', () => {
    const r = window.loggingLogScanner.analyzeLogs('ERROR boom\nINFO ok');
    window.lgSaveToHistory('ERROR boom\nINFO ok', r);
    const ta = document.createElement('textarea');
    ta.id = 'lgInput';
    document.body.appendChild(ta);
    card().querySelector('[data-hx-act="open"]').dispatchEvent(new window.Event('click', { bubbles: true }));
    expect(ta.value).toBe('ERROR boom\nINFO ok');
  });

  it('survives malformed localStorage payloads', () => {
    window.localStorage.setItem(HISTORY_KEY, '{not-json');
    expect(window.lgReadHistory()).toEqual([]);
    window.lgWriteHistory([{ id: 'a', input: 'x' }]);
    window.localStorage.setItem(HISTORY_KEY, '"oops"');
    expect(window.lgReadHistory()).toEqual([]);
  });

  it('does not save empty / whitespace-only input', () => {
    const r = window.loggingLogScanner.analyzeLogs('   ');
    window.lgSaveToHistory('   ', r);
    expect(window.lgReadHistory()).toEqual([]);
  });
});
