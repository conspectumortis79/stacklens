import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJs, runInWindow } from './support/fragmentLoader.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const STATIC_DIR = resolve(__dirname, '../../main/resources/static');
const SHARED_FRAGMENT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/shared/token-usage.html',
);
const SEARCH_FRAGMENT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/search/scripts.html',
);

function extractBlocks(path) {
  const html = readFileSync(path, 'utf8');
  const blocks = [];
  const re = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  for (const m of html.matchAll(re)) {
    const attrs = m[1];
    const body = m[2];
    const srcMatch = attrs.match(/\b(?:th:src|src)\s*=\s*"(?:@\{)?\/?(js\/[^"}]+)\}?"/i);
    if (srcMatch) {
      blocks.push(readFileSync(resolve(STATIC_DIR, srcMatch[1]), 'utf8'));
    } else if (body.trim()) {
      blocks.push(body);
    }
  }
  return blocks;
}

function boot() {
  const dom = new JSDOM(
    '<!DOCTYPE html><html><body>' +

      '<div class="sl-page-header"></div>' +
      '<div class="search-result-header"></div>' +

      '<div id="suAiPlaceholder"></div>' +
      '<div id="suAiStatus"></div>' +
      '<div id="suAiSpinner"></div>' +
      '<div id="suAiContent"></div>' +
      '<div id="suTabKiSpinner"></div>' +
      '<div id="suTabKiIcon"></div>' +
      '</body></html>',
    { runScripts: 'dangerously', pretendToBeVisual: true, url: 'http://localhost/' },
  );
  const win = dom.window;

  win.addEventListener('error', (e) => {
    if (e && e.error && /SU_.*_CONFIGS/.test(String(e.error.message || ''))) {
      e.preventDefault();
    }
  });

  try { win.localStorage.setItem('dev_show_token_usage', 'true'); } catch (_) {}

  runInWindow(win, readJs(resolve(__dirname, '../../main/resources/static/js/app.js')));

  win.fetch = vi.fn((url) => {
    if (String(url).includes('/api/ai-usage/ai/42')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({
          promptTokens: 4321,
          completionTokens: 123,
          totalTokens: 4444,
          filterTokensSaved: 0,
          model: 'gpt-test',
          provider: 'TestProvider',
          durationMs: 7000,
        }),
      });
    }
    return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
  });

  const sharedScripts = extractBlocks(SHARED_FRAGMENT);
  const cfInitBlock = sharedScripts.find((b) => /window\.cfTokenUsage\s*=/.test(b));
  if (!cfInitBlock) throw new Error('test setup: no window.cfTokenUsage block in shared fragment');
  const s0 = win.document.createElement('script');
  s0.textContent = cfInitBlock;
  win.document.body.appendChild(s0);
  s0.remove();

  const searchBlocks = extractBlocks(SEARCH_FRAGMENT);
  for (const block of searchBlocks) {
    const s = win.document.createElement('script');
    s.textContent = block;
    win.document.body.appendChild(s);
    s.remove();
  }

  if (typeof win.suBindAiBadge !== 'function') {
    throw new Error('test setup: window.suBindAiBadge not exposed');
  }

  return win;
}

describe('Search KI-Analyse badge — restore on existing analysis', () => {
  let win;

  beforeEach(() => {
    win = boot();
  });

  afterEach(() => {
    if (win) try { win.close(); } catch (_) {}
  });

  it('starts in empty-state (page-level init has no analysis id yet)', () => {

    win.cfTokenUsage.init('ai', null, {
      anchor: win.document.querySelector('.search-result-header'),
    });
    const bar = win.document.getElementById('tug-bar');
    expect(bar).not.toBeNull();
    expect(bar.classList.contains('tug-bar')).toBe(true);
    expect(bar.getAttribute('data-tug-kind')).toBe('ai');

    expect(bar.querySelector('.tug-pill--empty')).not.toBeNull();

    expect(bar.getAttribute('data-tug-id')).toBe('0');
  });

  it('suBindAiBadge(null) is a no-op (does not crash, does not strip the bar)', () => {

    win.cfTokenUsage.init('ai', null, {
      anchor: win.document.querySelector('.search-result-header'),
    });
    expect(() => win.suBindAiBadge(null)).not.toThrow();
    expect(() => win.suBindAiBadge(undefined)).not.toThrow();
    const bar = win.document.getElementById('tug-bar');
    expect(bar).not.toBeNull();

    expect(bar.querySelector('.tug-pill--empty')).not.toBeNull();

    expect(bar.getAttribute('data-tug-id')).toBe('0');
  });

  it('suBindAiBadge(42) re-binds the badge to the restored analysis id', async () => {

    win.cfTokenUsage.init('ai', null, {
      anchor: win.document.querySelector('.search-result-header'),
    });
    const bar0 = win.document.getElementById('tug-bar');
    expect(bar0).not.toBeNull();
    expect(bar0.querySelector('.tug-pill--empty')).not.toBeNull();

    expect(bar0.getAttribute('data-tug-id')).toBe('0');

    win.suBindAiBadge(42);

    await new Promise((r) => setTimeout(r, 30));

    const bar1 = win.document.getElementById('tug-bar');
    expect(bar1).not.toBeNull();
    expect(bar1.getAttribute('data-tug-id')).toBe('42');

    expect(bar1.querySelector('.tug-pill--empty')).toBeNull();

    const totalPill = Array.from(bar1.querySelectorAll('.tug-pill'))
      .find((el) => /4\s*444/.test(el.textContent));
    expect(totalPill).toBeDefined();

    const modelPill = bar1.querySelector('.tug-pill--model');
    expect(modelPill).not.toBeNull();
    expect(modelPill.textContent).toContain('gpt-test');
    expect(modelPill.textContent).toContain('TESTPROVIDER');
  });

  it('the fetch URL targets kind="ai" with the restored id (NOT the session id)', async () => {

    const capturedUrls = [];
    win.fetch = vi.fn((url) => {
      capturedUrls.push(String(url));
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({
          promptTokens: 1,
          completionTokens: 1,
          totalTokens: 2,
          model: 'm',
          provider: 'p',
          durationMs: 0,
        }),
      });
    });

    win.suBindAiBadge(99);

    win.cfTokenUsage.refresh();
    await new Promise((r) => setTimeout(r, 30));

    const usageCall = capturedUrls.find((u) => u.includes('/api/ai-usage/'));
    expect(usageCall).toBeDefined();
    expect(usageCall).toContain('/api/ai-usage/ai/99');
  });

  it('calling suBindAiBadge twice with different ids re-targets the badge', async () => {

    win.cfTokenUsage.init('ai', null, {
      anchor: win.document.querySelector('.search-result-header'),
    });
    win.suBindAiBadge(11);
    await new Promise((r) => setTimeout(r, 10));
    let bar = win.document.getElementById('tug-bar');
    expect(bar.getAttribute('data-tug-id')).toBe('11');

    win.suBindAiBadge(22);
    await new Promise((r) => setTimeout(r, 10));
    bar = win.document.getElementById('tug-bar');
    expect(bar.getAttribute('data-tug-id')).toBe('22');

    expect(win.document.querySelectorAll('#tug-bar')).toHaveLength(1);
  });
});
