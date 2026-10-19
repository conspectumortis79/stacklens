import { test, expect } from '@playwright/test';

/**
 * Tests for the KI Code Plan page (/ki-code-plan/{id}).
 *
 * This page does NOT use the standard StackLens layout (no sidebar).
 * It fetches data from /api/ticket-implementation/{id} and renders a structured
 * code plan. For a non-existent id it shows an error inside #loading.
 */
test.describe('KI Code Plan Page', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/ki-code-plan/1');
  });

  test('page responds with a 200 status', async ({ page }) => {
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/ki-code-plan/1')),
      page.goto('/ki-code-plan/1'),
    ]);
    expect(response.status()).toBe(200);
  });

  test('custom navigation bar is visible', async ({ page }) => {
    await expect(page.locator('nav')).toBeVisible();
  });

  test('"KI Code-Plan" title is shown in nav', async ({ page }) => {
    await expect(page.locator('.nav-title')).toBeVisible();
    await expect(page.locator('.nav-title')).toContainText('KI Code-Plan');
  });

  test('"Schließen" back link is present', async ({ page }) => {
    const backLink = page.locator('.nav-back');
    await expect(backLink).toBeVisible();
    await expect(backLink).toContainText('Schließen');
  });

  test('loading indicator is in the DOM', async ({ page }) => {
    await expect(page.locator('#loading')).toBeAttached();
  });

  test('page shows error or plan content for id=1 (no crash)', async ({ page }) => {
    await page.waitForFunction(
      () => {
        const loading = document.getElementById('loading');
        const content = document.getElementById('content');
        if (content && content.style.display !== 'none' && content.innerHTML.trim().length > 0) return true;
        // error path: loading div is repopulated with an error message
        if (loading && loading.innerHTML.includes('bi-exclamation-triangle')) return true;
        // loading div may contain the error text directly
        if (loading && loading.innerHTML.includes('Fehler')) return true;
        return false;
      },
      { timeout: 8_000 }
    );
    await expect(page.locator('nav')).toBeVisible();
  });

  test('page title contains "KI Code-Plan"', async ({ page }) => {
    await expect(page).toHaveTitle(/KI Code-Plan/i);
  });

  test('no JavaScript errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await page.goto('/ki-code-plan/1');
    await page.waitForTimeout(1_000);
    expect(errors).toHaveLength(0);
  });
});
