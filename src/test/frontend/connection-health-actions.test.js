import { describe, it, expect, afterEach, vi } from 'vitest';
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

function stubI18nT(translations) {
  window.i18n = {
    t: (key) => translations[key] || key,
    tList: () => [],
    apply: () => {},
    getLang: () => 'en',
    setLanguage: () => {},
    onReady: () => {}
  };
}

function stubShowAlert(records) {
  window.showAlert = (message, type) => {
    records.push({ message: String(message), type });
  };
  window.location = window.location || { reload() {} };
  if (!window.location.reload) window.location.reload = () => {};
}

afterEach(() => {
  untrackDocumentListeners();
});

describe('connection-health-actions', () => {
  describe('probeConn — skip path', () => {
    it('is a no-op when kind is missing (does not fetch or alert)', async () => {
      const calls = setFetch([makeJsonResponse({}, { status: 200 })]);
      const alerts = [];
      stubI18nT({ 'health.probe_ok': 'OK', 'health.probe_failed': 'failed' });
      stubShowAlert(alerts);
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      const result = await window.probeConn('', '42');
      expect(calls).toHaveLength(0);
      expect(alerts).toHaveLength(0);
      expect(result).toBeUndefined();
    });

    it('is a no-op when id is missing', async () => {
      const calls = setFetch([makeJsonResponse({}, { status: 200 })]);
      const alerts = [];
      stubI18nT({});
      stubShowAlert(alerts);
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      const result = await window.probeConn('jira', '');
      expect(calls).toHaveLength(0);
      expect(alerts).toHaveLength(0);
      expect(result).toBeUndefined();
    });
  });

  describe('probeConn — success path', () => {
    it('calls the probe endpoint with the correct URL', async () => {
      const calls = setFetch([makeJsonResponse({ healthy: true })]);
      const alerts = [];
      stubI18nT({ 'health.probe_ok': 'OK' });
      stubShowAlert(alerts);
      const reloadSpy = vi.fn();
      delete window.location;
      window.location = { reload: reloadSpy };
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      await window.probeConn('jira', 7);
      expect(calls).toHaveLength(1);
      expect(calls[0].url).toBe('/api/health/connections/jira/7/probe');
      expect(calls[0].init.method).toBe('POST');
      expect(calls[0].init.headers['Content-Type']).toBe('application/json');
    });

    it('surfaces an OK alert (i18n-translated) when healthy=true', async () => {
      setFetch([makeJsonResponse({ healthy: true, lastErrorMessage: null })]);
      const alerts = [];
      stubI18nT({ 'health.probe_ok': 'Verbindung OK' });
      window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
      window.location = { reload: () => {} };
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      await window.probeConn('jira', 7);
      expect(alerts).toEqual([{ message: 'Verbindung OK', type: 'success' }]);
    });

    it('surfaces a warning alert with lastErrorMessage when healthy=false', async () => {
      setFetch([makeJsonResponse({ healthy: false, lastErrorMessage: 'ECONNREFUSED' })]);
      const alerts = [];
      stubI18nT({ 'health.probe_failed': 'Probe failed' });
      window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
      window.location = { reload: () => {} };
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      await window.probeConn('gitlab', 'abc');
      expect(alerts).toEqual([{ message: 'ECONNREFUSED', type: 'warning' }]);
    });

    it('surfaces a warning with the translated fallback when healthy=false and no lastErrorMessage', async () => {
      setFetch([makeJsonResponse({ healthy: false })]);
      const alerts = [];
      stubI18nT({ 'health.probe_failed': 'Probe failed' });
      window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
      window.location = { reload: () => {} };
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      await window.probeConn('gitlab', 'abc');
      expect(alerts).toEqual([{ message: 'Probe failed', type: 'warning' }]);
    });

    it('calls window.location.reload after a successful probe', async () => {
      setFetch([makeJsonResponse({ healthy: true })]);
      stubI18nT({});
      window.showAlert = () => {};
      const reloadSpy = vi.fn();
      window.location = { reload: reloadSpy };
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      await window.probeConn('jira', 7);
      expect(reloadSpy).toHaveBeenCalledTimes(1);
    });

    it('returns the parsed JSON snapshot to the caller', async () => {
      const snap = { healthy: true, ts: 12345 };
      setFetch([makeJsonResponse(snap)]);
      stubI18nT({});
      window.showAlert = () => {};
      window.location = { reload: () => {} };
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      const result = await window.probeConn('jira', 7);
      expect(result).toEqual(snap);
    });
  });

  describe('probeConn — HTTP error path', () => {
    it('surfaces a danger alert with HTTP status when the server returns 5xx', async () => {
      setFetch([makeErrorResponse(503, 'service unavailable')]);
      const alerts = [];
      stubI18nT({ 'health.probe_failed': 'Probe failed: ' });
      window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
      window.location = { reload: () => {} };
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      await window.probeConn('jira', 7);
      expect(alerts).toHaveLength(1);
      expect(alerts[0].type).toBe('danger');
      expect(alerts[0].message).toContain('HTTP 503');
      expect(alerts[0].message).toContain('Probe failed: ');
    });
  });

  describe('probeConn — network error path', () => {
    it('surfaces a danger alert with the network error message and restores the button', async () => {
      globalThis.fetch = vi.fn(() => Promise.reject(new Error('Failed to fetch')));
      const alerts = [];
      stubI18nT({ 'health.probe_failed': 'Probe failed: ' });
      window.showAlert = (m, t) => alerts.push({ message: String(m), type: t });
      window.location = { reload: () => {} };
      const btn = document.createElement('button');
      btn.innerHTML = '<i class="bi bi-broadcast"></i>';
      btn.disabled = false;
      document.body.appendChild(btn);
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      await window.probeConn('jira', 7, btn);
      expect(alerts).toHaveLength(1);
      expect(alerts[0].type).toBe('danger');
      expect(alerts[0].message).toContain('Failed to fetch');
      expect(btn.disabled).toBe(false);
      expect(btn.innerHTML).toBe('<i class="bi bi-broadcast"></i>');
    });
  });

  describe('probeConn — button state during fetch', () => {
    it('disables the button and replaces its content with a spinner', async () => {
      let release;
      const gate = new Promise((r) => { release = r; });
      globalThis.fetch = vi.fn(() => gate.then(() => makeJsonResponse({ healthy: true })));
      stubI18nT({});
      window.showAlert = () => {};
      window.location = { reload: () => {} };
      const btn = document.createElement('button');
      btn.innerHTML = '<i class="bi bi-broadcast"></i>';
      document.body.appendChild(btn);
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      const promise = window.probeConn('jira', 7, btn);
      await flushMicrotasks(2);
      expect(btn.disabled).toBe(true);
      expect(btn.innerHTML).toContain('spinner-border');
      release();
      await promise;
    });

    it('does not touch the button when none was supplied', async () => {
      setFetch([makeJsonResponse({ healthy: true })]);
      stubI18nT({});
      window.showAlert = () => {};
      window.location = { reload: () => {} };
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      await expect(window.probeConn('jira', 7)).resolves.toBeDefined();
    });
  });

  describe('i18n fallback path', () => {
    it('uses the fallback string when window.i18n is missing', async () => {
      setFetch([makeJsonResponse({ healthy: true })]);
      delete window.i18n;
      window.showAlert = () => {};
      window.location = { reload: () => {} };
      loadScriptIsolated(window, 'js/connection-health-actions.js');
      const result = await window.probeConn('jira', 7);
      expect(result).toBeDefined();
    });
  });
});
