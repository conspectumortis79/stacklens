import { test, expect } from '@playwright/test';
import { goto, ALL_PAGES } from './helpers.js';

const baseUrl = new URL(process.env.BASE_URL || 'http://127.0.0.1:8091');

/**
 * Navigation & Sidebar
 * Verifies every page is reachable (HTTP < 400), the sidebar renders,
 * and the active link is correctly highlighted.
 */
test.describe('Sidebar Navigation', () => {

  test('home redirects to a known page', async ({ page }) => {
    const res = await page.goto('/');
    expect(res?.status()).toBeLessThan(400);
    await page.waitForSelector('#sidebar', { state: 'visible' });
    const currentUrl = new URL(page.url());
    expect(currentUrl.origin).toBe(baseUrl.origin);
    expect(currentUrl.pathname).toMatch(/^\//);
  });

  test('sidebar contains all navigation links', async ({ page }) => {
    await goto(page, '/');
    const nav = page.locator('#sidebarNav');
    await expect(nav).toBeVisible();

    // Use data-nav-id selectors so the test is language-independent
    const expectedNavIds = [
      'security-analysis', 'code-analysis', 'pipeline-analysis', 'grafana-analysis', 'jwt-analysis',
      'lasttest-analysis', 'review-analysis', 'sql-analysis', 'ticket-implementation',
      'ticket-creation', 'ticket-refinement', 'ticket-summary', 'sprint-review',
      'code-qa', 'tooling', 'search', 'config', 'logging',
      'docs', 'image-extraction', 'knowledge',
    ];
    for (const navId of expectedNavIds) {
      await expect(nav.locator(`[data-nav-id="${navId}"]`)).toBeVisible();
    }
  });

  test('sidebar brand "StackLens" is visible', async ({ page }) => {
    await goto(page, '/');
    await expect(page.locator('.sl-brand-text')).toContainText('StackLens');
  });

  test('quit button opens confirmation dialog', async ({ page }) => {
    await goto(page, '/');
    await page.locator('#sidebarQuitBtn').click();
    await expect(page.locator('#slQuitDialog')).toBeVisible();
    // cancel closes the dialog
    await page.locator('.sl-quit-btn-cancel').click();
    await expect(page.locator('#slQuitOverlay')).not.toHaveClass(/sl-quit-open/);
  });

  test('quit dialog closes on Escape', async ({ page }) => {
    await goto(page, '/');
    await page.locator('#sidebarQuitBtn').click();
    await expect(page.locator('#slQuitDialog')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('#slQuitOverlay')).not.toHaveClass(/sl-quit-open/);
  });

  for (const { path, name, rootSelector } of ALL_PAGES) {
    test(`${name} (${path}) loads without error`, async ({ page }) => {
      const res = await page.goto(path);
      expect(res?.status()).toBeLessThan(400);
      await page.waitForSelector('#sidebar', { state: 'visible' });
      // at least one page-specific element must be in the DOM
      await expect(page.locator(rootSelector).first()).toBeAttached({ timeout: 12_000 });
    });
  }

  test('active sidebar link is marked correctly', async ({ page }) => {
    await goto(page, '/jwt-analysis');
    const activeLink = page.locator('#sidebarNav .nav-link.active');
    await expect(activeLink).toBeVisible();
    await expect(activeLink).toContainText('JWT', { ignoreCase: true });
  });

  test('KI-chat button in page actions opens the chat drawer', async ({ page }) => {
    await goto(page, '/jwt-analysis');
    const kiBtn = page.getByRole('button', { name: /KI Chat/i });
    await kiBtn.click();
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
    await page.locator('#kiChatDrawer button[onclick*="kiChatClose"]').click();
    await expect(page.locator('#kiChatDrawer')).toBeHidden();
  });
});
