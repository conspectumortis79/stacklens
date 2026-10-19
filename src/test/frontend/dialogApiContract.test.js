import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { templateFiles, ownScriptFiles, RESOURCES } from './support/pageModelHarness.js';

const SHELL_SOURCES = [...templateFiles(), ...ownScriptFiles()]
  .map((file) => ({ file, text: readFileSync(file, 'utf8') }));

const STANDALONE_PAGES = new Set(
  templateFiles()
    .filter((file) => /\btemplates\/[^/]+\.html$/.test(file))
    .filter((file) => !readFileSync(file, 'utf8').includes('layout:decorate'))
    .map((file) => file.slice(RESOURCES.length + 1).split('\\').join('/')),
);

STANDALONE_PAGES.add('static/security-fix-diff.html');

const FALLBACK_SITES = [
  ['static/js/app.js', 1],
  ['templates/fragments/shared/scripts-local-repo.html', 2],
  ['templates/fragments/layout/scripts-prompt-file-browsers.html', 1],
];

const BLOCKING_CALL = /(^|[^.\w$])(?:window\.)?(alert|confirm)\s*\(/g;

function countBlockingCalls(text) {
  return [...text.matchAll(BLOCKING_CALL)].length;
}

function relativeOf(file) {
  return file.slice(RESOURCES.length + 1).split('\\').join('/');
}

describe('dialog api: the shell never uses blocking native dialogs', () => {
  it('layout-driven pages route messages through showAlert/showConfirm', () => {
    const offenders = [];
    for (const { file, text } of SHELL_SOURCES) {
      const rel = relativeOf(file);
      if (STANDALONE_PAGES.has(rel)) continue;
      const expected = (FALLBACK_SITES.find(([name]) => name === rel) || [, 0])[1];
      const actual = countBlockingCalls(text);
      if (actual > expected) offenders.push(`${rel}: ${actual} blocking calls, allowed ${expected}`);
    }
    expect(offenders).toEqual([]);
  });

  it('the fallback helpers delegate to the toast system instead of recursing', () => {
    const localRepo = readFileSync(`${RESOURCES}/templates/fragments/shared/scripts-local-repo.html`, 'utf8');
    const body = /function _lrAlert\(msg, kind\) \{[\s\S]*?\n  \}/.exec(localRepo)[0];
    expect(body).toContain('window.showAlert(');
    expect(body).not.toContain('window._lrAlert(');
  });

  it('detects a synthetic blocking call in a shell fragment', () => {
    expect(countBlockingCalls("if (bad) { alert('x'); }")).toBe(1);
    expect(countBlockingCalls("showAlert('x', 'danger');")).toBe(0);
  });
});
