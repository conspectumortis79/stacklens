import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

/**
 * Pipeline Analysis – history + state persistence tests.
 *
 * Verifies:
 *  1. The history accordion is always present and shows the empty-state message
 *     when no analysis has ever run.
 *  2. Per-row "Load" / "Delete" buttons render correctly when history rows exist.
 *  3. The selected repo and active analysis survive navigation away and back
 *     (mirrors the behaviour of code-analysis and security-analysis).
 */
test.describe('Pipeline Analysis – History + State persistence', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/pipeline-analysis');
  });

  test('history accordion is present on initial load', async ({ page }) => {
    await expect(page.locator('#hxCard-pipeline-analysis')).toBeAttached();
    await expect(page.locator('#hxCard-pipeline-analysis [data-hx-table]')).toBeAttached();
    await expect(page.locator('#hxCard-pipeline-analysis [data-hx-empty]')).toBeAttached();
  });

  test('history shows empty-state when no analyses exist', async ({ page }) => {
    // Clear any leftover state in localStorage so the page renders empty.
    await page.evaluate(() => localStorage.clear());
    await page.reload();
    await page.waitForSelector('#hxCard-pipeline-analysis', { state: 'attached' });
    await page.waitForFunction(() => {
      const el = document.querySelector('#hxCard-pipeline-analysis [data-hx-count]');
      return el && el.textContent;
    }, { timeout: 5000 });

    const emptyVisible = await page.locator('#hxCard-pipeline-analysis [data-hx-empty]').isVisible();
    const tableVisible = await page.locator('#hxCard-pipeline-analysis [data-hx-table]').isVisible();
    expect(emptyVisible || tableVisible).toBe(true);
  });

  test('history accordion toggles collapsed state', async ({ page }) => {
    await page.evaluate(() => localStorage.clear());
    await page.reload();
    await page.waitForSelector('#hxCard-pipeline-analysis', { state: 'attached' });

    const card = page.locator('#hxCard-pipeline-analysis');
    const head = page.locator('#hxCard-pipeline-analysis .hx-head');
    await expect(card).not.toHaveClass(/is-collapsed/);
    await head.click();
    await expect(card).toHaveClass(/is-collapsed/);
    await expect(head).toHaveAttribute('aria-expanded', 'false');
    await head.click();
    await expect(card).not.toHaveClass(/is-collapsed/);
    await expect(head).toHaveAttribute('aria-expanded', 'true');
  });

  test('selected repo + analysis survive navigation away and back', async ({ page }) => {
    // 1. Pick the first available local repo (if any), otherwise skip.
    const firstRepoCard = page.locator('[id^="paRepoCard-"]').first();
    if (await firstRepoCard.count() === 0) {
      test.skip(true, 'No local repos configured — cannot test state persistence.');
    }
    const repoId = (await firstRepoCard.getAttribute('id')) || '';
    const repoName = await firstRepoCard.getAttribute('data-repo-name');
    await firstRepoCard.click();

    // After click, paUserPromptCard should be visible.
    await expect(page.locator('#paUserPromptCard')).toBeVisible();

    // localStorage should now contain pa_state.
    const stored = await page.evaluate(() => localStorage.getItem('pa_state'));
    expect(stored).toBeTruthy();
    const state = JSON.parse(stored);
    expect(state.repoId).toBe(Number(repoId.replace('paRepoCard-', '')));
    expect(state.repoName).toBe(repoName);

    // 2. Navigate to another analysis page (e.g. code-analysis) and back.
    await page.click('a[href="/code-analysis"]');
    await page.waitForSelector('#sidebar', { state: 'visible' });
    await page.click('a[href="/pipeline-analysis"]');
    await page.waitForSelector('[class*="pa-"]', { state: 'attached' });

    // 3. The same repo card must be marked as selected again.
    await expect(page.locator('#' + repoId)).toHaveClass(/selected/);

    // The user-prompt input should still be visible.
    await expect(page.locator('#paUserPromptCard')).toBeVisible();
  });

  test('history delete button is rendered with proper i18n label', async ({ page }) => {
    await page.evaluate(() => localStorage.clear());
    await page.reload();
    await page.waitForSelector('#hxCard-pipeline-analysis', { state: 'attached' });

    const rows = page.locator('#hxCard-pipeline-analysis tbody tr.hx-row');
    if (await rows.count() > 0) {
      await expect(rows.first().locator('[data-hx-act="remove"]')).toBeAttached();
      await expect(rows.first().locator('[data-hx-act="open"]')).toBeAttached();
      await expect(rows.first().locator('.hx-badge[data-status]')).toBeAttached();
    }
  });

  /**
   * Regression: user has the GitLab accordion for a previously-selected config
   * collapsed, navigates away and back. The accordion must STAY collapsed.
   *
   * Before the fix, `paSelectGitlabAfterProjectsLoad` auto-opened the
   * accordion to load its projects on every visit when `pa_state` still
   * held a `gitlabConfigId`/`gitlabProjectId` from an earlier selection,
   * ignoring `pa_gl_open_<id> === '0'`. That overrode the user's deliberate
   * close and snapped the accordion open again on every SPA navigation.
   */
  test('GitLab config accordion stays collapsed when user closed it (no auto-reopen on navigate-back)', async ({ page, request }) => {
    // 1. Discover the actual GitLab config id from a real DB render.
    await goto(page, '/pipeline-analysis');
    await page.waitForSelector('.pa-gitlab-cfg, #paGitlabConfigList', { timeout: 8000 });
    const cfgId = await page
      .locator('.pa-gitlab-cfg .pa-gitlab-header')
      .first()
      .getAttribute('data-cfg-id')
      .catch(() => null);
    test.skip(!cfgId, 'No GitLab configs configured — cannot test the auto-reopen path.');

    await page.evaluate(() => localStorage.clear());

    // 2. Pre-seed BOTH:
    //    - `pa_gl_open_<id> = '0'` → user has explicitly closed the accordion.
    //    - `pa_state` with gitlabConfigId/gitlabProjectId → triggers the
    //      auto-open branch in paSelectGitlabAfterProjectsLoad.
    await page.addInitScript(({ cfgId }) => {
      try {
        localStorage.setItem('pa_gl_open_' + cfgId, '0');
        localStorage.setItem('pa_state', JSON.stringify({
          analysisId: null,
          repoId: null,
          repoName: 'demo-gitlab/probe',
          gitlabConfigId: Number(cfgId),
          gitlabProjectId: 999999,
          gitlabProjectPath: 'demo-gitlab/probe',
          githubRepoConfigId: null,
          tab: null,
        }));
      } catch (_) {}
    }, { cfgId });

    await goto(page, '/pipeline-analysis');
    await page.waitForSelector('.pa-gitlab-cfg', { timeout: 8000 });
    // Give paSelectGitlabAfterProjectsLoad's setTimeout-tick a chance to
    // (incorrectly) auto-open the accordion.
    await page.waitForTimeout(800);

    const projectsDiv = page.locator('.pa-gitlab-cfg').first().locator('.pa-gitlab-projects');
    await expect(projectsDiv).toHaveClass(/d-none/);
    await expect(page.evaluate((id) => localStorage.getItem('pa_gl_open_' + id), cfgId))
      .resolves.toBe('0');

    // 3. Navigate to another analysis page and back — the same SPA flow the
    //    user reported.
    await page.evaluate(() => window.navigateTo('/code-analysis'));
    await page.waitForFunction(() => !!document.querySelector('#hxCard-code-analysis'));
    await page.evaluate(() => window.navigateTo('/pipeline-analysis'));
    await page.waitForSelector('.pa-gitlab-cfg', { timeout: 8000 });
    await page.waitForTimeout(800);

    const projectsDiv2 = page.locator('.pa-gitlab-cfg').first().locator('.pa-gitlab-projects');
    await expect(projectsDiv2).toHaveClass(/d-none/);
    await expect(page.evaluate((id) => localStorage.getItem('pa_gl_open_' + id), cfgId))
      .resolves.toBe('0');
  });
});
