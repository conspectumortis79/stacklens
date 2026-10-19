import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { makeWindow, runInWindow, extractFunction } from './support/fragmentLoader.js';
import { readKnowledgeBundle } from './support/knowledgeModules.js';

const source = readKnowledgeBundle();

function boot() {
  const win = makeWindow(  `
    <main>
      <span id="kbSourceCount">0</span>
      <table id="kbSourceTable">
        <tbody id="kbSourceRows"></tbody>
      </table>
    </main>
  `);

  runInWindow(win, `
    window.i18n = { t: function (k, fallback) { return fallback || k; } };
    function esc(s) { return String(s == null ? '' : s); }
    function t(k, fallback) { return (window.i18n && window.i18n.t) ? window.i18n.t(k, fallback) : (fallback || k); }
    function kbNotify() {}
  `);

  win.fetch = vi.fn((url) => {
    if (String(url).endsWith('/api/knowledge/sources')) {
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve([
          { id: 1, name: 'Manual source', type: 'MANUAL', knowledgeBaseId: 1, status: 'READY', updatedAt: '2026-01-01' },
          { id: 2, name: 'File source',   type: 'FILE',   knowledgeBaseId: 1, status: 'READY', updatedAt: '2026-01-01' },
          { id: 3, name: 'Confluence page', type: 'CONFLUENCE', knowledgeBaseId: 1, status: 'READY', updatedAt: '2026-01-01' },
          { id: 4, name: 'Jira ticket',  type: 'JIRA',   knowledgeBaseId: 1, status: 'READY', updatedAt: '2026-01-01' },
          { id: 5, name: 'Review analysis', type: 'REVIEW_ANALYSIS', knowledgeBaseId: 1, status: 'READY', updatedAt: '2026-01-01' },
        ]),
      });
    }
    return Promise.reject(new Error('unexpected fetch: ' + url));
  });
  runInWindow(win, extractFunction(source, 'kbLoadSources'));
  runInWindow(win, extractFunction(source, 'kbFormatBytes'));
  runInWindow(win, extractFunction(source, 'kbFormatTimestamp'));
  return win;
}

function tickMicrotasks(win, n = 5) {
  let p = Promise.resolve();
  for (let i = 0; i < n; i++) p = p.then(() => {});
  return p;
}

describe('Wissensdatenbank source-list action column — sync button visibility', () => {
  let win;

  beforeEach(() => {
    win = boot();
  });

  afterEach(() => {
    if (win) try { win.close(); } catch (_) {   }
  });

  it('renders the sync button for CONFLUENCE sources', async () => {
    win.kbLoadSources();
    await tickMicrotasks(win);
    const confluenceRow = win.document.querySelector('#kbSourceRows').children[2];
    const html = confluenceRow.innerHTML;
    expect(html).toMatch(/kbSyncSource\(3\)/);
    expect(html).toMatch(/bi-arrow-clockwise/);
  });

  it('renders the sync button for JIRA sources', async () => {
    win.kbLoadSources();
    await tickMicrotasks(win);
    const jiraRow = win.document.querySelector('#kbSourceRows').children[3];
    const html = jiraRow.innerHTML;
    expect(html).toMatch(/kbSyncSource\(4\)/);
    expect(html).toMatch(/bi-arrow-clockwise/);
  });

  it('renders the sync button for auto-imported sources (REVIEW_ANALYSIS)', async () => {
    win.kbLoadSources();
    await tickMicrotasks(win);
    const reviewRow = win.document.querySelector('#kbSourceRows').children[4];
    const html = reviewRow.innerHTML;
    expect(html).toMatch(/kbSyncSource\(5\)/);
    expect(html).toMatch(/bi-arrow-clockwise/);
  });

  it('does NOT render the sync button for MANUAL sources', async () => {
    win.kbLoadSources();
    await tickMicrotasks(win);
    const manualRow = win.document.querySelector('#kbSourceRows').children[0];
    const html = manualRow.innerHTML;
    expect(html, 'sync button must be hidden for MANUAL sources').not.toMatch(/kbSyncSource\(1\)/);
    expect(html, 'sync icon must be hidden for MANUAL sources').not.toMatch(/bi-arrow-clockwise/);

    expect(html).toMatch(/kbDeleteSource\(1\)/);
  });

  it('does NOT render the sync button for FILE sources', async () => {
    win.kbLoadSources();
    await tickMicrotasks(win);
    const fileRow = win.document.querySelector('#kbSourceRows').children[1];
    const html = fileRow.innerHTML;
    expect(html, 'sync button must be hidden for FILE sources').not.toMatch(/kbSyncSource\(2\)/);
    expect(html, 'sync icon must be hidden for FILE sources').not.toMatch(/bi-arrow-clockwise/);

    expect(html).toMatch(/kbDeleteSource\(2\)/);
  });

  it('always renders the delete button for every source type', async () => {
    win.kbLoadSources();
    await tickMicrotasks(win);
    const rows = win.document.querySelector('#kbSourceRows').children;
    for (let i = 0; i < rows.length; i++) {
      const html = rows[i].innerHTML;
      expect(html, `delete button must be present on row ${i}`).toMatch(/kbDeleteSource\(/);
    }
  });
});
