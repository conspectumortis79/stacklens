import { describe, it, expect } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, readJs, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const FRAGMENTS = resolve(RES, 'templates/fragments/security-analysis');

const PARTS = [
  'scripts.html',
  'scripts-repo-run.html',
  'scripts-render.html',
  'scripts-history.html',
  'scripts-generate-fix.html',
  'scripts-helpers.html',
  'scripts-fix-pr.html',
  'scripts-repo-panel.html',
];

function running(overrides) {
  return Object.assign({
    id: 55,
    status: 'RUNNING',
    repoConfigId: 12,
    gitlabProjectPath: null,
    githubRepoConfigId: null,
    createdAt: '2026-09-02T20:16:53',
    currentIteration: 1,
    totalIterations: 3,
    currentStep: 'Iteration 1: Reconnaissance…',
    findingsJson: null,
    errorMessage: null,
  }, overrides || {});
}

const BODY = [
  '<div id="baMainArea"></div>',
  '<div id="hxCard-security-analysis"></div>',
  '<div class="ba-repo-card selected" id="baRepoCard-12" data-repo-id="12" data-repo-name="lasttest">',
  '  <div class="ba-repo-name">lasttest</div>',
  '  <div class="ba-card-status"></div>',
  '</div>',
].join('');

function bootWindow() {
  const win = makeWindow(BODY);
  runInWindow(win, [
    'window.SLScroll = function() { return { update: function() {} }; };',
    'window.showConfirm = function() {};',
    'window.baInitTokenUsageBar = function() {};',
    'if (!window.CSS) { window.CSS = { escape: function (v) { return String(v); } }; }',
  ].join('\n'));
  runInWindow(win, readJs(resolve(RES, 'static/js/history-list.js')));
  runInWindow(win, PARTS.map((file) => extractInlineScripts(resolve(FRAGMENTS, file))).join('\n;\n'));
  runInWindow(win, `
    window.__calls = { all: 0, status: 0, start: 0 };
    window.__ticks = [];
    window.__list = [];
    window.__posts = [];
    window.__failCancel = false;
    window.fetch = function (url, init) {
      var u = String(url);
      var reply = function (body) {
        return Promise.resolve({ ok: true, status: 200, json: function () { return Promise.resolve(body); } });
      };
      if (u.indexOf('/cancel') !== -1) {
        window.__posts.push(u);
        if (window.__failCancel) return Promise.reject(new Error('network down'));
        return reply({});
      }
      if (u.indexOf('/start-local') !== -1) { window.__calls.start++; return reply({ analysisId: 55, status: 'PENDING' }); }
      if (u.indexOf('/api/bug-analysis/all') !== -1) { window.__calls.all++; return reply(window.__list); }
      if (u.indexOf('/status') !== -1) { window.__calls.status++; return reply(window.__list[0]); }
      return reply([]);
    };
    window.setInterval = function (fn) { window.__ticks.push(fn); return window.__ticks.length; };
    window.clearInterval = function () {};
  `);
  win.baCurrentRepoId = 12;
  win.baCurrentRepoName = 'lasttest';
  win._appVisible = true;
  return win;
}

const flush = () => new Promise((r) => setTimeout(r, 0));

async function settle(times) {
  for (let i = 0; i < (times || 5); i += 1) await flush();
}

function historyRows(win) {
  return [...win.document.querySelectorAll('#hxCard-security-analysis tbody tr')]
    .map((row) => row.textContent.replace(/\s+/g, ' ').trim());
}

function historyText(win) {
  return historyRows(win).join(' | ');
}

