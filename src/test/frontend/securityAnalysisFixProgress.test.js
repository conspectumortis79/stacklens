import { describe, it, expect, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const FRAGMENTS = resolve(here, '../../main/resources/templates/fragments/security-analysis');
const CSS_DIR = resolve(here, '../../main/resources/static/css');
const THEMES = resolve(CSS_DIR, 'themes');
const I18N = resolve(here, '../../main/resources/static/i18n');

const PARTS = [
  'scripts.html',
  'scripts-helpers.html',
  'scripts-render.html',
  'scripts-generate-fix.html',
  'scripts-fix-pr.html',
];

function bootWindow(bodyHtml) {
  const win = makeWindow(bodyHtml);
  runInWindow(win, [
    'window.SLScroll = function() { return { update: function() {} }; };',
    'window.showConfirm = function() {};',
    'window.baInitTokenUsageBar = function() {};',
    'if (!window.CSS) { window.CSS = { escape: function (v) { return String(v); } }; }',
  ].join('\n'));
  const code = PARTS.map((file) => extractInlineScripts(resolve(FRAGMENTS, file))).join('\n;\n');
  runInWindow(win, code);
  win.fetch = () => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
  return win;
}

const FINDING = {
  id: 'f-1',
  severity: 'CRITICAL',
  type: 'MISSING_AUTH',
  file: 'src/main/kotlin/com/stacklens/adapter/inbound/rest/AdminController.kt',
  line: 15,
  description: 'Der Endpoint ist ohne Authentifizierung erreichbar.',
  confirmed: true,
};

function findingWith(id) {
  return Object.assign({}, FINDING, { id });
}

function makeFetchQueue() {
  const calls = [];
  const responses = [];
  const fn = (url, init) => {
    calls.push({ url, init });
    const next = responses.shift();
    return next ? next() : Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({}) });
  };
  fn.calls = calls;
  fn.respondWith = (body, init = {}) => {
    responses.push(() => Promise.resolve({
      ok: (init.status ?? 200) < 400,
      status: init.status ?? 200,
      json: () => Promise.resolve(body),
    }));
  };
  fn.rejectWith = (err) => { responses.push(() => Promise.reject(err)); };
  return fn;
}

describe('Fix-PR status is rendered inline at the finding', () => {
  let win;
  let doc;

  beforeEach(() => {
    win = bootWindow('<div id="baMainArea"></div><div id="baFindingsList"></div><div class="ba-repo-card selected"><div class="ba-card-status"></div></div>');
    doc = win.document;
    win.baCurrentAnalysisId = 7;
    win.baAllFindings = [findingWith('f-1'), findingWith('f-2')];
    win._baFixPrDetails = [];
    win._baFixPrRunning = 0;
    win._baFixFilter = 'ALL';
  });

  it('renders an idle fix row with the generate button and no banner markup', () => {
    doc.getElementById('baFindingsList').innerHTML = win.baRenderFinding(findingWith('f-1'), 0);
    const row = doc.querySelector('.ba-fix-row');
    expect(row).not.toBeNull();
    expect(row.getAttribute('data-fix-fid')).toBe('f-1');
    expect(row.querySelector('.ba-btn-fix')).not.toBeNull();
    expect(row.getAttribute('tabindex')).toBe('0');
    expect(doc.querySelector('.ba-fixpr-banner')).toBeNull();
    expect(doc.querySelector('.ba-fix-pr-badge')).toBeNull();
  });

  it('keeps the finding body nesting intact', () => {
    doc.getElementById('baFindingsList').innerHTML = win.baRenderFinding(findingWith('f-1'), 0);
    const row = doc.querySelector('.ba-fix-row');
    expect(row.parentElement.classList.contains('ba-finding-body')).toBe(true);
    expect(doc.querySelectorAll('.ba-finding').length).toBe(1);
  });

});

