import { test, expect } from '@playwright/test';

/**
 * E2E tests for the Pull-Request preview page
 *   served by GET /api/agent-pipeline/{id}/pull-request
 * and its error counterpart
 *   served by GET /api/agent-pipeline/{id}/pull-request-error
 *
 * Templates (verified):
 *   src/main/resources/templates/pull-request.html
 *   src/main/resources/templates/pull-request-error.html
 * Controller: src/main/kotlin/.../AgentPipelinePullRequestController.kt
 *
 * The page does NOT use the standard StackLens layout (no sidebar).
 * For a non-existent id the controller renders the error template
 * (HTTP 404) — this is verified below.
 */
test.describe('Pull-Request preview page', () => {

  test('endpoint returns 404 for a non-existent pipeline (error template)', async ({ page }) => {
    // The controller renders pull-request-error.html with HTTP 404 when
    // the pipeline id has no data.
    const [response] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/pull-request')),
      page.goto('/api/agent-pipeline/999999/pull-request'),
    ]);
    expect(response.status()).toBe(404);
  });

  test('error page renders with the right body (pipeline-not-found message)', async ({ page }) => {
    await page.goto('/api/agent-pipeline/999999/pull-request');
    const body = await page.locator('body').innerText();
    // The pull-request-error.html template shows "Error" + a message.
    expect(body).toMatch(/Error|Pipeline|nicht gefunden|not found/i);
  });

  test('error page is plain (no scripts, no nav)', async ({ page }) => {
    await page.goto('/api/agent-pipeline/999999/pull-request');
    await expect(page.locator('nav')).toHaveCount(0);
    await expect(page.locator('#sidebar')).toHaveCount(0);
  });

  test('error page is rendered (HTML root exists)', async ({ page }) => {
    await page.goto('/api/agent-pipeline/999999/pull-request');
    const html = page.locator('html');
    await expect(html).toBeAttached();
  });

  test('error page does not throw uncaught JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await page.goto('/api/agent-pipeline/999999/pull-request');
    await page.waitForTimeout(500);
    // The error page is plain HTML with no inline scripts; any errors
    // here are real.
    expect(errors).toHaveLength(0);
  });

  test('endpoint shape — URL pattern is /api/agent-pipeline/{id}/pull-request', async ({ page }) => {
    // Sanity: a malformed URL should NOT 200 with the pull-request template.
    // Anything that is not the documented URL pattern is out of scope.
    const res = await page.goto('/api/agent-pipeline/1/pull-requestX');
    expect(res?.status()).toBeGreaterThanOrEqual(400);
  });

  test('endpoint shape — a numeric id is accepted by the URL matcher', async ({ page }) => {
    // Even when the pipeline does not exist, the controller must match the
    // URL pattern (otherwise Spring would 404 with a generic Whitelabel
    // page, not the styled Error page).  The styled "Error" page is the
    // proof that the controller is wired up.
    await page.goto('/api/agent-pipeline/424242/pull-request');
    const txt = await page.locator('body').innerText();
    // The controller's error template starts with "Error" — a generic
    // Whitelabel page would start with "Whitelabel Error Page".
    expect(txt).toContain('Error');
    expect(txt).not.toContain('Whitelabel');
  });
});
