import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { PROJECT_ROOT } from './_load.js';
import { resolve } from 'node:path';
import { readFileSync } from 'node:fs';

describe('logging-fix repo card: select / remove / match-pill lifecycle', () => {
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
      `
      <!DOCTYPE html>
      <html lang="de">
      <body>
        <div id="lgResult" class="lg-result visible">
          <div id="lgSummaryBadges"></div>
          <div class="lg-fix-toolbar" style="">
            <button id="lgBtnStartFix" disabled><span data-i18n="logging.fix_btn_label_count">Fix (0)</span></button>
          </div>
        </div>
        <div id="lgLocalRepoList"></div>
        <div id="lgRepoMismatchWarn" style="display:none;"></div>
        <span id="lgRepoMismatchLogStacks"></span>
        <span id="lgRepoMismatchRepoStacks"></span>
      </body>
      </html>
      `,
      { url: 'http://localhost/logging' },
    );
    window = dom.window;
    global.document = window.document;
    global.window = window;
    global.fetch = vi.fn();

    window.eval(loadScript('scripts-repo.html'));
    window.eval(loadScript('scripts-fix.html'));
    window.eval(loadScript('scripts-stack-detection.html'));

    window._lgFix = window._lgFix || { selectedRepoId: null };
    window._lgStack = { logStacks: [], logIndicators: [], repoStacks: [], repoIndicators: [], repoMatch: 'unknown' };
    window._lgLastResult = { lines: [] };
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('addRepo creates a card with checkbox, icon, name, path and remove button', () => {
    lgAddLocalRepo({ id: 1, name: 'my-checkout-service', path: '/Users/dev/projects/checkout' });
    const card = document.querySelector('#lgLocalRepoList .lg-local-repo-card');
    expect(card).toBeTruthy();
    expect(card.getAttribute('data-repo-id')).toBe('1');
    expect(card.getAttribute('data-repo-path')).toBe('/Users/dev/projects/checkout');
    expect(card.querySelector('.lg-local-repo-checkbox')).toBeTruthy();
    expect(card.querySelector('.lg-local-repo-icon')).toBeTruthy();
    expect(card.querySelector('.lg-local-repo-name').textContent).toBe('my-checkout-service');
    expect(card.querySelector('.lg-local-repo-path').textContent).toBe('/Users/dev/projects/checkout');
    expect(card.querySelector('.lg-local-repo-del')).toBeTruthy();
    expect(card.querySelector('.lg-local-repo-del').textContent).toBe('Entfernen');
  });

  it('clicking the card toggles selection state', () => {
    lgAddLocalRepo({ id: 1, name: 'r', path: '/r' });
    const card = document.querySelector('.lg-local-repo-card');
    expect(card.classList.contains('selected')).toBe(false);
    card.click();
    expect(card.classList.contains('selected')).toBe(true);
    expect(window._lgFix.selectedRepoId).toBe(1);
    card.click();
    expect(card.classList.contains('selected')).toBe(false);
    expect(window._lgFix.selectedRepoId).toBeNull();
  });

  it('only one card can be selected at a time', () => {
    lgAddLocalRepo({ id: 1, name: 'r1', path: '/r1' });
    lgAddLocalRepo({ id: 2, name: 'r2', path: '/r2' });
    const cards = document.querySelectorAll('.lg-local-repo-card');
    cards[0].click();
    expect(cards[0].classList.contains('selected')).toBe(true);
    expect(cards[1].classList.contains('selected')).toBe(false);
    cards[1].click();
    expect(cards[0].classList.contains('selected')).toBe(false);
    expect(cards[1].classList.contains('selected')).toBe(true);
    expect(window._lgFix.selectedRepoId).toBe(2);
  });

  it('clicking remove button deletes the card and clears selection', async () => {
    fetch.mockResolvedValue({ ok: true });
    lgAddLocalRepo({ id: 5, name: 'r', path: '/r' });
    document.querySelector('.lg-local-repo-card').click();
    expect(window._lgFix.selectedRepoId).toBe(5);
    const delBtn = document.querySelector('.lg-local-repo-del');
    expect(delBtn).toBeTruthy();
    delBtn.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true }));
    for (let i = 0; i < 5; i++) await new Promise((r) => setTimeout(r, 10));
    expect(document.querySelectorAll('.lg-local-repo-card').length).toBe(0);
    expect(fetch).toHaveBeenCalledWith('/api/local-repos/5', expect.objectContaining({ method: 'DELETE' }));
  });

  it('remove button does NOT toggle selection (event isolation)', () => {
    lgAddLocalRepo({ id: 7, name: 'r', path: '/r' });
    const card = document.querySelector('.lg-local-repo-card');
    card.click();
    expect(card.classList.contains('selected')).toBe(true);
    const removeClicked = vi.fn();
    card.addEventListener('click', removeClicked);
    const delBtn = card.querySelector('.lg-local-repo-del');
    delBtn.click();
    expect(removeClicked).not.toHaveBeenCalled();
  });

  it('shows the lgNoRepoHint when last repo is removed', async () => {
    const noHint = document.createElement('div');
    noHint.id = 'lgNoRepoHint';
    noHint.style.display = 'none';
    document.getElementById('lgLocalRepoList').after(noHint);
    fetch.mockResolvedValue({ ok: true });
    lgAddLocalRepo({ id: 8, name: 'r', path: '/r' });
    expect(noHint.style.display).toBe('none');
    document.querySelector('.lg-local-repo-del').dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true }));
    for (let i = 0; i < 5; i++) await new Promise((r) => setTimeout(r, 10));
    expect(noHint.style.display).not.toBe('none');
  });

  it('lgDeleteLocalRepo correctly resolves the card from the button (regression: 3-arg signature bug)', async () => {
    const card = document.createElement('div');
    card.className = 'lg-local-repo-card selected';
    card.setAttribute('data-repo-id', '42');
    card.setAttribute('data-repo-path', '/p');
    card.innerHTML =
      '<span class="lg-local-repo-checkbox"><i class="bi bi-circle lg-local-repo-cb-icon"></i></span>' +
      '<i class="bi bi-folder2 lg-local-repo-icon"></i>' +
      '<div style="flex:1"><div class="lg-local-repo-name">server</div><div class="lg-local-repo-path">/p</div></div>' +
      '<button class="lg-local-repo-del" data-repo-id="42">Entfernen</button>';
    document.getElementById('lgLocalRepoList').appendChild(card);
    window._lgFix.selectedRepoId = 42;

    const delBtn = card.querySelector('.lg-local-repo-del');
    expect(delBtn).toBeTruthy();
    fetch.mockResolvedValue({ ok: true });
    const evt = { preventDefault: () => {}, stopPropagation: () => {} };
    lgDeleteLocalRepo(evt, delBtn, 42);
    for (let i = 0; i < 5; i++) await new Promise((r) => setTimeout(r, 10));

    expect(document.querySelectorAll('.lg-local-repo-card').length).toBe(0);
    expect(fetch).toHaveBeenCalledWith('/api/local-repos/42', expect.objectContaining({ method: 'DELETE' }));
    expect(window._lgFix.selectedRepoId).toBeNull();
  });

  it('lgDeleteLocalRepo extracts the id from the button when only (event, btn) is supplied', async () => {
    const card = document.createElement('div');
    card.className = 'lg-local-repo-card';
    card.setAttribute('data-repo-id', '99');
    card.innerHTML =
      '<button class="lg-local-repo-del" data-repo-id="99">Entfernen</button>';
    document.getElementById('lgLocalRepoList').appendChild(card);

    const delBtn = card.querySelector('.lg-local-repo-del');
    fetch.mockResolvedValue({ ok: true });
    const evt = { preventDefault: () => {}, stopPropagation: () => {} };
    lgDeleteLocalRepo(evt, delBtn);
    for (let i = 0; i < 5; i++) await new Promise((r) => setTimeout(r, 10));

    expect(fetch).toHaveBeenCalledWith(
      '/api/local-repos/99',
      expect.objectContaining({ method: 'DELETE' }),
    );
    expect(document.querySelectorAll('.lg-local-repo-card').length).toBe(0);
  });
});

