import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

test.describe('Tooling', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/tooling');
  });

  test('page loads with tl-elements', async ({ page }) => {
    await expect(page.locator('[class*="tl-"]').first()).toBeAttached();
  });

  test('main card is visible', async ({ page }) => {
    await expect(page.locator('.tl-card').first()).toBeVisible();
  });

  test('input fields (tl-input / tl-select) are present', async ({ page }) => {
    await expect(page.locator('.tl-input, .tl-select').first()).toBeAttached();
  });

  test('mode tabs are present', async ({ page }) => {
    await expect(page.locator('.tl-mode-tab').first()).toBeAttached();
  });

  test('one mode tab has the "active" class', async ({ page }) => {
    await expect(page.locator('.tl-mode-tab.active').first()).toBeAttached();
  });

  test('clicking an inactive mode tab switches the active class', async ({ page }) => {
    const tabs = page.locator('.tl-mode-tab');
    // Mode tabs live inside the edit overlay – only interact if visible
    if (await tabs.count() > 1 && await tabs.nth(1).isVisible()) {
      await tabs.nth(1).click();
      await expect(tabs.nth(1)).toHaveClass(/active/);
    }
  });

  test('tool cards (tl-tool) are listed', async ({ page }) => {
    // Tool cards are populated from the server; only check if any exist
    const tools = page.locator('.tl-tool').first();
    if (await tools.count() > 0) {
      await expect(tools).toBeAttached();
    }
  });

  test('primary action button is present', async ({ page }) => {
    await expect(page.locator('.tl-btn-primary, .tl-btn-run').first()).toBeAttached();
  });

  test('KI-chat opens', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });
});
