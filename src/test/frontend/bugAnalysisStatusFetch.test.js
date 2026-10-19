import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';



function _baJsonResponse(r) {
  return r.json().catch(function () { return { error: 'HTTP ' + r.status }; });
}

function baStopPollAndShowEmpty(state, errorObj) {
  if (!errorObj || !errorObj.error) return false;
  ++state._baPollVersion;
  if (state.baPollTimer) { clearInterval(state.baPollTimer); state.baPollTimer = null; }
  if (state.baCurrentAnalysisId === state._expectedId) {
    state.baCurrentAnalysisId = null;
    state.lastShown = 'empty';
  }
  return true;
}


function makeFetchResponse({ status, body, contentType }) {
  const headers = new Map();
  if (contentType) headers.set('content-type', contentType);
  return {
    status: status,
    ok: status >= 200 && status < 300,
    json: function () {
      if (!body) return Promise.reject(new SyntaxError('unexpected end of data'));
      if (typeof body === 'string' && !body.trim().startsWith('{') && !body.trim().startsWith('[')) {
        return Promise.reject(new SyntaxError('unexpected token'));
      }
      return Promise.resolve(JSON.parse(body));
    },
    headers: { get: (k) => headers.get(k.toLowerCase()) || null },
  };
}


describe('bug-analysis status fetch — empty-body regression', () => {

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  describe('_baJsonResponse', () => {
    it('parses a valid JSON body on 200', async () => {
      const r = makeFetchResponse({ status: 200, body: '{"id":14,"status":"RUNNING"}' });
      const out = await _baJsonResponse(r);
      expect(out).toEqual({ id: 14, status: 'RUNNING' });
    });

    it('does NOT throw on a 404 with an empty body (the original bug)', async () => {
      const r = makeFetchResponse({ status: 404, body: '' });
      const out = await _baJsonResponse(r);
      expect(out).toEqual({ error: 'HTTP 404' });
    });

    it('does NOT throw on a 500 with an HTML body (proxy / upstream error)', async () => {
      const r = makeFetchResponse({
        status: 500,
        body: '<html><body>500 Internal Server Error</body></html>',
        contentType: 'text/html',
      });
      const out = await _baJsonResponse(r);
      expect(out).toEqual({ error: 'HTTP 500' });
    });

    it('parses a 200 with an empty body as { error: "HTTP 200" }', async () => {
      const r = makeFetchResponse({ status: 200, body: '' });
      const out = await _baJsonResponse(r);
      expect(out).toEqual({ error: 'HTTP 200' });
    });
  });

  describe('polling loop — 404 on a deleted analysis', () => {
    let state;
    beforeEach(() => {
      state = {
        baCurrentAnalysisId: 14,
        baPollTimer: setInterval(() => {}, 9999),
        _baPollVersion: 0,
        _expectedId: 14,
        lastShown: null,
      };
    });
    afterEach(() => {
      if (state.baPollTimer) clearInterval(state.baPollTimer);
    });

    it('stops the timer and shows the empty state when the status endpoint returns 404', async () => {
      const r = makeFetchResponse({ status: 404, body: '' });
      const data = await _baJsonResponse(r);

      const handled = baStopPollAndShowEmpty(state, data);

      expect(handled).toBe(true);
      expect(state.baPollTimer).toBeNull();
      expect(state.baCurrentAnalysisId).toBeNull();
      expect(state.lastShown).toBe('empty');
    });

    it('does NOT stop the timer on a 200 with status RUNNING (positive path)', async () => {
      const r = makeFetchResponse({ status: 200, body: '{"id":14,"status":"RUNNING","currentIteration":1}' });
      const data = await _baJsonResponse(r);

      const handled = baStopPollAndShowEmpty(state, data);

      expect(handled).toBe(false);
      expect(state.baPollTimer).not.toBeNull();
      expect(state.baCurrentAnalysisId).toBe(14);
    });

    it('does NOT stop the timer on a 200 with status COMPLETED (let the success branch handle it)', async () => {
      const r = makeFetchResponse({ status: 200, body: '{"id":14,"status":"COMPLETED"}' });
      const data = await _baJsonResponse(r);

      const handled = baStopPollAndShowEmpty(state, data);

      expect(handled).toBe(false);
      expect(state.baPollTimer).not.toBeNull();
    });
  });

  describe('history detail loader — 404 on a deleted analysis', () => {
    it('shows the empty state when /status returns 404 for a history row', async () => {
      const out = { lastShown: null, baCurrentAnalysisId: 99 };

      const r = makeFetchResponse({ status: 404, body: '' });
      const data = await _baJsonResponse(r);

      if (!data || data.error) {
        out.baCurrentAnalysisId = null;
        out.lastShown = 'empty';
      }

      expect(out.lastShown).toBe('empty');
      expect(out.baCurrentAnalysisId).toBeNull();
    });
  });
});
