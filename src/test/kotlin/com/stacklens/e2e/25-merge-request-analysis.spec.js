import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto, installPageErrorFilter } from './helpers.js';

test.describe('Merge Request Analysis Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.request.delete('/api/mr-analysis/history').catch(() => {});
    await goto(page, '/code-qa');
  });

  test('page renders with the sidebar (standard layout)', async ({ page }) => {
    await expect(page.locator('#sidebar')).toBeVisible();
  });

  test('page title contains "MR-Analyse"', async ({ page }) => {
    await expect(page).toHaveTitle(/Code QA|Code-QA-Analyse/);
  });

  test('main form card is visible', async ({ page }) => {
    await expect(page.locator('.mr-card').first()).toBeVisible();
  });

  test('MR-URL input is present and empty', async ({ page }) => {
    const input = page.locator('#mrInputUrl');
    await expect(input).toBeVisible();
    await expect(input).toHaveValue('');
  });

  test('GitLab/GitHub config select is present', async ({ page }) => {
    await expect(page.locator('#mrSelectGitConfig')).toBeVisible();
  });

  test('Jira ticket input is present', async ({ page }) => {
    await expect(page.locator('#mrInputJiraKey')).toBeVisible();
  });

  test('Jira config select is present', async ({ page }) => {
    await expect(page.locator('#mrSelectJiraConfig')).toBeVisible();
  });

  test('additional instructions textarea is present', async ({ page }) => {
    await expect(page.locator('#mrInputAdditionalInstructions')).toBeVisible();
  });

  test('code-suggestions checkbox is present and unchecked', async ({ page }) => {
    const cb = page.locator('#mrChkCodeSuggestions');
    await expect(cb).toBeAttached();
    await expect(cb).not.toBeChecked();
  });

  test('integration-test checkbox is present and unchecked', async ({ page }) => {
    const cb = page.locator('#mrChkIntegrationTest');
    await expect(cb).toBeAttached();
    await expect(cb).not.toBeChecked();
  });

  test('start button is disabled when URL is empty', async ({ page }) => {
    await expect(page.locator('#mrBtnStart')).toBeDisabled();
  });

  test('cancel button is hidden initially', async ({ page }) => {
    await expect(page.locator('#mrBtnCancel')).toBeHidden();
  });

  test('typing a URL enables the start button', async ({ page }) => {
    await page.locator('#mrInputUrl').fill('https://gitlab.example.com/group/project/-/merge_requests/42');
    await expect(page.locator('#mrBtnStart')).toBeEnabled();
  });

  test('clearing the URL disables the start button again', async ({ page }) => {
    const url = page.locator('#mrInputUrl');
    await url.fill('https://gitlab.example.com/group/project/-/merge_requests/42');
    await expect(page.locator('#mrBtnStart')).toBeEnabled();
    await url.fill('');
    await expect(page.locator('#mrBtnStart')).toBeDisabled();
  });

  test('progress card is in DOM but hidden initially', async ({ page }) => {
    const wrap = page.locator('#mrProgressWrap');
    await expect(wrap).toBeAttached();
    await expect(wrap).toBeHidden();
  });

  test('error card is in DOM but hidden initially', async ({ page }) => {
    const card = page.locator('#mrErrorCard');
    await expect(card).toBeAttached();
    await expect(card).toBeHidden();
  });

  test('result card is in DOM but hidden initially', async ({ page }) => {
    const card = page.locator('#mrResultCard');
    await expect(card).toBeAttached();
    await expect(card).toBeHidden();
  });

  test('integration-test card is in DOM but hidden initially', async ({ page }) => {
    const card = page.locator('#mrIntegrationTestCard');
    await expect(card).toBeAttached();
    await expect(card).toBeHidden();
  });

  test('history table is rendered', async ({ page }) => {
    await expect(page.locator('#hxCard-code-qa')).toBeVisible();
  });

  test('history table has the expected column headers', async ({ page }) => {
    const headers = page.locator('#hxCard-code-qa thead th');
    await expect(headers).toHaveCount(4);
    await expect(headers.nth(1)).toHaveAttribute('data-i18n', 'common.history_col_status');
    await expect(headers.nth(2)).toHaveAttribute('data-i18n', 'common.history_col_created');
    await expect(headers.nth(3)).toHaveClass(/hx-col-actions/);
  });

  test('delete-all history button is present and hidden while the history is empty', async ({ page }) => {
    const delAll = page.locator('#hxCard-code-qa [data-hx-delall]');
    await expect(delAll).toBeAttached();
    await expect(delAll).toBeHidden();
  });

  test('KI-Chat button is present in the toolbar', async ({ page }) => {
    await expect(page.getByRole('button', { name: /KI Chat/i })).toBeVisible();
  });

  test('KI-Prompt button is present in the toolbar', async ({ page }) => {
    await expect(page.locator('button[data-prompt-prefix="mranalysis"]')).toBeAttached();
  });

  test('KI-Chat drawer opens when clicking the chat button', async ({ page }) => {
    await page.getByRole('button', { name: /KI Chat/i }).click();
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
  });

  test('mcp-sources meta tag declares gitlab', async ({ page }) => {
    const content = await page.locator('meta[name="mcp-sources"]').getAttribute('content');
    expect(content).toContain('gitlab');
  });

  test('typing an invalid URL still enables the button (client only checks emptiness)', async ({ page }) => {
    await page.locator('#mrInputUrl').fill('not-a-url');
    await expect(page.locator('#mrBtnStart')).toBeEnabled();
  });

  test('no JavaScript errors on page load', async ({ page }) => {
    const errors = installPageErrorFilter(page);
    await goto(page, '/code-qa');
    await page.waitForTimeout(800);
    expect(errors).toHaveLength(0);
  });

  test('dark theme: mr-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/code-qa');
    const bg = await page.locator('.mr-card').first().evaluate(
      (el) => getComputedStyle(el).backgroundColor,
    );
    expect(bg).not.toBe('rgb(255, 255, 255)');
  });

  test('popos theme: page renders without horizontal overflow', async ({ page }) => {
    await setThemeAndGoto(page, 'popos', '/code-qa');
    const hasOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
    );
    expect(hasOverflow).toBe(false);
  });

  test('toggling code-suggestions checkbox updates its state', async ({ page }) => {
    const cb = page.locator('#mrChkCodeSuggestions');
    await cb.check();
    await expect(cb).toBeChecked();
    await cb.uncheck();
    await expect(cb).not.toBeChecked();
  });

  test('toggling integration-test checkbox updates its state', async ({ page }) => {
    const cb = page.locator('#mrChkIntegrationTest');
    await cb.check();
    await expect(cb).toBeChecked();
    await cb.uncheck();
    await expect(cb).not.toBeChecked();
  });
});
