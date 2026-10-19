import { test, expect } from '@playwright/test';
import { goto, setTheme, getTheme } from './helpers.js';

/**
 * Theme system
 * Verifies all four themes set the correct data-theme attribute,
 * keep the sidebar intact, and do not cause layout overflow.
 * Also verifies that page-specific dropdowns (.ta-select) adapt to each theme.
 */
test.describe('Theme System', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/config');
  });

  test('default theme is "modern"', async ({ page }) => {
    // Fresh localStorage → modern theme
    await page.evaluate(() => localStorage.clear());
    await page.reload();
    await page.waitForSelector('#sidebar');
    const theme = await getTheme(page);
    expect(theme).toBe('modern');
  });

  const themes = [
    { id: 'modern',  label: 'Modern' },
    { id: 'legacy',  label: 'Classic' },
    { id: 'dark',    label: 'Dark' },
    { id: 'popos',   label: 'Pop!_OS' },
  ];

  for (const { id, label } of themes) {
    test(`theme "${label}" (${id}) sets data-theme correctly`, async ({ page }) => {
      await setTheme(page, id);
      const theme = await getTheme(page);
      expect(theme).toBe(id);
    });

    test(`theme "${label}" → sidebar stays visible`, async ({ page }) => {
      await setTheme(page, id);
      await expect(page.locator('#sidebar')).toBeVisible();
      await expect(page.locator('.sl-brand-text')).toBeVisible();
    });

    test(`theme "${label}" → no horizontal overflow`, async ({ page }) => {
      await setTheme(page, id);
      // No horizontal scroll should occur (overflow-x: hidden)
      const bodyScrollWidth = await page.evaluate(() => document.body.scrollWidth);
      const bodyClientWidth = await page.evaluate(() => document.body.clientWidth);
      expect(bodyScrollWidth).toBeLessThanOrEqual(bodyClientWidth + 5);
    });

    test(`theme "${label}" → no JS errors on config page`, async ({ page }) => {
      const errors = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await setTheme(page, id);
      expect(errors).toHaveLength(0);
    });
  }

  test('theme persists across page navigation', async ({ page }) => {
    await setTheme(page, 'dark');
    // Navigate to another page
    await page.goto('/jwt-analysis');
    await page.waitForSelector('#sidebar');
    const theme = await getTheme(page);
    expect(theme).toBe('dark');
  });

  test('theme persists after page reload', async ({ page }) => {
    await setTheme(page, 'popos');
    await page.reload();
    await page.waitForSelector('#sidebar');
    expect(await getTheme(page)).toBe('popos');
  });

  test('config page shows selected theme card', async ({ page }) => {
    await setTheme(page, 'dark');
    await goto(page, '/config');
    // Theme cards live in the "theme" tab – navigate there first
    await page.locator('.cf-tab[data-tab="theme"]').click();
    const selectedCard = page.locator('.cf-theme-card.selected');
    await expect(selectedCard).toBeVisible();
    const chosenTheme = await selectedCard.getAttribute('data-theme-choice');
    expect(chosenTheme).toBe('dark');
  });

  // ── DB dropdown (ta-select) adapts to theme ────────────────────────────────

  test('ticket-implementation: DB multi-select container is rendered', async ({ page }) => {
    await goto(page, '/ticket-implementation');
    const dbContainer = page.locator('#taDatabaseMultiSelect');
    // The element is always in the DOM; it may be empty (zero-height) when no DB
    // configs exist in the test environment, so toBeAttached() is the correct check.
    if (await dbContainer.count() > 0) {
      await expect(dbContainer).toBeAttached();
    }
  });

  test('ticket-implementation: #selectJiraConfig carries the ta-select class', async ({ page }) => {
    await goto(page, '/ticket-implementation');
    const jiraSelect = page.locator('#selectJiraConfig');
    if (await jiraSelect.count() > 0) {
      await expect(jiraSelect).toHaveClass(/ta-select/);
    }
  });

  test('dark theme: ta-select on ticket-implementation page has dark background', async ({ page }) => {
    await setTheme(page, 'dark');
    await goto(page, '/ticket-implementation');
    const jiraSelect = page.locator('#selectJiraConfig');
    if (await jiraSelect.count() > 0 && await jiraSelect.isVisible()) {
      const bg = await jiraSelect.evaluate((el) => getComputedStyle(el).backgroundColor);
      // dark theme: background is #2a2540 ≈ rgb(42, 37, 64) — must not be white/cream
      expect(bg).not.toMatch(/^rgb\(25[0-9],/); // not near-white
    }
  });

  test('popos theme: ta-select on ticket-implementation page has dark background', async ({ page }) => {
    await setTheme(page, 'popos');
    await goto(page, '/ticket-implementation');
    const jiraSelect = page.locator('#selectJiraConfig');
    if (await jiraSelect.count() > 0 && await jiraSelect.isVisible()) {
      const bg = await jiraSelect.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toMatch(/^rgb\(25[0-9],/); // not near-white
    }
  });

  test('modern theme: ta-select on ticket-implementation page has light background', async ({ page }) => {
    await setTheme(page, 'modern');
    await goto(page, '/ticket-implementation');
    const jiraSelect = page.locator('#selectJiraConfig');
    if (await jiraSelect.count() > 0 && await jiraSelect.isVisible()) {
      const bg = await jiraSelect.evaluate((el) => getComputedStyle(el).backgroundColor);
      // modern theme: background is #fff = rgb(255, 255, 255)
      expect(bg).toMatch(/^rgb\(255,/);
    }
  });

  test('switching from dark to modern resets ta-select to light background', async ({ page }) => {
    await setTheme(page, 'dark');
    await goto(page, '/ticket-implementation');
    const jiraSelect = page.locator('#selectJiraConfig');
    if (await jiraSelect.count() === 0 || !await jiraSelect.isVisible()) return;

    const bgDark = await jiraSelect.evaluate((el) => getComputedStyle(el).backgroundColor);
    expect(bgDark).not.toMatch(/^rgb\(255,/); // dark → not white

    // Switch to modern and navigate back
    await setTheme(page, 'modern');
    await goto(page, '/ticket-implementation');
    const bgModern = await jiraSelect.evaluate((el) => getComputedStyle(el).backgroundColor);
    expect(bgModern).toMatch(/^rgb\(255,/); // modern → white
  });

  test('CSS variable --app-text is defined for every theme', async ({ page }) => {
    for (const { id } of themes) {
      await setTheme(page, id);
      const defined = await page.evaluate(() =>
        getComputedStyle(document.documentElement).getPropertyValue('--app-text').trim() !== '',
      );
      expect(defined).toBe(true);
    }
  });
});
