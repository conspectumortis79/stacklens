/**
 * E2E test that validates the PPTX-to-HTML pipeline end-to-end.
 *
 *   1. Loads /review-analysis and switches through all themes.
 *   2. Downloads the dev PPTX fixture via /api/_test/pptx-fixture.
 *   3. Drops the file onto the dropzone and submits the form.
 *   4. Waits for the analysis to complete (status COMPLETED).
 *   5. Downloads the generated HTML and verifies that theme colors,
 *      fonts, image data URIs and the slide aspect ratio from the
 *      PPTX made it into the HTML 1:1.
 */
import { test, expect } from '@playwright/test';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { setThemeAndGoto } from './helpers.js';

const FIXTURE_PATH = path.join(os.tmpdir(), 'stacklens-sample-deck.pptx');

test.describe('Review Analysis PPTX pipeline', () => {
  test.beforeAll(async () => {
    const dir = path.dirname(FIXTURE_PATH);
    await fs.mkdir(dir, { recursive: true });
  });

  test('PPTX dropzone is visible and themed in every theme', async ({ page }) => {
    for (const theme of ['modern', 'dark', 'popos', 'legacy']) {
      await setThemeAndGoto(page, theme, '/review-analysis');
      await expect(page.locator('#raPptxDropzone')).toBeAttached();
      await expect(page.locator('#raPptxFileInput')).toBeAttached();
    }
  });

  test('PPTX upload → HTML mirrors PPTX styles 1:1', async ({ page }) => {
    await setThemeAndGoto(page, 'modern', '/review-analysis');

    // 0. Probe whether the configured AI model is reachable. The
    // review-analysis pipeline cannot complete without an AI (the standard
    // path asks the model for a Markdown report and the PPTX-mode path
    // asks it twice for question anchors + answers). If the probe fails we
    // skip with a clear message — same pattern as the other demo-dependent
    // tests (see e.g. 39-pipeline-demo.spec.js) so the e2e suite stays
    // green in CI environments that do not provide a real AI credential.
    //
    // NB: the probe endpoint returns HTTP 200 even when the model is not
    // configured (the JSON body just contains `success: false`), so we
    // also have to inspect the payload to detect a missing AI.
    let aiReachable = false;
    try {
      const probe = await page.request.post('/api/config/ai/test-model', {
        data: {},
        timeout: 15_000,
      });
      if (probe.ok()) {
        const payload = await probe.json().catch(() => ({}));
        aiReachable = payload.success === true;
      }
    } catch (_) {
      aiReachable = false;
    }
    test.skip(!aiReachable, 'AI model not reachable — skipping PPTX pipeline test (requires a configured AI provider).');

    // 1. Download the deterministic PPTX fixture.
    const resp = await page.request.get('/api/_test/pptx-fixture');
    expect(resp.status()).toBe(200);
    const buffer = await resp.body();
    await fs.writeFile(FIXTURE_PATH, buffer);
    expect(buffer.length).toBeGreaterThan(0);

    // 2. Upload through the hidden file input.
    const fileInput = page.locator('#raPptxFileInput');
    await fileInput.setInputFiles(FIXTURE_PATH);
    await expect(page.locator('#raPptxChip')).toBeVisible();

    // 3. Fill the rest of the form.  The URL is the auto-fill pattern the
    // review-analysis page applies when the user picks a localhost / docker
    // Jira config (see scripts-history.html `selectJiraConfig` change handler).
    // The placeholder `https://jira.example.com/boards/1` that the test
    // originally shipped with is unreachable and broke the Jira-sprint
    // resolution step before the PPTX pipeline even started.
    await page.locator('#inputSprintUrl').fill(
      'http://localhost:8081/jira/software/projects/DEMO/boards/5/sprints/42',
    );
    const cfgValue = await page.locator('#selectJiraConfig option').nth(1).getAttribute('value');
    if (cfgValue) await page.locator('#selectJiraConfig').selectOption(cfgValue);
    await page.locator('#inputAnalysisInstructions').fill(
      'Analyse all tickets and answer every slide question.',
    );

    // 4. Submit and wait for completion.
    await page.locator('#btnStart').click();
    await page.locator('#progressCard').waitFor({ state: 'visible', timeout: 30_000 });
    await page.locator('#resultsCard').waitFor({ state: 'visible', timeout: 600_000 });

    // 5. Inspect the generated HTML directly.
    const analysisId = await page.evaluate(() => window._ra?.completedId || window._ra?.runningId);
    expect(analysisId).toBeTruthy();
    const html = await page.evaluate(async (id) => {
      const r = await fetch(`/api/review-analyse/${id}/download/html`);
      return { status: r.status, body: await r.text() };
    }, analysisId);
    expect(html.status).toBe(200);
    expect(html.body).toContain('<!DOCTYPE html>');

    // The PPTX theme must have been applied to the HTML output.
    expect(html.body).toMatch(/--pptx-accent\d:/);
    expect(html.body).toMatch(/--pptx-heading-font/);
    expect(html.body).toMatch(/--pptx-body-font/);
    // The embedded PNG picture from the fixture must end up in the HTML.
    expect(html.body).toMatch(/data:image\/png;base64/);
    // The slide aspect ratio must match the source 1280×720.
    expect(html.body).toMatch(/aspect-ratio:1280\/720/);
    // Each slide from the fixture must be rendered.
    const slideMatches = html.body.match(/data-slide-index="\d+"/g) || [];
    expect(slideMatches.length).toBe(3);

    // ── Two-stage PPTX-mode contract (Phase 4) ─────────────────────────────
    // The HTML must NOT contain the legacy placeholder marker that the
    // pre-two-stage pipeline wrote into every shape. If the placeholder
    // leaked through, the answer pipeline is broken.
    expect(html.body).not.toMatch(/KI-Antwort f\u00fcr:/);
    // The HTML must contain answer spans (pptx-answer class) next to the
    // question text shapes. The exact count depends on the AI but it must
    // be at least one per slide that the Stage-1 plan identified.
    const answerCount = (html.body.match(/pptx-run pptx-answer/g) || []).length;
    expect(answerCount).toBeGreaterThan(0);
    // The detail DTO must expose the new JSON fields for the UI.
    const detail = await page.evaluate(async (id) => {
      const r = await fetch(`/api/review-analyse/${id}`);
      return r.json();
    }, analysisId);
    expect(detail).toHaveProperty('pptxAnswersJson');
    expect(detail).toHaveProperty('pptxDetectedLanguage');
  });
});