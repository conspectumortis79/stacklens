import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractFragmentScripts, readJs, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '../../main/resources');
const TEMPLATES = resolve(RES, 'templates');
const FRAGMENTS = resolve(TEMPLATES, 'fragments/pipeline-analysis');
const SCRIPTS_JS = resolve(RES, 'static/js/fragments/pipeline-analysis.js');

const CARD_ACTIONS = ['play', 'cancel', 'edit', 'delete', 'gh-play', 'gh-cancel', 'gl-play', 'gl-cancel'];

function btnRow(actions) {
  return '<div class="pa-btn-row">' + actions.map((a) =>
    `<button class="pa-btn-xs" data-action="${a}">${a}</button>`).join('') + '</div>';
}

const BODY = [
  '<div id="paAnalysisPanel"></div>',
  '<div id="paAnalysisTabs"></div><div id="paAnalysisHeader"></div>',
  '<div id="paEmptyState" class="d-none"></div><div id="paRunning" class="d-none"></div>',
  '<div id="paAborted" class="d-none"></div><div id="paError" class="d-none"></div>',
  '<div id="paRepoList"></div>',
  '<div id="hxCard-pipeline-analysis"></div>',
  '<div class="pa-repo-card" id="repoCard-7" data-repo-id="7" data-repo-name="stacklens">',
  '  <div class="pa-card-status"></div>', btnRow(['play', 'cancel', 'edit', 'delete']), '</div>',
  '<div class="pa-repo-card" id="repoCard-8" data-repo-id="8" data-repo-name="lasttest">',
  '  <div class="pa-card-status"></div>', btnRow(['play', 'cancel', 'edit', 'delete']), '</div>',
  '<div class="pa-repo-card" id="paGhCard-3" data-github-repo-id="3" data-github-repo-name="shop">',
  '  <div class="pa-card-status"></div>', btnRow(['gh-play', 'gh-cancel']), '</div>',
  '<div class="pa-repo-card" id="paGhCard-4" data-github-repo-id="4" data-github-repo-name="other">',
  '  <div class="pa-card-status"></div>', btnRow(['gh-play', 'gh-cancel']), '</div>',
  '<div class="pa-repo-card pa-gitlab-project" data-config-id="2" data-project-id="9">',
  '  <div class="pa-card-status"></div>', btnRow(['gl-play', 'gl-cancel']), '</div>',
  '<div class="pa-repo-card pa-gitlab-project" data-config-id="2" data-project-id="10">',
  '  <div class="pa-card-status"></div>', btnRow(['gl-play', 'gl-cancel']), '</div>',
].join('');

function analysis(overrides) {
  return Object.assign({
    id: 55, repoConfigId: null, gitlabConfigId: null, gitlabProjectId: null, githubRepoConfigId: null,
    status: 'RUNNING',
  }, overrides || {});
}

let win;

function boot() {
  win = makeWindow(BODY);
  runInWindow(win, [
    'window.SLScroll = function() { return { update: function() {} }; };',
    'window.showConfirm = function() {};',
    'window.paInitTokenUsageBar = function() {};',
    'window.paLoadGitlabConfigs = function() {};',
    'if (!window.CSS) { window.CSS = { escape: function (v) { return String(v); } }; }',
  ].join('\n'));
  runInWindow(win, readJs(resolve(RES, 'static/js/history-list.js')));
  runInWindow(win, extractFragmentScripts(resolve(FRAGMENTS, 'scripts.html')));
  runInWindow(win, `
    window.__posts = [];
    window.__gets = 0;
    window.__list = [];
    window.__status = null;
    window.fetch = function (url, init) {
      var u = String(url);
      if (init && init.method === 'POST') { window.__posts.push(u); }
      if (u.indexOf('/api/pipeline-analysis/all') !== -1) { window.__gets++; }
      var body = u.indexOf('/all') !== -1 ? window.__list : window.__status;
      return Promise.resolve({ ok: true, status: 200, json: function () { return Promise.resolve(body || {}); } });
    };
    window.alert = function () {};
    window.setInterval = function () { return 1; };
    window.clearInterval = function () {};
  `);
  return win;
}

const flush = () => new Promise((r) => setTimeout(r, 0));

function btn(cardSel, action) {
  return win.document.querySelector(`${cardSel} [data-action="${action}"]`);
}

function state(cardSel, action) {
  const el = btn(cardSel, action);
  return { disabled: el.disabled, opacity: el.style.opacity, cursor: el.style.cursor };
}

beforeEach(() => boot());

