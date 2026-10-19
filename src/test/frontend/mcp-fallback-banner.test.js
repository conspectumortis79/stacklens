import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  loadScriptIsolated, installBrowserStubs, resetDom,
  flushMicrotasks, makeJsonResponse, makeErrorResponse,
  untrackDocumentListeners
} from './_load.js';

installBrowserStubs();

function setFetch(responses) {
  const calls = [];
  let next = 0;
  globalThis.fetch = vi.fn((url, init) => {
    calls.push({ url: String(url), init: init || null });
    const r = responses[next++];
    if (!r) return Promise.reject(new Error('no mock for ' + url));
    return Promise.resolve(r);
  });
  return calls;
}

function makeBanner({ visible = false } = {}) {
  document.body.innerHTML =
    '<div id="mcpFallbackBanner" class="' + (visible ? '' : 'd-none') + '"><span id="mcpFallbackBannerText"></span></div>';
  return {
    banner: document.getElementById('mcpFallbackBanner'),
    text: document.getElementById('mcpFallbackBannerText')
  };
}

afterEach(() => {
  untrackDocumentListeners();
  vi.useRealTimers();
  if (window._appVisible === false) window._appVisible = true;
});

describe('mcp-fallback-banner', () => {
  describe('init prerequisites', () => {
    it('does nothing when the banner elements are missing from the DOM', async () => {
      resetDom();
      document.body.innerHTML = '<div></div>';
      const calls = setFetch([makeJsonResponse({ circuits: { jira: { isOpen: true } } })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(calls).toHaveLength(0);
    });

    it('immediately polls once on init, then schedules a 30 s interval', async () => {
      vi.useFakeTimers();
      resetDom();
      const { banner } = makeBanner();
      const calls = setFetch([
        makeJsonResponse({ circuits: {} }),
        makeJsonResponse({ circuits: {} })
      ]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await vi.advanceTimersByTimeAsync(0);
      expect(calls.length).toBeGreaterThanOrEqual(1);
      const callCountAfterImmediate = calls.length;
      vi.advanceTimersByTime(30000);
      await vi.advanceTimersByTimeAsync(0);
      expect(calls.length).toBeGreaterThan(callCountAfterImmediate);
      expect(banner.classList.contains('d-none')).toBe(true);
    });
  });

  describe('banner text construction (observed via DOM)', () => {
    it('uses the human provider label in the banner text', async () => {
      resetDom();
      const { text } = makeBanner();
      setFetch([makeJsonResponse({
        mcpServerName: 'srv',
        circuits: { jira: { provider: 'jira', isOpen: true, probing: false } }
      })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(text.textContent).toContain('Jira');
    });

    it('includes the MCP server name in parentheses', async () => {
      resetDom();
      const { text } = makeBanner();
      setFetch([makeJsonResponse({
        mcpServerName: 'mcp-prod',
        circuits: { jira: { provider: 'jira', isOpen: true, probing: false } }
      })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(text.textContent).toMatch(/MCP server \(mcp-prod\) unreachable for: Jira/);
    });

    it('omits the server name when the API does not return one', async () => {
      resetDom();
      const { text } = makeBanner();
      setFetch([makeJsonResponse({
        circuits: { jira: { provider: 'jira', isOpen: true, probing: false } }
      })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(text.textContent).toMatch(/^MCP server unreachable for: Jira\./);
    });

    it('lists multiple providers sorted and deduped', async () => {
      resetDom();
      const { text } = makeBanner();
      setFetch([makeJsonResponse({
        circuits: {
          a: { provider: 'github', isOpen: true },
          b: { provider: 'jira', isOpen: true },
          c: { provider: 'github', isOpen: true },
          d: { provider: 'gitlab', isOpen: true }
        }
      })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(text.textContent).toMatch(/GitHub, GitLab, Jira/);
    });

    it('passes through unknown providers using the raw name', async () => {
      resetDom();
      const { text } = makeBanner();
      setFetch([makeJsonResponse({
        circuits: { a: { provider: 'prometheus', isOpen: true } }
      })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(text.textContent).toContain('prometheus');
    });

    it('uses probing copy when at least one circuit is probing', async () => {
      resetDom();
      const { text } = makeBanner();
      setFetch([makeJsonResponse({
        circuits: { a: { provider: 'jira', isOpen: true, probing: true } }
      })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(text.textContent).toContain('Retrying in the background');
      expect(text.textContent).not.toContain('Next probe in up to 60s');
    });

    it('uses cooldown copy when no circuit is probing', async () => {
      resetDom();
      const { text } = makeBanner();
      setFetch([makeJsonResponse({
        circuits: {
          a: { provider: 'jira', isOpen: true, probing: false },
          b: { provider: 'gitlab', isOpen: true, probing: false }
        }
      })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(text.textContent).toContain('Next probe in up to 60s');
      expect(text.textContent).toContain('Native fallback is active');
    });
  });

  describe('circuit detection', () => {
    it('hides the banner when no circuit is open', async () => {
      resetDom();
      const { banner } = makeBanner({ visible: true });
      setFetch([makeJsonResponse({ circuits: {} })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(banner.classList.contains('d-none')).toBe(true);
    });

    it('ignores circuits whose isOpen flag is false', async () => {
      resetDom();
      const { banner } = makeBanner({ visible: true });
      setFetch([makeJsonResponse({ circuits: { jira: { provider: 'jira', isOpen: false } } })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(banner.classList.contains('d-none')).toBe(true);
    });

    it('shows the banner when at least one circuit is open', async () => {
      resetDom();
      const { banner } = makeBanner();
      setFetch([makeJsonResponse({
        circuits: { jira: { provider: 'jira', isOpen: true } }
      })]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(banner.classList.contains('d-none')).toBe(false);
    });

    it('leaves the banner state unchanged when the fetch itself rejects', async () => {
      resetDom();
      const { banner } = makeBanner({ visible: true });
      globalThis.fetch = vi.fn(() => Promise.reject(new Error('network down')));
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(banner.classList.contains('d-none')).toBe(false);
    });

    it('hides the banner on the next successful poll with no open circuits after a fetch error', async () => {
      vi.useFakeTimers();
      resetDom();
      const { banner } = makeBanner({ visible: true });
      globalThis.fetch = vi.fn()
        .mockImplementationOnce(() => Promise.reject(new Error('boom')))
        .mockImplementationOnce(() => Promise.resolve(makeJsonResponse({ circuits: {} })));
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await vi.advanceTimersByTimeAsync(0);
      expect(banner.classList.contains('d-none')).toBe(false);
      vi.advanceTimersByTime(30000);
      await vi.advanceTimersByTimeAsync(0);
      expect(banner.classList.contains('d-none')).toBe(true);
      vi.useRealTimers();
    });

    it('leaves the banner state unchanged when the server returns non-OK status', async () => {
      resetDom();
      const { banner } = makeBanner({ visible: true });
      setFetch([makeErrorResponse(500, 'oops')]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(banner.classList.contains('d-none')).toBe(false);
    });
  });

  describe('polling lifecycle', () => {
    beforeEach(() => { vi.useFakeTimers(); });
    afterEach(() => { vi.useRealTimers(); });

    it('stops polling on app:navigate', async () => {
      resetDom();
      const { banner } = makeBanner();
      const calls = setFetch([
        makeJsonResponse({ circuits: {} }),
        makeJsonResponse({ circuits: {} })
      ]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await vi.advanceTimersByTimeAsync(0);
      document.dispatchEvent(new Event('app:navigate'));
      vi.advanceTimersByTime(120000);
      await vi.advanceTimersByTimeAsync(0);
      expect(calls.length).toBe(1);
    });

    it('skips a poll tick when window._appVisible is false', async () => {
      resetDom();
      const { banner } = makeBanner();
      window._appVisible = false;
      const calls = setFetch([
        makeJsonResponse({ circuits: {} }),
        makeJsonResponse({ circuits: {} })
      ]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await vi.advanceTimersByTimeAsync(0);
      vi.advanceTimersByTime(60000);
      await vi.advanceTimersByTimeAsync(0);
      expect(calls.length).toBe(1);
    });

    it('remains in the correct polling state across multiple ticks', async () => {
      resetDom();
      const { banner } = makeBanner();
      const calls = setFetch([
        makeJsonResponse({ circuits: {} }),
        makeJsonResponse({ circuits: { jira: { provider: 'jira', isOpen: true } } }),
        makeJsonResponse({ circuits: {} })
      ]);
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await vi.advanceTimersByTimeAsync(0);
      expect(banner.classList.contains('d-none')).toBe(true);
      vi.advanceTimersByTime(30000);
      await vi.advanceTimersByTimeAsync(0);
      expect(banner.classList.contains('d-none')).toBe(false);
      vi.advanceTimersByTime(30000);
      await vi.advanceTimersByTimeAsync(0);
      expect(banner.classList.contains('d-none')).toBe(true);
      expect(calls.length).toBe(3);
    });
  });

  describe('fetch URL contract', () => {
    it('hits /api/mcp/status with same-origin credentials', async () => {
      resetDom();
      makeBanner();
      let captured;
      globalThis.fetch = (url, init) => {
        captured = { url: String(url), init };
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ circuits: {} }) });
      };
      loadScriptIsolated(window, 'js/mcp-fallback-banner.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(captured.url).toBe('/api/mcp/status');
      expect(captured.init.credentials).toBe('same-origin');
    });
  });
});
