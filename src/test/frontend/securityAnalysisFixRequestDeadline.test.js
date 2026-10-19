import { describe, it, expect, beforeEach } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const FRAGMENTS = resolve(here, '../../main/resources/templates/fragments/security-analysis');

const PARTS = [
  'scripts.html',
  'scripts-helpers.html',
  'scripts-render.html',
  'scripts-generate-fix.html',
  'scripts-fix-pr.html',
];

const SERVER_FIX_BUDGET_MS = 11 * 60 * 1000;

const FINDING = {
  id: 'f-1',
  severity: 'LOW',
  type: 'WEAK_INPUT_VALIDATION',
  file: 'src/main/kotlin/com/stacklens/domain/model/ConnectionEntity.kt',
  line: 5,
  description: 'Fehlende Eingabevalidierung.',
  confirmed: false,
};

function boot() {
  const win = makeWindow('<div id="baMainArea"></div><div id="baFindingsList"></div>');
  runInWindow(win, [
    'window.SLScroll = function() { return { update: function() {} }; };',
    'window.showConfirm = function() {};',
    'window.baInitTokenUsageBar = function() {};',
    'if (!window.CSS) { window.CSS = { escape: function (v) { return String(v); } }; }',
  ].join('\n'));
  const code = PARTS.map((file) => extractInlineScripts(resolve(FRAGMENTS, file))).join('\n;\n');
  runInWindow(win, code);
  win.baCurrentAnalysisId = 7;
  win.baAllFindings = [FINDING];
  win._baFixPrDetails = [];
  win._baFixPrRunning = 0;
  win._baFixFilter = 'ALL';
  win.document.getElementById('baFindingsList').innerHTML = win.baRenderFinding(FINDING, 0);
  return win;
}

function drain() {
  return new Promise((resolveDone) => { setTimeout(resolveDone, 0); });
}

describe('Security fix request deadline', () => {
  let win;
  let scheduled;
  let signals;

  beforeEach(() => {
    win = boot();
    scheduled = [];
    signals = [];
    win.setTimeout = (fn, ms) => {
      scheduled.push({ fn, ms });
      return scheduled.length;
    };
    win.clearTimeout = () => {};
    win.fetch = (url, init) => new Promise((resolveFetch, rejectFetch) => {
      signals.push(init.signal);
      init.signal.addEventListener('abort', () => {
        const err = new win.Error('aborted');
        err.name = 'AbortError';
        rejectFetch(err);
      });
    });
  });

  it('gives the browser more time than the server budget needs', () => {
    win.baGenerateFix(7, 'f-1', win.document.querySelector('.ba-btn-fix'));
    const abortTimers = scheduled.filter((entry) => entry.ms >= SERVER_FIX_BUDGET_MS);
    expect(abortTimers).toHaveLength(1);
    expect(abortTimers[0].ms).toBe(SERVER_FIX_BUDGET_MS);
    expect(scheduled.some((entry) => entry.ms === 6 * 60 * 1000)).toBe(false);
  });

  it('surfaces the timeout failure only when the deadline fires', async () => {
    win.baGenerateFix(7, 'f-1', win.document.querySelector('.ba-btn-fix'));
    const entry = win._baFixEntry('f-1');
    const abortTimer = scheduled.find((timer) => timer.ms === SERVER_FIX_BUDGET_MS);

    abortTimer.fn();
    await drain();

    expect(signals[0].aborted).toBe(true);
    expect(win._baFixStateOf(entry)).toBe('error');
    expect(entry.error).toContain('Zeitlimit');
  });
});