describe('Fix-PR state derivation and progress strip', () => {
  let win;

  beforeEach(() => {
    win = bootWindow('<div id="baMainArea"></div><div id="baFindingsList"></div>');
    win.baCurrentAnalysisId = 7;
    win._baFixPrDetails = [];
    win._baFixPrRunning = 0;
    win._baFixFilter = 'ALL';
  });

  it('classifies entries by their real fields', () => {
    expect(win._baFixStateOf(null)).toBe('idle');
    expect(win._baFixStateOf({ findingId: 1, startedAt: 1, inFlight: true })).toBe('running');
    expect(win._baFixStateOf({ findingId: 1, prUrl: 'https://x/merge_requests/9' })).toBe('done');
    expect(win._baFixStateOf({ findingId: 1, localDone: true })).toBe('done');
    expect(win._baFixStateOf({ findingId: 1, error: 'boom' })).toBe('error');
    expect(win._baFixStateOf({ findingId: 1, startedAt: 1 })).toBe('interrupted');
    expect(win._baFixStateOf({ findingId: 1, startedAt: 1, inFlight: true, interrupted: true })).toBe('interrupted');
  });

  it('counts every state and derives the running total', () => {
    win._baFixPrDetails = [
      { findingId: 'a', startedAt: 1, inFlight: true },
      { findingId: 'b', prUrl: 'https://x/pull/1' },
      { findingId: 'c', error: 'boom' },
      { findingId: 'd', localDone: true },
      { findingId: 'e', startedAt: 1, finishedAt: 2 },
    ];
    const counts = win._baFixCounts();
    expect(counts).toEqual({ total: 5, running: 1, done: 2, error: 1, interrupted: 1 });
    expect(win._baFixRecomputeRunning()).toBe(1);
  });

  it('renders no strip while nothing has been generated', () => {
    expect(win._baFixStripHtml()).toBe('');
  });

  it('renders the strip with counts, filter chips and the clock', () => {
    win.document.getElementById('baFindingsList').innerHTML = win.baRenderFinding(findingWith('f-1'), 0);
    win._baFixPrDetails = [
      { findingId: 'a', startedAt: Date.now() - 65_000, inFlight: true },
      { findingId: 'b', prUrl: 'https://gitlab.example/acme/shop/-/merge_requests/7' },
      { findingId: 'c', error: 'boom' },
      { findingId: 'd', startedAt: 1, finishedAt: 2 },
    ];
    const html = win._baFixStripHtml();
    expect(html).toContain('ba-fix-strip');
    expect(html).toContain('data-fix-filter="running"');
    expect(html).toContain('data-fix-filter="done"');
    expect(html).toContain('data-fix-filter="error"');
    expect(html).toContain('data-fix-filter="interrupted"');
    expect(html).toContain('data-fix-act="jump"');
    expect(html).toContain('data-fix-act="clear"');
    expect(html).toContain('01:05');
    expect(html).toContain('<b>1</b>');
    expect(html).not.toContain('disabled');
  });

  it('disables the filter and jump chips while no finding is rendered', () => {
    win._baFixPrDetails = [{ findingId: 'a', startedAt: Date.now() - 65_000, inFlight: true }];
    const html = win._baFixStripHtml();
    expect(html).toContain('data-fix-filter="running" aria-pressed="false" disabled');
    expect(html).toContain('data-fix-act="jump" disabled');
    expect(html).toContain('aria-disabled="true"');
    expect(html).toContain('Befunde erscheinen erst nach Abschluss der Analyse');
    expect(html).toContain('data-fix-act="clear"');
    expect(html.match(/data-fix-act="clear"[^>]*/)[0]).not.toContain('disabled');
  });

  it('offers an interrupted chip instead of a running clock when no request is open', () => {
    win._baFixPrDetails = [{ findingId: 'a', startedAt: 1, finishedAt: 61_000 }];
    const html = win._baFixStripHtml();
    expect(html).toContain('data-fix-filter="interrupted"');
    expect(html).not.toContain('data-fix-act="jump"');
    expect(html).toContain('keine laufenden Fixes');
  });

  it('shortens MR and PR urls to their platform notation', () => {
    expect(win._baFixShortUrl('https://gitlab.example/a/b/-/merge_requests/12')).toBe('MR\u00a0!12');
    expect(win._baFixShortUrl('https://github.com/a/b/pull/3')).toBe('PR\u00a0#3');
  });

  it('formats elapsed time from the real start and finish stamps', () => {
    const entry = { startedAt: 1_000, finishedAt: 62_000 };
    expect(win._baFixElapsedSeconds(entry)).toBe(61);
    expect(win._baFixClock(61)).toBe('01:01');
    expect(win._baFixClock(0)).toBe('00:00');
  });
});

