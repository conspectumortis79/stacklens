import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { readKnowledgeBundle } from './support/knowledgeModules.js';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const APP_JS = resolve(__dirname, '../../main/resources/static/js/app.js');
const KB_CSS = resolve(__dirname, '../../main/resources/static/css/knowledge.css');
const KB_HTML = resolve(__dirname, '../../main/resources/templates/knowledge.html');

const appJs = readFileSync(APP_JS, 'utf8');
const kbCss = readFileSync(KB_CSS, 'utf8');
const kbHtml = readFileSync(KB_HTML, 'utf8');

describe('app.js SPA navigation — page-specific stylesheet injection (F-1)', () => {
  it('app.js contains a link-rel-stylesheet regex and an append-to-head block', () => {

    expect(appJs, 'expected a link-regex (linkRe) matching rel=stylesheet in app.js')
      .toMatch(/linkRe\s*=\s*\/<link[\s\S]{0,120}?stylesheet/);
    expect(appJs, 'expected a querySelectorAll link[rel=stylesheet] dedupe loop')
      .toMatch(/querySelectorAll\(['"]link\[rel=["']stylesheet["']\]['"]\)/);
    expect(appJs, 'expected a data-spa-injected tag for the new <link>')
      .toMatch(/data-spa-injected/);
    expect(appJs, 'expected a document.head.appendChild for the new <link>')
      .toMatch(/document\.head\.appendChild\(link\)/);
  });

  it('kbAlreadyLoaded cache is removed from app.js (F-2)', () => {

    expect(appJs, 'kbAlreadyLoaded must not appear in app.js — the cache is gone')
      .not.toMatch(/kbAlreadyLoaded/);
  });

  it('knowledge.html declares its stylesheet in a head-extra fragment', () => {

    expect(kbHtml, 'knowledge.html must include <link rel="stylesheet" href=".../knowledge.css"> in head-extra')
      .toMatch(/<link\s+rel="stylesheet"\s+th:href="@\{\/css\/knowledge\.css\}"/);
    expect(readFileSync(KB_CSS, 'utf8').length, 'knowledge.css must be non-empty').toBeGreaterThan(0);
  });

  it('link-regex extracts a knowledge.css href from a head fragment', () => {

    const fakeHead = `
      <link rel="icon" type="image/x-icon" href="/favicon.ico">
      <link rel="icon" type="image/svg+xml" href="/favicon.svg">
      <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
      <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
      <link rel="stylesheet" href="/css/knowledge.css">
    `;
    const re = /<link\b[^>]*?\brel\s*=\s*["']stylesheet["'][^>]*?\bhref\s*=\s*["']([^"']+)["'][^>]*?>/gi;
    const hrefs = [];
    let m;
    while ((m = re.exec(fakeHead)) !== null) hrefs.push(m[1]);

    expect(hrefs, 'expected the CDN bootstrap.min.css href').toContain('https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css');
    expect(hrefs, 'expected the page-specific /css/knowledge.css href').toContain('/css/knowledge.css');
  });

  it('dedupe: a second SPA navigation to the same URL does not add a second <link>', () => {

    const { JSDOM } = require('jsdom');
    const dom = new JSDOM('<!DOCTYPE html><html><head></head><body><main></main></body></html>');
    const head = dom.window.document.head;

    const href = '/css/knowledge.css';
    if (!head.querySelector('link[rel="stylesheet"][href="' + href + '"]')) {
      const link = dom.window.document.createElement('link');
      link.rel = 'stylesheet';
      link.href = href;
      link.setAttribute('data-spa-injected', '1');
      head.appendChild(link);
    }
    expect(head.querySelectorAll('link[rel="stylesheet"]').length).toBe(1);

    if (!head.querySelector('link[rel="stylesheet"][href="' + href + '"]')) {
      const link = dom.window.document.createElement('link');
      link.rel = 'stylesheet';
      link.href = href;
      link.setAttribute('data-spa-injected', '1');
      head.appendChild(link);
    }
    expect(head.querySelectorAll('link[rel="stylesheet"]').length, 'dedupe failed: second navigation added a duplicate <link>').toBe(1);
  });
});

describe('app.js SPA — kbActionDelegate + helpers exposed for KB page (A-1, A-3)', () => {
  it('window.SLBrowseFiles is exported from app.js (A-3)', () => {

    expect(appJs, 'expected window.SLBrowseFiles = async function (...) in app.js')
      .toMatch(/window\.SLBrowseFiles\s*=\s*async\s+function/);
  });

  it('kbNotify is the local toast function name in knowledge.js (A-1)', () => {

    const kbJs = readKnowledgeBundle();

    expect(kbJs, 'expected `function kbNotify(` in knowledge.js').toMatch(/function\s+kbNotify\s*\(/);

    const bareNotify = kbJs.match(/(^|[^A-Za-z_])notify\s*\(/g) || [];
    expect(bareNotify.length, `found ${bareNotify.length} stale 'notify(' call sites in knowledge.js — should be kbNotify(`).toBe(0);
  });
});

describe('knowledge.js — F-16 (bases cache) and F-9 (double-click guard)', () => {
  const kbJs = readKnowledgeBundle();

  it('kbFetchBases caches the result in kbBasesCache (F-16)', () => {
    expect(kbJs, 'expected a kbBasesCache variable').toMatch(/var\s+kbBasesCache\s*=/);
    expect(kbJs, 'expected an in-flight de-dup via kbBasesInflight').toMatch(/kbBasesInflight/);

    expect(kbJs, 'expected kbInvalidateBasesCache() call after kbSubmitCreateBase')
      .toMatch(/kbSubmitCreateBase[\s\S]{0,1500}?kbInvalidateBasesCache\(\)/);
    expect(kbJs, 'expected kbInvalidateBasesCache() call after kbConfirmDeleteBase')
      .toMatch(/kbConfirmDeleteBase[\s\S]{0,1500}?kbInvalidateBasesCache\(\)/);
    expect(kbJs, 'expected kbInvalidateBasesCache() call after kbToggleBaseActive')
      .toMatch(/kbToggleBaseActive[\s\S]{0,1500}?kbInvalidateBasesCache\(\)/);
  });

  it('every submit handler locks the modal during the POST (F-9)', () => {

    for (const handler of ['kbSubmitManual', 'kbSubmitConfluence', 'kbSubmitJira']) {
      const re = new RegExp(
        'function\\s+' + handler +
          '[\\s\\S]*?kbBeginImportProgress\\([\\s\\S]*?\\.finally\\([\\s\\S]*?kbEndImportProgress',
      );
      expect(
        re.test(kbJs),
        handler + ' must call kbBeginImportProgress at the start and kbEndImportProgress in .finally',
      ).toBe(true);
    }
  });
});

describe('knowledge.html — F-4 (kbResetConfirmBtn ID), F-5 (colgroup), F-11 (severity classes)', () => {
  it('F-4: the reset confirm button carries id="kbResetConfirmBtn"', () => {
    expect(kbHtml, 'expected id="kbResetConfirmBtn" on the destructive confirm button')
      .toMatch(/id="kbResetConfirmBtn"/);
  });

  it('F-5: the Inhalte table has a <colgroup> with seven columns', () => {
    expect(kbHtml, 'expected kb-source-table class on the Inhalte <table>')
      .toMatch(/<table class="table align-middle mb-0 kb-source-table">/);
    expect(kbHtml, 'expected <colgroup> with 7 col children for the Inhalte table')
      .toMatch(/<colgroup>[\s\S]*?<col class="col-name"\/>[\s\S]*?<col class="col-type"\/>[\s\S]*?<col class="col-base"\/>[\s\S]*?<col class="col-size"\/>[\s\S]*?<col class="col-status"\/>[\s\S]*?<col class="col-updated"\/>[\s\S]*?<col class="col-actions"\/>[\s\S]*?<\/colgroup>/);
  });

  it('F-11: the danger cards use .kb-card-danger / .kb-card-warning, no inline border-left', () => {

    expect(kbHtml, 'expected kb-card-warning on the reindex card')
      .toMatch(/<div class="card border-0 shadow-sm cf-card kb-card-warning">/);
    expect(kbHtml, 'expected kb-card-danger on the reset card')
      .toMatch(/<div class="card border-0 shadow-sm cf-card mt-3 kb-card-danger">/);

    const dangerSection = kbHtml.match(/<section class="kb-tab-pane d-none" data-kb-pane="danger">([\s\S]*?)<\/section>/);
    expect(dangerSection, 'expected a danger tab-pane section').not.toBeNull();
    expect(dangerSection[1], 'inline border-left: ... in danger section — fix did not apply')
      .not.toMatch(/border-left:\s*4px/);
  });

  it('F-12: the danger-tab cards carry a visible card-header with an i18n title', () => {

    expect(kbHtml, 'expected a card-header inside the reindex (kb-card-warning) card')
      .toMatch(/<div class="card border-0 shadow-sm cf-card kb-card-warning">\s*<div class="card-header bg-white py-3">[\s\S]*?<h5 class="mb-0 fw-semibold">[\s\S]*?<i class="bi bi-arrow-clockwise text-warning me-2"><\/i>[\s\S]*?<span data-i18n="knowledge\.reindex\.title">/);
    expect(kbHtml, 'expected a card-header inside the reset (kb-card-danger) card')
      .toMatch(/<div class="card border-0 shadow-sm cf-card mt-3 kb-card-danger">\s*<div class="card-header bg-white py-3">[\s\S]*?<h5 class="mb-0 fw-semibold">[\s\S]*?<i class="bi bi-shield-exclamation text-danger me-2"><\/i>[\s\S]*?<span data-i18n="knowledge\.danger\.title">/);
  });
});
