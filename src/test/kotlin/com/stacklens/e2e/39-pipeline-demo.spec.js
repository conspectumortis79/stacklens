import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

/**
 * Pipeline Analysis – demo dataset smoke test.
 *
 * Verifies that when the demo GitLab mock is running on `localhost:3080`
 * (i.e. when `docker compose up -d fake-gitlab` has been executed), the
 * "Demo GitLab" connection exposes the new `pipeline-demo` project with
 * the expected pipeline / compose files.
 *
 * The test is conditional: it skips when the demo backend is unreachable so
 * the regular CI suite (which does not boot the fake-gitlab) still passes.
 */
test.describe('Pipeline Analysis – Demo dataset', () => {

  test.beforeEach(async ({ page }) => {
    await goto(page, '/pipeline-analysis');
  });

  test('GitLab panel exposes the pipeline-demo project when demo backend is running', async ({ page }) => {
    // Probe the API the page uses to discover GitLab projects.
    let projects = [];
    try {
      const resp = await page.request.get('http://localhost:3080/api/v4/projects');
      if (!resp.ok()) test.skip(true, 'fake-gitlab is not reachable on localhost:3080 – start it via docker compose up -d fake-gitlab.');
      projects = await resp.json();
    } catch (e) {
      test.skip(true, 'fake-gitlab unreachable – skipping demo dataset assertion.');
    }

    const pipelineDemo = projects.find((p) => p.path_with_namespace === 'demo-group/pipeline-demo');
    expect(pipelineDemo).toBeTruthy();
    expect(pipelineDemo.id).toBeGreaterThan(0);
  });

  test('pipeline-demo tree contains the headline compose + CI files', async ({ page }) => {
    let tree = [];
    try {
      const resp = await page.request.get('http://localhost:3080/api/v4/projects/8/repository/tree');
      if (!resp.ok()) test.skip(true, 'pipeline-demo project not reachable.');
      tree = await resp.json();
    } catch (e) {
      test.skip(true, 'fake-gitlab unreachable – skipping tree assertion.');
    }
    const paths = tree.map((e) => e.path);
    expect(paths).toContain('docker-compose.yml');
    expect(paths).toContain('Dockerfile');
    expect(paths).toContain('.gitlab-ci.yml');
    expect(paths).toContain('.github/workflows/ci.yml');
    expect(paths).toContain('Jenkinsfile');
  });

  test('pipeline-analysis page does not throw JS errors when demo GitLab is reachable', async ({ page }) => {
    // The page polls /api/local-repos/gitlab/configs and then /api/local-repos/gitlab/{id}/projects.
    // Both must succeed (or at least not produce JS errors) for the UI to render the panel.
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.reload();
    await page.waitForSelector('[class*="pa-"]', { state: 'attached' });
    expect(errors).toHaveLength(0);
  });
});
