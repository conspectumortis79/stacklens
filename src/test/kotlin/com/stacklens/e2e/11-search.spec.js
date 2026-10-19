import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

test.describe('Search', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/search');
  });

  test('page loads with su-elements', async ({ page }) => {
    await expect(page.locator('[class*="su-"]').first()).toBeAttached();
  });

  test('search input is present and focusable', async ({ page }) => {
    const input = page.locator('.su-input, input[type="search"], input[type="text"]').first();
    await expect(input).toBeAttached();
    if (await input.isEditable()) {
      await input.focus();
      await input.fill('SELECT');
      await expect(input).toHaveValue('SELECT');
    }
  });

  test('source selection controls are present', async ({ page }) => {
    await expect(page.locator('.su-src-pill')).toHaveCount(5);
    await page.locator('#suAdvToggleBtn').click();
    await expect(page.locator('.su-src-row')).toHaveCount(5);
  });

  test('clicking a source pill toggles the selected state', async ({ page }) => {
    const pill = page.locator('#suPillGitlab');
    await expect(pill).toHaveClass(/on/);
    await pill.click();
    await expect(pill).not.toHaveClass(/on/);
    await pill.click();
    await expect(pill).toHaveClass(/on/);
  });

  test('tab bar is present', async ({ page }) => {
    await expect(page.locator('.su-tab').first()).toBeAttached();
  });

  test('"select all" database button is present', async ({ page }) => {
    await expect(page.locator('.su-db-select-all-btn').first()).toBeAttached();
  });

  test('source rows are listed', async ({ page }) => {
    await expect(page.locator('.su-src-row').first()).toBeAttached();
  });

  test('empty search does not crash the page', async ({ page }) => {
    const input = page.locator('.su-input, input').first();
    if (await input.isEditable()) {
      await input.fill('');
      await input.press('Enter');
      await expect(page.locator('#sidebar')).toBeVisible();
    }
  });
});
