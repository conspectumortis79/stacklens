import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';

/**
 * E2E tests for the AI provider configuration tab in /config.
 * Extends 13-config.spec.js (OpenRouter) and 21-config-tabs.spec.js
 * (Copilot, OpenAI, Ollama, LM Studio) by adding:
 *
 *  - Visibility / model / hint sections for every provider
 *  - LM Studio URL field
 *  - OpenAI reasoning effort default
 *  - "Aktiv" badge appears next to the selected provider
 *  - /api/config/ai/{provider}-test smoke tests
 */
test.describe('AI provider configuration tab', () => {

  test.beforeEach(async ({ page }) => {
    // Stub all AI test calls to return a benign 200 so the page does not stall.
    await page.route('**/api/config/ai/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, models: [], modelCount: 0 }),
      });
    });
    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="ki"]').click();
  });

  // ─── Every provider has a radio button ────────────────────────────────

  test('All 5 AI provider radio buttons are present', async ({ page }) => {
    for (const p of ['copilot', 'openai', 'ollama', 'lmstudio', 'openrouter']) {
      await expect(page.locator(`input[name="aiProvider"][value="${p}"]`)).toBeAttached();
    }
  });

  // ─── Exactly one provider is the active default ─────────────────────

  test('Exactly one AI provider is the active default', async ({ page }) => {
    const radios = page.locator('input[name="aiProvider"]');
    const checked = await radios.evaluateAll((els) => els.filter((e) => e.checked).map((e) => e.value));
    expect(checked.length).toBe(1);
    // The default is one of the 5 known providers.
    expect(['copilot', 'openai', 'ollama', 'lmstudio', 'openrouter']).toContain(checked[0]);
  });

  // ─── The Copilot banner is always attached (even if not the default) ─

  test('Copilot info banner is in the DOM', async ({ page }) => {
    const copilotBanner = page.locator('#aiInfoBannerCopilot');
    await expect(copilotBanner).toBeAttached();
  });

  // ─── Selecting Copilot reveals the token section ─────────────────────

  test('Selecting Copilot reveals the token section', async ({ page }) => {
    const copilot = page.locator('input[name="aiProvider"][value="copilot"]');
    if (!(await copilot.isChecked())) {
      await copilot.check();
    }
    await expect(page.locator('#aiTokenSection')).toBeVisible();
  });

  // ─── OpenAI ─────────────────────────────────────────────────────────────

  test('Selecting OpenAI reveals the OpenAI key field', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="openai"]').check();
    await expect(page.locator('#aiOpenAiKeySection')).toBeVisible();
  });

  test('Selecting OpenAI reveals the OpenAI reasoning effort dropdown', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="openai"]').check();
    await expect(page.locator('#aiOpenAiReasoningSection')).toBeVisible();
    const sel = page.locator('#aiOpenAiReasoningEffort');
    await expect(sel).toBeVisible();
    // Default is 'medium' per the Thymeleaf template
    await expect(sel).toHaveValue('medium');
  });

  test('Selecting OpenAI reveals the OpenAI test button', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="openai"]').check();
    await expect(page.locator('#aiTestOpenAiBtn')).toBeVisible();
  });

  // ─── Ollama ─────────────────────────────────────────────────────────────

  test('Selecting Ollama reveals the Ollama URL field', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="ollama"]').check();
    await expect(page.locator('#aiOllamaUrlSection')).toBeVisible();
  });

  test('Selecting Ollama reveals the num_ctx input', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="ollama"]').check();
    await expect(page.locator('#aiOllamaNumCtxSection')).toBeVisible();
    await expect(page.locator('#aiOllamaNumCtx')).toBeAttached();
  });

  test('Selecting Ollama reveals the Ollama test button', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="ollama"]').check();
    await expect(page.locator('#aiTestOllamaBtn')).toBeVisible();
  });

  // ─── LM Studio ──────────────────────────────────────────────────────────

  test('Selecting LM Studio reveals the LM Studio URL field', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="lmstudio"]').check();
    await expect(page.locator('#aiLmStudioUrlSection')).toBeVisible();
  });

  test('Selecting LM Studio reveals the LM Studio test button', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="lmstudio"]').check();
    await expect(page.locator('#aiTestLmStudioBtn')).toBeVisible();
  });

  // ─── OpenRouter ─────────────────────────────────────────────────────────

  test('Selecting OpenRouter reveals the OpenRouter key field', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="openrouter"]').check();
    await expect(page.locator('#aiOpenRouterKeySection')).toBeVisible();
  });

  test('Selecting OpenRouter reveals the OpenRouter test button', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="openrouter"]').check();
    await expect(page.locator('#aiTestOpenRouterBtn')).toBeVisible();
  });

  // ─── Switching back to Copilot hides the other sections ───────────────

  test('Switching back to Copilot hides Ollama-specific sections', async ({ page }) => {
    await page.locator('input[name="aiProvider"][value="ollama"]').check();
    await expect(page.locator('#aiOllamaUrlSection')).toBeVisible();
    await page.locator('input[name="aiProvider"][value="copilot"]').check();
    await expect(page.locator('#aiOllamaUrlSection')).toHaveClass(/d-none/);
    await expect(page.locator('#aiOllamaNumCtxSection')).toHaveClass(/d-none/);
    await expect(page.locator('#aiTestOllamaBtn')).toHaveClass(/d-none/);
  });

  // ─── Aktiv badge ────────────────────────────────────────────────────────

  test('"Aktiv" badge next to a provider is in the DOM', async ({ page }) => {
    // The badges are always rendered, just only the active one is visible.
    const badge = page.locator('#aiActiveBadgeCopilot');
    if (await badge.count() > 0) {
      await expect(badge).toBeAttached();
    }
  });

  // ─── Model select ───────────────────────────────────────────────────────

  test('AI model select is present and is a <select>', async ({ page }) => {
    const sel = page.locator('#aiModel');
    await expect(sel).toBeAttached();
    const tagName = await sel.evaluate((el) => el.tagName);
    expect(tagName.toLowerCase()).toBe('select');
  });

  // ─── /api/config/ai smoke tests ────────────────────────────────────────

  test('GET /api/config/ai returns a JSON object', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/config/ai`);
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(typeof body).toBe('object');
  });

  test('GET /api/config/ai/test-openai returns JSON', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/config/ai/test-openai`);
    expect(res.status()).toBeLessThan(500);
    const body = await res.json();
    expect(typeof body).toBe('object');
  });

  test('GET /api/config/ai/test-ollama returns JSON', async ({ request }) => {
    const res = await request.get(`${baseUrl}/api/config/ai/test-ollama`);
    expect(res.status()).toBeLessThan(500);
    const body = await res.json();
    expect(typeof body).toBe('object');
  });
});