describe('logging-fix repo match-pill rendering', () => {
  let dom, window;

  function loadScript(file) {
    const html = require('fs').readFileSync(
      require('path').resolve(PROJECT_ROOT, `src/main/resources/templates/fragments/logging/${file}`),
      'utf8',
    );
    return html;
  }

  beforeEach(() => {
    dom = new JSDOM(
      `<!DOCTYPE html><html><body>
        <div id="lgResult" class="lg-result visible"></div>
        <div id="lgLocalRepoList"></div>
      </body></html>`,
      { url: 'http://localhost/logging' },
    );
    window = dom.window;
    global.document = window.document;
    global.window = window;
    global.fetch = vi.fn();

    const stackScript = (() => {
      const html = readFileSync(
        resolve(PROJECT_ROOT, 'src/main/resources/templates/fragments/logging/scripts-stack-detection.html'),
        'utf8',
      );
      const m = html.match(/<script[^>]*>([\s\S]*?)<\/script>/);
      let body = m[1];
      const names = new Set();
      for (const x of body.matchAll(/^\s*function\s+([A-Za-z_$][\w$]*)\s*\(/gm)) names.add(x[1]);
      for (const x of body.matchAll(/^\s*(?:var|let|const)\s+([A-Za-z_$][\w$]*)\s*=\s*function\b/gm)) names.add(x[1]);
      for (const name of names) {
        body += `\ntry { if (typeof ${name} !== 'undefined') window.${name} = ${name}; } catch (_) {}\n`;
      }
      return body;
    })();
    window.eval(stackScript);
    window._lgStack = { logStacks: [], repoStacks: [], repoMatch: 'unknown' };
  });

  it('renders a green match pill for "match"', () => {
    const card = document.createElement('div');
    card.className = 'lg-local-repo-card';
    card.innerHTML = '<div class="lg-local-repo-body">x</div>';
    document.body.appendChild(card);
    lgRenderRepoMatchBadge(card, 'match');
    const pill = card.querySelector('.lg-local-repo-match');
    expect(pill).toBeTruthy();
    expect(pill.getAttribute('data-match')).toBe('match');
    expect(pill.textContent).toContain('Passt');
  });

  it('renders a red mismatch pill', () => {
    const card = document.createElement('div');
    card.className = 'lg-local-repo-card';
    card.innerHTML = '<div class="lg-local-repo-body">x</div>';
    document.body.appendChild(card);
    lgRenderRepoMatchBadge(card, 'mismatch');
    const pill = card.querySelector('.lg-local-repo-match');
    expect(pill).toBeTruthy();
    expect(pill.getAttribute('data-match')).toBe('mismatch');
    expect(pill.textContent).toContain('nicht');
  });

  it('renders an orange unknown pill', () => {
    const card = document.createElement('div');
    card.className = 'lg-local-repo-card';
    card.innerHTML = '<div class="lg-local-repo-body">x</div>';
    document.body.appendChild(card);
    lgRenderRepoMatchBadge(card, 'unknown');
    const pill = card.querySelector('.lg-local-repo-match');
    expect(pill).toBeTruthy();
    expect(pill.getAttribute('data-match')).toBe('unknown');
    expect(pill.textContent).toContain('unbekannt');
  });

  it('replaces existing pill when re-rendered with different status', () => {
    const card = document.createElement('div');
    card.className = 'lg-local-repo-card';
    card.innerHTML = '<div class="lg-local-repo-body">x</div>';
    document.body.appendChild(card);
    lgRenderRepoMatchBadge(card, 'match');
    expect(card.querySelectorAll('.lg-local-repo-match').length).toBe(1);
    lgRenderRepoMatchBadge(card, 'mismatch');
    expect(card.querySelectorAll('.lg-local-repo-match').length).toBe(1);
    expect(card.querySelector('.lg-local-repo-match').getAttribute('data-match')).toBe('mismatch');
  });

  it('removes the pill when called with falsy value', () => {
    const card = document.createElement('div');
    card.className = 'lg-local-repo-card';
    card.innerHTML = '<div class="lg-local-repo-body">x</div>';
    document.body.appendChild(card);
    lgRenderRepoMatchBadge(card, 'match');
    expect(card.querySelector('.lg-local-repo-match')).toBeTruthy();
    lgRenderRepoMatchBadge(card, null);
    expect(card.querySelector('.lg-local-repo-match')).toBeNull();
  });
});
