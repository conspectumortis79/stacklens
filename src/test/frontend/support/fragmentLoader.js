import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const here = dirname(fileURLToPath(import.meta.url));
const STATIC_DIR = resolve(here, '..', '..', '..', 'main', 'resources', 'static');

export function extractInlineScripts(path) {
  const html = readFileSync(path, 'utf8');
  const blocks = [...html.matchAll(/<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi)];
  return blocks.map((m) => m[1]).join('\n;\n');
}

export const FRAGMENT_GLOBAL_SCRIPTS = ['js/page-storage.js'];

export function extractFragmentScripts(path) {
  const html = readFileSync(path, 'utf8');
  const parts = [];
  for (const globalScript of FRAGMENT_GLOBAL_SCRIPTS) {
    parts.push(readFileSync(resolve(STATIC_DIR, globalScript), 'utf8'));
  }
  for (const m of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
    const attrs = m[1];
    const body = m[2];
    const srcMatch = attrs.match(/\b(?:th:src|src)\s*=\s*"(?:@\{)?\/?(js\/[^"}]+)\}?"/i);
    if (srcMatch) {
      parts.push(readFileSync(resolve(STATIC_DIR, srcMatch[1]), 'utf8'));
    } else if (body.trim().length > 0) {
      parts.push(body);
    }
  }
  return parts.join('\n;\n');
}

export function readJs(path) {
  return readFileSync(path, 'utf8');
}

export function extractFunction(source, name) {
  const startRe = new RegExp('function\\s+' + name + '\\s*\\(');
  const start = source.search(startRe);
  if (start < 0) throw new Error('function ' + name + ' not found');
  let i = source.indexOf('{', start);
  let depth = 0;
  for (; i < source.length; i++) {
    const c = source[i];
    if (c === '{') depth++;
    else if (c === '}') {
      depth--;
      if (depth === 0) return source.slice(start, i + 1);
    }
  }
  throw new Error('unbalanced braces for function ' + name);
}

export function makeWindow(bodyHtml = '') {
  const dom = new JSDOM('<!DOCTYPE html><html><body>' + bodyHtml + '</body></html>', {
    runScripts: 'dangerously',
    pretendToBeVisual: true,
    url: 'http://localhost/',
  });
  return dom.window;
}

export function runInWindow(win, code) {
  const s = win.document.createElement('script');
  s.textContent = code;
  win.document.body.appendChild(s);
  s.remove();
}
