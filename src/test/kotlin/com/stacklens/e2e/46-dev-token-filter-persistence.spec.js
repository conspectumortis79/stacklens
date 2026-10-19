/**
 * Persistence regression scope for the remaining two developer options.
 *
 * Companion to 45-ki-prompt-persistence.spec.js which covered the
 * KI-Prompt-Button (`dev_show_prompt_buttons`). The same fresh-clone bug
 * pattern (server has no opinion in ui-prefs.json because the default
 * `stacklens.data-dir = ./data/` was wiped on `git clone`, while
 * `localStorage` still remembers the user's earlier ON-state) was
 * investigated for:
 *
 *   * Token-Verbrauchs-Anzeige (`dev_show_token_usage`)
 *       — used by the analysis-token bar and the KI-Chat usage panel.
 *       — The toggle is set from localStorage in cfInitPage and only
 *         potentially overridden by cfApplyUiPreferences when the server
 *         HAS an opinion (early-return otherwise). The actual on-page
 *         visibility is driven by direct localStorage reads inside
 *         cfTokenUsage.enabled() and __kiChatUsageEnabled(), both of
 *         which default to ON. No body class is used.
 *
 *   * Token-sparende Subprocess-Ausgabefilter (`text_filter_enabled`)
 *       — persisted server-side by TextFilterConfigAdapter into the same
 *         ui-prefs.json. The toggle is sourced from the authoritative
 *         /api/text-filter/config endpoint (read and write) with a
 *         localStorage fallback when the server is unreachable. No
 *         body class is used.
 *
 * Bug-hunt summary (checked before writing these tests):
 *
 *   1. dev_show_prompt_buttons   — BUG (fixed in 45): server fetch
 *                                  unconditionally set body class.
 *   2. dev_show_token_usage      — NO bug: no body class; toggle state
 *                                  is preserved via early-return on null.
 *   3. text_filter_enabled       — NO bug: server is authoritative via
 *                                  /api/text-filter/config with its own
 *                                  state model; cfApplyUiPreferences
 *                                  only fires the early-return path here.
 *
 * These tests therefore ASSERT POSITIVE behaviour (the previously
 * suspected bug does not regress) rather than proving a fix. If any
 * later change introduces the unconditional-override pattern for one
 * of these two toggles, the corresponding test in this file will
 * start failing loudly with the same shape as 45.
 */
import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

// /pipeline-analysis: analysis page with the per-page token-usage bar
// (fragments/shared/token-usage.html -> cfTokenUsage.init) and the global
// KI-Chat drawer (fragments/layout/scripts-ki-chat.html). Both honour
// dev_show_token_usage via direct localStorage reads.
const ANALYSIS_PAGE = '/pipeline-analysis';

// ─── dev_show_token_usage (Token-Verbrauchs-Anzeige) ────────────────────────

test.describe('dev_show_token_usage — KI-Chat usage panel after a fresh-clone', () => {

  test('Panel is visible when localStorage says ON and server /api/ui-prefs has no opinion', async ({ page }) => {
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({}),  // intentional: no dev_show_token_usage
      });
    });
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_show_token_usage', 'true'); } catch (_) {}
    });

    await goto(page, ANALYSIS_PAGE);
    await page.evaluate(() => window.kiChatOpen?.());

    const chatUsage = page.locator('#kiChatUsage');
    await expect(chatUsage).toBeVisible();
  });

  test('Panel stays hidden when localStorage says OFF and server has no opinion', async ({ page }) => {
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({}) });
    });
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_show_token_usage', 'false'); } catch (_) {}
    });

    await goto(page, ANALYSIS_PAGE);
    await page.evaluate(() => window.kiChatOpen?.());

    const chatUsage = page.locator('#kiChatUsage');
    await expect(chatUsage).toBeHidden();
  });

  test('Server value OFF overrides localStorage ON (server wins when present)', async ({ page }) => {
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ dev_show_token_usage: 'false' }),
      });
    });
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_show_token_usage', 'true'); } catch (_) {}
    });

    await goto(page, ANALYSIS_PAGE);
    await page.evaluate(() => window.kiChatOpen?.());

    const chatUsage = page.locator('#kiChatUsage');
    await expect(chatUsage).toBeHidden();
  });
});

test.describe('dev_show_token_usage — toggle state on /config after a fresh-clone', () => {

  test('Toggle is ON when localStorage says ON and server has no opinion', async ({ page }) => {
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({}) });
    });
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_show_token_usage', 'true'); } catch (_) {}
    });

    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="dev"]').click();

    const cb = page.locator('#devShowTokenUsage');
    await expect(cb).toBeChecked();
  });

  test('Toggle is OFF when localStorage says OFF and server has no opinion', async ({ page }) => {
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({}) });
    });
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_show_token_usage', 'false'); } catch (_) {}
    });

    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="dev"]').click();

    const cb = page.locator('#devShowTokenUsage');
    await expect(cb).not.toBeChecked();
  });
});

// ─── text_filter_enabled (Token-sparende Subprocess-Ausgabefilter) ─────────

test.describe('text_filter_enabled — toggle state is server-authoritative after a fresh-clone', () => {

  // The toggle's checked state is sourced from GET /api/text-filter/config,
  // NOT from GET /api/ui-prefs (cfInitPage), so the no-opinion /api/ui-prefs
  // fetch (the origin of the dev_show_prompt_buttons bug) cannot corrupt it.
  // These tests pin that contract by intercepting the authoritative endpoint
  // and confirming the toggle + localStorage follow it, regardless of
  // /api/ui-prefs contents.

  test('Toggle is ON when /api/text-filter/config returns enabled=true (and /api/ui-prefs has no opinion)', async ({ page }) => {
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({}) });
    });
    await page.route('**/api/text-filter/config', async (route) => {
      // Spring default for ui-prefs.json-missing is true.
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ enabled: true }) });
    });

    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="dev"]').click();

    const cb = page.locator('#devTextFilterEnabled');
    await expect(cb).toBeChecked();
    // localStorage is mirrored from the server response.
    await expect.poll(() => page.evaluate(() => localStorage.getItem('dev_text_filter_enabled'))).toBe('true');
  });

  test('Toggle is OFF when /api/text-filter/config returns enabled=false (server wins over localStorage)', async ({ page }) => {
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({}) });
    });
    await page.route('**/api/text-filter/config', async (route) => {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ enabled: false }) });
    });
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_text_filter_enabled', 'true'); } catch (_) {}
    });

    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="dev"]').click();

    const cb = page.locator('#devTextFilterEnabled');
    await expect(cb).not.toBeChecked();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('dev_text_filter_enabled'))).toBe('false');
  });

  test('Toggle falls back to localStorage when /api/text-filter/config is unreachable (network error)', async ({ page }) => {
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({}) });
    });
    await page.route('**/api/text-filter/config', async (route) => {
      await route.abort('failed');
    });
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_text_filter_enabled', 'true'); } catch (_) {}
    });

    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="dev"]').click();

    // The catch() branch in cfInitPage reads localStorage when the
    // authoritative GET fails; the checkbox should reflect that fallback.
    const cb = page.locator('#devTextFilterEnabled');
    await expect(cb).toBeChecked();
  });
});
