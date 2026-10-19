import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { installBrowserStubs, resetDom } from './_load.js';
import { readSource } from './_load.js';

installBrowserStubs();

let buildFileLineLink;
let linkifyResultContainer;

beforeEach(() => {
  resetDom();
  const src = readSource('js/mr-link-builder.js');
  window.eval(src + '\nwindow.MrLinkBuilder = MrLinkBuilder;\n');
  buildFileLineLink = window.MrLinkBuilder.buildFileLineLink;
  linkifyResultContainer = window.MrLinkBuilder.linkifyResultContainer;
});

afterEach(() => {
  vi.useRealTimers();
  delete window.MrLinkBuilder;
  delete window.mrOpenUrl;
});

describe('mr-link-builder', () => {
  it('builds a GitHub-style anchor #L<N> for github URLs', () => {
    const result = buildFileLineLink({
      diffsUrl: 'https://github.com/o/r/pull/5/files',
      filePath: 'src/A.php',
      lineNumber: 42,
    });
    expect(result.url).toBe('https://github.com/o/r/pull/5/files#L42');
    expect(result.isGithub).toBe(true);
  });

  it('builds a GitLab-style anchor #L<N> for non-github URLs', () => {
    const result = buildFileLineLink({
      diffsUrl: 'https://gitlab.example.com/g/p/-/merge_requests/7/diffs',
      filePath: 'src/A.php',
      lineNumber: 9,
    });
    expect(result.url).toBe('https://gitlab.example.com/g/p/-/merge_requests/7/diffs#L9');
    expect(result.isGithub).toBe(false);
  });

  it('omits line anchor when lineNumber is zero', () => {
    const result = buildFileLineLink({
      diffsUrl: 'https://github.com/o/r/pull/5/files',
      filePath: 'src/A.php',
      lineNumber: 0,
    });
    expect(result.url).toBe('https://github.com/o/r/pull/5/files');
  });

  it('returns nulls for empty diffsUrl', () => {
    const result = buildFileLineLink({ diffsUrl: '', filePath: 'src/A.php', lineNumber: 1 });
    expect(result.url).toBe(null);
  });

  it('linkifyResultContainer replaces File: and Line: text nodes with anchors', () => {
    window.mrOpenUrl = vi.fn();
    document.body.innerHTML =
      '<div id="result">' +
      '<p>File: src/A.php\nLine: 42</p>' +
      '<p>File: src/B.php</p>' +
      '</div>';
    const container = document.getElementById('result');
    const count = linkifyResultContainer(container, 'https://gitlab.example.com/g/p/-/merge_requests/7/diffs');
    expect(count).toBe(3);
    const anchors = container.querySelectorAll('a.mr-file-link');
    expect(anchors.length).toBe(3);
    const fileA = Array.from(anchors).find((a) => a.textContent.trim() === 'src/A.php');
    expect(fileA).toBeDefined();
    expect(fileA.getAttribute('href')).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#file-src-A-php',
    );
    const fileB = Array.from(anchors).find((a) => a.textContent.trim() === 'src/B.php');
    expect(fileB).toBeDefined();
    expect(fileB.getAttribute('href')).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#file-src-B-php',
    );
    const lineAnchor = Array.from(anchors).find((a) => a.textContent.trim() === '42');
    expect(lineAnchor).toBeDefined();
    expect(lineAnchor.getAttribute('href')).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#Lsrc-A-php_42',
    );
  });

  it('linkifyResultContainer keeps File: text when no Line follows', () => {
    window.mrOpenUrl = vi.fn();
    document.body.innerHTML =
      '<div id="result"><p>File: src/B.kt</p></div>';
    const container = document.getElementById('result');
    linkifyResultContainer(container, 'https://gitlab.example.com/g/p/-/merge_requests/7/diffs');
    const anchors = container.querySelectorAll('a.mr-file-link');
    expect(anchors.length).toBe(1);
    expect(anchors[0].getAttribute('href')).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#file-src-B-kt',
    );
  });

  it('linkifyResultContainer returns 0 when container is null', () => {
    expect(linkifyResultContainer(null, 'https://example.com/x')).toBe(0);
  });

  it('linkifyResultContainer returns 0 when diffsUrl is empty', () => {
    document.body.innerHTML = '<div id="result"><p>File: src/A.php</p></div>';
    const container = document.getElementById('result');
    expect(linkifyResultContainer(container, '')).toBe(0);
    expect(container.querySelectorAll('a.mr-file-link').length).toBe(0);
  });

  it('linkifyResultContainer still creates a line anchor when File: and Line: are in separate <p> elements (same violation)', () => {
    window.mrOpenUrl = vi.fn();
    document.body.innerHTML =
      '<div id="result">' +
      '<div class="mr-ri">\u274c No comments in PHP</div>' +
      '<p>File: src/Foo.php</p>' +
      '<p>Line: 42</p>' +
      '</div>';
    const container = document.getElementById('result');
    linkifyResultContainer(container, 'https://gitlab.example.com/g/p/-/merge_requests/7/diffs');
    const anchors = container.querySelectorAll('a.mr-file-link');
    const lineAnchor = Array.from(anchors).find((a) => a.textContent.trim() === '42');
    expect(lineAnchor).toBeDefined();
    expect(lineAnchor.getAttribute('href')).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#Lsrc-Foo-php_42',
    );
    const fileAnchor = Array.from(anchors).find((a) => a.textContent.trim() === 'src/Foo.php');
    expect(fileAnchor).toBeDefined();
    expect(fileAnchor.getAttribute('href')).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#file-src-Foo-php',
    );
  });

  it('linkifyResultContainer does NOT carry currentFile across a new violation (.mr-ri) boundary', () => {
    window.mrOpenUrl = vi.fn();
    document.body.innerHTML =
      '<div id="result">' +
      '<div class="mr-ri">\u274c Rule A</div>' +
      '<p>File: src/A.kt</p>' +
      '<p>Line: 1</p>' +
      '<div class="mr-ri">\u274c Rule B</div>' +
      '<p>Line: 7</p>' +
      '</div>';
    const container = document.getElementById('result');
    linkifyResultContainer(container, 'https://gitlab.example.com/g/p/-/merge_requests/7/diffs');
    const anchors = container.querySelectorAll('a.mr-file-link');
    const line1 = Array.from(anchors).find((a) => a.textContent.trim() === '1');
    expect(line1).toBeDefined();
    expect(line1.getAttribute('href')).toBe(
      'https://gitlab.example.com/g/p/-/merge_requests/7/diffs#Lsrc-A-kt_1',
    );
    const line7 = Array.from(anchors).find((a) => a.textContent.trim() === '7');
    expect(line7).toBeUndefined();
  });

  it('linkifyResultContainer resets currentFile when a heading appears between violations', () => {
    window.mrOpenUrl = vi.fn();
    document.body.innerHTML =
      '<div id="result">' +
      '<p>File: src/A.kt</p>' +
      '<p>Line: 1</p>' +
      '<h2>Other section</h2>' +
      '<p>Line: 9</p>' +
      '</div>';
    const container = document.getElementById('result');
    linkifyResultContainer(container, 'https://gitlab.example.com/g/p/-/merge_requests/7/diffs');
    const anchors = container.querySelectorAll('a.mr-file-link');
    const line9 = Array.from(anchors).find((a) => a.textContent.trim() === '9');
    expect(line9).toBeUndefined();
  });
});
