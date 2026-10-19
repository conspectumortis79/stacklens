import { describe, it, expect } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { readJs, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');

const INPUT = 'JWT validierung schlägt fehl in orbi';

const BODY = [
  '<form id="tcForm">',
  '<textarea id="tcInputText">' + INPUT + '</textarea>',
  '<span id="tcCharCount"></span>',
  '</form>',
  '<div id="tcProgressCard"></div>',
  '<div id="tcProgressStep"></div>',
  '<div id="tcError"></div>',
  '<button type="button" id="tcStart"></button>',
  '<button type="button" id="tcCancel"></button>',
  '<button type="button" id="tcBtnRegenerate"></button>',
  '<button type="button" id="tcBtnCreate"></button>',
  '<div id="tcResultCard"></div>',
  '<div id="tcResultContent"></div>',
  '<div id="tcCreateSection"></div>',
  '<div id="tcUploadZone"></div>',
  '<input id="tcProjectKey">',
  '<input id="tcCreateProjectKey">',
  '<select id="tcJiraConfig"></select>',
  '<select id="tcCreateJiraConfig"></select>',
  '<div id="tcIssueTypeSeg"></div>',
  '<div id="tcGenerateIssueTypeSeg"></div>',
  '<div id="hxCard-ticket-creation"></div>',
].join('');

function record(overrides) {
  return Object.assign({
    id: 41,
    inputText: INPUT,
    summary: null,
    issueType: 'Story',
    labels: null,
    resultJson: null,
    status: 'RUNNING',
    errorMessage: null,
    jiraConfigId: null,
    jiraProjectKey: null,
    jiraKey: null,
    jiraUrl: null,
    createdAt: '2026-09-04T20:34:00',
  }, overrides || {});
}

const PRELUDE = [
  'window.i18n = { onReady: function () {}, t: function (k) { return k; } };',
  'window.SLScroll = function () { return { update: function () {} }; };',
  'window.escapeHtml = function (s) {',
  '  return String(s == null ? "" : s)',
  '    .replace(/&/g, "&amp;").replace(/</g, "&lt;")',
  '    .replace(/>/g, "&gt;").replace(/"/g, "&quot;");',
  '};',
  'window.__timers = new Map();',
  'window.__timerId = 1;',
  'window.setTimeout = function (fn) { var id = window.__timerId++; window.__timers.set(id, fn); return id; };',
  'window.clearTimeout = function (id) { window.__timers.delete(id); };',
  'window.__list = [];',
  'window.__requests = [];',
  'window.__resolveGenerate = null;',
  'window.fetch = function (url, init) {',
  '  var u = String(url);',
  '  window.__requests.push(u);',
  '  var reply = function (body) {',
  '    return Promise.resolve({ ok: true, status: 200, json: function () { return Promise.resolve(body); } });',
  '  };',
  '  if (u === "/api/config/jira") return reply([]);',
  '  if (u === "/api/ticket-creation/history") return reply(window.__list);',
  '  if (u === "/api/ticket-creation/generate") {',
  '    return new Promise(function (done, fail) {',
  '      window.__resolveGenerate = function (body) { done(reply(body)); };',
  '      var signal = init && init.signal;',
  '      if (signal) signal.addEventListener("abort", function () {',
  '        var err = new Error("aborted"); err.name = "AbortError"; fail(err);',
  '      });',
  '    });',
  '  }',
  '  if (u.indexOf("/cancel") !== -1) return reply({ status: "CANCELLED" });',
  '  return reply({});',
  '};',
].join('\n');

function boot() {
  const win = makeWindow(BODY);
  runInWindow(win, PRELUDE);
  runInWindow(win, readJs(resolve(RES, 'static/js/history-list.js')));
  runInWindow(win, readJs(resolve(RES, 'static/js/fragments/ticket-creation-render.js')));
  runInWindow(win, readJs(resolve(RES, 'static/js/fragments/ticket-creation.js')));
  return win;
}

const settle = () => new Promise((r) => setTimeout(r, 0));

async function settleMany(times) {
  for (let i = 0; i < (times || 6); i += 1) await settle();
}

async function drain(win, times) {
  for (let round = 0; round < (times || 1); round += 1) {
    const due = [...win.__timers.values()];
    win.__timers.clear();
    due.forEach((fn) => fn());
    await settleMany(4);
  }
}

function startRun(win) {
  win.document.getElementById('tcStart').click();
}

function historyRows(win) {
  return [...win.document.querySelectorAll('#hxCard-ticket-creation tbody tr.hx-row')];
}

function titleOf(row) {
  const el = row.querySelector('.hx-title-text');
  return el ? el.textContent.trim() : '';
}

function enabledActions(win) {
  return historyRows(win).flatMap((row) =>
    [...row.querySelectorAll('button[data-hx-act]')]
      .filter((btn) => !btn.disabled)
      .map((btn) => btn.getAttribute('data-hx-act')));
}

function cancelCalls(win) {
  return win.__requests.filter((u) => u.indexOf('/cancel') !== -1);
}

describe('Ticket-Erstellung: genau ein Verlaufseintrag pro Lauf', () => {
  it('zeigt keinen Geister-Eintrag, solange der Server noch keinen Lauf liefert', async () => {
    const win = boot();
    win.__list = [];
    await settleMany();
    startRun(win);
    await settleMany();

    expect(historyRows(win)).toHaveLength(0);
  });

  it('übernimmt den laufenden Server-Datensatz als einzigen Eintrag', async () => {
    const win = boot();
    win.__list = [];
    await settleMany();
    startRun(win);
    await settleMany();

    win.__list = [record()];
    await drain(win, 1);

    expect(historyRows(win)).toHaveLength(1);
    expect(titleOf(historyRows(win)[0])).toBe(INPUT);
  });

  it('ergänzt keine zweite Zeile, wenn der laufende Datensatz schon beim ersten Laden vorhanden ist', async () => {
    const win = boot();
    win.__list = [record()];
    await settleMany();
    startRun(win);
    await settleMany();

    expect(historyRows(win)).toHaveLength(1);

    await drain(win, 3);
    expect(historyRows(win)).toHaveLength(1);
  });

  it('hebt den laufenden Server-Datensatz hervor und lässt ihn abbrechen statt ihn zu sperren', async () => {
    const win = boot();
    win.__list = [record()];
    await settleMany();
    startRun(win);
    await settleMany();

    const row = historyRows(win)[0];
    expect(row.classList.contains('is-current')).toBe(true);
    expect(row.querySelector('[data-hx-act="cancel"]').disabled).toBe(false);
    expect(row.querySelector('[data-hx-act="open"]').disabled).toBe(false);
  });

  it('behält nach Abschluss genau einen Eintrag, der gelöscht werden kann', async () => {
    const win = boot();
    win.__list = [record()];
    await settleMany();
    startRun(win);
    await settleMany();

    const result = { summary: 'JWT-Validierung in orbi prüfen', issueType: 'Story', description: 'Text' };
    win.__list = [record({
      status: 'COMPLETED',
      summary: result.summary,
      resultJson: JSON.stringify(result),
    })];
    win.__resolveGenerate(Object.assign({ historyId: 41 }, result));
    await settleMany();
    await drain(win, 2);

    expect(historyRows(win)).toHaveLength(1);
    expect(titleOf(historyRows(win)[0])).toBe(result.summary);
    expect(enabledActions(win)).toContain('remove');
  });

  it('bricht den Server-Lauf ab, auch wenn der Abbruch vor der Übernahme des Datensatzes erfolgt', async () => {
    const win = boot();
    win.__list = [];
    await settleMany();
    startRun(win);
    await settleMany();

    win.document.getElementById('tcCancel').click();
    win.__list = [record()];
    await drain(win, 2);

    expect(cancelCalls(win)).toEqual(['/api/ticket-creation/history/41/cancel']);
    expect(historyRows(win)).toHaveLength(1);
  });

  it('zeigt für einen abgebrochenen Lauf weiterhin die Eingabe als Zusammenfassung', async () => {
    const win = boot();
    win.__list = [record({ status: 'CANCELLED' })];
    win.document.dispatchEvent(new win.Event('app:navigate'));
    await settleMany();

    expect(historyRows(win)).toHaveLength(1);
    expect(titleOf(historyRows(win)[0])).toBe(INPUT);
  });
});
