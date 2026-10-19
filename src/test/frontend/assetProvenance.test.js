import { describe, it, expect } from 'vitest';
import * as H from './support/pageModelHarness.js';

const LINK_STYLESHEET = /<link\b[^>]*rel=["']stylesheet["'][^>]*href=["']([^"']+)["']/gi;
const LINK_ANY_ASSET = /<link\b[^>]*(?:href|src)=["'](https?:\/\/[^"']+)["'][^>]*>/gi;
const SCRIPT_SRC = /<script\b[^>]*src=["'](https?:\/\/[^"']+)["']/gi;
const CSS_URL = /url\((['"]?)(https?:\/\/[^)'"]+)\1\)/gi;
const JS_LOADER = /(?:\bsrc|_SRC|\.href)\s*=\s*['"](https?:\/\/[^'"]+)['"]/g;
const FETCH_URL = /fetch\(\s*['"](https?:\/\/[^'"]+)['"]/g;

function externalAssets(text) {
  const found = [];
  for (const re of [LINK_STYLESHEET, LINK_ANY_ASSET, SCRIPT_SRC, CSS_URL, JS_LOADER, FETCH_URL]) {
    for (const m of text.matchAll(re)) found.push(m[2] || m[1]);
  }
  return [...new Set(found)].filter((url) => url.startsWith('http'));
}

const FILES = [...H.templateFiles(), ...H.ownScriptFiles(), ...H.walk(H.joinPath(H.RESOURCES, 'static/css'), '.css')]
  .filter((f) => !f.endsWith('.min.js') && !f.includes(`${H.joinPath('static', 'vendor')}`))
  .map((file) => ({ file, text: H.read(file) }));

describe('asset provenance: shipped assets never come from a remote host', () => {
  it('no stylesheet, script, font or fetch target points at a CDN', () => {
    const remote = [];
    for (const { file, text } of FILES) {
      for (const url of externalAssets(text)) remote.push(`${url} (${H.relative(H.RESOURCES, file)})`);
    }
    expect([...new Set(remote)].sort()).toEqual([]);
  });

  it('detects a synthetic remote asset in markup, css and script loader forms', () => {
    expect(externalAssets('<link rel="stylesheet" href="https://cdn.example/x.css">')).toContain('https://cdn.example/x.css');
    expect(externalAssets('@font-face{src:url(https://cdn.example/f.woff2)}')).toContain('https://cdn.example/f.woff2');
    expect(externalAssets("s.src = 'https://cdn.example/lib.js';")).toContain('https://cdn.example/lib.js');
    expect(externalAssets("fetch('https://cdn.example/api')")).toContain('https://cdn.example/api');
  });

  it('leaves ordinary outbound content links untouched', () => {
    expect(externalAssets('<a href="https://jira.example.com/browse/ABC-1">Jira</a>')).toEqual([]);
    expect(externalAssets('<link rel="stylesheet" href="/vendor/css/bootstrap.min.css">')).toEqual([]);
  });

  it('every bundled vendor asset referenced by a page exists on disk', () => {
    const missing = [];
    for (const page of H.pages()) {
      for (const url of H.scriptAssetsOf(H.closureForTemplate(page))) {
        if (!url.startsWith('/vendor/') && !url.startsWith('/js/') && !url.startsWith('/css/')) continue;
        if (!H.existsSync(H.joinPath(H.STATIC, url.replace(/^\//, '')))) {
          missing.push(`${url} (${H.relative(H.TEMPLATES, page)})`);
        }
      }
    }
    expect([...new Set(missing)].sort()).toEqual([]);
  });

  it('the vendor bundle contains the libraries the shell declares', () => {
    const layout = H.read(H.joinPath(H.TEMPLATES, 'layout.html'));
    const required = [
      '/vendor/css/bootstrap.min.css',
      '/vendor/css/bootstrap-icons.min.css',
      '/vendor/css/dataTables.bootstrap5.min.css',
      '/vendor/css/buttons.bootstrap5.min.css',
      '/vendor/js/bootstrap.bundle.min.js',
      '/vendor/js/jquery.min.js',
      '/vendor/js/dataTables.min.js',
      '/vendor/js/dataTables.bootstrap5.min.js',
      '/vendor/js/dataTables.buttons.min.js',
      '/vendor/js/buttons.bootstrap5.min.js',
      '/vendor/js/buttons.html5.min.js',
      '/vendor/js/jszip.min.js',
    ];
    for (const asset of required) {
      expect(H.existsSync(H.joinPath(H.STATIC, asset.replace(/^\//, ''))), asset).toBe(true);
      expect(layout, `${asset} must be referenced by layout.html`).toContain(asset);
    }
  });
});