describe('Pipeline repo card locks its start button while a run is active', () => {
  it('treats RUNNING and PENDING as busy', () => {
    expect(win.paAnalysisIsRunning(analysis({ status: 'RUNNING' }))).toBe(true);
    expect(win.paAnalysisIsRunning(analysis({ status: 'PENDING' }))).toBe(true);
    expect(win.paAnalysisIsRunning(analysis({ status: 'COMPLETED' }))).toBe(false);
    expect(win.paAnalysisIsRunning(analysis({ status: 'CANCELLED' }))).toBe(false);
    expect(win.paAnalysisIsRunning(null)).toBe(false);
  });

  it('greys out start, edit and delete and enables cancel for a running local repo', () => {
    win.paApplyCardStatuses([analysis({ repoConfigId: 7 })]);

    for (const action of ['play', 'edit', 'delete']) {
      expect(state('#repoCard-7', action)).toEqual({ disabled: true, opacity: '0.5', cursor: 'not-allowed' });
    }
    expect(state('#repoCard-7', 'cancel')).toEqual({ disabled: false, opacity: '', cursor: '' });
    expect(win.document.querySelector('#repoCard-7 .pa-card-status-pill').classList.contains('pa-card-status--running')).toBe(true);
  });

  it('releases the buttons again once the run is no longer active', () => {
    win.paApplyCardStatuses([analysis({ repoConfigId: 7, status: 'COMPLETED' })]);

    expect(state('#repoCard-7', 'play')).toEqual({ disabled: false, opacity: '', cursor: '' });
    expect(state('#repoCard-7', 'cancel')).toEqual({ disabled: true, opacity: '0.4', cursor: 'not-allowed' });
  });

  it('clears the busy state for a repo without any analysis', () => {
    win.paApplyCardStatuses([analysis({ repoConfigId: 7 })]);
    expect(btn('#repoCard-7', 'play').disabled).toBe(true);

    win.paApplyCardStatuses([]);
    expect(state('#repoCard-7', 'play')).toEqual({ disabled: false, opacity: '', cursor: '' });
  });

  it('applies the same rule to the github and gitlab project cards', () => {
    win.paApplyCardStatuses([
      analysis({ id: 61, githubRepoConfigId: 3 }),
      analysis({ id: 62, gitlabConfigId: 2, gitlabProjectId: 9 }),
    ]);

    expect(btn('#paGhCard-3', 'gh-play').disabled).toBe(true);
    expect(btn('#paGhCard-3', 'gh-cancel').disabled).toBe(false);
    expect(btn('.pa-gitlab-project[data-project-id="9"]', 'gl-play').disabled).toBe(true);
    expect(btn('.pa-gitlab-project[data-project-id="9"]', 'gl-cancel').disabled).toBe(false);
    expect(btn('#repoCard-8', 'play').disabled).toBe(false);
  });

  it('releases the buttons when the polled run ends with a failure', async () => {
    win.paApplyCardStatuses([analysis({ repoConfigId: 7 })]);
    expect(btn('#repoCard-7', 'play').disabled).toBe(true);

    win.paCurrentAnalysisId = 55;
    win.__status = { id: 55, status: 'FAILED', errorMessage: 'boom' };
    win.__list = [analysis({ repoConfigId: 7, status: 'FAILED' })];
    await win.paPollStatus();
    await flush();

    expect(state('#repoCard-7', 'play')).toEqual({ disabled: false, opacity: '', cursor: '' });
    expect(win.document.querySelector('#repoCard-7 .pa-card-status-pill').classList.contains('pa-card-status--aborted')).toBe(true);
  });

  it('covers every data-action used by a pipeline card', () => {
    const sources = [
      SCRIPTS_JS,
      resolve(FRAGMENTS, 'scripts-pipeline-gitlab.html'),
      resolve(TEMPLATES, 'pipeline-analysis.html'),
    ].map((file) => readFileSync(file, 'utf8')).join('\n');
    const used = [...new Set([...sources.matchAll(/data-action="([^"]+)"/g)].map((m) => m[1]))].sort();
    expect(used).toEqual(CARD_ACTIONS.slice().sort());

    const busy = readFileSync(SCRIPTS_JS, 'utf8').match(/function paSetCardBusy[\s\S]*?\n  \}/)[0];
    used.forEach((action) => expect(busy).toContain(`[data-action="${action}"]`));
  });
});

describe('Pipeline card cancel targets the analysis of that card', () => {
  it('uses the shared cancel for the currently selected repo', async () => {
    win.paCurrentRepoId = 7;
    win.paCurrentAnalysisId = 55;
    let shared = 0;
    win.paCancelCurrent = () => { shared += 1; };

    win.paCancelForCard(win.document.getElementById('repoCard-7'));
    await flush();

    expect(shared).toBe(1);
    expect(win.__posts).toEqual([]);
  });

  it('cancels the latest run of another local repo through its own endpoint', async () => {
    win.paCurrentRepoId = 7;
    win.paCancelCurrent = () => { throw new Error('must not cancel the current analysis'); };

    win.paCancelForCard(win.document.getElementById('repoCard-8'));
    await flush();

    expect(win.__posts).toEqual(['/api/pipeline-analysis/cancel-latest-for-repo/8']);
  });

  it('cancels another local repo, gitlab project and github repo through their own endpoints', async () => {
    win.paCurrentRepoId = 7;
    win.paGitlabConfigId = 2;
    win.paGitlabProjectId = 9;
    win.paGithubRepoConfigId = 3;
    win.paCancelCurrent = () => { throw new Error('must not cancel the current analysis'); };

    win.paCancelForCard(win.document.getElementById('repoCard-8'));
    win.paCancelForCard(win.document.querySelector('.pa-gitlab-project[data-project-id="10"]'));
    win.paCancelForCard(win.document.getElementById('paGhCard-4'));
    await flush();

    expect(win.__posts).toEqual([
      '/api/pipeline-analysis/cancel-latest-for-repo/8',
      '/api/pipeline-analysis/cancel-latest-for-gitlab/2/10',
      '/api/pipeline-analysis/cancel-latest-for-github/4',
    ]);
  });

  it('keeps the current gitlab project on the shared cancel path', async () => {
    win.paGitlabConfigId = 2;
    win.paGitlabProjectId = 9;
    let shared = 0;
    win.paCancelCurrent = () => { shared += 1; };

    win.paCancelForCard(win.document.querySelector('.pa-gitlab-project'));
    await flush();

    expect(shared).toBe(1);
    expect(win.__posts).toEqual([]);
  });
});
