import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractInlineScripts, readJs, runInWindow } from './support/fragmentLoader.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const TEMPLATES = resolve(__dirname, '../../main/resources/templates');
const PAGE = resolve(TEMPLATES, 'code-analysis.html');
const SHARED = resolve(TEMPLATES, 'fragments/shared/token-usage.html');
const SCRIPTS_STATE = resolve(TEMPLATES, 'fragments/code-analysis/scripts.html');
const SCRIPTS_REPO_RUN = resolve(TEMPLATES, 'fragments/code-analysis/scripts-repo-run.html');
const SCRIPTS_FINDINGS = resolve(TEMPLATES, 'fragments/code-analysis/scripts-findings.html');
const SCRIPTS_MONOREPO = resolve(TEMPLATES, 'fragments/code-analysis/scripts-monorepo.html');
const SCRIPT_RE = /<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;

function emptyUsage() {
  return {
    promptTokens: 0,
    completionTokens: 0,
    totalTokens: 0,
    filterTokensSaved: 0,
    model: '',
    provider: '',
    durationMs: 0,
  };
}

function usageRow(id) {
  return {
    promptTokens: 9000,
    completionTokens: 676,
    totalTokens: 9676,
    filterTokensSaved: 0,
    model: 'gpt-code-' + id,
    provider: 'TestProvider',
    durationMs: 15000,
  };
}

function analysisRow(id, status) {
  return {
    id: id,
    status: status,
    summaryText: 'Zusammenfassung der Analyse ' + id,
    findingsJson: '[]',
    fileChangesJson: '[]',
    repoConfigId: 4,
  };
}

function jsonResponse(body) {
  return { ok: true, status: 200, json: () => Promise.resolve(body) };
}

function pageContentHtml() {
  const html = readFileSync(PAGE, 'utf8');
  const start = html.indexOf('<div layout:fragment="content">');
  const end = html.indexOf('<th:block layout:fragment="scripts">');
  return html.slice(start, end).replace(/<script\b[\s\S]*?<\/script>/gi, '');
}

