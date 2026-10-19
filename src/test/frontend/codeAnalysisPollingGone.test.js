import { describe, it, expect, beforeEach } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';
import { extractFunction, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const CA_DIR = resolve(__dirname, '../../main/resources/templates/fragments/code-analysis');
const MAIN = resolve(CA_DIR, 'scripts.html');
const RUN = resolve(CA_DIR, 'scripts-repo-run.html');
const MONO = resolve(CA_DIR, 'scripts-monorepo.html');

function source(path) {
  return readFileSync(path, 'utf8');
}

function response(init, body) {
  return Promise.resolve({
    ok: init.ok,
    status: init.status,
    json: () => (body === '' ? Promise.reject(new SyntaxError('Unexpected end of JSON input')) : Promise.resolve(body)),
  });
}

function fnBody(path, name) {
  const src = source(path);
  const start = src.indexOf('function ' + name + '(');
  return src.slice(start, src.indexOf('\n  }', start) + 4);
}

describe('Code-Analyse-Polling: gelöschte Analysen dürfen nicht endlos 404en', () => {
  let win;

  beforeEach(() => {
    win = makeWindow('<div id="caRepoList"></div>');
    runInWindow(win, extractFunction(source(MAIN), 'caFetchStatus'));
  });

  it('caFetchStatus meldet "gone" bei 404 mit leerem Body', async () => {
    win.fetch = () => response({ ok: false, status: 404 }, '');
    const res = await win.caFetchStatus(999999);
    expect(res.gone).toBe(true);
  });

  it('caFetchStatus meldet "gone" bei 200 mit unlesbarem Body', async () => {
    win.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.reject(new SyntaxError('Unexpected end of JSON input')),
    });
    const res = await win.caFetchStatus(1);
    expect(res.gone).toBe(true);
  });

  it('caFetchStatus meldet "retry" bei Netzwerkfehler', async () => {
    win.fetch = () => Promise.reject(new TypeError('NetworkError'));
    const res = await win.caFetchStatus(1);
    expect(res.retry).toBe(true);
    expect(res.gone).toBeUndefined();
  });

  it('caFetchStatus liefert Daten bei 200', async () => {
    win.fetch = () => response({ ok: true, status: 200 }, { id: 3, status: 'RUNNING' });
    const res = await win.caFetchStatus(3);
    expect(res.data).toEqual({ id: 3, status: 'RUNNING' });
  });

  it('caStartPolling stoppt und verwirrt die Auswahl, wenn die Analyse gelöscht wurde', async () => {
    runInWindow(win, extractFunction(source(RUN), 'caStopPolling'));
    runInWindow(win, extractFunction(source(RUN), 'caStartPolling'));
    const cleared = [];
    let tick = null;
    win.setInterval = (fn) => { tick = fn; return 12; };
    win.clearInterval = (id) => { cleared.push(id); };
    win.caFetchStatus = () => Promise.resolve({ gone: true });
    win.caCurrentAnalysisId = 999999;
    win.caPollTimer = null;
    win._caClearMonorepoTimers = () => {};
    win._caSetPollTimer = (id) => { win.caPollTimer = id; };
    win.caResetContent = () => {};
    win.caRefreshCardStatuses = () => {};
    win.caApplyData = () => { throw new Error('darf bei 404 nicht aufgerufen werden'); };
    win._appVisible = true;

    win.caStartPolling();
    tick();
    await new Promise((r) => setTimeout(r, 0));

    expect(cleared).toEqual([12]);
    expect(win.caPollTimer).toBe(null);
    expect(win.caCurrentAnalysisId).toBe(null);
  });

  it('caStartPolling pollt bei Netzwerkfehler weiter', async () => {
    runInWindow(win, extractFunction(source(RUN), 'caStopPolling'));
    runInWindow(win, extractFunction(source(RUN), 'caStartPolling'));
    const cleared = [];
    let tick = null;
    win.setInterval = (fn) => { tick = fn; return 21; };
    win.clearInterval = (id) => { cleared.push(id); };
    win.caFetchStatus = () => Promise.resolve({ retry: true });
    win.caCurrentAnalysisId = 4;
    win.caPollTimer = null;
    win._caClearMonorepoTimers = () => {};
    win._caSetPollTimer = (id) => { win.caPollTimer = id; };
    win.caResetContent = () => {};
    win.caRefreshCardStatuses = () => {};
    win._appVisible = true;

    win.caStartPolling();
    tick();
    await new Promise((r) => setTimeout(r, 0));

    expect(cleared).toEqual([]);
    expect(win.caPollTimer).toBe(21);
    expect(win.caCurrentAnalysisId).toBe(4);
  });

  it('der Monorepo-Loop zählt eine gelöschte Teilanalyse als abgeschlossen und macht weiter', async () => {
    runInWindow(win, extractFunction(source(MONO), 'caStartMonorepoPolling'));
    const cleared = [];
    const ticks = [];
    win.setInterval = (fn) => { ticks.push(fn); return 33; };
    win.clearInterval = (id) => { cleared.push(id); };
    win.caFetchStatus = () => Promise.resolve({ gone: true });
    win.caMonorepoApps = [{ id: 999998, appName: 'probe' }];
    win.caMonorepoTimers = { 999998: 33 };
    win.caMonorRepoDone = 0;
    win.caUpdateMonorepoAppCard = (id, name, data) => { win.lastCard = data; };
    win.caSetCardStatus = () => {};
    win.caRefreshCardStatuses = () => {};
    win.caCurrentRepoId = 1;
    win.caGitlabConfigId = null;
    win.caGitlabProjectId = null;
    win.document.getElementById = () => null;

    win.caStartMonorepoPolling();
    win.caMonorepoTimers[999998] = 33;
    ticks[0]();
    await new Promise((r) => setTimeout(r, 0));

    expect(cleared).toEqual([33]);
    expect(win.caMonorepoTimers[999998]).toBeUndefined();
    expect(win.caMonorRepoDone).toBe(1);
    expect(win.lastCard).toEqual({ status: 'FAILED' });
  });

  it('beide Loops nutzen caFetchStatus statt rohem r.json() ohne Behandlung', () => {
    for (const body of [fnBody(RUN, 'caStartPolling'), fnBody(MONO, 'caStartMonorepoPolling')]) {
      expect(body).toContain('caFetchStatus(');
      expect(body).not.toMatch(/return r\.json\(\);/);
    }
  });

  it('Timer-Registries überleben SPA-Navigation und werden beim Verlassen geleert', () => {
    const main = source(MAIN);
    expect(main).toContain('window._caMonorepoTimers || (window._caMonorepoTimers = {})');
    expect(main).toContain('window._caPollTimer || null');
    expect(main).toContain('window._caPollCleanupHandler');
    expect(main).toMatch(/_caPollCleanupHandler[\s\S]*caStopPolling\(\)/);
    expect(main).toContain("indexOf('code-analysis')");
  });

  it('caStopPolling räumt beide Registries über den Helper in-place auf', () => {
    const body = fnBody(RUN, 'caStopPolling');
    expect(body).toContain('_caClearMonorepoTimers()');
    expect(body).toContain('_caSetPollTimer(null)');
    expect(body).not.toContain('caMonorepoTimers = {}');
  });
});
