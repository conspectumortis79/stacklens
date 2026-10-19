import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const RES = resolve(here, '..', '..', '..', 'src', 'main', 'resources');

const read = (...parts) => readFileSync(resolve(RES, ...parts), 'utf8');

describe('Grafana dashboard-URL fetch sends the selected config', () => {
  const source = read('templates', 'fragments', 'grafana-analysis', 'scripts-dashboard-mgmt.html');
  const addDashboardBody = source.slice(
    source.indexOf('async function addDashboard()'),
    source.indexOf('async function loadFromConfig()')
  );

  it('reads the config select inside addDashboard', () => {
    expect(addDashboardBody).toContain("getElementById('selectGrafanaConfig')");
  });

  it('posts configId together with grafanaUrl', () => {
    expect(addDashboardBody).toMatch(/JSON\.stringify\(\{\s*grafanaUrl:\s*url,\s*configId:/);
  });

  it('sends null instead of NaN when no config is selected', () => {
    expect(addDashboardBody).toMatch(/isNaN\(presetConfigId\)\s*\?\s*null\s*:\s*presetConfigId/);
  });
});

describe('Code QA MR-URL input wiring', () => {
  const template = read('templates', 'code-qa.html');
  const script = read('static', 'js', 'fragments', 'code-qa.js');

  it('has no inline handler on the MR-URL input', () => {
    const input = template.slice(template.indexOf('id="mrInputUrl"'), template.indexOf('mrSelectGitConfig'));
    expect(input).not.toMatch(/\boninput\s*=/);
  });

  it('still enables the start button through the module listener', () => {
    expect(script).toMatch(/addEventListener\(\s*'input',\s*mrUpdateBtn\s*\)/);
  });

  it('keeps the module IIFE-private so no global is required', () => {
    expect(script.trimStart().startsWith('(function')).toBe(true);
  });
});

describe('Token-usage empty pill is translated after the dictionary loads', () => {
  const source = read('templates', 'fragments', 'shared', 'token-usage.html');
  const emptyBranch = source.slice(source.indexOf('if (!usage) {'), source.indexOf('var modelPill'));

  it('marks the pill with the i18n key', () => {
    expect(emptyBranch).toContain("setAttribute('data-i18n', 'config.token_usage.no_data')");
  });

  it('applies translations to the freshly built bar', () => {
    expect(emptyBranch).toContain('window.i18n.apply(bar)');
  });
});
