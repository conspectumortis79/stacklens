import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { readJs, runInWindow } from './support/fragmentLoader.js';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const STATIC_DIR = resolve(__dirname, '../../main/resources/static');
const SHARED_FRAGMENT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/shared/token-usage.html',
);
const SP_FRAGMENT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/ticket-refinement/scripts.html',
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
      '<div class="sp-page"><section class="sp-card sp-form-card">' +
      '<input id="spParentKey"/>' +
      '<div id="spHistory"></div>' +
      '<div id="spProgressCard" style="display:none"></div>' +
      '<div id="spResultsCard" style="display:none"></div>' +
      '<div id="spCancel" disabled></div>' +
      '<div id="spStart"></div>' +
      '<div id="spSprintLabel"></div>' +
      '</section></div>' +
      '</body></html>',
    { runScripts: 'dangerously', pretendToBeVisual: true, url: 'http://localhost/' },
  );
  const win = dom.window;

  try { win.localStorage.setItem('dev_show_token_usage', 'true'); } catch (_) {}

  runInWindow(win, readJs(resolve(__dirname, '../../main/resources/static/js/app.js')));

  win.fetch = vi.fn((url) => Promise.resolve({
    ok: true,
    json: () => Promise.resolve({}),
  }));

  const sharedScripts = extractBlocks(SHARED_FRAGMENT);
  const cfInitBlock = sharedScripts.find((b) => /window\.cfTokenUsage\s*=/.test(b));
  const s1 = win.document.createElement('script');
  s1.textContent = cfInitBlock;
  win.document.body.appendChild(s1);
  s1.remove();

  const spBlocks = extractBlocks(SP_FRAGMENT);
  for (const block of spBlocks) {
    const s = win.document.createElement('script');
    s.textContent = block;
    win.document.body.appendChild(s);
    s.remove();
  }

  const PAGE = resolve(
    __dirname,
    '../../main/resources/templates/ticket-refinement.html',
  );
  const pageBlocks = extractBlocks(PAGE);
  for (const block of pageBlocks) {

    if (!/spInitTokenUsageBar\b/.test(block)) continue;
    const s = win.document.createElement('script');
    s.textContent = block;
    win.document.body.appendChild(s);
    s.remove();
  }

  if (typeof win.spInitTokenUsageBar !== 'function') {
    throw new Error('test setup: spInitTokenUsageBar not exposed on window');
  }

  return win;
}

describe('Ticket-Refinement badge — restore after SPA nav', () => {
  let win;

  beforeEach(() => {
    win = boot();
  });

  afterEach(() => {
    if (win) try { win.close(); } catch (_) {}
  });

  it('starts in empty-state (page-level init has no analysis id yet)', () => {

    win.cfTokenUsage.init('ticket_refinement', null, {
      anchor: win.document.querySelector('.sp-form-card'),
    });
    const bar = win.document.getElementById('tug-bar');
    expect(bar).not.toBeNull();
    expect(bar.querySelector('.tug-pill--empty')).not.toBeNull();
  });

  it('spInitTokenUsageBar MUST re-bind the badge to the completed analysis id', async () => {

    win.cfTokenUsage.init('ticket_refinement', null, {
      anchor: win.document.querySelector('.sp-form-card'),
    });
    const bar0 = win.document.getElementById('tug-bar');
    expect(bar0).not.toBeNull();
    expect(bar0.querySelector('.tug-pill--empty')).not.toBeNull();

    win._sp.lastOpenId = 4242;

    win.fetch = vi.fn((url) => {
      if (String(url).includes('/api/ai-usage/ticket_refinement/4242')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({
            promptTokens: 1234,
            completionTokens: 56,
            totalTokens: 1290,
            filterTokensSaved: 0,
            model: 'gpt-test',
            provider: 'TestProvider',
            durationMs: 5000,
          }),
        });
      }
      return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
    });

    win.spInitTokenUsageBar();

    await new Promise((r) => setTimeout(r, 30));

    const bar1 = win.document.getElementById('tug-bar');
    expect(bar1).not.toBeNull();

    expect(bar1.querySelector('.tug-pill--empty')).toBeNull();

    expect(bar1.getAttribute('data-tug-id')).toBe('4242');

    const totalPill = Array.from(bar1.querySelectorAll('.tug-pill'))
      .find((el) => /1\s*234/.test(el.textContent));
    expect(totalPill).toBeDefined();
  });
});