describe('Fix-PR generation lifecycle drives the row', () => {
  let win;
  let doc;
  let fetchQueue;

  beforeEach(() => {
    win = bootWindow('<div id="baMainArea"></div><div id="baFindingsList"></div><div class="ba-repo-card selected"><div class="ba-card-status"></div></div>');
    doc = win.document;
    win.baCurrentAnalysisId = 7;
    win._baFixPrDetails = [];
    win._baFixPrRunning = 0;
    win._baFixFilter = 'ALL';
    win.baAllFindings = [findingWith('f-1')];
    doc.getElementById('baFindingsList').innerHTML = win.baRenderFinding(findingWith('f-1'), 0);
    fetchQueue = makeFetchQueue();
    win.fetch = fetchQueue;
  });

  function clickButton() {
    return doc.querySelector('.ba-btn-fix');
  }

  it('turns the button into a running row while the request is open', () => {
    fetchQueue.respondWith({ prUrl: 'https://gitlab.example/a/b/-/merge_requests/4' });
    win.baGenerateFix(7, 'f-1', clickButton());

    const row = doc.querySelector('.ba-fix-row');
    expect(row.querySelector('.ba-btn-fix')).toBeNull();
    expect(row.querySelector('.ba-fix-step-act')).not.toBeNull();
    expect(row.querySelector('[data-fix-act="cancel"]')).not.toBeNull();
    expect(win._baFixPrRunning).toBe(1);
    expect(fetchQueue.calls).toHaveLength(1);
    expect(fetchQueue.calls[0].url).toContain('/analysis/7/finding/f-1/generate-fix');
  });

  it('shows the created merge request with a short label once the response arrives', async () => {
    fetchQueue.respondWith({ prUrl: 'https://gitlab.example/a/b/-/merge_requests/4', explanation: 'Auth-Filter ergänzt' });
    win.baGenerateFix(7, 'f-1', clickButton());
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));

    const row = doc.querySelector('.ba-fix-row');
    expect(row.querySelector('.ba-fix-step-err')).toBeNull();
    expect(row.textContent).toContain('MR\u00a0!4');
    expect(row.textContent).toContain('Auth-Filter ergänzt');
    expect(win._baFixPrRunning).toBe(0);
    expect(win._baFixCounts().done).toBe(1);
    expect(doc.querySelector('.ba-card-fixchip').textContent).toContain('alle Fixes fertig');
  });

  it('reports a server error with a retry action instead of a banner', async () => {
    fetchQueue.respondWith({ error: 'GitLab-Token ohne write_repository' }, { status: 500 });
    win.baGenerateFix(7, 'f-1', clickButton());
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));

    const row = doc.querySelector('.ba-fix-row');
    expect(row.querySelector('.ba-fix-step-err')).not.toBeNull();
    expect(row.textContent).toContain('GitLab-Token ohne write_repository');
    expect(row.querySelector('[data-fix-act="retry"]')).not.toBeNull();
    expect(win._baFixCounts().error).toBe(1);
    expect(doc.getElementById('baFixPrBanner')).toBeNull();
  });

  it('cancelling aborts the request and keeps the finding retryable', async () => {
    let aborted = null;
    win.fetch = () => new Promise((resolveP, rejectP) => { aborted = rejectP; });
    win.baGenerateFix(7, 'f-1', clickButton());
    expect(win._baFixPrRunning).toBe(1);

    win.baCancelFixPr('f-1');
    expect(win._baFixPrRunning).toBe(0);
    const row = doc.querySelector('.ba-fix-row');
    expect(row.textContent).toContain('Vom Benutzer abgebrochen.');
    expect(row.querySelector('[data-fix-act="retry"]')).not.toBeNull();
    expect(typeof aborted).toBe('function');
  });

  it('renders the local fix result with an changes action', async () => {
    fetchQueue.respondWith({
      isLocalRepo: true,
      localChanges: [{ path: 'src/A.kt', code: '+ fun a()' }],
      explanation: 'Direkt im Arbeitsverzeichnis geändert',
    });
    win.baGenerateFix(7, 'f-1', clickButton());
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));

    const row = doc.querySelector('.ba-fix-row');
    expect(row.querySelector('[data-fix-act="changes"]')).not.toBeNull();
    expect(row.textContent).toContain('src/A.kt');
    expect(win._baFixCounts().done).toBe(1);
  });

  it('persists the finished entries per repository key', async () => {
    win.baGitlabConfigId = 3;
    win.baGitlabProjectId = 9;
    win.baCurrentRepoId = null;
    win.baGithubRepoConfigId = null;
    fetchQueue.respondWith({ prUrl: 'https://gitlab.example/a/b/-/merge_requests/5' });
    win.baGenerateFix(7, 'f-1', clickButton());
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));

    const stored = JSON.parse(win.localStorage.getItem('ba_fixpr'));
    expect(stored['gitlab:3:9']).toHaveLength(1);
    expect(stored['gitlab:3:9'][0].prUrl).toContain('merge_requests/5');
    expect(stored['gitlab:3:9'][0].controller).toBeUndefined();
  });
});

