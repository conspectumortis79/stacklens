import { test, expect } from '@playwright/test';

/**
 * Tests for the simulated GitHub Pull Request view (/simulated-github-pr/{id}).
 *
 * This page does NOT use the standard StackLens layout (no sidebar).
 * It fetches PR data from /api/ticket-implementation/{id} on load.
 * For a non-existent id the page shows an error message inside #gh-loading.
 */
test.describe('Simulated GitHub Pull Request Page', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/simulated-github-pr/1');
  });

  test('page responds with a 200 status', async ({ page }) => {
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/simulated-github-pr/1')),
      page.goto('/simulated-github-pr/1'),
    ]);
    expect(response.status()).toBe(200);
  });

  test('top navigation bar is visible', async ({ page }) => {
    await expect(page.locator('.gh-nav')).toBeVisible();
  });

  test('"KI-Simuliert" badge is shown', async ({ page }) => {
    await expect(page.locator('.gh-sim-badge')).toBeVisible();
  });

  test('breadcrumb element is in the DOM', async ({ page }) => {
    await expect(page.locator('#gh-breadcrumb')).toBeAttached();
  });

  test('loading indicator is in the DOM', async ({ page }) => {
    await expect(page.locator('#gh-loading')).toBeAttached();
  });

  test('page shows error or content for id=1 (no crash)', async ({ page }) => {
    await page.waitForFunction(
      () => {
        const loading = document.getElementById('gh-loading');
        const content = document.getElementById('gh-content');
        if (content && content.innerHTML && content.innerHTML.trim().length > 0) return true;
        if (loading && loading.innerHTML.includes('bi-exclamation-triangle')) return true;
        return false;
      },
      { timeout: 8_000 }
    );
    await expect(page.locator('.gh-nav')).toBeVisible();
  });

  test('no JavaScript errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await page.goto('/simulated-github-pr/1');
    await page.waitForTimeout(1_000);
    expect(errors).toHaveLength(0);
  });

  test('page title contains "Pull Request"', async ({ page }) => {
    await expect(page).toHaveTitle(/Pull Request/i);
  });
});
