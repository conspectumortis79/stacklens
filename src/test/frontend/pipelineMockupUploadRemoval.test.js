import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(__dirname, '..', '..', '..');
const TEMPLATES = resolve(ROOT, 'src', 'main', 'resources', 'templates');
const I18N = resolve(ROOT, 'src', 'main', 'resources', 'static', 'i18n');

const PAGE = resolve(TEMPLATES, 'pipeline-analysis.html');
const SCRIPTS = resolve(ROOT, 'src', 'main', 'resources', 'static', 'js', 'fragments', 'pipeline-analysis.js');
const STYLES = resolve(TEMPLATES, 'fragments', 'pipeline-analysis', 'styles.html');

const MOCKUP_MARKERS = [
  'PA_MOCKUP',
  'paMockup',
  'pa-mockup',
  'mockup_title',
  'mockup_zone_hint',
  'mockup_remove_title',
  'mockup_invalid_type',
  'mockup_too_large',
];

function read(path) {
  return readFileSync(path, 'utf8');
}

describe('Pipeline-Analyse: Mockup-Upload ist restlos entfernt', () => {
  for (const path of [PAGE, SCRIPTS, STYLES]) {
    it(`${path.split('/templates/')[1]} enthält keine Mockup-Reste`, () => {
      const source = read(path);
      for (const marker of MOCKUP_MARKERS) {
        expect(source, `${path} enthält weiterhin "${marker}"`).not.toContain(marker);
      }
    });
  }

  for (const lang of ['de', 'en']) {
    it(`${lang}.json pipeline-Sektion hat keine mockup-Keys mehr`, () => {
      const pipeline = JSON.parse(read(resolve(I18N, `${lang}.json`))).pipeline;
      const leftovers = Object.keys(pipeline).filter((key) => key.startsWith('mockup_'));
      expect(leftovers).toEqual([]);
    });
  }
});

describe('Pipeline-Analyse: Seiten-Guard ist vom Formular entkoppelt', () => {
  const source = read(SCRIPTS);

  it('paPageMounted prüft das Analyse-Panel statt der Benutzer-Anweisungs-Karte', () => {
    expect(read(PAGE)).toContain('id="paAnalysisPanel"');
    expect(source).toContain("function paPageMounted() {\n    return !!document.getElementById('paAnalysisPanel');");
    expect(source).not.toContain('paHasSelectableDom');
  });

  it('Sichtbarkeit der Karte wird über einen eigenen null-sicheren Helfer gesteuert', () => {
    expect(source).toContain('function paShowUserPromptCard() {');
    expect(source).toContain("var card = document.getElementById('paUserPromptCard');");
    expect(source).toContain("if (card) card.classList.remove('d-none');");
    expect(source.match(/paShowUserPromptCard\(\);/g).length).toBe(4);
    expect(source.match(/if \(!paPageMounted\(\)\) return;/g).length).toBe(3);
  });

  it('Benutzer-Anweisungen bleiben als verdrahtetes Feature erhalten', () => {
    expect(read(PAGE)).toContain('id="paUserPromptInput"');
    expect(source).toContain('function paUserPromptValue()');
    expect(source.match(/userPrompt: paUserPromptValue\(\)/g).length).toBe(3);
  });
});
