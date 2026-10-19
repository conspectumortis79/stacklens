import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const LAYOUT = resolve(__dirname, '../../main/resources/templates/layout.html');
const SCRIPTS_PAGE = resolve(
  __dirname,
  '../../main/resources/templates/fragments/config/scripts-page.html',
);

const PANES_SYSTEM = resolve(
  __dirname,
  '../../main/resources/templates/fragments/config/panes-system.html',
);

function loadLayoutBodyScript() {
  const html = readFileSync(LAYOUT, 'utf8');
  const blocks = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((m) => m[1]);
  const body = blocks.find((b) => b.includes('cfApplyDockerTestEnvVisibility'));
  if (!body) throw new Error('layout.html does not contain the docker-toggle body script');
  return body;
}

describe('Layout.html exposes IIFE-local docker-toggle functions on window', () => {
  it('assigns cfApplyDockerTestEnvVisibility to window at the end of the IIFE', () => {
    const code = loadLayoutBodyScript();
    expect(code, 'IIFE body must mention cfApplyDockerTestEnvVisibility')
      .toContain('cfApplyDockerTestEnvVisibility');
    expect(
      code,
      'layout.html body script must expose cfApplyDockerTestEnvVisibility on ' +
        '`window` so page-scoped scripts in config/scripts-page.html can ' +
        'call it from outside the IIFE. Without this export the onchange ' +
        'handler on #devDockerTestEnv raises `ReferenceError: ' +
        'cfApplyDockerTestEnvVisibility is not defined`.'
    ).toMatch(/window\.cfApplyDockerTestEnvVisibility\s*=\s*cfApplyDockerTestEnvVisibility/);
  });

  it('also exposes cfApplyDockerSidebarFromLocalStorage and cfInitDockerTestEnvVisibility', () => {
    const code = loadLayoutBodyScript();
    expect(code).toMatch(/window\.cfApplyDockerSidebarFromLocalStorage\s*=\s*cfApplyDockerSidebarFromLocalStorage/);
    expect(code).toMatch(/window\.cfInitDockerTestEnvVisibility\s*=\s*cfInitDockerTestEnvVisibility/);
  });
});

describe('Layout.html IIFE exports are actually reachable from outside', () => {
  it('window.cfApplyDockerTestEnvVisibility is callable after the IIFE returns', () => {
    const dom = new JSDOM('<!DOCTYPE html><html><body></body></html>', {
      runScripts: 'dangerously',
      url: 'http://localhost/',
    });
    const win = dom.window;
    win.localStorage.getItem = () => null;
    win.localStorage.setItem = () => {};
    win.fetch = () => Promise.resolve({ json: () => Promise.resolve({}) });

    const s = win.document.createElement('script');
    s.textContent = loadLayoutBodyScript();
    win.document.body.appendChild(s);
    s.remove();

    expect(typeof win.cfApplyDockerTestEnvVisibility).toBe('function');
    expect(() => win.cfApplyDockerTestEnvVisibility()).not.toThrow();
    expect(() => win.cfApplyDockerSidebarFromLocalStorage()).not.toThrow();
  });

  it('scripts-page.html calls cfApplyDockerTestEnvVisibility through a typeof guard', () => {
    const scriptsPage = readFileSync(SCRIPTS_PAGE, 'utf8');
    expect(
      scriptsPage,
      'cfDevToggleDockerTestEnv must guard the cfApplyDockerTestEnvVisibility ' +
        'call with `typeof === "function"` so a missing export does not ' +
        'crash the toggle with `ReferenceError`.'
    ).toMatch(/typeof\s+cfApplyDockerTestEnvVisibility\s*===\s*['"]function['"]/);
  });
});

describe('Panes-system exposes the docker-toggle checkbox via id', () => {
  it('declares the dev-docker-test checkbox with id="devDockerTestEnv"', () => {
    const html = readFileSync(PANES_SYSTEM, 'utf8');
    expect(
      html,
      'panes-system.html must render the developer-option toggle as ' +
        '<input id="devDockerTestEnv" ...> so scripts-page.html can ' +
        'synchronise its checked-state with localStorage on page load.'
    ).toMatch(/id="devDockerTestEnv"/);
    expect(html).toMatch(/onchange=["']cfDevToggleDockerTestEnv\(this\.checked\)["']/);
  });
});
