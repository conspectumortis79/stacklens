import { test, expect } from '@playwright/test';
import { goto, installPageErrorFilter } from './helpers.js';

test.describe('Pipeline Analysis – form validation + submit', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/pipeline-analysis');
  });

  test('user-prompt input card is hidden until a repo is selected', async ({ page }) => {
    const card = page.locator('#paUserPromptCard');
    if (await card.count() === 0) {
      return;
    }
    await expect(card).toBeHidden();
  });

  test('KI-Prompt button carries the "pipeline" prefix', async ({ page }) => {
    const btn = page.locator('[data-prompt-prefix="pipeline"]').first();
    await expect(btn).toBeAttached();
  });

  test('mockup upload UI is not part of the pipeline form', async ({ page }) => {
    await expect(page.locator('#paMockupFileInput')).toHaveCount(0);
    await expect(page.locator('#paMockupUploadZone')).toHaveCount(0);
    await expect(page.locator('.pa-mockup-section')).toHaveCount(0);
    await expect(page.locator('input[type="file"]')).toHaveCount(0);
    const symbols = await page.evaluate(() => ({
      files: typeof window.PA_MOCKUP_FILES,
      selected: typeof window.paMockupFileSelected,
    }));
    expect(symbols.files).toBe('undefined');
    expect(symbols.selected).toBe('undefined');
  });

  test('page selection guards use the analysis panel as the page marker', async ({ page }) => {
    const state = await page.evaluate(() => ({
      marker: !!document.getElementById('paAnalysisPanel'),
      guardMounted: typeof paPageMounted === 'function' && paPageMounted(),
      cardShown: typeof paShowUserPromptCard === 'function',
      legacyGuard: typeof window.paHasSelectableDom,
    }));
    expect(state.marker).toBe(true);
    expect(state.guardMounted).toBe(true);
    expect(state.cardShown).toBe(true);
    expect(state.legacyGuard).toBe('undefined');
  });

  test('PR-view link is hidden until an analysis run is created', async ({ page }) => {
    const prLinks = page.locator('a[href*="/pull-request"], button[onclick*="pull-request"]');
    const count = await prLinks.count();
    for (let i = 0; i < count; i++) {
      const visible = await prLinks.nth(i).isVisible();
      expect(visible).toBe(false);
    }
  });

  test('a failing /api/pipeline-analysis/start-local surfaces a toast', async ({ page }) => {
    let dialogShown = false;
    page.on('dialog', async (d) => {
      dialogShown = true;
      await d.dismiss();
    });

    await page.route('**/api/pipeline-analysis/start-local', (route) =>
      route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'simulated backend failure' }),
      }),
    );

    await page.locator('#paUserPromptCard').evaluate((el) => {
      el.classList.remove('d-none');
      el.style.display = 'block';
    });
    const ta = page.locator('#paUserPromptInput');
    await ta.evaluate((el) => { el.style.display = 'block'; });
    await ta.fill('A valid request that the backend will reject.');

    await page.evaluate(() => {
      if (typeof paStartLocal === 'function') {
        paStartLocal(99999, 'simulated-repo');
      }
    });

    await page.waitForTimeout(500);

    expect(dialogShown, 'native dialogs are replaced by the toast system').toBe(false);
    const toast = page.locator('#alertArea [data-sl-toast]');
    await expect(toast.first()).toBeVisible();
    await expect(toast.first()).toContainText(/simulated backend failure/i);
  });

  test('no JS errors on initial pipeline-analysis load', async ({ page }) => {
    const errors = installPageErrorFilter(page);
    await page.reload();
    await page.waitForSelector('#sidebar', { state: 'visible' });
    await page.waitForTimeout(500);
    expect(errors).toEqual([]);
  });
});
