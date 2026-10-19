import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(here, '../../..');
const THEMES = ['modern', 'dark', 'popos', 'legacy'];
const REFERENCE = 'ca-repo-card';
const FIXED = 'pa-repo-card';

function themeCss(theme) {
  return readFileSync(resolve(ROOT, 'src/main/resources/static/css/themes', `theme-${theme}.css`), 'utf8');
}

function fragmentCss(page) {
  const html = readFileSync(resolve(ROOT, `src/main/resources/templates/fragments/${page}/styles.html`), 'utf8');
  return [...html.matchAll(/<style>([\s\S]*?)<\/style>/g)].map((m) => m[1]).join('\n');
}

function normalizeTokens(decl) {
  return decl === null ? null : decl.replace(/--(?:ca|pa)-/g, '--xx-');
}

function declaration(css, selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = css.match(new RegExp('(?:^|\\})\\s*([^{}]*' + escaped + '[^{}]*)\\{([^}]*)\\}', 'm'));
  if (!match) return null;
  const selectorText = match[1].trim();
  const last = selectorText.split(',').pop().trim();
  if (!new RegExp(escaped + '(?![-\\w])').test(last)) return null;
  return match[2].replace(/\s+/g, ' ').trim();
}

function backgroundOf(css, selector) {
  const decl = declaration(css, selector);
  if (!decl) return null;
  const bg = decl.match(/background:\s*([^;]+)/);
  return bg ? bg[1].trim() : null;
}

function boxShadowOf(css, selector) {
  const decl = declaration(css, selector);
  if (!decl) return null;
  const shadow = decl.match(/box-shadow:\s*([^;]+)/);
  return shadow ? shadow[1].trim() : null;
}

describe('Pipeline repo cards match the code analysis reference in every theme', () => {
  it.each(THEMES)('%s defines a selected background for the pipeline card', (theme) => {
    const css = themeCss(theme);
    expect(backgroundOf(css, `.${FIXED}.selected`), `${theme}: .${FIXED}.selected fehlt`).not.toBeNull();
  });

  it.each(THEMES)('%s selects the same background as code analysis', (theme) => {
    const css = themeCss(theme);
    expect(backgroundOf(css, `.${FIXED}.selected`)).toBe(backgroundOf(css, `.${REFERENCE}.selected`));
  });

  it.each(THEMES)('%s hovers the same background as code analysis', (theme) => {
    const css = themeCss(theme);
    expect(backgroundOf(css, `.${FIXED}:hover`)).toBe(backgroundOf(css, `.${REFERENCE}:hover`));
  });

  it.each(THEMES)('%s renders an unselected card like code analysis', (theme) => {
    const css = themeCss(theme);
    expect(backgroundOf(css, `.${FIXED}`)).toBe(backgroundOf(css, `.${REFERENCE}`));
    expect(boxShadowOf(css, `.${FIXED}`)).toBe(boxShadowOf(css, `.${REFERENCE}`));
  });

  it('keeps the accent stripe of the selected card identical to code analysis', () => {
    THEMES.forEach((theme) => {
      const css = themeCss(theme);
      expect(boxShadowOf(css, `.${FIXED}.selected`), theme).toBe(boxShadowOf(css, `.${REFERENCE}.selected`));
    });
  });

  it('ships the same default theme rules for both pages', () => {
    const ca = fragmentCss('code-analysis');
    const pa = fragmentCss('pipeline-analysis');
    [`${FIXED}`, `${FIXED}:hover`, `${FIXED}.selected`].forEach((selector, index) => {
      const reference = [`${REFERENCE}`, `${REFERENCE}:hover`, `${REFERENCE}.selected`][index];
      expect(normalizeTokens(declaration(pa, '.' + selector))).toEqual(normalizeTokens(declaration(ca, '.' + reference)));
    });
  });
});

describe('Theme colours for repo cards live in the theme files only', () => {
  it.each(THEMES)('%s overrides for the pipeline card are not hidden in the page fragment', (theme) => {
    const css = fragmentCss('pipeline-analysis');
    expect(css).not.toMatch(new RegExp(`html\\[data-theme="${theme}"\\][^{]*\\.pa-repo-card`));
  });

  it('the pipeline fragment carries no !important repo-card background', () => {
    const css = fragmentCss('pipeline-analysis');
    const repoRules = css.match(/\.pa-repo-card[^{]*\{[^}]*\}/g) || [];
    expect(repoRules.length).toBeGreaterThan(0);
    expect(repoRules.filter((rule) => /!important/.test(rule))).toEqual([]);
  });
});

describe('Every repo card class of the app is themed uniformly', () => {
  const cardClasses = THEMES.reduce((acc, theme) => {
    const css = themeCss(theme);
    (css.match(/html\[data-theme="[a-z"]+"\s*\]\s*\.([a-z]{2}-[a-z-]*repo-card)\.selected/g) || [])
      .map((selector) => selector.split('] .')[1].replace('.selected', ''))
      .forEach((name) => acc.add(name));
    return acc;
  }, new Set());

  it.each([...cardClasses])('%s has the same selected background as code analysis in all themes', (name) => {
    THEMES.forEach((theme) => {
      const css = themeCss(theme);
      const reference = backgroundOf(css, `.${REFERENCE}.selected`);
      const actual = name === REFERENCE ? reference : backgroundOf(css, `.${name}.selected`);
      expect(actual, `${name} in ${theme}`).toBe(reference);
    });
  });
});
