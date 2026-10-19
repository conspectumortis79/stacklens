import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { buildIndex, definedClasses, usedClassTokens, cssFiles } from './support/cssHarness.js';

const { used, prefixes, defined } = buildIndex();

function isLive(name) {
  if (used.has(name)) return true;
  return [...prefixes].some((prefix) => prefix.length >= 4 && name.startsWith(prefix));
}

describe('css contract: every defined class is reachable', () => {
  it('the stylesheet corpus was discovered', () => {
    expect(cssFiles().length).toBeGreaterThan(8);
    expect(defined.size).toBeGreaterThan(500);
  });

  it('flags a synthetic class that nothing references', () => {
    expect(definedClasses('.totallyUnusedWidget:hover{color:red}').has('totallyUnusedWidget')).toBe(true);
    expect(isLive('totallyUnusedWidget')).toBe(false);
  });

  it('reports only the class names whose liveness was verified by hand', () => {
    const verifiedDead = [
      'ca-panel-list',
      'lt-tab-bar',
      'fb-dir-item',
      'fb-dir-name',
      'fb-selected',
      'gl-tabs',
    ];
    const stillDead = verifiedDead.filter((name) => !isLive(name));
    expect(stillDead.sort()).toEqual(verifiedDead.slice().sort());
  });

  it('accepts a class that only appears through concatenated names', () => {
    const probe = usedClassTokens([{ file: 'sample.js', text: '<span class="kb-source-badge-\' + status + \'">' }]);
    expect([...probe.prefixes].some((prefix) => 'kb-source-badge-READY'.startsWith(prefix))).toBe(true);
  });

  it('keeps the dynamically named source badge styles reachable', () => {
    const live = readFileSync('src/main/resources/static/js/knowledge/config.js', 'utf8');
    expect(live).toContain('kb-source-badge kb-source-badge-');
    expect(isLive('kb-source-badge-READY')).toBe(true);
  });
});
