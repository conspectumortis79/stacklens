import { describe, it, expect, beforeEach } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';
import { extractFunction, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const FOCUS = resolve(__dirname, '../../main/resources/templates/fragments/tooling/scripts-focus-view.html');

function source() {
  return readFileSync(FOCUS, 'utf8');
}

function response(init, body) {
  return Promise.resolve({
    ok: init.ok,
    status: init.status,
    json: () => (body === '' ? Promise.reject(new SyntaxError('Unexpected end of JSON input')) : Promise.resolve(body)),
  });
}

function fnBody(name) {
  const src = source();
  const start = src.indexOf('function ' + name + '(');
  return src.slice(start, src.indexOf('\n}', start) + 2);
}

describe('Tooling-Polling: ein gelöschtes Tool darf nicht endlos 404en', () => {
  let win;

  beforeEach(() => {
    win = makeWindow('<div id="focusView"></div>');
    runInWindow(win, extractFunction(source(), 'tlFetchStatus'));
  });

  it('tlFetchStatus meldet "gone" bei 404 mit leerem Body', async () => {
    win.fetch = () => response({ ok: false, status: 404 }, '');
    const res = await win.tlFetchStatus(999999);
    expect(res.gone).toBe(true);
  });

  it('tlFetchStatus meldet "gone" bei 200 mit unlesbarem Body', async () => {
    win.fetch = () => Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.reject(new SyntaxError('Unexpected end of JSON input')),
    });
    const res = await win.tlFetchStatus(1);
    expect(res.gone).toBe(true);
  });

  it('tlFetchStatus meldet "retry" bei Netzwerkfehler', async () => {
    win.fetch = () => Promise.reject(new TypeError('NetworkError'));
    const res = await win.tlFetchStatus(1);
    expect(res.retry).toBe(true);
    expect(res.gone).toBeUndefined();
  });

  it('tlFetchStatus liefert Daten bei 200', async () => {
    win.fetch = () => response({ ok: true, status: 200 }, { status: 'COMPILING' });
    const res = await win.tlFetchStatus(5);
    expect(res.data).toEqual({ status: 'COMPILING' });
  });

  it('pollFocusedTool stoppt und aktualisiert die Liste, wenn das Tool gelöscht wurde', async () => {
    runInWindow(win, extractFunction(source(), '_tlSetFocusPoll'));
    runInWindow(win, extractFunction(source(), 'pollFocusedTool'));
    const cleared = [];
    let tick = null;
    win.setInterval = (fn) => { tick = fn; return 41; };
    win.clearInterval = (id) => { cleared.push(id); };
    win.tlFetchStatus = () => Promise.resolve({ gone: true });
    win.focusPollInterval = null;
    win.window = win;
    win.loadSidebarTools = () => { win.listReloaded = true; };
    win.renderFocusCard = () => {};
    win.showAlert = () => {};

    win.pollFocusedTool(999999);
    tick();
    await new Promise((r) => setTimeout(r, 0));

    expect(cleared).toEqual([41]);
    expect(win.focusPollInterval).toBe(null);
    expect(win.window._tlFocusPollInterval).toBe(null);
    expect(win.listReloaded).toBe(true);
  });

  it('pollFocusedTool pollt bei Netzwerkfehler weiter', async () => {
    runInWindow(win, extractFunction(source(), '_tlSetFocusPoll'));
    runInWindow(win, extractFunction(source(), 'pollFocusedTool'));
    let tick = null;
    win.setInterval = (fn) => { tick = fn; return 43; };
    win.clearInterval = () => {};
    win.tlFetchStatus = () => Promise.resolve({ retry: true });
    win.focusPollInterval = null;
    win.loadSidebarTools = () => { win.listReloaded = true; };
    win.renderFocusCard = () => {};
    win.showAlert = () => {};

    win.pollFocusedTool(8);
    tick();
    await new Promise((r) => setTimeout(r, 0));

    expect(win.focusPollInterval).toBe(43);
    expect(win.listReloaded).toBeUndefined();
  });

  it('beide gone-Zweige löschen das Intervall, bevor sie die Referenz leeren', () => {
    const src = source();
    const focusGone = src.slice(src.indexOf('if (res.gone) {'), src.indexOf('if (res.gone) {') + 160);
    expect(focusGone).toContain('clearInterval(focusPollInterval)');
    expect(focusGone.indexOf('clearInterval')).toBeLessThan(focusGone.indexOf('_tlSetFocusPoll(null)'));
    const promptGone = src.slice(src.indexOf('if (sr.gone) {'), src.indexOf('if (sr.gone) {') + 200);
    expect(promptGone).toContain('clearInterval(_tlPromptPollInterval)');
    expect(promptGone.indexOf('clearInterval')).toBeLessThan(promptGone.indexOf('_tlSetPromptPoll(null)'));
  });

  it('beide Poll-Loops nutzen tlFetchStatus statt rohem fetch mit schluckendem catch', () => {
    const focus = fnBody('pollFocusedTool');
    expect(focus).toContain('tlFetchStatus(id)');
    expect(focus).toContain('res.gone');
    expect(focus).not.toMatch(/return r\.json\(\)/);
    expect(focus).not.toContain('if (!res.ok) return;');
    expect(focus).not.toContain('catch(_)');

    const src = source();
    const prompt = src.slice(src.indexOf('_tlSetPromptPoll(setInterval('));
    expect(prompt.slice(0, 1400)).toContain('tlFetchStatus(savedId)');
    expect(prompt.slice(0, 1400)).toContain('sr.gone');
    expect(prompt.slice(0, 1400)).not.toContain('catch(_)');
  });

  it('beide Intervalle liegen auf dem Fenster und werden beim Verlassen der Seite geräumt', () => {
    const src = source();
    expect(src).toContain('window._tlFocusPollInterval || null');
    expect(src).toContain('window._tlPromptPollInterval || null');
    expect(src).toContain('window._tlPollCleanupHandler');
    expect(src).toMatch(/tlStopToolPolls\(\)[\s\S]*clearInterval\(focusPollInterval\)/);
    expect(src).toMatch(/_tlPollCleanupHandler[\s\S]*tlStopToolPolls\(\)/);
    expect(src).toContain("indexOf('tooling')");
  });
});