describe('Fix state filters the finding list', () => {
  let win;
  let doc;

  beforeEach(() => {
    win = bootWindow('<div id="baMainArea"></div><div id="baFindingsList"></div>');
    win.baCurrentAnalysisId = 7;
    win._baFixPrDetails = [];
    win._baFixFilter = 'ALL';
    win.baActiveSevFilter = 'ALL';
    win.baActiveTypeFilter = 'ALL';
    doc = win.document;
    doc.getElementById('baFindingsList').innerHTML =
      win.baRenderFinding(findingWith('f-1'), 0) + win.baRenderFinding(findingWith('f-2'), 1);
  });

  function visibleIds() {
    return Array.from(doc.querySelectorAll('#baFindingsList .ba-finding'))
      .filter((el) => el.style.display !== 'none')
      .map((el) => el.dataset.fixId);
  }

  it('shows every finding while no fix filter is active', () => {
    win.baApplyFilters();
    expect(visibleIds()).toEqual(['f-1', 'f-2']);
  });

  it('keeps only the failed findings when the error filter is on', () => {
    win._baFixPrDetails = [{ findingId: 'f-2', error: 'boom' }];
    win._baFixFilter = 'error';
    win.baApplyFilters();
    expect(visibleIds()).toEqual(['f-2']);
  });

  it('treats findings without an entry as open', () => {
    win._baFixPrDetails = [{ findingId: 'f-2', prUrl: 'https://x/pull/2' }];
    win._baFixFilter = 'open';
    win.baApplyFilters();
    expect(visibleIds()).toEqual(['f-1']);
  });

  it('combines the fix filter with the severity filter', () => {
    win._baFixPrDetails = [{ findingId: 'f-1', prUrl: 'https://x/pull/1' }];
    win._baFixFilter = 'done';
    win.baActiveSevFilter = 'HIGH';
    win.baApplyFilters();
    expect(visibleIds()).toEqual([]);
  });
});

