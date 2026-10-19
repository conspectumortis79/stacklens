import { describe, it, expect } from 'vitest';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCRIPTS_PAGE = resolve(
  __dirname,
  '../../main/resources/templates/fragments/config/scripts-page.html',
);
const MR_URL = 'http://localhost:3080/demo-group/frontend-app/-/merge_requests/1';
const FRAGMENT_CODE = extractInlineScripts(SCRIPTS_PAGE);

function emptyResponse() {
  return {
    ok: true,
    status: 200,
    json: () => Promise.resolve({}),
    text: () => Promise.resolve(''),
  };
}

function makeStubWindow(bodyHtml) {
  const win = makeWindow(bodyHtml);
  win.__opened = [];
  win.open = (url) => {
    win.__opened.push(url);
    return null;
  };
  win.fetch = () => Promise.resolve(emptyResponse());
  win.i18n = { t: (k) => k };
  return win;
}

function domReady(win) {
  return new Promise((done) => {
    if (win.document.readyState !== 'loading') done();
    else win.addEventListener('DOMContentLoaded', () => done());
  });
}

async function bootConfigPage(runs, bodyHtml) {
  const win = makeStubWindow(bodyHtml);
  runInWindow(win, FRAGMENT_CODE);
  await domReady(win);
  for (let i = 1; i < runs; i++) runInWindow(win, FRAGMENT_CODE);
  return win;
}

function click(win, id) {
  const event = new win.MouseEvent('click', { bubbles: true, cancelable: true, view: win });
  win.document.getElementById(id).dispatchEvent(event);
  return event;
}

const MR_LINK_HTML =
  '<a id="caMrLink" class="sl-link" href="' + MR_URL + '">' + MR_URL + '</a>';

describe('sl-link external opener of the config page', () => {
  it('opens exactly one tab per click on a single config page run', async () => {
    const win = await bootConfigPage(1, MR_LINK_HTML);
    const event = click(win, 'caMrLink');
    expect(event.defaultPrevented).toBe(true);
    expect(win.__opened).toEqual([MR_URL]);
  });

  it('opens exactly one tab after the SPA re-executed the config fragment five times', async () => {
    const win = await bootConfigPage(5, MR_LINK_HTML);
    const event = click(win, 'caMrLink');
    expect(event.defaultPrevented).toBe(true);
    expect(win.__opened).toEqual([MR_URL]);
  });

  it('routes through the desktop bridge only once when running as desktop app', async () => {
    const win = await bootConfigPage(4, MR_LINK_HTML);
    const bridgeCalls = [];
    win.desktopApp = { openInBrowser: (url) => bridgeCalls.push(url) };
    click(win, 'caMrLink');
    expect(bridgeCalls).toEqual([MR_URL]);
    expect(win.__opened).toEqual([]);
  });

  it('keeps working when the fragment runs again after a hard reload', async () => {
    const first = await bootConfigPage(3, MR_LINK_HTML);
    click(first, 'caMrLink');
    expect(first.__opened).toEqual([MR_URL]);
    const second = await bootConfigPage(6, MR_LINK_HTML);
    click(second, 'caMrLink');
    expect(second.__opened).toEqual([MR_URL]);
  });
});
