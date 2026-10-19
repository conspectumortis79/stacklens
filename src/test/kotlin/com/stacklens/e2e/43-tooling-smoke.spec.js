import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';

/**
 * Tooling smoke tests.
 *
 * These tests guard against a class of bugs that is otherwise hard to catch
 * in a unit test:
 *
 *   - `NoClassDefFoundError` / `ClassNotFoundException` at runtime when the
 *     compiled JAR is missing a class that is referenced by another class
 *     loaded later (typical symptom: a freshly-built JAR replaces an older
 *     one, but the running server still has the older JAR's bytecode in
 *     memory and the new symbol is not there yet).
 *   - Whitelabel error pages served with HTTP 500 because the controller
 *     blew up during model-attribute resolution.
 *
 * History: the `/tooling` web controller triggered a `NoClassDefFoundError
 * for com/stacklens/application/port/inbound/tooling/ConfluenceConfigSummary`
 * after a JAR was replaced under a running server. The Spring response was
 * a generic HTTP 500 Whitelabel page, which the existing 12-tooling.spec.js
 * did NOT catch because it only checks DOM selectors (it never asserted on
 * the HTTP status code).
 *
 * These smoke tests therefore hit every Tooling* web/API endpoint with a
 * real HTTP request and assert the response status is in the 2xx/3xx range.
 * A Whitelabel 500 page or a Jackson exception leaking out would be caught
 * immediately.
 */
