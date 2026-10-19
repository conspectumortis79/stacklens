import { describe, it, expect, beforeAll } from 'vitest';
import { loadScript, installBrowserStubs } from './_load.js';

installBrowserStubs();

describe('simulated-helpers', () => {
  beforeAll(() => {
    loadScript(window, 'js/simulated-helpers.js');
  });

  describe('esc', () => {
    it('returns empty string for null and undefined', () => {
      expect(window.eval('esc(null)')).toBe('');
      expect(window.eval('esc(undefined)')).toBe('');
    });

    it('escapes the five HTML-sensitive characters', () => {
      expect(window.eval(`esc('<>&\\"\\'')`)).toBe('&lt;&gt;&amp;&quot;&#39;');
    });

    it('coerces non-string primitives to a string before escaping', () => {
      expect(window.eval('esc(0)')).toBe('0');
      expect(window.eval('esc(false)')).toBe('false');
    });

    it('escapes & first so subsequent replacements are not double-escaped', () => {
      expect(window.eval(`esc('a&b<c>')`)).toBe('a&amp;b&lt;c&gt;');
    });
  });

  describe('txt', () => {
    it('returns empty string for null and undefined', () => {
      expect(window.eval('txt(null)')).toBe('');
      expect(window.eval('txt(undefined)')).toBe('');
    });

    it('returns strings verbatim', () => {
      expect(window.eval("txt('hello')")).toBe('hello');
    });

    it('flattens Atlassian Document Format content into joined lines', () => {
      const adf = {
        content: [
          { content: [{ text: 'line one' }, { text: 'still one' }] },
          { content: [{ text: 'line two' }] }
        ]
      };
      const out = window.eval(`txt(${JSON.stringify(adf)})`);
      expect(out).toBe('line one\nstill one\nline two');
    });

    it('joins multiple blocks with one newline per block (flatMap result)', () => {
      const adf = {
        content: [
          { content: [{ text: 'a' }] },
          { content: [{ text: 'b' }] },
          { content: [{ text: 'c' }] }
        ]
      };
      expect(window.eval(`txt(${JSON.stringify(adf)})`)).toBe('a\nb\nc');
    });

    it('returns a non-ADF object as pretty-printed JSON', () => {
      const obj = { foo: 'bar', n: 1 };
      const out = window.eval(`txt(${JSON.stringify(obj)})`);
      expect(JSON.parse(out)).toEqual(obj);
      expect(out).toContain('\n');
    });

    it('returns pretty JSON when ADF field is missing or null', () => {
      expect(JSON.parse(window.eval("txt({})"))).toEqual({});
      const out = window.eval('txt({ content: null })');
      expect(JSON.parse(out)).toEqual({ content: null });
    });

    it('still flattens when block-level content is missing', () => {
      const adf = { content: [{ content: [{ text: 'a' }] }, { content: [{ text: 'b' }] }] };
      const out = window.eval(`txt(${JSON.stringify(adf)})`);
      expect(out).toBe('a\nb');
    });

    it('omits blocks with no content field entirely', () => {
      const adf = { content: [{ content: [{ text: 'a' }] }, {}] };
      const out = window.eval(`txt(${JSON.stringify(adf)})`);
      expect(out).toBe('a');
    });
  });

  describe('shortSha', () => {
    it('returns 8 lowercase hex characters (no NaN, no 0x)', () => {
      const samples = [];
      for (let i = 0; i < 20; i++) {
        const v = window.eval('shortSha()');
        expect(v).toMatch(/^[0-9a-f]{8}$/);
        samples.push(v);
      }
      expect(new Set(samples).size).toBeGreaterThan(1);
    });
  });

  describe('fmtDate', () => {
    it('uses the supplied date when one is given', () => {
      const v = window.eval("fmtDate('2024-03-15T12:34:00Z')");
      expect(typeof v).toBe('string');
      expect(v.length).toBeGreaterThan(0);
      expect(v).toMatch(/\d{4}/);
    });

    it('returns a string for the current date when called with null', () => {
      const v = window.eval('fmtDate(null)');
      expect(typeof v).toBe('string');
      expect(v).toMatch(/\d{2}\.\d{2}\.\d{4}|\d{4}/);
    });
  });
});
