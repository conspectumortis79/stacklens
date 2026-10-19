import { describe, it, expect, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const FRAGMENTS = resolve(RES, 'templates/fragments/security-analysis');
const STYLES = resolve(FRAGMENTS, 'styles.html');
const I18N = resolve(RES, 'static/i18n');

const PARTS = ['scripts.html', 'scripts-helpers.html', 'scripts-render.html', 'scripts-fix-pr.html'];

const LOCAL_CARD =
  '<div class="ba-repo-card" id="baRepoCard-7" data-repo-id="7" data-repo-name="stacklens"' +
  ' data-repo-path="/Users/mortis/Code/Kotlin/stacklens"></div>';

const OPEN_WINDOWS = [];

function bootWindow(bodyHtml) {
  const win = makeWindow('<div id="baMainArea"></div>' + (bodyHtml || ''));
  OPEN_WINDOWS.push(win);
  runInWindow(win, [
    'window.SLScroll = function() { return { update: function() {} }; };',
    'window.showConfirm = function() {};',
    'window.baInitTokenUsageBar = function() {};',
    'window.baLoadHistory = function() {};',
    'if (!window.CSS) { window.CSS = { escape: function (v) { return String(v); } }; }',
  ].join('\n'));
  runInWindow(win, PARTS.map((file) => extractInlineScripts(resolve(FRAGMENTS, file))).join('\n;\n'));
  win.fetch = () => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
  return win;
}

function selectRepo(win, assignments) {
  const base = {
    baCurrentRepoId: null,
    baCurrentRepoName: '',
    baCurrentAnalysisId: null,
    baGitlabConfigId: null,
    baGitlabProjectId: null,
    baGitlabProjectPath: null,
    baGithubRepoConfigId: null,
    baGithubRepoName: '',
  };
  const merged = Object.assign(base, assignments);
  runInWindow(win, Object.keys(merged).map((k) => k + ' = ' + JSON.stringify(merged[k]) + ';').join('\n'));
}

function chip(win, selector) {
  return win.document.querySelector('.ba-run-head ' + selector);
}

afterEach(() => {
  OPEN_WINDOWS.splice(0).forEach((win) => {
    if (win._baRunClockTimer) {
      win.clearInterval(win._baRunClockTimer);
      win._baRunClockTimer = null;
    }
  });
});

describe('security analysis: repository head of the progress card', () => {
  it('names the selected local repository above the current step', () => {
    const win = bootWindow(LOCAL_CARD);
    selectRepo(win, { baCurrentRepoId: 7, baCurrentRepoName: 'stacklens', baCurrentAnalysisId: 4821 });
    win.baShowRunning('stacklens', 2, 3, 'Iteration 2: Exploit…');

    const card = win.document.querySelector('.ba-iter-progress');
    const head = card.querySelector('.ba-run-head');
    expect(head).not.toBe(null);
    expect(card.getAttribute('data-provider')).toBe('local');
    expect(head.querySelector('.ba-run-badge').className).toContain('bi-folder2');
    expect(win.document.getElementById('baRunRepo').textContent).toBe('stacklens');
    expect(head.contains(win.document.getElementById('baRunPct'))).toBe(true);
    expect(head.querySelector('.ba-run-state').className).toContain('ba-card-status--running');
    expect(head.querySelector('.ba-run-state-dot')).not.toBe(null);
    expect(card.querySelector('.ba-run-body').contains(win.document.getElementById('baProgressBarInner'))).toBe(true);
    expect(chip(win, '.ba-run-chip--prov').textContent).toBe('Lokal');
    expect(chip(win, '#baRunPathChip').textContent).toBe('/Users/mortis/Code/Kotlin/stacklens');
    expect(chip(win, '#baRunIdChip').textContent).toBe('Analyse #4821');
  });

  it('replaces the muted repository line below the step label', () => {
    const win = bootWindow(LOCAL_CARD);
    selectRepo(win, { baCurrentRepoId: 7, baCurrentRepoName: 'stacklens' });
    win.baShowRunning('stacklens', 1, 3, 'Iteration 1: Reconnaissance…');
    expect(win.document.querySelector('.ba-run-repo')).toBe(null);
    expect(win.document.querySelectorAll('.ba-run-meta').length).toBe(1);
  });

  it('marks a GitLab project with the gitlab provider identity', () => {
    const win = bootWindow();
    selectRepo(win, {
      baCurrentRepoName: 'auth-service',
      baGitlabConfigId: 3,
      baGitlabProjectId: 9,
      baGitlabProjectPath: 'demo-group/auth-service',
      baCurrentAnalysisId: 4822,
    });
    win.baShowRunning('auth-service', 1, 3);

    expect(win.document.querySelector('.ba-iter-progress').getAttribute('data-provider')).toBe('gitlab');
    expect(win.document.querySelector('.ba-run-badge').className).toContain('bi-gitlab');
    expect(chip(win, '.ba-run-chip--prov').textContent).toBe('GitLab');
    expect(chip(win, '#baRunPathChip').textContent).toBe('demo-group/auth-service');
  });

  it('marks a GitHub repository and hides a duplicated path chip', () => {
    const win = bootWindow();
    selectRepo(win, {
      baCurrentRepoName: 'acme/web-shop',
      baGithubRepoConfigId: 12,
      baGithubRepoName: 'acme/web-shop',
    });
    win.baShowRunning('acme/web-shop', 1, 3);

    expect(win.document.querySelector('.ba-iter-progress').getAttribute('data-provider')).toBe('github');
    expect(win.document.querySelector('.ba-run-badge').className).toContain('bi-github');
    expect(chip(win, '.ba-run-chip--prov').textContent).toBe('GitHub');
    expect(chip(win, '#baRunPathChip').hidden).toBe(true);
  });

  it('reveals the analysis id on the first poll without remounting the card', () => {
    const win = bootWindow(LOCAL_CARD);
    selectRepo(win, { baCurrentRepoId: 7, baCurrentRepoName: 'stacklens' });
    win.baShowRunning('stacklens', 1, 3, 'a');
    const fill = win.document.getElementById('baProgressBarInner');
    expect(chip(win, '#baRunIdChip').hidden).toBe(true);

    selectRepo(win, { baCurrentRepoId: 7, baCurrentRepoName: 'stacklens', baCurrentAnalysisId: 55 });
    win.baShowRunning('stacklens', 2, 3, 'b');
    expect(win.document.getElementById('baProgressBarInner')).toBe(fill);
    expect(chip(win, '#baRunIdChip').hidden).toBe(false);
    expect(chip(win, '#baRunIdChip').textContent).toBe('Analyse #55');
    expect(win.document.getElementById('baRunPct').textContent).toBe('67 %');
  });

  it('follows a provider switch of the selected repository', () => {
    const win = bootWindow(LOCAL_CARD);
    selectRepo(win, { baCurrentRepoId: 7, baCurrentRepoName: 'stacklens' });
    win.baShowRunning('stacklens', 1, 3, 'a');
    selectRepo(win, {
      baCurrentRepoName: 'auth-service',
      baGitlabConfigId: 3,
      baGitlabProjectId: 9,
      baGitlabProjectPath: 'demo-group/auth-service',
    });
    win.baShowRunning('auth-service', 1, 3, 'b');

    const card = win.document.querySelector('.ba-iter-progress');
    expect(card.getAttribute('data-provider')).toBe('gitlab');
    expect(card.querySelector('.ba-run-badge').className).toContain('bi-gitlab');
    expect(win.document.getElementById('baRunRepo').textContent).toBe('auth-service');
    expect(chip(win, '#baRunPathChip').textContent).toBe('demo-group/auth-service');
  });
});

describe('security analysis: repository head of the result view', () => {
  function completedData(overrides) {
    return Object.assign({
      id: 4821,
      status: 'COMPLETED',
      findingsJson: '[]',
      createdAt: '1000',
      completedAt: '301000',
    }, overrides);
  }

  it('keeps the repository identity on top of a finished analysis', () => {
    const win = bootWindow(LOCAL_CARD);
    runInWindow(win, 'window.slHistory = { parseDate: function (v) { return new Date(Number(v)); } };');
    selectRepo(win, { baCurrentRepoId: 7, baCurrentRepoName: 'stacklens', baCurrentAnalysisId: 4821 });
    win.baRenderResult(completedData({}));

    const head = win.document.querySelector('.ba-iter-progress .ba-run-head');
    expect(head).not.toBe(null);
    expect(head.querySelector('.ba-run-head-name').textContent).toBe('stacklens');
    expect(head.querySelector('.ba-run-state').className).toContain('ba-card-status--completed');
    expect(head.querySelector('.ba-run-state').textContent).toBe('Abgeschlossen');
    expect(head.querySelector('.ba-run-state-dot')).toBe(null);
    expect(head.querySelector('.ba-run-elapsed').textContent).toBe('Dauer 05:00');
    expect(win.document.querySelector('.ba-run-head-right #baRunPct')).toBe(null);
  });

  it('labels a failed analysis in the same head', () => {
    const win = bootWindow(LOCAL_CARD);
    selectRepo(win, { baCurrentRepoId: 7, baCurrentRepoName: 'stacklens', baCurrentAnalysisId: 4821 });
    win.baRenderResult(completedData({ status: 'FAILED', errorMessage: 'Timeout' }));

    const head = win.document.querySelector('.ba-iter-progress .ba-run-head');
    expect(head.querySelector('.ba-run-state').className).toContain('ba-card-status--aborted');
    expect(head.querySelector('.ba-run-state').textContent).toBe('Fehlgeschlagen');
    expect(head.querySelector('.ba-run-elapsed')).toBe(null);
  });
});

describe('security analysis: repository head css', () => {
  const css = readFileSync(STYLES, 'utf8');

  it('paints the provider accent rail per provider', () => {
    expect(css).toContain('--ba-prov-gitlab: #fc6d26;');
    expect(css).toContain('.ba-iter-progress[data-provider="local"]  { --ba-run-prov: var(--ba-prov-local); }');
    expect(css).toContain('.ba-iter-progress[data-provider="gitlab"] { --ba-run-prov: var(--ba-prov-gitlab); }');
    expect(css).toContain('.ba-iter-progress[data-provider="github"] { --ba-run-prov: var(--ba-prov-github); }');
    expect(css).toMatch(/\.ba-iter-progress \{[^}]*border-left: 4px solid var\(--ba-run-prov, var\(--ba-accent\)\)/);
  });

  it('keeps the github badge readable in the dark theme', () => {
    expect(css).toMatch(/html\[data-theme="dark"\] \{\s*--ba-prov-github: #c9d1d9;\s*\}/);
  });

  it('moves the card padding into the head and the body', () => {
    expect(css).toMatch(/\.ba-iter-progress \{[^}]*padding: 0;/);
    expect(css).toContain('.ba-run-body { padding: 20px 28px 22px; }');
    expect(css).toContain('.ba-run-head {');
    expect(css).toContain('.ba-run-chip[hidden] { display: none; }');
    expect(css).not.toContain('.ba-run-repo');
  });

  it('leaves the status pill colours to the shipped theme classes', () => {
    expect(css).toMatch(/\.ba-run-state \{[^}]*\}/);
    const pill = css.match(/\.ba-run-state \{[^}]*\}/)[0];
    expect(pill).not.toMatch(/color:|background:/);
  });
});

describe('i18n for the repository head', () => {
  const de = JSON.parse(readFileSync(resolve(I18N, 'de.json'), 'utf8')).bug_analysis;
  const en = JSON.parse(readFileSync(resolve(I18N, 'en.json'), 'utf8')).bug_analysis;
  const KEYS = ['provider_local', 'provider_gitlab', 'provider_github', 'analysis_ref', 'status_failed', 'run_duration'];

  it('defines every head label in both languages', () => {
    KEYS.forEach((key) => {
      expect(typeof de[key]).toBe('string');
      expect(typeof en[key]).toBe('string');
    });
  });

  it('translates the labels instead of copying the german text', () => {
    expect(de.provider_local).toBe('Lokal');
    expect(en.provider_local).toBe('Local');
    expect(de.analysis_ref).toBe('Analyse');
    expect(en.analysis_ref).toBe('Analysis');
    expect(de.status_failed).toBe('Fehlgeschlagen');
    expect(en.status_failed).toBe('Failed');
  });

  it('keeps the duration placeholder in both languages', () => {
    expect(de.run_duration).toContain('{t}');
    expect(en.run_duration).toContain('{t}');
  });
});
