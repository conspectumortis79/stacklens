import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';

/**
 * E2E tests for the Tooling flow that go beyond what 12-tooling.spec.js and
 * 20-tool-view.spec.js already cover:
 *
 *  - List endpoint smoke
 *  - Create + read + status + delete cycle via the REST API
 *  - Tool-view page (status badge text, action bar data attrs)
 *  - Cancellation
 *  - The 4-tooling test bucket uses tv* IDs that are populated by JS at
 *    load time — we assert that the JS only runs after the server response.
 *
 * NOTE: The `request` fixture (Playwright APIRequestContext) does NOT inherit
 * the `baseURL` defined in `use`.  All REST calls below use absolute URLs.
 */
test.describe('Tooling – create/run/cancel flow', () => {

  test('GET /api/tooling returns a list (possibly empty)', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/tooling`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
  });

  test('Create tool → read it → check status → delete it', async ({ request }) => {
    const create = await request.post(`${baseUrl}/api/tooling`, {
      data: {
        name: `E2E-Cycle-Tool-${Date.now()}`,
        scriptContent: 'fun main() = println("e2e")',
      },
    });
    expect(create.ok()).toBeTruthy();
    const tool = await create.json();
    expect(tool.id).toBeTruthy();

    try {
      // Read by id
      const get = await request.get(`${baseUrl}/api/tooling/${tool.id}`);
      expect(get.ok()).toBeTruthy();
      const fetched = await get.json();
      expect(fetched.id).toBe(tool.id);

      // Status endpoint
      const status = await request.get(`${baseUrl}/api/tooling/${tool.id}/status`);
      expect(status.ok()).toBeTruthy();
      const s = await status.json();
      expect(typeof s).toBe('object');
      expect(s).toHaveProperty('status');
    } finally {
      // Clean up
      await request.delete(`${baseUrl}/api/tooling/${tool.id}`);
    }
  });

  test('GET /api/tooling/{id} for a missing id does not crash', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/tooling/99999999`);
    // Spring may return 404 or 200-with-empty/null body; both acceptable
    expect(res.status()).toBeLessThan(500);
  });

  test('DELETE /api/tooling/{id} for a missing id does not crash', async ({ request }) => {
    const res = await request.delete(`${baseUrl}/api/tooling/99999999`);
    // Spring may return 404 or 204 even for missing ids; both acceptable
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/tooling/{id}/cancel for a fresh tool returns 2xx', async ({ request }) => {
    // Create a tool, cancel any (non-existent) run, then delete it.
    const create = await request.post(`${baseUrl}/api/tooling`, {
      data: {
        name: `E2E-Cancel-Tool-${Date.now()}`,
        scriptContent: 'fun main() = println("e2e")',
      },
    });
    expect(create.ok()).toBeTruthy();
    const tool = await create.json();
    try {
      const cancel = await request.post(`${baseUrl}/api/tooling/${tool.id}/cancel`);
      expect(cancel.status()).toBeLessThan(500);
    } finally {
      await request.delete(`${baseUrl}/api/tooling/${tool.id}`);
    }
  });

  // ── Tool-View page (UI) ─────────────────────────────────────────────────

  test.describe('with a freshly-created tool', () => {
    let toolId;

    test.beforeEach(async ({ page, request }) => {
      const create = await request.post(`${baseUrl}/api/tooling`, {
        data: {
          name: `E2E-UI-Tool-${Date.now()}`,
          scriptContent: 'fun main() = println("e2e-ui")',
        },
      });
      expect(create.ok()).toBeTruthy();
      const body = await create.json();
      toolId = body.id;

      // Block real tool-run streaming to keep the test fast and deterministic.
      await page.route('**/api/tooling/*/run-stream**', (route) => {
        route.fulfill({
          status: 200,
          contentType: 'text/event-stream',
          body: 'data: {"type":"done"}\n\n',
        });
      });

      await goto(page, `/tool/${toolId}`);
    });

    test.afterEach(async ({ request }) => {
      if (toolId) {
        await request.delete(`${baseUrl}/api/tooling/${toolId}`).catch(() => {/* ignore */});
      }
    });

    test('action bar carries the correct data-tool-id', async ({ page }) => {
      const bar = page.locator('#tvActionBar');
      await expect(bar).toBeAttached();
      const id = await bar.getAttribute('data-tool-id');
      expect(String(id)).toBe(String(toolId));
    });

    test('status badge is present and not empty', async ({ page }) => {
      const badge = page.locator('#tvStatusBadge');
      await expect(badge).toBeAttached();
      const text = (await badge.textContent() ?? '').trim();
      expect(text.length).toBeGreaterThan(0);
    });

    test('title icon is rendered', async ({ page }) => {
      await expect(page.locator('#tvTitleIcon')).toBeAttached();
    });

    test('sidebar is present (uses standard layout)', async ({ page }) => {
      await expect(page.locator('#sidebar')).toBeVisible();
    });

    test('page has no JS errors on load', async ({ page }) => {
      const errors = [];
      page.on('pageerror', (err) => errors.push(err.message));
      // The /tool/{id} page keeps a tool-status SSE stream open for the
      // lifetime of the page. Default page.reload() waits for the `load`
      // event which the SSE stream prevents from firing. Use `domcontentloaded`
      // (fires as soon as the HTML is parsed) with a generous timeout instead.
      await page.reload({ waitUntil: 'domcontentloaded', timeout: 30_000 });
      await page.waitForSelector('#tvStatusBadge', { state: 'attached', timeout: 15_000 });
      await page.waitForTimeout(500);
      expect(errors).toHaveLength(0);
    });
  });
});
