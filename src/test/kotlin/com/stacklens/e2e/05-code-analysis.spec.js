import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Code Analysis', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/code-analysis');
  });

  test('page loads with ca-elements', async ({ page }) => {
    await expect(page.locator('[class*="ca-"]').first()).toBeAttached();
  });

  test('sidebar and result panels exist in the DOM', async ({ page }) => {
    await expect(page.locator('.ca-panel, [class*="ca-panel"]').first()).toBeAttached();
  });

  test('tab bar is present', async ({ page }) => {
    await expect(page.locator('.ca-tabs, .ca-result-header').first()).toBeAttached();
  });

  test('repo list containers are in the DOM', async ({ page }) => {
    await expect(page.locator('#repoList')).toBeAttached();
    await expect(page.locator('#gitlabRepoSection')).toBeAttached();
  });

  test('KI-chat opens', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });

  test('clicking a repo card throws no JS exception', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const card = page.locator('.ca-repo-card').first();
    if (await card.count() > 0 && await card.isVisible()) await card.click();
    expect(errors).toHaveLength(0);
  });

  // ── Extended tests ─────────────────────────────────────────────────────────

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="ca-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  test('tab switching produces no JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const tabs = page.locator('.ca-tab-btn, .ca-tabs button').all();
    const allTabs = await tabs;
    for (const tab of allTabs.slice(0, 3)) {
      if (await tab.isVisible()) await tab.click();
    }
    expect(errors).toHaveLength(0);
  });

  test('dark theme: main panel background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/code-analysis');
    const bodyBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(bodyBg).not.toBe('rgb(255, 255, 255)');
  });

  test('ca-panel-list accepts keyboard navigation without errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const list = page.locator('.ca-panel-list, .ca-repo-card').first();
    if (await list.count() > 0) {
      await list.focus().catch(() => {});
      await page.keyboard.press('ArrowDown');
    }
    expect(errors).toHaveLength(0);
  });

  test('result area is present in the DOM', async ({ page }) => {
    await expect(page.locator('.ca-result, [class*="ca-result"]').first()).toBeAttached();
  });

  test('GitHub accordion opens without JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const ghHeader = page.locator('#githubRepoSection .ca-gh-accordion-header').first();
    if (await ghHeader.count() > 0) {
      await ghHeader.click();
      await page.waitForTimeout(200);
    }
    expect(errors).toHaveLength(0);
  });

  test('GitHub filter input appears when accordion has multiple repos', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const ghHeader = page.locator('#githubRepoSection .ca-gh-accordion-header').first();
    if (await ghHeader.count() > 0) {
      await ghHeader.click();
      await page.waitForTimeout(200);
      // Filter input is only injected when there are 2+ repos, so guard with count check
      const filterInput = page.locator('.ca-gh-filter');
      if (await filterInput.count() > 0) {
        await expect(filterInput).toBeVisible();
        await expect(filterInput).toHaveAttribute('type', 'search');
        await expect(filterInput).toHaveClass(/sl-search-input/);
        await filterInput.fill('test');
        await page.waitForTimeout(100);
        await filterInput.press('Escape');
        await expect(filterInput).toHaveValue('');
      }
    }
    expect(errors).toHaveLength(0);
  });
});

test.describe('Code Analysis Draw.io loader', () => {
  test('loads GraphViewer while rendering after SPA navigation and direct page load', async ({ page }) => {
    const drawioXml = '<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/><mxCell id="2" value="API" vertex="1" parent="1"><mxGeometry x="20" y="20" width="120" height="60" as="geometry"/></mxCell></root></mxGraphModel>';

    await goto(page, '/ticket-creation');
    expect(await page.evaluate(() => typeof window.GraphViewer)).toBe('undefined');

    await page.locator('.sidebar-link[href="/code-analysis"]').evaluate((link) => link.click());
    await expect(page).toHaveURL(/\/code-analysis$/);
    const spaViewerType = await page.evaluate(async (xml) => {
      window.caDrawioXmlStore = xml;
      await window.caRenderDrawio(xml);
      return typeof window.GraphViewer;
    }, drawioXml);

    await page.reload();
    expect(await page.evaluate(() => typeof window.GraphViewer)).toBe('undefined');
    const directViewerType = await page.evaluate(async (xml) => {
      window.caDrawioXmlStore = xml;
      await window.caRenderDrawio(xml);
      return typeof window.GraphViewer;
    }, drawioXml);

    expect(spaViewerType).toBe('function');
    expect(directViewerType).toBe('function');
    await expect(page.locator('#caDrawioContainer .mxgraph')).toBeAttached();

    await page.evaluate(() => window.openCaDrawioFullscreen());
    await expect(page.locator('#caDrawioFullContainer .mxgraph')).toBeAttached();
    await expect(page.locator('#caDrawioFullContainer')).not.toContainText('GraphViewer nicht geladen');
  });
});
