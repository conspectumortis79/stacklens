
import path from 'node:path';
import { test, expect, request } from '@playwright/test';
import { goto } from './helpers.js';

/**
 * Creating a knowledge source blocks the HTTP request until every chunk
 * is embedded (KnowledgeDocumentIngester → KnowledgeEmbeddingDispatcher
 * → Ollama `/api/embed`, which the server waits on for up to 60 s).
 * On a dev machine that embedding call queues behind whatever else the
 * local model host is busy with (the chat/analysis specs hit the same
 * Ollama), so the suite-wide `actionTimeout: 10_000` is far too tight:
 * the client gave up with a TimeoutError while the server was still
 * working and eventually created the source. The source-creating
 * requests below therefore carry an explicit, generous timeout.
 */
const EMBED_REQUEST_TIMEOUT = 150_000;

test.describe('Knowledge database (RAG)', () => {
  test.beforeEach(async ({ page, context }) => {

    await context.request
      .put('http://127.0.0.1:8091/api/config/language', { data: { language: 'de' } })
      .catch(() => {  });

    await context.clearCookies();
    await page.addInitScript(() => { try { localStorage.clear(); sessionStorage.clear(); } catch (_) {} });

    const consoleErrors = [];
    page.on('pageerror', (e) => consoleErrors.push(`pageerror: ${e.message}`));
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(`console: ${msg.text()}`);
    });
    page.__consoleErrors = consoleErrors;
  });

  test('page loads and Inhalte tab renders without ReferenceError', async ({ page }) => {
    await goto(page, '/knowledge');

    await expect(page.locator('[data-i18n="page_titles.knowledge"]').first()).toBeVisible({ timeout: 10_000 });

    await page.click('button:has-text("Inhalte")');

    await page.waitForSelector('#kbSourceRows tr', { timeout: 10_000 });

    const count = await page.locator('#kbSourceCount').textContent();
    expect(count).toMatch(/^\d+$/);

    const errs = page.__consoleErrors;

    const pageErrors = errs.filter((e) => e.startsWith('pageerror'));
    expect(pageErrors, `unexpected page errors: ${pageErrors.join(' | ')}`).toEqual([]);
  });

  test('config endpoint returns valid JSON', async ({ playwright }) => {
    const ctx = await playwright.request.newContext({
      baseURL: process.env.BASE_URL || 'http://127.0.0.1:8091',
    });
    const resp = await ctx.get('/api/knowledge/config');
    expect(resp.status()).toBe(200);
    const body = await resp.json();
    expect(body).toHaveProperty('provider');
    expect(body).toHaveProperty('model');
    expect(['OPENAI', 'OPENROUTER', 'OLLAMA']).toContain(body.provider);
  });

  test('create manual source, then see it in Inhalte tab', async ({ page, playwright }) => {
    test.setTimeout(240_000);
    const ctx = await playwright.request.newContext({
      baseURL: process.env.BASE_URL || 'http://127.0.0.1:8091',
    });

    let bases = await ctx.get('/api/knowledge/bases').then((r) => r.json());
    if (!Array.isArray(bases) || bases.length === 0) {
      const seed = await ctx.post('/api/knowledge/bases', {
        data: { name: `e2e-create-${Date.now()}`, description: 'auto-created', includeInGlobalChat: true },
      });
      expect(seed.status(), 'createBase for create-manual-source test').toBe(200);
      bases = await ctx.get('/api/knowledge/bases').then((r) => r.json());
    }
    const knowledgeBaseId = bases[0].id;
    const name = `e2e-test-${Date.now()}`;
    const resp = await ctx.post('/api/knowledge/sources/manual', {
      timeout: EMBED_REQUEST_TIMEOUT,
      data: {
        knowledgeBaseId,
        name,
        content: 'E2E-Integration-Test: Diese Quelle wird durch den Test angelegt.',
      },
    });
    expect(resp.status()).toBe(200);

    await goto(page, '/knowledge');

    const pageTitle = await page.locator('[data-i18n="page_titles.knowledge"]').first().textContent();
    expect(['Wissensdatenbank', 'Knowledge Database']).toContain(pageTitle.trim());
    await page.click('button:has-text("Inhalte")');

    await page.waitForSelector(`#kbSourceRows td.kb-source-name:has-text("${name}")`, { timeout: 10_000 });
    await expect(page.locator(`#kbSourceRows td.kb-source-name:has-text("${name}")`)).toBeVisible();
  });

  test('page renders without CSS fade animations on the knowledge database', async ({ page }) => {

    await goto(page, '/knowledge');

    const animated = await page.evaluate(() => {

      const root = document.querySelector('.kb-page');
      if (!root) return [{ error: 'kb-page not found' }];
      const all = Array.from(root.querySelectorAll('*'));
      return all
        .map((el) => {
          const cs = getComputedStyle(el);
          return { sel: el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (el.className && typeof el.className === 'string' ? '.' + el.className.split(/\s+/).join('.') : ''), animationName: cs.animationName, animationDuration: cs.animationDuration };
        })
        .filter((s) => s.animationName && s.animationName !== 'none' && s.animationDuration !== '0s')
        .slice(0, 10);
    });
    expect(animated, JSON.stringify(animated, null, 2)).toEqual([]);
  });

  test('export-all returns 200 and a non-empty archive', async ({ playwright }) => {

    const ctx = await playwright.request.newContext({
      baseURL: process.env.BASE_URL || 'http://127.0.0.1:8091',
    });

    let bases = await ctx.get('/api/knowledge/bases').then((r) => r.json());
    if (!Array.isArray(bases) || bases.length === 0) {
      const create = await ctx.post('/api/knowledge/bases', {
        data: {
          name: `e2e-export-base-${Date.now()}`,
          description: 'auto-created by export-all test',
          includeInGlobalChat: true,
        },
      });
      expect(create.status(), 'createBase for export-all test').toBe(200);
      bases = await ctx.get('/api/knowledge/bases').then((r) => r.json());
    }
    expect(bases.length, 'at least one knowledge base must exist for export-all').toBeGreaterThan(0);

    const resp = await ctx.post('/api/knowledge/export-all', {
      data: { password: 'e2e-export-password' },
    });
    expect(resp.status()).toBe(200);
    const body = await resp.body();
    expect(body.length).toBeGreaterThan(200);

    expect(body.subarray(0, 4).toString('utf8')).toBe('PK\u0003\u0004');

    expect(body.toString('utf8')).toContain('MANIFEST.json');
  });

  test('trash icon deletes a source without a confirm dialog', async ({ page, playwright }) => {
    test.setTimeout(240_000);

    const ctx = await playwright.request.newContext({
      baseURL: process.env.BASE_URL || 'http://127.0.0.1:8091',
    });

    let bases = await ctx.get('/api/knowledge/bases').then((r) => r.json());
    if (!Array.isArray(bases) || bases.length === 0) {
      const seed = await ctx.post('/api/knowledge/bases', {
        data: { name: `e2e-trash-base-${Date.now()}`, description: 'auto-created', includeInGlobalChat: true },
      });
      expect(seed.status(), 'createBase for trash test').toBe(200);
      bases = await ctx.get('/api/knowledge/bases').then((r) => r.json());
    }
    const knowledgeBaseId = bases[0].id;
    const stamp = Date.now();
    const name = `e2e-trash-${stamp}`;
    const create = await ctx.post('/api/knowledge/sources/manual', {
      timeout: EMBED_REQUEST_TIMEOUT,
      data: { knowledgeBaseId, name, content: 'source to be deleted' },
    });
    expect(create.status()).toBe(200);

    await goto(page, '/knowledge');
    await page.click('button:has-text("Inhalte")');

    const row = page.locator(`#kbSourceRows td.kb-source-name:has-text("${name}")`);
    await expect(row).toBeVisible({ timeout: 10_000 });

    let dialogShown = false;
    page.on('dialog', async (d) => { dialogShown = true; await d.accept(); });

    await row.locator('xpath=ancestor::tr').locator('button.btn-outline-danger').click();

    await expect(row).toHaveCount(0, { timeout: 5_000 });
    expect(dialogShown, 'expected direct delete without a confirm dialog').toBe(false);

    const list = await ctx.get('/api/knowledge/sources').then((r) => r.json());
    expect(list.find((s) => s.name === name)).toBeUndefined();
  });

  test('Alle exportieren opens a themed password modal (not the native prompt)', async ({ page }) => {

    await goto(page, '/knowledge');

    let nativePromptShown = false;
    page.on('dialog', async (d) => {
      nativePromptShown = true;
      await d.dismiss();
    });

    await page.click('button:has-text("Alle exportieren")');
    const modal = page.locator('#kbExportPasswordModal');
    await expect(modal).toBeVisible({ timeout: 5_000 });

    expect(nativePromptShown, 'expected themed modal, not OS prompt').toBe(false);

    const input = page.locator('#kbExportPasswordInput');
    await expect(input).toBeVisible();

    await input.fill('e2e-themed-password');
    await page.click('#kbExportPasswordConfirm');

    await expect(modal).toBeHidden({ timeout: 10_000 });
  });

  test('Jira source validation returns 400 with a German error message instead of 500', async ({ page, playwright }) => {

    await goto(page, '/knowledge');
    await page.click('button:has-text("Inhalte")');
    await page.waitForSelector('#kbSourceRows', { state: 'visible', timeout: 10_000 });
    const ctx = await playwright.request.newContext({
      baseURL: process.env.BASE_URL || 'http://127.0.0.1:8091',
    });
    for (const [body, label] of [
      [{ knowledgeBaseId: 1, jiraConfigId: 1, rootTicketKey: '', includeChildren: true, includeComments: true, maxDepth: 10 }, 'empty'],
      [{ knowledgeBaseId: 1, jiraConfigId: 1, rootTicketKey: '   ', includeChildren: true, includeComments: true, maxDepth: 10 }, 'whitespace'],
      [{ knowledgeBaseId: 1, jiraConfigId: 1, rootTicketKey: 'invalid', includeChildren: true, includeComments: true, maxDepth: 10 }, 'no-pattern'],
    ]) {
      const r = await ctx.post('/api/knowledge/sources/jira', { data: body });
      expect(r.status(), label + ' status').toBe(400);
      const json = await r.json();
      expect(json, label + ' shape').toHaveProperty('error');
      expect(typeof json.error, label + ' error type').toBe('string');
      expect(json.error.length, label + ' error non-empty').toBeGreaterThan(0);
    }
    await ctx.dispose();
  });

  test('Datei button opens the shared file browser before the form modal', async ({ page }) => {
    await goto(page, '/knowledge');
    await page.click('button:has-text("Inhalte")');
    await page.waitForSelector('#kbSourceRows', { state: 'visible' });
    await page.click('button[onclick="kbOpenAddSource(\'file\')"]:has-text("Datei")');
    const browser = page.locator('#kiChatDirBrowserModal');
    await expect(browser).toBeVisible({ timeout: 5_000 });
    await expect(page.locator('#kbAddFileModal')).not.toHaveClass(/show/);
    await page.locator('#kiDbCancel').click();
    await expect(browser).toHaveCount(0);
    await expect(page.locator('#kbAddFileModal')).not.toHaveClass(/show/);
  });

  test('Datei button opens the shared themed browser and never the native OS picker', async ({ page }) => {
    await goto(page, '/knowledge');
    await page.click('button:has-text("Inhalte")');
    await page.waitForSelector('#kbSourceRows', { state: 'visible' });
    let nativeFileChooserOpened = false;
    page.on('filechooser', () => { nativeFileChooserOpened = true; });
    const browse = page.waitForRequest((request) => {
      const url = new URL(request.url());
      return url.pathname === '/api/tooling/files/browse'
        && url.searchParams.get('fileExtensions') === 'pdf,doc,docx,txt,md,markdown';
    });
    await page.click('button[onclick="kbOpenAddSource(\'file\')"]:has-text("Datei")');
    await expect(browse).resolves.toBeTruthy();
    await expect(page.locator('#kiChatDirBrowserModal')).toBeVisible({ timeout: 5_000 });
    expect(nativeFileChooserOpened, 'expected the shared browser, not the OS file picker').toBe(false);
    await expect(page.locator('#kiDbFooterLabel')).toContainText('PDF, DOC, DOCX, TXT, MD');
    await expect(page.locator('#kbFilePickerOverlay')).toHaveCount(0);
  });

  test('picking a document in the shared browser fills the file form', async ({ page }) => {
    const docsDir = path.resolve(process.cwd(), 'src/main/resources/static/docs');
    await goto(page, '/knowledge');
    await page.click('button:has-text("Inhalte")');
    await page.waitForSelector('#kbSourceRows', { state: 'visible' });
    await page.click('button[onclick="kbOpenAddSource(\'file\')"]:has-text("Datei")');
    await expect(page.locator('#kiChatDirBrowserModal')).toBeVisible({ timeout: 5_000 });
    await page.locator('#kiDbPath').fill(docsDir);
    await page.locator('#kiDbGo').click();
    const markdownRow = page.locator('#kiDbList button', { hasText: '.md' }).first();
    await expect(markdownRow).toBeVisible({ timeout: 5_000 });
    await markdownRow.click();
    await expect(page.locator('#kiChatDirBrowserModal')).toHaveCount(0);
    await expect(page.locator('#kbAddFileModal')).toBeVisible();
    await expect(page.locator('#kbFileSelectedName')).not.toBeEmpty();
    const picked = await page.locator('#kbFileCurrentPath').inputValue();
    expect(picked.startsWith(docsDir + path.sep)).toBe(true);
    expect(picked.endsWith('.md')).toBe(true);
  });

  test('the shared browser stays up until the file form is rendered', async ({ page }) => {
    const docsDir = path.resolve(process.cwd(), 'src/main/resources/static/docs');
    await page.route('**/api/knowledge/bases', (route) => setTimeout(() => route.continue(), 400));
    await goto(page, '/knowledge');
    await page.click('button:has-text("Inhalte")');
    await page.waitForSelector('#kbSourceRows', { state: 'visible' });
    await page.click('button[onclick="kbOpenAddSource(\'file\')"]:has-text("Datei")');
    await expect(page.locator('#kiChatDirBrowserModal')).toBeVisible({ timeout: 5_000 });
    await page.locator('#kiDbPath').fill(docsDir);
    await page.locator('#kiDbGo').click();
    const markdownRow = page.locator('#kiDbList button', { hasText: '.md' }).first();
    await expect(markdownRow).toBeVisible({ timeout: 5_000 });
    await page.evaluate(() => window.kbInvalidateBasesCache());
    await page.evaluate(() => {
      window.__layerSamples = [];
      const started = performance.now();
      const tick = () => {
        window.__layerSamples.push({
          t: Math.round(performance.now() - started),
          browser: !!document.getElementById('kiChatDirBrowserModal'),
          form: document.getElementById('kbAddFileModal')?.classList.contains('show') ?? false,
        });
        if (performance.now() - started < 1500) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    });
    await markdownRow.click();
    await page.waitForFunction(() => document.getElementById('kbAddFileModal').classList.contains('show'), null, { timeout: 5_000 });
    await page.waitForTimeout(300);
    const samples = await page.evaluate(() => window.__layerSamples);

    expect(samples.some((s) => s.browser && !s.form), 'the browser must be up before the form').toBe(true);
    const gap = samples.filter((s) => !s.browser && !s.form);
    expect(gap, 'no frame may sit between the browser and the file form: ' + JSON.stringify(gap)).toEqual([]);
  });

  test('Knowledge database reset endpoint wipes user bases and re-seeds the system-managed analysis base', async ({ playwright }) => {

    const ctx = await playwright.request.newContext({
      baseURL: process.env.BASE_URL || 'http://127.0.0.1:8091',
    });

    const create = await ctx.post('/api/knowledge/bases', {
      data: { name: `e2e-reset-base-${Date.now()}`, description: 'for reset test', includeInGlobalChat: true },
    });
    expect(create.status()).toBe(200);
    const sentinelId = (await create.json()).id;
    let before = await ctx.get('/api/knowledge/bases').then((r) => r.json());
    expect(before.length, 'at least the sentinel must exist before reset').toBeGreaterThan(0);
    expect(before.some((b) => b.id === sentinelId), 'sentinel base must be visible before reset').toBe(true);

    const reset = await ctx.post('/api/knowledge/reset');
    expect(reset.status()).toBe(200);
    const resetBody = await reset.json();
    expect(resetBody.ok).toBe(true);

    const after = await ctx.get('/api/knowledge/bases').then((r) => r.json());
    expect(after.some((b) => b.id === sentinelId), 'sentinel base must be gone after reset').toBe(false);
    const systemNames = after.filter((b) => b.systemManaged === true).map((b) => b.name);
    expect(systemNames, 'system "StackLens-Analyseergebnisse" must be re-seeded').toContain('StackLens-Analyseergebnisse');
  });

  test('Reindex-all endpoint is reachable and returns ok', async ({ playwright }) => {

    const ctx = await playwright.request.newContext({
      baseURL: process.env.BASE_URL || 'http://127.0.0.1:8091',
    });
    const resp = await ctx.post('/api/knowledge/reindex-all');
    expect(resp.status()).toBe(200);
    const body = await resp.json();
    expect(body.ok).toBe(true);
    expect(typeof body.baseCount).toBe('number');
    expect(typeof body.totalChunks).toBe('number');
  });

  test('Danger zone tab exposes the reset and reindex buttons', async ({ page }) => {

    await goto(page, '/knowledge');
    const dangerTab = page.locator('button[data-kb-tab="danger"]');
    await expect(dangerTab).toBeVisible();
    await dangerTab.click();

    await expect(page.locator('#kbReindexResult')).toBeAttached();
    await expect(page.locator('#kbResetBtn')).toBeVisible();

    await page.click('#kbResetBtn');
    await expect(page.locator('#kbResetConfirmBox')).toBeVisible();

    await page.click('button[onclick*="kbCancelReset"]');
    await expect(page.locator('#kbResetConfirmBox')).toBeHidden();
  });

  test('Maintenance tab is accessible from the page header', async ({ page }) => {

    await goto(page, '/knowledge');
    const tabs = await page.locator('#kbTabs .nav-link').allTextContents();
    const hasDangerTab = tabs.some((t) => t.toLowerCase().includes('wartung') || t.toLowerCase().includes('danger'));
    expect(hasDangerTab, 'expected a maintenance / danger zone tab in the navigation').toBe(true);
  });

  test('Danger zone tab exposes labelled card-headers for both sub-windows', async ({ page }) => {

    await goto(page, '/knowledge');
    await page.click('button[data-kb-tab="danger"]');
    const dangerPane = page.locator('section[data-kb-pane="danger"]');
    await expect(dangerPane).toBeVisible();

    const reindexHeader = dangerPane.locator(
      '.kb-card-warning .card-header [data-i18n="knowledge.reindex.title"]',
    );
    const resetHeader = dangerPane.locator(
      '.kb-card-danger .card-header [data-i18n="knowledge.danger.title"]',
    );
    await expect(reindexHeader, 'expected a visible label in the reindex card header').toBeVisible();
    await expect(resetHeader, 'expected a visible label in the reset card header').toBeVisible();

    const resetText = (await resetHeader.textContent() || '').toLowerCase();
    expect(
      resetText.includes('zurücksetz') || resetText.includes('reset'),
      'reset card header must mention resetting, got: ' + resetText,
    ).toBe(true);
  });
});
