import { describe, it, expect } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractFunction, extractInlineScripts, makeWindow, readJs, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const LT = resolve(RES, 'templates/fragments/lasttest-analysis');

const PARTS = [
  'scripts.html',
  'scripts-repo-panel.html',
  'scripts-run.html',
  'scripts-helpers.html',
  'scripts-save-history.html',
  'scripts-k6.html',
  'scripts-k6-run.html',
];

function cardHtml(id, name) {
  return '<div class="lt-repo-card" id="ltRepoCard-' + id + '" data-repo-id="' + id + '" data-repo-name="' + name + '" data-repo-path="/repo/' + name + '" data-repo-active="true">' +
    '<div class="lt-repo-name">' + name + '</div><div class="lt-repo-path">/repo/' + name + '</div>' +
    '<div class="lt-card-status"></div>' +
    '<div class="lt-btn-row"><button class="lt-btn-xs lt-btn-xs-success"></button><button class="lt-btn-xs lt-btn-xs-cancel"></button></div>' +
    '</div>';
}

const BODY = [
  '<div id="ltProjectList"></div>',
  '<div id="ltSidebarScrollContent"></div><div id="ltSidebarScrollTrack"></div><div id="ltSidebarScrollThumb"></div>',
  '<div id="ltLocalRepoList">' + cardHtml(3, 'stacklens') + cardHtml(4, 'lasttest') + '</div>',
  '<div id="ltHeader"></div>',
  '<div id="ltTabs" class="lt-tabs d-none"><button class="lt-tab-btn"></button></div>',
  '<div id="ltEmptyState" class="d-none"></div><div id="ltRunning" class="d-none"></div>',
  '<div id="ltAborted" class="d-none"></div><div id="ltError" class="d-none"></div>',
  '<div id="ltSummaryContent" class="d-none"></div>',
  '<div id="ltFindingsContent"></div><div id="ltFindingsBadge"></div>',
  '<div id="ltK6Content"></div><div id="tabBtnLtK6"></div>',
  '<div id="tabLtK6"></div><div id="ltK6ParamsPanel"></div>',
  '<div id="hxCard-lasttest-analysis"></div>',
].join('');

function analysis(status, repoConfigId) {
  return {
    id: 15,
    status,
    repoConfigId: repoConfigId || 3,
    gitlabConfigId: null,
    gitlabProjectId: null,
    gitlabProjectPath: null,
    githubRepoConfigId: null,
    createdAt: '2026-09-03T22:25:25',
    completedAt: status === 'COMPLETED' ? '2026-09-03T22:31:02' : null,
    summaryText: status === 'COMPLETED' ? 'fertig' : null,
    findingsJson: status === 'COMPLETED' ? JSON.stringify([{ id: 'F1', severity: 'LOW', description: 'd' }]) : null,
    k6Script: null,
    environmentsJson: null,
    errorMessage: null,
    modelUsed: 'copilot-cli/x',
    k6ExitCode: null,
    k6Output: null,
    k6Error: null,
    k6SummaryJson: null,
  };
}

function bootWindow() {
  const win = makeWindow(BODY);
  runInWindow(win, [
    'window.SLScroll = function () { return { update: function () {} }; };',
    'window.showConfirm = function () {};',
    'window.showAlert = function () {};',
    'window.bootstrap = { Modal: function () { this.show = function () {}; this.hide = function () {}; } };',
    'window.bootstrap.Modal.getInstance = function () { return { hide: function () {} }; };',
    'if (!window.CSS) { window.CSS = { escape: function (v) { return String(v); } }; }',
    'window.__running = ' + JSON.stringify(analysis('RUNNING')) + ';',
    'window.__list = [window.__running];',
    'window.__status = window.__running;',
    'window.__ticks = [];',
    'window.__nextId = 15;',
    'window.fetch = function (url) {',
    '  var u = String(url);',
    '  var reply = function (body) { return Promise.resolve({ ok: true, status: 200, json: function () { return Promise.resolve(body); } }); };',
    '  var latest = u.match(/\\/local\\/(\\d+)\\/latest/);',
    '  if (u.indexOf("/api/loadtest-analysis/all") !== -1) return reply(window.__list);',
    '  if (latest) return reply(Number(latest[1]) === window.__running.repoConfigId ? window.__running : { status: "NONE" });',
    '  if (u.indexOf("-status") !== -1) return reply({ running: false, exitCode: null, output: "", error: "", summaryJson: null });',
    '  if (u.indexOf("/status") !== -1) return reply(window.__status);',
    '  if (u.indexOf("/start-local") !== -1) return reply({ analysisId: window.__nextId });',
    '  return reply([]);',
    '};',
    'window.setInterval = function (fn) { window.__ticks.push(fn); return window.__ticks.length; };',
    'window.clearInterval = function () {};',
  ].join('\n'));
  runInWindow(win, extractFunction(readJs(resolve(RES, 'static/js/app.js')), 'escHtml'));
  runInWindow(win, readJs(resolve(RES, 'static/js/history-list.js')));
  runInWindow(win, PARTS.map((f) => extractInlineScripts(resolve(LT, f))).join('\n;\n'));
  return win;
}

