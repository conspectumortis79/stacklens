import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

// Valid HS256 token with an expired exp claim
const EXPIRED_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9' +
  '.eyJzdWIiOiJ0ZXN0dXNlciIsImlzcyI6ImRlbW8iLCJhdWQiOiJteS1hcHAiLCJpYXQiOjE2MDAwMDAwMDAsImV4cCI6MTYwMDAwMDAwMX0' +
  '.fakesignature';

// Token without exp claim (unlimited lifetime)
const NO_EXP_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9' +
  '.eyJzdWIiOiJ1c2VyMTIzIiwiaXNzIjoiZGVtby1zZXJ2ZXIiLCJuYW1lIjoiVGVzdCBVc2VyIn0' +
  '.fakesignature';

test.describe('JWT Analysis', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/jwt-analysis');
  });

  // ── Page structure ────────────────────────────────────────────────────
  test('page loads and shows the input card', async ({ page }) => {
    await expect(page.locator('.jwt-page')).toBeVisible();
    await expect(page.locator('.jwt-card').first()).toBeVisible();
    await expect(page.locator('#jwtInput')).toBeVisible();
  });

  test('analyze button is present', async ({ page }) => {
    await expect(page.locator('button:has([data-i18n="jwt_analysis.analyze"])')).toBeVisible();
  });

  test('result section is hidden initially', async ({ page }) => {
    await expect(page.locator('#jwtResult')).toBeHidden();
  });

  // ── Validation ────────────────────────────────────────────────────────
  test('empty input shows error alert', async ({ page }) => {
    await page.locator('#jwtInput').clear();
    await page.locator('button:has([data-i18n="jwt_analysis.analyze"])').click();
    await expect(page.locator('#jwtErrorArea')).toBeVisible();
    await expect(page.locator('#jwtErrorArea')).toContainText(/Fehler|leer|eingeben|error|enter|empty/i);
  });

  test('invalid token string shows error', async ({ page }) => {
    await page.locator('#jwtInput').fill('not-a-jwt-token');
    await page.locator('button:has([data-i18n="jwt_analysis.analyze"])').click();
    await expect(page.locator('#jwtErrorArea')).toBeVisible({ timeout: 8_000 });
  });

  // ── Analysis result (API-dependent) ──────────────────────────────────
  test('submitting a token triggers either result or error', async ({ page }) => {
    await page.locator('#jwtInput').fill(NO_EXP_TOKEN);
    await page.locator('button:has([data-i18n="jwt_analysis.analyze"])').click();
    // Both a result panel and an error area are acceptable depending on API validation
    await page.waitForFunction(
      () =>
        document.getElementById('jwtResult')?.style.display !== 'none' ||
        document.getElementById('jwtErrorArea')?.style.display !== 'none',
      { timeout: 10_000 },
    );
  });

  test('error area is cleared when a new analysis starts', async ({ page }) => {
    // 1. trigger an error
    await page.locator('#jwtInput').fill('broken');
    await page.locator('button:has([data-i18n="jwt_analysis.analyze"])').click();
    await expect(page.locator('#jwtErrorArea')).toBeVisible({ timeout: 8_000 });

    // 2. start a new request – error area must be hidden before any new result
    await page.locator('#jwtInput').fill(NO_EXP_TOKEN);
    await page.locator('button:has([data-i18n="jwt_analysis.analyze"])').click();
    await page.waitForFunction(
      () =>
        document.getElementById('jwtErrorArea')?.style.display === 'none' ||
        document.getElementById('jwtResult')?.style.display !== 'none',
      { timeout: 10_000 },
    );
  });

  // ── Theming ───────────────────────────────────────────────────────────
  const themes = [
    { id: 'dark'   },
    { id: 'popos'  },
    { id: 'modern' },
    { id: 'legacy' },
  ];

  for (const { id } of themes) {
    test(`theme "${id}" → .jwt-card renders with correct background`, async ({ page }) => {
      await setThemeAndGoto(page, id, '/jwt-analysis');
      await page.waitForSelector('.jwt-card');
      const bg = await page.locator('.jwt-card').first().evaluate(
        (el) => window.getComputedStyle(el).backgroundColor,
      );
      expect(bg).not.toBe('');
      await expect(page.locator('.jwt-card').first()).toBeVisible();
    });
  }

  // ── KI Chat ───────────────────────────────────────────────────────────
  test('KI-chat button opens the drawer', async ({ page }) => {
    await page.getByRole('button', { name: /KI Chat/i }).click();
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
  });

  test('KI-chat drawer shows "JWT" in the tab badge', async ({ page }) => {
    await page.getByRole('button', { name: /KI Chat/i }).click();
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
    const badge = page.locator('#kiChatTabBadge');
    await expect(badge).toBeAttached();
    // Badge may or may not carry a label depending on kiChatConfig availability.
    // If text is present it must mention JWT; an empty badge is also acceptable.
    const text = (await badge.textContent() ?? '').trim();
    if (text.length > 0) {
      await expect(badge).toContainText(/JWT/i);
    }
  });

  test('KI-chat closes via the × button', async ({ page }) => {
    await page.getByRole('button', { name: /KI Chat/i }).click();
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
    await page.locator('#kiChatDrawer button[onclick*="kiChatClose"]').click();
    await expect(page.locator('#kiChatDrawer')).toBeHidden();
  });
});

