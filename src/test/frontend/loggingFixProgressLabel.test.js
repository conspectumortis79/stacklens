import { describe, it, expect } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const FIX_SCRIPTS = resolve(
  here,
  '../../main/resources/templates/fragments/logging/scripts-fix.html',
);
const I18N_SCRIPT = resolve(here, '../../main/resources/static/js/i18n.js');

const STAR = '*';
const CDATA_OPEN = '/' + STAR + '<![CDATA[' + STAR + '/]';
const CDATA_CLOSE = '/' + STAR + ']]>' + STAR + '/';

const TRANSLATIONS = {
  logging: {
    fix_status_pending: 'Ausstehend',
    fix_status_running: 'Läuft…',
    fix_status_running_with_stage: 'Stage läuft: {0}',
    fix_status_done: 'Abgeschlossen',
    fix_status_error: 'Fehler',
    fix_status_cancelled: 'Abgebrochen',
    fix_status_skipped: 'Übersprungen',
    fix_poll_gone: 'Diese Analyse existiert nicht mehr — Server neu gestartet oder Analyse gelöscht.',
    fix_poll_lost: 'Keine Antwort vom Server — die Pipeline-Anzeige wird nicht mehr aktualisiert.',
    fix_stage_diagnose: '① Diagnose — Root Cause',
    fix_stage_plan: '② Architektur & Coding-Guidelines',
    fix_stage_code: '③ Code-Fix',
    fix_stage_tests: '④ Unit- & Systemtests',
  },
};

function productionTranslator() {
  const source = readFileSync(I18N_SCRIPT, 'utf8');
  const match = source.match(/function t\(key\) \{[\s\S]*?\n  \}/);
  if (!match) throw new Error('cannot find t(key) in i18n.js');
  return new Function('_t', match[0] + '\nreturn t;')(TRANSLATIONS);
}

function stripCdata(body) {
  let trimmed = body.trim();
  if (trimmed.startsWith(CDATA_OPEN)) trimmed = trimmed.slice(CDATA_OPEN.length);
  if (trimmed.endsWith(CDATA_CLOSE)) trimmed = trimmed.slice(0, trimmed.length - CDATA_CLOSE.length);
  return trimmed;
}

const STAGE_MARKUP = ['diagnose', 'plan', 'code', 'tests']
  .map(
    (stage) => `
      <div class="lg-fix-stage" data-stage="${stage}">
        <div class="lg-fix-stage-head lg-fix-stage-pending"><i class="lg-fix-stage-icon"></i></div>
        <div class="lg-fix-stage-badge lg-fix-stage-badge-pending"></div>
        <div class="lg-fix-stage-body"></div>
      </div>`,
  )
  .join('');

function bootFixScripts() {
  const html = readFileSync(FIX_SCRIPTS, 'utf8');
  const scriptMatch = html.match(/<script\s+th:inline="none">([\s\S]*?)<\/script>/);
  if (!scriptMatch) throw new Error('cannot find inline script in scripts-fix.html');
  const scripts =
    'var lgUpdateFixButtonState = function () {};\n' +
    'window.lgUpdateFixButtonState = lgUpdateFixButtonState;\n' +
    'window.lgGetSelectedRepoId = function () { return null; };\n' +
    stripCdata(scriptMatch[1]);

  const dom = new JSDOM(
    `<body>
      <div class="lg-card" id="lgResult">
        ${STAGE_MARKUP}
        <span id="lgFixStatusText" class="lg-fix-status-text">Warte auf Start…</span>
        <div id="lgFixError" style="display:none"></div>
        <button id="lgBtnFixWrite" disabled></button>
      </div>
    </body>`,
    { url: 'http://localhost/logging' },
  );
  const win = dom.window;
  global.document = win.document;
  global.window = win;
  win.i18n = { t: productionTranslator(), getLang: () => 'de' };
  win.eval(scripts);
  return win;
}

function statusText(win) {
  return win.document.getElementById('lgFixStatusText').textContent;
}

function stageElement(win, stage) {
  return win.document.querySelector(`.lg-fix-stage[data-stage="${stage}"]`);
}

function badgeText(win, stage) {
  return stageElement(win, stage).querySelector('.lg-fix-stage-badge').textContent;
}

