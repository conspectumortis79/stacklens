import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

test.describe('Ticket Creation — refresh during generation', () => {

  test('progress card visible after Cmd+R / reload during active generation', async ({ page, context }) => {
    // Intercept generate to NEVER respond (simulates a long-running AI call still in flight)
    await context.route('**/api/ticket-creation/generate', async (route) => {
      // Hang indefinitely
      await new Promise(() => {});
    });

    // History returns empty so the restore poll keeps spinning
    await context.route('**/api/ticket-creation/history**', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([]),
      }),
    );

    await goto(page, '/ticket-creation');
    await expect(page.locator('#tcInputText')).toBeAttached();

    await page.locator('#tcInputText').fill('A long input that triggers a slow AI generation');
    await page.locator('#tcStart').click();

    // Confirm: BEFORE reload, the progress card is visible
    await expect(page.locator('#tcProgressCard')).toBeVisible({ timeout: 3000 });

    // Snapshot sessionStorage so we know what was there before reload
    const beforeReload = await page.evaluate(() => sessionStorage.getItem('tc_running_state'));
    expect(beforeReload).not.toBeNull();

    // Hard reload — simulates Cmd+R / Ctrl+R
    await page.reload({ waitUntil: 'domcontentloaded' });

    // Snapshot sessionStorage immediately after reload
    const afterReload = await page.evaluate(() => sessionStorage.getItem('tc_running_state'));

    // THIS is the bug: after refresh the progress card should still be visible
    await expect(page.locator('#tcProgressCard')).toBeVisible({ timeout: 5000 });
  });

});
