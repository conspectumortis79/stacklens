import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

/**
 * Tests for the Tool View page (/tool/{id}).
 *
 * An unknown id redirects to /tooling.
 * For a real tool, the page shows the tool details with status badge and
 * action bar populated by tvInit().
 */
test.describe('Tool View Page', () => {

  test('unknown tool id redirects to /tooling', async ({ page }) => {
    await page.goto('/tool/999999');
    // After redirect, the tooling list page should load
    await page.waitForSelector('#sidebar', { state: 'visible' });
    expect(page.url()).toContain('/tooling');
  });

  test.describe('with a real tool', () => {
    let toolId;

    test.beforeEach(async ({ page }) => {
      // Create a tool via the API before navigating to the tool-view page
      const resp = await page.request.post('/api/tooling', {
        data: {
          name: 'E2E Test Tool',
          scriptContent: 'fun main() = println("hello e2e")',
        },
      });
      expect(resp.ok()).toBeTruthy();
      const body = await resp.json();
      toolId = body.id;
      expect(toolId).toBeTruthy();

      await goto(page, `/tool/${toolId}`);
    });

    test.afterEach(async ({ page }) => {
      // Clean up: delete the tool after each test
      if (toolId) {
        await page.request.delete(`/api/tooling/${toolId}`);
      }
    });

    test('tool-view page loads with tl-card', async ({ page }) => {
      await expect(page.locator('.tl-card').first()).toBeVisible();
    });

    test('status badge is visible', async ({ page }) => {
      await expect(page.locator('#tvStatusBadge')).toBeVisible();
    });

    test('action bar is in the DOM', async ({ page }) => {
      await expect(page.locator('#tvActionBar')).toBeAttached();
    });

    test('action bar has the tool id as data attribute', async ({ page }) => {
      const bar = page.locator('#tvActionBar');
      const dataId = await bar.getAttribute('data-tool-id');
      expect(String(dataId)).toBe(String(toolId));
    });

    test('page title contains the tool name', async ({ page }) => {
      await expect(page).toHaveTitle(/E2E Test Tool/i);
    });

    test('title icon badge is present', async ({ page }) => {
      await expect(page.locator('#tvTitleIcon')).toBeAttached();
    });

    test('sidebar is visible (standard layout)', async ({ page }) => {
      await expect(page.locator('#sidebar')).toBeVisible();
    });

    test('no JavaScript errors on page load', async ({ page }) => {
      const errors = [];
      page.on('pageerror', (err) => {
        // The tool-view page embeds the draw.io viewer (viewer.diagrams.net)
        // which loads additional resources from that CDN.  When the test
        // environment has no outbound HTTPS to viewer.diagrams.net, the
        // script load fails and surfaces here as a `pageerror`.  These CDN
        // failures are not bugs in StackLens code — they are an environmental
        // constraint of hermetic CI sandboxes — so we filter them out and
        // assert on the remaining (real) JavaScript errors only.
        if (err?.message && /viewer\.diagrams\.net/.test(err.message)) return;
        errors.push(err.message);
      });
      await goto(page, `/tool/${toolId}`);
      await page.waitForTimeout(500);
      expect(errors).toHaveLength(0);
    });
  });

});
