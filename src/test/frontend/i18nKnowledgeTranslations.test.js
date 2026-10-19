import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';


const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const I18N_DIR = resolve(__dirname, '../../main/resources/static/i18n');

function readJson(filename) {
  const path = resolve(I18N_DIR, filename);
  const raw = readFileSync(path, 'utf8');
  return { raw, data: JSON.parse(raw), path };
}

function pluck(obj, path) {
  const parts = path.split('.');
  let cur = obj;
  for (const p of parts) {
    if (cur == null || typeof cur !== 'object') return undefined;
    cur = cur[p];
  }
  return cur;
}

function resolvePath(ref) {
  if (ref.startsWith('nav.') || ref.startsWith('page_titles.')) return ref;
  if (ref.startsWith('knowledge.')) return ref;
  return ref;
}


describe('Knowledge i18n JSON structural integrity', () => {
  for (const lang of ['de', 'en']) {
    it(`${lang}.json parses as valid JSON`, () => {
      const { raw } = readJson(`${lang}.json`);
      expect(() => JSON.parse(raw)).not.toThrow();
    });

    it(`${lang}.json defines top-level "knowledge" as an object`, () => {
      const { data } = readJson(`${lang}.json`);
      expect(data.knowledge).toBeDefined();
      expect(typeof data.knowledge).toBe('object');
      expect(data.knowledge).not.toBeNull();
    });
  }
});


describe('Knowledge i18n coverage (bug-report scope)', () => {
  const cases = [
    {
      ref: 'nav.knowledge',
      lookup: 'nav.knowledge',
      symptom: 'sidebar falls back to "Wissensdatenbank" on every language',
    },
    {
      ref: 'page_titles.knowledge',
      lookup: 'page_titles.knowledge',
      symptom: 'document.title / browser tab title falls back to default',
    },
    {
      ref: 'knowledge.add.file_picker.title',
      lookup: 'knowledge.add.file_picker.title',
      symptom: 'file picker modal "Datei auswählen" falls back to default',
    },
    {
      ref: 'knowledge.add.file_picker.confirm',
      lookup: 'knowledge.add.file_picker.confirm',
      symptom: 'file picker modal "Hochladen und vektorisieren" falls back to default',
    },
    {
      ref: 'knowledge.add.file_picker.empty',
      lookup: 'knowledge.add.file_picker.empty',
      symptom: 'file picker empty-state message falls back to default',
    },
    {
      ref: 'knowledge.add.file_picker.no_selection',
      lookup: 'knowledge.add.file_picker.no_selection',
      symptom: 'file picker "no selection" validation message falls back to default',
    },
    {
      ref: 'knowledge.reindex.title',
      lookup: 'knowledge.reindex.title',
      symptom: 'Wartung card-header falls back to default (no card title)',
    },
    {
      ref: 'knowledge.danger.title',
      lookup: 'knowledge.danger.title',
      symptom: 'Gefahrenzone card-header falls back to default (no card title)',
    },
  ];

  for (const c of cases) {
    for (const lang of ['de', 'en']) {
      it(`${lang}.json has ${c.ref} — ${c.symptom}`, () => {
        const { data } = readJson(`${lang}.json`);
        const v = pluck(data, c.lookup);
        expect(v, `${c.ref} missing in ${lang}.json (looked up at ${c.lookup})`).toBeDefined();
        expect(v, `${c.ref} is null in ${lang}.json`).not.toBeNull();
        expect(
          typeof v === 'string' && v.trim().length > 0,
          `${c.ref} in ${lang}.json is blank or not a string: ${JSON.stringify(v)}`,
        ).toBe(true);
        expect(v, `${c.ref} in ${lang}.json equals its key (fall-through)`).not.toBe(c.ref);
      });
    }
  }

  it('en.json translations differ from de.json for the bug-report keys', () => {
    const de = readJson('de.json').data;
    const en = readJson('en.json').data;
    const distinctPairs = cases.filter(c => {
      const d = pluck(de, c.lookup);
      const e = pluck(en, c.lookup);
      return typeof d === 'string' && typeof e === 'string' && d !== e;
    });
    expect(
      distinctPairs.length,
      'No translations differ between de.json and en.json — en.json was probably copy-pasted from de.json',
    ).toBeGreaterThan(0);
  });
});