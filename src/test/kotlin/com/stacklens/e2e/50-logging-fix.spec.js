import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';
import { createLocalRepo, deleteLocalRepoById, deleteLocalReposByScope } from './localRepoRegistry.js';
import { mkdtempSync, realpathSync, rmSync } from 'fs';
import { join } from 'path';
import { tmpdir } from 'os';

const BASE_URL = 'http://127.0.0.1:8091';

let E2E_FIXTURE;

test.describe('Logging-fix pipeline', () => {
  test.beforeEach(async ({ page }) => {
    await deleteLocalReposByScope(BASE_URL, 'LOGGING_ANALYSIS');
    if (E2E_FIXTURE) {
      try { rmSync(E2E_FIXTURE, { recursive: true, force: true }); } catch (_) {}
    }
    E2E_FIXTURE = realpathSync.native(mkdtempSync(join(tmpdir(), 'stacklens-e2e-no-stack-')));
    await page.addInitScript({
      content: [
        `window.__E2E_FIXTURE_PATH = ${JSON.stringify(E2E_FIXTURE)};`,
        `window._lgStack = window._lgStack || { logStacks: [], logIndicators: [], repoStacks: [], repoIndicators: [], repoMatch: 'unknown' };
      window.__forceMatchUnknown = function () {
        if (window._lgStack) {
          window._lgStack.repoMatch = 'unknown';
          lgUpdateFixButtonState();
        }
      };`,
      ].join('\n'),
    });
  });

  test.afterAll(() => {
    if (E2E_FIXTURE) {
      try { rmSync(E2E_FIXTURE, { recursive: true, force: true }); } catch (_) {}
    }
  });

  const SAMPLE_LOG = [
    '2025-01-15 10:00:01 ERROR com.example.app.OrderService - Failed to save order id=99',
    'java.sql.SQLException: Connection refused',
    '    at com.example.app.OrderService.save(OrderService.java:87)',
  ].join('\n');

  test('page exposes the optional repository block', async ({ page }) => {
    await goto(page, '/logging');
    await expect(page.locator('text=Repository (optional)').first()).toBeVisible();
    await expect(page.locator('#lgLocalRepoList')).toBeAttached();
    await expect(page.locator('#lgBtnStartFix')).toBeAttached();
  });

  test('fix button is disabled before any analysis runs', async ({ page }) => {
    await goto(page, '/logging');
    const btn = page.locator('#lgBtnStartFix');
    await expect(btn).toBeAttached();
    await expect(btn).toBeDisabled();
  });

  test('fix toolbar stays hidden without repository selected even when badges are active', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#lgResult')).toBeVisible();
    const toolbar = page.locator('.lg-fix-toolbar');
    await expect(toolbar).toBeHidden();
    const btn = page.locator('#lgBtnStartFix');
    await expect(btn).toBeHidden();
  });

  test('add repository then analyze: fix toolbar appears with count of active badges', async ({ page }) => {
    await goto(page, '/logging');
    await page.evaluate(() => {
      window.__kiChatOpenDirBrowser = (cb) => setTimeout(() => cb(window.__E2E_FIXTURE_PATH), 50);
    });
    await page.locator('button.lg-repo-add-btn').click();
    await page.waitForTimeout(2500);
    await page.locator('#lgLocalRepoList .lg-local-repo-card').last().click();
    await page.waitForTimeout(800);
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#lgResult')).toBeVisible();
    await page.waitForTimeout(500);
    const toolbar = page.locator('.lg-fix-toolbar');
    await expect(toolbar).toBeVisible();
    const btn = page.locator('#lgBtnStartFix');
    await expect(btn).toBeVisible();
    await expect(btn).toBeEnabled();
    const text = await btn.textContent();
    expect(text).toMatch(/\(\d+\)/);
    expect(text).not.toContain('{0}');
  });

  test('selecting then deselecting a repo toggles the toolbar visibility', async ({ page }) => {
    await goto(page, '/logging');
    await page.evaluate(() => {
      window.__kiChatOpenDirBrowser = (cb) => setTimeout(() => cb(window.__E2E_FIXTURE_PATH), 50);
    });
    await page.locator('button.lg-repo-add-btn').click();
    await page.waitForFunction(
      (expectedPath) => {
        const cards = document.querySelectorAll('#lgLocalRepoList .lg-local-repo-card');
        return Array.from(cards).some(c => c.getAttribute('data-repo-path') === expectedPath);
      },
      E2E_FIXTURE,
      { timeout: 5000 },
    );
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await page.evaluate(() => window.__forceMatchUnknown ? window.__forceMatchUnknown() : null);
    await page.evaluate(() => window.__forceMatchUnknown ? window.__forceMatchUnknown() : null);
    await page.locator('#lgLocalRepoList .lg-local-repo-card').last().click();
    await page.waitForTimeout(300);
    await expect(page.locator('.lg-fix-toolbar')).toBeVisible();
    await page.locator('#lgLocalRepoList .lg-local-repo-card').last().click();
    await page.waitForTimeout(300);
    await page.evaluate(() => window.__forceMatchUnknown ? window.__forceMatchUnknown() : null);
    await expect(page.locator('.lg-fix-toolbar')).toBeHidden();
    await page.locator('#lgLocalRepoList .lg-local-repo-card').last().click();
    await page.waitForTimeout(300);
    await page.evaluate(() => window.__forceMatchUnknown ? window.__forceMatchUnknown() : null);
    await expect(page.locator('.lg-fix-toolbar')).toBeVisible();
  });

  test('remove button removes the card and hides the toolbar again', async ({ page }) => {
    await goto(page, '/logging');
    await page.evaluate(() => {
      window.__kiChatOpenDirBrowser = (cb) => setTimeout(() => cb(window.__E2E_FIXTURE_PATH), 50);
    });
    await page.locator('button.lg-repo-add-btn').click();
    await page.waitForTimeout(2500);
    await page.locator('#lgLocalRepoList .lg-local-repo-card').last().click();
    await page.waitForTimeout(300);
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await page.evaluate(() => window.__forceMatchUnknown ? window.__forceMatchUnknown() : null);
    await expect(page.locator('.lg-fix-toolbar')).toBeVisible();
    await page.locator('#lgLocalRepoList .lg-local-repo-card .lg-local-repo-del').first().click();
    await page.waitForTimeout(2000);
    expect(await page.locator('#lgLocalRepoList .lg-local-repo-card').count()).toBe(0);
    await expect(page.locator('.lg-fix-toolbar')).toBeHidden();
  });

  test('a single click selects a repository card that was rendered by the server', async ({ page }) => {
    const repo = await createLocalRepo(BASE_URL, {
      name: 'server-rendered-repo',
      path: E2E_FIXTURE,
      scope: 'LOGGING_ANALYSIS',
    });

    try {
      await goto(page, '/logging');
      const card = page.locator(
        `#lgLocalRepoList .lg-local-repo-card[data-repo-path="${E2E_FIXTURE}"]`,
      );
      await expect(card).toHaveCount(1);

      await card.click();

      await expect(card).toHaveClass(/selected/);
      expect(await page.evaluate(() => window._lgFix.selectedRepoId)).toBe(repo.id);

      await page.locator('#lgInput').fill(SAMPLE_LOG);
      await page.locator('#lgBtnAnalyze').click();
      await expect(page.locator('#lgResult')).toBeVisible();
      await expect(page.locator('.lg-fix-toolbar')).toBeVisible();
      await expect(page.locator('#lgBtnStartFix')).toBeEnabled();
    } finally {
      await deleteLocalRepoById(BASE_URL, repo.id);
    }
  });
});
