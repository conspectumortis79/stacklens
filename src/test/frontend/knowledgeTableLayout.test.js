import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { chromium } from 'playwright';

const BASE_URL = process.env.STACKLENS_UI_URL;
const RUN_LIVE_LAYOUT_TESTS = typeof BASE_URL === 'string' && /^https?:\/\//.test(BASE_URL);

const RESOLUTIONS = [
  { name: 'FHD', width: 1920, height: 1080 },
  { name: '2K',  width: 2560, height: 1440 },
  { name: '4K',  width: 3840, height: 2160 },
  { name: '5K',  width: 5120, height: 2880 },
];

async function measure(page) {
  await page.locator('button[data-kb-tab="bases"]').click();
  await page.waitForTimeout(400);
  return await page.evaluate(() => {
    const get = (el) => el ? {
      fs: getComputedStyle(el).fontSize,
      fw: getComputedStyle(el).fontWeight,
    } : null;
    const rect = (el) => el ? el.getBoundingClientRect() : null;
    const baseName = document.querySelector('#kbBaseRows td.kb-base-name');
    const desc = document.querySelector('#kbBaseRows td.text-muted');
    const th = document.querySelector('th');
    const rows = Array.from(document.querySelectorAll('#kbBaseRows tr'));
    const rowInfo = rows.map((row) => {
      const cells = row.querySelectorAll('td');
      const statusCell = cells[3];
      const actionCell = cells[4];
      const status = statusCell
        ? Array.from(statusCell.querySelectorAll('.badge, .kb-system-tag'))
            .map((b) => ({ text: b.textContent.trim(), y: Math.round(b.getBoundingClientRect().y) }))
        : [];
      const actions = actionCell
        ? Array.from(actionCell.querySelectorAll('.btn'))
            .map((b) => ({ y: Math.round(b.getBoundingClientRect().y) }))
        : [];
      return { status, actions };
    });
    return {
      baseName: get(baseName),
      desc: get(desc),
      th: get(th),
      rows: rowInfo,
    };
  });
}

describe.skipIf(!RUN_LIVE_LAYOUT_TESTS)('Knowledge bases table layout', () => {
  let browser;

  beforeAll(async () => {
    browser = await chromium.launch();
    await fetch(`${BASE_URL}/api/config/language`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ language: 'de' }),
    });
  });
  afterAll(async () => {
    await browser.close();
  });

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

      it('base name is rendered at 16px / 600 semibold (was 900 extra-bold)', async () => {
        const m = await measure(page);
        expect(m.baseName).not.toBeNull();
        expect(m.baseName.fs).toBe('16px');
        expect(parseInt(m.baseName.fw, 10)).toBeLessThanOrEqual(700);
      });

      it('description is rendered at 14px / 400 (was 900 extra-bold)', async () => {
        const m = await measure(page);
        expect(m.desc).not.toBeNull();
        expect(m.desc.fs).toBe('14px');
        expect(parseInt(m.desc.fw, 10)).toBeLessThanOrEqual(500);
      });

      it('TH header is rendered at 11px / 700 bold (was 800 extra-bold)', async () => {
        const m = await measure(page);
        expect(m.th).not.toBeNull();
        expect(m.th.fs).toBe('11px');
        expect(parseInt(m.th.fw, 10)).toBeLessThanOrEqual(700);
      });

      it('status badges and action buttons share the same Y in every row', async () => {
        const m = await measure(page);
        for (const row of m.rows) {
          if (row.actions.length === 0) continue;
          const statusYs = row.status.map((s) => s.y);
          const statusSet = new Set(statusYs);
          expect(statusSet.size, `status badges in row ${JSON.stringify(row.status)} should share one Y`).toBe(1);
          const actionYs = row.actions.map((a) => a.y);
          const actionSet = new Set(actionYs);
          expect(actionSet.size, `action buttons should share one Y, got ${JSON.stringify(actionYs)}`).toBe(1);
          const statusY = statusYs[0];
          const actionY = actionYs[0];
          expect(
            Math.abs(statusY - actionY),
            `row status Y (${statusY}) and action Y (${actionY}) drift by more than 2 px`,
          ).toBeLessThanOrEqual(2);
        }
      });

      it('the "System" tag on systemManaged bases is rendered as a badge', async () => {
        const before = await page.evaluate(() =>
          document.querySelectorAll('#kbBaseRows .kb-system-tag').length
        );
        if (before === 0) {
          await fetch(`${BASE_URL}/api/knowledge/bases`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              name: `e2e-systembase-${Date.now()}`,
              description: 'auto-created by layout test',
              includeInGlobalChat: true,
              systemManaged: true,
            }),
          });
          await page.reload({ waitUntil: 'domcontentloaded' });
          await page.waitForSelector('#sidebar', { state: 'visible' });
          await page.waitForTimeout(1500);
          await page.locator('button[data-kb-tab="bases"]').click();
          await page.waitForTimeout(400);
        }
        const m = await measure(page);
        const systemRow = m.rows.find((r) => r.status.some((s) => s.text === 'System'));
        expect(systemRow, 'no row with a System tag found').toBeDefined();
        const allYs = systemRow.status.map((s) => s.y);
        expect(new Set(allYs).size, 'System tag Y differs from badges in the same row').toBe(1);
      });
    });
  }
});
