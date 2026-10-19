import { describe, it, expect } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, extractFunction, readJs, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const FINDINGS = resolve(RES, 'templates/fragments/code-analysis/scripts-findings.html');
const SHARED_HELPERS = resolve(RES, 'static/js/shared-helpers.js');

const BODY = [
  '<select id="mrGitlabConfigSelect"><option value="7">Demo GitLab</option></select>',
  '<div id="mrProjectRow" style="display:none;"><select id="mrProjectSelect"></select></div>',
  '<div id="mrModalError" class="alert alert-danger d-none"></div>',
].join('');

function response(status, body) {
  const text = body === undefined ? '' : JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(text),
    json: () => Promise.resolve(text === '' ? null : JSON.parse(text)),
  };
}

function boot(findingsSource) {
  const win = makeWindow(BODY);
  runInWindow(win, readJs(SHARED_HELPERS));
  runInWindow(win, [
    extractFunction(findingsSource, 'caHideEl'),
    extractFunction(findingsSource, 'caShowEl'),
    extractFunction(findingsSource, 'caMrLoadProjects'),
  ].join('\n;\n'));
  return win;
}

async function settle(win) {
  for (let i = 0; i < 8; i++) await new Promise((r) => setTimeout(r, 0));
}

function errorState(win) {
  const el = win.document.getElementById('mrModalError');
  return {
    visible: !el.className.includes('d-none'),
    text: el.textContent,
    rowHidden: win.document.getElementById('mrProjectRow').style.display === 'none',
    options: win.document.getElementById('mrProjectSelect').options.length,
  };
}

describe('GitLab project loader of the MR modal', () => {
  const source = extractInlineScripts(FINDINGS);

  it('shows the server error message instead of throwing when the endpoint replies 500 with an error object', async () => {
    const win = boot(source);
    const seen = [];
    win.addEventListener('error', (e) => seen.push(String(e.message)));
    win.fetch = () => Promise.resolve(response(500, { error: 'GitLab nicht erreichbar' }));

    win.caMrLoadProjects();
    await settle(win);

    expect(seen).toEqual([]);
    const state = errorState(win);
    expect(state.visible).toBe(true);
    expect(state.text).toContain('GitLab nicht erreichbar');
    expect(state.rowHidden).toBe(true);
  });

  it('reports a status message when the endpoint answers 404 with an empty body', async () => {
    const win = boot(source);
    win.fetch = () => Promise.resolve(response(404));

    win.caMrLoadProjects();
    await settle(win);

    const state = errorState(win);
    expect(state.visible).toBe(true);
    expect(state.text.length).toBeGreaterThan(0);
  });

  it('fills the project select when the endpoint answers an array', async () => {
    const win = boot(source);
    win.fetch = () =>
      Promise.resolve(response(200, [{ id: 1, path: 'demo/api' }, { id: 2, path: 'demo/web' }]));

    win.caMrLoadProjects();
    await settle(win);

    const state = errorState(win);
    expect(state.visible).toBe(false);
    expect(state.rowHidden).toBe(false);
    expect(state.options).toBe(3);
    const select = win.document.getElementById('mrProjectSelect');
    expect(select.options[1].value).toBe('1');
    expect(select.options[1].textContent).toBe('demo/api');
  });

  it('shared helper resolves arrays and rejects error bodies with their message', async () => {
    const win = makeWindow('');
    runInWindow(win, readJs(SHARED_HELPERS));
    const helper = win.eval('window.slHelpers.jsonArray');

    await expect(helper(response(200, [{ id: 1 }]))).resolves.toHaveLength(1);
    await expect(helper(response(500, { error: 'kaputt' }))).rejects.toThrow('kaputt');
    await expect(helper(response(404))).rejects.toThrow(/404/);
    await expect(helper(response(200, { unexpected: 'shape' }))).rejects.toThrow();
    await expect(helper({ ok: true, status: 200, text: () => Promise.resolve('<html>login required</html>') })).rejects.toThrow();
  });
});
