import { describe, it, expect } from 'vitest';
import { readKnowledgeBundle } from './support/knowledgeModules.js';
import { readFileSync } from 'node:fs';
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

const SUBMIT_FLOWS = [
  { fn: 'kbSubmitManual',     key: 'import_manual',     modal: 'kbAddManualModal' },
  { fn: 'kbSubmitFile',       key: 'import_file',      modal: 'kbAddFileModal' },
  { fn: 'kbSubmitConfluence', key: 'import_confluence', modal: 'kbAddConfluenceModal' },
  { fn: 'kbSubmitJira',       key: 'import_jira',      modal: 'kbAddJiraModal' },
  { fn: 'kbSubmitJiraSprint', key: 'import_jira_sprint', modal: 'kbAddJiraSprintModal' },
];

describe('knowledge.js — modal-internal progress band (Mockup 2)', () => {
  describe('helper surface', () => {
    it('declares kbBeginImportProgress', () => {
      expect(kbJs).toMatch(/function\s+kbBeginImportProgress\s*\(/);
    });

    it('declares kbEndImportProgress', () => {
      expect(kbJs).toMatch(/function\s+kbEndImportProgress\s*\(/);
    });

    it('kbBeginImportProgress uses querySelector(.modal-header) to find the anchor point', () => {
      expect(kbJs).toMatch(/kbBeginImportProgress[\s\S]*?querySelector\(['"]\.modal-header['"]\)/);
    });

    it('kbBeginImportProgress inserts the band before .modal-header.nextSibling via insertBefore', () => {
      expect(kbJs).toMatch(/kbBeginImportProgress[\s\S]*?insertBefore\([\s\S]*?header\.nextSibling\)/);
    });

    it('kbBeginImportProgress disables every input/select/textarea/button in the modal', () => {
      expect(kbJs).toMatch(
        /kbBeginImportProgress[\s\S]*?querySelectorAll\(['"]input, select, textarea, button['"]\)[\s\S]*?disabled\s*=\s*true/,
      );
    });

    it('kbBeginImportProgress adds the kb-import-form-busy class to the modal', () => {
      expect(kbJs).toMatch(/kbBeginImportProgress[\s\S]*?classList\.add\(['"]kb-import-form-busy['"]\)/);
    });

    it('kbEndImportProgress removes the band and restores prior disabled state', () => {
      expect(kbJs).toMatch(/kbEndImportProgress[\s\S]*?querySelectorAll\(['"]\.kb-import-progress-band['"]\)[\s\S]*?parentNode\.removeChild/);
      expect(kbJs).toMatch(/kbEndImportProgress[\s\S]*?data-kb-imp-prev-disabled/);
      expect(kbJs).toMatch(/kbEndImportProgress[\s\S]*?classList\.remove\(['"]kb-import-form-busy['"]\)/);
    });
  });

  for (const flow of SUBMIT_FLOWS) {
    describe(flow.fn + ' integration', () => {
      it('shows the modal-internal progress band before the fetch', () => {
        const re = new RegExp('function\\s+' + flow.fn + '[\\s\\S]*?kbBeginImportProgress\\(');
        expect(kbJs, flow.fn + ' calls kbBeginImportProgress').toMatch(re);
      });

      it('passes the matching modal element ID to kbBeginImportProgress', () => {
        const re = new RegExp(
          'function\\s+' + flow.fn +
            '[\\s\\S]*?getElementById\\([\'"]' + flow.modal + '[\'"]\\)' +
            '[\\s\\S]*?kbBeginImportProgress\\(\\s*modalEl\\s*,',
        );
        expect(kbJs, flow.fn + ' reads modal ' + flow.modal + ' and passes modalEl to kbBeginImportProgress').toMatch(re);
      });

      it('uses an AbortController and the unique inflight key __' + flow.key + '__', () => {
        const re = new RegExp(
          'function\\s+' + flow.fn +
            '[\\s\\S]*?new\\s+AbortController\\(\\)' +
            '[\\s\\S]*?kbInflightSourceFetches\\[[\'"]__' + flow.key + '__[\'"]\\]',
        );
        expect(kbJs, flow.fn + ' registers its AbortController under __' + flow.key + '__').toMatch(re);
      });

      it('threads signal: ctrl.signal into the fetch options', () => {
        const re = new RegExp('function\\s+' + flow.fn + '[\\s\\S]*?signal:\\s*ctrl\\.signal');
        expect(kbJs, flow.fn + ' uses signal: ctrl.signal').toMatch(re);
      });

      it('catches AbortError and shows the "Aktion abgebrochen." toast', () => {
        const re = new RegExp(
          'function\\s+' + flow.fn +
            '[\\s\\S]*?AbortError' +
            '[\\s\\S]*?knowledge\\.action\\.cancelled',
        );
        expect(kbJs, flow.fn + ' handles AbortError → knowledge.action.cancelled').toMatch(re);
      });

      it('cleans up the band + inflight registry in .finally()', () => {
        const re = new RegExp(
          'function\\s+' + flow.fn +
            '[\\s\\S]*?\\.finally\\(function\\s*\\(\\)\\s*\\{' +
            '[\\s\\S]*?delete\\s+kbInflightSourceFetches\\[[\'"]__' + flow.key + '__[\'"]\\]' +
            '[\\s\\S]*?kbEndImportProgress\\(modalEl\\)',
        );
        expect(kbJs, flow.fn + ' calls kbEndImportProgress(modalEl) in finally()').toMatch(re);
      });

      it('hides the modal after a successful import', () => {
        const re = new RegExp(
          'function\\s+' + flow.fn +
            '[\\s\\S]*?bootstrap\\.Modal\\.getInstance\\(modalEl\\)\\.hide\\(\\)',
        );
        expect(kbJs, flow.fn + ' hides the modal on success').toMatch(re);
      });
    });
  }

  it('no kbSubmit* still routes through the legacy inline-strip helper', () => {
    const offenders = SUBMIT_FLOWS.filter((f) =>
      new RegExp('function\\s+' + f.fn + '[\\s\\S]*?kbBuildSourceProgressStrip').test(kbJs),
    );
    expect(offenders, 'kbSubmit* should not use kbBuildSourceProgressStrip anymore').toEqual([]);
  });
});

describe('knowledge.css — themed modal-internal progress band (4 themes)', () => {
  it('defines the .kb-import-progress-band container using --app-accent-yellow', () => {
    expect(kbCss).toMatch(/\.kb-import-progress-band\s*\{/);
    expect(kbCss).toMatch(/var\(--app-accent-yellow\)/);
  });

  it('defines the label / phase / eta / bar sub-rules inside the band', () => {
    expect(kbCss).toMatch(/\.kb-import-progress-meta\s*\{/);
    expect(kbCss).toMatch(/\.kb-import-progress-label\s*\{/);
    expect(kbCss).toMatch(/\.kb-import-progress-phase\s*\{/);
    expect(kbCss).toMatch(/\.kb-import-progress-eta\s*\{/);
    expect(kbCss).toMatch(/\.kb-import-progress-bar\s*\{/);
    expect(kbCss).toMatch(/\.kb-import-progress-bar::after/);
  });

  it('defines the kb-import-form-busy visual treatment for the modal body', () => {
    expect(kbCss).toMatch(/\.kb-import-form-busy\s+\.modal-body\s*\{/);
    expect(kbCss).toMatch(/pointer-events:\s*none/);
  });

  it('hides the primary submit button while the modal is busy', () => {
    expect(kbCss).toMatch(/\.kb-import-form-busy\s+\.modal-footer\s+\.btn-primary\s*\{/);
    expect(kbCss).toMatch(/display:\s*none/);
  });

  it('overrides the band background in dark mode', () => {
    expect(kbCss).toMatch(/html\[data-theme=["']dark["']\]\s+\.kb-import-progress-band\s*\{/);
  });

  it('overrides the band background in popos mode', () => {
    expect(kbCss).toMatch(/html\[data-theme=["']popos["']\]\s+\.kb-import-progress-band\s*\{/);
  });

  it('overrides the band background in legacy / klassisch mode', () => {
    expect(kbCss).toMatch(/html\[data-theme=["']legacy["']\]\s+\.kb-import-progress-band\s*\{/);
  });

  it('honours prefers-reduced-motion by stopping the band animations', () => {
    expect(kbCss).toMatch(/@media\s+\(prefers-reduced-motion:\s*reduce\)/);
    expect(kbCss).toMatch(/\.kb-import-progress-label\s+\.bi,[^}]*\.kb-import-progress-bar::after\s*\{[^}]*animation:\s*none/);
  });
});

describe('i18n — German + English keys for the modal progress band', () => {
  it('DE exposes knowledge.import.progress.* with the 5 source labels and the ETA placeholder', () => {
    expect(deJson).toMatch(/"manual_label":\s*"Manuelle Notiz wird eingefügt"/);
    expect(deJson).toMatch(/"manual_phase_vectorize":\s*"vektorisiere Text"/);
    expect(deJson).toMatch(/"file_label":\s*"Datei wird eingefügt"/);
    expect(deJson).toMatch(/"file_phase_read":\s*"lese Datei"/);
    expect(deJson).toMatch(/"confluence_label":\s*"Confluence wird importiert"/);
    expect(deJson).toMatch(/"confluence_phase_walk":\s*"lade Hierarchie"/);
    expect(deJson).toMatch(/"jira_label":\s*"Jira-Ticket wird importiert"/);
    expect(deJson).toMatch(/"jira_phase_walk":\s*"lade Hierarchie"/);
    expect(deJson).toMatch(/"jira_sprint_label":\s*"Sprint wird importiert"/);
    expect(deJson).toMatch(/"jira_sprint_phase_walk":\s*"lade Top-Level-Tickets"/);
    expect(deJson).toMatch(/"eta_seconds":\s*"ca\.\s+noch\s+\{0\}\s+Sek\.\s*"/);
  });

  it('EN exposes the same keys with English copy', () => {
    expect(enJson).toMatch(/"manual_label":\s*"Saving manual note"/);
    expect(enJson).toMatch(/"manual_phase_vectorize":\s*"vectorising text"/);
    expect(enJson).toMatch(/"file_label":\s*"Saving file"/);
    expect(enJson).toMatch(/"file_phase_read":\s*"reading file"/);
    expect(enJson).toMatch(/"confluence_label":\s*"Importing from Confluence"/);
    expect(enJson).toMatch(/"confluence_phase_walk":\s*"loading hierarchy"/);
    expect(enJson).toMatch(/"jira_label":\s*"Importing Jira ticket"/);
    expect(enJson).toMatch(/"jira_phase_walk":\s*"loading hierarchy"/);
    expect(enJson).toMatch(/"jira_sprint_label":\s*"Importing sprint"/);
    expect(enJson).toMatch(/"jira_sprint_phase_walk":\s*"loading top-level tickets"/);
  });
});
