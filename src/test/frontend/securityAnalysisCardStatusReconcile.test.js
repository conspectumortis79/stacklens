import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

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

function analysis(id, repoConfigId, status, iteration, step) {
  return {
    id,
    repoConfigId,
    status,
    currentIteration: iteration,
    totalIterations: 3,
    currentStep: step,
    createdAt: '2026-09-02T20:16:53',
    completedAt: status === 'COMPLETED' ? '2026-09-02T20:22:10' : null,
    gitlabConfigId: null,
    gitlabProjectId: null,
    gitlabProjectPath: null,
    githubRepoConfigId: null,
    findingsJson: status === 'COMPLETED'
      ? JSON.stringify([{ id: 'f-1', severity: 'LOW', type: 'X', file: 'a.kt', line: 1, description: 'd', confirmed: true }])
      : null,
    summaryText: status === 'COMPLETED' ? 'fertig' : null,
    errorMessage: null,
    modelUsed: 'copilot-cli/x',
  };
}

const A_RUN = analysis(55, 13, 'RUNNING', 1, 'Iteration 1: Reconnaissance…');
const B_RUN = analysis(56, 12, 'RUNNING', 1, 'Iteration 1: Reconnaissance…');
const A_DONE = analysis(55, 13, 'COMPLETED', 3, null);
const B_DONE = analysis(56, 12, 'COMPLETED', 3, null);

function cardHtml(id, name) {
  return '<div class="ba-repo-card" id="baRepoCard-' + id + '" data-repo-id="' + id + '" data-repo-name="' + name + '">' +
    '<div class="ba-repo-name">' + name + '</div><div class="ba-card-status"></div>' +
    '<div class="ba-btn-row"><button class="ba-btn-xs ba-btn-xs-success"></button>' +
    '<button class="ba-btn-xs ba-btn-xs-cancel"></button><button class="ba-btn-xs ba-btn-xs-outline"></button></div></div>';
}

function bootWindow(list) {
  const win = makeWindow(
    '<div id="baMainArea"></div><div id="hxCard-security-analysis"></div>' + cardHtml(13, 'stacklens') + cardHtml(12, 'lasttest'),
  );
  runInWindow(win, [
    'window.SLScroll = function() { return { update: function() {} }; };',
    'window.showConfirm = function() {};',
    'window.baInitTokenUsageBar = function() {};',
    'if (!window.CSS) { window.CSS = { escape: function (v) { return String(v); } }; }',
  ].join('\n'));
  runInWindow(win, PARTS.map((file) => extractInlineScripts(resolve(FRAGMENTS, file))).join('\n;\n'));
  runInWindow(win, `
    window.__list = ${JSON.stringify(list)};
    window.__status = ${JSON.stringify(list[0])};
    window.__ticks = [];
    window.fetch = function (url) {
      var u = String(url);
      var reply = function (body) { return Promise.resolve({ ok: true, status: 200, json: function () { return Promise.resolve(body); } }); };
      if (u.indexOf('/api/bug-analysis/all') !== -1) return reply(window.__list);
      if (u.indexOf('/status') !== -1) return reply(window.__status);
      return reply([]);
    };
    window.setInterval = function (fn) { window.__ticks.push(fn); return window.__ticks.length; };
    window.clearInterval = function () {};
  `);
  win._appVisible = true;
  return win;
}

const flush = () => new Promise((r) => setTimeout(r, 0));

async function settle(times) {
  for (let i = 0; i < (times || 8); i += 1) await flush();
}

function pillText(win, repoId) {
  const el = win.document.querySelector(`#baRepoCard-${repoId} .ba-card-status`);
  return el ? el.textContent.replace(/\s+/g, ' ').trim() : null;
}

function hasSpinner(win, repoId) {
  return !!win.document.querySelector(`#baRepoCard-${repoId} .ba-card-status .spinner-border`);
}

