import { test, expect } from '@playwright/test';

/**
 * Tests for the simulated GitLab Merge Request view (/simulated-mr/{id}).
 *
 * This page does NOT use the standard StackLens layout (no sidebar).
 * It fetches MR data from /api/ticket-implementation/{id} on load.
 * For a non-existent id the page shows an error message inside #gl-loading.
 */
test.describe('Simulated Merge Request Page', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/simulated-mr/1');
  });

  test('page responds with a 200 status', async ({ page }) => {
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/simulated-mr/1')),
      page.goto('/simulated-mr/1'),
    ]);
    // Thymeleaf renders the page; the initial HTTP response should be 200
    expect(response.status()).toBe(200);
  });

  test('top navigation bar is visible', async ({ page }) => {
    await expect(page.locator('.gl-nav')).toBeVisible();
  });

  test('"Simuliert · StackLens" badge is shown', async ({ page }) => {
    await expect(page.locator('.gl-sim-badge')).toBeVisible();
  });

  test('breadcrumb element is in the DOM', async ({ page }) => {
    await expect(page.locator('#gl-breadcrumb')).toBeAttached();
  });

  test('loading indicator is present on initial render', async ({ page }) => {
    // The loading div is always in the markup; it shows either a spinner or
    // an error message depending on whether the API call succeeded.
    await expect(page.locator('#gl-loading')).toBeAttached();
  });

  test('page shows error or content for id=1 (no crash)', async ({ page }) => {
    // After the fetch resolves, either #gl-content is shown (data found)
    // or #gl-loading shows an error icon.  Either way the page must not throw.
    await page.waitForFunction(
      () => {
        const loading = document.getElementById('gl-loading');
        const content = document.getElementById('gl-content');
        // content shown → fetch succeeded
        if (content && content.style.display !== 'none' && content.innerHTML.trim().length > 0) return true;
        // error inside loading → fetch failed gracefully
        if (loading && loading.innerHTML.includes('bi-exclamation-triangle')) return true;
        return false;
      },
      { timeout: 8_000 }
    );
    await expect(page.locator('.gl-nav')).toBeVisible();
  });

  test('no JavaScript errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await page.goto('/simulated-mr/1');
    await page.waitForTimeout(1_000);
    expect(errors).toHaveLength(0);
  });

  test('page title contains "Merge Request"', async ({ page }) => {
    await expect(page).toHaveTitle(/Merge Request/i);
  });
});
