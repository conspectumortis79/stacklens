import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const FRAGMENTS = resolve(RES, 'templates/fragments/security-analysis');
const PAGE_HTML = resolve(RES, 'templates/security-analysis.html');
const STYLES_HTML = resolve(RES, 'templates/fragments/security-analysis/styles.html');
const APP_JS = resolve(RES, 'static/js/app.js');

const PARTS = [
  'scripts.html',
  'scripts-helpers.html',
  'scripts-render.html',
  'scripts-generate-fix.html',
  'scripts-fix-pr.html',
];

const LIVE_MARKUP = '<div id="baFixLive" class="ba-fix-sr" role="status" aria-live="polite"></div>';

function bootSecurityAnalysisScripts(bodyHtml) {
  const win = makeWindow(bodyHtml);
  runInWindow(win, [
    'window.SLScroll = function() { return { update: function() {} }; };',
    'window.showConfirm = function() {};',
    'window.baInitTokenUsageBar = function() {};',
    'if (!window.CSS) { window.CSS = { escape: function (v) { return String(v); } }; }',
  ].join('\n'));
  const code = PARTS.map((file) => extractInlineScripts(resolve(FRAGMENTS, file))).join('\n;\n');
  runInWindow(win, code);
  win._baFixPrDetails = [];
  win._baFixPrRunning = 0;
  win._baFixFilter = 'ALL';
  return win;
}

function entry(overrides) {
  return Object.assign({ findingId: 1, label: 'SQL INJECTION', startedAt: Date.now() }, overrides);
}

describe('Fix-PR live region is page scoped', () => {
  it('renders the live region inside the page container of security-analysis.html', () => {
    const page = readFileSync(PAGE_HTML, 'utf8');
    expect(page).toMatch(
      new RegExp(
        '<div class="ba-page"[^>]*>\\s*<div id="baFixLive" class="ba-fix-sr" role="status" aria-live="polite"></div>',
      ),
    );
  });

  it('ships the screen-reader-only rule in the page scoped style fragment', () => {
    expect(readFileSync(STYLES_HTML, 'utf8')).toMatch(/\.ba-fix-sr\s*\{[^}]*clip-path:\s*inset\(50%\)/);
  });

  it('writes the announce text into the existing page element', () => {
    const win = bootSecurityAnalysisScripts(
      LIVE_MARKUP + '<div id="baMainArea"></div><div class="ba-repo-card selected"><div class="ba-card-status"></div></div>',
    );
    const live = win.document.getElementById('baFixLive');
    win._baFixPrDetails = [entry({ prUrl: 'https://gitlab.example/x/merge_requests/4' }), entry({ findingId: 2, error: 'boom' })];
    win._baFixAnnounce();
    expect(win.document.getElementById('baFixLive')).toBe(live);
    expect(win.document.querySelectorAll('.ba-fix-sr').length).toBe(1);
    expect(live.textContent).toContain('0 Fix-PRs aktiv,');
    expect(live.textContent).toContain('1 fertig,');
    expect(live.textContent).toContain('1 fehlgeschlagen.');
  });

  it('creates no element when the page container is absent', () => {
    const win = bootSecurityAnalysisScripts('<main><div id="searchArea"></div></main>');
    const bodyChildrenBefore = win.document.body.childElementCount;
    win._baFixPrDetails = [entry({})];
    win._baFixAnnounce();
    win._baUpdateFixPrBadge();
    expect(win.document.getElementById('baFixLive')).toBeNull();
    expect(win.document.querySelectorAll('.ba-fix-sr').length).toBe(0);
    expect(win.document.body.childElementCount).toBe(bodyChildrenBefore);
  });

  it('keeps a background fix completion body-free on a foreign page', () => {
    const win = bootSecurityAnalysisScripts('<main></main>');
    const bodyChildrenBefore = win.document.body.childElementCount;
    win._baFixPrDetails = [entry({})];
    win._baRegisterFixPrHook();
    win._baFixPrHook(entry({ prUrl: 'https://gitlab.example/x/merge_requests/4' }));
    expect(win.document.getElementById('baFixLive')).toBeNull();
    expect(win.document.body.childElementCount).toBe(bodyChildrenBefore);
  });
});

describe('SPA navigation tears down body level security-analysis artifacts', () => {
  let appSource;

  beforeEach(() => {
    appSource = readFileSync(APP_JS, 'utf8');
  });

  it('removes the diff overlay that the fix-pr fragment appends to body', () => {
    expect(appSource).toMatch(/getElementById\('baFixPrDiffOverlay'\)/);
    expect(appSource).toMatch(/_diffOverlay\)\s*_diffOverlay\.remove\(\)/);
  });

  it('no longer references the removed detail overlay id', () => {
    expect(appSource).not.toContain('baFixPrDetailOverlay');
    const fragments = readFileSync(resolve(FRAGMENTS, 'scripts-fix-pr.html'), 'utf8');
    expect(fragments).not.toContain('baFixPrDetailOverlay');
  });
});
