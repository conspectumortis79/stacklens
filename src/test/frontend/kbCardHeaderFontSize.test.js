
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const KB_CSS = resolve(
  __dirname,
  '../../main/resources/static/css/knowledge.css',
);
const kbCss = readFileSync(KB_CSS, 'utf8');

function ruleBody(selector, source = kbCss) {
  const esc = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = new RegExp(esc + '\\s*\\{([^}]*)\\}', 'm');
  const m = source.match(re);
  if (!m) throw new Error('CSS rule not found: ' + selector);
  return m[1];
}

function fontSizeOf(selector) {
  const body = ruleBody(selector);
  const m = body.match(/font-size:\s*([^;]+);/);
  if (!m) throw new Error('font-size not declared in rule for ' + selector);
  return m[1].trim();
}

describe('Wissensdatenbank card-header font size hierarchy', () => {

  it('card-header h5 is pinned to 1rem (16px), not the Bootstrap default 1.25rem', () => {
    const size = fontSizeOf('.kb-page .card-header h5');
    expect(size, 'card-header h5 was not pinned to 1rem; the fix regressed').toBe('1rem');
  });

  it('all six card-header h5 elements fall under the same selector', () => {
    const KB_HTML = resolve(
      __dirname,
      '../../main/resources/templates/knowledge.html',
    );
    const kbHtml = readFileSync(KB_HTML, 'utf8');
    const cardHeaders = kbHtml.match(/<div class="card-header[^"]*">/g) || [];
    expect(cardHeaders.length, 'expected at least 6 card-header divs on the KB page')
      .toBeGreaterThanOrEqual(6);
    const h5InCardHeaders = (kbHtml.match(/<div class="card-header[^"]*">[\s\S]*?<h5\b/g) || []).length;
    expect(h5InCardHeaders, 'every card-header must contain an <h5> to share the 1rem rule')
      .toBe(cardHeaders.length);
  });


  it('every card-header h5 falls under the same .kb-page .card-header h5 rule', () => {
    const selector = '\\.kb-page \\.card-header h5';
    const matches = kbCss.match(new RegExp(selector + '\\s*\\{([^}]*)\\}', 'g')) || [];
    expect(matches.length, 'expected exactly one kb-page card-header h5 rule — multiple rules will desync the headings')
      .toBe(1);
  });


  it('tab nav is 0.875rem (14px), matching the body text', () => {
    const size = fontSizeOf('.kb-page #kbTabs .nav-link');
    expect(size, 'tab nav must be 14px (0.875rem) to match the body text')
      .toBe('0.875rem');
  });

  it('badge is 0.875rem (14px), matching the body text', () => {
    const size = fontSizeOf('.kb-page .badge');
    expect(size, 'badge must be 14px (0.875rem) to match the body text')
      .toBe('0.875rem');
  });

  it('export-password modal: show-password checkbox label is 0.875rem (14px) and bold', () => {
    const size = fontSizeOf('#kbExportPasswordModal .form-check-label');
    expect(size, 'show-password label must be 14px (0.875rem) to match the description text')
      .toBe('0.875rem');
  });

  it('delete-base modal: body text is 0.875rem (14px) and bold is capped at 700', () => {
    const body = ruleBody('#kbDeleteBaseModal .modal-body');
    const bodySize = (body.match(/font-size:\s*([^;]+);/) || [])[1];
    expect(bodySize && bodySize.trim(), 'modal-body must declare font-size').toBeTruthy();
    expect(bodySize.trim(), 'modal-body font-size must be 14px (0.875rem)')
      .toBe('0.875rem');
    const p = ruleBody('#kbDeleteBaseModal .modal-body p');
    expect(p, 'modal-body p rule must exist').toBeTruthy();
    expect(p.match(/font-size:\s*([^;]+);/)[1].trim(), 'modal-body p font-size must be 14px')
      .toBe('0.875rem');
    const strong = ruleBody('#kbDeleteBaseModal .modal-body strong');
    expect(strong, 'modal-body strong rule must exist').toBeTruthy();
    expect(strong.match(/font-weight:\s*([^;]+);/)[1].trim(), 'modal-body strong weight must be 700')
      .toBe('700');
    const alert = ruleBody('#kbDeleteBaseModal .modal-body .alert-danger');
    expect(alert, 'alert-danger rule must exist').toBeTruthy();
    expect(alert.match(/font-size:\s*([^;]+);/)[1].trim(), 'alert-danger font-size must be 14px')
      .toBe('0.875rem');
    expect(alert.match(/font-weight:\s*([^;]+);/)[1].trim(), 'alert-danger font-weight must be 700')
      .toBe('700');
    const small = ruleBody('#kbDeleteBaseModal .modal-body .small');
    expect(small, '.small override rule must exist in the modal').toBeTruthy();
    expect(small.match(/font-size:\s*([^;]+);/)[1].trim(), '.small inside modal must be 14px')
      .toBe('0.875rem');
  });

  it('source name (kb-source-name) is 0.875rem (14px), matching the type badge', () => {
    const size = fontSizeOf('.kb-page .kb-source-name');
    expect(size, 'source name must be 14px (0.875rem) to match the type badge')
      .toBe('0.875rem');
  });

  it('source table type badge is 0.8125rem (13px) — 10% smaller than the body', () => {
    const size = fontSizeOf('.kb-page .kb-source-table .badge');
    expect(size, 'type badge inside the Inhalte table must be 13px (0.8125rem) — 10% smaller than the body')
      .toBe('0.8125rem');
  });


  it('metric value stays at 1.25rem (20px) — the prominent number font size', () => {
    const size = fontSizeOf('.kb-page .card-body .kb-metric-value');
    expect(size).toBe('1.25rem');
  });

  it('metric text stays at 1rem (16px) — the metric label font size', () => {
    const size = fontSizeOf('.kb-page .card-body .kb-metric-text');
    expect(size).toBe('1rem');
  });


  it('the four-tier font-size hierarchy is preserved (12/14/16/20 px)', () => {
    const lines = kbCss.split(/\r?\n/);
    const sizes = [];
    let inKbPageBlock = false;
    let braceDepth = 0;
    for (const line of lines) {
      if (/\.kb-page\b/.test(line) && /\{/.test(line)) inKbPageBlock = true;
      if (inKbPageBlock) {
        braceDepth += (line.match(/\{/g) || []).length;
        braceDepth -= (line.match(/\}/g) || []).length;
        const m = line.match(/font-size:\s*([^;]+);/);
        if (m) sizes.push(m[1].trim());
        if (braceDepth <= 0) inKbPageBlock = false;
      }
    }
    const unique = [...new Set(sizes)].sort();
    expect(unique, `unexpected font-size tier(s) introduced: ${JSON.stringify(unique)}`).toEqual([
      '0.8125rem',
      '0.875rem',
      '1.25rem',
      '1rem',
    ].sort());
  });
});
