import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  loadScriptIsolated, installBrowserStubs, resetDom,
  forgetTrackedListeners, untrackDocumentListeners,
  flushMicrotasks, makeJsonResponse, makeErrorResponse
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

function standardDom() {
  document.body.innerHTML =
    '<span data-i18n="nav.home">x</span>'
    + '<input data-i18n-placeholder="search.keyword">'
    + '<a data-i18n-title="page.title"></a>'
    + '<button data-i18n-aria-label="common.close" data-lang-btn="en">EN</button>'
    + '<button data-i18n-tooltip="common.help" data-lang-btn="de">DE</button>';
}

async function boot({ configResponse, langResponse, dom = standardDom } = {}) {
  resetDom();
  dom();
  const responses = [];
  if (configResponse !== false) responses.push(makeJsonResponse(configResponse || { language: 'en' }));
  if (langResponse !== false) responses.push(makeJsonResponse(langResponse || { a: { b: 'B' } }));
  const calls = setFetch(responses);
  loadScriptIsolated(window, 'js/i18n.js');
  document.dispatchEvent(new Event('DOMContentLoaded'));
  await flushMicrotasks();
  return calls;
}

describe('i18n', () => {
  afterEach(() => {
    untrackDocumentListeners();
  });

  describe('lookup', () => {
    beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/i18n.js'); });

    it('exposes t, tList, setLanguage, apply, getLang, onReady', () => {
      expect(window.i18n).toBeDefined();
      expect(typeof window.i18n.t).toBe('function');
      expect(typeof window.i18n.tList).toBe('function');
      expect(typeof window.i18n.setLanguage).toBe('function');
      expect(typeof window.i18n.apply).toBe('function');
      expect(typeof window.i18n.getLang).toBe('function');
      expect(typeof window.i18n.onReady).toBe('function');
    });

    it('returns the key itself when no translations are loaded', () => {
      expect(window.i18n.t('any.missing.key')).toBe('any.missing.key');
    });

    it('returns an empty string for falsy keys', () => {
      expect(window.i18n.t('')).toBe('');
      expect(window.i18n.t(null)).toBe('');
      expect(window.i18n.t(undefined)).toBe('');
    });

    it('walks nested dot-keys once translations are loaded', async () => {
      await boot({ langResponse: { nav: { home: { subtitle: 'Willkommen' } } } });
      expect(window.i18n.t('nav.home.subtitle')).toBe('Willkommen');
      expect(window.i18n.t('nav.home.missing')).toBe('nav.home.missing');
    });

    it('returns the key if a non-string leaf is encountered', async () => {
      await boot({ langResponse: { a: { b: { c: 42 } } } });
      expect(window.i18n.t('a.b.c')).toBe('a.b.c');
    });

    it('returns [] from tList when the key resolves to a non-array', async () => {
      await boot({ langResponse: { x: 'string' } });
      expect(window.i18n.tList('x')).toEqual([]);
      expect(window.i18n.tList('missing')).toEqual([]);
    });

    it('returns the array from tList when the key resolves to a list', async () => {
      await boot({ langResponse: { colors: ['red', 'green'] } });
      expect(window.i18n.tList('colors')).toEqual(['red', 'green']);
    });

    it('returns [] from tList for falsy keys', () => {
      expect(window.i18n.tList('')).toEqual([]);
      expect(window.i18n.tList(null)).toEqual([]);
      expect(window.i18n.tList(undefined)).toEqual([]);
    });
  });

  describe('apply()', () => {
    it('populates data-i18n, data-i18n-placeholder, data-i18n-title, data-i18n-aria-label, data-i18n-tooltip', async () => {
      const lang = {
        nav: { home: 'Home' },
        search: { keyword: 'Search…' },
        page: { title: 'MyPage' },
        common: { close: 'Close', help: 'Help' }
      };
      await boot({ langResponse: lang });
      expect(document.querySelector('[data-i18n="nav.home"]').textContent).toBe('Home');
      expect(document.querySelector('[data-i18n-placeholder="search.keyword"]').placeholder).toBe('Search…');
      expect(document.querySelector('[data-i18n-title="page.title"]').title).toBe('MyPage');
      expect(document.querySelector('[data-i18n-aria-label="common.close"]').getAttribute('aria-label')).toBe('Close');
      expect(document.querySelector('[data-i18n-tooltip="common.help"]').getAttribute('data-tooltip')).toBe('Help');
    });

    it('preserves child icons inside [data-i18n] elements', async () => {
      const lang = { files: { label: 'Dateien' } };
      await boot({
        langResponse: lang,
        dom: () => {
          document.body.innerHTML =
            '<button data-i18n="files.label"><i class="bi bi-folder"></i></button>';
        }
      });
      const btn = document.querySelector('[data-i18n="files.label"]');
      expect(btn.querySelector('i.bi-folder')).not.toBeNull();
      expect(btn.textContent).toContain('Dateien');
    });

    it('marks the active lang button as btn-primary and others as btn-outline-secondary', async () => {
      await boot({
        dom: () => {
          document.body.innerHTML =
            '<button data-lang-btn="en" class="btn">EN</button>'
            + '<button data-lang-btn="de" class="btn">DE</button>';
        }
      });
      const enBtn = document.querySelector('[data-lang-btn="en"]');
      const deBtn = document.querySelector('[data-lang-btn="de"]');
      expect(enBtn.className).toContain('btn-primary');
      expect(enBtn.className).not.toContain('btn-outline-secondary');
      expect(deBtn.className).toContain('btn-outline-secondary');
      expect(deBtn.className).not.toContain('btn-primary');
    });

    it('sets document.documentElement.lang to the active language', async () => {
      await boot();
      expect(document.documentElement.lang).toBe('en');
    });

    it('leaves an element untouched when the key is missing', async () => {
      await boot({
        langResponse: { only: { this: 'X' } },
        dom: () => {
          document.body.innerHTML =
            '<span data-i18n="only.this">Original</span>'
            + '<span data-i18n="missing.path">Untouched</span>';
        }
      });
      expect(document.querySelector('[data-i18n="only.this"]').textContent).toBe('X');
      expect(document.querySelector('[data-i18n="missing.path"]').textContent).toBe('Untouched');
    });
  });

  describe('language bootstrap', () => {
    it('fetches the persisted language first, then the translation file', async () => {
      const lang = { greeting: 'Hi' };
      const calls = await boot({ configResponse: { language: 'en' }, langResponse: lang });
      expect(calls[0].url).toBe('/api/config/language');
      expect(calls[1].url).toBe('/i18n2/en.json');
      expect(window.i18n.t('greeting')).toBe('Hi');
      expect(window.i18n.getLang()).toBe('en');
    });

    it('falls back to English when the language endpoint fails', async () => {
      const calls = setFetch([
        makeErrorResponse(500, ''),
        makeJsonResponse({ greeting: 'Hi' })
      ]);
      resetDom();
      document.body.innerHTML = '<span data-i18n="greeting">…</span>';
      loadScriptIsolated(window, 'js/i18n.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      expect(calls[0].url).toBe('/api/config/language');
      expect(calls[1].url).toBe('/i18n2/en.json');
      expect(window.i18n.getLang()).toBe('en');
    });

    it('setLanguage() ignores languages other than en/de (no fetch beyond cache)', async () => {
      const fetchSpy = setFetch([]);
      resetDom();
      loadScriptIsolated(window, 'js/i18n.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      const callsBefore = fetchSpy.length;
      window.i18n.setLanguage('fr');
      window.i18n.setLanguage('');
      window.i18n.setLanguage(null);
      window.i18n.setLanguage('xx');
      await flushMicrotasks();
      expect(fetchSpy.length).toBe(callsBefore);
    });

    it('setLanguage() persists via PUT then reloads translations', async () => {
      const en = { a: { b: 'B' } };
      const de = { a: { b: 'BB' } };
      const calls = setFetch([
        makeJsonResponse({ language: 'en' }),
        makeJsonResponse(en),
        makeJsonResponse({}, { status: 200 }),
        makeJsonResponse(de)
      ]);
      resetDom();
      document.body.innerHTML = '<span data-i18n="a.b">…</span>';
      loadScriptIsolated(window, 'js/i18n.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      window.i18n.setLanguage('de');
      await flushMicrotasks();
      expect(calls[2].url).toBe('/api/config/language');
      expect(calls[2].init.method).toBe('PUT');
      const body = JSON.parse(calls[2].init.body);
      expect(body.language).toBe('de');
      expect(calls[3].url).toBe('/i18n2/de.json');
      expect(window.i18n.t('a.b')).toBe('BB');
      expect(window.i18n.getLang()).toBe('de');
    });

    it('does not refetch translations when setLanguage is called repeatedly with the same lang', async () => {
      const en = { a: { b: 'B' } };
      const calls = setFetch([
        makeJsonResponse({ language: 'en' }),
        makeJsonResponse(en),
        makeJsonResponse({}, { status: 200 })
      ]);
      resetDom();
      loadScriptIsolated(window, 'js/i18n.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      window.i18n.setLanguage('en');
      window.i18n.setLanguage('en');
      await flushMicrotasks();
      const enFetches = calls.filter((c) => c.url === '/i18n2/en.json');
      expect(enFetches.length).toBe(1);
    });

    it('logs an error when setLanguage PUT itself throws', async () => {
      resetDom();
      loadScriptIsolated(window, 'js/i18n.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      await flushMicrotasks();
      globalThis.fetch = vi.fn(() => Promise.reject(new Error('network down')));
      const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      window.i18n.setLanguage('de');
      await flushMicrotasks();
      expect(errSpy).toHaveBeenCalled();
    });
  });

  describe('onReady()', () => {
    it('runs queued callbacks after the first successful load', async () => {
      setFetch([
        makeJsonResponse({ language: 'en' }),
        makeJsonResponse({ a: 'A' })
      ]);
      resetDom();
      loadScriptIsolated(window, 'js/i18n.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      const order = [];
      window.i18n.onReady(() => order.push('queued-1'));
      window.i18n.onReady(() => order.push('queued-2'));
      await flushMicrotasks();
      expect(order).toEqual(['queued-1', 'queued-2']);
    });

    it('runs a callback synchronously when translations are already loaded', async () => {
      await boot({ langResponse: { a: 'A' } });
      const order = [];
      window.i18n.onReady(() => order.push('late'));
      expect(order).toEqual(['late']);
    });

    it('falls back to English and flushes the onReady queue when /i18n2/<lang>.json 404s', async () => {
      setFetch([
        makeJsonResponse({ language: 'xx' }),
        makeErrorResponse(404, 'not found'),
        makeJsonResponse({ a: 'A' })
      ]);
      resetDom();
      loadScriptIsolated(window, 'js/i18n.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      const order = [];
      window.i18n.onReady(() => order.push('q1'));
      await flushMicrotasks();
      expect(order).toEqual(['q1']);
      expect(window.i18n.getLang()).toBe('en');
      expect(window.i18n.t('a')).toBe('A');
    });

    it('leaves the onReady queue unflushed when both primary and fallback /i18n2/<lang>.json 404', async () => {
      setFetch([
        makeJsonResponse({ language: 'xx' }),
        makeErrorResponse(404, 'not found'),
        makeErrorResponse(404, 'not found either')
      ]);
      resetDom();
      loadScriptIsolated(window, 'js/i18n.js');
      document.dispatchEvent(new Event('DOMContentLoaded'));
      const order = [];
      window.i18n.onReady(() => order.push('q1'));
      await flushMicrotasks();
      expect(order).toEqual([]);
      const calls = globalThis.fetch.mock.calls.map((c) => c[0]);
      expect(calls).toContain('/i18n2/xx.json');
      expect(calls).toContain('/i18n2/en.json');
    });
  });

  describe('SPA navigation hook', () => {
    it('re-applies translations when an app:navigate event is dispatched', async () => {
      await boot({
        langResponse: { greeting: 'Hi' },
        dom: () => {
          document.body.innerHTML = '<span data-i18n="greeting">x</span>';
        }
      });
      const node = document.querySelector('[data-i18n="greeting"]');
      expect(node).not.toBeNull();
      node.textContent = 'mutated';
      document.dispatchEvent(new Event('app:navigate'));
      expect(node.textContent).toBe('Hi');
    });
  });
});