function pageLevelTokenUsageInit() {
  const html = readFileSync(PAGE, 'utf8');
  const tail = html.slice(html.indexOf('<th:block layout:fragment="scripts">'));
  const blocks = [...tail.matchAll(SCRIPT_RE)].map((m) => m[1]).filter((b) => b.trim());
  const initBlock = blocks.find((b) => /slTokenUsagePage\(\{/.test(b));
  if (!initBlock) throw new Error('test setup: code-analysis.html has no page-level slTokenUsagePage registration');
  return initBlock;
}

function boot() {
  const dom = new JSDOM(
    '<!DOCTYPE html><html><body>' + pageContentHtml() + '</body></html>',
    { runScripts: 'dangerously', pretendToBeVisual: true, url: 'http://localhost/' },
  );
  const win = dom.window;

  try { win.localStorage.setItem('dev_show_token_usage', 'true'); } catch (_) {}

  runInWindow(win, readJs(resolve(__dirname, '../../main/resources/static/js/app.js')));

  for (const path of [SHARED, SCRIPTS_STATE, SCRIPTS_REPO_RUN, SCRIPTS_FINDINGS, SCRIPTS_MONOREPO]) {
    runInWindow(win, extractInlineScripts(path));
  }

  const usageStore = new Map();
  const usageRequests = [];
  win.__usageRequests = usageRequests;
  win.__latest = { status: 'NONE' };
  win.__setUsage = function (id, row) { usageStore.set(id, row); };
  win.fetch = vi.fn(function (url) {
    const u = String(url);
    if (u.indexOf('/api/ai-usage/code/') === 0) {
      usageRequests.push(u);
      const id = Number(u.split('/').pop());
      return Promise.resolve(jsonResponse(usageStore.get(id) || emptyUsage()));
    }
    if (/^\/api\/code-analysis\/local\/\d+\/latest$/.test(u)) {
      return Promise.resolve(jsonResponse(win.__latest));
    }
    if (u === '/api/code-analysis/start-local') {
      return Promise.resolve(jsonResponse({ analysisId: 77 }));
    }
    return Promise.resolve(jsonResponse([]));
  });

  runInWindow(win, pageLevelTokenUsageInit());
  return win;
}

function selectLocalRepo(win, analysisId, status) {
  win.__latest = analysisId ? analysisRow(analysisId, status || 'COMPLETED') : { status: 'NONE' };
  win.selectRepo(4, 'stacklens', '/Users/mortis/Code/Kotlin/stacklens');
}

async function settle(ms) {
  await new Promise((r) => setTimeout(r, ms || 40));
}

function bar(win) {
  return win.document.getElementById('tug-bar');
}

describe('Code-Analyse: Token-Verbrauchs-Badge', () => {
  let win;

  beforeEach(() => {
    win = boot();
  });

  afterEach(() => {
    if (win) {
      try { win.caStopPolling(); } catch (_) {}
      try { win.cfTokenUsage.teardownAll(); } catch (_) {}
      try { win.close(); } catch (_) {}
    }
  });

  it('Seiten-Init rendert den Badge ohne Analyse-ID im leeren Zustand', () => {
    expect(bar(win)).not.toBeNull();
    expect(bar(win).getAttribute('data-tug-kind')).toBe('code');
    expect(bar(win).getAttribute('data-tug-id')).toBe('0');
    expect(bar(win).querySelector('.tug-pill--empty')).not.toBeNull();
  });

  it('Repository-Restore bindet den Badge an die gefundene Analyse', async () => {
    win.__setUsage(42, usageRow(42));
    selectLocalRepo(win, 42);
    await settle();

    expect(win.__usageRequests).toContain('/api/ai-usage/code/42');
    expect(bar(win).getAttribute('data-tug-id')).toBe('42');
    expect(bar(win).querySelector('.tug-pill--empty')).toBeNull();
    expect(bar(win).querySelector('.tug-pill--model').textContent).toContain('gpt-code-42');
    expect(bar(win).textContent).toMatch(/9\u202F676/);
  });

  it('caStartAnalysis bindet den Badge an die neu gestartete Analyse', async () => {
    win.__setUsage(77, usageRow(77));
    win.caCurrentRepoId = 4;
    win.caStartAnalysis();
    await settle();

    expect(bar(win).getAttribute('data-tug-id')).toBe('77');
    expect(win.__usageRequests).toContain('/api/ai-usage/code/77');
    expect(bar(win).querySelector('.tug-pill--model').textContent).toContain('gpt-code-77');
  });

  it('Badge bleibt während der laufenden Analyse leer und füllt sich, sobald Token-Zeilen existieren', async () => {
    selectLocalRepo(win, 55, 'RUNNING');
    await settle();

    expect(bar(win).getAttribute('data-tug-id')).toBe('55');
    expect(bar(win).querySelector('.tug-pill--empty')).not.toBeNull();

    win.__setUsage(55, usageRow(55));
    win.cfTokenUsage.refresh();
    await settle();

    expect(bar(win).querySelector('.tug-pill--empty')).toBeNull();
    expect(bar(win).querySelector('.tug-pill--model').textContent).toContain('gpt-code-55');
  });

  it('Repository ohne Analyse zeigt leeren Zustand statt alter Token-Werte', async () => {
    win.__setUsage(42, usageRow(42));
    selectLocalRepo(win, 42);
    await settle();
    expect(bar(win).querySelector('.tug-pill--model')).not.toBeNull();

    selectLocalRepo(win, null);
    await settle();

    expect(bar(win)).not.toBeNull();
    expect(bar(win).getAttribute('data-tug-id')).toBe('0');
    expect(bar(win).querySelector('.tug-pill--model')).toBeNull();
    expect(bar(win).querySelector('.tug-pill--empty')).not.toBeNull();
  });

  it('mehrfaches Binden hält genau ein Badge im DOM', async () => {
    win.__setUsage(11, usageRow(11));
    win.__setUsage(12, usageRow(12));
    selectLocalRepo(win, 11);
    await settle();
    selectLocalRepo(win, 12);
    await settle();

    expect(win.document.querySelectorAll('#tug-bar')).toHaveLength(1);
    expect(bar(win).getAttribute('data-tug-id')).toBe('12');
  });
});
