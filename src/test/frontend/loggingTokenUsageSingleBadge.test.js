import { describe, it, expect, afterEach } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const TEMPLATES = resolve(__dirname, '../../main/resources/templates');
const SHARED_FRAGMENT = resolve(TEMPLATES, 'fragments/shared/token-usage.html');
const LOGGING_PAGE = resolve(TEMPLATES, 'logging.html');
const LOGGING_FRAGMENTS = resolve(TEMPLATES, 'fragments/logging');
const SCRIPTS_FIX = resolve(LOGGING_FRAGMENTS, 'scripts-fix.html');

const LAYOUT_DOM =
  '<main id="mainScrollContent">' +
    '<div class="sl-page-header">Log-Analyse</div>' +
    '<div id="alertArea"></div>' +
    '<div id="pageContent">' +
      '<div class="lg-page" data-cf-token-usage-anchor="">' +
        '<div class="lg-card"><h2>Log-Eingabe</h2></div>' +
        '<div class="lg-history-card">Verlauf</div>' +
      '</div>' +
    '</div>' +
  '</main>';

const USAGE = {
  provider: 'openai-compatible',
  model: 'qwen3.8-flash',
  totalTokens: 584,
  promptTokens: 531,
  completionTokens: 53,
  durationMs: 3000,
};

const INIT_CALL_RE =
  /cfTokenUsage\.init\(\s*'([a-z_]+)'\s*,\s*([^,]+?)\s*,\s*\{\s*anchor:\s*document\.querySelector\(\s*'([^']+)'\s*\)/g;

function initCallsOf(path) {
  return [...readFileSync(path, 'utf8').matchAll(INIT_CALL_RE)].map((m) => ({
    kind: m[1],
    id: m[2],
    anchor: m[3],
  }));
}

function loggingCallSites() {
  const fragmentFiles = readdirSync(LOGGING_FRAGMENTS)
    .filter((name) => name.endsWith('.html'))
    .map((name) => resolve(LOGGING_FRAGMENTS, name));
  return [LOGGING_PAGE, ...fragmentFiles].flatMap(initCallsOf);
}

function bootLoggingPage() {
  const win = makeWindow(LAYOUT_DOM);
  win.localStorage.setItem('dev_show_token_usage', 'true');
  win.escapeHtml = (value) => String(value == null ? '' : value);
  win.i18n = { t: (key) => key };
  win.fetch = () =>
    Promise.resolve({ ok: true, json: () => Promise.resolve(USAGE) });
  runInWindow(win, extractInlineScripts(SHARED_FRAGMENT));
  return win;
}

function settle() {
  return new Promise((done) => setTimeout(done, 0));
}

describe('logging: ein einziges Token-Verbrauchs-Badge, oben, mit Live-Daten', () => {
  let win;

  afterEach(() => {
    if (win) {
      win.cfTokenUsage.teardownAll();
      try { win.close(); } catch (_) {}
      win = null;
    }
  });

  it('jeder logging-seitige init-Aufruf bindet an denselben Anker unter dem Page-Header', () => {
    const calls = loggingCallSites();
    expect(calls.length).toBeGreaterThanOrEqual(2);
    expect(calls.map((call) => call.anchor)).toEqual(
      calls.map(() => '.sl-page-header'),
    );
  });

  it('Seiten-Init und Fix-Rebind nutzen dieselbe Backend-Kind', () => {
    expect([...new Set(loggingCallSites().map((call) => call.kind))]).toEqual([
      'logging_fix',
    ]);
  });

  it('nach dem Fix-Start sitzt das einzige Badge direkt unter dem Page-Header und zeigt die Token-Zahlen', async () => {
    win = bootLoggingPage();
    const pageCall = initCallsOf(LOGGING_PAGE)[0];
    const fixCall = initCallsOf(SCRIPTS_FIX)[0];
    const document = win.document;

    win.cfTokenUsage.init(pageCall.kind, null, {
      anchor: document.querySelector(pageCall.anchor),
    });
    win.cfTokenUsage.init(fixCall.kind, 42, {
      anchor: document.querySelector(fixCall.anchor),
    });
    await settle();

    const bars = document.querySelectorAll('#tug-bar');
    expect(bars.length).toBe(1);

    const bar = bars[0];
    const pageHeader = document.querySelector('.sl-page-header');
    expect(bar.parentElement).toBe(pageHeader.parentElement);
    expect(bar.previousElementSibling).toBe(pageHeader);
    expect(bar.nextElementSibling).toBe(document.getElementById('alertArea'));
    expect(bar.querySelector('.tug-pill--empty')).toBeNull();
    expect(bar.textContent).toContain('584');
    expect(bar.textContent).toContain('531');
    expect(bar.textContent).toContain('53');
    expect(
      document.getElementById('pageContent').querySelector('#tug-bar'),
    ).toBeNull();
  });

  it('ein Ankerwechsel hängt nie eine zweite Leiste an, sondern ersetzt die bestehende', async () => {
    win = bootLoggingPage();
    const pageCall = initCallsOf(LOGGING_PAGE)[0];
    const document = win.document;

    win.cfTokenUsage.init(pageCall.kind, null, {
      anchor: document.querySelector(pageCall.anchor),
    });
    expect(document.querySelectorAll('#tug-bar').length).toBe(1);

    win.cfTokenUsage.init('logging_fix', 42, {
      anchor: document.querySelector('[data-cf-token-usage-anchor]'),
    });
    await settle();

    const bars = document.querySelectorAll('#tug-bar');
    expect(bars.length).toBe(1);
    expect(bars[0].previousElementSibling).toBe(
      document.querySelector('.lg-page'),
    );
  });
});
