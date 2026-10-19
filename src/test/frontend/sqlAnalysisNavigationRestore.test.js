import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow, readJs } from './support/fragmentLoader.js';


const __dirname = dirname(fileURLToPath(import.meta.url));
const SQL_DIR = resolve(__dirname, '../../main/resources/templates/fragments/sql-analysis');
const SCRIPTS = resolve(SQL_DIR, 'scripts.html');
const HISTORY = resolve(SQL_DIR, 'scripts-history.html');
const STATIC = resolve(__dirname, '../../main/resources/static/js');
const HELPERS = resolve(STATIC, 'shared-helpers.js');
const COMPONENT = resolve(STATIC, 'history-list.js');

const PAGE_DOM = `
  <main>
    <textarea id="analyzeSql"></textarea>
    <textarea id="analyzeDdl"></textarea>
    <div id="analyzeDdlWrap"></div>
    <button id="btnAnalyze">Analysieren</button>
    <button id="btnRunTest">Query testen</button>
    <button id="btnData">Testdaten</button>
    <div id="analyzeLoading"></div>
    <div id="analyzeResult"></div>
    <div id="analyzeError"></div>
    <div id="analyzeDone"></div>
    <div id="analyzeNeedDataHint"></div>
    <div id="analyzeDataPending"></div>
    <div id="dataLoading"></div>
    <div id="dataResult"></div>
    <div id="dataError"></div>
    <button id="rTabBtnInsights"></button>
    <button id="rTabBtnTest" style="display:none"></button>
    <div id="rTabInsights"></div>
    <div id="syntaxBanner"></div>
    <div id="syntaxIssuesList"></div>
    <div id="optimizationsList"></div>
    <div id="indexList"></div>
    <div id="insightsText"></div>
    <div id="insightsCard"></div>
    <div id="tablesContainer"></div>
    <div id="execOrder"></div><div id="execOrderList"></div>
    <div id="dataNotes"></div><div id="dataNotesText"></div>
    <div id="dataValidation"></div><div id="dataValidationText"></div>

    <div id="hxCard-sql-analysis" class="hx-card"></div>
  </main>
`;

function resp(data) {
  return Promise.resolve({ ok: true, json: () => Promise.resolve(data) });
}

function boot() {
  const win = makeWindow(PAGE_DOM);
  win.i18n = { t: (k) => k };
  win.showAlert = () => {};
  if (!win.AbortController) win.AbortController = AbortController;

  const server = { history: [], nextId: 1, combinedResolve: null };
  win.__server = server;

  win.fetch = (url, opts) => {
    if (url === '/api/sql-analyse/analyze-combined') {
      const body = JSON.parse(opts.body);
      const rec = {
        id: server.nextId++,
        sql: body.sql,
        ddl: body.ddl || null,
        status: 'RUNNING',
        createdAt: new Date().toISOString(),
        hasTestData: false,
        testData: null,
        analysisResult: null,
      };
      server.history.unshift(rec);
      server.runningRec = rec;
      return new Promise((res, rej) => {
        server.combinedResolve = (payload) => res({ ok: true, json: () => Promise.resolve(payload) });
        server.combinedReject = (err) => rej(err);
      });
    }
    if (url === '/api/sql-analyse/history') {
      if (opts && opts.method === 'DELETE') { server.history = []; return resp({}); }
      return resp(server.history.map((r) => ({ ...r })));
    }
    const mId = /\/history\/(\d+)$/.exec(url);
    if (mId) return resp({ ...(server.history.find((r) => r.id === Number(mId[1])) || {}) });
    if (/\/history\/\d+\/status$/.test(url)) return resp({});
    return resp({});
  };

  return win;
}

function loadScripts(win) {
  runInWindow(win, readJs(HELPERS));
  runInWindow(win, readJs(COMPONENT));
  runInWindow(win, extractInlineScripts(SCRIPTS));
  runInWindow(win, extractInlineScripts(HISTORY));
}

