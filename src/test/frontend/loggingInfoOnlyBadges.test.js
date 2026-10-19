import { describe, it, expect, beforeEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { PROJECT_ROOT } from './_load.js';
import { resolve } from 'node:path';
import { readFileSync } from 'node:fs';

describe('logging-fix: only-INFO badges do not enable fix button', () => {
  let dom, window;

  function loadScript(file) {
    const html = readFileSync(
      resolve(PROJECT_ROOT, `src/main/resources/templates/fragments/logging/${file}`),
      'utf8',
    );
    const m = html.match(/<script[^>]*>([\s\S]*?)<\/script>/);
    if (!m) return '';
    let body = m[1];
    const names = new Set();
    for (const x of body.matchAll(/^\s*function\s+([A-Za-z_$][\w$]*)\s*\(/gm)) names.add(x[1]);
    for (const x of body.matchAll(/^\s*(?:var|let|const)\s+([A-Za-z_$][\w$]*)\s*=\s*function\b/gm)) names.add(x[1]);
    for (const x of body.matchAll(/^\s*window\.([A-Za-z_$][\w$]*)\s*=\s*[A-Za-z_$][\w$]*\s*;/gm)) names.add(x[1]);
    for (const name of names) {
      body += `\ntry { if (typeof ${name} !== 'undefined') window.${name} = ${name}; } catch (_) {}\n`;
    }
    return body;
  }

  beforeEach(() => {
    dom = new JSDOM(
      `<!DOCTYPE html><html lang="de"><body>
        <div id="lgResult" class="lg-result visible">
          <div id="lgSummaryBadges"></div>
          <div class="lg-fix-toolbar" style="">
            <button id="lgBtnStartFix" disabled><span data-i18n="logging.fix_btn_label_count">Fix (0)</span></button>
          </div>
        </div>
        <div id="lgLocalRepoList"></div>
        <div id="lgNoRepoHint" style="display:none;">x</div>
        <div id="lgRepoMismatchWarn" style="display:none;"></div>
        <span id="lgRepoMismatchLogStacks"></span>
        <span id="lgRepoMismatchRepoStacks"></span>
      </body></html>`,
      { url: 'http://localhost/logging' },
    );
    window = dom.window;
    global.document = window.document;
    global.window = window;
    global.fetch = () => Promise.resolve({ ok: true, json: async () => ({}) });

    window.eval(loadScript('scripts-repo.html'));
    window.eval(loadScript('scripts-fix.html'));
    window.eval(loadScript('scripts-stack-detection.html'));
    window._lgStack = { logStacks: [], logIndicators: [], repoStacks: [], repoIndicators: [], repoMatch: 'unknown' };
    window._lgLastResult = { lines: [] };
  });

  it('counts 0 active fix-relevant badges when only INFO badge is active', () => {
    document.getElementById('lgSummaryBadges').innerHTML =
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="info">INFO<span class="lg-summary-badge-count">3</span></button>';
    expect(lgGetActiveBadgeCount()).toBe(0);
  });

  it('counts 1 active badge when both ERROR and INFO are active', () => {
    document.getElementById('lgSummaryBadges').innerHTML =
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="error">ERROR<span class="lg-summary-badge-count">1</span></button>' +
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="info">INFO<span class="lg-summary-badge-count">3</span></button>';
    expect(lgGetActiveBadgeCount()).toBe(1);
  });

  it('counts multiple fix-relevant badges correctly', () => {
    document.getElementById('lgSummaryBadges').innerHTML =
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="critical">CRITICAL<span class="lg-summary-badge-count">1</span></button>' +
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="error">ERROR<span class="lg-summary-badge-count">2</span></button>' +
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="warn">WARN<span class="lg-summary-badge-count">5</span></button>' +
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="info">INFO<span class="lg-summary-badge-count">10</span></button>';
    expect(lgGetActiveBadgeCount()).toBe(3);
  });

  it('counts 0 when no fix-relevant badges are active', () => {
    document.getElementById('lgSummaryBadges').innerHTML =
      '<button class="lg-summary-badge lg-summary-badge-inactive" data-category="error">ERROR<span class="lg-summary-badge-count">1</span></button>' +
      '<button class="lg-summary-badge lg-summary-badge-inactive" data-category="warn">WARN<span class="lg-summary-badge-count">2</span></button>';
    expect(lgGetActiveBadgeCount()).toBe(0);
  });

  it('fix button stays disabled when only INFO badge is active + repo selected + result visible', () => {
    lgAddLocalRepo({ id: 1, name: 'r', path: '/r', active: true, scope: 'LOGGING_ANALYSIS' });
    document.querySelector('#lgLocalRepoList .lg-local-repo-card').classList.add('selected');
    window._lgFix.selectedRepoId = 1;
    document.getElementById('lgResult').classList.add('visible');
    document.getElementById('lgSummaryBadges').innerHTML =
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="info">INFO<span class="lg-summary-badge-count">5</span></button>';
    lgUpdateFixButtonState();
    const toolbar = document.querySelector('.lg-fix-toolbar');
    const btn = document.getElementById('lgBtnStartFix');
    expect(btn.disabled).toBe(true);
    expect(toolbar.style.display).toBe('none');
  });

  it('fix button becomes enabled when at least one fix-relevant badge is active', () => {
    lgAddLocalRepo({ id: 1, name: 'r', path: '/r', active: true, scope: 'LOGGING_ANALYSIS' });
    document.querySelector('#lgLocalRepoList .lg-local-repo-card').classList.add('selected');
    window._lgFix.selectedRepoId = 1;
    document.getElementById('lgResult').classList.add('visible');
    document.getElementById('lgSummaryBadges').innerHTML =
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="error">ERROR<span class="lg-summary-badge-count">1</span></button>' +
      '<button class="lg-summary-badge lg-summary-badge-active" data-category="info">INFO<span class="lg-summary-badge-count">5</span></button>';
    lgUpdateFixButtonState();
    const toolbar = document.querySelector('.lg-fix-toolbar');
    const btn = document.getElementById('lgBtnStartFix');
    expect(btn.disabled).toBe(false);
    expect(toolbar.style.display).not.toBe('none');
    expect(btn.textContent).toContain('(1)');
  });
});
