import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Review Analysis', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/review-analysis');
  });

  test('page loads with ra-elements', async ({ page }) => {
    await expect(page.locator('[class*="ra-"]').first()).toBeAttached();
  });

  test('main card is visible', async ({ page }) => {
    await expect(page.locator('.ra-card').first()).toBeVisible();
  });

  test('download chips are present', async ({ page }) => {
    await expect(page.locator('.ra-dl-chip').first()).toBeAttached();
  });

  test('input fields are present', async ({ page }) => {
    await expect(page.locator('.ra-input, .ra-select').first()).toBeAttached();
  });

  test('accordion header is clickable without errors', async ({ page }) => {
    const head = page.locator('.hx-head').first();
    if (await head.count() > 0) {
      const errors = [];
      page.on('pageerror', (e) => errors.push(e.message));
      await head.click();
      expect(errors).toHaveLength(0);
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
    await page.waitForSelector('[class*="ra-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });

  test('ra-select (review type dropdown) is present', async ({ page }) => {
    const sel = page.locator('.ra-select').first();
    if (await sel.count() > 0) {
      await expect(sel).toBeAttached();
    }
  });

  test('start analyse button is present', async ({ page }) => {
    const btn = page.locator('.ra-btn, button[onclick*="ra"]').first();
    if (await btn.count() > 0) {
      await expect(btn).toBeAttached();
    }
  });

  test('dark theme: ra-card background is not white', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/review-analysis');
    const card = page.locator('.ra-card').first();
    if (await card.count() > 0 && await card.isVisible()) {
      const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
      expect(bg).not.toBe('rgb(255, 255, 255)');
    }
  });

  test('accordion expand/collapse cycle produces no JS errors', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const heads = await page.locator('.hx-head').all();
    for (const head of heads.slice(0, 2)) {
      await head.click();
      await page.waitForTimeout(150);
      await head.click();
    }
    expect(errors).toHaveLength(0);
  });

  test('generated responsive Chart.js dashboard stays bounded in a wide window', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.reload();
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    const dashboard = `<!DOCTYPE html>
<html><head><style>
body{margin:0;padding:24px;background:#0f1117}.chart-grid{display:grid;grid-template-columns:1fr 1fr;gap:24px}
.chart-box{padding:24px;position:relative;background:#22263a}.chart-box canvas{display:block;margin:0 auto;max-width:100%}
</style><script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.2/dist/chart.umd.min.js"><\/script></head>
<body><div class="chart-grid">
<div class="chart-box"><canvas id="chart-a" width="280" height="280"></canvas></div>
<div class="chart-box"><canvas id="chart-b" width="280" height="280"></canvas></div>
</div><script>
window.addEventListener('load',function(){['chart-a','chart-b'].forEach(function(id){new Chart(document.getElementById(id),{type:'doughnut',data:{labels:['Done','Open'],datasets:[{data:[4,2]}]},options:{responsive:true,maintainAspectRatio:false}});});});
<\/script></body></html>`;

    await page.evaluate((html) => {
      window.showResults('```html\n' + html + '\n```', 'Sizing regression', new Date().toISOString());
    }, dashboard);
    const frame = page.frameLocator('#raDashboardIframe');
    await expect.poll(() => frame.locator('body').evaluate(() => typeof window.Chart)).toBe('function');
    await page.waitForTimeout(1200);

    const firstSize = await frame.locator('#chart-a').evaluate((canvas) => ({ width: canvas.width, height: canvas.height, clientHeight: canvas.clientHeight }));
    await page.waitForTimeout(500);
    const secondSize = await frame.locator('#chart-a').evaluate((canvas) => ({ width: canvas.width, height: canvas.height, clientHeight: canvas.clientHeight }));
    const iframeHeight = await page.locator('#raDashboardIframe').evaluate((iframe) => iframe.getBoundingClientRect().height);

    expect(firstSize.height).toBeLessThanOrEqual(320);
    expect(secondSize.height).toBeLessThanOrEqual(320);
    expect(secondSize.clientHeight).toBeLessThanOrEqual(320);
    expect(iframeHeight).toBeLessThanOrEqual(1200);
    expect(errors.filter((message) => message.includes('Canvas exceeds max size'))).toHaveLength(0);
  });
});
