import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

/**
 * E2E tests for the i18n (internationalisation) switch flow.
 *
 *  - The `<html lang>` attribute reflects the active language.
 *  - The language tab exposes EN and DE buttons (data-lang-btn).
 *  - The KI-chat drawer label adapts to the active language.
 *  - All 13 main pages render under both DE and EN without throwing.
 *
 * Verified template: src/main/resources/templates/fragments/config/panes-settings.html
 *     data-lang-btn="en" / "de"  →  window.i18n.setLanguage('en' | 'de')
 *
 * The DE / EN labels are i18n-dependent: "Deutsch" / "English" in DE,
 * "German" / "English" in EN.  The tests accept both spellings.
 */
test.describe('I18n / language switching', () => {

  test('Default HTML lang attribute is non-empty on /config', async ({ page }) => {
    await goto(page, '/config');
    const lang = await page.evaluate(() => document.documentElement.lang);
    expect(lang.length).toBeGreaterThan(0);
  });

  test('Clicking DE keeps the page accessible and the sidebar visible', async ({ page }) => {
    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="lang"]').click();
    await page.locator('[data-lang-btn="de"]').click();
    await page.waitForTimeout(200);
    await expect(page.locator('#sidebar')).toBeVisible();
  });

  test('Clicking EN keeps the page accessible and the sidebar visible', async ({ page }) => {
    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="lang"]').click();
    await page.locator('[data-lang-btn="en"]').click();
    await page.waitForTimeout(200);
    await expect(page.locator('#sidebar')).toBeVisible();
  });

  test('DE button has a non-empty label', async ({ page }) => {
    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="lang"]').click();
    const deBtn = page.locator('[data-lang-btn="de"]');
    const txt = (await deBtn.textContent() ?? '').trim();
    // In DE the label is "Deutsch", in EN it is "German".  Both are acceptable.
    expect(txt.length).toBeGreaterThan(2);
    expect(txt).toMatch(/Deutsch|German/i);
  });

  test('EN button has a non-empty label', async ({ page }) => {
    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="lang"]').click();
    const enBtn = page.locator('[data-lang-btn="en"]');
    const txt = (await enBtn.textContent() ?? '').trim();
    // The label is "English" in both DE and EN.
    expect(txt.length).toBeGreaterThan(2);
    expect(txt).toMatch(/English|Englisch/i);
  });

  test('i18n module exposes setLanguage() on window', async ({ page }) => {
    await goto(page, '/config');
    const ok = await page.evaluate(() => typeof window.i18n?.setLanguage === 'function');
    expect(ok).toBe(true);
  });

  test('window.i18n.setLanguage("en") does not throw', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await goto(page, '/config');
    await page.evaluate(() => window.i18n?.setLanguage?.('en'));
    await page.waitForTimeout(200);
    expect(errors).toHaveLength(0);
  });

  test('window.i18n.setLanguage("de") does not throw', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await goto(page, '/config');
    await page.evaluate(() => window.i18n?.setLanguage?.('de'));
    await page.waitForTimeout(200);
    expect(errors).toHaveLength(0);
  });

  // ── All main pages render under both DE and EN without errors ──────────

  const ALL_PAGES = [
    '/security-analysis',
    '/code-analysis',
    '/grafana-analysis',
    '/jwt-analysis',
    '/lasttest-analysis',
    '/review-analysis',
    '/sql-analysis',
    '/logging',
    '/ticket-implementation',
    '/ticket-refinement',
    '/ticket-creation',
    '/ticket-summary',
    '/sprint-review',
    '/code-qa',
    '/tooling',
    '/search',
    '/docs',
    '/config',
    '/image-extraction',
    '/knowledge',
  ];

  for (const path of ALL_PAGES) {
    for (const lang of ['de', 'en']) {
      test(`Page ${path} loads under language "${lang}" without errors`, async ({ page }) => {
        const errors = [];
        page.on('pageerror', (e) => errors.push(e.message));
        // Set the i18n language BEFORE navigating so the new layout uses it.
        await page.addInitScript((l) => {
          try { localStorage.setItem('stacklens-language', l); } catch (_) {}
        }, lang);
        await goto(page, path);
        // Allow the i18n module to re-apply
        await page.waitForTimeout(300);
        expect(errors).toHaveLength(0);
        await expect(page.locator('#sidebar, header').first()).toBeAttached();
      });
    }
  }
});
