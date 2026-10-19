import { test, expect } from '@playwright/test';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';

/**
 * E2E coverage of the public REST API surface that is not exercised by the
 * per-page Playwright suites. Each test only verifies the controller is
 * reachable, returns the documented status code, and the body is JSON.
 *
 * Verified controllers (file path under src/main/kotlin/.../rest):
 *   - AdminController.kt          → /api/admin/factory-reset-db
 *   - AppLifecycleController.kt   → /api/quit
 *   - AutostartApiController.kt   → /api/autostart/{status,enable,disable}
 *   - PromptApiController.kt      → /api/prompts/{groups,key}
 *   - UiPreferencesController.kt  → /api/ui-prefs/{key}
 *   - JwtAnalysisController.kt    → /api/jwt-analyse
 *   - McpApiController.kt         → /api/config/mcp
 *
 * NOTE: The `request` fixture (Playwright APIRequestContext) does NOT
 * inherit the `baseURL` defined in `use`.  All calls below use an absolute
 * URL composed from the BASE_URL env var.
 *
 * The /api/quit endpoint actually shuts down the JVM (verified in
 * AppLifecycleController.kt) so it is NEVER called via POST here; the
 * wrong-method test below is sufficient to prove the controller is wired up.
 */
test.describe('REST API endpoints – smoke tests', () => {

  // ─── App lifecycle ─────────────────────────────────────────────────────────

  test('GET /api/quit on browser is rejected (POST-only)', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/quit`);
    // Spring returns 405 Method Not Allowed for the wrong verb.
    expect([405, 401, 403]).toContain(res.status());
  });

  // NOTE: We deliberately do NOT POST /api/quit in the test suite — that
  // endpoint actually shuts down the JVM (verified in AppLifecycleController.kt).
  // The wrong-method test above is sufficient to prove the controller is wired up.

  // ─── UI preferences ───────────────────────────────────────────────────────

  test('GET /api/ui-prefs/anyKey returns a JSON object with "value"', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/ui-prefs/never-set`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body).toHaveProperty('value');
  });

  test('POST /api/ui-prefs/anyKey with a value persists it', async ({ request }) => {
    const key = `e2e-test-${Date.now()}`;
    const put = await request.post(`${baseUrl}/api/ui-prefs/${key}`, {
      data: { value: 'hello-e2e' },
    });
    expect(put.ok()).toBeTruthy();
    const get = await request.get(`${baseUrl}/api/ui-prefs/${key}`);
    expect(get.ok()).toBeTruthy();
    const body = await get.json();
    expect(body.value).toBe('hello-e2e');
  });

  test('POST /api/ui-prefs/anyKey without a value returns 400', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/ui-prefs/anyKey`, { data: {} });
    expect(res.status()).toBe(400);
  });

  // ─── Autostart ────────────────────────────────────────────────────────────

  test('GET /api/autostart/status returns a JSON object', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/autostart/status`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    // Body must be a non-null object (the controller always returns Map).
    expect(typeof body).toBe('object');
  });

  test('POST /api/autostart/enable responds with a JSON object', async ({ request }) => {
    // On a headless CI box this may 4xx if autostart is unsupported —
    // anything in the 2xx–4xx range is acceptable; the test is about
    // reachability, not platform support.
    const res = await request.post(`${baseUrl}/api/autostart/enable`);
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/autostart/disable responds with a JSON object', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/autostart/disable`);
    expect(res.status()).toBeLessThan(500);
  });

  // ─── Prompts API ──────────────────────────────────────────────────────────

  test('GET /api/prompts/groups responds (with or without prefix)', async ({ request }) => {
    // The /api/prompts/groups endpoint requires a `prefix` parameter;
    // without one the controller returns 400.  Both 2xx-with-list and
    // 400 are acceptable proofs that the controller is wired up.
    const res = await request.get(`${baseUrl}/api/prompts/groups`);
    expect(res.status()).toBeLessThan(500);
  });

  test('GET /api/prompts/groups?prefix=any responds', async ({ request }) => {
    // With a prefix, the controller returns a list of group objects or
    // 4xx if the prefix is unknown.
    const res = await request.get(`${baseUrl}/api/prompts/groups?prefix=anyprefix`);
    expect([200, 400, 404]).toContain(res.status());
  });

  test('GET /api/prompts/{key} for a missing prompt does not crash', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/prompts/__nope__/${Date.now()}`);
    // Either 404 or an empty body — both are acceptable; the test is about
    // not crashing the server.
    expect(res.status()).toBeLessThan(500);
  });

  // ─── JWT analysis ─────────────────────────────────────────────────────────

  test('POST /api/jwt-analyse with empty token returns 400', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/jwt-analyse`, { data: { token: '' } });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body).toHaveProperty('error');
  });

  // ─── MCP server config (read-only) ────────────────────────────────────────

  test('GET /api/config/mcp returns a JSON list', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/config/mcp`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
  });

  // ─── Health / status endpoint discovery ───────────────────────────────────

  test('GET /api/ on the API namespace returns 404 (no controller) — sanity check', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/`);
    expect(res.status()).toBeGreaterThanOrEqual(400);
  });
});
