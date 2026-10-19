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

function loadInlineScript() {
  loadScript(window, 'js/logging-analysis.js');
  window.escapeHtml = function(s) {
    return String(s === null || s === undefined ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  };
  const html = readSource('../templates/logging.html');
  const re = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi;
  const blocks = [];
  let m;
  while ((m = re.exec(html)) !== null) blocks.push(m[1]);
  const code = blocks.join('\n');
  window.eval(code);
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
    '<div class="lg-history-card" id="lgHistoryCard" hidden>',
    '  <div class="lg-acrd-body" id="lgHistoryBody"></div>',
    '  <div id="lgHistoryEmpty"></div>',
    '  <table id="lgHistoryTable"><tbody id="lgHistoryTbody"></tbody></table>',
    '  <div id="lgHistoryCountText"></div>',
    '  <button id="lgBtnDelAll"></button>',
    '</div>',
    '<div id="lgRestoreToast"></div>',
  ].join('\n');
  window.i18n = { t: function(k) { return k; } };
  window.SLScroll = function() { return { update: function() {} }; };
  try { window.confirm = function() { return true; }; } catch (_) {}
  window.slConfirm = null;
  if (!Element.prototype.scrollIntoView) {
    Element.prototype.scrollIntoView = function() {};
  }
}

describe('logging badge filter', () => {
  beforeEach(() => {
    resetDom();
    setDom();
    loadInlineScript();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  function analyze(text) {
    return window.loggingLogScanner.analyzeLogs(text);
  }

  function offAllExcept() {
    var keys = ['critical', 'error', 'warn', 'exception', 'stack', 'oom', 'connection', 'http', 'database', 'timeout', 'deprecated', 'info'];
    var s = {};
    for (var i = 0; i < keys.length; i++) s[keys[i]] = false;
    for (var j = 0; j < arguments.length; j++) s[arguments[j]] = true;
    return s;
  }

  it('all badges are active by default after analyze', () => {
    document.getElementById('lgInput').value = 'ERROR boom\nINFO ok';
    window.lgAnalyze();
    expect(window._lgSelectedBadges).toEqual({});
    const btns = document.querySelectorAll('.lg-summary-badge');
    expect(btns.length).toBeGreaterThan(0);
    btns.forEach(function(b) {
      expect(b.classList.contains('lg-summary-badge-active')).toBe(true);
      expect(b.classList.contains('lg-summary-badge-inactive')).toBe(false);
      expect(b.getAttribute('aria-pressed')).toBe('true');
    });
  });

  it('renders info badge only when info lines exist', () => {
    const r = analyze('ERROR boom\nINFO ok\nINFO ok');
    window._lgLastResult = r;
    window.lgRenderSummary(r);
    window.lgRenderView();
    const cats = Array.from(document.querySelectorAll('.lg-summary-badge')).map(function(b) {
      return b.getAttribute('data-category');
    });
    expect(cats).toContain('info');
    expect(cats).toContain('error');
    expect(cats).not.toContain('critical');
  });

  it('renders the info badge with the correct count for a clean log', () => {
    const r = analyze('INFO a\nINFO b\nINFO c');
    window._lgLastResult = r;
    window.lgRenderSummary(r);
    const badges = document.querySelectorAll('.lg-summary-badge');
    expect(badges.length).toBe(1);
    expect(badges[0].getAttribute('data-category')).toBe('info');
    expect(badges[0].querySelector('.lg-summary-badge-count').textContent).toBe('3');
    expect(document.getElementById('lgSummaryHint').style.display).toBe('');
  });

  it('clicking a badge toggles its active class and re-renders the view', () => {
    document.getElementById('lgInput').value = 'ERROR boom\nINFO ok';
    window.lgAnalyze();
    const errorBtn = document.querySelector('.lg-summary-badge[data-category="error"]');
    expect(errorBtn).toBeTruthy();
    expect(errorBtn.classList.contains('lg-summary-badge-active')).toBe(true);
    const beforeLines = document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)').length;

    window.lgToggleBadge('error');
    expect(errorBtn.classList.contains('lg-summary-badge-active')).toBe(false);
    expect(errorBtn.classList.contains('lg-summary-badge-inactive')).toBe(true);
    expect(errorBtn.getAttribute('aria-pressed')).toBe('false');
    expect(window._lgSelectedBadges.error).toBe(false);

    const afterLines = document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)').length;
    expect(afterLines).toBeLessThan(beforeLines);

    window.lgToggleBadge('error');
    expect(errorBtn.classList.contains('lg-summary-badge-active')).toBe(true);
    expect(document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)').length).toBe(beforeLines);
  });

  it('lgLineMatchesBadges follows the OR-of-categories rule per line', () => {
    const r = window.loggingLogScanner.analyzeLogs([
      '2025-01-15 ERROR com.example.X - boom',
      'java.lang.NullPointerException: x',
      '    at com.example.Y.foo(Y.java:1)',
      'INFO all good',
    ].join('\n'));
    window._lgLastResult = r;
    const lines = r.lines;
    expect(lines[0].categories).toEqual(['error']);
    expect(lines[1].categories).toEqual(['exception']);
    expect(lines[2].categories).toEqual(['stack']);
    expect(lines[3].categories).toEqual([]);
    expect(window.lgLineMatchesBadges(lines[0])).toBe(true);
    expect(window.lgLineMatchesBadges(lines[1])).toBe(true);
    expect(window.lgLineMatchesBadges(lines[2])).toBe(true);
    expect(window.lgLineMatchesBadges(lines[3])).toBe(true);

    window._lgSelectedBadges = offAllExcept();
    expect(window.lgLineMatchesBadges(lines[0])).toBe(false);
    expect(window.lgLineMatchesBadges(lines[1])).toBe(false);
    expect(window.lgLineMatchesBadges(lines[2])).toBe(false);
    expect(window.lgLineMatchesBadges(lines[3])).toBe(false);

    window._lgSelectedBadges = offAllExcept('exception');
    expect(window.lgLineMatchesBadges(lines[0])).toBe(false);
    expect(window.lgLineMatchesBadges(lines[1])).toBe(true);
    expect(window.lgLineMatchesBadges(lines[2])).toBe(false);
    expect(window.lgLineMatchesBadges(lines[3])).toBe(false);

    window._lgSelectedBadges = offAllExcept('info');
    expect(window.lgLineMatchesBadges(lines[3])).toBe(true);
    expect(window.lgLineMatchesBadges(lines[0])).toBe(false);
  });

  it('a line with multiple categories stays visible when any of its categories is active', () => {
    const r = window.loggingLogScanner.analyzeLogs([
      '2025-01-15 10:00:00.000 FATAL java.lang.RuntimeException: boom and segfault',
    ].join('\n'));
    window._lgLastResult = r;
    const line = r.lines[0];
    expect(line.categories).toContain('critical');
    expect(line.categories).toContain('exception');

    window._lgSelectedBadges = offAllExcept();
    expect(window.lgLineMatchesBadges(line)).toBe(false);

    window._lgSelectedBadges = offAllExcept('exception');
    expect(window.lgLineMatchesBadges(line)).toBe(true);

    window._lgSelectedBadges = offAllExcept('critical');
    expect(window.lgLineMatchesBadges(line)).toBe(true);

    window._lgSelectedBadges = offAllExcept('critical', 'exception');
    expect(window.lgLineMatchesBadges(line)).toBe(true);
  });

  it('"only issues" stays orthogonal to the badge filter', () => {
    const r = window.loggingLogScanner.analyzeLogs([
      'INFO something happened',
      'INFO another info',
      'ERROR boom'
    ].join('\n'));
    window._lgLastResult = r;
    window.lgRenderView();
    document.getElementById('lgOnlyIssues').checked = true;
    window.lgRenderView();
    const visible = document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)');
    expect(visible.length).toBe(1);
    expect(visible[0].classList.contains('lg-sev-error')).toBe(true);

    document.getElementById('lgOnlyIssues').checked = false;
    window.lgRenderView();
    expect(document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)').length).toBe(3);
  });

  it('"only issues" + filter combine: only the intersection is visible', () => {
    const r = window.loggingLogScanner.analyzeLogs([
      'INFO something happened',
      'WARN heads up',
      'ERROR boom',
      '2025-01-15 10:00:00.000 FATAL unrecoverable',
    ].join('\n'));
    window._lgLastResult = r;
    window._lgSelectedBadges = offAllExcept('warn');
    document.getElementById('lgOnlyIssues').checked = true;
    window.lgRenderView();
    const visible = document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)');
    expect(visible.length).toBe(1);
    expect(visible[0].classList.contains('lg-sev-warn')).toBe(true);

    document.getElementById('lgOnlyIssues').checked = false;
    window._lgSelectedBadges = offAllExcept('warn', 'critical');
    window.lgRenderView();
    const v2 = document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)');
    expect(v2.length).toBe(2);
  });

  it('lgClear resets the badge filter state', () => {
    document.getElementById('lgInput').value = 'ERROR boom\nINFO ok';
    window.lgAnalyze();
    window.lgToggleBadge('error');
    window.lgToggleBadge('warn');
    expect(window._lgSelectedBadges.error).toBe(false);
    expect(window._lgSelectedBadges.warn).toBe(false);

    window.lgClear();
    expect(window._lgSelectedBadges).toEqual({});
  });

  it('multiple badges can be toggled independently and combine as OR', () => {
    const r = window.loggingLogScanner.analyzeLogs([
      'ERROR com.example.X - boom',
      'WARN com.example.Y - heads up',
      'INFO all good'
    ].join('\n'));
    window._lgLastResult = r;
    window.lgRenderView();
    expect(document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)').length).toBe(3);

    window._lgSelectedBadges = offAllExcept('warn', 'info');
    window.lgRenderView();
    const visible = document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)');
    expect(visible.length).toBe(2);
    expect(visible[0].classList.contains('lg-sev-warn')).toBe(true);
    expect(visible[1].classList.contains('lg-sev-info')).toBe(true);

    window._lgSelectedBadges = offAllExcept('error', 'info');
    window.lgRenderView();
    const visible2 = document.querySelectorAll('#lgLogView .lg-line:not(.lg-hidden)');
    expect(visible2.length).toBe(2);
    expect(visible2[0].classList.contains('lg-sev-error')).toBe(true);
    expect(visible2[1].classList.contains('lg-sev-info')).toBe(true);
  });

  it('toggling a non-rendered category still updates state', () => {
    document.getElementById('lgInput').value = 'ERROR boom\nINFO ok';
    window.lgAnalyze();
    window.lgToggleBadge('oom');
    expect(window._lgSelectedBadges.oom).toBe(false);
    expect(Object.prototype.hasOwnProperty.call(window._lgSelectedBadges, 'oom')).toBe(true);
  });
});
