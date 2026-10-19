import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

const REPO_ROOT = resolve(__dirname, '..', '..', '..');
const I18N_DIR = join(REPO_ROOT, 'src', 'main', 'resources', 'static', 'i18n');
const TEMPLATES_DIR = join(REPO_ROOT, 'src', 'main', 'resources', 'templates');
const STATIC_JS_DIR = join(REPO_ROOT, 'src', 'main', 'resources', 'static', 'js');

function readJson(filename) {
  const path = join(I18N_DIR, filename);
  const raw = readFileSync(path, 'utf8');
  return { raw, data: JSON.parse(raw), path };
}

function walk(dir, acc) {
  const out = acc || [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}

function topLevelKeys(raw) {

  let start = 0;
  while (start < raw.length && /\s/.test(raw[start])) start++;
  if (raw[start] !== '{') throw new Error('Not a JSON object');
  const keys = [];
  let i = start + 1;
  let depth = 0;
  let inStr = false;
  while (i < raw.length) {
    const c = raw[i];
    if (inStr) {
      if (c === '\\') { i += 2; continue; }
      if (c === '"') inStr = false;
      i++;
      continue;
    }
    if (c === '"') {

      if (depth === 0) {
        const j = raw.indexOf('"', i + 1);
        const key = raw.slice(i + 1, j);

        let k = j + 1;
        while (k < raw.length && /\s/.test(raw[k])) k++;
        if (raw[k] === ':') {
          keys.push(key);
          i = j + 1;
          continue;
        }

        inStr = true;
        i = j + 1;
        continue;
      }
      inStr = true;
      i++;
      continue;
    }
    if (c === '{' || c === '[') depth++;
    else if (c === '}' || c === ']') depth--;
    i++;
  }
  return keys;
}

function collectReferencedPipelineKeys() {
  const refs = new Set();
  const files = walk(TEMPLATES_DIR).filter(f => f.endsWith('.html'))
    .concat(walk(STATIC_JS_DIR).filter(f => f.endsWith('.js')));

  const patterns = [
    /data-i18n="(pipeline\.[a-zA-Z0-9_]+)"/g,
    /paT\(\s*['"](pipeline\.[a-zA-Z0-9_]+)['"]\s*\)/g,
  ];

  for (const f of files) {
    const src = readFileSync(f, 'utf8');
    for (const re of patterns) {
      let m;
      while ((m = re.exec(src)) !== null) refs.add(m[1]);
    }
  }

  const leaves = new Set();
  for (const ref of refs) leaves.add(ref.slice('pipeline.'.length));
  return { leaves, refs };
}

describe('i18n JSON structural integrity', () => {
  for (const lang of ['de', 'en']) {
    it(`${lang}.json parses as valid JSON`, () => {
      const { raw } = readJson(`${lang}.json`);

      expect(() => JSON.parse(raw)).not.toThrow();
    });

    it(`${lang}.json has NO duplicate top-level keys`, () => {
      const { raw } = readJson(`${lang}.json`);
      const keys = topLevelKeys(raw);
      const seen = new Map();
      const dupes = [];
      for (const k of keys) {
        seen.set(k, (seen.get(k) || 0) + 1);
        if (seen.get(k) === 2) dupes.push(k);
      }
      expect(
        dupes,
        `${lang}.json contains duplicate top-level keys: ${dupes.join(', ')}. ` +
          `Browsers and Jackson silently use the LAST occurrence on a ` +
          `duplicate, which silently DROPS the earlier object keys. ` +
          `This was the root cause of the Pipeline-Analyse page showing ` +
          `raw keys like "pipeline.status_completed".`,
      ).toEqual([]);
    });

    it(`${lang}.json has exactly one "pipeline" top-level key`, () => {
      const { raw } = readJson(`${lang}.json`);
      const keys = topLevelKeys(raw);
      const pipelineCount = keys.filter(k => k === 'pipeline').length;
      expect(pipelineCount, `${lang}.json has ${pipelineCount} top-level "pipeline" keys`).toBe(1);
    });
  }
});

describe('pipeline.* i18n coverage', () => {
  const { leaves, refs } = collectReferencedPipelineKeys();

  it('collects at least 30 pipeline.* references from templates', () => {

    expect(refs.size).toBeGreaterThanOrEqual(30);
  });

  for (const lang of ['de', 'en']) {
    const { data } = readJson(`${lang}.json`);
    const pipeline = data.pipeline || {};

    it(`${lang}.json defines a "pipeline" object`, () => {
      expect(typeof pipeline).toBe('object');
      expect(pipeline).not.toBeNull();
    });

    it(`${lang}.json pipeline has >= 80 keys (UI + be_*)`, () => {

      expect(Object.keys(pipeline).length).toBeGreaterThanOrEqual(80);
    });

    for (const leaf of leaves) {
      it(`${lang}.json has pipeline.${leaf}`, () => {
        const v = pipeline[leaf];
        expect(v, `pipeline.${leaf} is missing in ${lang}.json`).toBeDefined();
        expect(v, `pipeline.${leaf} is null in ${lang}.json`).not.toBeNull();
        expect(
          typeof v === 'string' && v.trim().length > 0,
          `pipeline.${leaf} in ${lang}.json is blank or not a string: ${JSON.stringify(v)}`,
        ).toBe(true);

        expect(v, `pipeline.${leaf} in ${lang}.json equals its key (fall-through)`).not.toBe(`pipeline.${leaf}`);
      });
    }
  }
});
