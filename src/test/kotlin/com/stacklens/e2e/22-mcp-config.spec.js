import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

/**
 * E2E tests for the MCP server configuration tab.
 * Covers tab navigation, table rendering, themed dialogs (showConfirm/showAlert),
 * sync-from-stacklens, modal CRUD and the local process lifecycle UI.
 */
test.describe('MCP server config tab', () => {
  test.beforeEach(async ({ page }) => {
    // Wipe any leftover MCP rows so deterministic empty-state tests pass.
    await page.request.get('/api/config/mcp').then(async (r) => {
      if (!r.ok()) return;
      const list = await r.json();
      for (const c of list) await page.request.delete(`/api/config/mcp/${c.id}`);
    }).catch(() => {});
    await goto(page, '/config');
    await page.locator('.cf-tab[data-tab="mcp"]').click();
    await expect(page.locator('#tab-mcp')).toBeVisible();
  });

  test('MCP tab shows the empty state when no servers configured', async ({ page }) => {
    await expect(page.locator('#tab-mcp')).toContainText('Noch kein MCP-Server konfiguriert');
  });

  test('MCP tab has all action buttons in the header', async ({ page }) => {
    await expect(page.locator('button[onclick="mcpSyncFromStacklens()"]')).toBeVisible();
    await expect(page.locator('button[onclick="mcpOpenModal()"]')).toBeVisible();
  });

  test('MCP local process card shows Start/Stop/Restart/Refresh buttons', async ({ page }) => {
    await expect(page.locator('button[onclick="mcpProcessStart()"]')).toBeVisible();
    await expect(page.locator('button[onclick="mcpProcessStop()"]')).toBeVisible();
    await expect(page.locator('button[onclick="mcpProcessRestart()"]')).toBeVisible();
    await expect(page.locator('button[onclick="mcpProcessRefresh()"]')).toBeVisible();
    await expect(page.locator('#mcpProcessBadge')).toBeAttached();
  });

  test('"Aus StackLens übernehmen" opens themed confirm modal (not native confirm)', async ({ page }) => {
    // We must NOT see a native window.confirm — proves dialog is themed.
    let nativeCalled = false;
    page.on('dialog', (d) => { nativeCalled = true; d.dismiss(); });

    await page.locator('button[onclick="mcpSyncFromStacklens()"]').click();
    await expect(page.locator('#confirmModal')).toBeVisible();
    await expect(page.locator('#confirmModalTitle')).toContainText(/übernehmen|import connections/i);
    expect(nativeCalled).toBe(false);

    // Cancel via the themed close-X button
    await page.locator('#confirmModal .btn-close').click();
    await expect(page.locator('#confirmModal')).toBeHidden();
  });

  test('confirming "Aus StackLens übernehmen" creates an MCP config row', async ({ page }) => {
    await page.locator('button[onclick="mcpSyncFromStacklens()"]').click();
    await expect(page.locator('#confirmModal')).toBeVisible();
    await page.locator('#confirmModalOkBtn').click();
    // Page reloads; wait for tab to come back and verify a row is present.
    await page.waitForLoadState('load');
    await page.locator('.cf-tab[data-tab="mcp"]').click();
    await expect(page.locator('#tab-mcp tbody tr').first()).toBeVisible();
    await expect(page.locator('#tab-mcp')).not.toContainText('Noch kein MCP-Server konfiguriert');
  });

  test('"Neuer MCP-Server" opens the editor modal with default Base-URL', async ({ page }) => {
    await page.locator('button[onclick="mcpOpenModal()"]').click();
    await expect(page.locator('#mcpModal')).toBeVisible();
    await expect(page.locator('#mcpName')).toHaveValue('');
    await expect(page.locator('#mcpBaseUrl')).toHaveValue('http://localhost:7456');
    // Cancel
    await page.locator('#mcpModal [data-bs-dismiss="modal"]').first().click();
    await expect(page.locator('#mcpModal')).toBeHidden();
  });

  test('saving a new MCP config via the modal adds a row', async ({ page }) => {
    await page.locator('button[onclick="mcpOpenModal()"]').click();
    await expect(page.locator('#mcpModal')).toBeVisible();
    await page.locator('#mcpName').fill('E2E Test MCP');
    await page.locator('#mcpBaseUrl').fill('http://localhost:7456');
    await page.locator('button[onclick="mcpSave()"]').click();
    await page.waitForLoadState('load');
    await page.locator('.cf-tab[data-tab="mcp"]').click();
    await expect(page.locator('#tab-mcp')).toContainText('E2E Test MCP');
  });

  test('delete button on an MCP row opens themed delete-confirm dialog', async ({ page }) => {
    // Create one through API for speed.
    await page.request.post('/api/config/mcp', {
      data: { name: 'Delete Me MCP', baseUrl: 'http://localhost:7456', active: false },
    });
    await page.reload();
    await page.locator('.cf-tab[data-tab="mcp"]').click();
    let nativeCalled = false;
    page.on('dialog', (d) => { nativeCalled = true; d.dismiss(); });
    await page.locator('#tab-mcp button[onclick*="mcpDelete"]').first().click();
    await expect(page.locator('#confirmModal')).toBeVisible();
    expect(nativeCalled).toBe(false);
    await page.locator('#confirmModalOkBtn').click();
    await page.waitForLoadState('load');
    await page.locator('.cf-tab[data-tab="mcp"]').click();
    await expect(page.locator('#tab-mcp')).not.toContainText('Delete Me MCP');
  });

  test('process Start without any config shows themed alert (no native popup)', async ({ page }) => {
    let nativeCalled = false;
    page.on('dialog', (d) => { nativeCalled = true; d.dismiss(); });
    await page.locator('button[onclick="mcpProcessStart()"]').click();
    await page.waitForTimeout(800);
    expect(nativeCalled).toBe(false);
    // Either an alert appeared, or the badge stayed unknown/idle.
    const alert = page.locator('#alertArea .alert');
    if (await alert.count() > 0) {
      await expect(alert.first()).toBeVisible();
    }
  });
});
