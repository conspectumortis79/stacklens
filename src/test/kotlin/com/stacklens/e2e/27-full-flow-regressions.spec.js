import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

const DEMO_MR_URL = 'http://localhost:3080/demo-group/ecommerce-api/-/merge_requests/1';

function statusDoneRegex() {
  return /Completed|Abgeschlossen/i;
}

test.describe.serial('Full Flow Regressions (Points 1-4)', () => {
  test.beforeEach(async ({ context, page }) => {
    await context.route('**/api/ui-prefs', (route) => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ dev_docker_test_env: 'true' }),
    }));
    await page.addInitScript(() => {
      try { localStorage.setItem('dev_docker_test_env', 'true'); } catch (_) {}
    });
  });
  test('Point 1: Code Analysis full flow including MR creation', async ({ page, context }) => {
    test.setTimeout(2 * 60 * 1000);

    let statusPolls = 0;
    let createdMrUrl = null;

    await context.route('**/api/local-repos/gitlab/configs', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([{ id: 1, name: 'Demo GitLab' }]),
    }));

    await context.route('**/api/local-repos/gitlab/1/projects', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([{ id: 7, name: 'ecommerce-api', path: 'demo-group/ecommerce-api', httpUrl: 'http://localhost:3080/demo-group/ecommerce-api' }]),
    }));

    await context.route('**/api/code-analysis/gitlab/1/project/7/latest', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ status: 'NONE' }),
    }));

    await context.route('**/api/code-analysis/start-gitlab', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ analysisId: 900, status: 'RUNNING' }),
    }));

    await context.route('**/api/code-analysis/analysis/900/status', route => {
      statusPolls += 1;
      const done = statusPolls >= 2;
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          id: 900,
          status: done ? 'COMPLETED' : 'RUNNING',
          summaryText: 'Mocked code-analysis summary',
          findingsJson: done ? JSON.stringify([{ type: 'HEXAGONAL', severity: 'HIGH', file: 'a.kt', description: 'x' }]) : null,
          fileChangesJson: done ? JSON.stringify([{ path: 'a.kt', changeType: 'UPDATE', code: 'fun x() = 1' }]) : null,
          gitlabConfigId: 1,
          gitlabProjectId: 7,
          mrUrl: null,
        }),
      });
    });

    await context.route('**/api/code-analysis/all', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([{ id: 900, status: statusPolls >= 2 ? 'COMPLETED' : 'RUNNING', gitlabProjectPath: 'demo-group/ecommerce-api' }]),
    }));

    await context.route('**/api/code-analysis/analysis/900/create-mr', route => {
      createdMrUrl = 'http://localhost:3080/demo-group/ecommerce-api/-/merge_requests/900';
      route.fulfill({ contentType: 'application/json', body: JSON.stringify({ mrUrl: createdMrUrl }) });
    });

    await context.route('http://localhost:3080/**', route => route.fulfill({
      status: 200,
      contentType: 'text/html',
      body: '<html><head><title>Merge Request Mock</title></head><body>MR mock page</body></html>',
    }));

    await goto(page, '/code-analysis');
    const gitlabHeader = page.locator('.ca-gitlab-header:has-text("Demo GitLab")');
    await gitlabHeader.scrollIntoViewIfNeeded();
    await gitlabHeader.click();

    const repoCard = page.locator('.ca-repo-card:has-text("ecommerce-api")').first();
    await expect(repoCard).toBeVisible({ timeout: 30_000 });
    await repoCard.locator('button[data-action="gl-play"]').click();

    await expect(page.locator('#analysisStatusBadge')).toContainText(statusDoneRegex(), { timeout: 60_000 });

    const mrCreateBtn = page.locator(
      '#caMrBanner button[onclick*="caOpenMrDialog"]:visible, #caMrSection button[onclick*="caOpenMrDialog"]:visible'
    ).first();
    await expect(mrCreateBtn).toBeVisible({ timeout: 30_000 });
    await mrCreateBtn.click();

    await expect.poll(async () => createdMrUrl).toContain('/merge_requests/');

    await page.goto(createdMrUrl);
    await expect(page).toHaveTitle(/Merge Request Mock/i, { timeout: 30_000 });
  });

  test('Point 2: Docker restart button refreshes view when Docker becomes available', async ({ page, context }) => {
    test.setTimeout(2 * 60 * 1000);

    let infoCalls = 0;

    await context.route('**/api/docker-test/info**', (route) => {
      infoCalls += 1;
      const dockerUp = infoCalls >= 4;
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          composeFile: '/tmp/docker-compose.yml',
          found: 'true',
          dockerAvailable: dockerUp ? 'true' : 'false',
          dockerDiagnostic: dockerUp ? '' : 'Cannot connect to Docker daemon',
        }),
      });
    });

    await context.route('**/api/docker-test/state**', (route) => {
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ status: 'idle', jobId: null, output: [] }),
      });
    });

    await context.route('**/api/docker-test/restart-docker', (route) => {
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          ok: 'true',
          dockerAvailable: 'false',
          dockerDiagnostic: 'starting',
          message: 'Docker restart initiated',
          details: 'test',
        }),
      });
    });

    await goto(page, '/docker-test');

    await page.evaluate(() => {
      window.dtStart = function () {};
    });

    const notAvailable = page.locator('#dtDockerNotAvailable');
    await expect(notAvailable).toBeVisible({ timeout: 30_000 });

    const restartBtn = page.locator('#dtRestartDockerBtn');
    await expect(restartBtn).toBeVisible();
    await restartBtn.click();

    await expect(notAvailable).toBeHidden({ timeout: 30_000 });
    await expect(page.locator('#dtStartBtn')).toBeEnabled({ timeout: 30_000 });
  });

  test('Point 3: Code QA full flow from demo MR URL to result and history', async ({ page, context }) => {
    test.setTimeout(2 * 60 * 1000);

    let statusPolls = 0;

    await page.addInitScript(() => {
      class SilentEventSource {
        addEventListener() {}
        close() {}
      }
      window.EventSource = SilentEventSource;
    });

    await context.route('**/api/mr-analysis', (route) => {
      const req = route.request();
      if (req.method() === 'POST') {
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ id: 700, status: 'RUNNING' }) });
      }
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([{ id: 700, mrUrl: DEMO_MR_URL, jiraTicketKey: 'ECOM-42', status: 'COMPLETED', createdAt: '2026-05-27T19:30:00' }]),
      });
    });

    await context.route('**/api/mr-analysis/700/status', route => {
      statusPolls += 1;
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ id: 700, status: statusPolls >= 2 ? 'COMPLETED' : 'RUNNING', currentStep: 'step', logOutput: 'log' }),
      });
    });

    await context.route('**/api/mr-analysis/700', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        id: 700,
        status: 'COMPLETED',
        mrUrl: DEMO_MR_URL,
        resultMarkdown: '## Analyse\nAlles gut',
        integrationTestEnabled: false,
      }),
    }));

    await goto(page, '/code-qa');

    await page.locator('#mrInputUrl').fill(DEMO_MR_URL);
    await page.locator('#mrBtnStart').click();

    await expect(page.locator('#mrProgressWrap')).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('#mrResultCard')).toBeVisible({ timeout: 60_000 });
    await expect(page.locator('#mrMarkdownBody')).toContainText('Analyse');
    await expect.poll(async () => await page.locator('#hxCard-code-qa tbody tr.hx-row').count(), { timeout: 60_000 }).toBeGreaterThan(0);
  });

  test('Point 4: Code Analysis history load keeps MR action path usable', async ({ page, context }) => {
    test.setTimeout(2 * 60 * 1000);

    const historyItem = {
      id: 901,
      status: 'COMPLETED',
      gitlabConfigId: 1,
      gitlabProjectId: 7,
      gitlabProjectPath: 'demo-group/ecommerce-api',
      fileChangesJson: JSON.stringify([{ path: 'x.kt', changeType: 'UPDATE', code: 'val x = 1' }]),
      findingsJson: JSON.stringify([]),
      summaryText: 'history summary',
      mrUrl: null,
      createdAt: '2026-05-27T19:30:00',
    };

    await context.route('**/api/local-repos/gitlab/configs', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([{ id: 1, name: 'Demo GitLab' }]),
    }));

    await context.route('**/api/local-repos/gitlab/1/projects', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([{ id: 7, name: 'ecommerce-api', path: 'demo-group/ecommerce-api' }]),
    }));

    await context.route('**/api/code-analysis/all', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([historyItem]),
    }));

    await context.route('**/api/code-analysis/gitlab/1/project/7/latest', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ status: 'NONE' }),
    }));

    await context.route('**/api/code-analysis/analysis/901/status', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(historyItem),
    }));

    await context.route('**/api/code-analysis/analysis/901/create-mr', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ mrUrl: 'http://localhost:3080/demo-group/ecommerce-api/-/merge_requests/901' }),
    }));

    await goto(page, '/code-analysis');

    const gitlabHeader = page.locator('.ca-gitlab-header:has-text("Demo GitLab")');
    await gitlabHeader.scrollIntoViewIfNeeded();
    await gitlabHeader.click();
    const repoCard = page.locator('.ca-repo-card:has-text("ecommerce-api")').first();
    await expect(repoCard).toBeVisible({ timeout: 30_000 });
    await repoCard.click();

    const historyLoadBtn = page.locator('#hxCard-code-analysis tbody [data-hx-act="open"]').first();
    await expect(historyLoadBtn).toBeVisible({ timeout: 30_000 });
    await historyLoadBtn.click();

    const mrCreateBtn = page.locator(
      '#caMrBanner button[onclick*="caOpenMrDialog"]:visible, #caMrSection button[onclick*="caOpenMrDialog"]:visible'
    ).first();
    await expect(mrCreateBtn).toBeVisible({ timeout: 30_000 });
    await mrCreateBtn.click();

    await expect.poll(async () => await page.locator('#caMrLink').getAttribute('href'), { timeout: 30_000 }).toContain('/merge_requests/');
  });
});
