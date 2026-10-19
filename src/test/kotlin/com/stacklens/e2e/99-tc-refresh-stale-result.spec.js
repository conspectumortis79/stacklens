import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

test.describe('Ticket Creation — refresh during generation must show progress, not stale result', () => {

  test('immediate refresh during new generation shows progress, NOT a previous history result', async ({ page, context }) => {
    // Simulate that the history already contains a PREVIOUS completed ticket
    // from an earlier run.  This is the realistic state the user described.
    const PREV_TICKET = {
      id: 42,
      inputText: 'Some totally different previous request',
      summary: 'OLD PREVIOUS TICKET',
      issueType: 'Story',
      labels: 'old',
      resultJson: JSON.stringify({
        summary: 'OLD PREVIOUS TICKET',
        description: 'This is from a previous generation — must NOT be shown on refresh.',
        issueType: 'Story',
        labels: ['old'],
      }),
      status: 'COMPLETED',
      createdAt: '2026-05-26T10:00:00',
    };

    await context.route('**/api/ticket-creation/history**', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([PREV_TICKET]),
      }),
    );

    // /generate hangs forever to simulate an in-flight AI call
    await context.route('**/api/ticket-creation/generate', async () => {
      await new Promise(() => {});
    });

    await goto(page, '/ticket-creation');

    // Start a NEW generation with a different input
    await page.locator('#tcInputText').fill('Brand new request typed right now');
    await page.locator('#tcStart').click();

    // Progress card visible before refresh
    await expect(page.locator('#tcProgressCard')).toBeVisible({ timeout: 3000 });

    // Hard refresh while the generation is still "running"
    await page.reload();

    // ── After refresh ───────────────────────────────────────────────────────
    // The progress card MUST be visible because the generation is still going.
    await expect(page.locator('#tcProgressCard')).toBeVisible({ timeout: 5000 });

    // The result card MUST NOT show the stale previous ticket.
    // Wait a bit to give any erroneous restore-poll time to fire.
    await page.waitForTimeout(2500);

    const resultVisible = await page.locator('#tcResultCard').isVisible();
    const resultCardText = resultVisible ? await page.locator('#tcResultCard').innerText() : '';


    // The stale ticket may legitimately appear in the history table — that's
    // fine.  What MUST NOT happen is the result card rendering it as the
    // current generation's result.
    expect(resultVisible).toBe(false);
    expect(resultCardText).not.toContain('OLD PREVIOUS TICKET');

    // And the progress card must still be visible (the generation is still pending)
    await expect(page.locator('#tcProgressCard')).toBeVisible();
  });

});
