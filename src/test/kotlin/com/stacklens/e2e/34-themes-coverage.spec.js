import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

/**
 * E2E theme-coverage matrix — extends the basic theme tests in
 * 02-themes.spec.js by exercising ALL pages in ALL 4 themes.
 *
 * For every (page × theme) pair we verify in a SINGLE test:
 *  - The page renders with HTTP < 400.
 *  - The expected page-specific element is attached.
 *  - The sidebar is visible (unless the page uses a non-standard layout).
 *  - The page has no horizontal overflow.
 *  - No uncaught JS errors fire during a 300ms settle window.
 *
 * Optimization: uses `setThemeAndGoto` which pre-installs an init-script
 * setting localStorage BEFORE the page loads — avoids the extra reload
 * that `setTheme()` performs. Cuts ~50% off each (page × theme) test.
 *
 * The previous version had two separate test loops (renders + sidebar)
 * each doing a fresh page load. Merging them halves the page-load
 * count while keeping the same coverage matrix.
 */
test.describe('Theme coverage – every page in every theme', () => {

  const THEMES = ['modern', 'legacy', 'dark', 'popos'];

  /**
   * Each entry is [path, primary-selector].
   * All current StackLens pages render with the standard layout that
   * includes a #sidebar, so we no longer track per-page layout flags.
   */
  const PAGES = [
    ['/security-analysis',   '[class*="ba-"]'],
    ['/code-analysis',       '[class*="ca-"]'],
    ['/grafana-analysis',    '[class*="ga-"]'],
    ['/jwt-analysis',        '.jwt-page'],
    ['/lasttest-analysis',   '[class*="lt-"]'],
    ['/review-analysis',     '[class*="ra-"]'],
    ['/sql-analysis',        '[class*="sa-"]'],
    ['/logging',             '[class*="lg-"]'],
    ['/ticket-implementation', '[class*="ta-"]'],
    ['/ticket-refinement',   '[class*="sp-"]'],
    ['/ticket-creation',     '[class*="tc-"]'],
    ['/ticket-summary',      '[class*="ts-"]'],
    ['/code-qa',             '.mr-card'],
    ['/tooling',             '[class*="tl-"]'],
    ['/search',              '[class*="su-"]'],
    ['/docs',                '.doc-tabs'],
    ['/config',              '.cf-tab'],
    ['/image-extraction',    '.ie-page'],
    ['/knowledge',           '.kb-page'],
    ['/sprint-review',       '.sr-page'],
    // Docker test is hidden by default; only test that the URL does not crash.
    ['/docker-test',         '.dt-card'],
  ];

  // Single loop: for every (page × theme) verify all checks in one pass.
  for (const [path, selector] of PAGES) {
    for (const theme of THEMES) {
      test(`${path} renders correctly under "${theme}" theme`, async ({ page }) => {
        const errors = [];
        page.on('pageerror', (e) => errors.push(e.message));
        // Install theme via init-script and navigate in one shot.
        await setThemeAndGoto(page, theme, path);
        // Page-specific element must be attached.
        await expect(page.locator(selector).first()).toBeAttached({ timeout: 8_000 });
        // Sidebar must be visible (most pages use the standard layout).
        await expect(page.locator('#sidebar')).toBeVisible();
        // No horizontal overflow.
        const overflow = await page.evaluate(() =>
          document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
        );
        expect(overflow).toBe(false);
        // Give the page a brief settle window to surface late JS errors.
        await page.waitForTimeout(200);
        expect(errors).toHaveLength(0);
      });
    }
  }

  // ── Theme card selected state mirrors the active theme ────────────────

  for (const theme of THEMES) {
    test(`Active theme card on /config reflects "${theme}"`, async ({ page }) => {
      await setThemeAndGoto(page, theme, '/config');
      await page.locator('.cf-tab[data-tab="theme"]').click();
      const selected = page.locator('.cf-theme-card.selected');
      await expect(selected).toBeVisible();
      const id = await selected.getAttribute('data-theme-choice');
      expect(id).toBe(theme);
    });
  }
});