test.describe('Tooling – smoke tests (catches NoClassDefFoundError at runtime)', () => {

  // ─── Web endpoints (Thymeleaf) ──────────────────────────────────────────

  test('GET /tooling returns 200 (not 500 Whitelabel)', async ({ request }) => {
    const res = await request.get(`${baseUrl}/tooling`);
    // MUST be 2xx — a 500 here means a controller method blew up while
    // resolving model attributes (the exact symptom of NoClassDefFoundError).
    expect(res.status(), '/tooling must not be a Whitelabel 500').toBeLessThan(400);
    // The body must look like a real page, not Spring's Whitelabel error page.
    const body = await res.text();
    expect(body).not.toMatch(/Whitelabel/i);
    expect(body).not.toMatch(/This application has no explicit mapping for \/error/i);
  });

  test('GET /tooling renders the standard layout (sidebar + main content)', async ({ request }) => {
    const res = await request.get(`${baseUrl}/tooling`);
    expect(res.ok()).toBeTruthy();
    const body = await res.text();
    // Standard layout includes the sidebar and tooling-specific card class.
    expect(body).toContain('id="sidebar"');
    expect(body).toMatch(/tl-card|tooling/);
  });

  test('GET /tooling (browser) does not throw a JS error on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const resp = await page.goto('/tooling');
    expect(resp?.status()).toBeLessThan(400);
    await page.waitForSelector('#sidebar', { state: 'visible' });
    // A real Whitelabel 500 would not show the sidebar.
    await expect(page.locator('#sidebar')).toBeVisible();
    // Wait briefly so the JS initialisation has a chance to run.
    await page.waitForTimeout(500);
    expect(errors, 'no uncaught JS errors on /tooling').toHaveLength(0);
  });

  test('GET /tool/999999 redirects to /tooling without 500', async ({ request }) => {
    // The controller does a 302 redirect when the tool id is unknown.
    const res = await request.get(`${baseUrl}/tool/999999`, {
      maxRedirects: 0,
      failOnStatusCode: false,
    });
    // Either 302 (the documented redirect) or 200 (if Playwright followed it)
    // are acceptable; 500 would mean the controller crashed.
    expect(res.status(), '/tool/{unknown} must not crash').toBeLessThan(500);
  });

  // ─── REST endpoints ─────────────────────────────────────────────────────

  test('GET /api/tooling returns 200 with a JSON list (not 500)', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/tooling`);
    expect(res.status(), '/api/tooling must not be a 500').toBeLessThan(400);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('GET /api/tooling/{id} for an unknown id returns 4xx, not 5xx', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/tooling/999999999`);
    // The controller returns 404 — 5xx would be a bug.
    expect(res.status()).toBeLessThan(500);
  });

  test('GET /api/tooling/{id}/status for an unknown id returns 4xx, not 5xx', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/tooling/999999999/status`);
    expect(res.status()).toBeLessThan(500);
  });

  test('GET /api/tooling/files/browse returns 200 with a directory listing', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/tooling/files/browse`);
    expect(res.status()).toBeLessThan(400);
    const body = await res.json();
    expect(body).toHaveProperty('items');
    expect(body).toHaveProperty('currentPath');
  });

  // ─── Defensive: every ToolsService-calling endpoint is reachable ────────
  // The ToolingService is injected into multiple controllers. We enumerate
  // the most important public surfaces and assert each is reachable.

  test('POST /api/tooling with blank body returns 400 (not 5xx)', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/tooling`, {
      data: { name: '', scriptContent: '' },
    });
    expect(res.status()).toBe(400);
  });

  test('GET /api/config/mcp returns 200 with a list (ToolingService is not touched but the namespace is related)', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/config/mcp`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  // ─── Cross-page regression: visiting /tooling from the sidebar does not crash ──

  test('SPA navigation to /tooling via sidebar link works', async ({ page }) => {
    await goto(page, '/');
    // Click the tooling nav-link — exercises the SPA-navigate code path.
    // The sl-nav-drag-item wrapper intercepts pointer events for drag-handle
    // purposes, so we click with force:true (the click is still dispatched
    // to the underlying <a> which the drag-handler code routes to navigateTo()).
    const toolingLink = page.locator('#sidebarNav a[href="/tooling"]').first();
    await toolingLink.click({ force: true });
    await page.waitForSelector('.tl-card', { state: 'visible', timeout: 5_000 });
    // The active link in the sidebar must be the tooling one.
    await expect(page.locator('#sidebarNav a[href="/tooling"].active')).toBeVisible();
  });

  // ─── Regression guard: NoClassDefFoundError must surface as 500 ──────────
  // If the running server is started from a stale JAR, the ThymeleafView
  // resolution may fail with a stack trace containing "NoClassDefFoundError"
  // or "ClassNotFoundException". We assert the response body does NOT
  // contain either of these strings (the body of a healthy 200 page is the
  // rendered HTML; the body of a 500 Whitelabel page may also leak the
  // exception class name).

  test('GET /tooling response body does not leak NoClassDefFoundError', async ({ request }) => {
    const res = await request.get(`${baseUrl}/tooling`);
    const body = await res.text();
    expect(body, 'response must not leak NoClassDefFoundError').not.toMatch(/NoClassDefFoundError/);
    expect(body, 'response must not leak ClassNotFoundException').not.toMatch(/ClassNotFoundException/);
  });

  test('GET /api/tooling response body does not leak NoClassDefFoundError', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/tooling`);
    const body = await res.text();
    expect(body).not.toMatch(/NoClassDefFoundError/);
    expect(body).not.toMatch(/ClassNotFoundException/);
  });
});

test.describe('Tooling – full-page smoke (browser)', () => {

  // For every page that ultimately calls into the ToolingService (directly
  // or transitively via layout-fragment includes), verify the page renders
  // without 5xx and without an uncaught JS error.

  const PAGES_THAT_USE_TOOLING = [
    '/tooling',          // ToolingWebController
    '/tool/999999',      // ToolingWebController (redirects to /tooling)
    '/',                 // root → /search; sidebar loads /api/tooling via loadSidebarTools()
    '/config',           // /config tab mcp might call tooling
  ];

  for (const path of PAGES_THAT_USE_TOOLING) {
    test(`Page ${path} renders without 5xx and without JS errors`, async ({ page, request }) => {
      const errors = [];
      page.on('pageerror', (e) => errors.push(e.message));
      const resp = await page.goto(path);
      // 3xx (redirect) is fine; 5xx is the bug we are guarding against.
      expect(resp?.status() ?? 0).toBeLessThan(500);
      // Wait briefly for any client-side init (loadSidebarTools fires
      // /api/tooling, etc.) to complete.
      await page.waitForTimeout(800);
      expect(errors, `no JS errors on ${path}`).toHaveLength(0);
      // Sidebar must be visible — a 500 Whitelabel page would not have it.
      await expect(page.locator('#sidebar')).toBeVisible();
    });
  }
});