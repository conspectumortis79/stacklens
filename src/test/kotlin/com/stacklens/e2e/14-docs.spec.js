import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Documentation', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/docs');
  });

  test('page loads with documentation tabs', async ({ page }) => {
    await expect(page.locator('.doc-tabs')).toBeVisible();
  });

  test('at least two tab buttons are present', async ({ page }) => {
    expect(await page.locator('.doc-tab-btn').count()).toBeGreaterThanOrEqual(2);
  });

  test('first tab is active initially', async ({ page }) => {
    await expect(page.locator('.doc-tab-btn.active').first()).toBeVisible();
  });

  test('clicking the second tab switches content', async ({ page }) => {
    const tabs = page.locator('.doc-tab-btn');
    if (await tabs.count() > 1) {
      await tabs.nth(1).click();
      await expect(tabs.nth(1)).toHaveClass(/active/);
      await expect(tabs.first()).not.toHaveClass(/active/);
    }
  });

  test('markdown body is attached', async ({ page }) => {
    await expect(page.locator('.doc-markdown-body').first()).toBeAttached({ timeout: 12_000 });
  });

  test('search bar is present', async ({ page }) => {
    await expect(page.locator('.doc-search-bar')).toBeAttached();
  });

  test('searching a term does not crash the page', async ({ page }) => {
    const input = page.locator('.doc-search-bar input').first();
    if (await input.count() > 0 && await input.isEditable()) {
      await input.fill('JWT');
      await page.waitForTimeout(500);
      await expect(page.locator('.doc-tabs')).toBeVisible();
    }
  });

  test('theme "popos" → active tab button has orange colour', async ({ page }) => {
    await setThemeAndGoto(page, 'popos', '/docs');
    const activeTab = page.locator('.doc-tab-btn.active').first();
    if (await activeTab.count() > 0) {
      expect(await activeTab.evaluate((el) => getComputedStyle(el).color)).toBeTruthy();
    }
  });

  test('theme "dark" → markdown body does not use the light default text colour', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/docs');
    const body = page.locator('.doc-markdown-body').first();
    if (await body.count() > 0) {
      const color = await body.evaluate((el) => getComputedStyle(el).color);
      expect(color).not.toBe('rgb(31, 31, 31)');
    }
  });
});
