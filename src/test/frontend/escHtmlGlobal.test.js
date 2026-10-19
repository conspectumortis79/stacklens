import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { loadScriptIsolated, resetDom } from './_load.js';

describe('app.js — escHtml / escapeHtml global', () => {
  beforeEach(() => { resetDom(); loadScriptIsolated(window, 'js/app.js'); });
  afterEach(() => { resetDom(); });

  it('exposes escHtml on window', () => {
    expect(typeof window.escHtml).toBe('function');
  });

  it('exposes escapeHtml as alias on window', () => {
    expect(typeof window.escapeHtml).toBe('function');
    expect(window.escapeHtml).toBe(window.escHtml);
  });

  it('exposes ieEsc as alias on window (image-extraction legacy name)', () => {
    expect(typeof window.ieEsc).toBe('function');
    expect(window.ieEsc).toBe(window.escHtml);
  });

  it('escapes the five dangerous HTML characters', () => {
    expect(window.escHtml(`<script>alert("xss&'")</script>`))
      .toBe('&lt;script&gt;alert(&quot;xss&amp;&#39;&quot;)&lt;/script&gt;');
  });

  it('treats null and undefined as empty string', () => {
    expect(window.escHtml(null)).toBe('');
    expect(window.escHtml(undefined)).toBe('');
  });

  it('escapes ampersand BEFORE lt/gt to avoid double-escape (regression for & -> &amp; -> &amp;amp;)', () => {
    expect(window.escHtml('a&b<c')).toBe('a&amp;b&lt;c');
  });
});
