import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJs, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(here, '..', '..', '..');
const STATIC_DIR = resolve(ROOT, 'src', 'main', 'resources', 'static');
const SEARCH_FRAGMENT = resolve(ROOT, 'src', 'main', 'resources', 'templates', 'fragments', 'search', 'scripts.html');

function extractBlocks(path) {
  const html = readFileSync(path, 'utf8');
  const blocks = [];
  for (const m of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
    const srcMatch = m[1].match(/\b(?:th:src|src)\s*=\s*"(?:@\{)?\/?(js\/[^"}]+)\}?"/i);
    if (srcMatch) blocks.push(readFileSync(resolve(STATIC_DIR, srcMatch[1]), 'utf8'));
    else if (m[2].trim()) blocks.push(m[2]);
  }
  return blocks;
}

function boot(payload) {
  const dom = new JSDOM(
    '<!DOCTYPE html><html><body><div class="sl-page-header"></div>' +
      '<div id="suAiPlaceholder"></div><div id="suAiStatus"></div><div id="suAiSpinner"></div>' +
      '<div id="suAiContent"></div><div id="suTabKiSpinner"></div><div id="suTabKiIcon"></div>' +
      '</body></html>',
    { runScripts: 'dangerously', pretendToBeVisual: true, url: 'http://localhost/' },
  );
  const win = dom.window;
  win.i18n = { t: (k) => k };
  win.fetch = vi.fn(() => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(payload) }));
  win.clearInterval = vi.fn(win.clearInterval);
  win.setInterval = vi.fn(win.setInterval);
  runInWindow(win, readJs(resolve(STATIC_DIR, 'js/app.js')));
  extractBlocks(SEARCH_FRAGMENT).forEach((block) => runInWindow(win, block));
  if (typeof win.suPollAiAnalysis !== 'function') throw new Error('test setup: window.suPollAiAnalysis is not exposed');
  return win;
}

async function flush(times = 6) {
  for (let i = 0; i < times; i++) await Promise.resolve();
}

describe('search KI-Analyse polling terminates on every terminal status', () => {
  let win;

  afterEach(() => {
    if (win) try { win.close(); } catch (_) {}
  });

  beforeEach(() => {
    vi.useRealTimers();
  });

  it('treats NO_TOKEN as a terminal analysis status', () => {
    win = boot({ status: 'NO_TOKEN' });

    expect(win.suIsAiTerminalStatus('NO_TOKEN')).toBe(true);
    expect(win.suIsAiTerminalStatus('CANCELLED')).toBe(true);
    expect(win.suIsAiTerminalStatus('RUNNING')).toBe(false);
    expect(win.suIsAiTerminalStatus('PENDING')).toBe(false);
  });

  it('stops the poll timer when the analysis came back without a token', async () => {
    win = boot({ status: 'NO_TOKEN' });

    win.suPollAiAnalysis(1);
    await flush();

    expect(win.clearInterval).toHaveBeenCalled();
    expect(win.setInterval).not.toHaveBeenCalled();
    expect(win.document.getElementById('suAiStatus').style.display).not.toBe('flex');
  });

  it('leaves the running poll untouched for a non terminal status', async () => {
    win = boot({ status: 'RUNNING' });
    win.clearInterval.mockClear();
    win.setInterval.mockClear();

    win.suPollAiAnalysis(1);
    await flush();

    expect(win.clearInterval).not.toHaveBeenCalled();
    expect(win.setInterval).not.toHaveBeenCalled();
  });

  it('renders the result and stops once the analysis completed', async () => {
    win = boot({ status: 'COMPLETED', analysisText: 'fertig' });

    win.suPollAiAnalysis(1);
    await flush();

    expect(win.clearInterval).toHaveBeenCalled();
    expect(win.document.getElementById('suAiContent').style.display).toBe('');
  });
});
