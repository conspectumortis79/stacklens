import { test, expect } from '@playwright/test';
import { goto } from './helpers.js';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';

const TERMINAL = ['COMPLETED', 'FAILED', 'CANCELLED'];
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Start a search through the REST API and wait until it settles.
 *
 * The spec files run in parallel against ONE server, and
 * 36b-delete-all-sessions.spec.js wipes *every* session with
 * `DELETE /api/sessions`. When that bulk delete lands while this spec
 * is polling, `GET /api/search/{id}/status` answers 404 with an empty
 * body and the session we just created is gone — which surfaced as
 * `finalStatus: null` (or a session stuck in RUNNING), even though the
 * search itself behaved perfectly.
 *
 * A session deleted by a sibling spec is not a product failure, so it
 * is treated as retryable: when the status row disappears, the search
 * is started from scratch (up to `attempts` times).
 *
 * `isSettled` defaults to "any terminal status"; pass a predicate to
 * additionally wait for the payload to carry data (e.g. the decoded
 * databaseErrors).
 *
 * Returns `{ sessionId, ...statusBody }`.
 */
async function runSearchUntilSettled(
  request,
  payload,
  {
    isSettled = (j) => TERMINAL.includes(j.status),
    attempts = 3,
    budgetMs = 25_000,
  } = {},
) {
  let lastSeen = null;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    const startRes = await request.post(`${baseUrl}/api/search`, { data: payload });
    expect(startRes.status(), 'POST /api/search').toBeLessThan(400);
    const { sessionId } = await startRes.json();
    expect(sessionId, 'POST /api/search must return a sessionId').toBeGreaterThan(0);

    const deadline = Date.now() + budgetMs;
    let missingReads = 0;
    while (Date.now() < deadline) {
      const res = await request.get(`${baseUrl}/api/search/${sessionId}/status`);
      let body = null;
      try { body = await res.json(); } catch (_) { body = null; }

      if (!body || typeof body.status !== 'string') {
        // 404 + empty body => the session row was deleted underneath us.
        // (Two consecutive reads, so a single transient hiccup does not
        //  throw away an in-flight search.)
        missingReads += 1;
        if (missingReads >= 2) break;
        await sleep(500);
        continue;
      }
      missingReads = 0;
      lastSeen = body;
      if (isSettled(body)) return { sessionId, ...body };
      await sleep(500);
    }

    if (attempt < attempts) {
      console.log(
        `search session ${sessionId} did not settle on attempt ${attempt}/${attempts} `
        + `(last status: ${lastSeen ? lastSeen.status : 'deleted mid-flight'}) — starting over`,
      );
    }
  }
  throw new Error(
    `search did not settle within ${attempts} attempt(s) of ${budgetMs}ms `
    + `— last status: ${lastSeen ? lastSeen.status : 'session wiped before the first read'}, `
    + `last databaseErrors: ${lastSeen && Array.isArray(lastSeen.databaseErrors) ? lastSeen.databaseErrors.length : 'n/a'}`,
  );
}

/**
 * E2E test for the "DB connection failed" banner on the search page.
 *
 * Flow:
 *   1. Create a DatabaseConfig via REST pointing at 127.0.0.1 on a
 *      free loopback port (instant "connection refused" — no JDBC
 *      timeout wait, so the test stays fast and hermetic).
 *   2. Open /search in the browser.
 *   3. Disable every source except Database, type a keyword, submit.
 *   4. Wait until the live-polling banner clears (the session is
 *      terminal) and the DB-error banner becomes visible.
 *   5. Assert the banner shows the config name + a non-empty error
 *      message, and that expanding it reveals a per-config row.
 *   6. Clean up the created DB config.
 *
 * This is the real frontend → backend → frontend round-trip for the
 * databaseErrors JSON payload added in the same feature.
 */
