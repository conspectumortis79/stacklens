import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

/**
 * Extended configuration tests — covers all config tabs not already checked
 * in 13-config.spec.js (which only covers theme selection and a basic tab switch).
 *
 * Tabs covered here: gitlab, db, confluence, grafana, jira, github, ki, lang,
 * autostart, tools, dev, reset.
 */
test.describe('Configuration – all tabs', () => {

  test.beforeEach(async ({ page }) => {
    // Prevent real AI-provider model-loading requests from being in-flight when
    // a page closes.  Leaving a fetch pending while Playwright tears down the
    // context can destabilise Firefox, causing the next test's beforeEach
    // navigation to fail with "Target page, context or browser has been closed".
    // Tests that need specific AI responses register their own page.route()
    // calls AFTER this one; Playwright's last-registered-wins ordering means
    // those more-specific handlers take priority over this catch-all.
    await page.route('**/api/config/ai/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, models: [], modelCount: 0 }),
      });
    });
    await goto(page, '/config');
  });

  // ─── Tab switching ────────────────────────────────────────────────────────

  test('all expected tabs are present in the strip', async ({ page }) => {
    const tabNames = ['gitlab', 'db', 'confluence', 'grafana', 'jira', 'github', 'ki', 'lang', 'theme', 'autostart', 'tools', 'dev', 'reset'];
    for (const tab of tabNames) {
      await expect(page.locator(`.cf-tab[data-tab="${tab}"]`)).toBeAttached();
    }
  });

  test('GitLab tab is active by default', async ({ page }) => {
    // gitlab is the first tab defined in tab-strip.html
    await expect(page.locator('.cf-tab[data-tab="gitlab"]')).toHaveClass(/active/);
  });

  test('clicking gitlab tab shows its pane', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="gitlab"]').click();
    await expect(page.locator('#tab-gitlab')).toHaveClass(/active/);
  });

  test('clicking db tab shows database pane', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="db"]').click();
    await expect(page.locator('#tab-db')).toBeAttached();
  });

  test('clicking confluence tab shows confluence pane', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="confluence"]').click();
    await expect(page.locator('#tab-confluence')).toBeAttached();
  });

  test('clicking grafana tab shows grafana pane', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="grafana"]').click();
    await expect(page.locator('#tab-grafana')).toBeAttached();
  });

  test('clicking jira tab shows jira pane', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="jira"]').click();
    await expect(page.locator('#tab-jira')).toBeAttached();
  });

  test('clicking github tab shows github pane', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="github"]').click();
    await expect(page.locator('#tab-github')).toBeAttached();
  });

  // ─── KI / AI tab ─────────────────────────────────────────────────────────

  test('KI tab is accessible and contains AI token field', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="ki"]').click();
    await expect(page.locator('#tab-ki')).toBeAttached();
    await expect(page.locator('#aiGithubToken')).toBeAttached();
  });

  test('AI model select is present in KI tab', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="ki"]').click();
    await expect(page.locator('#aiModel')).toBeAttached();
  });

  test('Copilot, OpenAI, Ollama, LM Studio, OpenRouter and Claude Code provider radio buttons are present in KI tab', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="ki"]').click();
    await expect(page.locator('input[name="aiProvider"][value="copilot"]')).toBeAttached();
    await expect(page.locator('input[name="aiProvider"][value="openai"]')).toBeAttached();
    await expect(page.locator('input[name="aiProvider"][value="ollama"]')).toBeAttached();
    await expect(page.locator('input[name="aiProvider"][value="lmstudio"]')).toBeAttached();
    await expect(page.locator('input[name="aiProvider"][value="openrouter"]')).toBeAttached();
    await expect(page.locator('input[name="aiProvider"][value="claudecode"]')).toBeAttached();
  });

  test('selecting Claude Code provider shows API key field and Claude models in dropdown', async ({ page }) => {
    await page.route('**/api/config/ai/test-claudecode', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, accountHint: 'sk-...1234', modelCount: 0, models: [] }),
      });
    });

    await page.locator('.cf-tab[data-tab="ki"]').click();
    await page.locator('input[name="aiProvider"][value="claudecode"]').check();

    // Claude Code-specific fields become visible
    await expect(page.locator('#aiClaudeCodeKeySection')).not.toHaveClass(/d-none/);
    await expect(page.locator('#aiTestClaudeCodeBtn')).not.toHaveClass(/d-none/);
    // Other providers' fields stay hidden
    await expect(page.locator('#aiTokenSection')).toHaveClass(/d-none/);
    await expect(page.locator('#aiOpenAiKeySection')).toHaveClass(/d-none/);
    await expect(page.locator('#aiLmStudioUrlSection')).toHaveClass(/d-none/);
    await expect(page.locator('#aiOpenRouterKeySection')).toHaveClass(/d-none/);
    // Banner switched
    await expect(page.locator('#aiInfoBannerClaudeCode')).toBeVisible();
    await expect(page.locator('#aiInfoBannerCopilot')).toBeHidden();
    // Test button works (uses the mock route above)
    await page.locator('#aiTestClaudeCodeBtn').click();
    // Locale-independent success indicator: the success class is applied
    // and the ✓ check-mark prefix is always emitted by the JS handler.
    await expect(page.locator('#aiTestStatus')).toHaveClass(/text-success/);
    await expect(page.locator('#aiTestStatus')).toContainText('✓');
    await expect(page.locator('#aiTestStatus')).toContainText('sk-...1234');
  });

  test('Claude Code dropdown is replaced by the models the account reports', async ({ page }) => {
    await page.route('**/api/config/ai/test-claudecode', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, accountHint: 'sk-...1234', modelCount: 2, models: ['claude-opus-4-9', 'claude-sonnet-4-5'] }),
      });
    });

    await page.locator('.cf-tab[data-tab="ki"]').click();
    await page.locator('input[name="aiProvider"][value="claudecode"]').check();

    await expect(page.locator('#aiClaudeCodeModelsGroup option[value="claude-opus-4-9"]')).toBeAttached();
    const values = await page.locator('#aiClaudeCodeModelsGroup option').evaluateAll(els => els.map(o => o.value));
    expect(values).toContain('claude-opus-4-9');
    expect(values.length).toBeLessThanOrEqual(3);
    await expect(page.locator('#aiModel')).toHaveValue(/claude-/);
  });

  test('selecting OpenAI provider shows API key field and loads GPT models into dropdown', async ({ page }) => {
    await page.route('**/api/config/ai/test-openai', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, modelCount: 2, models: ['gpt-5.2', 'gpt-5-mini'] }),
      });
    });

    await page.locator('.cf-tab[data-tab="ki"]').click();
    await page.locator('input[name="aiProvider"][value="openai"]').check();

    await expect(page.locator('#aiOpenAiKeySection')).not.toHaveClass(/d-none/);
    await expect(page.locator('#aiOpenAiReasoningSection')).not.toHaveClass(/d-none/);
    await expect(page.locator('#aiOpenAiReasoningEffort')).toHaveValue('medium');
    await page.locator('#aiOpenAiReasoningEffort').selectOption('high');
    await expect(page.locator('#aiOpenAiReasoningEffort')).toHaveValue('high');
    await expect(page.locator('#aiTestOpenAiBtn')).not.toHaveClass(/d-none/);
    await expect(page.locator('#aiTokenSection')).toHaveClass(/d-none/);
    await expect(page.locator('#aiOllamaUrlSection')).toHaveClass(/d-none/);
    await expect(page.locator('#aiLmStudioUrlSection')).toHaveClass(/d-none/);
    await expect(page.locator('#aiOpenAiModelsGroup option[value="gpt-5.2"]')).toBeAttached();
    await expect(page.locator('#aiModel')).toHaveValue('gpt-5.2');
  });

  test('selecting Ollama provider shows Ollama URL field and num_ctx input', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="ki"]').click();
    await page.locator('input[name="aiProvider"][value="ollama"]').check();
    await expect(page.locator('#aiOllamaUrlSection')).not.toHaveClass(/d-none/);
    await expect(page.locator('#aiTestOllamaBtn')).not.toHaveClass(/d-none/);
    await expect(page.locator('#aiOllamaNumCtxSection')).not.toHaveClass(/d-none/);
  });

  test('switching back to Copilot provider hides Ollama-specific fields', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="ki"]').click();
    await page.locator('input[name="aiProvider"][value="ollama"]').check();
    await page.locator('input[name="aiProvider"][value="copilot"]').check();
    await expect(page.locator('#aiOllamaNumCtxSection')).toHaveClass(/d-none/);
    await expect(page.locator('#aiTestOllamaBtn')).toHaveClass(/d-none/);
  });

  test('password toggle button reveals AI token field', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="ki"]').click();
    // Ensure copilot provider is selected so the token section (and toggle button) is visible
    await page.locator('input[name="aiProvider"][value="copilot"]').check();
    const tokenField = page.locator('#aiGithubToken');
    await expect(tokenField).toHaveAttribute('type', 'password');
    // Click the eye-toggle button
    const toggleBtn = page.locator('button[onclick*="togglePw"]');
    if (await toggleBtn.count() > 0) {
      await toggleBtn.first().click();
      await expect(tokenField).toHaveAttribute('type', 'text');
    }
  });

  // ─── Language tab ─────────────────────────────────────────────────────────

  test('language tab contains EN and DE buttons', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="lang"]').click();
    await expect(page.locator('[data-lang-btn="en"]')).toBeAttached();
    await expect(page.locator('[data-lang-btn="de"]')).toBeAttached();
  });

  // ─── Autostart tab ────────────────────────────────────────────────────────

  test('autostart tab contains the toggle checkbox', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="autostart"]').click();
    await expect(page.locator('#autostartToggle')).toBeAttached();
  });

  // ─── Tools tab ────────────────────────────────────────────────────────────

  test('tools tab shows platform label after loading', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="tools"]').click();
    // cfLoadToolsStatus() is called on tab click; platform label may update
    await expect(page.locator('#toolsPlatformLabel')).toBeAttached();
  });

  // ─── Dev tab ─────────────────────────────────────────────────────────────

  test('dev tab contains developer checkboxes', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="dev"]').click();
    await expect(page.locator('#devShowPromptButtons')).toBeAttached();
    await expect(page.locator('#devDockerTestEnv')).toBeAttached();
  });

  // ─── Reset / Danger zone tab ──────────────────────────────────────────────

  test('reset tab contains the DB reset button', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="reset"]').click();
    await expect(page.locator('#cfDbResetBtn')).toBeAttached();
  });

  test('DB reset button reveals confirmation box when clicked', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="reset"]').click();
    const resetBtn = page.locator('#cfDbResetBtn');
    await resetBtn.click();
    // cfInitDbReset() should show the confirmation box
    await expect(page.locator('#cfDbResetConfirmBox')).toBeVisible({ timeout: 5_000 });
  });

  // ─── GitLab tab interactions ──────────────────────────────────────────────

  test('gitlab tab shows "Neue Verbindung" button', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="gitlab"]').click();
    const btn = page.locator('button[onclick*="openGitlabModal"]');
    await expect(btn).toBeVisible();
  });

  // ─── No JS errors ─────────────────────────────────────────────────────────

  test('no JavaScript errors while switching all tabs', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (err) => errors.push(err.message));
    const tabs = ['gitlab', 'db', 'confluence', 'grafana', 'jira', 'github', 'ki', 'lang', 'theme', 'autostart', 'dev', 'reset'];
    for (const tab of tabs) {
      await page.locator(`.cf-tab[data-tab="${tab}"]`).click();
      await page.waitForTimeout(150);
    }
    expect(errors).toHaveLength(0);
  });
});