function lastTick(win) {
  return win.__ticks[win.__ticks.length - 1];
}

describe('Repo card status reconciles with the server truth', () => {
  it('maps every server status onto one pill state', () => {
    const win = bootWindow([A_RUN, B_RUN]);
    expect(win.baCardStatusType('RUNNING')).toBe('running');
    expect(win.baCardStatusType('PENDING')).toBe('running');
    expect(win.baCardStatusType('COMPLETED')).toBe('completed');
    expect(win.baCardStatusType('FAILED')).toBe('aborted');
    expect(win.baCardStatusType('CANCELLED')).toBe('aborted');
    expect(win.baCardStatusType(undefined)).toBeNull();
    expect(win.baCardStatusType('ANALYZING')).toBeNull();
  });

  it('renders each pill exactly once across the fragments', () => {
    const sources = PARTS.map((file) => readFileSync(resolve(FRAGMENTS, file), 'utf8')).join('\n');
    expect(sources.match(/ba-card-status-pill ba-card-status--running/g)).toHaveLength(1);
    expect(sources.match(/ba-card-status-pill ba-card-status--completed/g)).toHaveLength(1);
    expect(sources.match(/ba-card-status-pill ba-card-status--aborted/g)).toHaveLength(1);
    expect(sources.match(/class="spinner-border spinner-border-sm"/g)).toHaveLength(1);
  });

  it('shows the finished state on the card of a repo selected after its run completed', async () => {
    const win = bootWindow([A_RUN, B_RUN]);
    win.baSelectLocal(13, 'stacklens');
    await settle();
    expect(pillText(win, 13)).toBe('Läuft…');
    expect(hasSpinner(win, 13)).toBe(true);

    win.__list = [A_DONE, B_RUN];
    win.baSelectLocal(13, 'stacklens');
    await settle();

    expect(pillText(win, 13)).toBe('Abgeschlossen');
    expect(hasSpinner(win, 13)).toBe(false);
  });

  it('reconciles on the cached view path too', async () => {
    const win = bootWindow([A_RUN, B_RUN]);
    win.baSelectLocal(13, 'stacklens');
    await settle();

    win.__list = [A_DONE, B_RUN];
    win.baSelectLocal(13, 'stacklens');
    await settle();
    expect(pillText(win, 13)).toBe('Abgeschlossen');

    win.baSelectLocal(12, 'lasttest');
    await settle();
    win.document.querySelector('#baRepoCard-13 .ba-card-status').innerHTML = '<span class="stale">Läuft…</span>';

    win.baSelectLocal(13, 'stacklens');
    await settle();
    expect(pillText(win, 13)).toBe('Abgeschlossen');
    expect(hasSpinner(win, 13)).toBe(false);
  });

  it('fixes the other repo card as well when a polled run completes', async () => {
    const win = bootWindow([A_RUN, B_RUN]);
    win.baSelectLocal(13, 'stacklens');
    await settle();
    win.baSelectLocal(12, 'lasttest');
    await settle();
    expect(pillText(win, 13)).toBe('Läuft…');

    win.__list = [A_DONE, B_DONE];
    win.__status = B_DONE;
    await lastTick(win)();
    await settle(12);

    expect(pillText(win, 12)).toBe('Abgeschlossen');
    expect(pillText(win, 13)).toBe('Abgeschlossen');
    expect(hasSpinner(win, 13)).toBe(false);
  });

  it('shows the aborted pill for a failed run instead of clearing it', async () => {
    const win = bootWindow([A_RUN, B_RUN]);
    win.baSelectLocal(13, 'stacklens');
    await settle();

    win.__list = [analysis(55, 13, 'FAILED', 3, null), B_RUN];
    win.__status = analysis(55, 13, 'FAILED', 3, null);
    await lastTick(win)();
    await settle(12);

    expect(pillText(win, 13)).toBe('Abgebrochen');
    expect(hasSpinner(win, 13)).toBe(false);
  });
});