function navigateBack(win) {
  loadScripts(win);
  win.document.dispatchEvent(new win.CustomEvent('app:navigate', { detail: { url: '/sql-analyse' } }));
}

async function flush(win, n = 5) {
  for (let i = 0; i < n; i++) await new Promise((r) => setTimeout(r, 0));
}

describe('SQL-Analyse: status + Verlauf survive navigate-away-and-back (still running)', () => {
  let win;

  beforeEach(() => {
    win = boot();
    loadScripts(win);
  });

  afterEach(() => {
    if (win && win._saRestoredPollTimer) win.clearInterval(win._saRestoredPollTimer);
  });

  it('sanity: starting an analysis persists a RUNNING record + running-state key', async () => {
    win.document.getElementById('analyzeSql').value = 'SELECT * FROM orders';
    win.doAnalyze();
    await flush(win);

    expect(win.__server.history.length).toBe(1);
    expect(win.__server.history[0].status).toBe('RUNNING');
    expect(win.sessionStorage.getItem('sa_analyse_running')).toBeTruthy();
    expect(win.document.getElementById('analyzeLoading').className).toContain('visible');
    expect(win.document.getElementById('btnAnalyze').disabled).toBe(true);
  });

  it('after navigate away + back, the RUNNING analysis is STILL shown and in the Verlauf', async () => {
    win.document.getElementById('analyzeSql').value = 'SELECT * FROM orders WHERE total > 100';
    win.doAnalyze();
    await flush(win);
    expect(win.__server.runningRec.status).toBe('RUNNING');

    navigateBack(win);
    await flush(win);

    expect(win.document.getElementById('btnAnalyze').disabled).toBe(true);
    expect(win.document.getElementById('analyzeLoading').className).toContain('visible');
    expect(win._saRestoredPollTimer).toBeTruthy();
    expect(win.sessionStorage.getItem('sa_analyse_running')).toBeTruthy();

    const tbody = win.document.querySelector('#hxCard-sql-analysis tbody');
    expect(tbody.innerHTML).toContain('data-status="RUNNING"');
    expect(tbody.querySelectorAll('tr.hx-row').length).toBe(1);
    expect(tbody.innerHTML).toContain('Verfolgen');
  });

  it('when the analysis COMPLETES while away, navigate-back shows it as COMPLETED in the Verlauf', async () => {
    win.document.getElementById('analyzeSql').value = 'SELECT 1';
    win.doAnalyze();
    await flush(win);

    const rec = win.__server.runningRec;
    rec.status = 'COMPLETED';
    rec.hasTestData = true;
    rec.analysisResult = JSON.stringify({ syntaxStatus: 'OK', syntaxDetails: 'fine', optimizations: [], indexSuggestions: [], insights: '' });

    navigateBack(win);
    await flush(win);

    const tbody = win.document.querySelector('#hxCard-sql-analysis tbody');
    expect(tbody.innerHTML).toContain('data-status="COMPLETED"');
    expect(win.document.getElementById('btnAnalyze').disabled).toBe(false);
    expect(win.sessionStorage.getItem('sa_analyse_running')).toBeFalsy();
  });

  it('even if the SPA aborts the in-flight fetch on navigation, the analysis stays in the Verlauf', async () => {
    win.document.getElementById('analyzeSql').value = 'SELECT * FROM audit';
    win.doAnalyze();
    await flush(win);
    expect(win.__server.history[0].status).toBe('RUNNING');

    win.__server.combinedReject(Object.assign(new Error('aborted'), { name: 'AbortError' }));
    await flush(win);
    expect(win.sessionStorage.getItem('sa_analyse_running')).toBeFalsy();

    navigateBack(win);
    await flush(win);

    const tbody = win.document.querySelector('#hxCard-sql-analysis tbody');
    expect(tbody.innerHTML).toContain('data-status="RUNNING"');
    expect(tbody.querySelectorAll('tr.hx-row').length).toBeGreaterThan(0);
  });
});
