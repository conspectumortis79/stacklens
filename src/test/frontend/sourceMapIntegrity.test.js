import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, existsSync, statSync } from 'node:fs';
import { resolve, join, relative } from 'node:path';

const STATIC = resolve(process.cwd(), 'src/main/resources/static');
const MAP_REF = /(?:\/\/|\/\*)#\s*sourceMappingURL=([^\s*]+)/g;

function filesUnder(dir) {
  const out = [];
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) out.push(...filesUnder(full));
    else if (/\.(css|js)$/.test(entry) && !entry.endsWith('.map')) out.push(full);
  }
  return out.sort();
}

function mapReferences(file) {
  const text = readFileSync(file, 'utf8');
  return [...text.matchAll(MAP_REF)].map((m) => m[1]);
}

describe('shipped assets never point at a missing source map', () => {
  const candidates = [...filesUnder(join(STATIC, 'vendor')), ...filesUnder(join(STATIC, 'js'))]
    .filter((f) => !f.endsWith('.min.js') || existsSync(f));

  it('the asset corpus was discovered', () => {
    expect(candidates.length).toBeGreaterThan(20);
    expect(candidates.some((f) => f.includes('/vendor/'))).toBe(true);
  });

  it('every sourceMappingURL target exists next to its asset', () => {
    const broken = [];
    for (const file of candidates) {
      for (const ref of mapReferences(file)) {
        if (/^https?:/.test(ref) || /^data:/.test(ref)) continue;
        const target = resolve(join(file, '..'), ref);
        if (!existsSync(target)) broken.push(`${relative(STATIC, file)} -> ${ref}`);
      }
    }
    expect(broken.sort()).toEqual([]);
  });

  it('flags an asset whose map file is absent', () => {
    const broken = [];
    for (const file of candidates) {
      for (const ref of mapReferences(file)) {
        if (!existsSync(resolve(join(file, '..'), ref))) broken.push(file);
      }
    }
    expect(broken).toEqual([]);
    expect(mapReferences(join(STATIC, 'js/grafana/chart.umd.min.js'))).toEqual([]);
    expect(readFileSync(join(STATIC, 'js/grafana/chart.umd.min.js'), 'utf8')).toContain('window.Chart');
  });
});
