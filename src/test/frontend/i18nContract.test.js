import { describe, it, expect } from 'vitest';
import { resolve } from 'node:path';
import * as H from './support/pageModelHarness.js';

const de = JSON.parse(H.read(H.joinPath(H.RESOURCES, 'static/i18n/de.json')));
const en = JSON.parse(H.read(H.joinPath(H.RESOURCES, 'static/i18n/en.json')));

function flatten(node, prefix = '') {
  const out = [];
  for (const [key, value] of Object.entries(node)) {
    const path = prefix ? prefix + '.' + key : key;
    if (value !== null && typeof value === 'object' && !Array.isArray(value)) out.push(...flatten(value, path));
    else out.push([path, value]);
  }
  return out;
}

const deFlat = Object.fromEntries(flatten(de));
const enFlat = Object.fromEntries(flatten(en));
const keys = new Set(Object.keys(deFlat));
const namespaces = new Set(Object.keys(de));

const sources = [...H.templateFiles(), ...H.ownScriptFiles(), ...H.walk(H.KOTLIN, '.kt')]
  .map((file) => ({ file, text: H.read(file) }));

const contractSources = [...H.walk(H.FRONTEND, '.test.js'), ...H.walk(resolve(H.FRONTEND, '..'), '.kt')]
  .map((file) => ({ file, text: H.read(file) }));