describe('logging fix: live progress label in #lgFixStatusText while pipeline runs', () => {
  it('shows "Stage läuft: ② Architektur & Coding-Guidelines" while currentStage=plan', () => {
    const win = bootFixScripts();
    lgRenderFixStatus('RUNNING', 'plan');
    expect(statusText(win)).toBe('Stage läuft: ② Architektur & Coding-Guidelines');
  });

  it('shows "Stage läuft: ① Diagnose — Root Cause" while currentStage=diagnose', () => {
    const win = bootFixScripts();
    lgRenderFixStatus('RUNNING', 'diagnose');
    expect(statusText(win)).toBe('Stage läuft: ① Diagnose — Root Cause');
  });

  it('falls back to generic "Läuft…" when currentStage is missing or unknown', () => {
    const win = bootFixScripts();
    lgRenderFixStatus('RUNNING', '');
    expect(statusText(win)).toBe('Läuft…');
    lgRenderFixStatus('RUNNING', null);
    expect(statusText(win)).toBe('Läuft…');
  });

  it('shows the terminal text once the status reaches DONE / ERROR / CANCELLED', () => {
    const win = bootFixScripts();
    lgRenderFixStatus('DONE');
    expect(statusText(win)).toBe('Abgeschlossen');
    lgRenderFixStatus('ERROR');
    expect(statusText(win)).toBe('Fehler');
    lgRenderFixStatus('CANCELLED');
    expect(statusText(win)).toBe('Abgebrochen');
  });
});

describe('logging fix: per-stage badges render every server status', () => {
  it('renders the SKIPPED badge instead of leaving a skipped stage at "Ausstehend"', () => {
    const win = bootFixScripts();
    lgRenderFixStage('plan', 'SKIPPED', '[Plan-Stage übersprungen — Diagnose nicht möglich]');
    expect(badgeText(win, 'plan')).toBe('Übersprungen');
    const stage = stageElement(win, 'plan');
    expect(stage.className).toContain('lg-fix-stage-skipped');
    expect(stage.querySelector('.lg-fix-stage-badge').className).toContain(
      'lg-fix-stage-badge-skipped',
    );
    expect(stage.className).toContain('lg-fix-stage-open');
  });

  it('renders RUNNING, DONE and PENDING badges', () => {
    const win = bootFixScripts();
    lgRenderFixStage('diagnose', 'RUNNING', 'x');
    expect(badgeText(win, 'diagnose')).toBe('Läuft…');
    lgRenderFixStage('diagnose', 'DONE', 'x');
    expect(badgeText(win, 'diagnose')).toBe('Abgeschlossen');
    lgRenderFixStage('code', 'PENDING', '');
    expect(badgeText(win, 'code')).toBe('Ausstehend');
  });
});

describe('logging fix: a restarted pipeline clears the previous run from the panel', () => {
  it('lgResetFixStages clears bodies, badges and the expanded state of every stage', () => {
    const win = bootFixScripts();
    lgRenderFixStage('diagnose', 'DONE', 'old output');
    lgResetFixStages();
    const stage = stageElement(win, 'diagnose');
    expect(stage.querySelector('.lg-fix-stage-body').textContent).toBe('');
    expect(stage.className).not.toContain('lg-fix-stage-open');
    expect(badgeText(win, 'diagnose')).toBe('Ausstehend');
  });
});

function bootPolling() {
  const win = bootFixScripts();
  win._lgFix = {
    runId: 7,
    pollTimer: 1234,
    pollFailures: 0,
    timerInterval: 5678,
    timerStartedAt: 0,
    cancelled: false,
  };
  global.window._lgFix = win._lgFix;
  return win;
}

function response(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status: status,
    json: () => Promise.resolve(body),
  };
}

async function settle() {
  for (let i = 0; i < 8; i += 1) {
    await new Promise((r) => setTimeout(r, 0));
  }
}

function errorText(win) {
  const el = win.document.getElementById('lgFixError');
  return { visible: el.style.display !== 'none', text: el.textContent };
}

describe('logging fix: a poll that cannot reach the server must end the run display', () => {
  it('renders the terminal status and stops the timers on DONE', async () => {
    const win = bootPolling();
    global.fetch = () => Promise.resolve(response(200, { status: 'DONE', currentStage: '', stages: {} }));
    lgPollFix();
    await settle();
    expect(statusText(win)).toBe('Abgeschlossen');
    expect(win._lgFix.pollTimer).toBeNull();
    expect(win._lgFix.timerInterval).toBeNull();
  });

  it('reports a vanished run on 404 instead of freezing the panel', async () => {
    const win = bootPolling();
    global.fetch = () => Promise.resolve(response(404, null));
    lgPollFix();
    await settle();
    expect(errorText(win).visible).toBe(true);
    expect(errorText(win).text).toContain('existiert nicht mehr');
    expect(win._lgFix.pollTimer).toBeNull();
    expect(win._lgFix.timerInterval).toBeNull();
  });

  it('gives up after repeated transport failures and says so', async () => {
    const win = bootPolling();
    global.fetch = () => Promise.reject(new Error('network down'));
    for (let i = 0; i < 5; i += 1) {
      lgPollFix();
      await settle();
    }
    expect(errorText(win).visible).toBe(true);
    expect(errorText(win).text).toContain('Keine Antwort vom Server');
    expect(win._lgFix.pollTimer).toBeNull();
  });

  it('keeps polling through a single hiccup', async () => {
    const win = bootPolling();
    global.fetch = () => Promise.reject(new Error('network down'));
    lgPollFix();
    await settle();
    expect(errorText(win).visible).toBe(false);
    expect(win._lgFix.pollFailures).toBe(1);
  });
});
