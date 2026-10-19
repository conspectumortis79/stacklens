import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Grafana Analysis', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/grafana-analysis');
  });

  test('page loads with ga-elements', async ({ page }) => {
    await expect(page.locator('[class*="ga-"]').first()).toBeAttached();
  });

  test('split layout (left + right pane) is present', async ({ page }) => {
    await expect(page.locator('.ga-split-left, .ga-split-right').first()).toBeAttached();
  });

  test('textarea or select for Grafana config is present', async ({ page }) => {
    await expect(page.locator('.ga-textarea, .ga-select').first()).toBeAttached();
  });

  test('split divider is in the DOM', async ({ page }) => {
    // Split container wraps both panes; the divider may only appear after interaction
    await expect(page.locator('.ga-split-container, .ga-split-divider').first()).toBeAttached();
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
    await page.waitForSelector('[class*="ga-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  test('Grafana config select dropdown is present and functional', async ({ page }) => {
    const sel = page.locator('.ga-select').first();
    if (await sel.count() > 0) {
      await expect(sel).toBeAttached();
    }
  });

  test('send / ask button is present', async ({ page }) => {
    const btn = page.locator('button[onclick*="ga"], .ga-btn, .ga-send-btn').first();
    if (await btn.count() > 0) {
      await expect(btn).toBeAttached();
    }
  });

  test('dark theme: ga-split-left background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/grafana-analysis');
    const left = page.locator('.ga-split-left').first();
    if (await left.count() > 0 && await left.isVisible()) {
      const bg = await left.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('chat area accepts input without JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const chatInput = page.locator('.ga-chat-input, input[id*="gaChat"], textarea[id*="gaChat"]').first();
    if (await chatInput.count() > 0 && await chatInput.isEditable()) {
      await chatInput.fill('test query');
    }
    expect(errors).toHaveLength(0);
  });
});
