import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJs, runInWindow } from './support/fragmentLoader.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SCRIPTS_PATH = resolve(
  __dirname,
  '../../main/resources/static/js/fragments/pipeline-analysis.js',
);
const SHARED_TOKEN_USAGE_PATH = resolve(
  __dirname,
  '../../main/resources/templates/fragments/shared/token-usage.html',
);

function loadFragmentSource(path) {
  return readFileSync(path, 'utf8');
}

function extractSharedTokenUsageBlock(html) {
  const blocks = [...html.matchAll(/<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi)]
    .map((m) => m[1]);
  const block = blocks.find((b) => /window\.cfTokenUsage\s*=/.test(b));
  if (!block) throw new Error('no window.cfTokenUsage block found');
  return block;
}

function stripComments(src) {
  return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
}

function findFunctionBody(src, name) {
  const sigRe = new RegExp('function\\s+' + name + '\\s*\\(');
  const start = src.search(sigRe);
  if (start < 0) throw new Error('function ' + name + ' not found');
  let i = src.indexOf('{', start);
  let depth = 0;
  for (; i < src.length; i++) {
    const c = src[i];
    if (c === '{') depth++;
    else if (c === '}') {
      depth--;
      if (depth === 0) return src.slice(start, i + 1);
    }
  }
  throw new Error('unbalanced braces for ' + name);
}

describe('Pipeline-Analyse: Token-Usage-Badge wird nach Restore korrekt gebunden', () => {
  let source;

  beforeEach(() => {
    source = loadFragmentSource(SCRIPTS_PATH);
  });

  it('paRenderResult initialisiert die Token-Usage-Bar (Bug-Fix-Hook)', () => {
    const fn = findFunctionBody(source, 'paRenderResult');
    const cleaned = stripComments(fn);

    expect(cleaned).toContain('paRestoreTab()');
    expect(cleaned).toContain('paInitTokenUsageBar');

    const restoreTabIdx = cleaned.lastIndexOf('paRestoreTab()');
    const initBadgeIdx = cleaned.lastIndexOf('paInitTokenUsageBar');

    expect(restoreTabIdx).toBeGreaterThan(-1);
    expect(initBadgeIdx).toBeGreaterThan(-1);
    expect(initBadgeIdx).toBeGreaterThan(restoreTabIdx);
  });

  it('paLoadAnalysisById endet über paRenderResult (und damit über den Badge-Init)', () => {
    const fn = findFunctionBody(source, 'paLoadAnalysisById');
    const cleaned = stripComments(fn);
    expect(cleaned).toContain('paRenderResult(data)');
  });

  it('paLoadLatest ruft paRenderResult(data) für COMPLETED-Analysen', () => {
    const fn = findFunctionBody(source, 'paLoadLatest');
    const cleaned = stripComments(fn);
    expect(cleaned).toContain("status === 'COMPLETED'");
    expect(cleaned).toContain('paRenderResult(data)');
  });

  it('paPollStatus ruft paRenderResult(data) beim Übergang auf COMPLETED', () => {
    const fn = findFunctionBody(source, 'paPollStatus');
    const cleaned = stripComments(fn);
    expect(cleaned).toContain("status === 'COMPLETED'");
    expect(cleaned).toContain('paRenderResult(data)');
  });
});

describe('Pipeline-Analyse: Badge-Render-Verhalten in JSDOM', () => {
  let dom;
  let win;

  beforeEach(() => {
    dom = new JSDOM(
      '<!DOCTYPE html><html><body>' +
        '<div class="sl-page-header"></div>' +
        '<div class="pa-page" data-cf-token-usage-anchor="">' +
          '<div id="paAnalysisTabs" class="d-none"></div>' +
          '<div id="paAnalysisHeader"></div>' +
          '<div id="paSummaryContent" class="d-none"></div>' +
          '<div id="paFindingsContent"></div>' +
          '<div id="paFilesContent"></div>' +
          '<div id="paOptimizationsContent"></div>' +
          '<div id="paMermaidContainer" class="d-none"></div>' +
          '<div id="paMermaidEmpty"></div>' +
          '<div id="paMermaidActions" class="d-none"></div>' +
          '<div id="paFindingsBadge" style="display:none;"></div>' +
          '<div id="paAllFindings" class="d-none"></div>' +
          '<div id="paMrBanner" class="d-none"></div>' +
          '<div id="paMrResult" class="d-none"></div>' +
          '<div id="paMrError" class="d-none"></div>' +
          '<div class="pa-tab-btn" onclick="paSwitch(\'paTabSummary\', this)"></div>' +
        '</div>' +
      '</body></html>',
      { runScripts: 'dangerously', pretendToBeVisual: true, url: 'http://localhost/' },
    );
    win = dom.window;
    win.localStorage.setItem('dev_show_token_usage', 'true');

    runInWindow(win, readJs(resolve(__dirname, '../../main/resources/static/js/app.js')));

    const tugSrc = loadFragmentSource(SHARED_TOKEN_USAGE_PATH);
    runInWindow(win, extractSharedTokenUsageBlock(tugSrc));
  });

  afterEach(() => {
    if (dom) try { dom.window.close(); } catch (_) {   }
  });

  it('init mit null ID zeigt "Keine Token-Daten verfügbar" (Baseline)', () => {
    win.cfTokenUsage.init('pipeline', null, { anchor: win.document.querySelector('.sl-page-header') });
    const bar = win.document.getElementById('tug-bar');
    expect(bar).not.toBeNull();
    expect(bar.classList.contains('tug-bar')).toBe(true);
    const pill = bar.querySelector('.tug-pill--empty');
    expect(pill).not.toBeNull();
    expect(pill.innerHTML).toContain('bi-hourglass-split');
  });

  it('erfolgreiche Init mit echter ID ersetzt die leere Pille durch Daten-Pillen', async () => {
    win.fetch = function (url) {
      expect(url).toBe('/api/ai-usage/pipeline/42');
      return Promise.resolve({
        ok: true,
        json: function () { return Promise.resolve({
          promptTokens: 1234,
          completionTokens: 567,
          totalTokens: 1801,
          filterTokensSaved: 200,
          model: 'claude-sonnet-4.6',
          provider: 'Copilot',
          durationMs: 12000,
        }); },
      });
    };

    win.cfTokenUsage.init('pipeline', null, { anchor: win.document.querySelector('.sl-page-header') });
    win.cfTokenUsage.init('pipeline', 42, { anchor: win.document.querySelector('.sl-page-header') });

    await new Promise((r) => setTimeout(r, 50));

    const bar = win.document.getElementById('tug-bar');
    expect(bar).not.toBeNull();
    expect(bar.getAttribute('data-tug-id')).toBe('42');
    expect(bar.getAttribute('data-tug-kind')).toBe('pipeline');
    expect(bar.querySelector('.tug-pill--empty')).toBeNull();
    expect(bar.querySelector('.tug-pill--model')).not.toBeNull();
    expect(bar.textContent).toMatch(/claude-sonnet-4\.6/i);
  });
});
