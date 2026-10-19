import { describe, it, expect, beforeAll, afterAll, beforeEach, afterEach } from 'vitest';
import { chromium } from 'playwright';
import { installBrowserStubs, resetDom, readSource, loadScript } from './_load.js';

const CSS = readSource('css/history.css');
const JS = readSource('js/history-list.js');

function ruleBody(selector) {
  const start = CSS.indexOf(selector + ' {');
  if (start === -1) return null;
  const open = CSS.indexOf('{', start);
  return CSS.slice(open + 1, CSS.indexOf('}', open));
}

const LONG_NOTE = 'java.lang.IllegalStateException: OpenRouter error (HTTP 429): {"error":{"message":"Provider returned error","code":429,'
  + '"metadata":{"raw":"qwen/qwen3.8-flash is temporarily rate-limited upstream. Please retry shortly, or add your own key to accumulate '
  + 'your rate limits: https://openrouter.ai/settings/integrations","provider_name":"Alibaba","is_byok":false,'
  + '"limit_source":"upstream_provider_shared_pool","remedy_hint":"Retry shortly, add your own provider key"}}}';

const LONG_PROJECT = 'demo-group/a-remarkably-long-microservice-repository-name-for-load-testing';

const HISTORY_ITEMS = [
  { id: 9001, status: 'COMPLETED', createdAt: '2026-09-03T14:05:00', gitlabProjectPath: LONG_PROJECT, findingsJson: '[]' },
  { id: 9002, status: 'FAILED', createdAt: '2026-09-03T13:22:00', repoConfigId: 3, errorMessage: LONG_NOTE }
];

describe('Historientabelle — feste Breite der Projektspalte', () => {
  it('begrenzt die Projektspalte mit einer Mindestbreite statt sie ins Unendliche wachsen zu lassen', () => {
    const body = ruleBody('.hx-cell-title');
    expect(body).not.toBeNull();
    const min = /min-width:\s*(\d+)px/.exec(body);
    expect(min, '.hx-cell-title braucht ein min-width in px').not.toBeNull();
    expect(Number(min[1])).toBeGreaterThanOrEqual(120);
  });

  it('bricht lange Titel-, Pfad- und Fehlerspalten im Zellenumbruch statt in einer Zeile', () => {
    for (const selector of ['.hx-title-text', '.hx-title-sub', '.hx-title-note-text']) {
      const body = ruleBody(selector);
      expect(body, selector + ' fehlt in history.css').not.toBeNull();
      expect(body, selector + ' muss lange Wörter umbrechen').toContain('overflow-wrap: anywhere');
      expect(body, selector + ' darf die Spalte nicht einzeilig aufblähen').not.toMatch(/white-space:\s*nowrap/);
      expect(body, selector + ' darf keine starre Obergrenze besitzen').not.toMatch(/max-width:/);
    }
  });

  it('begrenzt den Fehlerhinweis auf mehrere Zeilen mit Ellipse', () => {
    const body = ruleBody('.hx-title-note-text');
    const clamp = /-webkit-line-clamp:\s*(\d+)/.exec(body);
    expect(clamp, 'der Fehlerhinweis muss auf mehrere Zeilen begrenzt werden').not.toBeNull();
    expect(Number(clamp[1])).toBeGreaterThanOrEqual(2);
    expect(body).toContain('overflow: hidden');
  });

  it('hält Status-, Befunde-, Erstellt- und Aktionsspalte in ihrer natürlichen Breite', () => {
    expect(ruleBody('.hx-badge')).toContain('white-space: nowrap');
    expect(ruleBody('.hx-cell-time')).toContain('white-space: nowrap');
    expect(ruleBody('.hx-cell-actions')).toContain('white-space: nowrap');
  });

  it('scrollt horizontal statt Spalten unsichtbar abzuschneiden, wenn kein Platz bleibt', () => {
    expect(ruleBody('.hx-body')).toContain('overflow-x: auto');
  });

  it('liefert den vollständigen Fehlersatz als title-Attribut der Hinweiszeile', () => {
    expect(JS).toContain('class="hx-title-note" title="\' + esc(note) + \'"');
    expect(JS).toContain('<span class="hx-title-note-text">\' + esc(note)');
  });
});

