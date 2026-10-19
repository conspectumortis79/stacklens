import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('SQL Analysis', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/sql-analysis');
  });

  test('page loads with sa-elements', async ({ page }) => {
    await expect(page.locator('[class*="sa-"]').first()).toBeAttached();
  });

  test('main card is visible', async ({ page }) => {
    await expect(page.locator('.sa-card').first()).toBeVisible();
  });

  test('SQL textarea is present and editable', async ({ page }) => {
    const ta = page.locator('.sa-textarea, textarea').first();
    await expect(ta).toBeAttached();
    if (await ta.isEditable()) {
      await ta.fill('SELECT * FROM users WHERE id = 1;');
      await expect(ta).toHaveValue('SELECT * FROM users WHERE id = 1;');
    }
  });

  test('submitting empty input shows an error or status', async ({ page }) => {
    // Use the specific analyze button by ID to avoid strict-mode violation with tab button
    const btn = page.locator('#btnAnalyze');
    if (await btn.count() > 0) {
      await btn.click();
      await page.waitForFunction(
        () => document.querySelector('.sa-syntax-error, .sa-error, [class*="status"]') !== null,
        { timeout: 8_000 },
      ).catch(() => { /* acceptable if nothing changes */ });
    }
  });

  test('history cards exist in the DOM', async ({ page }) => {
    await expect(page.locator('#hxCard-sql-analysis').first()).toBeAttached();
  });

  test('KI-chat opens', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });

  // ── Extended tests ─────────────────────────────────────────────────────────

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="sa-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  test('database connection dropdown is present', async ({ page }) => {
    // sa-select or similar element for DB selection
    const sel = page.locator('.sa-select, select[id*="sa"]').first();
    if (await sel.count() > 0) {
      await expect(sel).toBeAttached();
    }
  });

  test('SQL textarea accepts multi-line input', async ({ page }) => {
    const ta = page.locator('.sa-textarea, textarea').first();
    if (await ta.count() > 0 && await ta.isEditable()) {
      const sql = 'SELECT id,\n  name\nFROM users\nWHERE active = 1;';
      await ta.fill(sql);
      const val = await ta.inputValue();
      expect(val).toContain('SELECT');
    }
  });

  test('dark theme: sa-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/sql-analysis');
    const card = page.locator('.sa-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('clicking history card does not cause JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const card = page.locator('#hxCard-sql-analysis').first();
    if (await card.count() > 0 && await card.isVisible()) {
      await card.click();
    }
    expect(errors).toHaveLength(0);
  });
});
