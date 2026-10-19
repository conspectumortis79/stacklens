import { describe, it, expect } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const MODAL_PATH = resolve(
  __dirname,
  '../../main/resources/templates/fragments/logging/modals.html',
);
const STYLES_PATH = resolve(
  __dirname,
  '../../main/resources/templates/fragments/logging/styles.html',
);

function bootModalMarkup() {
  return readFileSync(MODAL_PATH, 'utf8')
    .replace(/<!\[CDATA\[/g, '')
    .replace(/\]\]>/g, '')
    .replace(/<th:block[^>]*\/>/g, '')
    .replace(/<th:block[^>]*>([\s\S]*?)<\/th:block>/g, '$1');
}

function getLoggingFixStylesSource() {
  return readFileSync(STYLES_PATH, 'utf8');
}

describe('logging fix: write-to-repo modal follows the active theme (modern / dark / popos / legacy)', () => {
  it('uses CSS classes instead of inline styles so a theme can paint it', () => {
    const html = bootModalMarkup();
    const modalBlock = html.match(/<div id="lgFixWriteModal">[\s\S]*?<\/div>\s*<\/div>/);
    expect(modalBlock, 'modal markup must be present').not.toBeNull();
    const inlineStyleAttrs = (modalBlock[0].match(/\sstyle="[^"]*"/g) || []);
    expect(
      inlineStyleAttrs,
      `modal must not use any inline style attributes; found ${inlineStyleAttrs.length} (e.g. ${inlineStyleAttrs[0] ?? ''})`,
    ).toHaveLength(0);
  });

  it('defines the .lg-fix-modal CSS rules in styles.html and uses --app-* theme tokens', () => {
    const css = getLoggingFixStylesSource();
    const expectedTokenReferences = [
      'background: var(--app-cream);',
      'background: var(--app-cream-2);',
      'border: 1px solid var(--app-border);',
      'color: var(--app-text);',
      'color: var(--app-text-muted);',
      'color: var(--app-accent-green);',
      'color: var(--app-accent-red);',
      'background: var(--app-accent-green);',
    ];
    for (const fragment of expectedTokenReferences) {
      expect(css, `styles.html must contain '${fragment}'`).toContain(fragment);
    }
  });

  it('drops the old hardcoded #1e7a3a / #b91c1c / rgba(0,0,0,0.18) "white-plate" colours in the modal block', () => {
    const css = getLoggingFixStylesSource();
    expect(css, '.lg-fix-modal must not use the legacy hardcoded #1e7a3a').not.toMatch(/\.lg-fix-modal[^{]*\{[^}]*#1e7a3a/);
    expect(css, '.lg-fix-modal must not use the legacy hardcoded #b91c1c inside the .lg-fix-modal block').not.toMatch(/\.lg-fix-modal[^{]*\{[^}]*#b91c1c/);
    const baseModal = css.match(/\.lg-fix-modal\s*\{[^}]*\}/);
    expect(baseModal, 'a single .lg-fix-modal { … } rule must exist').not.toBeNull();
    expect(baseModal[0], '.lg-fix-modal must not pin a hardcoded white-ish background').not.toMatch(/background:\s*#fff/);
    expect(baseModal[0], '.lg-fix-modal must not pin a hardcoded white-ish color').not.toMatch(/color:\s*#fff/);
  });

  it('exposes per-theme overrides that adjust the backdrop and the brand-coloured title icon', () => {
    const css = getLoggingFixStylesSource();
    expect(css).toMatch(/html\[data-theme="dark"\]\s+\.lg-fix-modal[\s\S]*box-shadow/);
    expect(css).toMatch(/html\[data-theme="dark"\]\s+\.lg-fix-modal-backdrop[\s\S]*background:\s*rgba\(0,0,0,\.65\)/);
    expect(css).toMatch(/html\[data-theme="popos"\]\s+\.lg-fix-modal-head\s+i\.icon[\s\S]*color:\s*var\(--app-accent-blue\)/);
    expect(css).toMatch(/html\[data-theme="popos"\]\s+\.lg-fix-modal-btn-confirm[\s\S]*color:\s*#1b1b2b/);
  });

  it('uses a .visible toggle so the open/close script can paint the modal without inline display tricks', () => {
    const css = getLoggingFixStylesSource();
    expect(css).toMatch(/#lgFixWriteModal\s*\{\s*display:\s*none;\s*\}/);
    expect(css).toMatch(/#lgFixWriteModal\.visible\s*\{\s*display:\s*block;\s*\}/);
  });

  it('keeps the file rows in the modal readable on every theme (no inline styles in the rendered list)', () => {
    const css = getLoggingFixStylesSource();
    expect(css).toMatch(/\.lg-fix-modal-file-path\s*\{[\s\S]*color:\s*var\(--app-text\)/);
    expect(css).toMatch(/\.lg-fix-modal-file-desc\s*\{[\s\S]*color:\s*var\(--app-text-muted\)/);
  });
});
