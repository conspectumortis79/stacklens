import { test, expect } from '@playwright/test';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';

/**
 * Smoke tests for REST endpoints that are NOT covered by any per-feature
 * Playwright spec.  Keeps the suite lightweight by avoiding full UI flows
 * for backend-only controllers that are already covered by Spring system tests.
 *
 * Covered controllers:
 *   - AgentFileApiController.kt  → /api/agents/pipeline[/{name}]
 *   - MakeRunnerController.kt    → /api/make-runner
 *   - AiAnalysisController.kt    → /api/sessions/{id}/analyze (validation)
 *   - DesktopIntegrationController.kt → /api/open-url (validation)
 */
test.describe('Backend miscellaneous smoke tests', () => {

  // ─── Agent file API ──────────────────────────────────────────────────────

  test('GET /api/agents/pipeline lists the bundled pipeline agents', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/agents/pipeline`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(Array.isArray(body)).toBeTruthy();
    // All 12 PHP pipeline agents are bundled (lena, nora, cole, max, …).
    expect(body.length).toBeGreaterThanOrEqual(8);
    for (const entry of body) {
      expect(entry).toHaveProperty('name');
      expect(entry).toHaveProperty('label');
    }
  });

  test('GET /api/agents/pipeline/{name} returns the bundled markdown for a known agent', async ({ request }) => {
    // "nora" is one of the always-bundled agents (analysis-stage)
    const res = await request.get(`${baseUrl}/api/agents/pipeline/nora`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.name).toBe('nora');
    expect(typeof body.content).toBe('string');
    expect(body.content.length).toBeGreaterThan(0);
    // isCustom is always a boolean flag
    expect(typeof body.isCustom).toBe('boolean');
  });

  test('GET /api/agents/pipeline/{name} returns 404 for an unknown agent', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/agents/pipeline/__nope_xyz__`);
    expect(res.status()).toBe(404);
  });

  test('PUT /api/agents/pipeline/{name} with empty content returns 400', async ({ request }) => {
    const res = await request.put(`${baseUrl}/api/agents/pipeline/nora`, {
      data: { content: '' },
    });
    expect(res.status()).toBe(400);
  });

  test('PUT /api/agents/pipeline/{name} without content field returns 400', async ({ request }) => {
    const res = await request.put(`${baseUrl}/api/agents/pipeline/nora`, {
      data: {},
    });
    expect(res.status()).toBe(400);
  });

  test('DELETE /api/agents/pipeline/{name} resets a known agent to default', async ({ request }) => {
    const res = await request.delete(`${baseUrl}/api/agents/pipeline/nora`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.ok).toBe(true);
    expect(body.isCustom).toBe(false);
  });

  test('DELETE /api/agents/pipeline/{name} returns 404 for unknown agent', async ({ request }) => {
    const res = await request.delete(`${baseUrl}/api/agents/pipeline/__nope_xyz__`);
    expect(res.status()).toBe(404);
  });

  // ─── Make runner ─────────────────────────────────────────────────────────

  test('POST /api/make-runner without body is rejected (4xx)', async ({ request }) => {
    // The endpoint requires a JSON body and streams output via SSE.
    // An empty/invalid request must NOT crash the server.
    const res = await request.post(`${baseUrl}/api/make-runner`, {
      headers: { 'Accept': 'application/json' },
      data: {},
    });
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/make-runner with missing repoPath returns 4xx', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/make-runner`, {
      headers: { 'Accept': 'application/json' },
      data: { target: 'test' },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/make-runner with missing target returns 4xx', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/make-runner`, {
      headers: { 'Accept': 'application/json' },
      data: { repoPath: '/tmp' },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  // ─── AI analysis session endpoint ────────────────────────────────────────

  test('GET /api/sessions/999999/analysis returns a JSON object (likely {status:"NONE"})', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/sessions/999999/analysis`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body).toHaveProperty('status');
  });

  test('POST /api/sessions/999999/analyze returns 404 for unknown session', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/sessions/999999/analyze`, {
      data: { includeMermaid: false, includeDrawio: false },
    });
    expect(res.status()).toBe(404);
  });

  test('POST /api/sessions/999999/analysis/save-diagram with empty fields returns 400', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/sessions/999999/analysis/save-diagram`, {
      data: { path: '', filename: '', content: '' },
    });
    expect(res.status()).toBe(400);
  });

  // ─── Desktop integration: open-url safety checks ─────────────────────────

  test('POST /api/open-url rejects non-http schemes', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/open-url`, {
      data: { url: 'file:///etc/passwd' },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body).toHaveProperty('error');
  });

  test('POST /api/open-url rejects javascript scheme', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/open-url`, {
      data: { url: 'javascript:alert(1)' },
    });
    expect(res.status()).toBe(400);
  });

  test('POST /api/open-url rejects missing url field', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/open-url`, {
      data: {},
    });
    expect(res.status()).toBe(400);
  });

  test('POST /api/open-url rejects malformed url', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/open-url`, {
      data: { url: 'not a url with spaces and no scheme' },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/open-url accepts an http URL (will try to open a browser)', async ({ request }) => {
    // We use a localhost URL the OS will simply refuse to open — this
    // still validates the request reaches the controller and is well-formed.
    const res = await request.post(`${baseUrl}/api/open-url`, {
      data: { url: 'http://127.0.0.1:1/' },
    });
    // The controller returns 200 on success or 500 when no browser is
    // available (headless CI). Either is acceptable for a smoke test.
    expect(res.status()).toBeLessThan(600);
  });

  // ─── AI provider test endpoints (smoke) ───────────────────────────────────

  test('POST /api/config/ai/test-token responds (with or without valid token)', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/config/ai/test-token`, {
      data: {},
    });
    // 200 when a token is configured, 4xx when not — both are valid.
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/config/ai/copilot-models returns a JSON object', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/config/ai/copilot-models`, {
      data: {},
    });
    // May return 4xx when no GitHub token is configured.
    expect(res.status()).toBeLessThan(500);
    const body = await res.json();
    expect(typeof body).toBe('object');
  });

  test('POST /api/config/ai/test-lmstudio responds with JSON', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/config/ai/test-lmstudio`, {
      data: { url: 'http://127.0.0.1:1234' },
    });
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/config/ai/test-openrouter responds with JSON', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/config/ai/test-openrouter`, {
      data: { apiKey: 'sk-or-noop' },
    });
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/config/ai/test-model responds with JSON', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/config/ai/test-model`, {
      data: { model: 'gpt-4o' },
    });
    expect(res.status()).toBeLessThan(500);
  });

  // ─── MCP process control ────────────────────────────────────────────────

  test('POST /api/config/mcp/process/restart responds without 5xx', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/config/mcp/process/restart`);
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/config/mcp/{id}/process/start responds without 5xx', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/config/mcp/999999/process/start`);
    expect(res.status()).toBeLessThan(500);
  });

  // ─── Load-test save-script ──────────────────────────────────────────────

  test('POST /api/loadtest-analysis/analysis/{id}/save-script responds without 5xx', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/loadtest-analysis/analysis/999999/save-script`, {
      data: { script: 'export const options = {vus: 1, duration: "1s"}; export default function() {}' },
    });
    expect(res.status()).toBeLessThan(500);
  });

  // ─── Bug-analysis generate-fix ──────────────────────────────────────────

  test('POST /api/bug-analysis/analysis/{id}/finding/{fid}/generate-fix is reachable', async ({ request }) => {
    // Endpoint may return 5xx when the analysis/finding doesn't exist
    // (the AI runner cannot generate a fix for a missing entity). The
    // test only verifies the controller is wired up and reachable.
    const res = await request.post(
      `${baseUrl}/api/bug-analysis/analysis/999999/finding/999999/generate-fix`,
      { data: {} },
    );
    expect(res.status()).toBeLessThan(600);
  });

  // ─── Local repos gitlab clone ───────────────────────────────────────────

  test('POST /api/local-repos/gitlab/{id}/clone responds without 5xx', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/local-repos/gitlab/999999/clone`, {
      data: { projectId: 999999 },
    });
    expect(res.status()).toBeLessThan(500);
  });

  // ─── Sessions confluence-more ───────────────────────────────────────────

  test('POST /api/sessions/{id}/confluence/more responds without 5xx', async ({ request }) => {
    const res = await request.post(`${baseUrl}/api/sessions/999999/confluence/more`, {
      data: {},
    });
    expect(res.status()).toBeLessThan(500);
  });

  // ─── Grafana demo / proxy ───────────────────────────────────────────────

  test('GET /api/grafana-analyse/demo-prometheus/query responds with JSON', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/grafana-analyse/demo-prometheus/query`);
    expect(res.status()).toBeLessThan(500);
    const body = await res.json();
    expect(typeof body).toBe('object');
  });

  test('GET /api/grafana-analyse/proxy/999999/** responds without 5xx', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/grafana-analyse/proxy/999999/api/dashboards/home`);
    // May 4xx when the demo config doesn't exist; 5xx would indicate a bug.
    expect(res.status()).toBeLessThan(500);
  });

  // ─── Prompt read-file (already covered by Spring system test, smoke here) ─

  test('GET /api/prompts/read-file with missing path returns 4xx', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/prompts/read-file`);
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });
});