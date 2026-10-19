import { describe, it, expect, beforeEach, afterEach } from 'vitest';
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

function extractInlineScripts(html) {
  const re = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi;
  const blocks = [];
  let m;
  while ((m = re.exec(html)) !== null) blocks.push(m[1]);
  return blocks.join('\n');
}

function setDom() {
  document.body.innerHTML = [
    '<textarea id="lgInput"></textarea>',
    '<div id="lgError"></div>',
    '<div id="lgResult"></div>',
    '<div id="lgDonePill"></div>',
    '<div class="lg-summary-badges" id="lgSummaryBadges"></div>',
    '<div class="lg-summary-hint" id="lgSummaryHint" style="display:none;"></div>',
    '<div class="lg-summary-info" id="lgSummaryInfo"></div>',
    '<input type="checkbox" id="lgOnlyIssues">',
    '<div class="lg-log-view-wrap"><div id="lgLogView"></div><div class="sl-scroll-track"><div class="sl-scroll-thumb" id="lgLogViewThumb"></div></div></div>',
    '<div id="lgLimitNote"></div>',
    '<div id="hxCard-logging" class="hx-card"></div>',
    '<div id="lgRestoreToast"></div>',
  ].join('\n');
  window.i18n = { t: function(k) { return k; } };
  window.SLScroll = function() { return { update: function() {} }; };
  try { window.confirm = function() { return true; }; } catch (_) {}
  window.slConfirm = null;
  if (!Element.prototype.scrollIntoView) {
    Element.prototype.scrollIntoView = function() {};
  }
  if (typeof window.escapeHtml !== 'function') {
    window.escapeHtml = function(s) {
      return String(s === null || s === undefined ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    };
  }
}

function loadInlineAndHistoryScripts() {
  loadScript(window, 'js/shared-helpers.js');
  loadScript(window, 'js/history-list.js');
  loadScript(window, 'js/logging-analysis.js');
  const loggingHtml = readSource('../templates/logging.html');
  const historyHtml = readSource('../templates/fragments/logging/scripts-history.html');
  const code = extractInlineScripts(loggingHtml) + '\n' + extractInlineScripts(historyHtml);
  window.eval(code);
}

describe('lgRestoreFromHistory must not duplicate the history', () => {
  beforeEach(() => {
    resetDom();
    setDom();
    loadInlineAndHistoryScripts();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  it('does not save a new history entry when restoring an existing one', () => {
    const r = window.loggingLogScanner.analyzeLogs('ERROR boom\nINFO ok');
    window.lgSaveToHistory('ERROR boom\nINFO ok', r);
    expect(window.lgReadHistory()).toHaveLength(1);
    const id = window.lgReadHistory()[0].id;

    window.lgRestoreFromHistory(id);

    expect(document.getElementById('lgInput').value).toBe('ERROR boom\nINFO ok');
    expect(window.lgReadHistory()).toHaveLength(1);
    expect(window.lgReadHistory()[0].id).toBe(id);
  });

  it('keeps the original entry unchanged when restored multiple times', () => {
    const r = window.loggingLogScanner.analyzeLogs('ddsa');
    window.lgSaveToHistory('ddsa', r);
    expect(window.lgReadHistory()).toHaveLength(1);
    const originalId = window.lgReadHistory()[0].id;
    const originalCreatedAt = window.lgReadHistory()[0].createdAt;

    for (let i = 0; i < 3; i++) {
      window.lgRestoreFromHistory(originalId);
    }

    const list = window.lgReadHistory();
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe(originalId);
    expect(list[0].createdAt).toBe(originalCreatedAt);
  });

  it('still saves a new entry when the user clicks Analysieren on modified content', () => {
    const r = window.loggingLogScanner.analyzeLogs('ERROR boom');
    window.lgSaveToHistory('ERROR boom', r);
    expect(window.lgReadHistory()).toHaveLength(1);

    const ta = document.getElementById('lgInput');
    ta.value = 'ERROR boom modified';
    window.lgAnalyze();

    expect(window.lgReadHistory()).toHaveLength(2);
  });

  it('still saves when called directly via lgAnalyze with no opts (default behaviour)', () => {
    const ta = document.getElementById('lgInput');
    ta.value = 'ERROR fresh';
    window.lgAnalyze();

    expect(window.lgReadHistory()).toHaveLength(1);
    expect(window.lgReadHistory()[0].input).toBe('ERROR fresh');
  });
});
