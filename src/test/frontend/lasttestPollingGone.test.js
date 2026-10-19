import { describe, it, expect, beforeEach } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';
import { extractFunction, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const LT_DIR = resolve(__dirname, '../../main/resources/templates/fragments/lasttest-analysis');
const RUN = resolve(LT_DIR, 'scripts-run.html');
const SAVE = resolve(LT_DIR, 'scripts-save-history.html');
const MAIN = resolve(LT_DIR, 'scripts.html');

function source(path) {
  return readFileSync(path, 'utf8');
}

function statusResponse(init, body) {
  return Promise.resolve({
    ok: init.ok,
    status: init.status,
    json: () => (body === '' ? Promise.reject(new SyntaxError('Unexpected end of JSON input')) : Promise.resolve(body)),
  });
}

describe('Lasttest-Polling: ein gelöschter Analyse-Eintrag darf nicht endlos 404en', () => {
  let win;

  beforeEach(() => {
    win = makeWindow('<div id="ltProjectList"></div>');
    runInWindow(win, extractFunction(source(RUN), 'ltFetchStatus'));
  });

  it('ltFetchStatus meldet "gone" bei 404 mit leerem Body', async () => {
    win.fetch = () => statusResponse({ ok: false, status: 404 }, '');
    const res = await win.ltFetchStatus(1);
    expect(res.gone).toBe(true);
    expect(res.data).toBeUndefined();
  });

  it('ltFetchStatus meldet "gone" bei 200 mit unlesbarem Body', async () => {
    win.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.reject(new SyntaxError('Unexpected end of JSON input')),
    });
    const res = await win.ltFetchStatus(1);
    expect(res.gone).toBe(true);
  });

  it('ltFetchStatus meldet "retry" bei Netzwerkfehler, nicht "gone"', async () => {
    win.fetch = () => Promise.reject(new TypeError('NetworkError'));
    const res = await win.ltFetchStatus(1);
    expect(res.retry).toBe(true);
    expect(res.gone).toBeUndefined();
  });

  it('ltFetchStatus liefert die Daten bei 200 mit Body', async () => {
    win.fetch = () => statusResponse({ ok: true, status: 200 }, { id: 7, status: 'RUNNING' });
    const res = await win.ltFetchStatus(7);
    expect(res.data).toEqual({ id: 7, status: 'RUNNING' });
  });

  it('ltStartPolling beendet das Intervall, sobald der Eintrag nicht mehr existiert', async () => {
    runInWindow(win, extractFunction(source(RUN), 'ltStartPolling'));
    const cleared = [];
    let tick = null;
    let refreshed = 0;
    win.setInterval = (fn) => { tick = fn; return 99; };
    win.clearInterval = (id) => { cleared.push(id); };
    win.ltFetchStatus = () => Promise.resolve({ gone: true });
    win.ltCurrentAnalysisId = 1;
    win.ltCurrentConfigId = 11;
    win.ltCurrentProjectId = 22;
    win._ltPollTimers = {};
    win._ltAnalysisRunningConfigId = 11;
    win._ltAnalysisRunningProjectId = 22;
    win.ltSetRunBadge = () => {};
    win.ltSetProjectStatus = () => {};
    win.ltUpdateStartBtn = () => {};
    win.ltShowEmpty = () => {};
    win.ltRefreshHistory = () => { refreshed++; };

    win.ltStartPolling();
    expect(typeof tick).toBe('function');
    tick();
    await new Promise((r) => setTimeout(r, 0));

    expect(cleared).toEqual([99]);
    expect(win._ltPollTimers[1]).toBeUndefined();
    expect(win.ltCurrentAnalysisId).toBe(null);
    expect(refreshed).toBe(1);
  });

  it('ltStartPolling pollt weiter, wenn das Netzwerk streikt statt den Eintrag zu löschen', async () => {
    runInWindow(win, extractFunction(source(RUN), 'ltStartPolling'));
    const cleared = [];
    let tick = null;
    win.setInterval = (fn) => { tick = fn; return 77; };
    win.clearInterval = (id) => { cleared.push(id); };
    win.ltFetchStatus = () => Promise.resolve({ retry: true });
    win.ltCurrentAnalysisId = 5;
    win.ltCurrentConfigId = 11;
    win.ltCurrentProjectId = 22;
    win._ltPollTimers = {};
    win.ltSetRunBadge = () => {};
    win.ltSetProjectStatus = () => {};
    win.ltUpdateStartBtn = () => {};
    win.ltApplyResult = () => {};
    win.ltShowEmpty = () => {};
    win.ltRefreshHistory = () => {};

    win.ltStartPolling();
    tick();
    await new Promise((r) => setTimeout(r, 0));

    expect(cleared).toEqual([]);
    expect(win._ltPollTimers[5]).toBe(77);
    expect(win.ltCurrentAnalysisId).toBe(5);
  });

  it('alle drei Status-Loops nutzen ltFetchStatus statt rohes r.json() mit schluckendem catch', () => {
    const body = source(RUN);
    for (const fn of ['ltStartPolling', '_ltStartLocalPolling', '_ltStartGithubPolling']) {
      const start = body.indexOf('function ' + fn + '(');
      expect(start).toBeGreaterThan(-1);
      const loop = body.slice(start, body.indexOf('\n}\n', start));
      expect(loop).toContain('ltFetchStatus(capturedId)');
      expect(loop).not.toMatch(/analysis\/' \+ capturedId \+ '\/status'\)\s*\n\s*\.then\(function \(r\) \{ return r\.json\(\); \}\)/);
      expect(loop).not.toContain('.catch(function() {})');
      expect(loop).not.toContain('.catch(function(){})');
      expect(loop).toContain('res.gone');
    }
  });

  it('der Timer-Registry überlebt SPA-Navigation und wird beim Wegnavigate geleert', () => {
    const main = source(MAIN);
    expect(main).toContain('window._ltPollTimers || (window._ltPollTimers = {})');
    expect(main).toContain('window._ltPollCleanupHandler');
    expect(main).toContain("indexOf('lasttest-analysis')");
    expect(main).toMatch(/_ltPollCleanupHandler[\s\S]*clearInterval\(_ltPollTimers\[id\]\)/);
  });

  it('ltSelectHistoryItem verwirrt die Auswahl bei einem gelöschten Eintrag', () => {
    const save = source(SAVE);
    const fn = save.slice(save.indexOf('function ltSelectHistoryItem'));
    const body = fn.slice(0, fn.indexOf('\n}\n') + 3);
    expect(body).toContain('ltFetchStatus(analysisId)');
    expect(body).toContain('if (res.gone)');
    expect(body).toContain('ltCurrentAnalysisId = null');
    expect(body).not.toContain('.catch(function() {})');
  });
});