test.describe('Search – DB connection error banner', () => {
  let createdDbConfigId = null;
  let createdDbConfigName = null;

  // Three search attempts (25 s each) plus the banner poll below.
  test.setTimeout(240_000);

  test.afterEach(async ({ request }) => {
    if (createdDbConfigId != null) {
      try {
        await request.delete(`${baseUrl}/api/config/database/${createdDbConfigId}`);
      } catch (_) {
        // ignore — best-effort cleanup
      }
      createdDbConfigId = null;
      createdDbConfigName = null;
    }
  });

  test('banner appears when the only selected DB is unreachable', async ({ page, request }) => {
    // 1. Backend setup — point a DB config at 127.0.0.1:1, a TCP port
    //    nothing is listening on (the tcpmux service from the 1970s
    //    is long dead on every modern OS).  A JDBC connection to that
    //    target returns "Connection refused" within milliseconds — no
    //    need to play the more elaborate `ServerSocket(0)` port-pick
    //    game that we'd then have to close before the search runs.
    const refusedPort = 1;

    createdDbConfigName = `e2e-broken-db-${Date.now()}`;
    const createRes = await request.post(`${baseUrl}/api/config/database`, {
      data: {
        name: createdDbConfigName,
        dbType: 'POSTGRESQL',
        host: '127.0.0.1',
        port: refusedPort,
        databaseName: 'does_not_matter',
        username: 'e2e',
        password: 'e2e',
        schemas: '',
        active: true,
      },
    });
    expect(createRes.status(), 'POST /api/config/database').toBeLessThan(400);
    const created = await createRes.json();
    createdDbConfigId = created.id;
    expect(createdDbConfigId).toBeGreaterThan(0);

    // 2. Kick the search off via the REST endpoint directly.  This is
    //    more robust than driving the search form UI (which depends on
    //    the advanced panel being expanded, on chip click order, and
    //    on the `select all` button auto-selecting every DB — easy to
    //    regress and not what this test is supposed to verify).  The
    //    thing under test is the *banner rendering*, not the form
    //    behaviour.
    const settled = await runSearchUntilSettled(
      request,
      {
        keywords: ['e2e-db-error'],
        gitlabRepositories: [],
        jiraProjects: [],
        jiraComponents: [],
        jiraLabels: [],
        confluenceWebsites: [],
        grafanaPages: [],
        pipelineFilters: [],
        includeGitlab: false,
        includeDatabase: true,
        includePipeline: false,
        includeConfluence: false,
        includeGrafana: false,
        includeJira: false,
        includeContextMap: false,
        crossReferenceEnabled: false,
        gitlabConfigIds: [],
        // Targeted to ONLY the broken config we just created, so the
        // banner count assertion below is unambiguous (the demo
        // seeder also provisions MySQL configs that are unreachable
        // in the e2e environment).
        databaseConfigIds: [createdDbConfigId],
        confluenceConfigIds: [],
        grafanaConfigIds: [],
        jiraConfigIds: [],
        githubConfigIds: [],
      },
      // 3. Wait for the search to terminate.  The session row lives on
      //    the server, so we poll its status until it is COMPLETED *and*
      //    the payload already carries the decoded `databaseErrors` —
      //    that is what unblocks the page-side banner render.
      {
        isSettled: (j) => j.status === 'COMPLETED'
          && Array.isArray(j.databaseErrors) && j.databaseErrors.length > 0,
      },
    );

    const { sessionId } = settled;
    expect(settled.status, 'search status').toBe('COMPLETED');
    expect(
      settled.databaseErrors,
      'search finished COMPLETED but databaseErrors is empty',
    ).not.toHaveLength(0);

    // 4. Open the search page and navigate to the session via the
    //    existing UI flow.  suOpenSession is the same entry point
    //    the live-poll timer uses internally — invoking it from
    //    page.evaluate() exercises the exact same DOM wiring the
    //    live-poll triggers when a real /api/search response comes
    //    back, without us having to wait for the (faked) status
    //    response to land during a 3-second poll tick.
    await goto(page, '/search');
    // Force the UI language to German before the banner renders.
    // The title assertion below expects the German string
    // "Datenbank-Verbindung fehlgeschlagen"; the server-side
    // language config (loaded by js/i18n.js#init) defaults to
    // English, which would leave the title in English and fail the
    // assertion even though the banner itself is correct.
    await page.waitForFunction(() => typeof window.i18n?.setLanguage === 'function', null, { timeout: 15_000 });
    // Wait for the initial i18n bootstrap to settle so our setLanguage
    // call doesn't race with init()'s own /api/config/language fetch.
    await page.waitForFunction(
      () => document.documentElement.lang && document.documentElement.lang.length > 0,
      null,
      { timeout: 15_000 },
    );
    await page.evaluate(() => { try { window.i18n.setLanguage('de'); } catch (_) {} });
    // setLanguage() is async (PUT /api/config/language then
    // loadLanguage()); getLang() flips to 'de' once loadLanguage
    // finishes, so poll until it does.
    await page.waitForFunction(
      () => window.i18n && window.i18n.getLang && window.i18n.getLang() === 'de',
      null,
      { timeout: 15_000 },
    );
    // Clear any session state that a previous test left in sessionStorage,
    // otherwise the page may render the previous session's banner
    // (or not) regardless of what we hand to suOpenSession below.
    await page.evaluate(() => { try { sessionStorage.clear(); } catch (_) {} });
    // Wait for the search page's scripts to attach suOpenSession
    // to the global window.  goto() in helpers.js only waits for
    // the sidebar to be visible, which is faster than script init.
    await page.waitForFunction(() => typeof window.suOpenSession === 'function', null, { timeout: 15_000 });
    // Pass COMPLETED (the actual final status, not RUNNING) so the
    // page doesn't have to wait for the next 3s poll before the
    // banner is rendered. The page still uses the live-poll cadence
    // for subsequent updates; we only short-circuit the initial
    // render here.
    await page.evaluate(
      ([id, kw]) => {
        try { window.suOpenSession(id, kw, 'COMPLETED', false); }
        catch (e) { document.title = 'OPEN_SESSION_ERROR: ' + e.message; }
      },
      [sessionId, 'e2e-db-error'],
    );

    // The page-2 view must become visible and the banner must show
    // up after the status poll lands (≤3s).
    await page.waitForSelector('#suViewResults', { state: 'visible', timeout: 15_000 });
    await expect.poll(
      async () => {
        return await page.evaluate(() => {
          const el = document.getElementById('suDbErrorBanner');
          if (!el) return false;
          return window.getComputedStyle(el).display !== 'none';
        });
      },
      { timeout: 60_000, intervals: [250] },
    ).toBe(true);

    // 5. Assertions on the rendered banner.
    const banner = page.locator('#suDbErrorBanner');
    await expect(banner).toBeVisible();
    // Title must show "1 Datenbank-Verbindung fehlgeschlagen"
    await expect(banner.locator('#suDbErrorCount')).toHaveText('1');
    await expect(banner.locator('.su-db-error-title')).toContainText(
      'Datenbank-Verbindung fehlgeschlagen',
    );

    // Expand the banner by clicking the header, then assert the
    // per-config row is rendered with the right config name and a
    // non-empty error message.
    await banner.locator('.su-db-error-header').click();
    const list = banner.locator('#suDbErrorList');
    await expect(list).toBeVisible();
    const items = list.locator('.su-db-error-item');
    await expect(items).toHaveCount(1);
    const first = items.first();
    await expect(first.locator('.su-db-error-name')).toContainText(createdDbConfigName);
    // POSTGRESQL badge
    await expect(first.locator('.su-db-error-type-badge')).toHaveText('POSTGRESQL');
    // host / database
    await expect(first.locator('.su-db-error-target')).toContainText('127.0.0.1');
    await expect(first.locator('.su-db-error-target')).toContainText('does_not_matter');
    // The actual error message must be non-empty (the JDBC layer
    // always produces something for "Connection refused").
    const errMsg = (await first.locator('.su-db-error-msg').innerText()).trim();
    expect(errMsg.length).toBeGreaterThan(0);
  });

  test('banner stays hidden when the search uses no databases', async ({ page, request }) => {
    // Sanity check: with includeDatabase=false, no banner should ever
    // appear, even if a previous session's banner is still in the DOM.
    // Same approach as the first test: drive the search via REST, then
    // open the session in the browser to verify the banner stays
    // hidden.
    const settled = await runSearchUntilSettled(
      request,
      {
        keywords: ['e2e-no-db-banner'],
        gitlabRepositories: [],
        jiraProjects: [],
        jiraComponents: [],
        jiraLabels: [],
        confluenceWebsites: [],
        grafanaPages: [],
        pipelineFilters: [],
        includeGitlab: false,
        includeDatabase: false,
        includePipeline: false,
        includeConfluence: false,
        includeGrafana: false,
        includeJira: false,
        includeContextMap: false,
        crossReferenceEnabled: false,
        gitlabConfigIds: [],
        databaseConfigIds: [],
        confluenceConfigIds: [],
        grafanaConfigIds: [],
        jiraConfigIds: [],
        githubConfigIds: [],
      },
    );
    const { sessionId } = settled;
    expect(settled.status, 'search status').toBe('COMPLETED');

    // Open in browser and check the banner stays hidden.
    await goto(page, '/search');
    await page.waitForFunction(() => typeof window.suOpenSession === 'function', null, { timeout: 15_000 });
    await page.evaluate(
      ([id, kw]) => {
        window.suOpenSession(id, kw, 'RUNNING', false);
      },
      [sessionId, 'e2e-no-db-banner'],
    );
    await page.waitForSelector('#suViewResults', { state: 'visible', timeout: 15_000 });
    // Wait long enough for several status polls to land (3s each).
    await page.waitForTimeout(7000);
    const banner = page.locator('#suDbErrorBanner');
    const visible = await banner.isVisible().catch(() => false);
    expect(visible).toBe(false);
  });
});
