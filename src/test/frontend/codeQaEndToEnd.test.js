import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  installBrowserStubs, resetDom, readSource, loadScript,
} from './_load.js';

installBrowserStubs();

let mrRenderMarkdown;

function loadProductionScripts() {

  loadScript(window, 'js/app.js');
  loadScript(window, 'js/mr-violations.js');
  loadScript(window, 'js/mr-link-builder.js');

  const iife = readSource('js/fragments/code-qa.js');
  const body = iife
    .replace(/^\s*\(function\s*\(\)\s*\{/, '')
    .replace(/\}\)\(\);\s*$/, '');

  const { writeFileSync, mkdtempSync, readFileSync } = require('node:fs');
  const { tmpdir } = require('node:os');
  const { join } = require('node:path');
  const dir = mkdtempSync(join(tmpdir(), 'sl-cqa-e2e-'));
  const file = join(dir, 'snippet.js');
  const snippet = `(function () {\n${body}\n` +
    `window.mrRenderMarkdown = mrRenderMarkdown;\n` +
    `window.escHtml = escHtml;\n` +
    `})();\n`;
  writeFileSync(file, snippet);
  const code = readFileSync(file, 'utf8');
  window.eval(code);
}

beforeEach(() => {
  resetDom();

  document.body.innerHTML = '<div id="mrMarkdownBody"></div>';

  window.mrOpenUrl = vi.fn();
  window.i18n = { t: (k) => k };

  window._mrDiffHeadSha = 'abc123';
  loadProductionScripts();
  mrRenderMarkdown = window.mrRenderMarkdown;
});

afterEach(() => {
  delete window.mrOpenUrl;
  delete window.i18n;
  delete window.mrRenderMarkdown;
  delete window.escHtml;
});

