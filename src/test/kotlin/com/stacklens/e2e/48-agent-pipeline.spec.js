import { test, expect } from '@playwright/test';
import { goto, setThemeAndGoto } from './helpers.js';

test.describe('PHP Agent Pipeline (embedded on /ticket-implementation)', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/ticket-implementation');
  });

  test('page loads with the standard ticket-implementation container', async ({ page }) => {
    await expect(page.locator('[class*="ta-"]').first()).toBeAttached();
  });

  test('PHP Agent Pipeline start button is present in the form area', async ({ page }) => {
    const btn = page.locator('#btnStartAgentPipeline');
    await expect(btn).toBeAttached();
    const title = (await btn.getAttribute('title')) ?? '';
    expect(title).toContain('PHP Agent Pipeline');
    expect(title).toContain('Lena');
    expect(title).toContain('Rex');
  });

  test('mockup chip container is present in the DOM and starts hidden until chips exist', async ({ page }) => {
    const chips = page.locator('#taMockupChips');
    await expect(chips).toBeAttached();
    await expect(chips).toBeHidden();
  });

  test('mockup file browser modal is present and hidden by default', async ({ page }) => {
    await expect(page.locator('#taMockupFbModal')).toBeHidden();
  });

  test('cancel button is present', async ({ page }) => {
    const cancel = page.locator('button:has-text("Abbrechen"), button:has-text("Cancel"), [data-i18n="common.cancel"]').first();
    await expect(cancel).toBeAttached();
  });

  // ── Theme matrix ─────────────────────────────────────────────────────────

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await page.waitForTimeout(500);
    expect(errors).toHaveLength(0);
  });

  for (const theme of ['modern', 'legacy', 'dark', 'popos']) {
    test(`renders correctly under "${theme}" theme without horizontal overflow`, async ({ page }) => {
      await setThemeAndGoto(page, theme, '/ticket-implementation');
      await expect(page.locator('#sidebar')).toBeVisible();
      await expect(page.locator('#btnStartAgentPipeline')).toBeAttached();
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      );
      expect(overflow).toBe(false);
    });
  }

  // ── REST smoke ───────────────────────────────────────────────────────────

  test('GET /api/agent-pipeline returns a JSON array', async ({ request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/agent-pipeline`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('POST /api/agent-pipeline with an empty body is rejected (4xx)', async ({ request, baseURL }) => {
    const res = await request.post(`${baseURL}/api/agent-pipeline`, { data: {} });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/agent-pipeline/from-jira with an empty body is rejected (4xx)', async ({ request, baseURL }) => {
    const res = await request.post(`${baseURL}/api/agent-pipeline/from-jira`, { data: {} });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('GET /api/agent-pipeline/{id} returns 4xx for an unknown id', async ({ request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/agent-pipeline/99999999`);
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/agent-pipeline/{id}/cancel returns 4xx for an unknown id', async ({ request, baseURL }) => {
    const res = await request.post(`${baseURL}/api/agent-pipeline/99999999/cancel`);
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('GET /api/agent-pipeline/mockup-file without a path is rejected', async ({ request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/agent-pipeline/mockup-file`);
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('GET /api/agent-pipeline/mockup-file with a non-existent path returns 4xx', async ({ request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/agent-pipeline/mockup-file?path=/nonexistent/file/that/does/not/exist.png`);
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('GET /api/agent-pipeline/{id}/check-repo-access for unknown id returns 4xx', async ({ request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/agent-pipeline/99999999/check-repo-access`);
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });
});
