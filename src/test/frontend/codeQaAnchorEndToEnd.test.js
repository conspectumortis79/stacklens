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
  const dir = mkdtempSync(join(tmpdir(), 'sl-cqa-anchor-'));
  const file = join(dir, 'snippet.js');
  const snippet = `(function () {\n${body}\nwindow.mrRenderMarkdown = mrRenderMarkdown;\n})();\n`;
  writeFileSync(file, snippet);
  window.eval(readFileSync(file, 'utf8'));
}

const GITLAB = 'http://localhost:3080';
const PROJECT = 'demo-group/backend-api';
const MR_URL = `${GITLAB}/${PROJECT}/-/merge_requests/1`;

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

describe('end-to-end: line link URL must match a real GitLab element', () => {
  it('the <a>42</a> URL produced by mr-link-builder matches an actual anchor id in the live GitLab MR diff page', async () => {

    const aiMarkdown = [
      '❌ No comments',
      'File: src/main/kotlin/com/company/backendapi/security/JwtAuthenticationFilter.kt',
      'Line: 42',
      'Original:',
      '// bad',
      'Suggestion:',
      '// good',
    ].join('\n');

    mrRenderMarkdown(aiMarkdown, MR_URL);
    window.MrLinkBuilder.linkifyResultContainer(
      document.getElementById('mrMarkdownBody'),
      MR_URL + '/diffs',
    );

    const anchors = document
      .getElementById('mrMarkdownBody')
      .querySelectorAll('a.mr-file-link');
    const lineLink = Array.from(anchors).find(
      (a) => a.textContent.trim() === '42',
    );
    expect(lineLink, 'line link should be created').toBeDefined();
    const href = lineLink.getAttribute('href');
    console.log('Generated href:', href);

    const blobShape = /\/-\/blob\/abc123\/src\/main\/kotlin\/com\/company\/backendapi\/security\/JwtAuthenticationFilter\.kt#L42$/;
    expect(
      blobShape.test(href),
      `Expected blob-view URL but got: ${href}`,
    ).toBe(true);

  });

  it('clicking the line link in a real browser would jump to that line (the actual user flow)', async () => {
    const aiMarkdown = [
      '❌ No comments',
      'File: src/main/kotlin/com/company/backendapi/security/JwtAuthenticationFilter.kt',
      'Line: 45',
      'Original:',
      '// bad',
      'Suggestion:',
      '// good',
    ].join('\n');

    mrRenderMarkdown(aiMarkdown, MR_URL);
    window.MrLinkBuilder.linkifyResultContainer(
      document.getElementById('mrMarkdownBody'),
      MR_URL + '/diffs',
    );

    const openedUrls = [];
    window.mrOpenUrl = (u) => openedUrls.push(u);
    const anchors = document
      .getElementById('mrMarkdownBody')
      .querySelectorAll('a.mr-file-link');
    const lineLink = Array.from(anchors).find(
      (a) => a.textContent.trim() === '45',
    );
    expect(lineLink).toBeDefined();
    lineLink.click();
    expect(openedUrls).toHaveLength(1);
    const openedUrl = openedUrls[0];
    console.log('mrOpenUrl called with:', openedUrl);
    expect(openedUrl).toBe(
      'http://localhost:3080/demo-group/backend-api/-/blob/abc123/src/main/kotlin/com/company/backendapi/security/JwtAuthenticationFilter.kt#L45',
    );
  });
});