describe('Verlauf folgt dem Start einer Sicherheitsanalyse', () => {
  it('lädt den Verlauf direkt nach dem Start erneut und zeigt den laufenden Lauf', async () => {
    const win = bootWindow();
    await settle();
    expect(historyRows(win)).toEqual([]);

    win.__list = [running({})];
    const before = win.__calls.all;
    win.baStartAnalysis(12, 'lasttest', null, null, null);
    await settle();

    expect(win.__calls.start).toBe(1);
    expect(win.__calls.all - before).toBeGreaterThan(0);
    const rows = historyRows(win);
    expect(rows).toHaveLength(2);
    expect(historyText(win)).toContain('lasttest');
    expect(historyText(win)).toContain('Läuft');
    expect(historyText(win)).toContain('Reconnaissance');
  });

  it('syncs the history from the running branch of the status poll', async () => {
    const win = bootWindow();
    win.__list = [running({})];
    await settle();

    const pollIndex = win.__ticks.length;
    const pollTick = () => win.__ticks[pollIndex]();

    win.baStartPoll(55);
    await pollTick();
    await settle();
    expect(historyText(win)).toContain('Reconnaissance');

    const afterFirstStep = win.__calls.all;
    await pollTick();
    await settle();
    expect(win.__calls.all).toBe(afterFirstStep);

    win.__list = [running({ currentIteration: 2, currentStep: 'Iteration 2: Defensive verification…' })];
    await pollTick();
    await settle();
    expect(win.__calls.all).toBeGreaterThan(afterFirstStep);
    expect(historyText(win)).toContain('Defensive verification');
  });

  it('reloads only when iteration or step changed', async () => {
    const win = bootWindow();
    await settle();
    const before = win.__calls.all;

    expect(win.baSyncHistoryProgress(running({ currentStep: 'A' }))).toBe(true);
    expect(win.baSyncHistoryProgress(running({ currentStep: 'A' }))).toBe(false);
    expect(win.baSyncHistoryProgress(running({ currentStep: 'B' }))).toBe(true);
    expect(win.baSyncHistoryProgress(running({ currentIteration: 2, currentStep: 'B' }))).toBe(true);
    expect(win.baSyncHistoryProgress(null)).toBe(false);
    await settle();
    expect(win.__calls.all - before).toBe(3);
  });
});

describe('Verlauf folgt dem Abbruch einer Sicherheitsanalyse', () => {
  it('reloads the history after the analysis was cancelled from the repo card', async () => {
    const win = bootWindow();
    win.__list = [running({})];
    win.baCurrentAnalysisId = 55;
    await win.baLoadHistory();
    await settle();
    expect(historyText(win)).toContain('Läuft');

    win.__list = [running({ status: 'CANCELLED', currentStep: null, errorMessage: 'Abgebrochen' })];
    const before = win.__calls.all;
    win.baCancelAnalysis();
    await settle();

    expect(win.__posts).toEqual(['/api/bug-analysis/analysis/55/cancel']);
    expect(win.__calls.all - before).toBe(1);
    expect(historyText(win)).toContain('Abgebrochen');
    expect(historyText(win)).not.toContain('Reconnaissance');
  });

  it('reloads the history even when the cancel request fails', async () => {
    const win = bootWindow();
    win.__list = [running({})];
    win.baCurrentAnalysisId = 55;
    await settle();

    win.__failCancel = true;
    const before = win.__calls.all;
    win.baCancelAnalysis();
    await settle();

    expect(win.__posts).toEqual(['/api/bug-analysis/analysis/55/cancel']);
    expect(win.__calls.all - before).toBe(1);
  });

  it('keeps the card pill and the empty state while syncing the history', async () => {
    const win = bootWindow();
    win.__list = [running({})];
    win.baCurrentAnalysisId = 55;
    await settle();

    win.baCancelAnalysis();
    await settle();

    const pill = win.document.querySelector('#baRepoCard-12 .ba-card-status');
    expect(pill.textContent).toContain('Abgebrochen');
    expect(win.document.querySelector('#baMainArea .ba-empty')).not.toBeNull();
    expect(win.baPollTimer).toBeNull();
  });

  it('does nothing without a current analysis', async () => {
    const win = bootWindow();
    await settle();
    const before = win.__calls.all;

    win.baCurrentAnalysisId = null;
    win.baCancelAnalysis();
    await settle();

    expect(win.__posts).toEqual([]);
    expect(win.__calls.all).toBe(before);
  });
});
