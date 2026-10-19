import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { chromium } from 'playwright';

const BASE_URL = process.env.STACKLENS_UI_URL;
const RUN_LIVE_LAYOUT_TESTS = typeof BASE_URL === 'string' && /^https?:\/\//.test(BASE_URL);

const RESOLUTIONS = [
  { name: 'Playwright default (1280)', width: 1280, height: 800 },
  { name: 'FHD',                       width: 1920, height: 1080 },
  { name: '2K',                        width: 2560, height: 1440 },
  { name: '4K',                        width: 3840, height: 2160 },
  { name: '5K',                        width: 5120, height: 2880 },
];

async function measureSourcesTable(page) {
  await page.locator('button[data-kb-tab="sources"]').click();
  await page.waitForTimeout(400);
  return await page.evaluate(() => {
    const tbl = document.querySelector('.kb-source-table');
    if (!tbl) return null;
    const tblRect = tbl.getBoundingClientRect();
    const rows = Array.from(tbl.querySelectorAll('tbody tr')).map((row) => {
      const cells = row.querySelectorAll('td');
      const typeBadge = cells[1] && cells[1].querySelector('.badge');
      const typeCell = cells[1] ? cells[1].getBoundingClientRect() : null;
      const baseCell = cells[2] ? cells[2].getBoundingClientRect() : null;
      const actionCell = cells[6] ? cells[6].getBoundingClientRect() : null;
      const badgeRect = typeBadge ? typeBadge.getBoundingClientRect() : null;
      return {
        type: typeBadge ? typeBadge.textContent.trim() : null,
        badgeLeft:  badgeRect ? Math.round(badgeRect.left)  : null,
        badgeRight: badgeRect ? Math.round(badgeRect.right) : null,
        badgeWidth: badgeRect ? Math.round(badgeRect.width)  : null,
        typeCellRight: typeCell  ? Math.round(typeCell.right)  : null,
        typeCellWidth: typeCell  ? Math.round(typeCell.width)  : null,
        baseCellLeft:  baseCell  ? Math.round(baseCell.left)   : null,
        actionWidth:   actionCell ? Math.round(actionCell.width) : null,
      };
    });
    return { tableWidth: Math.round(tblRect.width), rows };
  });
}

describe.skipIf(!RUN_LIVE_LAYOUT_TESTS)('Knowledge source table layout — no column overlap', () => {
  let browser;

  beforeAll(async () => {
    browser = await chromium.launch();
    await fetch(`${BASE_URL}/api/config/language`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ language: 'de' }),
    });
  });
  afterAll(async () => { await browser.close(); });

  for (const res of RESOLUTIONS) {
    describe(`${res.name} (${res.width}x${res.height})`, () => {
      let page;
      beforeAll(async () => {
        page = await browser.newPage({ viewport: { width: res.width, height: res.height } });
        await page.goto(`${BASE_URL}/knowledge`, { waitUntil: 'domcontentloaded' });
        await page.waitForSelector('#sidebar', { state: 'visible' });
        await page.waitForTimeout(1500);
      });
      afterAll(async () => { await page.close(); });

      it('the type badge sits inside the TYP column (no overflow past the cell boundary)', async () => {
        const m = await measureSourcesTable(page);
        expect(m, 'no sources rendered — test cannot run without at least one row').not.toBeNull();
        for (const row of m.rows) {
          if (!row.badgeRight) continue;
          expect(
            row.badgeRight,
            `row "${row.type}" badge right=${row.badgeRight} overshoots TYP cell right=${row.typeCellRight}`,
          ).toBeLessThanOrEqual(row.typeCellRight + 0.5);
        }
      });

      it('the type badge does not overlap the BASE column (#NN starts after the badge ends)', async () => {
        const m = await measureSourcesTable(page);
        expect(m, 'no sources rendered — test cannot run without at least one row').not.toBeNull();
        for (const row of m.rows) {
          if (!row.badgeRight) continue;
          expect(
            row.badgeRight,
            `row "${row.type}" badge right=${row.badgeRight} overlaps BASE column left=${row.baseCellLeft} — ` +
              `this is the regression from screenshot wissen.png: "TICKET_REFINEMEN#53"`,
          ).toBeLessThanOrEqual(row.baseCellLeft + 0.5);
        }
      });

      it('the action column is at least 96 px wide so the sync + trash buttons fit on one line', async () => {
        const m = await measureSourcesTable(page);
        expect(m, 'no sources rendered — test cannot run without at least one row').not.toBeNull();
        for (const row of m.rows) {
          if (!row.actionWidth) continue;
          expect(
            row.actionWidth,
            `row "${row.type}" action column width=${row.actionWidth} px is below the 96 px minimum ` +
              `(sync 32 + margin 4 + trash 32 + cell padding 16 ≈ 84 px rounded up to 96)`,
          ).toBeGreaterThanOrEqual(96);
        }
      });
    });
  }
});

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const KB_CSS_PATH = resolve(__dirname, '../../main/resources/static/css/knowledge.css');
const kbCss = readFileSync(KB_CSS_PATH, 'utf8');

describe('Knowledge source table layout — static CSS pin (no server required)', () => {
  it('.kb-source-table uses table-layout: auto (was: fixed; caused the wissen.png overlap)', () => {
    const m = kbCss.match(/\.kb-source-table\s*\{\s*([^}]*)\}/);
    expect(m, '.kb-source-table rule not found in knowledge.css').not.toBeNull();
    expect(
      m[1],
      '.kb-source-table must declare `table-layout: auto` so each column sizes to its content ' +
        'instead of being pinned to the 12 % / 14 % / … percentages that caused TICKET_REFINEMENT ' +
        'to spill over the column boundary and overlap the #NN column (screenshot wissen.png).',
    ).toMatch(/table-layout:\s*auto/);
    const tableRules = kbCss;
    const kbSourceColWidths = tableRules.match(/\.kb-source-table\s+col\.[\w-]+\s*\{[^}]*width:\s*\d+%/g);
    expect(
      kbSourceColWidths,
      '.kb-source-table must not declare any percentage widths on <col> elements — auto layout ' +
        'computes widths from content, the percentages were the original cause of the overlap.',
    ).toBeNull();
  });

  it('.kb-source-table keeps min-width: 96px on the action cell so the buttons do not wrap', () => {
    const m = kbCss.match(
      /\.kb-source-table\s+(?:th|td):last-child\s*\{([^}]*)\}/,
    );
    expect(m, 'last-child rule for .kb-source-table not found in knowledge.css').not.toBeNull();
    expect(
      m[1],
      'last-child rule must declare min-width: 96px so the sync + trash pair stays on one line ' +
        'even when the table is rendered at narrow viewports (the regression the user warned ' +
        'about: "icons zum syncen und löschen dort bleiben wo sie sind").',
    ).toMatch(/min-width:\s*96px/);
    expect(
      m[1],
      'last-child rule must keep white-space: nowrap so the buttons cannot wrap onto two lines.',
    ).toMatch(/white-space:\s*nowrap/);
    expect(
      m[1],
      'last-child rule must keep text-align: right so the buttons stay flush to the right edge ' +
        'of the action column, matching the position they had in the screenshot.',
    ).toMatch(/text-align:\s*right/);
  });
});