installBrowserStubs();

describe('slHistory — Projektzelle mit langem Fehlerhinweis', () => {
  beforeEach(() => {
    resetDom();
    loadScript(window, 'js/history-list.js');
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('kapselt den Hinweis in einen umbruchfähigen Textcontainer ohne Text zu verlieren', () => {
    const host = document.createElement('div');
    host.className = 'hx-card';
    document.body.appendChild(host);
    window.slHistory.mount({
      root: host,
      page: 'layouttest',
      columns: {
        title: { key: 'load_test.col_project', fallback: 'Projekt' },
        metric: { key: 'common.history_col_findings', fallback: 'Befunde' }
      },
      map: {
        id: a => a.id,
        status: a => a.status,
        createdAt: a => a.createdAt,
        title: a => a.label,
        note: a => a.error || ''
      },
      actions: { open: () => {} }
    }).setData([{ id: 1, label: 'stacklens', status: 'FAILED', createdAt: '2026-09-03T13:22:00', error: LONG_NOTE }]);

    const note = host.querySelector('.hx-title-note');
    const text = host.querySelector('.hx-title-note-text');
    expect(text).not.toBeNull();
    expect(note.getAttribute('title')).toBe(LONG_NOTE);
    expect(text.textContent).toBe(LONG_NOTE);
    expect(note.closest('.hx-cell-title')).not.toBeNull();
  });
});

const BASE_URL = process.env.STACKLENS_UI_URL;
const RUN_LIVE_LAYOUT_TESTS = typeof BASE_URL === 'string' && /^https?:\/\//.test(BASE_URL);

const RESOLUTIONS = [
  { name: 'Playwright default (1280)', width: 1280, height: 800 },
  { name: 'FHD', width: 1920, height: 1080 },
  { name: '2K', width: 2560, height: 1440 }
];

function measureLasttestHistory() {
  const card = document.getElementById('hxCard-lasttest-analysis');
  const table = card.querySelector('table.hx-table');
  const cardBox = card.getBoundingClientRect();
  const heads = Array.from(table.querySelectorAll('thead th'));
  const lastHead = heads[heads.length - 1].getBoundingClientRect();
  const cells = Array.from(table.querySelectorAll('tbody td, thead th'));
  return {
    cardRight: Math.round(cardBox.right),
    lastColumnRight: Math.round(lastHead.right),
    columnCount: heads.length,
    overflowingCells: cells.filter(c => c.scrollWidth > c.clientWidth + 1).length,
    rowHeights: Array.from(table.querySelectorAll('tbody tr')).map(r => Math.round(r.getBoundingClientRect().height))
  };
}

describe.skipIf(!RUN_LIVE_LAYOUT_TESTS)('Lasttest-Verlauf — alle Spalten sichtbar', () => {
  let browser;

  beforeAll(async () => {
    browser = await chromium.launch();
  });
  afterAll(async () => { await browser.close(); });

  for (const res of RESOLUTIONS) {
    it(`bleibt bei ${res.name} ohne abgeschnittene Spalten`, async () => {
      const page = await browser.newPage({ viewport: { width: res.width, height: res.height } });
      await page.goto(`${BASE_URL}/lasttest-analysis`, { waitUntil: 'networkidle' });
      await page.evaluate(items => ltHistory().setData(items), HISTORY_ITEMS);
      await page.waitForTimeout(200);
      const m = await page.evaluate(measureLasttestHistory);
      await page.close();
      expect(m.columnCount).toBe(5);
      expect(m.overflowingCells, 'Zellen laufen über ihre Spaltengrenze über').toBe(0);
      expect(m.lastColumnRight, `Aktionsspalte rechts über die Karte hinaus (Karte ${m.cardRight})`).toBeLessThanOrEqual(m.cardRight + 1);
      expect(Math.max(...m.rowHeights), 'Zeilenhöhe darf nicht unbegrenzt wachsen').toBeLessThanOrEqual(200);
    });
  }
});