const CANDIDATE = /[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+/g;
const DIRECT = [
  /\bi18n\.t(?:List)?\(\s*['"]([a-z0-9_]+(?:\.[a-z0-9_]+)+)['"]/g,
  /(?<![\w.])t(?:List)?\(\s*['"]([a-z0-9_]+(?:\.[a-z0-9_]+)+)['"]/g,
  /data-i18n(?:-(?:placeholder|title|aria-label|tooltip|html))?=["']([a-z0-9_]+(?:\.[a-z0-9_]+)+)["']/g,
  /#\{([a-z0-9_]+(?:\.[a-z0-9_]+)+)(?::[^}]*)?\}/g,
];
const PROPERTY = [
  /\b(?:titleKey|emptyKey|labelKey|msgKey|textKey|headingKey|subtitleKey|introMessageKey|suggestionsKey|plainSuggestionsKey|reasonKey|buttonKey|hintKey|hint|title)\s*[:=]\s*['"]([a-z0-9_]+(?:\.[a-z0-9_]+)+)['"]/g,
];
const DYNAMIC_PREFIX = [
  { re: /['"]([a-z0-9_]+(?:\.[a-z0-9_]+)*_?)['"]\s*\+/g, suffix: '' },
  { re: /['"]([a-z0-9_]+(?:\.[a-z0-9_]+)+_)['"]/g, suffix: '' },
  { re: /"([a-z0-9_]+(?:\.[a-z0-9_]+)*)\.\$\{?\w+/g, suffix: '.' },
  { re: /'([a-z0-9_]+(?:\.[a-z0-9_]+)*)\.\$\{?\w+/g, suffix: '.' },
];

function namespaceRoot(token) {
  return token.split('.')[0];
}

const referenced = new Map();
const dynamicPrefixes = new Set();

for (const { file, text } of sources) {
  if (file.endsWith('.json')) continue;
  for (const m of text.matchAll(CANDIDATE)) {
    if (keys.has(m[0]) && !referenced.has(m[0])) referenced.set(m[0], `${H.relative(H.RESOURCES, file)}:${text.slice(0, m.index).split('\n').length}`);
  }
  for (const re of DIRECT.concat(PROPERTY)) {
    for (const m of text.matchAll(re)) {
      if (!namespaces.has(namespaceRoot(m[1]))) continue;
      if (m[1].endsWith('_')) continue;
      if (!referenced.has(m[1])) referenced.set(m[1], `${H.relative(H.RESOURCES, file)}:${text.slice(0, m.index).split('\n').length}`);
    }
  }
  for (const rule of DYNAMIC_PREFIX) {
    for (const m of text.matchAll(rule.re)) {
      const token = m[1] + rule.suffix;
      if (!namespaces.has(namespaceRoot(token))) continue;
      dynamicPrefixes.add(token);
    }
  }
}

const coveredByDynamic = (key) => [...dynamicPrefixes]
  .filter((p) => p.includes('.') && p.length >= 5)
  .some((p) => key.startsWith(p.endsWith('_') || p.endsWith('.') ? p : p + '.'));

const topNamespaceOf = (key) => key.split('.')[0];
const allNamespaces = new Set(Object.keys(deFlat).map(topNamespaceOf));

const composedRequirements = [];
for (const { file } of sources) {
  if (!file.endsWith('.kt')) continue;
  const text = H.read(file);
  const namespaces = new Set();
  for (const m of text.matchAll(/"([a-z][a-z0-9_]*)\.\$\{?\w+/g)) namespaces.add(m[1]);
  if (!namespaces.size) continue;
  const bare = new Set();
  for (const m of text.matchAll(/\b(?:t|tr)\(\s*"([a-z0-9_]+)"/g)) bare.add(m[1]);
  for (const ns of namespaces) {
    if (!allNamespaces.has(ns)) continue;
    for (const leaf of bare) composedRequirements.push({ key: ns + '.' + leaf, file: H.relative(H.RESOURCES, file) });
  }
}

describe('i18n contract: every referenced key exists in de and en', () => {
  it('de.json and en.json expose the identical key set', () => {
    const onlyDe = Object.keys(deFlat).filter((k) => !(k in enFlat));
    const onlyEn = Object.keys(enFlat).filter((k) => !(k in deFlat));
    expect({ onlyDe, onlyEn }).toEqual({ onlyDe: [], onlyEn: [] });
  });

  it('de and en use the same placeholder numbering per key', () => {
    const ph = (v) => (String(v).match(/\{\d+\}/g) || []).sort().join(',');
    const mismatches = Object.keys(deFlat)
      .filter((k) => k in enFlat && ph(deFlat[k]) !== ph(enFlat[k]))
      .map((k) => `${k} de=${ph(deFlat[k])} en=${ph(enFlat[k])}`);
    expect(mismatches).toEqual([]);
  });

  it('no referenced key is missing from the catalogues', () => {
    const missing = [];
    for (const { file, text } of sources) {
      if (file.endsWith('.json')) continue;
      for (const re of DIRECT.concat(PROPERTY)) {
        for (const m of text.matchAll(re)) {
          if (!namespaces.has(namespaceRoot(m[1]))) continue;
          if (m[1].endsWith('_')) continue;
          if (!keys.has(m[1])) missing.push(`${m[1]} (${H.relative(H.RESOURCES, file)}:${text.slice(0, m.index).split('\n').length})`);
        }
      }
    }
    expect([...new Set(missing)].sort()).toEqual([]);
  });

  it('ignores trailing-underscore dynamic prefixes instead of reporting them as keys', () => {
    for (const re of DIRECT) {
      for (const m of "t('sprint_review.state_' + code, 'x')".matchAll(re)) expect(m[1].endsWith('_'), m[1]).toBe(true);
    }
  });

  it('keys composed from a kotlin namespace prefix exist in both catalogues', () => {
    const missing = [];
    for (const { key, file } of composedRequirements) {
      if (!keys.has(key)) missing.push(`${key} (${file})`);
    }
    expect([...new Set(missing)].sort()).toEqual([]);
  });

  it('composed keys count as referenced so they can never be pruned as unused', () => {
    for (const { key } of composedRequirements) {
      if (!keys.has(key)) continue;
      expect(referenced.has(key) || coveredByDynamic(key), key).toBe(true);
    }
  });

  it('detects a referenced-but-undefined key in a fixture', () => {
    const fixture = "showAlert(window.i18n.t('nope.does_not_exist'), 'danger');";
    const found = [];
    for (const re of DIRECT) {
      for (const m of fixture.matchAll(re)) if (!keys.has(m[1])) found.push(m[1]);
    }
    expect(found).toEqual(['nope.does_not_exist']);
  });
});

describe('i18n contract: catalogue entries stay in use', () => {
  it('no defined key is unreferenced outside a dynamic prefix', () => {
    const contractKept = new Set();
    for (const { text } of contractSources) {
      for (const m of text.matchAll(/[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+/g)) {
        if (keys.has(m[0])) contractKept.add(m[0]);
      }
    }
    const unused = Object.keys(deFlat)
      .filter((k) => !referenced.has(k) && !contractKept.has(k) && !coveredByDynamic(k))
      .sort();
    expect(unused).toEqual([]);
  });

  it('dynamic prefixes that exist actually resolve to keys', () => {
    const stale = [...dynamicPrefixes]
      .filter((p) => p.includes('.') && p.length >= 5)
      .filter((p) => !Object.keys(deFlat).some((k) => k.startsWith(p.endsWith('_') || p.endsWith('.') ? p : p + '.')))
      .sort();
    expect(stale).toEqual([]);
  });
});

export { deFlat, enFlat, keys, referenced, dynamicPrefixes, coveredByDynamic };
