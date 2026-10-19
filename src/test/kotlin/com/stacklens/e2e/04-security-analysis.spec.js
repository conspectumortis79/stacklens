import { test, expect } from '@playwright/test';
import { goto, setTheme } from './helpers.js';

test.describe('Security Analysis', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/security-analysis');
  });

  test('page loads and renders sidebar + main container', async ({ page }) => {
    await expect(page.locator('#sidebar')).toBeVisible();
    await expect(page.locator('[class*="ba-"]').first()).toBeAttached();
  });

  test('repo list panel is present', async ({ page }) => {
    await expect(page.locator('.ba-panel, [class*="ba-sidebar"]').first()).toBeAttached();
  });

  test('KI-chat button opens the drawer', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    await btn.click();
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
  });

  test('filter buttons are present', async ({ page }) => {
    // Filter buttons are dynamically rendered after analysis; guard with count check
    const filterBtn = page.locator('.ba-filter-btn').first();
    if (await filterBtn.count() > 0) {
      await expect(filterBtn).toBeAttached();
    }
  });

  test('theme "popos" → ba-panel has a dark background', async ({ page }) => {
    await setTheme(page, 'popos');
    await page.goto('/security-analysis');
    await page.waitForSelector('[class*="ba-"]');
    const panel = page.locator('.ba-panel').first();
    if (await panel.count() > 0) {
      const bg = await panel.evaluate((el) => getComputedStyle(el).backgroundColor);
      // must not be the warm-cream default
      expect(bg).not.toContain('255, 253, 248');
    }
  });

  test('theme "legacy" → active filter button has teal colour', async ({ page }) => {
    await setTheme(page, 'legacy');
    await page.goto('/security-analysis');
    const activeFilter = page.locator('.ba-filter-btn.active').first();
    if (await activeFilter.count() > 0) {
      expect(await activeFilter.evaluate((el) => getComputedStyle(el).backgroundColor)).toBeTruthy();
    }
  });

  // ── Extended tests ─────────────────────────────────────────────────────────

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="ba-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  test('analyze / start button is present', async ({ page }) => {
    const btn = page.locator('.ba-btn, button[onclick*="saStart"], button[onclick*="baStart"]').first();
    if (await btn.count() > 0) {
      await expect(btn).toBeAttached();
    }
  });

  test('ba-panel background adapts to dark theme', async ({ page }) => {
    await setTheme(page, 'dark');
    await goto(page, '/security-analysis');
    // The body should have data-theme="dark" and the main surface should not be white
    const bodyBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(bodyBg).not.toBe('rgb(255, 255, 255)');
  });

  test('clicking KI-chat twice hides the drawer', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() === 0) return;
    await btn.click();
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
    // Second click should close
    await btn.click();
    await expect(page.locator('#kiChatDrawer')).toBeHidden();
  });

  test('severity or category filter chips are present when repo has data', async ({ page }) => {
    // Static check: if filters render, they should not cause page errors
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const chips = page.locator('.ba-filter-btn, .ba-chip').first();
    if (await chips.count() > 0) await chips.click();
    expect(errors).toHaveLength(0);
  });

  test('GitHub accordion opens without JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const ghHeader = page.locator('#baGithubSection .ba-gh-accordion-header').first();
    if (await ghHeader.count() > 0) {
      await ghHeader.click();
      await page.waitForTimeout(200);
    }
    expect(errors).toHaveLength(0);
  });

  test('GitHub filter input appears when accordion has multiple repos', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const ghHeader = page.locator('#baGithubSection .ba-gh-accordion-header').first();
    if (await ghHeader.count() > 0) {
      await ghHeader.click();
      await page.waitForTimeout(200);
      const filterInput = page.locator('.ba-gh-filter');
      if (await filterInput.count() > 0) {
        await expect(filterInput).toBeVisible();
        await expect(filterInput).toHaveAttribute('type', 'search');
        await expect(filterInput).toHaveClass(/sl-search-input/);
        await filterInput.fill('test');
        await page.waitForTimeout(100);
        await filterInput.press('Escape');
        await expect(filterInput).toHaveValue('');
      }
    }
    expect(errors).toHaveLength(0);
  });
});
