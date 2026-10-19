import { test, expect } from '@playwright/test';
import { goto, setTheme, setThemeAndGoto } from './helpers.js';

test.describe('Docker Test Page', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/docker-test');
  });

  test('page loads with dt-card', async ({ page }) => {
    await expect(page.locator('.dt-card').first()).toBeVisible();
  });

  test('status badge is visible and shows idle state', async ({ page }) => {
    const badge = page.locator('#dtStatusBadge');
    await expect(badge).toBeVisible();
    await expect(badge).toHaveClass(/dt-status-idle/);
  });

  test('start button is present', async ({ page }) => {
    await expect(page.locator('#dtStartBtn')).toBeVisible();
  });

  test('reset button is present', async ({ page }) => {
    await expect(page.locator('#dtResetBtn')).toBeVisible();
  });

  test('output console is in the DOM', async ({ page }) => {
    await expect(page.locator('#dtOutput')).toBeAttached();
  });

  test('editor panel is present', async ({ page }) => {
    await expect(page.locator('.dt-editor-panel')).toBeVisible();
  });

  test('file list container is in the DOM', async ({ page }) => {
    await expect(page.locator('#dtFileList')).toBeAttached();
  });

  test('editor textarea is in the DOM', async ({ page }) => {
    await expect(page.locator('#dtEditorTextarea')).toBeAttached();
  });

  test('save & rebuild button is present (initially disabled)', async ({ page }) => {
    const saveBtn = page.locator('#dtSaveBtn');
    await expect(saveBtn).toBeAttached();
    // Save button should be disabled until a file is selected and modified
    await expect(saveBtn).toBeDisabled();
  });

  test('data-files REST endpoint returns a JSON array (smoke)', async ({ request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/docker-test/data-files`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
    body.forEach((entry) => {
      expect(entry.path).toEqual(expect.any(String));
      expect(entry.label).toEqual(expect.any(String));
    });
  });

  test('PUT /api/docker-test/data-file rejects a path outside the docker dir', async ({ request, baseURL }) => {
    const res = await request.put(`${baseURL}/api/docker-test/data-file`, {
      data: { path: '/etc/hosts', content: 'overwrite-attempt' },
    });
    // The backend must refuse with 4xx, never with 200
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('save button stays disabled until a file is loaded into the editor', async ({ page, request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/docker-test/data-files`);
    const files = await res.json();
    if (!Array.isArray(files) || files.length === 0) {
      test.skip(true, 'no demo fixtures present — start the docker demo stack first');
    }
    const firstFile = files[0];
    const fileItem = page.locator(`#dtFileList .dt-file-item[data-path="${firstFile.path}"]`).first();
    await fileItem.click();
    // dtSelectFile() fetches /data-file?path=… and populates the textarea.
    await expect(page.locator('#dtEditorTextarea')).toBeEnabled();
    // After loading, the save button should be enabled (the rebuild flow always
    // allows save once a file is selected; the dirty flag is a UX sugar layer).
    await expect(page.locator('#dtSaveBtn')).toBeEnabled();
    // The textarea must contain the loaded content (non-empty).
    const value = await page.locator('#dtEditorTextarea').inputValue();
    expect(value.length).toBeGreaterThan(0);
  });

  test('typing in the editor does not throw and keeps the save button enabled', async ({ page, request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/docker-test/data-files`);
    const files = await res.json();
    if (!Array.isArray(files) || files.length === 0) {
      test.skip(true, 'no demo fixtures present — start the docker demo stack first');
    }
    await page.locator('#dtFileList .dt-file-item').first().click();
    await expect(page.locator('#dtEditorTextarea')).toBeEnabled();
    const ta = page.locator('#dtEditorTextarea');
    await ta.focus();
    await page.keyboard.press('End');
    await page.keyboard.type('\n# e2e marker');
    await expect(page.locator('#dtSaveBtn')).toBeEnabled();
  });

  test('sidebar is visible (standard layout)', async ({ page }) => {
    await expect(page.locator('#sidebar')).toBeVisible();
  });

  test('theme "dark" → dt-card uses dark background', async ({ page }) => {
    await setThemeAndGoto(page, 'dark', '/docker-test');
    const card = page.locator('.dt-card').first();
    const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
    // dark theme overrides background to #1f1c2c
    expect(bg).not.toBe('rgb(255, 253, 248)');
  });

  test('theme "popos" → dt-card does not use light background', async ({ page }) => {
    await setThemeAndGoto(page, 'popos', '/docker-test');
    const card = page.locator('.dt-card').first();
    const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor);
    expect(bg).not.toBe('rgb(255, 253, 248)');
  });

  test('no JavaScript errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (err) => {
      // The docker-test page embeds the draw.io viewer (viewer.diagrams.net)
      // which in turn loads additional resources from that CDN.  When the test
      // environment has no outbound HTTPS to viewer.diagrams.net, the script
      // load fails and surfaces here as a `pageerror`.  These CDN failures are
      // not bugs in StackLens code — they are an environmental constraint of
      // hermetic CI sandboxes — so we filter them out and assert on the
      // remaining (real) JavaScript errors only.
      if (err?.message && /viewer\.diagrams\.net/.test(err.message)) return;
      errors.push(err.message);
    });
    await goto(page, '/docker-test');
    // Allow a moment for JS to initialise
    await page.waitForTimeout(500);
    expect(errors).toHaveLength(0);
  });
});
