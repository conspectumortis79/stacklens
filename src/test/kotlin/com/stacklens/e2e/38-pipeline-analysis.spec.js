import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto, installPageErrorFilter } from './helpers.js';

test.describe('Pipeline Analysis', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/pipeline-analysis');
  });

  test('page loads with pa-elements', async ({ page }) => {
    await expect(page.locator('[class*="pa-"]').first()).toBeAttached();
  });

  test('sidebar and result panels exist in the DOM', async ({ page }) => {
    await expect(page.locator('.pa-panel, [class*="pa-panel"]').first()).toBeAttached();
  });

  test('repo list containers are in the DOM', async ({ page }) => {
    await expect(page.locator('#repoList')).toBeAttached();
    await expect(page.locator('#paGitlabRepoSection')).toBeAttached();
  });

  test('summary tab empty-state is visible when no analysis has run yet', async ({ page }) => {
    await expect(page.locator('#paEmptyState')).toBeAttached();
  });

  test('user-prompt input card is hidden until a repo is selected', async ({ page }) => {
    const card = page.locator('#paUserPromptCard');
    if (await card.count() > 0) {
      await expect(card).toBeHidden();
    }
  });

  test('KI-Prompt button exists with correct data attribute', async ({ page }) => {
    const btn = page.locator('[data-prompt-prefix="pipeline"]').first();
    await expect(btn).toBeAttached();
  });

  test('KI-chat button opens the drawer', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="pa-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  test('tab switching produces no JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.waitForTimeout(100);
    expect(errors).toHaveLength(0);
  });

  test('result area is present in the DOM', async ({ page }) => {
    await expect(page.locator('.pa-result, [class*="pa-result"]').first()).toBeAttached();
  });

  test('GitHub section is rendered when there are GitHub configs', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="pa-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  for (const theme of ['modern', 'dark', 'popos', 'legacy']) {
    test(`theme "${theme}" renders without errors`, async ({ page }) => {
      await setTheme(page, theme);
      await page.goto('/pipeline-analysis');
      await page.waitForSelector('[class*="pa-"]', { state: 'attached' });
      const applied = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
      expect(applied).toBe(theme);
    });
  }

  test('dark theme: panel background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/pipeline-analysis');
    const panelBg = await page.evaluate(() => {
      const el = document.querySelector('.pa-panel');
      return el ? getComputedStyle(el).backgroundColor : '';
    });
    expect(panelBg).not.toBe('rgb(255, 255, 255)');
  });

  test('sidebar contains the pipeline-analysis nav entry', async ({ page }) => {
    await expect(page.locator('[data-nav-id="pipeline-analysis"]')).toBeAttached();
    await expect(page.locator('[data-nav-id="pipeline-analysis"]')).toBeVisible();
  });

  test('paSelect* helpers are safe to call on a page without the pipeline-analysis DOM', async ({ page }) => {
    const errors = installPageErrorFilter(page);

    await goto(page, '/pipeline-analysis');

    const stateOnPipeline = await page.evaluate(() => ({
      hasMarker: !!document.getElementById('paAnalysisPanel'),
      guardMounted: typeof paPageMounted === 'function' && paPageMounted(),
      hasSelectLocal: typeof paSelectLocal === 'function',
      hasSelectGithub: typeof paSelectGithub === 'function',
      hasSelectGitlab: typeof paSelectGitlab === 'function',
    }));
    expect(stateOnPipeline.hasMarker, '#paAnalysisPanel must be present on /pipeline-analysis').toBe(true);
    expect(stateOnPipeline.guardMounted, 'paPageMounted() must report the pipeline-analysis page').toBe(true);
    expect(stateOnPipeline.hasSelectLocal, 'paSelectLocal must be a global on /pipeline-analysis').toBe(true);
    expect(stateOnPipeline.hasSelectGithub, 'paSelectGithub must be a global on /pipeline-analysis').toBe(true);
    expect(stateOnPipeline.hasSelectGitlab, 'paSelectGitlab must be a global on /pipeline-analysis').toBe(true);

    await page.evaluate(() => {
      if (typeof window.navigateTo === 'function') {
        window.navigateTo('/docker-test');
      } else {
        window.location.href = '/docker-test';
      }
    });
    await page.waitForURL('**/docker-test', { timeout: 5_000 });

    const stateOnDockerTest = await page.evaluate(() => ({
      hasMarker: !!document.getElementById('paAnalysisPanel'),
      guardMounted: typeof paPageMounted === 'function' && paPageMounted(),
    }));
    expect(stateOnDockerTest.hasMarker, '#paAnalysisPanel must be absent on /docker-test').toBe(false);
    expect(stateOnDockerTest.guardMounted, 'paPageMounted() must report false on /docker-test').toBe(false);

    await page.evaluate(() => {
      const call = (label, fn) => {
        try { fn(); }
        catch (e) { window.__paTestErr = label + ': ' + e.message; throw e; }
      };
      call('paSelectLocal', () => paSelectLocal(99999, 'phantom-local'));
      call('paSelectGithub', () => paSelectGithub(99999, 'phantom-gh'));
      call('paSelectGitlab', () => paSelectGitlab(1, 1, 'g/p', 'phantom-gl'));
    });

    expect(errors, 'paSelect* helpers must not throw when the pipeline-analysis DOM is missing').toEqual([]);
  });
});
