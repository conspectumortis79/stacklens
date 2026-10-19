/**
 * Regression: KI-Prompt-Button visibility after a fresh git clone.
 *
 * BUG: StackLens persists developer options in two places — a server-side
 * `ui-prefs.json` (default `./data/ui-prefs.json`, wiped on `git clone`
 * because `data/` is in `.gitignore`) and the browser's `localStorage`
 * (per-origin, persists across re-clones as long as the browser profile
 * is the same). After deleting the project, re-cloning it and restarting
 * via `start.sh`, all toggle UI controls read ON (because they are
 * sourced from `localStorage` synchronously in `cfInitPage`), but the
 * KI-Prompt-Button stayed hidden on every analysis page. Root cause:
 * the `/api/ui-prefs` fetch callback in `layout.html` (and the mirror
 * `cfApplyUiPreferences` in the config page's `scripts-page.html`) was
 * UNCONDITIONALLY setting the body class `hide-prompt-buttons` based on
 * the server value. When the server has no opinion (`undefined` from
 * Jackson because the key is absent from `ui-prefs.json`), the prompt
 * became `false` and the class was added — overriding the earlier
 * localStorage-driven initial paint.
 *
 * This test reproduces the bug deterministically by mocking
 * `/api/ui-prefs` to return an empty body (the analogue of "fresh clone
 * with no ui-prefs.json entry for the key"), pre-seeding localStorage
 * with the dev option, navigating to an analysis page that exposes a
 * KI-Prompt-Button via `data-prompt-prefix`, and asserting:
 *   1. body does not carry `hide-prompt-buttons`
 *   2. the KI-Prompt-Button is actually displayed (not hidden by CSS)
 *
 * The same fall-back-to-localStorage pattern was previously applied for
 * `dev_docker_test_env` in `cfApplyUiPreferences` and on the initial-paint
 * path in `cfApplyDockerSidebarFromLocalStorage`; this test is the
 * analogue for `dev_show_prompt_buttons`. See commit e914ee9 for the
 * test-docker-side fix.
 */
import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

const PROMPT_BUTTON_PAGE = '/pipeline-analysis';
const PROMPT_BUTTON_SELECTOR = 'button[data-prompt-prefix="pipeline"]';

test.describe('KI-Prompt-Button persistence (regression: fresh-clone + localStorage)', () => {

  test('Button stays visible when localStorage says ON and server /api/ui-prefs has no opinion', async ({ page }) => {
    // 1. Mock /api/ui-prefs to omit dev_show_prompt_buttons — this is the
    //    exact "server has no opinion" situation a fresh clone produces
    //    when ui-prefs.json does not contain the key yet.
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({}), // intentionally empty — no dev_show_prompt_buttons
      });
    });

    // 2. Pre-install the dev option in localStorage BEFORE the page loads,
    //    simulating a returning user who activated the option in a previous
    //    install/build that the browser still remembers.
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_show_prompt_buttons', 'true'); } catch (_) {}
    });

    await goto(page, PROMPT_BUTTON_PAGE);

    // 3. Sanity: the body class must NOT carry hide-prompt-buttons, because
    //    the initial paint (from localStorage) sets it to "show" and the
    //    fetch callback must preserve that state when the server has no
    //    opinion. The CSS rule `body.hide-prompt-buttons [data-prompt-prefix]
    //    { display: none !important; }` is what hides the button otherwise.
    const bodyClasses = await page.evaluate(() => document.body.className);
    expect(bodyClasses.split(/\s+/)).not.toContain('hide-prompt-buttons');

    // 4. The button must be attached …
    const btn = page.locator(PROMPT_BUTTON_SELECTOR);
    await expect(btn).toBeAttached();

    // … AND visible (computed style is not `display:none`).
    // We read `getComputedStyle(...).display` rather than using
    // `toBeVisible()` because the button is `disabled` until prompts
    // are loaded, which Playwright's actionability check tolerates but
    // `toBeVisible()` only treats as visible if there are no occlusion
    // parents. Reading the computed style is the most direct assertion
    // that the developer-option bug is fixed.
    const display = await btn.evaluate((el) => getComputedStyle(el).display);
    expect(display).not.toBe('none');
  });

  test('Button stays hidden when localStorage is missing and server has no opinion (safe default)', async ({ page }) => {
    // Empty body, empty localStorage → default state (hide) must remain
    // unchanged by the fetch callback. Guards against over-eager fallback
    // that would silently turn the button ON for first-time visitors.
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({}),
      });
    });

    await goto(page, PROMPT_BUTTON_PAGE);

    const bodyClasses = await page.evaluate(() => document.body.className);
    expect(bodyClasses.split(/\s+/)).toContain('hide-prompt-buttons');

    const btn = page.locator(PROMPT_BUTTON_SELECTOR);
    const display = await btn.evaluate((el) => getComputedStyle(el).display);
    expect(display).toBe('none');
  });

  test('Server value OFF overrides localStorage ON (server wins when present)', async ({ page }) => {
    // When the server IS authoritative, its value must still take effect
    // even if localStorage disagrees. Prevents the fall-back from
    // accidentally downgrading the server to "advisory" status.
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ dev_show_prompt_buttons: 'false' }),
      });
    });
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_show_prompt_buttons', 'true'); } catch (_) {}
    });

    await goto(page, PROMPT_BUTTON_PAGE);

    const bodyClasses = await page.evaluate(() => document.body.className);
    expect(bodyClasses.split(/\s+/)).toContain('hide-prompt-buttons');

    const btn = page.locator(PROMPT_BUTTON_SELECTOR);
    const display = await btn.evaluate((el) => getComputedStyle(el).display);
    expect(display).toBe('none');
  });
});
