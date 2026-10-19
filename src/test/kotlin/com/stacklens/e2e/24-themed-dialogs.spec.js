import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

/**
 * E2E tests for the in-app themed dialog helpers (showAlert / showConfirm).
 * These replace native window.alert() / window.confirm() so they are
 * compatible with JavaFX WebView and follow the application theme.
 */
test.describe('Themed in-app dialogs', () => {
  test('showAlert renders a Bootstrap alert in #alertArea', async ({ page }) => {
    await goto(page, '/config');
    await page.evaluate(() => window.showAlert('Test message', 'success'));
    await expect(page.locator('#alertArea .alert.alert-success')).toContainText('Test message');
  });

  test('showAlert dedupes identical messages', async ({ page }) => {
    await goto(page, '/config');
    await page.evaluate(() => {
      window.showAlert('Dedupe me', 'info');
      window.showAlert('Dedupe me', 'info');
      window.showAlert('Dedupe me', 'info');
    });
    await expect(page.locator('#alertArea .alert')).toHaveCount(1);
  });

  test('showConfirm displays the themed modal with title and body', async ({ page }) => {
    await goto(page, '/config');
    await page.evaluate(() => {
      window.showConfirm('Body text here', 'Title text', () => {}, 'OK', 'bi-check', '#1d6fa1');
    });
    await expect(page.locator('#confirmModal')).toBeVisible();
    await expect(page.locator('#confirmModalTitle')).toContainText('Title text');
    await expect(page.locator('#confirmModalMessage')).toContainText('Body text here');
    await expect(page.locator('#confirmModalOkBtn')).toContainText('OK');
  });

  test('showConfirm OK button invokes the callback', async ({ page }) => {
    await goto(page, '/config');
    await page.evaluate(() => {
      window.__confirmCalled = false;
      window.showConfirm('Are you sure?', 'Confirm', () => { window.__confirmCalled = true; });
    });
    await page.locator('#confirmModalOkBtn').click();
    expect(await page.evaluate(() => window.__confirmCalled)).toBe(true);
    await expect(page.locator('#confirmModal')).toBeHidden();
  });

  test('showConfirm Cancel button does NOT invoke the callback', async ({ page }) => {
    await goto(page, '/config');
    await page.evaluate(() => {
      window.__confirmCalled = false;
      window.showConfirm('Are you sure?', 'Confirm', () => { window.__confirmCalled = true; });
    });
    await page.locator('#confirmModal .btn-close').click();
    expect(await page.evaluate(() => window.__confirmCalled)).toBe(false);
    await expect(page.locator('#confirmModal')).toBeHidden();
  });

  test('showConfirm OK button has custom icon and color', async ({ page }) => {
    await goto(page, '/config');
    await page.evaluate(() => {
      window.showConfirm('msg', 'title', () => {}, 'Apply', 'bi-arrow-repeat', '#225599');
    });
    const btn = page.locator('#confirmModalOkBtn');
    await expect(btn.locator('i.bi-arrow-repeat')).toBeAttached();
    const bg = await btn.evaluate((el) => el.style.background);
    expect(bg).toContain('rgb(34, 85, 153)');
  });
});
