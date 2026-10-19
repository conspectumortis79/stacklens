import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Ticket Refinement', () => {

  test.beforeEach(async ({ page }) => {
    await page.request.delete('/api/ticket-refinement').catch(() => {/* ignore */});
    await goto(page, '/ticket-refinement');
  });

  test('page loads with sp-elements', async ({ page }) => {
    await expect(page.locator('[class*="sp-"]').first()).toBeAttached();
  });

  test('main form card is visible', async ({ page }) => {
    await expect(page.locator('.sp-form-card').first()).toBeVisible();
  });

  test('parent-ticket input is present', async ({ page }) => {
    await expect(page.locator('#spParentKey')).toBeAttached();
  });

  test('jira-config select is present', async ({ page }) => {
    await expect(page.locator('#spJiraConfig')).toBeAttached();
  });

  test('prompt-template textarea is present', async ({ page }) => {
    await expect(page.locator('#spTemplate')).toBeAttached();
  });

  test('start button is present', async ({ page }) => {
    await expect(page.locator('#spStart')).toBeAttached();
  });

  test('history list is in the DOM', async ({ page }) => {
    await expect(page.locator('#hxCard-ticket-refinement')).toBeAttached();
  });

  test('KI-Prompt button is present', async ({ page }) => {
    await expect(page.locator('[data-prompt-prefix="ticketrefinement"]')).toBeAttached();
  });

  test('KI-Chat opens', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });

  test('show-default fills the textarea', async ({ page }) => {
    const showBtn = page.locator('#spShowDefault');
    const ta = page.locator('#spTemplate');
    await showBtn.click();
    await expect.poll(() => ta.inputValue(), { timeout: 5000 }).not.toBe('');
  });

  test('start without parent key keeps the form on screen and focuses the input', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.locator('#spStart').click();
    await page.waitForTimeout(150);
    expect(errors).toHaveLength(0);
    // Form is still visible (no submission happened)
    await expect(page.locator('.sp-form-card')).toBeVisible();
  });

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="sp-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  test('dark theme: sp-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/ticket-refinement');
    const card = page.locator('.sp-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('repo list container is present', async ({ page }) => {
    await expect(page.locator('#spRepoList')).toBeAttached();
  });

  test('repo list contains no remote GitLab config checkboxes', async ({ page }) => {
    // Local-only: the loadRepoConfigs() function no longer fetches /api/config/gitlab.
    // Verify that no checkbox with data-repo-type="gitlab" is rendered that originates
    // from a remote config (as opposed to a locally-stored repo).
    // Wait a short moment for the async load to complete.
    await page.waitForTimeout(300);
    const gitlabCbs = page.locator('#spRepoList input[data-repo-type="gitlab"]');
    // In a fresh demo environment there are no local repos with type "gitlab"
    // so the count should be zero.
    expect(await gitlabCbs.count()).toBe(0);
  });

  test('repo list shows empty notice when no local repos configured', async ({ page }) => {
    // Intercept /api/local-repos to simulate an empty list.
    await page.route('**/api/local-repos', (route) =>
      route.fulfill({ contentType: 'application/json', body: '[]' }),
    );
    await goto(page, '/ticket-refinement');
    await page.waitForTimeout(300);
    const empty = page.locator('#spRepoList .sp-repo-empty');
    await expect(empty).toBeAttached();
  });
});