describe('code-qa end-to-end: AI markdown -> rendered DOM -> clickable links', () => {
  it('REAL AI OUTPUT (plain text violations): line 42 must produce a clickable link that opens /diffs#L42', () => {

    const aiMarkdown = [
      '## Zusammenfassung der Code-Quality-Befunde',
      '',
      'Hier sind die wichtigsten Verstöße gegen die Coding-Guidelines.',
      '',
      '❌ Keine Kommentare in PHP',
      'File: src/Service/FooService.php',
      'Line: 42',
      'Original:',
      '// bad comment',
      'Suggestion:',
      '// good comment',
      '',
      '❌ SQL-Injection Risiko',
      'File: src/Repository/BarRepository.php',
      'Line: 17',
      'Original:',
      '$query = "SELECT * FROM users WHERE id = " . $id;',
      'Suggestion:',
      '$stmt = $pdo->prepare("SELECT * FROM users WHERE id = ?");',
      '',
    ].join('\n');

    mrRenderMarkdown(
      aiMarkdown,
      'https://gitlab.example.com/group/project/-/merge_requests/7',
    );
    window.MrLinkBuilder.linkifyResultContainer(
      document.getElementById('mrMarkdownBody'),
      'https://gitlab.example.com/group/project/-/merge_requests/7/diffs',
    );

    const body = document.getElementById('mrMarkdownBody');
    const anchors = body.querySelectorAll('a.mr-file-link');

    console.log('--- DOM after linkify ---');
    console.log(body.innerHTML);
    console.log('--- anchors ---');
    anchors.forEach((a) => {
      console.log(`  text=${JSON.stringify(a.textContent)} href=${a.getAttribute('href')}`);
    });

    const line42 = Array.from(anchors).find(
      (a) => a.textContent.trim() === '42',
    );
    expect(line42, 'expected <a>42</a> link').toBeDefined();

    expect(line42.getAttribute('href')).toBe(
      'https://gitlab.example.com/group/project/-/blob/abc123/src/Service/FooService.php#L42',
    );

    const line17 = Array.from(anchors).find(
      (a) => a.textContent.trim() === '17',
    );
    expect(line17, 'expected <a>17</a> link').toBeDefined();
    expect(line17.getAttribute('href')).toBe(
      'https://gitlab.example.com/group/project/-/blob/abc123/src/Repository/BarRepository.php#L17',
    );

    const fileFoo = Array.from(anchors).find(
      (a) => a.textContent.trim() === 'src/Service/FooService.php',
    );
    expect(fileFoo, 'expected <a>src/Service/FooService.php</a> link').toBeDefined();

    let capturedUrl = null;
    window.mrOpenUrl = vi.fn((u) => { capturedUrl = u; });

    mrRenderMarkdown(
      aiMarkdown,
      'https://gitlab.example.com/group/project/-/merge_requests/7',
    );
    window.MrLinkBuilder.linkifyResultContainer(
      document.getElementById('mrMarkdownBody'),
      'https://gitlab.example.com/group/project/-/merge_requests/7/diffs',
    );
    const fresh = document.getElementById('mrMarkdownBody').querySelectorAll('a.mr-file-link');
    const lineLink = Array.from(fresh).find((a) => a.textContent.trim() === '42');
    expect(lineLink).toBeDefined();
    lineLink.click();
    expect(capturedUrl).toBe(
      'https://gitlab.example.com/group/project/-/blob/abc123/src/Service/FooService.php#L42',
    );
  });

  it('REAL AI OUTPUT (fenced violations): same expectation, fenced format', () => {
    const aiMarkdown = [
      '## Befunde',
      '',
      '```',
      '❌ Keine Kommentare in PHP',
      'File: src/Service/FooService.php',
      'Line: 42',
      'Original:',
      '// bad comment',
      'Suggestion:',
      '// good comment',
      '```',
      '',
    ].join('\n');

    mrRenderMarkdown(
      aiMarkdown,
      'https://gitlab.example.com/group/project/-/merge_requests/7',
    );
    window.MrLinkBuilder.linkifyResultContainer(
      document.getElementById('mrMarkdownBody'),
      'https://gitlab.example.com/group/project/-/merge_requests/7/diffs',
    );

    const body = document.getElementById('mrMarkdownBody');
    const anchors = body.querySelectorAll('a.mr-file-link');
    console.log('--- fenced DOM ---');
    console.log(body.innerHTML);
    console.log('--- fenced anchors ---');
    anchors.forEach((a) => {
      console.log(`  text=${JSON.stringify(a.textContent)} href=${a.getAttribute('href')}`);
    });

    const line42 = Array.from(anchors).find(
      (a) => a.textContent.trim() === '42',
    );
    expect(line42, 'fenced: expected <a>42</a> link').toBeDefined();
    expect(line42.getAttribute('href')).toBe(
      'https://gitlab.example.com/group/project/-/blob/abc123/src/Service/FooService.php#L42',
    );
  });

  it('REGRESSION: clicking the FIRST line link opens the FIRST line, not the last', () => {
    const aiMarkdown = [
      '❌ Rule A',
      'File: src/A.php',
      'Line: 42',
      'Original:',
      'a',
      'Suggestion:',
      'b',
      '',
      '❌ Rule B',
      'File: src/B.php',
      'Line: 17',
      'Original:',
      'a',
      'Suggestion:',
      'b',
    ].join('\n');

    const mrUrl = 'https://gitlab.example.com/g/p/-/merge_requests/7';
    mrRenderMarkdown(aiMarkdown, mrUrl);
    window.MrLinkBuilder.linkifyResultContainer(
      document.getElementById('mrMarkdownBody'),
      mrUrl + '/diffs',
    );

    const anchors = document
      .getElementById('mrMarkdownBody')
      .querySelectorAll('a.mr-file-link');
    const line42 = Array.from(anchors).find((a) => a.textContent.trim() === '42');
    const line17 = Array.from(anchors).find((a) => a.textContent.trim() === '17');
    expect(line42).toBeDefined();
    expect(line17).toBeDefined();

    const openedUrls = [];
    window.mrOpenUrl = (u) => openedUrls.push(u);
    line42.click();
    expect(openedUrls).toEqual([
      'https://gitlab.example.com/g/p/-/blob/abc123/src/A.php#L42',
    ]);

    line17.click();
    expect(openedUrls).toEqual([
      'https://gitlab.example.com/g/p/-/blob/abc123/src/A.php#L42',
      'https://gitlab.example.com/g/p/-/blob/abc123/src/B.php#L17',
    ]);
  });

  it('REGRESSION: clicking the FIRST file link opens the FIRST file URL, not the last', () => {

    const aiMarkdown = [
      '❌ Rule A',
      'File: src/A.php',
      'Line: 1',
      'Original:',
      'a',
      'Suggestion:',
      'b',
      '',
      '❌ Rule B',
      'File: src/B.php',
      'Line: 2',
      'Original:',
      'a',
      'Suggestion:',
      'b',
    ].join('\n');

    const mrUrl = 'https://gitlab.example.com/g/p/-/merge_requests/7';
    mrRenderMarkdown(aiMarkdown, mrUrl);
    window.MrLinkBuilder.linkifyResultContainer(
      document.getElementById('mrMarkdownBody'),
      mrUrl + '/diffs',
    );

    const anchors = document
      .getElementById('mrMarkdownBody')
      .querySelectorAll('a.mr-file-link');
    const fileA = Array.from(anchors).find(
      (a) => a.textContent.trim() === 'src/A.php',
    );
    const fileB = Array.from(anchors).find(
      (a) => a.textContent.trim() === 'src/B.php',
    );
    expect(fileA).toBeDefined();
    expect(fileB).toBeDefined();

    const openedUrls = [];
    window.mrOpenUrl = (u) => openedUrls.push(u);
    fileA.click();
    fileB.click();

    expect(openedUrls.length).toBe(2);
    expect(openedUrls[0]).toBe(
      'https://gitlab.example.com/g/p/-/blob/abc123/src/A.php',
    );
    expect(openedUrls[1]).toBe(
      'https://gitlab.example.com/g/p/-/blob/abc123/src/B.php',
    );
  });
});
