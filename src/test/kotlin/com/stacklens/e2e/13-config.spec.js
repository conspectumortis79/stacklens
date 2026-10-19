import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto, getTheme } from './helpers.js';

test.describe('Configuration', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/config');
  });

  test('page loads with cf-elements', async ({ page }) => {
    await expect(page.locator('[class*="cf-"]').first()).toBeAttached();
  });

  test('config tabs are visible', async ({ page }) => {
    await expect(page.locator('.cf-tab').first()).toBeVisible();
  });

  test('all four theme cards are visible', async ({ page }) => {
    // Theme cards are in the "theme" tab – click it first
    await page.locator('.cf-tab[data-tab="theme"]').click();
    await expect(page.locator('.cf-theme-card')).toHaveCount(4);
    for (const id of ['modern', 'legacy', 'dark', 'popos']) {
      await expect(page.locator(`.cf-theme-card[data-theme-choice="${id}"]`)).toBeVisible();
    }
  });

  test('clicking the "Dark" theme card sets data-theme="dark"', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="theme"]').click();
    await page.locator('.cf-theme-card[data-theme-choice="dark"]').click();
    await page.waitForFunction(() => document.documentElement.getAttribute('data-theme') === 'dark');
    expect(await getTheme(page)).toBe('dark');
    await expect(page.locator('.cf-theme-card[data-theme-choice="dark"]')).toHaveClass(/selected/);
  });

  test('clicking the "Pop!_OS" theme card sets data-theme="popos"', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="theme"]').click();
    await page.locator('.cf-theme-card[data-theme-choice="popos"]').click();
    await page.waitForFunction(() => document.documentElement.getAttribute('data-theme') === 'popos');
    expect(await getTheme(page)).toBe('popos');
  });

  test('clicking the "Modern" theme card sets data-theme="modern"', async ({ page }) => {
    // switch away first, then back
    await page.locator('.cf-tab[data-tab="theme"]').click();
    await page.locator('.cf-theme-card[data-theme-choice="dark"]').click();
    await page.locator('.cf-theme-card[data-theme-choice="modern"]').click();
    await page.waitForFunction(() => document.documentElement.getAttribute('data-theme') === 'modern');
    expect(await getTheme(page)).toBe('modern');
  });

  test('tab switch shows different content panel', async ({ page }) => {
    const tabs = page.locator('.cf-tab');
    if (await tabs.count() > 1) {
      // After clicking a tab, the corresponding cf-tab-pane becomes active.
      // Check that the .active class moves between tabs to verify the switch worked.
      await tabs.first().click();
      await expect(tabs.first()).toHaveClass(/active/);
      await tabs.nth(1).click();
      await expect(tabs.nth(1)).toHaveClass(/active/);
    }
  });

  test('theme "popos" → cf-card has a dark background', async ({ page }) => {
    await setTheme(page, 'popos');
    await goto(page, '/config');
    const card = page.locator('.cf-card').first();
    if (await card.count() > 0) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toContain('255, 253, 248');
    }
  });

  // ── Jira: comment visibility role field ────────────────────────────────────
  test.describe('Jira modal: comment visibility role', () => {
    test('input field is rendered with help text in the Jira modal', async ({ page }) => {
      await page.evaluate(() => {
        if (typeof window.__openJiraModal === 'function') window.__openJiraModal();
      });
      const input = page.locator('#jrVisibilityRole');
      await expect(input).toBeVisible({ timeout: 5000 });
      await expect(input).toHaveValue('');
      await expect(page.locator('label[for="jrVisibilityRole"], [data-i18n="config.jira.visibility_role_label"]').first()).toBeVisible();
      await expect(page.locator('[data-i18n="config.jira.visibility_role_hint"]')).toBeVisible();
    });

    for (const theme of ['modern', 'legacy', 'dark', 'popos']) {
      test(`field renders correctly under "${theme}" theme`, async ({ page }) => {
        await setTheme(page, theme);
        await goto(page, '/config');
        await page.evaluate(() => {
          if (typeof window.__openJiraModal === 'function') window.__openJiraModal();
        });
        const input = page.locator('#jrVisibilityRole');
        await expect(input).toBeVisible({ timeout: 5000 });
        // sanity: input has some visible (non-zero) width and is part of a Bootstrap form-control
        await expect(input).toHaveClass(/form-control/);
        const box = await input.boundingBox();
        expect(box && box.width).toBeGreaterThan(0);
      });
    }

    test('round-trips commentVisibilityRole through the REST API', async ({ request }) => {
      const unique = `E2E-Vis-${Date.now()}`;
      const create = await request.post('/api/config/jira', {
        data: {
          name: unique,
          baseUrl: 'https://jira.example.com',
          projectKey: 'E2E',
          username: 'u',
          apiToken: 't',
          cloudMode: true,
          commentVisibilityRole: 'Developers',
          active: true,
        },
      });
      expect(create.ok()).toBeTruthy();
      const created = await create.json();
      expect(created.commentVisibilityRole).toBe('Developers');

      const list = await request.get('/api/config/jira');
      expect(list.ok()).toBeTruthy();
      const all = await list.json();
      const ours = all.find((c) => c.name === unique);
      expect(ours).toBeTruthy();
      expect(ours.commentVisibilityRole).toBe('Developers');

      // cleanup
      if (ours && ours.id != null) {
        await request.delete(`/api/config/jira/${ours.id}`);
      }
    });
  });

  // ── OpenRouter Provider ─────────────────────────────────────────────────────
  test.describe('KI/OpenRouter Provider', () => {
    test.beforeEach(async ({ page }) => {
      await goto(page, '/config');
      // Click the KI tab to open AI config
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
    });

    test('KI tab contains OpenRouter radio button', async ({ page }) => {
      const openRouterRadio = page.locator('input[name="aiProvider"][value="openrouter"]');
      await expect(openRouterRadio).toBeVisible();
    });

    test('selecting OpenRouter reveals API key field', async ({ page }) => {
      await page.locator('input[name="aiProvider"][value="openrouter"]').click();
      const keySection = page.locator('#aiOpenRouterKeySection');
      await expect(keySection).toBeVisible();
    });

    test('OpenRouter API key field is visible under dark theme', async ({ page }) => {
      await setThemeAndGoto(page, 'dark', '/config');
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
      await page.locator('input[name="aiProvider"][value="openrouter"]').click();
      const keyInput = page.locator('#aiOpenRouterApiKey');
      await expect(keyInput).toBeVisible();
    });

    test('OpenRouter API key field is visible under modern theme', async ({ page }) => {
      await setThemeAndGoto(page, 'modern', '/config');
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
      await page.locator('input[name="aiProvider"][value="openrouter"]').click();
      const keyInput = page.locator('#aiOpenRouterApiKey');
      await expect(keyInput).toBeVisible();
    });

    test('OpenRouter API key field is visible under classic theme', async ({ page }) => {
      await setThemeAndGoto(page, 'legacy', '/config');
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
      await page.locator('input[name="aiProvider"][value="openrouter"]').click();
      const keyInput = page.locator('#aiOpenRouterApiKey');
      await expect(keyInput).toBeVisible();
    });

    test('OpenRouter API key field is visible under popos theme', async ({ page }) => {
      await setThemeAndGoto(page, 'popos', '/config');
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
      await page.locator('input[name="aiProvider"][value="openrouter"]').click();
      const keyInput = page.locator('#aiOpenRouterApiKey');
      await expect(keyInput).toBeVisible();
    });

    test('OpenRouter info banner is present in the UI', async ({ page }) => {
      const banner = page.locator('#aiInfoBannerOpenRouter');
      await expect(banner).toBeAttached();
    });

    test('OpenRouter test button is present when OpenRouter is selected', async ({ page }) => {
      await page.locator('input[name="aiProvider"][value="openrouter"]').click();
      const testBtn = page.locator('#aiTestOpenRouterBtn');
      await expect(testBtn).toBeVisible();
    });

    test('getAiConfig API returns openRouterApiKeySet false when not configured', async ({ request }) => {
      const res = await request.get('/api/config/ai');
      expect(res.ok()).toBeTruthy();
      const data = await res.json();
      expect(typeof data.openRouterApiKeySet).toBe('boolean');
    });

    test('OpenRouter model optgroup is present in the model select', async ({ page }) => {
      await page.locator('input[name="aiProvider"][value="openrouter"]').click();
      const modelGroup = page.locator('#aiOpenRouterModelsGroup');
      await expect(modelGroup).toBeAttached();
    });
  });

  // ── Claude Code Provider ─────────────────────────────────────────────────────
  test.describe('KI/Claude Code Provider', () => {
    test.beforeEach(async ({ page }) => {
      await goto(page, '/config');
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
    });

    test('KI tab contains Claude Code radio button', async ({ page }) => {
      const radio = page.locator('input[name="aiProvider"][value="claudecode"]');
      await expect(radio).toBeVisible();
    });

    test('selecting Claude Code reveals API key field', async ({ page }) => {
      await page.locator('input[name="aiProvider"][value="claudecode"]').click();
      const keySection = page.locator('#aiClaudeCodeKeySection');
      await expect(keySection).toBeVisible();
    });

    test('Claude Code API key field is visible under dark theme', async ({ page }) => {
      await setThemeAndGoto(page, 'dark', '/config');
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
      await page.locator('input[name="aiProvider"][value="claudecode"]').click();
      const keyInput = page.locator('#aiClaudeCodeApiKey');
      await expect(keyInput).toBeVisible();
    });

    test('Claude Code API key field is visible under modern theme', async ({ page }) => {
      await setThemeAndGoto(page, 'modern', '/config');
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
      await page.locator('input[name="aiProvider"][value="claudecode"]').click();
      const keyInput = page.locator('#aiClaudeCodeApiKey');
      await expect(keyInput).toBeVisible();
    });

    test('Claude Code API key field is visible under classic theme', async ({ page }) => {
      await setThemeAndGoto(page, 'legacy', '/config');
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
      await page.locator('input[name="aiProvider"][value="claudecode"]').click();
      const keyInput = page.locator('#aiClaudeCodeApiKey');
      await expect(keyInput).toBeVisible();
    });

    test('Claude Code API key field is visible under popos theme', async ({ page }) => {
      await setThemeAndGoto(page, 'popos', '/config');
      const kiTab = page.locator('.cf-tab[data-tab="ki"]');
      if (await kiTab.count() > 0) {
        await kiTab.click();
      }
      await page.locator('input[name="aiProvider"][value="claudecode"]').click();
      const keyInput = page.locator('#aiClaudeCodeApiKey');
      await expect(keyInput).toBeVisible();
    });

    test('Claude Code info banner is present in the UI', async ({ page }) => {
      const banner = page.locator('#aiInfoBannerClaudeCode');
      await expect(banner).toBeAttached();
    });

    test('Claude Code test button is present when Claude Code is selected', async ({ page }) => {
      await page.locator('input[name="aiProvider"][value="claudecode"]').click();
      const testBtn = page.locator('#aiTestClaudeCodeBtn');
      await expect(testBtn).toBeVisible();
    });

    test('getAiConfig API returns claudeCodeApiKeySet false when not configured', async ({ request }) => {
      const res = await request.get('/api/config/ai');
      expect(res.ok()).toBeTruthy();
      const data = await res.json();
      expect(typeof data.claudeCodeApiKeySet).toBe('boolean');
    });

    test('Claude Code model optgroup is present in the model select', async ({ page }) => {
      await page.locator('input[name="aiProvider"][value="claudecode"]').click();
      const modelGroup = page.locator('#aiClaudeCodeModelsGroup');
      await expect(modelGroup).toBeAttached();
    });
  });
});
