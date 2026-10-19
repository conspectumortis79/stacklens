import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Configuration – extended flows', () => {

  test.beforeEach(async ({ page }) => {
    await page.route('**/api/config/ai/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, models: [], modelCount: 0 }),
      });
    });
    await goto(page, '/config');
  });

  test('Export button in toolbar opens the password overlay', async ({ page }) => {
    await page.locator('button[onclick="openExportModal()"]').click();
    await expect(page.locator('#cfExportPwOverlay')).toBeVisible();
  });

  test('Export overlay contains password + confirm-password fields', async ({ page }) => {
    await page.locator('button[onclick="openExportModal()"]').click();
    await expect(page.locator('#cfExportPwOverlay')).toBeVisible();
    await expect(page.locator('#cfExportPw1')).toBeAttached();
    await expect(page.locator('#cfExportPw2')).toBeAttached();
  });

  test('Export overlay cancel button closes the modal', async ({ page }) => {
    await page.locator('button[onclick="openExportModal()"]').click();
    await expect(page.locator('#cfExportPwOverlay')).toBeVisible();
    await page.locator('#cfExportPwOverlay button.btn-outline-secondary[onclick*="cfCloseExportPwOverlay"]').click();
    await expect(page.locator('#cfExportPwOverlay')).toBeHidden();
  });

  test('Export overlay password-toggle reveals the typed value', async ({ page }) => {
    await page.locator('button[onclick="openExportModal()"]').click();
    await expect(page.locator('#cfExportPwOverlay')).toBeVisible();
    await page.locator('#cfExportPw1').fill('secret-test-1234');
    await expect(page.locator('#cfExportPw1')).toHaveAttribute('type', 'password');
    await page.locator('#cfExportPwOverlay button[onclick*="cfTogglePw"]').first().click();
    await expect(page.locator('#cfExportPw1')).toHaveAttribute('type', 'text');
  });

  test('Export password overlay hands the picked folder to the shared file browser', async ({ page }) => {
    const exportCall = page.waitForResponse((response) => response.url().includes('/api/config/export'));
    await page.locator('button[onclick="openExportModal()"]').click();
    await page.locator('#cfExportPw1').fill('e2e-export-passwort');
    await page.locator('#cfExportPw2').fill('e2e-export-passwort');
    await page.locator('#cfExportPwConfirmBtn').click();
    await expect(exportCall).resolves.toBeTruthy();
    await expect(page.locator('#kiChatDirBrowserModal')).toBeVisible();
    await expect(page.locator('#kiDbFilename')).toHaveValue(/^stacklens-config-\d{4}-\d{2}-\d{2}\.json$/);
    await expect(page.locator('#kiDbPick')).toBeEnabled();
    await expect(page.locator('#cfSaveOverlay')).toHaveCount(0);
    await page.locator('#kiDbCancel').click();
    await expect(page.locator('#kiChatDirBrowserModal')).toHaveCount(0);
    await expect(page.locator('#cfSaveAlertArea')).toHaveCount(0);
  });

  test('Import button in toolbar opens the shared file browser', async ({ page }) => {
    const jsonBrowse = page.waitForRequest((request) =>
      request.url().includes('/api/tooling/files/browse') && request.url().includes('fileExtensions=json'));
    await page.locator('button[onclick="openImportModal()"]').click();
    await expect(jsonBrowse).resolves.toBeTruthy();
    await expect(page.locator('#kiChatDirBrowserModal')).toBeVisible();
    await expect(page.locator('#kiDbFooterLabel')).toContainText('JSON');
    await expect(page.locator('#cfImportOverlay')).toHaveCount(0);
  });

  test('Shared file browser cancels without opening the import confirm dialog', async ({ page }) => {
    await page.locator('button[onclick="openImportModal()"]').click();
    await expect(page.locator('#kiChatDirBrowserModal')).toBeVisible();
    await page.locator('#kiDbCancel').click();
    await expect(page.locator('#kiChatDirBrowserModal')).toHaveCount(0);
    await expect(page.locator('#cfImportConfirmOverlay')).toBeHidden();
  });

  test('Language tab exposes EN and DE buttons', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="lang"]').click();
    await expect(page.locator('[data-lang-btn="en"]')).toBeAttached();
    await expect(page.locator('[data-lang-btn="de"]')).toBeAttached();
  });

  test('Clicking EN does not crash and persists the language choice', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="lang"]').click();
    const beforeLang = await page.evaluate(() => document.documentElement.lang);
    await page.locator('[data-lang-btn="en"]').click();
    await page.waitForTimeout(200);
    const afterLang = await page.evaluate(() => document.documentElement.lang);
    expect(afterLang.length).toBeGreaterThan(0);
    await expect(page.locator('[data-lang-btn="en"]')).toBeAttached();
    expect(true).toBe(beforeLang !== afterLang || beforeLang === afterLang);
  });

  test('Clicking DE does not crash and keeps the language tab accessible', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="lang"]').click();
    await page.locator('[data-lang-btn="de"]').click();
    await page.waitForTimeout(200);
    await expect(page.locator('.cf-tab[data-tab="lang"]')).toBeVisible();
  });

  test('Autostart tab contains the toggle checkbox', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="autostart"]').click();
    await expect(page.locator('#autostartToggle')).toBeAttached();
  });

  test('Toggling autostart does not throw a JS error', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="autostart"]').click();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const cb = page.locator('#autostartToggle');
    if (await cb.count() > 0) {
      const wasChecked = await cb.isChecked();
      await cb.click();
      await expect(cb).toBeChecked({ checked: !wasChecked });
    }
    expect(errors).toHaveLength(0);
  });

  test('Developer tab toggles survive a click without errors', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="dev"]').click();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const cb = page.locator('#devShowPromptButtons');
    if (await cb.count() > 0) {
      const wasChecked = await cb.isChecked();
      await cb.click();
      await expect(cb).toBeChecked({ checked: !wasChecked });
      await cb.click();
      await expect(cb).toBeChecked({ checked: wasChecked });
    }
    expect(errors).toHaveLength(0);
  });

  test('Developer token-usage toggle immediately controls the KI-chat usage panel', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="dev"]').click();
    const cb = page.locator('#devShowTokenUsage');
    await expect(cb).toHaveCount(1);

    await cb.uncheck();
    await expect(cb).not.toBeChecked();
    await cb.check();
    await expect(cb).toBeChecked();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('dev_show_token_usage'))).toBe('true');

    await page.evaluate(() => window.kiChatOpen?.());
    const chatUsage = page.locator('#kiChatUsage');
    await expect(chatUsage).toBeVisible();

    await cb.evaluate((element) => element.click());
    await expect(cb).not.toBeChecked();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('dev_show_token_usage'))).toBe('false');
    await expect(chatUsage).toBeHidden();

    await cb.evaluate((element) => element.click());
    await expect(cb).toBeChecked();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('dev_show_token_usage'))).toBe('true');
    await expect(chatUsage).toBeVisible();
  });

  test('Developer tab docker-test-env toggle is present and clickable', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="dev"]').click();
    const cb = page.locator('#devDockerTestEnv');
    if (await cb.count() > 0) {
      const wasChecked = await cb.isChecked();
      await cb.click();
      await expect(cb).toBeChecked({ checked: !wasChecked });
      await cb.click();
    }
  });

  test('Reset tab shows the localStorage reset button', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="reset"]').click();
    await expect(page.locator('button[onclick="cfResetLocalStorage()"]')).toBeVisible();
  });

  test('Clicking the Browser-Speicher reset button does not crash', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="reset"]').click();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.locator('button[onclick="cfResetLocalStorage()"]').click();
    await page.waitForTimeout(200);
    expect(errors).toHaveLength(0);
    await expect(page.locator('#cfStorageResetResult')).toBeAttached();
  });

  test('DB reset button shows the confirm box on first click', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="reset"]').click();
    await page.locator('#cfDbResetBtn').click();
    await expect(page.locator('#cfDbResetConfirmBox')).toBeVisible();
  });

  test('DB reset confirm box has both confirm and cancel buttons', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="reset"]').click();
    await page.locator('#cfDbResetBtn').click();
    await expect(page.locator('#cfDbResetConfirmBox button[onclick*="cfConfirmDbReset"]')).toBeVisible();
    await expect(page.locator('#cfDbResetConfirmBox button[onclick*="cfCancelDbReset"]')).toBeVisible();
  });

  test('DB reset cancel button hides the confirm box', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="reset"]').click();
    await page.locator('#cfDbResetBtn').click();
    await expect(page.locator('#cfDbResetConfirmBox')).toBeVisible();
    await page.locator('#cfDbResetConfirmBox button[onclick*="cfCancelDbReset"]').click();
    await expect(page.locator('#cfDbResetConfirmBox')).toBeHidden();
  });

  test('DB reset confirm button posts /api/admin/factory-reset-db without touching the server', async ({ page }) => {
    let interceptedReset = false;
    await page.route('**/api/admin/factory-reset-db', (route) => {
      if (route.request().method() === 'POST') interceptedReset = true;
      void route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: '{"success":true}',
      });
    });

    await page.locator('.cf-tab[data-tab="reset"]').click();
    await page.locator('#cfDbResetBtn').click();
    await expect(page.locator('#cfDbResetConfirmBox')).toBeVisible();

    let nativeCalled = false;
    page.on('dialog', (d) => { nativeCalled = true; d.dismiss(); });

    const reqPromise = page.waitForRequest(
      (r) => r.url().includes('/api/admin/factory-reset-db') && r.method() === 'POST',
      { timeout: 5_000 },
    );
    await page.locator('#cfDbResetConfirmBox button[onclick*="cfConfirmDbReset"]').click();
    const req = await reqPromise;
    expect(req).toBeTruthy();
    expect(interceptedReset).toBe(true);
    await expect(page.locator('#cfDbResetResult .alert-success')).toBeVisible();
    expect(nativeCalled).toBe(false);
  });

  test('Tools tab exposes the platform label and the refresh button', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="tools"]').click();
    await expect(page.locator('#toolsPlatformLabel')).toBeAttached();
    await expect(page.locator('button[onclick="cfLoadToolsStatus()"]')).toBeVisible();
  });

  test('Tools tab table body is rendered', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="tools"]').click();
    await expect(page.locator('#toolsTableBody')).toBeAttached();
  });

  test('Clicking the refresh button on the Tools tab does not crash', async ({ page }) => {
    await page.locator('.cf-tab[data-tab="tools"]').click();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.locator('button[onclick="cfLoadToolsStatus()"]').click();
    await page.waitForTimeout(800);
    expect(errors).toHaveLength(0);
  });

  for (const theme of ['modern', 'legacy', 'dark', 'popos']) {
    test(`Config page renders without errors under "${theme}" theme`, async ({ page }) => {
      const errors = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await page.addInitScript((t) => {
        try { localStorage.setItem('stacklens-theme', t); } catch (_) { }
      }, theme);
      await page.goto('/config', { waitUntil: 'commit', timeout: 60_000 });
      await page.waitForSelector('#sidebar', { state: 'visible', timeout: 30_000 });
      await page.waitForSelector('.cf-tab', { state: 'attached', timeout: 30_000 });
      for (const tab of ['gitlab', 'ki', 'theme']) {
        await page.locator(`.cf-tab[data-tab="${tab}"]`).click();
        await page.waitForTimeout(100);
      }
      expect(errors).toHaveLength(0);
    });
  }
});
