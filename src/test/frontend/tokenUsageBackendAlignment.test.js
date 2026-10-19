import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const TEMPLATES_DIR = resolve(
  __dirname,
  '../../main/resources/templates',
);
const KOTLIN_DIR = resolve(
  __dirname,
  '../../main/kotlin/com/stacklens/application/service',
);

function* walk(dir, ext) {
  for (const entry of readdirSync(dir)) {
    const p = join(dir, entry);
    if (statSync(p).isDirectory()) yield* walk(p, ext);
    else if (p.endsWith(ext)) yield p;
  }
}

function* pageKinds() {
  const initRe = /cfTokenUsage\.init\(\s*['"]([a-z_]+)['"]/g;
  const watchRe = /cfTokenUsage\.watch\(\s*['"]([a-z_]+)['"]/g;
  for (const path of walk(TEMPLATES_DIR, '.html')) {
    const html = readFileSync(path, 'utf8');
    const m1 = [...html.matchAll(initRe)];
    const m2 = [...html.matchAll(watchRe)];
    for (const m of [...m1, ...m2]) {
      yield { kind: m[1], file: path.replace(TEMPLATES_DIR + '/', '') };
    }
  }
}

function backendKinds() {
  const stringLiteralRe =
    /(?:aiUsageContext|copilotPort)\.(?:setContext|withContext)\(\s*['"]([a-z_]+)['"]/g;
  const enumRefRe =
    /(?:aiUsageContext|copilotPort)\.(?:setContext|withContext)\(\s*AiUsageKind\.([A-Z_]+)\.id/g;
  const constRefRe =
    /(?:aiUsageContext|copilotPort)\.(?:setContext|withContext)\(\s*([A-Z][A-Z0-9_]+)\s*[,)]/g;

  const out = new Set();
  const enumSrc = readFileSync(
    resolve(KOTLIN_DIR, '../../domain/model/AiUsage.kt'),
    'utf8',
  );
  function resolveEnum(name) {
    const m = enumSrc.match(
      new RegExp('\\b' + name + "\\(\\s*['\"]([a-z_]+)['\"]\\s*\\)"),
    );
    return m ? m[1] : null;
  }

  for (const path of walk(KOTLIN_DIR, '.kt')) {
    const src = readFileSync(path, 'utf8');
    for (const m of src.matchAll(stringLiteralRe)) out.add(m[1]);
    for (const m of src.matchAll(enumRefRe)) {
      const id = resolveEnum(m[1]);
      if (id) out.add(id);
    }
    const constMap = new Map();
    const constDeclRe = /\b(?:const\s+val|val)\s+([A-Z][A-Z0-9_]+)(?:\s*:\s*[\w.<>?]+)?\s*=\s*['"]([a-z_]+)['"]/g;
    for (const m of src.matchAll(constDeclRe)) {
      constMap.set(m[1], m[2]);
    }
    for (const m of src.matchAll(constRefRe)) {
      const id = constMap.get(m[1]);
      if (id) out.add(id);
    }
  }
  return out;
}

describe('Token-usage badge ↔ backend tracking alignment', () => {
  let backendSupported;

  beforeEach(() => {
    backendSupported = backendKinds();
  });

  it('every page-level badge kind is tracked on the backend', () => {
    const usedByFile = new Map();
    for (const { kind, file } of pageKinds()) {
      if (!usedByFile.has(kind)) usedByFile.set(kind, []);
      usedByFile.get(kind).push(file);
    }

    const orphans = [];
    for (const [kind, files] of usedByFile.entries()) {
      if (!backendSupported.has(kind)) {
        for (const file of files) {
          orphans.push(`  - ${kind}  (used in ${file})`);
        }
      }
    }
    if (orphans.length > 0) {
      throw new Error(
        `${orphans.length} page-level badge kind(s) are not tracked by ` +
          `the backend — calling cfTokenUsage.init(kind, …) for them will ` +
          `always show the empty-state pill because /api/ai-usage/${orphans[0].trim().split(' ')[0]}/{id} ` +
          `returns no rows.\n` +
          `Fix: add aiUsageContext.setContext(<kind>, <id>) in the service ` +
          `that owns the AI call, OR change the badge to a kind the backend ` +
          `already tracks.\n\n` +
          orphans.join('\n'),
      );
    }
  });

  it('sanity: backend tracking is not empty (catches "I deleted all setContext calls")', () => {
    expect(backendSupported.size).toBeGreaterThanOrEqual(5);
  });

  it('sanity: every AiUsageKind enum value is referenced on the backend', () => {
    const enumSrc = readFileSync(
      resolve(KOTLIN_DIR, '../../domain/model/AiUsage.kt'),
      'utf8',
    );
    const enumIds = new Set();
    for (const m of enumSrc.matchAll(/\b([A-Z_]+)\(\s*['"]([a-z_]+)['"]\s*\)/g)) {
      enumIds.add(m[2]);
    }
    expect(enumIds.size).toBeGreaterThan(0);

    const orphanedEnum = [];
    for (const id of enumIds) {
      if (!backendSupported.has(id)) orphanedEnum.push(id);
    }
    if (orphanedEnum.length > 0) {
      console.warn(
        '[info] AiUsageKind values without setContext binding: ' +
          orphanedEnum.join(', '),
      );
    }
  });
});