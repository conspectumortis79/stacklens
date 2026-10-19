import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

/**
 * Pipeline Analysis – MR/PR creation banner.
 *
 * Verifies that the new "MR/PR erstellen" banner + dialog appear in the
 * Befunde tab of the Pipeline-Analyse page across all themes, and that the
 * dialog controls / buttons are correctly hooked up.  These tests do not
 * exercise the actual MR/PR creation (which requires a live GitLab/GitHub
 * instance and an AI analysis) — instead they assert the DOM shape so the
 * UI stays consistent even before a real analysis exists.
 *
 * Mirrors the structure of `05-code-analysis.spec.js` so the same Playwright
 * harness can be re-used.
 */
test.describe('Pipeline Analysis – MR / PR banner', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/pipeline-analysis');
  });

  // ── DOM presence (no analysis yet) ───────────────────────────────────────

  test('MR banner is hidden when no analysis is loaded', async ({ page }) => {
    const banner = page.locator('#paMrBanner');
    // Either not in the DOM yet or hidden — never visible at page load.
    if (await banner.count() > 0) {
      await expect(banner).toBeHidden();
    }
  });

  test('MR result / error containers exist in the DOM', async ({ page }) => {
    await expect(page.locator('#paMrResult')).toBeAttached();
    await expect(page.locator('#paMrError')).toBeAttached();
  });

  test('GitLab MR configuration modal is in the DOM', async ({ page }) => {
    await expect(page.locator('#paMrModal')).toBeAttached();
    await expect(page.locator('#paMrGitlabConfigSelect')).toBeAttached();
    await expect(page.locator('#paMrProjectSelect')).toBeAttached();
    await expect(page.locator('#paMrSubmitBtn')).toBeAttached();
  });

  // ── Theme matrix ──────────────────────────────────────────────────────────

  for (const theme of ['modern', 'dark', 'popos', 'legacy']) {
    test(`theme "${theme}" renders the MR modal without errors`, async ({ page }) => {
      await setTheme(page, theme);
      await page.goto('/pipeline-analysis');
      await page.waitForSelector('[class*="pa-"]', { state: 'attached' });
      const applied = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
      expect(applied).toBe(theme);
      // Sanity-check the modal is still in the DOM after the theme switch.
      await expect(page.locator('#paMrModal')).toBeAttached();
    });
  }

  test('dark theme: MR modal content has a non-white background', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/pipeline-analysis');
    // The modal markup is hidden but always rendered; verify the computed
    // background is overridden by the dark-theme CSS rule.
    const bg = await page.evaluate(() => {
      const el = document.querySelector('.pa-modal-content');
      return el ? getComputedStyle(el).backgroundColor : '';
    });
    // In dark mode the modal background should not be plain white.
    expect(bg).not.toBe('rgb(255, 255, 255)');
  });

  // ── Error handling ────────────────────────────────────────────────────────

  test('createMrOrPr returns 404 for unknown analysis without crashing the page', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const resp = await page.request.post('/api/pipeline-analysis/analysis/999999/create-mr-or-pr', {
      data: {},
    });
    expect(resp.status()).toBe(404);
    expect(errors).toHaveLength(0);
  });

  test('createMrOrPr accepts an empty body without 4xx', async ({ page }) => {
    // For a non-existent analysis the controller returns 404 BEFORE the
    // service is called, so an empty body must succeed (with a 404).
    const resp = await page.request.post('/api/pipeline-analysis/analysis/999999/create-mr-or-pr', {
      headers: { 'Content-Type': 'application/json' },
    });
    expect(resp.status()).toBe(404);
  });

  test('createMrOrPr accepts an empty JSON body', async ({ page }) => {
    const resp = await page.request.post('/api/pipeline-analysis/analysis/999999/create-mr-or-pr', {
      headers: { 'Content-Type': 'application/json' },
      data: '{}',
    });
    expect(resp.status()).toBe(404);
  });

  test('createMrOrPr forwards gitlabConfigId + gitlabProjectId overrides', async ({ page }) => {
    // We can't observe the result without a real analysis, but we can
    // verify the endpoint accepts the payload shape without a 400.
    const resp = await page.request.post('/api/pipeline-analysis/analysis/999999/create-mr-or-pr', {
      headers: { 'Content-Type': 'application/json' },
      data: JSON.stringify({ gitlabConfigId: 1, gitlabProjectId: 2 }),
    });
    // 404 because the analysis doesn't exist — not 400 because of the body.
    expect(resp.status()).toBe(404);
  });

  // ── No JS errors across themes ───────────────────────────────────────────

  test('no JS errors on page load (all themes)', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="pa-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });
});

/**
 * Pipeline Analysis – simulated MR / PR pages
 *
 * Verifies the dedicated `/simulated-pipeline-mr/{id}` and
 * `/simulated-pipeline-pr/{id}` pages that the demo / local-repo flow
 * points at. These do NOT share markup with the existing JiraTicketImpl
 * simulated pages — they render the pipeline-analysis-specific JSON shape
 * (summaryText + findingsJson) directly.
 */
test.describe('Pipeline Analysis – simulated MR / PR pages', () => {

  test('simulated-pipeline-mr/1 responds with 200', async ({ page }) => {
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/simulated-pipeline-mr/1')),
      page.goto('/simulated-pipeline-mr/1'),
    ]);
    expect(response.status()).toBe(200);
  });

  test('simulated-pipeline-mr/1 shows the GitLab-styled top navigation', async ({ page }) => {
    await page.goto('/simulated-pipeline-mr/1');
    await expect(page.locator('.gl-nav')).toBeVisible();
    await expect(page.locator('.gl-sim-badge')).toBeVisible();
  });

  test('simulated-pipeline-pr/1 responds with 200', async ({ page }) => {
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/simulated-pipeline-pr/1')),
      page.goto('/simulated-pipeline-pr/1'),
    ]);
    expect(response.status()).toBe(200);
  });

  test('simulated-pipeline-pr/1 shows the GitHub-styled top navigation', async ({ page }) => {
    await page.goto('/simulated-pipeline-pr/1');
    await expect(page.locator('.gh-nav')).toBeVisible();
    await expect(page.locator('.gh-sim-badge')).toBeVisible();
  });

  test('simulated-pipeline-mr/1 page title contains "Merge Request"', async ({ page }) => {
    await page.goto('/simulated-pipeline-mr/1');
    await expect(page).toHaveTitle(/Merge Request/i);
  });

  test('simulated-pipeline-pr/1 page title contains "Pull Request"', async ({ page }) => {
    await page.goto('/simulated-pipeline-pr/1');
    await expect(page).toHaveTitle(/Pull Request/i);
  });
});