describe('Fix-PR restore keeps a single source of truth', () => {
  let win;

  beforeEach(() => {
    win = bootWindow('<div id="baMainArea"></div>');
    win._baFixPrDetails = [];
    win._baFixPrRunning = 0;
  });

  it('restores entries and recomputes the running counter', () => {
    const restored = win._baFixRestoreState([
      { findingId: 'a', startedAt: 1, inFlight: true },
      { findingId: 'b', prUrl: 'https://x/pull/1' },
      { findingId: 'c', localDone: true },
      null,
      { label: 'ohne findingId' },
    ]);
    expect(restored.map((d) => d.findingId)).toEqual(['a', 'b', 'c']);
    expect(win._baFixPrRunning).toBe(1);
    expect(win._baFixFilter).toBe('ALL');
  });

  it('downgrades restored entries without a live request to interrupted', () => {
    win.baCurrentRepoId = 5;
    win.baGitlabConfigId = null;
    win.baGitlabProjectId = null;
    win.baGithubRepoConfigId = null;
    const restored = win._baFixRestoreState([
      { findingId: 'a', startedAt: Date.now() - 68 * 60 * 1000, step: 'Fix wird generiert…' },
    ]);
    expect(win._baFixStateOf(restored[0])).toBe('interrupted');
    expect(restored[0].ownerRepoKey).toBe('local:5');
    expect(win._baFixCounts()).toEqual({ total: 1, running: 0, done: 0, error: 0, interrupted: 1 });
    expect(win._baFixRecomputeRunning()).toBe(0);
  });

  it('keeps an entry that still owns an open request running across a restore', () => {
    win.baCurrentRepoId = 5;
    win.baGitlabConfigId = null;
    win.baGitlabProjectId = null;
    win.baGithubRepoConfigId = null;
    const live = { findingId: 'a', startedAt: Date.now(), inFlight: true };
    win._baFixRestoreState([live]);
    expect(win._baFixStateOf(live)).toBe('running');
    expect(live.interrupted).toBeUndefined();
  });

  it('clears everything when no state is stored', () => {
    win._baFixPrDetails = [{ findingId: 'a', startedAt: 1 }];
    win._baFixRestoreState(null);
    expect(win._baFixPrDetails).toEqual([]);
    expect(win._baFixPrRunning).toBe(0);
  });
});

