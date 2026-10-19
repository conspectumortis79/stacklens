import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractFunction, extractInlineScripts, makeWindow, readJs, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const TPL = resolve(RES, 'templates');
const LT = resolve(TPL, 'fragments/lasttest-analysis');

function cardHtml(id, name, active) {
  return '<div class="lt-repo-card' + (active ? '' : ' lt-repo-inactive') + '" id="ltRepoCard-' + id +
    '" data-repo-id="' + id + '" data-repo-name="' + name + '" data-repo-path="/repo/' + name +
    '" data-repo-active="' + active + '">' +
    '<div class="lt-repo-name">' + name + '</div><div class="lt-repo-path">/repo/' + name + '</div>' +
    '<div class="lt-card-status"></div>' +
    '<div class="lt-btn-row">' +
    '<button class="lt-btn-xs lt-btn-xs-success"></button>' +
    '<button class="lt-btn-xs lt-btn-xs-outline" data-lt-edit="true"></button>' +
    '</div></div>';
}

const BODY = [
  '<div id="ltLocalRepoList">' + cardHtml(3, 'stacklens', true) + '</div>',
  '<div id="ltProjectList"></div>',
].join('');

function bootWindow() {
  const win = makeWindow(BODY);
  runInWindow(win, [
    'window.bootstrap = { Modal: function () { this.show = function () {}; this.hide = function () {}; } };',
    'window.showConfirm = function () {};',
    'window.showAlert = function () {};',
  ].join('\n'));
  runInWindow(win, extractFunction(readJs(resolve(RES, 'static/js/app.js')), 'escHtml'));
  runInWindow(win, extractInlineScripts(resolve(LT, 'scripts-repo-panel.html')));
  return win;
}

describe('Deaktiviertes Repository in der Lasttest-Analyse', () => {
  it('graut die Karte aus, wenn "Aktiv" im Modal ausgeschaltet wird', () => {
    const win = bootWindow();
    win.ltRefreshLocalRepoCard({ id: 3, name: 'stacklens', path: '/repo/stacklens', active: false });

    const card = win.document.getElementById('ltRepoCard-3');
    expect(card.getAttribute('data-repo-active')).toBe('false');
    expect(card.classList.contains('lt-repo-inactive')).toBe(true);
  });

  it('entfernt den Deaktivierungs-Zustand wieder, wenn das Repo reaktiviert wird', () => {
    const win = bootWindow();
    win.ltRefreshLocalRepoCard({ id: 3, name: 'stacklens', path: '/repo/stacklens', active: false });
    win.ltRefreshLocalRepoCard({ id: 3, name: 'stacklens', path: '/repo/stacklens', active: true });

    const card = win.document.getElementById('ltRepoCard-3');
    expect(card.getAttribute('data-repo-active')).toBe('true');
    expect(card.classList.contains('lt-repo-inactive')).toBe(false);
  });

  it('legt eine neu angelegte, inaktive Karte direkt deaktiviert an', () => {
    const win = bootWindow();
    win.ltRefreshLocalRepoCard({ id: 7, name: 'demo', path: '/repo/demo', active: false });

    const card = win.document.getElementById('ltRepoCard-7');
    expect(card).not.toBeNull();
    expect(card.classList.contains('lt-repo-inactive')).toBe(true);
  });

  it('.lt-repo-inactive blockiert Klicks, lässt aber das Bearbeiten-Symbol zu', () => {
    const css = readFileSync(resolve(LT, 'styles.html'), 'utf8');
    expect(css).toMatch(/\.lt-repo-inactive\s*\{[^}]*pointer-events:\s*none/);
    expect(css).toMatch(/\.lt-repo-inactive\s+\.lt-btn-row\s+button\[data-lt-edit\]\s*\{[^}]*pointer-events:\s*auto/);
  });

  it('serverseitig gerenderte Karten werden inaktiv dargestellt (wie Code-/Pipeline-Analyse)', () => {
    const html = readFileSync(resolve(TPL, 'lasttest-analysis.html'), 'utf8');
    expect(html).toMatch(/th:classappend="\$\{!r\.active\}\s*\?\s*' lt-repo-inactive'"/);
    expect(html).toMatch(/data-lt-edit="true"/);
  });
});
