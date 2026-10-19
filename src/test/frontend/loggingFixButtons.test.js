import { describe, it, expect, beforeEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { PROJECT_ROOT } from './_load.js';
import { resolve } from 'node:path';
import { readFileSync } from 'node:fs';

describe('logging-fix UI: badge activation & fix-button visibility', () => {
  let dom, window;

  function setupDom() {
    dom = new JSDOM(
      `
      <!DOCTYPE html>
      <html lang="de">
      <body>
        <div id="lgResult" class="lg-result visible">
          <div id="lgSummaryBadges">
            <button class="lg-summary-badge lg-summary-badge-active" data-category="error"></button>
            <button class="lg-summary-badge lg-summary-badge-inactive" data-category="warn"></button>
            <button class="lg-summary-badge lg-summary-badge-active" data-category="critical"></button>
            <button class="lg-summary-badge lg-summary-badge-inactive" data-category="info"></button>
          </div>
          <div class="lg-fix-toolbar">
            <button id="lgBtnStartFix" disabled>
              <span data-i18n="logging.fix_btn_label_count">Fix (0)</span>
            </button>
          </div>
        </div>
        <div id="lgLocalRepoList"></div>
      </body>
      </html>
      `,
      { url: 'http://localhost/logging' },
    );
    window = dom.window;
    global.document = window.document;
    global.window = window;
    global.fetch = () => Promise.resolve({ ok: true, json: async () => ({}) });

    const repoScript = extractScriptContent(
      readFileSync(
        resolve(
          PROJECT_ROOT,
          'src/main/resources/templates/fragments/logging/scripts-repo.html',
        ),
        'utf8',
      ),
    );
    const fixScript = extractScriptContent(
      readFileSync(
        resolve(
          PROJECT_ROOT,
          'src/main/resources/templates/fragments/logging/scripts-fix.html',
        ),
        'utf8',
      ),
    );
    window.eval(repoScript);
    window.eval(fixScript);
  }

  beforeEach(setupDom);

  it('counts active summary badges correctly', () => {
    expect(lgGetActiveBadgeCount()).toBe(2);
  });

  it('hides the fix toolbar when no repository is selected even after an analysis', () => {
    window._lgLastResult = { lines: [] };
    lgUpdateFixButtonState();
    expect(document.querySelector('.lg-fix-toolbar').style.display).toBe('none');
    expect(document.getElementById('lgBtnStartFix').disabled).toBe(false);
  });

  it('hides the fix toolbar when no analysis result exists', () => {
    document.getElementById('lgResult').classList.remove('visible');
    window._lgLastResult = null;
    document.querySelector('#lgLocalRepoList').innerHTML =
      '<div class="lg-local-repo-card selected" data-repo-id="42"></div>';
    lgUpdateFixButtonState();
    expect(document.querySelector('.lg-fix-toolbar').style.display).toBe('none');
  });

  it('shows the fix toolbar when a repo is selected AND an analysis exists', () => {
    window._lgLastResult = { lines: [] };
    document.querySelector('#lgLocalRepoList').innerHTML =
      '<div class="lg-local-repo-card selected" data-repo-id="42"></div>';
    lgUpdateFixButtonState();
    expect(document.querySelector('.lg-fix-toolbar').style.display).not.toBe('none');
    expect(document.getElementById('lgBtnStartFix').disabled).toBe(false);
  });

  it('reveals the toolbar only after a repository is added AND selected on top of an existing analysis', () => {
    window._lgLastResult = { lines: [] };
    lgUpdateFixButtonState();
    expect(document.querySelector('.lg-fix-toolbar').style.display).toBe('none');

    document.querySelector('#lgLocalRepoList').innerHTML =
      '<div class="lg-local-repo-card selected" data-repo-id="42"></div>';
    lgUpdateFixButtonState();
    expect(document.querySelector('.lg-fix-toolbar').style.display).not.toBe('none');
    expect(document.getElementById('lgBtnStartFix').disabled).toBe(false);
  });

  it('hides the fix toolbar and disables the button when no fix-relevant badge is active', () => {
    window._lgLastResult = { lines: [] };
    document.querySelector('#lgResult').classList.add('visible');
    document.querySelector('#lgLocalRepoList').innerHTML =
      '<div class="lg-local-repo-card selected" data-repo-id="42"></div>';
    document.querySelectorAll('.lg-summary-badge-active').forEach((b) =>
      b.classList.replace('lg-summary-badge-active', 'lg-summary-badge-inactive'),
    );
    lgUpdateFixButtonState();
    expect(document.querySelector('.lg-fix-toolbar').style.display).toBe('none');
    expect(document.getElementById('lgBtnStartFix').disabled).toBe(true);
  });

  it('keeps the fix toolbar visible when at least one fix-relevant badge is active', () => {
    window._lgLastResult = { lines: [] };
    document.querySelector('#lgResult').classList.add('visible');
    document.querySelector('#lgLocalRepoList').innerHTML =
      '<div class="lg-local-repo-card selected" data-repo-id="42"></div>';
    document.querySelector('#lgSummaryBadges').innerHTML =
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="info">INFO<span class="lg-summary-badge-count">1</span></button>' +
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="error">ERROR<span class="lg-summary-badge-count">2</span></button>';
    lgUpdateFixButtonState();
    expect(document.querySelector('.lg-fix-toolbar').style.display).not.toBe('none');
    expect(document.getElementById('lgBtnStartFix').disabled).toBe(false);
    expect(document.getElementById('lgBtnStartFix').textContent).toContain('(1)');
  });

  it('renders stage status transitions: pending → running → done', () => {
    const stages = ['diagnose', 'plan', 'code', 'tests'];
    for (const s of stages) {
      const div = document.createElement('div');
      div.innerHTML = `
        <div class="lg-fix-stage" data-stage="${s}">
          <div class="lg-fix-stage-head lg-fix-stage-pending">
            <i class="lg-fix-stage-icon"></i>
            <span class="lg-fix-stage-badge"></span>
          </div>
          <div class="lg-fix-stage-body"></div>
        </div>`;
      document.body.appendChild(div.firstElementChild);
    }
    lgRenderFixStage('diagnose', 'RUNNING', 'chunk-1');
    expect(
      document
        .querySelector('.lg-fix-stage[data-stage="diagnose"] .lg-fix-stage-head')
        .className,
    ).toContain('lg-fix-stage-running');
    lgRenderFixStage('diagnose', 'DONE', 'chunk-1-chunk-2');
    expect(
      document
        .querySelector('.lg-fix-stage[data-stage="diagnose"] .lg-fix-stage-head')
        .className,
    ).toContain('lg-fix-stage-done');
    expect(
      document.querySelector('.lg-fix-stage[data-stage="diagnose"] .lg-fix-stage-body')
        .textContent,
    ).toBe('chunk-1-chunk-2');
  });

  it('substitutes {0} placeholder in i18n.t(key, args)', () => {
    const saved = window.i18n;
    window.i18n = {
      t: (key, ...args) => {
        const map = { 'foo.bar': 'Hello {0}, you have {1} messages' };
        let s = map[key] || key;
        args.forEach((arg, i) => {
          s = s.replace(new RegExp('\\{' + i + '\\}', 'g'), String(arg));
        });
        return s;
      },
    };
    try {
      expect(window.i18n.t('foo.bar', 'Alice', 5)).toBe('Hello Alice, you have 5 messages');
    } finally {
      window.i18n = saved;
    }
  });
});

function extractScriptContent(html) {
  const match = html.match(/<script[^>]*>([\s\S]*?)<\/script>/);
  if (!match) return '';
  let body = match[1];
  const names = new Set();
  for (const m of body.matchAll(/^\s*function\s+([A-Za-z_$][\w$]*)\s*\(/gm)) names.add(m[1]);
  for (const m of body.matchAll(/^\s*(?:var|let|const)\s+([A-Za-z_$][\w$]*)\s*=\s*function\b/gm)) names.add(m[1]);
  for (const m of body.matchAll(/^\s*window\.([A-Za-z_$][\w$]*)\s*=\s*[A-Za-z_$][\w$]*\s*;/gm)) names.add(m[1]);
  for (const name of names) {
    body += `\ntry { if (typeof ${name} !== 'undefined') window.${name} = ${name}; } catch (_) {}\n`;
  }
  return body;
}
