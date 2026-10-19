import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { readKnowledgeBundle } from './support/knowledgeModules.js';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const KB_CSS = resolve(__dirname, '../../main/resources/static/css/knowledge.css');
const DE_JSON = resolve(__dirname, '../../main/resources/static/i18n/de.json');
const EN_JSON = resolve(__dirname, '../../main/resources/static/i18n/en.json');

const kbJs = readKnowledgeBundle();
const kbCss = readFileSync(KB_CSS, 'utf8');
const deJson = readFileSync(DE_JSON, 'utf8');
const enJson = readFileSync(EN_JSON, 'utf8');

describe('knowledge.js — inline progress strip (Mockup 1) for kbSyncSource + kbSubmitManual', () => {
  describe('helper surface', () => {
    it('declares an in-flight AbortController registry', () => {
      expect(kbJs).toMatch(/var\s+kbInflightSourceFetches\s*=\s*\{\s*\}/);
    });

    it('declares a row-locator that reads the data-source-id attribute', () => {
      expect(kbJs).toMatch(/function\s+kbSourceRow\s*\(/);
      expect(kbJs).toMatch(/tr\[data-source-id=.*String\(id\)/);
    });

    it('declares a builder that emits the strip markup with spinner, label, progress bar and optional cancel', () => {
      expect(kbJs).toMatch(/function\s+kbBuildSourceProgressStrip\s*\(/);
      expect(kbJs).toMatch(/kb-source-progress-line/);
      expect(kbJs).toMatch(/kb-progress-bar-indeterminate/);
      expect(kbJs).toMatch(/bi-arrow-clockwise spin kb-source-progress-icon/);
      expect(kbJs).toMatch(/kb-source-progress-cancel/);
      expect(kbJs).toMatch(/kb-source-progress-cancel-placeholder/);
    });

    it('declares a show helper that inserts the strip after the matching row and dedups if a strip is already there', () => {
      expect(kbJs).toMatch(/function\s+kbShowSourceProgress\s*\(/);
      expect(kbJs).toMatch(/kb-source-progress-row[\s\S]*?nextSibling[\s\S]*?insertBefore/);
    });

    it('declares a hide helper that removes the strip directly after the matching row', () => {
      expect(kbJs).toMatch(/function\s+kbHideSourceProgress\s*\(/);
      expect(kbJs).toMatch(/kb-source-progress-row[\s\S]*?parentNode\.removeChild/);
    });

    it('declares a cancel helper that aborts the in-flight fetch for the strip source id', () => {
      expect(kbJs).toMatch(/function\s+kbCancelSourceProgressById\s*\(/);
      expect(kbJs).toMatch(/kbInflightSourceFetches[\s\S]*?\.abort\(\)/);
    });
  });

  describe('kbSyncSource integration', () => {
    it('shows the strip before the fetch and uses AbortController', () => {
      expect(kbJs).toMatch(
        /function\s+kbSyncSource[\s\S]*?kbShowSourceProgress\([\s\S]*?new\s+AbortController\(\)[\s\S]*?signal:\s*ctrl\.signal/,
      );
    });

    it('hides the strip and clears the AbortController in finally', () => {
      expect(kbJs).toMatch(
        /function\s+kbSyncSource[\s\S]*?\.\s*finally\(\s*function\s*\(\)\s*\{[\s\S]*?delete\s+kbInflightSourceFetches[\s\S]*?kbHideSourceProgress/,
      );
    });

    it('shows the Abbrechen toast when the fetch is aborted by the user', () => {
      expect(kbJs).toMatch(
        /\.catch\(function\s*\(e\)\s*\{[\s\S]*?AbortError[\s\S]*?knowledge\.action\.cancelled[\s\S]*?\}\)/,
      );
    });
  });

  describe('row markup', () => {
    it('emits data-source-id on every sources-table row so the strips can be located after re-render', () => {
      expect(kbJs).toMatch(/<tr\s+data-source-id=.*esc\(String\(s\.id\)/);
    });
  });
});

describe('knowledge.css — themed inline progress strip (4 themes)', () => {
  it('defines the strip container (.kb-source-progress-line) using the --app-accent-yellow token', () => {
    expect(kbCss).toMatch(/\.kb-source-progress-line\s*\{/);
    expect(kbCss).toMatch(/var\(--app-accent-yellow\)/);
  });

  it('styles the indeterminate progress bar with the stripe animation', () => {
    expect(kbCss).toMatch(/\.kb-progress-bar-stripe[\s\S]*?animation:\s*kb-stripe-slide/);
    expect(kbCss).toMatch(/@keyframes\s+kb-stripe-slide/);
  });

  it('overrides the strip background in dark mode so the tint reads on the dark surface', () => {
    expect(kbCss).toMatch(
      /html\[data-theme=["']dark["']\]\s+\.kb-source-progress-line\s*\{/,
    );
  });

  it('overrides the strip background in popos mode', () => {
    expect(kbCss).toMatch(
      /html\[data-theme=["']popos["']\]\s+\.kb-source-progress-line\s*\{/,
    );
  });

  it('overrides the strip background in legacy / klassisch mode', () => {
    expect(kbCss).toMatch(
      /html\[data-theme=["']legacy["']\]\s+\.kb-source-progress-line\s*\{/,
    );
  });

  it('uses the spinner font-size that matches the existing bi-arrow-clockwise usage', () => {
    expect(kbCss).toMatch(/\.kb-source-progress-icon\s*\{[\s\S]*?font-size:\s*1\.1rem/);
  });

  it('keeps comment delimiters balanced (file-level CSS sanity — duplicates CssSyntaxTest)', () => {
    const open = (kbCss.match(/\/\*/g) || []).length;
    const close = (kbCss.match(/\*\//g) || []).length;
    expect(close).toBe(open);
  });

  it('forbids lines starting with bare slash', () => {
    const offenders = kbCss
      .split('\n')
      .map((line, idx) => [idx + 1, line])
      .filter(([, line]) => {
        const t = line.trimStart();
        return t.startsWith('/') && !t.startsWith('/*');
      });
    expect(offenders).toEqual([]);
  });
});

describe('i18n — German + English keys for the inline progress strip', () => {
  it('DE exposes the sync strip labels + the cancellation action', () => {
    expect(deJson).toMatch(/"sync_in_progress":\s*"Synchronisiere Jira-Quelle"/);
    expect(deJson).toMatch(/"sync_stage_walk":\s*"lade Hierarchie"/);
    expect(deJson).toMatch(/"cancelled":\s*"Aktion abgebrochen\."/);
  });

  it('EN exposes the same keys with English copy', () => {
    expect(enJson).toMatch(/"sync_in_progress":\s*"Synchronising Jira source"/);
    expect(enJson).toMatch(/"sync_stage_walk":\s*"loading hierarchy"/);
    expect(enJson).toMatch(/"cancelled":\s*"Action cancelled\."/);
  });
});
