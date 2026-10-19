import { test, expect } from '@playwright/test';
import { goto, setThemeAndGoto } from './helpers.js';

test.describe('Logging analysis', () => {

  const SAMPLE_LOG = [
    '2025-01-15 10:00:01.101 INFO  com.example.app.Main - Application started',
    '2025-01-15 10:00:02.202 WARN  com.example.app.Cache - Cache entry missing, will reload',
    '2025-01-15 10:00:03.303 ERROR com.example.app.OrderService - Failed to save order id=99',
    'java.sql.SQLException: Connection refused',
    '    at com.example.app.OrderService.save(OrderService.java:87)',
    '2025-01-15 10:00:04.404 INFO  com.example.app.Health - Health check OK',
    '2025-01-15 10:00:05.505 FATAL com.example.app.Main - Unrecoverable state, shutting down',
  ].join('\n');

  test('is reachable via the sidebar and shows the input card', async ({ page }) => {
    await goto(page, '/');
    const navItem = page.locator('[data-nav-id="logging"]');
    await expect(navItem).toBeVisible();
    await navItem.click();
    await page.waitForURL(/\/logging$/);
    await expect(page.locator('#lgInput')).toBeVisible();
    await expect(page.locator('#lgBtnAnalyze')).toBeVisible();
  });

  test('page header shows the KI Chat and KI-Prompt buttons', async ({ page }) => {
    await goto(page, '/logging');
    await expect(page.locator('button[onclick="kiChatToggle()"]')).toBeVisible();
    const promptBtn = page.locator('[data-prompt-prefix="logging"]');
    await expect(promptBtn).toBeAttached();
  });

  test('KI-Prompt button becomes enabled and opens the editor with the logging section', async ({ page }) => {
    await page.route('**/api/ui-prefs', async (route) => {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ dev_show_prompt_buttons: 'true' }),
      });
    });
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_show_prompt_buttons', 'true'); } catch (_) { }
    });
    await goto(page, '/logging');
    const promptBtn = page.locator('[data-prompt-prefix="logging"]');
    await expect(promptBtn).toBeEnabled({ timeout: 15_000 });
    await promptBtn.click();
    await expect(page.locator('#promptEditorModal')).toBeVisible({ timeout: 10_000 });
    await expect(page.locator('#promptEditorModal')).toContainText('Log-Analyse', { timeout: 10_000 });
  });

  test('empty input shows an error instead of a result', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#lgError')).toBeVisible();
    await expect(page.locator('#lgResult')).toBeHidden();
  });

  test('analyzes a pasted log and marks problem lines with badges', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#lgResult')).toBeVisible();
    await expect(page.locator('#lgDonePill')).toHaveClass(/visible/);

    await expect(page.locator('#lgLogView .lg-line')).toHaveCount(7);
    await expect(page.locator('#lgLogView .lg-line.lg-sev-critical')).toHaveCount(1);
    await expect(page.locator('#lgLogView .lg-line.lg-sev-error')).toHaveCount(3);
    await expect(page.locator('#lgLogView .lg-line.lg-sev-warn')).toHaveCount(1);
    await expect(page.locator('#lgLogView .lg-badge-critical')).toHaveCount(1);
    await expect(page.locator('#lgLogView .lg-badge-stack')).toHaveCount(1);
    await expect(page.locator('#lgLogView .lg-badge-connection')).toHaveCount(1);

    const summary = page.locator('#lgSummaryBadges');
    await expect(summary).toContainText('1');
    await expect(page.locator('#lgSummaryInfo')).toContainText('7');
  });

  test('issues-only filter hides clean lines', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#lgLogView .lg-line')).toHaveCount(7);

    await page.locator('#lgOnlyIssues').check();
    await expect(page.locator('#lgLogView .lg-line:not(.lg-hidden)')).toHaveCount(5);
  });

  test('clear removes input and result', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#lgResult')).toBeVisible();

    await page.locator('.lg-btn-secondary:has-text("Leeren"), .lg-btn-secondary:has-text("Clear")').first().click();
    await expect(page.locator('#lgInput')).toHaveValue('');
    await expect(page.locator('#lgResult')).toBeHidden();
  });

  test('renders in all four themes without JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    for (const theme of ['modern', 'legacy', 'dark', 'popos']) {
      await setThemeAndGoto(page, theme, '/logging');
      await expect(page.locator('#lgInput')).toBeVisible();
      await page.locator('#lgInput').fill(SAMPLE_LOG);
      await page.locator('#lgBtnAnalyze').click();
      await expect(page.locator('#lgResult')).toBeVisible();
    }
    expect(errors).toHaveLength(0);
  });

  test('history card shows the unified empty state before the first analysis', async ({ page }) => {
    await goto(page, '/logging');
    await page.evaluate(() => window.localStorage.removeItem('stacklens.logging.history'));
    await goto(page, '/logging');
    await expect(page.locator('#hxCard-logging')).toBeVisible();
    await expect(page.locator('#hxCard-logging [data-hx-empty]')).toBeVisible();
    await expect(page.locator('#hxCard-logging tbody tr.hx-row')).toHaveCount(0);
    await expect(page.locator('#hxCard-logging [data-hx-delall]')).toBeHidden();
  });

  test('analysis populates history and shows a row with action buttons', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();

    const card = page.locator('#hxCard-logging');
    await expect(card.locator('[data-hx-table]')).toBeVisible();
    await expect(card.locator('tbody tr.hx-row')).toHaveCount(1);
    await expect(card.locator('[data-hx-count]')).toContainText('1');
    await expect(card.locator('[data-hx-delall]')).toBeVisible();
    await expect(card.locator('tbody [data-hx-act="open"]')).toHaveCount(1);
    await expect(card.locator('tbody [data-hx-act="remove"]')).toHaveCount(1);
    await expect(card.locator('tbody .hx-badge[data-status="COMPLETED"]')).toHaveCount(1);
  });

  test('restoring a history entry re-fills the textarea and re-renders the result', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill('INFO something else');
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#hxCard-logging tbody tr.hx-row')).toHaveCount(1);

    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#hxCard-logging tbody tr.hx-row')).toHaveCount(2);

    await page.locator('#hxCard-logging tbody [data-hx-act="open"]').first().click();
    await expect(page.locator('#lgInput')).toHaveValue(SAMPLE_LOG);
    await expect(page.locator('#lgResult')).toBeVisible();
    await expect(page.locator('#lgRestoreToast')).toBeVisible();
  });

  test('restoring a history entry does not duplicate the history row', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#hxCard-logging tbody tr.hx-row')).toHaveCount(1);

    for (let i = 0; i < 3; i++) {
      await page.locator('#hxCard-logging tbody [data-hx-act="open"]').first().click();
    }

    await expect(page.locator('#hxCard-logging tbody tr.hx-row')).toHaveCount(1);
    await expect(page.locator('#hxCard-logging [data-hx-count]')).toContainText('1');
  });

  test('single-entry delete button removes only that row', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await page.locator('#lgInput').fill('INFO tail');
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#hxCard-logging tbody tr.hx-row')).toHaveCount(2);

    await page.locator('#hxCard-logging tbody [data-hx-act="remove"]').first().click();
    await expect(page.locator('#hxCard-logging tbody tr.hx-row')).toHaveCount(1);
  });

  test('Alle löschen empties the history and falls back to the empty state', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    await page.locator('#lgInput').fill('INFO x');
    await page.locator('#lgBtnAnalyze').click();
    await expect(page.locator('#hxCard-logging tbody tr.hx-row')).toHaveCount(2);

    await page.locator('#hxCard-logging [data-hx-delall]').click();
    await expect(page.locator('#hxCard-logging tbody tr.hx-row')).toHaveCount(0);
    await expect(page.locator('#hxCard-logging [data-hx-empty]')).toBeVisible();
  });

  test('accordion toggle expands and collapses the history body', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();

    const card  = page.locator('#hxCard-logging');
    const head  = page.locator('#hxCard-logging .hx-head').first();
    const body  = page.locator('#hxCard-logging [data-hx-body]');
    await expect(body).toBeVisible();
    await expect(card).not.toHaveClass(/is-collapsed/);

    await head.click();
    await expect(body).toBeHidden();
    await expect(card).toHaveClass(/is-collapsed/);

    await head.click();
    await expect(body).toBeVisible();
    await expect(card).not.toHaveClass(/is-collapsed/);
  });

  test('summary badges are rendered as toggle buttons and all are active by default', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();

    const badges = page.locator('#lgSummaryBadges .lg-summary-badge');
    await expect(badges.first()).toBeVisible();
    const count = await badges.count();
    expect(count).toBeGreaterThan(0);

    for (let i = 0; i < count; i++) {
      const b = badges.nth(i);
      await expect(b).toHaveClass(/lg-summary-badge-active/);
      await expect(b).not.toHaveClass(/lg-summary-badge-inactive/);
      await expect(b).toHaveAttribute('aria-pressed', 'true');
    }
  });

  test('clicking a summary badge hides lines of that category from the log view', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();

    const errorBadge = page.locator('#lgSummaryBadges .lg-summary-badge[data-category="error"]');
    await expect(errorBadge).toBeVisible();

    const errorCategoryLine = page.locator('#lgLogView .lg-line:has-text("Failed to save order id=99")');
    await expect(errorCategoryLine).toBeVisible();

    const visibleBefore = await page.locator('#lgLogView .lg-line:not(.lg-hidden)').count();

    await errorBadge.click();
    await expect(errorBadge).toHaveClass(/lg-summary-badge-inactive/);
    await expect(errorBadge).not.toHaveClass(/lg-summary-badge-active/);
    await expect(errorBadge).toHaveAttribute('aria-pressed', 'false');

    await expect(errorCategoryLine).toBeHidden();

    const visibleAfter = await page.locator('#lgLogView .lg-line:not(.lg-hidden)').count();
    expect(visibleAfter).toBeLessThan(visibleBefore);
    expect(visibleAfter).toBeGreaterThan(0);

    await errorBadge.click();
    await expect(errorBadge).toHaveClass(/lg-summary-badge-active/);
    await expect(errorCategoryLine).toBeVisible();
  });

  test('multiple badges can be toggled and the view reflects their combination', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();

    const errorBadge = page.locator('#lgSummaryBadges .lg-summary-badge[data-category="error"]');
    const criticalBadge = page.locator('#lgSummaryBadges .lg-summary-badge[data-category="critical"]');

    const errorLine  = page.locator('#lgLogView .lg-line:has-text("Failed to save order id=99")');
    const criticalLine = page.locator('#lgLogView .lg-line:has-text("Unrecoverable state")');
    const warnLine = page.locator('#lgLogView .lg-line:has-text("Cache entry missing")');

    await errorBadge.click();
    await criticalBadge.click();

    await expect(errorLine).toBeHidden();
    await expect(criticalLine).toBeHidden();
    await expect(warnLine).toBeVisible();
  });

  test('toggling all badges off leaves the log view empty', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();

    const badges = page.locator('#lgSummaryBadges .lg-summary-badge');
    const count = await badges.count();
    for (let i = 0; i < count; i++) {
      await badges.nth(i).click();
    }
    const visible = await page.locator('#lgLogView .lg-line:not(.lg-hidden)').count();
    expect(visible).toBe(0);
  });

  test('Leeren button resets the badge filter alongside the input and result', async ({ page }) => {
    await goto(page, '/logging');
    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();

    const errorBadge = page.locator('#lgSummaryBadges .lg-summary-badge[data-category="error"]');
    await errorBadge.click();
    await expect(errorBadge).toHaveClass(/lg-summary-badge-inactive/);

    await page.locator('.lg-btn-secondary:has-text("Leeren"), .lg-btn-secondary:has-text("Clear")').first().click();
    await expect(page.locator('#lgInput')).toHaveValue('');
    await expect(page.locator('#lgResult')).toBeHidden();

    await page.locator('#lgInput').fill(SAMPLE_LOG);
    await page.locator('#lgBtnAnalyze').click();
    const newErrorBadge = page.locator('#lgSummaryBadges .lg-summary-badge[data-category="error"]');
    await expect(newErrorBadge).toHaveClass(/lg-summary-badge-active/);
  });

  test('summary badge filter works in all four themes without JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    for (const theme of ['modern', 'legacy', 'dark', 'popos']) {
      await setThemeAndGoto(page, theme, '/logging');
      await page.locator('#lgInput').fill(SAMPLE_LOG);
      await page.locator('#lgBtnAnalyze').click();

      const errorBadge = page.locator('#lgSummaryBadges .lg-summary-badge[data-category="error"]');
      await expect(errorBadge).toBeVisible();
      await errorBadge.click();
      await expect(errorBadge).toHaveClass(/lg-summary-badge-inactive/);

      await expect(page.locator('#lgLogView .lg-line:has-text("Failed to save order id=99")')).toBeHidden();
    }
    expect(errors).toHaveLength(0);
  });
});