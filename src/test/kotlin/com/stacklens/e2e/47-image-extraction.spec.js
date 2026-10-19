import { test, expect } from '@playwright/test';
import { goto, setThemeAndGoto } from './helpers.js';

test.describe('Image Extraction Page', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/image-extraction');
  });

  test('page loads with the ie-page container', async ({ page }) => {
    await expect(page.locator('.ie-page').first()).toBeVisible();
  });

  test('sidebar contains the image-extraction nav entry', async ({ page }) => {
    await expect(page.locator('#sidebarNav [data-nav-id="image-extraction"]'))
      .toBeVisible();
  });

  test('KI-Prompt button is present with the imageextraction prefix', async ({ page }) => {
    const btn = page.locator('[data-prompt-prefix="imageextraction"]').first();
    await expect(btn).toBeAttached();
    // The button starts disabled and is enabled by the prompt-ready poller
    // only after /api/prompts/groups resolves. We assert the contract (data
    // attribute + click handler) instead of a transient disabled state, which
    // races with the async poller.
    await expect(btn).toHaveAttribute('data-prompt-prefix', 'imageextraction');
    const onclick = (await btn.getAttribute('onclick')) ?? '';
    expect(onclick).toContain("openPromptEditorForPrefix('imageextraction'");
  });

  test('file dropped outside the dropzone does not navigate away from StackLens', async ({ page }) => {
    const urlBefore = page.url();

    await page.evaluate(() => {
      window.__ieSpyCalls = 0;
      const orig = window.ieFileSelected;
      window.ieFileSelected = function() {
        window.__ieSpyCalls++;
        return orig ? orig.apply(this, arguments) : undefined;
      };
    });

    await page.evaluate(() => {
      const dt = new DataTransfer();
      const file = new File(['fake'], 'test.png', { type: 'image/png' });
      dt.items.add(file);
      const target = document.querySelector('.ie-page-subtitle') || document.body;
      const ev = new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: dt });
      target.dispatchEvent(ev);
    });

    await page.waitForTimeout(200);
    expect(page.url()).toBe(urlBefore);
    const spyCalls = await page.evaluate(() => window.__ieSpyCalls);
    expect(spyCalls).toBe(0);
  });

  test('file dropped on the dropzone still triggers the upload flow', async ({ page }) => {
    await page.evaluate(() => {
      window.__ieSpyCalls = 0;
      const orig = window.ieFileSelected;
      window.ieFileSelected = function() {
        window.__ieSpyCalls++;
        return orig ? orig.apply(this, arguments) : undefined;
      };
    });

    await page.evaluate(() => {
      const dt = new DataTransfer();
      const file = new File(['fake'], 'test.png', { type: 'image/png' });
      dt.items.add(file);
      const target = document.getElementById('ieDropzone');
      const ev = new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: dt });
      target.dispatchEvent(ev);
    });

    await page.waitForTimeout(150);
    const spyCalls = await page.evaluate(() => window.__ieSpyCalls);
    expect(spyCalls).toBe(1);
  });

  test('dropping a non-file payload (text) outside the dropzone is left alone', async ({ page }) => {
    const urlBefore = page.url();
    await page.evaluate(() => {
      const dt = new DataTransfer();
      dt.items.add('plain text sample', 'text/plain');
      const target = document.querySelector('.ie-page-subtitle') || document.body;
      const ev = new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: dt });
      target.dispatchEvent(ev);
    });
    await page.waitForTimeout(150);
    expect(page.url()).toBe(urlBefore);
  });

  test('KI-Chat button is present and opens the drawer', async ({ page }) => {
    await page.evaluate(() => kiChatToggle());
    await expect(page.locator('#kiChatDrawer')).toBeVisible();
  });

  test('dropzone is present, focusable and clickable', async ({ page }) => {
    const dz = page.locator('#ieDropzone');
    await expect(dz).toBeVisible();
    await expect(dz).toHaveAttribute('role', 'button');
    await expect(dz).toHaveAttribute('tabindex', '0');
  });

  test('file input accepts the documented image formats', async ({ page }) => {
    const input = page.locator('#ieFileInput');
    await expect(input).toBeAttached();
    const accept = (await input.getAttribute('accept')) ?? '';
    expect(accept).toContain('image/png');
    expect(accept).toContain('image/jpeg');
    expect(accept).toContain('.tif');
  });

  test('file chip is hidden until a file is selected', async ({ page }) => {
    await expect(page.locator('#ieFileChip')).toBeHidden();
  });

  test('OCR engine selector exposes both AI-first and Tesseract options with Tesseract default', async ({ page }) => {
    await expect(page.locator('#ieOcrAiFirst')).toBeAttached();
    await expect(page.locator('#ieOcrTesseract')).toBeAttached();
    const aiChecked = await page.locator('#ieOcrAiFirst').isChecked();
    const tessChecked = await page.locator('#ieOcrTesseract').isChecked();
    expect(aiChecked).toBe(false);
    expect(tessChecked).toBe(true);
  });

  test('extract button is present and disabled until a file is selected', async ({ page }) => {
    const btn = page.locator('#ieBtnExtract');
    await expect(btn).toBeAttached();
    await expect(btn).toBeDisabled();
  });

  test('cancel button is hidden until an extraction is in progress', async ({ page }) => {
    await expect(page.locator('#ieBtnCancel')).toBeHidden();
  });

  test('server-side save path input and browse button are present', async ({ page }) => {
    await expect(page.locator('#ieSavePath')).toBeAttached();
    await expect(page.locator('#ieSavePath')).toBeVisible();
    // Locate the browse button by its click-handler — text is i18n-dependent
    // (de: "Zielordner wählen", en: "Choose target folder") so a text-based
    // locator is brittle across Firefox/Chromium language renders.
    await expect(page.locator('[onclick*="ieOpenSavePathBrowser"]').first())
      .toBeAttached();
  });

  test('error card and result card are hidden initially', async ({ page }) => {
    await expect(page.locator('#ieErrorCard')).toBeHidden();
    await expect(page.locator('#ieResultCard')).toBeHidden();
  });

  test('status pills (loading/done) are present but inactive on page load', async ({ page }) => {
    await expect(page.locator('#ieLoading')).toBeHidden();
    await expect(page.locator('#ieDone')).toBeHidden();
  });

  // ── Theme & layout matrix ────────────────────────────────────────────────

  test('no JavaScript errors on page load', async ({ page }) => {
    const errors = [];
    page.on('pageerror', (err) => errors.push(err.message));
    await page.waitForTimeout(500);
    expect(errors).toHaveLength(0);
  });

  for (const theme of ['modern', 'legacy', 'dark', 'popos']) {
    test(`renders correctly under "${theme}" theme without horizontal overflow`, async ({ page }) => {
      await setThemeAndGoto(page, theme, '/image-extraction');
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth > document.documentElement.clientWidth + 1,
      );
      expect(overflow).toBe(false);
      await expect(page.locator('#sidebar')).toBeVisible();
    });
  }

  // ── REST smoke ───────────────────────────────────────────────────────────

  test('GET /api/image-extraction/history returns a JSON array', async ({ request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/image-extraction/history`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
  });

  test('POST /api/image-extraction/extract rejects a request with no source', async ({ request, baseURL }) => {
    const res = await request.post(`${baseURL}/api/image-extraction/extract`, {
      data: { source: '', engine: 'ai_first' },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('POST /api/image-extraction/extract-upload rejects an empty multipart upload', async ({ request, baseURL }) => {
    const res = await request.post(`${baseURL}/api/image-extraction/extract-upload`, {
      multipart: { file: { name: 'empty.png', mimeType: 'image/png', buffer: Buffer.alloc(0) } },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('GET /api/image-extraction/history/{id} returns 404 for unknown id', async ({ request, baseURL }) => {
    const res = await request.get(`${baseURL}/api/image-extraction/history/99999999`);
    expect(res.status()).toBe(404);
  });
});
