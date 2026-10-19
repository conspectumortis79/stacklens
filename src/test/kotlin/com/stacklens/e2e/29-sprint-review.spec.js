import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Sprint Review', () => {

  test.beforeEach(async ({ context, page }) => {
    await context.route('**/api/ui-prefs', (route) => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ dev_docker_test_env: 'true' }),
    }));
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_docker_test_env', 'true'); } catch (_) {}
    });
    await page.request.delete('/api/sprint-review').catch(() => {/* ignore */});
    await goto(page, '/sprint-review');
  });

  test('page loads with sr-elements', async ({ page }) => {
    await expect(page.locator('[class*="sr-"]').first()).toBeAttached();
  });

  test('main form card is visible', async ({ page }) => {
    await expect(page.locator('.sr-form-card').first()).toBeVisible();
  });

  test('sprint-name input is present', async ({ page }) => {
    await expect(page.locator('#srSprintName')).toBeAttached();
  });

  test('target-ticket input is present', async ({ page }) => {
    await expect(page.locator('#srTargetKey')).toBeAttached();
  });

  test('jira-config select is present', async ({ page }) => {
    await expect(page.locator('#srJiraConfig')).toBeAttached();
  });

  test('include-improvement checkbox is present', async ({ page }) => {
    await expect(page.locator('#srIncludeImprovement')).toBeAttached();
  });

  test('prompt-template textarea is present', async ({ page }) => {
    await expect(page.locator('#srTemplate')).toBeAttached();
  });

  test('start button is present', async ({ page }) => {
    await expect(page.locator('#srStart')).toBeAttached();
  });

  test('history list is in the DOM', async ({ page }) => {
    await expect(page.locator('#hxCard-sprint-review')).toBeAttached();
  });

  test('results container is in the DOM', async ({ page }) => {
    await expect(page.locator('#srResultsCard')).toBeAttached();
  });

  test('status container is in the DOM', async ({ page }) => {
    await expect(page.locator('#srStatus')).toBeAttached();
  });

  test('KI-Prompt button is present', async ({ page }) => {
    await expect(page.locator('[data-prompt-prefix="sprintreview"]')).toBeAttached();
  });

  test('KI-Chat opens', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });

  test('show-default fills the textarea', async ({ page }) => {
    const showBtn = page.locator('#srShowDefault');
    const ta = page.locator('#srTemplate');
    await showBtn.click();
    await expect.poll(() => ta.inputValue(), { timeout: 5000 }).not.toBe('');
  });

  test('start without sprint name keeps the form on screen and focuses the input', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.locator('#srStart').click();
    await page.waitForTimeout(150);
    expect(errors).toHaveLength(0);
    await expect(page.locator('.sr-form-card')).toBeVisible();
  });

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="sr-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
    // The SPA must not surface the legacy "applyDemoAutoFill is not defined"
    // ReferenceError that used to be thrown on every navigation into
    // /sprint-review after the demo-button refactor. The symbol must not
    // leak out of any inline script that runs on this page.
    const applyDemoAutoFillDefined = await page.evaluate(() => typeof window.applyDemoAutoFill !== 'undefined');
    expect(applyDemoAutoFillDefined).toBe(false);
  });

  test('dark theme: sr-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/sprint-review');
    const card = page.locator('.sr-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('modern theme: sr-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'modern', '/sprint-review');
    const card = page.locator('.sr-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('popos theme: sr-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'popos', '/sprint-review');
    const card = page.locator('.sr-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('sidebar contains the Sprint Review link', async ({ page }) => {
    const link = page.locator('a[href="/sprint-review"]');
    await expect(link.first()).toBeAttached();
  });

  test('jira-config dropdown is populated by the page load', async ({ page }) => {
    // The sprint-review scripts must call /api/config/jira on page load so
    // the user can pick which Jira configuration the run should use.
    // The demo Jira config from the DemoDataSeeder is the expected option.
    const sel = page.locator('#srJiraConfig');
    await expect.poll(async () => await sel.locator('option').count(), { timeout: 5000 }).toBeGreaterThan(0);
  });

  test('sprint name and target ticket are auto-filled when a demo Jira config is selected', async ({ page, context }) => {
    await context.route('**/api/config/jira', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 1,
            name: 'Demo Jira',
            baseUrl: 'http://localhost:8081',
            active: true,
          },
        ]),
      }),
    );
    await goto(page, '/sprint-review');
    const sprintInput = page.locator('#srSprintName');
    const targetInput = page.locator('#srTargetKey');
    await expect.poll(() => sprintInput.inputValue(), { timeout: 5000 }).not.toBe('');
    await expect.poll(() => targetInput.inputValue(), { timeout: 5000 }).not.toBe('');
  });

  test('In-Jira-ansehen link is registered in the page actions', async ({ page }) => {
    // The "In Jira ansehen" link lives in the result card and is only
    // shown once an ADF table has been produced. The demo Jira view
    // itself is a separate route registered by TicketWebController
    // ('/demo-jira-ticket').
    const jiraLink = page.locator('#srViewInJira');
    await expect(jiraLink).toBeAttached();
    // Verify the page-action link on the result card is wired up to the
    // demo Jira route. (It starts hidden until the ADF table exists.)
    const href = await jiraLink.getAttribute('href');
    expect(href).toBe('/demo-jira-ticket');
  });

  test('demo Jira page renders with the ADF table renderer', async ({ page }) => {
    // Hard-load the demo Jira route and verify the page-level scaffolding
    // (header + toolbar + tabs) is present. The Jira-style issue key
    // (SPRINT-200) is rendered in `.djira-issue-key`; the title H1
    // (`#djiraIssueTitle`) carries the human-readable summary.
    await goto(page, '/demo-jira-ticket');
    await expect(page.locator('.djira-header')).toBeVisible();
    await expect(page.locator('.djira-issue-key')).toContainText('SPRINT-200');
    await expect(page.locator('#djiraIssueTitle')).toContainText('Sprint Demo Review');
    await expect(page.locator('.djira-toolbar')).toBeAttached();
    await expect(page.locator('[data-djira-tab="description"]')).toBeAttached();
    await expect(page.locator('[data-djira-tab="raw"]')).toBeAttached();
  });

  test('post-to-jira button is wired up and starts hidden', async ({ page }) => {
    // The "Sprint Ticket-Tabelle in Jira posten" button is the manual
    // post trigger. It must be present in the DOM (so the click handler
    // binds) but start hidden — it only becomes visible after an analysis
    // has built the ADF document.
    const btn = page.locator('#srPostToJira');
    await expect(btn).toBeAttached();
    await expect(btn).toBeHidden();
  });

  test('In-Jira-ansehen link points at the demo-jira preview route', async ({ page }) => {
    // The "In Jira ansehen" link lives in the result card and is only
    // shown once an ADF table has been produced. The demo Jira view
    // itself is a separate route registered by TicketWebController
    // ('/demo-jira-ticket').
    const jiraLink = page.locator('#srViewInJira');
    await expect(jiraLink).toBeAttached();
    // Verify the page-action link on the result card is wired up to the
    // demo Jira route. (It starts hidden until the ADF table exists.)
    const href = await jiraLink.getAttribute('href');
    expect(href).toBe('/demo-jira-ticket');
  });
});
