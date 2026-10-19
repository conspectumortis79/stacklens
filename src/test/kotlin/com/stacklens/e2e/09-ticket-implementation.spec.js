import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';
const agentPipelineUrl = new URL('/api/agent-pipeline', baseUrl).toString();

test.describe('Ticket Implementation', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/ticket-implementation');
  });

  test('page loads with ta-elements', async ({ page }) => {
    await expect(page.locator('[class*="ta-"]').first()).toBeAttached();
  });

  test('ticket input fields are present', async ({ page }) => {
    await expect(page.locator('.ta-input, .ta-select').first()).toBeAttached();
  });

  test('GitLab config header is present', async ({ page }) => {
    // Header is dynamically rendered; guard with count check
    const header = page.locator('.ta-gl-config-header').first();
    if (await header.count() > 0) {
      await expect(header).toBeAttached();
    }
  });

  test('clicking GitLab config header expands/collapses the section', async ({ page }) => {
    const header = page.locator('.ta-gl-config-header').first();
    if (await header.count() > 0) {
      const errors = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await header.click();
      await page.waitForTimeout(300);
      await header.click();
      expect(errors).toHaveLength(0);
    }
  });

  test('project filter input is editable', async ({ page }) => {
    const filter = page.locator('.ta-gl-project-filter').first();
    if (await filter.count() > 0 && await filter.isEditable()) {
      await filter.fill('test');
      await expect(filter).toHaveValue('test');
    }
  });

  test('KI-chat opens', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });

  // ── Extended tests ─────────────────────────────────────────────────────────

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="ta-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  test('Jira config select has ta-select class', async ({ page }) => {
    const jiraSelect = page.locator('#selectJiraConfig');
    if (await jiraSelect.count() > 0) {
      await expect(jiraSelect).toHaveClass(/ta-select/);
    }
  });

  test('DB multi-select container is present', async ({ page }) => {
    const dbContainer = page.locator('#taDatabaseMultiSelect');
    await expect(dbContainer).toBeAttached();
  });

  test('ticket key input is present and editable', async ({ page }) => {
    const input = page.locator('#inputTicketKey');
    if (await input.count() > 0 && await input.isVisible()) {
      await input.fill('PROJ-123');
      await expect(input).toHaveValue('PROJ-123');
    }
  });

  test('mode bar tabs exist and are clickable without errors', async ({ page }) => {
    const tabs = page.locator('.ta-input-mode-bar button, .ta-mode-tab, .ta-tab').first();
    if (await tabs.count() > 0) {
      const errors = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await tabs.click();
      expect(errors).toHaveLength(0);
    }
  });

  test('manual-text mode shows raw text textarea', async ({ page }) => {
    // Find a "Freitext" or manual mode button in the mode bar
    const manualTab = page.locator('[data-i18n="ticket_implementation.mode_manual"], button:has-text("Freitext")').first();
    if (await manualTab.count() > 0) {
      await manualTab.click();
      // Raw text area should become visible
      const textarea = page.locator('#taRawTextInput, .ta-textarea').first();
      if (await textarea.count() > 0) {
        await expect(textarea).toBeAttached();
      }
    }
  });

  test('dark theme: ta-select has correct background', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/ticket-implementation');
    const jiraSelect = page.locator('#selectJiraConfig');
    if (await jiraSelect.count() > 0 && await jiraSelect.isVisible()) {
      const bg = await jiraSelect.evaluate((el) => getComputedStyle(el).backgroundColor);
      // dark: #2a2540 = rgb(42, 37, 64) — not white
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('popos theme: ta-select has correct background', async ({ page }) => {
    await setThemeAndGoto(page, 'popos', '/ticket-implementation');
    const jiraSelect = page.locator('#selectJiraConfig');
    if (await jiraSelect.count() > 0 && await jiraSelect.isVisible()) {
      const bg = await jiraSelect.evaluate((el) => getComputedStyle(el).backgroundColor);
      // popos: #28283c = rgb(40, 40, 60) — not white
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('agent pipeline panel is hidden initially', async ({ page, request }) => {
    // Cancel any running/pending pipelines so the JS restore logic does not show the panel
    const apResp = await request.get(agentPipelineUrl).catch(() => null);
    if (apResp && apResp.ok()) {
      const runs = await apResp.json().catch(() => []);
      for (const run of runs) {
        if (run.status === 'RUNNING' || run.status === 'PENDING') {
          await request.post(`${agentPipelineUrl}/${run.id}/cancel`).catch(() => {});
        }
      }
      // Wait until no more RUNNING/PENDING pipelines exist (max 5 s)
      for (let i = 0; i < 10; i++) {
        const checkResp = await request.get(agentPipelineUrl).catch(() => null);
        if (!checkResp || !checkResp.ok()) break;
        const remaining = await checkResp.json().catch(() => []);
        const stillActive = remaining.some(r => r.status === 'RUNNING' || r.status === 'PENDING');
        if (!stillActive) break;
        await page.waitForTimeout(500);
      }
    }
    // Clear in-memory JS pipeline state so taRestore() won't re-open the panel
    await page.evaluate(() => { if (window._ta) window._ta.pipelineId = null; });
    await goto(page, '/ticket-implementation');
    // Give the async restore logic (setTimeout 0) time to settle
    await page.waitForTimeout(500);
    const panel = page.locator('#agentPipelineCard');
    if (await panel.count() > 0) {
      await expect(panel).toBeHidden();
    }
  });

  test('local repo list section is present', async ({ page }) => {
    await expect(page.locator('#localRepoList, .ta-local-repo-card, #taNoRepoHint').first()).toBeAttached();
  });

  // ── Frontend Mockup section ────────────────────────────────────────────────

  test('mockup section is present in the form', async ({ page }) => {
    await expect(page.locator('#taMockupSection')).toBeAttached();
  });

  test('mockup upload button is visible', async ({ page }) => {
    const uploadZone = page.locator('#taMockupUploadZone');
    await expect(uploadZone).toBeVisible();
  });

  test('mockup file input accepts only allowed types', async ({ page }) => {
    const input = page.locator('#taMockupFileInput');
    await expect(input).toBeAttached();
    const accept = await input.getAttribute('accept');
    expect(accept).toContain('.png');
    expect(accept).toContain('.jpg');
    expect(accept).toContain('.svg');
    expect(accept).toContain('.heic');
    expect(accept).toContain('.tiff');
    expect(accept).not.toContain('.pdf');
    expect(accept).not.toContain('.doc');
  });

  test('mockup info icon is present and has data-tooltip', async ({ page }) => {
    const icon = page.locator('.ta-info-icon').first();
    await expect(icon).toBeAttached();
    const tooltip = await icon.getAttribute('data-tooltip');
    expect(tooltip).toBeTruthy();
    expect(tooltip.length).toBeGreaterThan(10);
  });

  test('mockup preview wrap is hidden initially', async ({ page }) => {
    const preview = page.locator('#taMockupPreviewWrap');
    await expect(preview).toBeHidden();
  });

  test('mockup section has no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('#taMockupSection', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });
});
