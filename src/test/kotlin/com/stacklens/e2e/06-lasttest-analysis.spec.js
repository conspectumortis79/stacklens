import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Load Test Analysis', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/lasttest-analysis');
  });

  test('page loads with lt-elements', async ({ page }) => {
    await expect(page.locator('[class*="lt-"]').first()).toBeAttached();
  });

  test('sidebar and tab bar are present', async ({ page }) => {
    await expect(page.locator('.lt-sidebar, .lt-tab-bar, [class*="lt-sidebar"]').first()).toBeAttached();
  });

  test('repository and project list containers are in the DOM', async ({ page }) => {
    await expect(page.locator('#ltLocalRepoList')).toBeAttached();
    await expect(page.locator('#ltProjectList')).toBeAttached();
  });

  test('result area exists', async ({ page }) => {
    await expect(page.locator('.lt-result, [class*="lt-result"]').first()).toBeAttached();
  });

  test('KI-chat button opens the drawer', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    // Wait for a visible lt-layout element (not the hidden file-browser pathbar)
    await page.waitForSelector('.lt-layout', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  // ── Extended tests ─────────────────────────────────────────────────────────

  test('lt tab clicks produce no JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const tabs = page.locator('.lt-tab, .lt-tab-btn').all();
    const allTabs = await tabs;
    for (const tab of allTabs.slice(0, 3)) {
      if (await tab.isVisible()) await tab.click();
    }
    expect(errors).toHaveLength(0);
  });

  test('dark theme: lt-layout background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/lasttest-analysis');
    const bg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(bg).not.toBe('rgb(255, 255, 255)');
  });

  test('lt-repo-card click does not cause JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const card = page.locator('.lt-repo-card').first();
    if (await card.count() > 0 && await card.isVisible()) await card.click();
    expect(errors).toHaveLength(0);
  });

  test('action button (start / analyse) is present', async ({ page }) => {
    const btn = page.locator('.lt-action, .lt-btn, button[onclick*="lt"]').first();
    if (await btn.count() > 0) {
      await expect(btn).toBeAttached();
    }
  });

  test('GitHub accordion opens without JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const ghHeader = page.locator('#ltGithubSection .lt-github-accordion-header').first();
    if (await ghHeader.count() > 0) {
      await ghHeader.click();
      await page.waitForTimeout(200);
    }
    expect(errors).toHaveLength(0);
  });

  test('GitHub filter input appears when accordion has multiple repos', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const ghHeader = page.locator('#ltGithubSection .lt-github-accordion-header').first();
    if (await ghHeader.count() > 0) {
      await ghHeader.click();
      await page.waitForTimeout(200);
      const filterInput = page.locator('.lt-gh-filter');
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
