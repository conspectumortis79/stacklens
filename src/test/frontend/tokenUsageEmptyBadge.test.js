import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJs, runInWindow } from './support/fragmentLoader.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SHARED_FRAGMENT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/shared/token-usage.html',
);

function bootCfTokenUsage(opts = {}) {
  const html = readFileSync(SHARED_FRAGMENT, 'utf8');
  const re = /<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;
  const blocks = [...html.matchAll(re)].map((m) => m[1]).filter((b) => b.trim());
  if (blocks.length === 0) throw new Error('no inline script blocks in fragment');

  const scriptsBlock = blocks.find((b) => /window\.cfTokenUsage\s*=/.test(b));
  if (!scriptsBlock) throw new Error('no window.cfTokenUsage script block found');

  const dom = new JSDOM(
    '<!DOCTYPE html><html><body><div class="sl-page-header"></div></body></html>',
    { runScripts: 'dangerously', pretendToBeVisual: true, url: 'http://localhost/' },
  );
  const win = dom.window;

  runInWindow(win, readJs(resolve(__dirname, '../../main/resources/static/js/app.js')));

  if (opts.toggleOff) {
    try { win.localStorage.setItem('dev_show_token_usage', 'false'); } catch (_) {}
  } else {
    try { win.localStorage.setItem('dev_show_token_usage', 'true'); } catch (_) {}
  }

  const s = win.document.createElement('script');
  s.textContent = scriptsBlock;
  win.document.body.appendChild(s);
  s.remove();

  return win;
}

describe('shared/token-usage fragment — badge visibility', () => {
  let win;

  afterEach(() => {
    if (win) { try { win.close(); } catch (_) {} }
  });

  it('exposes window.cfTokenUsage.init/teardown/init/watch/refresh', () => {
    win = bootCfTokenUsage();
    expect(typeof win.cfTokenUsage.init).toBe('function');
    expect(typeof win.cfTokenUsage.teardown).toBe('function');
    expect(typeof win.cfTokenUsage.refresh).toBe('function');
  });

  it('init(kind, null) shows the empty-state badge (the bug-fix contract)', () => {
    win = bootCfTokenUsage();
    expect(win.document.getElementById('tug-bar')).toBeNull();

    win.cfTokenUsage.init('codeqa', null, {
      anchor: win.document.querySelector('.sl-page-header'),
    });

    const bar = win.document.getElementById('tug-bar');
    expect(bar).not.toBeNull();
    expect(bar.classList.contains('tug-bar')).toBe(true);
    expect(bar.getAttribute('data-tug-kind')).toBe('codeqa');
    const emptyPill = bar.querySelector('.tug-pill--empty');
    expect(emptyPill).not.toBeNull();
    expect(emptyPill.innerHTML).toContain('bi-hourglass-split');
  });

  it('init(kind, 1234) skips the HTTP fetch and shows empty-state when id is 0', async () => {
    win = bootCfTokenUsage();
    const origFetch = win.fetch;
    let fetchCalls = 0;
    win.fetch = function () { fetchCalls++; return Promise.reject(new Error('no network')); };

    win.cfTokenUsage.init('loadtest', 0, {
      anchor: win.document.querySelector('.sl-page-header'),
    });

    await Promise.resolve();

    expect(fetchCalls).toBe(0);
    const bar = win.document.getElementById('tug-bar');
    expect(bar).not.toBeNull();

    win.fetch = origFetch;
  });

  it('does NOT show the badge when the dev-options toggle is OFF', () => {
    win = bootCfTokenUsage({ toggleOff: true });
    win.cfTokenUsage.init('review', null, {
      anchor: win.document.querySelector('.sl-page-header'),
    });
    expect(win.document.getElementById('tug-bar')).toBeNull();
  });

  it('prefers the `.sa-page-tabs` anchor on the SQL page so the badge lands at the top', () => {
    win = bootCfTokenUsage();
    const wrapper = win.document.createElement('div');
    wrapper.className = 'sa-page';
    const tabs = win.document.createElement('div');
    tabs.className = 'sa-page-tabs';
    wrapper.appendChild(tabs);
    const panel = win.document.createElement('div');
    panel.id = 'panelAnalyze';
    panel.innerHTML = '<h2>Long results</h2>'.repeat(200);
    wrapper.appendChild(panel);
    win.document.body.appendChild(wrapper);

    win.cfTokenUsage.init('sql', null, { anchor: null });
    const bar = win.document.getElementById('tug-bar');
    expect(bar).not.toBeNull();
    expect(bar.previousElementSibling).toBe(tabs);
    expect(bar.parentNode).toBe(wrapper);
  });

  it('falls back to a generic `<div class="*-page">` wrapper when no specific anchor exists', () => {
    win = bootCfTokenUsage();
    const wrapper = win.document.createElement('div');
    wrapper.className = 'sp-page';
    const form = win.document.createElement('section');
    form.className = 'sp-card sp-form-card';
    form.id = 'sp-form-card';
    wrapper.appendChild(form);
    win.document.body.appendChild(wrapper);

    win.cfTokenUsage.init('ticket_refinement', null, {
      anchor: win.document.querySelector('.nonexistent-anchor-class, .tra-result-header'),
    });
    const bar = win.document.getElementById('tug-bar');
    expect(bar).not.toBeNull();
    expect(bar.previousElementSibling).toBe(form);
  });

  it('calling init() twice does not duplicate the bar (idempotent)', () => {
    win = bootCfTokenUsage();
    const anchor = win.document.querySelector('.sl-page-header');
    win.cfTokenUsage.init('codeqa', null, { anchor });
    win.cfTokenUsage.init('codeqa', null, { anchor });
    const bars = win.document.querySelectorAll('#tug-bar');
    expect(bars.length).toBe(1);
  });

  it('every page that wires up the token-usage scripts also loads the styles', () => {
    const ROOT = resolve(__dirname, '../../main/resources/templates');
    const SCRIPTS_NEEDLE = 'fragments/shared/token-usage :: scripts';
    const STYLES_NEEDLE  = 'fragments/shared/token-usage :: styles';

    const offenders = [];
    function* walk(dir) {
      for (const entry of readdirSync(dir)) {
        const p = join(dir, entry);
        if (statSync(p).isDirectory()) yield* walk(p);
        else if (entry.endsWith('.html')) yield p;
      }
    }
    for (const path of walk(ROOT)) {
      const html = readFileSync(path, 'utf8');
      if (!html.includes(SCRIPTS_NEEDLE)) continue;
      if (!html.includes(STYLES_NEEDLE)) {
        offenders.push(path.replace(ROOT + '/', ''));
      }
    }
    expect(offenders).toEqual([]);
  });

  it('survives an `app:navigate` event (SPA navigation does NOT wipe the bar)', () => {
    win = bootCfTokenUsage();
    const anchor = win.document.querySelector('.sl-page-header');
    win.cfTokenUsage.init('ticket_refinement', null, { anchor });
    expect(win.document.getElementById('tug-bar')).not.toBeNull();

    win.document.dispatchEvent(new win.Event('app:navigate'));

    expect(win.document.getElementById('tug-bar')).not.toBeNull();
    expect(win.document.getElementById('tug-bar').classList.contains('tug-bar')).toBe(true);
  });
});