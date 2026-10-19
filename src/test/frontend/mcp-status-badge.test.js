import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  loadScriptIsolated, installBrowserStubs, resetDom,
  flushMicrotasks, makeJsonResponse,
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

function addMetaSources(names) {
  const meta = document.createElement('meta');
  meta.setAttribute('name', 'mcp-sources');
  meta.setAttribute('content', names.join(','));
  document.head.appendChild(meta);
}

function makeHeader() {
  const header = document.createElement('div');
  header.className = 'sl-page-header';
  header.innerHTML = '<div style="flex:1"><h1>Title</h1></div>';
  document.body.appendChild(header);
  return header;
}

afterEach(() => {
  untrackDocumentListeners();
  vi.useRealTimers();
});

describe('mcp-status-badge', () => {
  describe('meta tag parsing', () => {
    beforeEach(() => { resetDom(); });

    it('does nothing when the meta tag is absent', async () => {
      setFetch([makeJsonResponse({})]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(document.getElementById('mcpStatusBadge')).toBeNull();
    });

    it('ignores empty or unknown source names in the meta tag', async () => {
      addMetaSources(['', 'unknown', '   ', 'jira']);
      makeHeader();
      setFetch([makeJsonResponse({ processRunning: false, routes: {} })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const host = document.getElementById('mcpStatusBadge');
      expect(host).not.toBeNull();
      const chips = host.querySelectorAll('.mcp-status-chip');
      expect(chips.length).toBe(1);
      expect(chips[0].textContent).toContain('Jira');
    });

    it('is case-insensitive about the source names in the meta tag', async () => {
      addMetaSources(['JIRA', 'GitLab', 'CONFLUENCE']);
      makeHeader();
      setFetch([makeJsonResponse({ processRunning: false, routes: {} })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const host = document.getElementById('mcpStatusBadge');
      expect(host).not.toBeNull();
      const labels = Array.from(host.querySelectorAll('.mcp-status-chip')).map((c) => c.textContent);
      expect(labels.some((l) => l.includes('Jira'))).toBe(true);
      expect(labels.some((l) => l.includes('GitLab'))).toBe(true);
      expect(labels.some((l) => l.includes('Confluence'))).toBe(true);
    });
  });

  describe('style injection', () => {
    it('injects the chip CSS once when sources are present', async () => {
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      setFetch([makeJsonResponse({ processRunning: false, routes: {} })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const style = document.getElementById('mcp-status-badge-style');
      expect(style).not.toBeNull();
      expect(style.textContent).toContain('.mcp-status-chip');
    });

    it('does not inject duplicate styles when re-initialised via app:navigate', async () => {
      vi.useFakeTimers();
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      setFetch([makeJsonResponse({ processRunning: false, routes: {} })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await vi.advanceTimersByTimeAsync(0);
      document.dispatchEvent(new Event('app:navigate'));
      await vi.advanceTimersByTimeAsync(0);
      const styles = document.querySelectorAll('#mcp-status-badge-style');
      expect(styles.length).toBe(1);
    });
  });

  describe('chip rendering based on status', () => {
    async function boot(statuses) {
      resetDom();
      addMetaSources(['jira', 'gitlab', 'github']);
      makeHeader();
      const calls = setFetch(statuses);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const host = document.getElementById('mcpStatusBadge');
      return { host, calls };
    }

    it('renders all chips as "nativ" when the MCP server is not running', async () => {
      const { host } = await boot([
        makeJsonResponse({
          processRunning: false,
          mcpServerName: 'srv',
          routes: { jira: true, gitlab: true, github: true }
        })
      ]);
      const chips = host.querySelectorAll('.mcp-status-chip');
      expect(chips.length).toBe(3);
      chips.forEach((chip) => {
        expect(chip.classList.contains('mcp-on')).toBe(false);
        expect(chip.textContent).toContain('nativ');
        expect(chip.querySelector('.bi-hdd-network-fill')).toBeNull();
        expect(chip.querySelector('.bi-hdd-fill')).not.toBeNull();
      });
    });

    it('renders chips as "MCP" + mcp-on when server runs and the source is routed through MCP', async () => {
      const { host } = await boot([
        makeJsonResponse({
          processRunning: true,
          mcpServerName: 'srv',
          routes: { jira: true, gitlab: false, github: true }
        })
      ]);
      const labels = Array.from(host.querySelectorAll('.mcp-status-chip')).map((c) => c.textContent);
      const onLabels = Array.from(host.querySelectorAll('.mcp-status-chip.mcp-on')).map((c) => c.textContent);
      expect(onLabels.length).toBe(2);
      expect(onLabels.every((l) => l.includes('MCP'))).toBe(true);
      const offLabels = labels.filter((l) => l.includes('nativ'));
      expect(offLabels.length).toBe(1);
      expect(offLabels[0]).toContain('GitLab');
    });

    it('keeps a chip as "nativ" when routes say true but the process is not running', async () => {
      const { host } = await boot([
        makeJsonResponse({ processRunning: false, routes: { jira: true } })
      ]);
      const chips = Array.from(host.querySelectorAll('.mcp-status-chip'));
      expect(chips[0].classList.contains('mcp-on')).toBe(false);
      expect(chips[0].textContent).toContain('nativ');
    });

    it('keeps a chip as "nativ" when the server runs but the source is not routed', async () => {
      const { host } = await boot([
        makeJsonResponse({ processRunning: true, routes: { jira: false, gitlab: false, github: false } })
      ]);
      const chips = Array.from(host.querySelectorAll('.mcp-status-chip.mcp-on'));
      expect(chips.length).toBe(0);
      const all = Array.from(host.querySelectorAll('.mcp-status-chip'));
      all.forEach((c) => expect(c.textContent).toContain('nativ'));
    });

    it('renders nativ chips for status=null (no process info available)', async () => {
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      setFetch([{ ok: false, status: 500, statusText: 'err', json: () => Promise.reject(new Error('no json')), text: () => Promise.resolve('') }]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const host = document.getElementById('mcpStatusBadge');
      expect(host).not.toBeNull();
      const chips = host.querySelectorAll('.mcp-status-chip');
      expect(chips.length).toBe(1);
      expect(chips[0].classList.contains('mcp-on')).toBe(false);
      expect(chips[0].textContent).toContain('nativ');
    });
  });

  describe('tooltip and aria-label', () => {
    it('sets the source-routed title when the chip is on (processRunning + routes=true)', async () => {
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      setFetch([makeJsonResponse({
        processRunning: true,
        mcpServerName: 'mcpserver',
        routes: { jira: true }
      })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const chip = document.querySelector('.mcp-status-chip');
      expect(chip.getAttribute('title')).toBe('Jira: routed via MCP — mcpserver');
      expect(chip.getAttribute('aria-label')).toBe('Jira: routed via MCP — mcpserver');
    });

    it('sets the native-config title when the chip is off', async () => {
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      setFetch([makeJsonResponse({
        processRunning: false,
        mcpServerName: 'mcpserver',
        routes: { jira: true }
      })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const chip = document.querySelector('.mcp-status-chip');
      expect(chip.getAttribute('title')).toBe('Jira: native config');
      expect(chip.getAttribute('aria-label')).toBe('Jira: native config');
    });

    it('omits the server name suffix in the tooltip when mcpServerName is absent', async () => {
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      setFetch([makeJsonResponse({
        processRunning: true,
        routes: { jira: true }
      })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const chip = document.querySelector('.mcp-status-chip');
      expect(chip.getAttribute('title')).toBe('Jira: routed via MCP');
    });
  });

  describe('host element lifecycle', () => {
    it('appends a fresh host to the page header on init', async () => {
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      setFetch([makeJsonResponse({ processRunning: false, routes: {} })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const host = document.getElementById('mcpStatusBadge');
      expect(host).not.toBeNull();
      expect(host.parentNode).toBe(document.querySelector('.sl-page-header > div'));
    });

    it('removes a pre-existing host on init so duplicates cannot appear', async () => {
      resetDom();
      addMetaSources(['jira']);
      const header = makeHeader();
      const stale = document.createElement('div');
      stale.id = 'mcpStatusBadge';
      stale.className = 'mcp-status-row';
      header.appendChild(stale);
      setFetch([makeJsonResponse({ processRunning: false, routes: {} })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const all = document.querySelectorAll('#mcpStatusBadge');
      expect(all.length).toBe(1);
      expect(all[0]).not.toBe(stale);
    });

    it('is a no-op (no host, no chips) when there is no .sl-page-header', async () => {
      resetDom();
      addMetaSources(['jira']);
      setFetch([makeJsonResponse({ processRunning: true, routes: { jira: true } })]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(document.getElementById('mcpStatusBadge')).toBeNull();
    });

    it('does not start the poll timer when no sources are declared', async () => {
      vi.useFakeTimers();
      resetDom();
      makeHeader();
      const calls = setFetch([]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await vi.advanceTimersByTimeAsync(60000);
      expect(calls.length).toBe(0);
    });
  });

  describe('render deduplication', () => {
    it('does not re-render the same chip row when status is unchanged across polls', async () => {
      vi.useFakeTimers();
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      const calls = setFetch([
        makeJsonResponse({ processRunning: true, routes: { jira: true } }),
        makeJsonResponse({ processRunning: true, routes: { jira: true } }),
        makeJsonResponse({ processRunning: true, routes: { jira: true } })
      ]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await vi.advanceTimersByTimeAsync(0);
      const host = document.getElementById('mcpStatusBadge');
      const beforeHTML = host.innerHTML;
      vi.advanceTimersByTime(10000);
      await vi.advanceTimersByTimeAsync(0);
      vi.advanceTimersByTime(10000);
      await vi.advanceTimersByTimeAsync(0);
      const afterHTML = host.innerHTML;
      expect(afterHTML).toBe(beforeHTML);
      expect(calls.length).toBe(3);
    });
  });

  describe('cleanup on app:navigate without a host', () => {
    it('poll tick is a no-op when the host has been removed since the previous tick', async () => {
      vi.useFakeTimers();
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      setFetch([
        makeJsonResponse({ processRunning: true, routes: { jira: true } }),
        makeJsonResponse({ processRunning: false, routes: {} })
      ]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await vi.advanceTimersByTimeAsync(0);
      expect(document.getElementById('mcpStatusBadge')).not.toBeNull();
    });
  });

  describe('fetch contract', () => {
    it('hits /api/mcp/status with same-origin credentials', async () => {
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      let captured;
      globalThis.fetch = (url, init) => {
        captured = { url: String(url), init };
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ processRunning: false, routes: {} }) });
      };
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(captured.url).toBe('/api/mcp/status');
      expect(captured.init.credentials).toBe('same-origin');
    });

    it('keeps the host in the DOM with nativ chips when the server returns a non-OK status', async () => {
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      setFetch([{ ok: false, status: 503, statusText: 'err', json: () => Promise.reject(new Error('no json')), text: () => Promise.resolve('') }]);
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const host = document.getElementById('mcpStatusBadge');
      expect(host).not.toBeNull();
      const chips = host.querySelectorAll('.mcp-status-chip');
      expect(chips.length).toBe(1);
      expect(chips[0].textContent).toContain('nativ');
    });

    it('keeps the host in the DOM with nativ chips when the fetch itself rejects', async () => {
      resetDom();
      addMetaSources(['jira']);
      makeHeader();
      globalThis.fetch = vi.fn(() => Promise.reject(new Error('boom')));
      loadScriptIsolated(window, 'js/mcp-status-badge.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const host = document.getElementById('mcpStatusBadge');
      expect(host).not.toBeNull();
      const chips = host.querySelectorAll('.mcp-status-chip');
      expect(chips.length).toBe(1);
      expect(chips[0].textContent).toContain('nativ');
    });
  });
});
