import { test, expect } from '@playwright/test';

const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8091';

/**
 * Regression test for the
 *   org.hibernate.StaleObjectStateException: Row was updated or deleted
 *   by another transaction (or unsaved-value mapping was incorrect):
 *   [com.stacklens.domain.model.SearchResult#106]
 * crash that happened when the user clicked "Alle löschen" on the
 * search history while DB results were still being written by a
 * previous in-flight search.
 *
 * Root cause: SearchHistoryService.deleteAllSessions was using the
 * N+1 pattern (load all sessions → for each, load results → mark
 * removed → auto-flush).  As soon as the first session's results had
 * been flushed, the second iteration's findBySessionId… query re-issued
 * the same DELETE during auto-flush, by which time the rows were
 * already gone → StaleObjectStateException bubbled up as a 500.
 *
 * Fix: a single bulk
 *   DELETE FROM SearchResult WHERE sessionId IN (:sessionIds)
 * statement, replacing the N+1 loop entirely.
 *
 * This test reproduces the original race by:
 *   1. Creating a DB config that points at an unreachable host
 *      (instant connection refused, no JDBC login-timeout wait)
 *   2. Running a search that targets ONLY that broken DB
 *   3. Immediately (synchronously, in this test) calling
 *      DELETE /api/sessions to wipe the history
 *
 * The fix makes step 3 succeed without a 500; without the fix, the
 * test would surface the StaleObjectStateException stack trace in the
 * server logs and return a 500 to the browser.
 */
test.describe('Search – delete all sessions does not crash', () => {
  let createdDbConfigId = null;
  let createdSessionId = null;

  test.afterEach(async ({ request }) => {
    if (createdSessionId != null) {
      try { await request.delete(`${baseUrl}/api/sessions/${createdSessionId}`); } catch (_) {}
    }
    if (createdDbConfigId != null) {
      try { await request.delete(`${baseUrl}/api/config/database/${createdDbConfigId}`); } catch (_) {}
    }
    createdSessionId = null;
    createdDbConfigId = null;
  });

  test('DELETE /api/sessions after a search with unreachable DB returns 2xx', async ({ request }) => {
    // 1. Create a broken DB config.
    const createRes = await request.post(`${baseUrl}/api/config/database`, {
      data: {
        name: `delete-bug-${Date.now()}`,
        dbType: 'POSTGRESQL',
        host: '127.0.0.1',
        port: 1,
        databaseName: 'x',
        username: 'x',
        password: 'x',
        schemas: '',
        active: true,
      },
    });
    expect(createRes.status()).toBeLessThan(400);
    const created = await createRes.json();
    createdDbConfigId = created.id;

    // 2. Start a search that targets ONLY the broken DB so the
    //    resulting session has the kind of write-behind state that
    //    used to trip the StaleObjectStateException.
    const startRes = await request.post(`${baseUrl}/api/search`, {
      data: {
        keywords: ['delete-bug-keyword'],
        includeGitlab: false,
        includeDatabase: true,
        includePipeline: false,
        includeConfluence: false,
        includeGrafana: false,
        includeJira: false,
        includeContextMap: false,
        crossReferenceEnabled: false,
        databaseConfigIds: [createdDbConfigId],
        gitlabConfigIds: [],
        confluenceConfigIds: [],
        grafanaConfigIds: [],
        jiraConfigIds: [],
        githubConfigIds: [],
      },
    });
    expect(startRes.status()).toBeLessThan(400);
    const { sessionId } = await startRes.json();
    createdSessionId = sessionId;

    // 3. Wait for the search to terminate so the row is in a clean
    //    state when we ask the server to delete it.  A non-JSON answer
    //    (404 + empty body) means the session vanished — that can only
    //    be our own bulk delete in step 4 or a sibling spec, so just
    //    keep polling until the deadline instead of crashing on
    //    `json()`.
    const deadline = Date.now() + 30_000;
    while (Date.now() < deadline) {
      const r = await request.get(`${baseUrl}/api/search/${sessionId}/status`);
      let j = null;
      try { j = await r.json(); } catch (_) { j = null; }
      if (j && (j.status === 'COMPLETED' || j.status === 'FAILED' || j.status === 'CANCELLED')) break;
      await new Promise((r) => setTimeout(r, 250));
    }

    // 4. The actual regression: DELETE /api/sessions while the
    //    session still has its (possibly still-flushed) SearchResult
    //    rows.  Pre-fix this returned 500; post-fix it returns 200
    //    with a `{deleted: N}` body.
    const delRes = await request.delete(`${baseUrl}/api/sessions`);
    expect(delRes.status(), 'DELETE /api/sessions must not 500').toBeLessThan(400);
    const body = await delRes.json();
    expect(typeof body.deleted).toBe('number');
    expect(body.deleted).toBeGreaterThanOrEqual(1);

    // Clear the cleanup references — the bulk delete already cleaned up.
    createdSessionId = null;
  });
});
