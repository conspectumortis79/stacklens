import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

/**
 * E2E tests for the per-source MCP/native indicator chips that are rendered
 * by /js/mcp-status-badge.js whenever a page declares
 * <meta name="mcp-sources" content="...">.
 *
 * The Sicherheits-Analyse declares gitlab + github as sources, so two chips
 * must render after page load. Each chip shows the source-icon, the source
 * label, a status dot, the mode-icon (bi-hdd-fill = native, bi-hdd-network-fill
 * = MCP) and a "nativ"/"MCP" label.
 */
test.describe('MCP source-indicator chips', () => {
  test('Sicherheits-Analyse renders gitlab + github chips', async ({ page }) => {
    await goto(page, '/security-analysis');
    const chips = page.locator('.mcp-status-chip');
    await expect(chips).toHaveCount(2);
    await expect(chips.nth(0)).toContainText(/GitLab/i);
    await expect(chips.nth(1)).toContainText(/GitHub/i);
  });

  test('chips include source-icon (bi-git/bi-github)', async ({ page }) => {
    await goto(page, '/security-analysis');
    await expect(page.locator('.mcp-status-chip i.bi-git').first()).toBeAttached();
    await expect(page.locator('.mcp-status-chip i.bi-github').first()).toBeAttached();
  });

  test('chips include status dot and mode-icon', async ({ page }) => {
    await goto(page, '/security-analysis');
    const first = page.locator('.mcp-status-chip').first();
    await expect(first.locator('.mcp-dot')).toBeAttached();
    // Either MCP icon (bi-hdd-network-fill) or native icon (bi-hdd-fill)
    const modeIcon = first.locator('i.bi-hdd-fill, i.bi-hdd-network-fill');
    await expect(modeIcon.first()).toBeAttached();
  });

  test('chips show "nativ" or "MCP" mode label', async ({ page }) => {
    await goto(page, '/security-analysis');
    const text = await page.locator('.mcp-status-chip').first().textContent();
    expect(text).toMatch(/nativ|MCP/i);
  });

  test('chips render correctly in dark theme', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/security-analysis');
    const chip = page.locator('.mcp-status-chip').first();
    await expect(chip).toBeVisible();
    const color = await chip.evaluate((el) => getComputedStyle(el).color);
    // dark theme uses light-grey/green text — definitely not pure black
    expect(color).not.toBe('rgb(0, 0, 0)');
  });

  test('chips render correctly in popos theme', async ({ page }) => {
    await setThemeAndGoto(page, 'popos', '/security-analysis');
    const chip = page.locator('.mcp-status-chip').first();
    await expect(chip).toBeVisible();
  });

  test('chips render correctly in legacy theme', async ({ page }) => {
    await setThemeAndGoto(page, 'legacy', '/security-analysis');
    const chip = page.locator('.mcp-status-chip').first();
    await expect(chip).toBeVisible();
  });

  test('pages without meta tag do NOT render any chips', async ({ page }) => {
    // /docs has no meta name="mcp-sources"
    await goto(page, '/docs');
    await expect(page.locator('.mcp-status-chip')).toHaveCount(0);
  });

  // ── Per-analysis-page coverage ────────────────────────────────────────────
  // Every analysis/search page must permanently show at least one source chip
  // so the user always knows whether data comes natively or through the MCP
  // server. Regression guard: this matrix MUST stay in sync with the
  // <meta name="mcp-sources"> tag of each template.
  const PAGE_CHIPS = [
    { path: '/security-analysis',     min: 2, expectLabels: ['GitLab', 'GitHub'] },
    { path: '/code-analysis',         min: 2, expectLabels: ['GitLab', 'GitHub'] },
    { path: '/review-analysis',       min: 2, expectLabels: ['Jira', 'GitLab'] },
    { path: '/ticket-implementation',       min: 3, expectLabels: ['Jira', 'Confluence', 'GitLab'] },
    { path: '/sql-analysis',          min: 1, expectLabels: ['DB'] },
    { path: '/grafana-analysis',      min: 1, expectLabels: ['Grafana'] },
    { path: '/code-qa',               min: 1, expectLabels: ['GitLab'] },
    { path: '/lasttest-analysis',     min: 3, expectLabels: ['GitLab', 'GitHub', 'Grafana'] },
    { path: '/search',                min: 6, expectLabels: ['Jira', 'Confluence', 'GitLab', 'GitHub', 'Grafana', 'DB'] },
  ];
  for (const { path, min, expectLabels } of PAGE_CHIPS) {
    test(`${path} permanently renders source chips with mode label`, async ({ page }) => {
      await goto(page, path);
      const chips = page.locator('.mcp-status-chip');
      await expect(chips).toHaveCount(min);
      for (const label of expectLabels) {
        await expect(chips.filter({ hasText: label })).toHaveCount(1);
      }
      // Each chip must contain a mode-icon (native or MCP) and the mode label.
      for (let i = 0; i < min; i++) {
        const chip = chips.nth(i);
        await expect(chip.locator('i.bi-hdd-fill, i.bi-hdd-network-fill')).toHaveCount(1);
        const txt = await chip.textContent();
        expect(txt).toMatch(/nativ|MCP/i);
      }
    });
  }
});
