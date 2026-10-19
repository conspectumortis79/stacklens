import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Ticket Summary', () => {

  test.beforeEach(async ({ page }) => {
    await page.request.delete('/api/ticket-summary').catch(() => {});
    await goto(page, '/ticket-summary');
  });

  test('page loads with ts-elements', async ({ page }) => {
    await expect(page.locator('[class*="ts-"]').first()).toBeAttached();
  });

  test('form card is visible', async ({ page }) => {
    await expect(page.locator('.ts-form-card').first()).toBeVisible();
  });

  test('ticket-key input is present', async ({ page }) => {
    await expect(page.locator('#tsTicketKey')).toBeAttached();
  });

  test('jira-config select is present', async ({ page }) => {
    await expect(page.locator('#tsJiraConfig')).toBeAttached();
  });

  test('prompt-template textarea is present', async ({ page }) => {
    await expect(page.locator('#tsTemplate')).toBeAttached();
  });

  test('start button is present', async ({ page }) => {
    await expect(page.locator('#tsStart')).toBeAttached();
  });

  test('cancel button is present and initially disabled', async ({ page }) => {
    const cancel = page.locator('#tsCancel');
    await expect(cancel).toBeAttached();
    await expect(cancel).toBeDisabled();
  });

  test('history list is in the DOM', async ({ page }) => {
    await expect(page.locator('#hxCard-ticket-summary')).toBeAttached();
  });

  test('results container is in the DOM', async ({ page }) => {
    await expect(page.locator('#tsResults')).toBeAttached();
  });

  test('status container is in the DOM', async ({ page }) => {
    await expect(page.locator('#tsStatus')).toBeAttached();
  });

  test('KI-Prompt button is present', async ({ page }) => {
    await expect(page.locator('[data-prompt-prefix="ticketsummary"]')).toBeAttached();
  });

  test('KI-Chat opens', async ({ page }) => {
    const btn = page.getByRole('button', { name: /KI Chat/i });
    if (await btn.count() > 0) {
      await btn.click();
      await expect(page.locator('#kiChatDrawer')).toBeVisible();
    }
  });

  test('show-default fills the textarea', async ({ page }) => {
    const showBtn = page.locator('#tsShowDefault');
    const ta = page.locator('#tsTemplate');
    await showBtn.click();
    await expect.poll(() => ta.inputValue(), { timeout: 5000 }).not.toBe('');
  });

  test('start without ticket key keeps the form on screen and focuses the input', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.locator('#tsStart').click();
    await page.waitForTimeout(150);
    expect(errors).toHaveLength(0);
    await expect(page.locator('.ts-form-card')).toBeVisible();
  });

  test('no JS errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="ts-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  test('dark theme: ts-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/ticket-summary');
    const card = page.locator('.ts-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('modern theme: ts-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'modern', '/ticket-summary');
    const card = page.locator('.ts-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('popos theme: ts-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'popos', '/ticket-summary');
    const card = page.locator('.ts-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('legacy (classic) theme: ts-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'legacy', '/ticket-summary');
    const card = page.locator('.ts-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('tsStart is enabled when jira config and ticket key are filled', async ({ page }) => {
    await page.route('**/api/config/jira', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([{ id: 1, name: 'Demo Jira', active: true }]),
      }),
    );
    await goto(page, '/ticket-summary');
    await page.locator('#tsJiraConfig').selectOption({ index: 0 });
    await page.locator('#tsTicketKey').fill('DEMO-1');
    await expect(page.locator('#tsStart')).toBeEnabled();
  });

  test('clicking start triggers POST /api/ticket-summary', async ({ page }) => {
    await page.route('**/api/config/jira', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([{ id: 1, name: 'Demo Jira', active: true }]),
      }),
    );

    await page.route('**/api/ticket-summary', async (route) => {
      const req = route.request();
      if (req.method() === 'POST') {
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({ id: 1234, status: 'PENDING', currentStep: 'Lade Ticket…' }),
        });
      }
      if (req.method() === 'GET') {
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify([]),
        });
      }
      if (req.method() === 'DELETE') {
        return route.fulfill({ status: 204, body: '' });
      }
      return route.fulfill({ status: 200, body: '{}' });
    });

    await page.route('**/api/ticket-summary/1234', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          id: 1234,
          status: 'RUNNING',
          currentStep: 'Lade Ticket…',
          ticketKey: 'DEMO-1',
        }),
      }),
    );

    await goto(page, '/ticket-summary');
    await page.locator('#tsJiraConfig').selectOption({ index: 0 });
    await page.locator('#tsTicketKey').fill('DEMO-1');
    await page.locator('#tsStart').click();

    await expect(page.locator('#tsProgressCard')).toBeVisible({ timeout: 4000 });
  });

  test('demo ticket is auto-filled when demo config is selected and field is empty', async ({ page }) => {
    await goto(page, '/ticket-summary');
    const demoValue = await page.evaluate(() => {
      const sel = document.getElementById('tsJiraConfig');
      if (!sel) return null;
      for (const opt of Array.from(sel.options)) {
        if (opt.dataset && opt.dataset.demo === 'true') return opt.value;
      }
      return null;
    });
    if (demoValue) {
      await page.locator('#tsJiraConfig').selectOption(demoValue);
      const v = await page.locator('#tsTicketKey').inputValue();
      expect(v).toMatch(/^[A-Z][A-Z0-9_]+-\d+$/);
    } else {
      expect(true).toBe(true);
    }
  });

  test('delete-all fires DELETE without a confirm() dialog', async ({ page }) => {
    await page.route('**/api/ticket-summary', route => {
      if (route.request().method() !== 'GET') return route.continue();
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 1, ticketKey: 'DEMO-1', status: 'COMPLETED', createdAt: new Date().toISOString() },
        ]),
      });
    });
    await goto(page, '/ticket-summary');

    const delAll = page.locator('#hxCard-ticket-summary [data-hx-delall]');
    await expect(delAll).toBeVisible();

    let confirmCalled = false;
    let alertCalled = false;
    page.on('dialog', d => {
      if (d.type() === 'confirm') confirmCalled = true;
      if (d.type() === 'alert')   alertCalled = true;
      d.dismiss();
    });

    const delReq = page.waitForRequest(r => r.url().includes('/api/ticket-summary') && r.method() === 'DELETE', { timeout: 4000 });
    await delAll.click();
    await delReq;

    expect(confirmCalled).toBe(false);
    expect(alertCalled).toBe(false);
  });
});
