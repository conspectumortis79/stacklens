import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { makeWindow, runInWindow, extractFunction } from './support/fragmentLoader.js';
import { readKnowledgeBundle } from './support/knowledgeModules.js';

const source = readKnowledgeBundle();

function boot(payload) {
  const win = makeWindow(`
    <span id="kbSourceCount">0</span>
    <table id="kbSourceTable">
      <tbody id="kbSourceRows"></tbody>
    </table>
  `);
  runInWindow(win, `
    window.i18n = { t: function (k, fallback) { return fallback || k; } };
    function esc(s) { return String(s == null ? '' : s); }
    function t(k, fallback) { return (window.i18n && window.i18n.t) ? window.i18n.t(k, fallback) : (fallback || k); }
    function kbNotify() {}
  `);
  win.fetch = vi.fn((url) => {
    if (String(url).endsWith('/api/knowledge/sources')) {
      return Promise.resolve({ ok: true, json: () => Promise.resolve(payload) });
    }
    return Promise.reject(new Error('unexpected fetch: ' + url));
  });
  runInWindow(win, extractFunction(source, 'kbLoadSources'));
  runInWindow(win, extractFunction(source, 'kbFormatBytes'));
  runInWindow(win, extractFunction(source, 'kbFormatTimestamp'));
  return win;
}

function tick(win, n = 5) {
  let p = Promise.resolve();
  for (let i = 0; i < n; i++) p = p.then(() => {});
  return p;
}

describe('Wissensdatenbank Inhalte-Tab — Grösse column and compact timestamp', () => {
  let win;

  afterEach(() => { if (win) try { win.close(); } catch (_) {} });

  it('renders a size cell with the human-readable token and an exact-bytes tooltip', async () => {
    win = boot([
      { id: 1, name: 'Jira sprint', type: 'JIRA_SPRINT', knowledgeBaseId: 36, status: 'READY', updatedAt: '2026-08-15T13:56:38.840213', totalBytes: 2457312 },
    ]);
    win.kbLoadSources();
    await tick(win);
    const row = win.document.querySelector('#kbSourceRows').children[0];
    const html = row.innerHTML;
    expect(html, 'size cell must carry kb-source-size class').toMatch(/class="kb-source-size"/);
    expect(html, 'size badge must render MB token').toMatch(/<span class="kb-source-size-badge"[^>]*>2\.3 MB<\/span>/);
    expect(html, 'size tooltip must carry the exact byte count with thin-space separators').toMatch(/title="2\u202F457\u202F312 Bytes"/);
  });

  it('renders the timestamp without milliseconds and without the T separator', async () => {
    win = boot([
      { id: 1, name: 'Manual note', type: 'MANUAL', knowledgeBaseId: 1, status: 'READY', updatedAt: '2026-08-15T13:56:38.840213' },
    ]);
    win.kbLoadSources();
    await tick(win);
    const row = win.document.querySelector('#kbSourceRows').children[0];
    const cells = row.querySelectorAll('td');
    const updatedCell = cells[cells.length - 2];
    const text = updatedCell.textContent;
    expect(text, 'updated cell text must show YYYY-MM-DD HH:MM:SS').toBe('2026-08-15 13:56:38');
    expect(text, 'updated cell text must NOT carry the millisecond fraction').not.toMatch(/\.840213/);
    expect(text, 'updated cell text must NOT carry the T separator').not.toMatch(/T/);
  });

  it('renders an em-dash placeholder when totalBytes is null (freshly created source)', async () => {
    win = boot([
      { id: 1, name: 'Pending source', type: 'CONFLUENCE', knowledgeBaseId: 1, status: 'PENDING', updatedAt: '2026-08-15T13:56:38' },
    ]);
    win.kbLoadSources();
    await tick(win);
    const row = win.document.querySelector('#kbSourceRows').children[0];
    const html = row.innerHTML;
    expect(html, 'null totalBytes must render as em-dash placeholder').toMatch(/<span class="kb-source-size-badge">\u2014<\/span>/);
  });

  it('kbFormatBytes formats KB / MB / GB / B ranges correctly', () => {
    win = boot([]);
    const fmt = win.kbFormatBytes;
    expect(fmt(null)).toEqual({ display: '\u2014', tooltip: '' });
    expect(fmt(undefined)).toEqual({ display: '\u2014', tooltip: '' });
    expect(fmt(0)).toEqual({ display: '0 B', tooltip: '0 Bytes' });
    expect(fmt(512)).toEqual({ display: '512 B', tooltip: '512 Bytes' });
    expect(fmt(2457312).display).toBe('2.3 MB');
    expect(fmt(2457312).tooltip).toBe('2\u202F457\u202F312 Bytes');
    expect(fmt(866304).display).toBe('846 KB');
    expect(fmt(866304).tooltip).toBe('866\u202F304 Bytes');
    expect(fmt(1234567890).display).toBe('1.1 GB');
    expect(fmt(1024).display).toBe('1 KB');
  });

  it('kbFormatTimestamp drops milliseconds and the T separator', () => {
    win = boot([]);
    const ts = win.kbFormatTimestamp;
    expect(ts('2026-08-15T13:56:38.840213')).toBe('2026-08-15 13:56:38');
    expect(ts('2026-08-15T13:56:38')).toBe('2026-08-15 13:56:38');
    expect(ts(null)).toBe('');
    expect(ts('')).toBe('');
  });
});