describe('Removed banner and overlay code stays removed', () => {
  const sources = PARTS.map((file) => readFileSync(resolve(FRAGMENTS, file), 'utf8')).join('\n');
  const styles = readFileSync(resolve(FRAGMENTS, 'styles.html'), 'utf8');

  it.each([
    ['baFixPrBanner', 'the duplicate top banner'],
    ['ba-fix-pr-badge', 'the duplicated sidebar badge'],
    ['baShowFixPrDetail', 'the click-away detail overlay'],
    ['_baFixPrShowDone', 'the second done flag'],
  ])('does not reference %s (%s)', (symbol) => {
    expect(sources).not.toContain(symbol);
  });

  it('ships no css for the removed banner and overlay', () => {
    expect(styles).not.toContain('ba-fixpr-banner');
    expect(styles).not.toContain('ba-fixpr-detail');
    expect(styles).toContain('.ba-fix-strip');
  });

  it('drops the hardcoded banner colours', () => {
    expect(styles).not.toMatch(/#e8f4fd|#90c8f0/);
  });

  it('colours the new strip and rows through theme tokens only', () => {
    const rules = styles.match(/^\s*\.ba-(?:fix-(?:strip|track|chip|row|cap|step|stage|res|acts?|meta|link-btn|result|finding)[^\n]*|card-fixchip[^\n]*)\s*\{[^}]*\}/gm) || [];
    expect(rules.length).toBeGreaterThan(20);
    const offenders = rules.filter((rule) => /#(?!fff\b|ffffff\b)[0-9a-fA-F]{3,6}/.test(rule));
    expect(offenders).toEqual([]);
  });
});

describe('Running fix row states the expected duration', () => {
  let win;
  let doc;

  beforeEach(() => {
    win = bootWindow('<div id="baMainArea"></div><div id="baFindingsList"></div>');
    doc = win.document;
    win.baCurrentAnalysisId = 7;
    win.baAllFindings = [findingWith('f-1')];
    win._baFixPrDetails = [];
    win._baFixPrRunning = 0;
    win._baFixFilter = 'ALL';
    doc.getElementById('baFindingsList').innerHTML = win.baRenderFinding(findingWith('f-1'), 0);
  });

  it('names the KI step and the measured duration instead of spinning silently', () => {
    win._baFixStartEntry('f-1', 'ConnectionEntity.kt WEAK_INPUT_VALIDATION');
    win._baFixUpdateRow('f-1');

    expect(win._baFixStateOf(win._baFixEntry('f-1'))).toBe('running');
    expect(doc.querySelector('.ba-fix-stage').textContent).toContain('4–10 Minuten');
  });
});

describe('Fix entries stay owned by the repository that started them', () => {
  let win;
  let doc;

  beforeEach(() => {
    win = bootWindow([
      '<div id="baMainArea"></div><div id="baFindingsList"></div>',
      '<div class="ba-repo-card selected" data-repo-id="5"><div class="ba-card-status"></div></div>',
      '<div class="ba-repo-card" data-repo-id="6"><div class="ba-card-status"></div></div>',
    ].join(''));
    doc = win.document;
    win.baCurrentAnalysisId = 7;
    win.baAllFindings = [findingWith('f-1')];
    win._baFixPrDetails = [];
    win._baFixPrRunning = 0;
    win._baFixFilter = 'ALL';
    win.baGithubRepoConfigId = null;
    win.baGitlabConfigId = null;
    win.baGitlabProjectId = null;
    win.baCurrentRepoId = 5;
    doc.getElementById('baFindingsList').innerHTML = win.baRenderFinding(findingWith('f-1'), 0);
  });

  function selectGitlabRepo() {
    win.baCurrentRepoId = null;
    win.baGitlabConfigId = 7;
    win.baGitlabProjectId = 3;
    win._baFixRestoreState([
      { findingId: 'g-1', prUrl: 'https://gitlab.example/a/b/-/merge_requests/1', startedAt: 1, finishedAt: 2 },
    ]);
  }

  it('stamps a new entry with the repo key and an open request handle', () => {
    const entry = win._baFixStartEntry('f-1', 'AdminEndpoint:15 MISSING_CONTROL');
    expect(entry.ownerRepoKey).toBe('local:5');
    expect(entry.inFlight).toBe(true);
    expect(win._baFixStateOf(entry)).toBe('running');
  });

  it('persists a completion that arrives after the repository switched', () => {
    const entry = win._baFixStartEntry('f-1', 'AdminEndpoint:15 MISSING_CONTROL');
    win._baUpdateFixPrBadge();
    selectGitlabRepo();

    entry.prUrl = 'https://gitlab.example/demo/shop/-/merge_requests/4';
    win._baFixFinish(entry);

    const stored = JSON.parse(win.localStorage.getItem('ba_fixpr'));
    expect(stored['local:5'][0].prUrl).toContain('merge_requests/4');
    expect(stored['local:5'][0].inFlight).toBeUndefined();
    expect(stored['local:5'][0].interrupted).toBeNull();
    expect(stored['gitlab:7:3']).toHaveLength(1);
  });

  it('marks open entries as interrupted when the page is hidden', () => {
    const entry = win._baFixStartEntry('f-1', 'AdminEndpoint:15 MISSING_CONTROL');
    win._baUpdateFixPrBadge();
    win._baFixWireLifecycle();

    win.dispatchEvent(new win.Event('pagehide'));

    expect(win._baFixStateOf(entry)).toBe('interrupted');
    expect(win._baFixCounts().running).toBe(0);
    const stored = JSON.parse(win.localStorage.getItem('ba_fixpr'));
    expect(stored['local:5'][0].interrupted).toBe(true);
    expect(stored['local:5'][0].inFlight).toBeUndefined();
  });

  it('wires the pagehide listener only once per document', () => {
    win._baFixWireLifecycle();
    win._baFixWireLifecycle();
    expect(win.__baFixLifecycleWired).toBe(true);
  });

  it('clears interrupted and running entries and aborts the open request', () => {
    const entry = win._baFixStartEntry('f-1', 'AdminEndpoint:15 MISSING_CONTROL');
    let aborted = false;
    entry.controller = { abort: () => { aborted = true; } };
    win._baFixPrDetails.push({ findingId: 'f-2', startedAt: 1, finishedAt: 2 });
    win._baUpdateFixPrBadge();
    expect(doc.querySelector('.ba-fix-strip')).not.toBeNull();

    win.baClearFixPrHistory();

    expect(aborted).toBe(true);
    expect(win._baFixPrDetails).toHaveLength(0);
    expect(JSON.parse(win.localStorage.getItem('ba_fixpr'))['local:5']).toBeUndefined();
    expect(doc.querySelector('.ba-fix-strip')).toBeNull();
  });

  it('removes the fix chip from a card that lost the selection', () => {
    win._baFixPrDetails = [{ findingId: 'f-1', startedAt: Date.now(), inFlight: true, ownerRepoKey: 'local:5' }];
    const first = doc.querySelector('.ba-repo-card.selected');
    win._baFixPaintCardChip();
    expect(first.querySelector('.ba-card-fixchip')).not.toBeNull();

    first.classList.remove('selected');
    const next = doc.querySelector('.ba-repo-card[data-repo-id="6"]');
    next.classList.add('selected');
    win._baFixPaintCardChip();

    expect(first.querySelector('.ba-card-fixchip')).toBeNull();
    expect(next.querySelector('.ba-card-fixchip')).not.toBeNull();
  });
});

describe('Fix progress strip stays an opaque sticky overlay', () => {
  const THEME_FILES = ['dark', 'legacy', 'modern', 'popos'];

  const STYLE_SOURCES = [
    ['security analysis base styles', readFileSync(resolve(FRAGMENTS, 'styles.html'), 'utf8')],
    ...THEME_FILES.map((theme) => [
      `${theme} theme`,
      readFileSync(resolve(THEMES, `theme-${theme}.css`), 'utf8'),
    ]),
  ];

  const SURFACE_SOURCES = [
    ['custom.css', readFileSync(resolve(CSS_DIR, 'custom.css'), 'utf8')],
    ...THEME_FILES.map((theme) => [
      `theme-${theme}.css`,
      readFileSync(resolve(THEMES, `theme-${theme}.css`), 'utf8'),
    ]),
  ];

  function stripRules(source) {
    return source.match(/[^{}]*\.ba-fix-strip(?![-\w])[^{}]*\{[^}]*\}/g) || [];
  }

  function backgroundOf(rule) {
    return (rule.match(/background(?:-color)?\s*:\s*([^;]+);/) || [])[1];
  }

  function carriesAlpha(value) {
    return /rgba\(|hsla\(|\btransparent\b|\/\s*[\d.]+%/.test(value);
  }

  it('pins the strip to the top of the scrolling result area', () => {
    const rules = stripRules(STYLE_SOURCES[0][1]);
    expect(rules).toHaveLength(1);
    expect(rules[0]).toMatch(/position:\s*sticky/);
    expect(rules[0]).toMatch(/top:\s*0/);
  });

  it.each(STYLE_SOURCES)('fills the strip opaquely in the %s', (label, source) => {
    const backgrounds = stripRules(source).map(backgroundOf);
    expect(backgrounds.length).toBeGreaterThan(0);
    backgrounds.forEach((background) => {
      expect(background, `${label} must declare a strip background`).toBeTruthy();
      expect(carriesAlpha(background), `${label} strip background must be opaque: ${background}`).toBe(false);
    });
  });

  it.each(SURFACE_SOURCES)('keeps the paper surface token opaque in %s', (file, source) => {
    const tokens = source.match(/--app-cream:\s*[^;]+;/g) || [];
    expect(tokens.length).toBeGreaterThan(0);
    tokens.forEach((token) => expect(carriesAlpha(token), `${file}: ${token}`).toBe(false));
  });
});

describe('Fix-PR translations exist in both languages', () => {
  const de = JSON.parse(readFileSync(resolve(I18N, 'de.json'), 'utf8')).bug_analysis;
  const en = JSON.parse(readFileSync(resolve(I18N, 'en.json'), 'utf8')).bug_analysis;
  const keys = [
    'fix_strip_label', 'fix_cap', 'fix_step_request', 'fix_step_generate', 'fix_step_result',
    'fix_filter_all', 'fix_filter_active', 'fix_filter_done', 'fix_filter_error',
    'fix_running_since', 'fix_failed_stage', 'fix_done_in', 'fix_idle_hint', 'fix_files_suffix',
    'fix_retry', 'fix_jump_active', 'fix_since', 'fix_runs', 'fix_no_runs',
    'fix_filter_interrupted', 'fix_interrupted_after', 'fix_card_interrupted',
    'fix_a_interrupted', 'fix_filter_needs_findings',
    'fix_card_running', 'fix_card_done', 'fix_card_error',
    'fix_a_running', 'fix_a_done', 'fix_a_error',
  ];

  it.each(keys)('defines bug_analysis.%s', (key) => {
    expect(typeof de[key]).toBe('string');
    expect(de[key].length).toBeGreaterThan(0);
    expect(typeof en[key]).toBe('string');
    expect(en[key].length).toBeGreaterThan(0);
  });
});
