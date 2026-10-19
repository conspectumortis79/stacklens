import { describe, it, expect } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, readJs, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const FRAGMENTS = resolve(RES, 'templates/fragments/code-analysis');

const PARTS = [
  'scripts.html',
  'scripts-repo-run.html',
  'scripts-monorepo.html',
  'scripts-findings.html',
  'scripts-history.html',
];

function live(overrides) {
  return Object.assign({
    id: 55,
    status: 'RUNNING',
    repoConfigId: 12,
    gitlabProjectPath: null,
    githubRepoConfigId: null,
    monorepoAppName: null,
    createdAt: '2026-09-02T20:16:53',
    findingsJson: null,
    errorMessage: null,
  }, overrides || {});
}

const BODY = [
  '<div id="analysisHeader"><span id="analysisStatusBadge"></span></div>',
  '<div id="analysisTabs"></div>',
  '<div id="caEmptyState"></div>',
  '<div id="caRunning"></div>',
  '<div id="caAborted"></div>',
  '<div id="caError"></div>',
  '<div id="caSummaryContent"></div>',
  '<div id="caDuplicateContent"></div>',
  '<div id="caHexContent"></div>',
  '<div id="caUnusedContent"></div>',
  '<div id="caAllContent"></div>',
  '<div id="caChangesContent"></div>',
  '<div id="caMrBanner"><div class="d-flex gap-2"></div></div>',
  '<div id="caMrSection"></div>',
  '<div id="caMrResult"><a id="caMrLink"></a></div>',
  '<div id="caMrError"></div>',
  '<div id="caMermaidContainer"></div>',
  '<div id="caMermaidActions"></div>',
  '<div id="caMermaidEmpty"></div>',
  '<div id="caDrawioContainer"></div>',
  '<div id="caDrawioActions"></div>',
  '<div id="caDrawioEmpty"></div>',
  '<div id="hxCard-code-analysis"></div>',
  '<div class="ca-repo-card" id="repoCard-12" data-repo-id="12" data-repo-name="ecommerce" data-repo-path="/srv/ecommerce">',
  '  <div class="ca-repo-name">ecommerce</div>',
  '  <div class="ca-card-status"></div>',
  '</div>',
].join('');

function bootWindow() {
  const win = makeWindow(BODY);
  runInWindow(win, [
    'window.SLScroll = function() { return { update: function() {} }; };',
    'window.showConfirm = function() {};',
    'window.caLoadGitlabConfigs = function() {};',
    'window.localStorage.removeItem("ca_state");',
  ].join('\n'));
  runInWindow(win, readJs(resolve(RES, 'static/js/history-list.js')));
  runInWindow(win, PARTS.map((file) => extractInlineScripts(resolve(FRAGMENTS, file))).join('\n;\n'));
  runInWindow(win, `
    window.__calls = { all: 0, status: 0, start: 0 };
    window.__ticks = [];
    window.__list = [];
    window.__status = null;
    window.fetch = function (url) {
      var u = String(url);
      var reply = function (body) {
        return Promise.resolve({ ok: true, status: 200, json: function () { return Promise.resolve(body); } });
      };
      if (u.indexOf('/start-') !== -1) { window.__calls.start++; return reply({ analysisId: 55, status: 'PENDING' }); }
      if (u.indexOf('/api/code-analysis/all') !== -1) { window.__calls.all++; return reply(window.__list); }
      if (u.indexOf('/status') !== -1) { window.__calls.status++; return reply(window.__status || window.__list[0]); }
      return reply([]);
    };
    window.setInterval = function (fn) { window.__ticks.push(fn); return window.__ticks.length; };
    window.clearInterval = function () {};
  `);
  win.caCurrentRepoId = 12;
  win.caCurrentRepoName = 'ecommerce';
  win._appVisible = true;
  return win;
}

const flush = () => new Promise((r) => setTimeout(r, 0));

async function settle(times) {
  for (let i = 0; i < (times || 6); i += 1) await flush();
}

function historyText(win) {
  return [...win.document.querySelectorAll('#hxCard-code-analysis tbody tr')]
    .map((row) => row.textContent.replace(/\s+/g, ' ').trim())
    .join(' | ');
}

describe('Verlauf folgt dem Start einer Code-Analyse', () => {
  it('lädt den Verlauf direkt nach dem Start erneut und zeigt den laufenden Lauf', async () => {
    const win = bootWindow();
    win.__list = [];
    await settle();
    expect(historyText(win)).toBe('');

    win.__list = [live({})];
    const before = win.__calls.all;
    win.caStartAnalysis();
    await settle();

    expect(win.__calls.start).toBe(1);
    expect(win.__calls.all - before).toBeGreaterThan(0);
    expect(historyText(win)).toContain('ecommerce');
    expect(historyText(win)).toContain('Läuft');
  });

  it('syncs the history from the running branch of the status poll', async () => {
    const win = bootWindow();
    win.__list = [];
    win.__status = live({ status: 'PENDING' });
    await settle();
    win.__list = [live({ status: 'PENDING' })];
    expect(historyText(win)).toBe('');

    const pollTick = () => win.__ticks[win.__ticks.length - 1]();

    win.caCurrentAnalysisId = 55;
    win.caStartPolling();
    await pollTick();
    await settle();
    expect(historyText(win)).toContain('Ausstehend');

    const afterPending = win.__calls.all;
    await pollTick();
    await settle();
    expect(win.__calls.all).toBe(afterPending);

    win.__list = [live({ status: 'RUNNING' })];
    win.__status = live({ status: 'RUNNING' });
    await pollTick();
    await settle();
    expect(win.__calls.all).toBeGreaterThan(afterPending);
    expect(historyText(win)).toContain('Läuft');
  });

  it('syncs every monorepo app into the history', async () => {
    const win = bootWindow();
    win.__list = [];
    await settle();

    win.__list = [live({ id: 71, monorepoAppName: 'billing' })];
    win.__status = live({ id: 71, monorepoAppName: 'billing' });
    expect(historyText(win)).toBe('');

    const before = win.__calls.all;
    win.caMonorepoApps = [{ id: 71, appName: 'billing' }];
    win.caMonorepoTimers = {};
    win.caStartMonorepoPolling();
    await win.__ticks[win.__ticks.length - 1]();
    await settle();

    expect(win.__calls.all).toBeGreaterThan(before);
    expect(historyText(win)).toContain('billing');
    expect(historyText(win)).toContain('Läuft');
  });

  it('reloaded the history only when the live signature changed', async () => {
    const win = bootWindow();
    win.__list = [];
    await settle();
    const before = win.__calls.all;

    expect(win.caSyncHistoryProgress(live({ status: 'PENDING' }))).toBe(true);
    expect(win.caSyncHistoryProgress(live({ status: 'PENDING' }))).toBe(false);
    expect(win.caSyncHistoryProgress(live({ status: 'RUNNING' }))).toBe(true);
    expect(win.caSyncHistoryProgress(live({ id: 72, status: 'RUNNING' }))).toBe(true);
    expect(win.caSyncHistoryProgress(null)).toBe(false);
    await settle();
    expect(win.__calls.all - before).toBe(3);
  });
});