const flush = () => new Promise((r) => setTimeout(r, 0));
async function settle(times) {
  for (let i = 0; i < (times || 8); i += 1) await flush();
}

function statusEl(win, repoId) {
  return win.document.querySelector('#ltRepoCard-' + repoId + ' .lt-card-status');
}
function pillOf(win, repoId) {
  const el = statusEl(win, repoId);
  return el ? el.textContent.replace(/\s+/g, ' ').trim() : null;
}
function spinnerOf(win, repoId) {
  return !!statusEl(win, repoId).querySelector('.spinner-border');
}
function startBtnOf(win, repoId) {
  return win.document.querySelector('#ltRepoCard-' + repoId + ' .lt-btn-xs-success');
}
function finishRun(win, status) {
  win.__running = analysis(status);
  win.__status = win.__running;
  win.__list = [win.__running];
  return win.__ticks[win.__ticks.length - 1]();
}

describe('Lasttest-Karte meldet das Ende der Analyse ohne erneute Navigation', () => {
  it('zeigt Abgeschlossen, sobald der Poll endet', async () => {
    const win = bootWindow();
    await settle();

    win.ltClickStartLocal(3, 'stacklens');
    await settle();
    expect(pillOf(win, 3)).toContain('Läuft');

    await finishRun(win, 'COMPLETED');
    await settle(12);

    expect(pillOf(win, 3)).toContain('Abgeschlossen');
    expect(spinnerOf(win, 3)).toBe(false);
    expect(startBtnOf(win, 3).disabled).toBe(false);
  });

  it('zeigt Abgeschlossen, wenn die laufende Karte vorher erneut angeklickt wurde', async () => {
    const win = bootWindow();
    await settle();

    win.ltClickStartLocal(3, 'stacklens');
    await settle();
    win.ltSelectLocalRepo(3, 'stacklens');
    await settle();
    expect(pillOf(win, 3)).toContain('Läuft');

    await finishRun(win, 'COMPLETED');
    await settle(12);

    expect(pillOf(win, 3)).toContain('Abgeschlossen');
    expect(spinnerOf(win, 3)).toBe(false);
    expect(startBtnOf(win, 3).disabled).toBe(false);
  });

  it('zeigt Abgeschlossen, während ein anderes Repository geöffnet ist', async () => {
    const win = bootWindow();
    await settle();

    win.ltClickStartLocal(3, 'stacklens');
    await settle();
    win.ltSelectLocalRepo(4, 'lasttest');
    await settle();

    await finishRun(win, 'COMPLETED');
    await settle(12);

    expect(pillOf(win, 3)).toContain('Abgeschlossen');
    expect(spinnerOf(win, 3)).toBe(false);
    expect(pillOf(win, 4)).toBe('');
  });

  it('zeigt Fehlgeschlagen, wenn die Analyse im Poll fehlschlägt', async () => {
    const win = bootWindow();
    await settle();

    win.ltClickStartLocal(3, 'stacklens');
    await settle();
    win.ltSelectLocalRepo(3, 'stacklens');
    await settle();

    await finishRun(win, 'FAILED');
    await settle(12);

    expect(pillOf(win, 3)).toContain('Fehlgeschlagen');
    expect(spinnerOf(win, 3)).toBe(false);
  });

  it('überschreibt einen neuen Lauf nicht mit dem Status des alten', async () => {
    const win = bootWindow();
    await settle();

    win.ltClickStartLocal(3, 'stacklens');
    await settle();
    const ticksBefore = win.__ticks.length;
    const activeTick = win.__ticks[ticksBefore - 1];

    win.__nextId = 16;
    win.ltStartLocalAnalysis();
    await settle();
    expect(win.__ticks).toHaveLength(ticksBefore + 1);

    win.__status = analysis('COMPLETED');
    win.__status.id = 15;
    await activeTick();
    await settle(12);

    expect(pillOf(win, 3)).toContain('Läuft');
    expect(win._ltPollTimers[15]).toBeUndefined();
    expect(win.ltCurrentAnalysisId).toBe(16);
  });
});
