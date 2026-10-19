import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SQL_SERVICE = resolve(
  __dirname,
  '../../main/kotlin/com/stacklens/application/service/SqlAnalysisService.kt',
);

function splitFunctions(src) {
  const out = [];
  const re = /override\s+fun\s+(\w+)\s*\([^)]*\)\s*:\s*[^{]+\{/g;
  let m;
  while ((m = re.exec(src)) !== null) {
    const name = m[1];
    const start = m.index + m[0].length - 1;
    let depth = 0;
    let i = start;
    while (i < src.length) {
      const c = src[i];
      if (c === '{') depth++;
      else if (c === '}') {
        depth--;
        if (depth === 0) {
          out.push({ name, body: src.slice(start, i + 1) });
          break;
        }
      }
      i++;
    }
  }
  return out;
}

describe('SqlAnalysisService — every AI call must be token-tracked', () => {
  const src = readFileSync(SQL_SERVICE, 'utf8');
  const fns = splitFunctions(src);

  it('the service contains the four known AI-call entry points', () => {
    const names = fns.map((f) => f.name).sort();
    expect(names).toEqual(
      expect.arrayContaining([
        'analyzeCombined',
        'analyzeQuery',
        'generateSampleData',
        'generateQuery',
      ]),
    );
  });

  for (const { name, body } of fns) {
    if (!/copilotCliService\.execute\b/.test(body)) continue;

    it(`${name}() wraps its copilot call in an aiUsageContext binding`, () => {
      const hasBind =
        /aiUsageContext\.setContext\(\s*["']sql["']/.test(body) ||
        /aiUsageContext\.withContext\(\s*["']sql["']/.test(body);

      expect(
        hasBind,
        `${name}() calls copilot without binding aiUsageContext — ` +
          `the dev-options token bar would always show "keine Daten". ` +
          `Wrap the copilot call in aiUsageContext.withContext("sql", id) { \u2026 }.`,
      ).toBe(true);
    });

    it(`${name}() echoes the analysis id back to the frontend`, () => {
      const hasVirtualId = /(?:System\.currentTimeMillis|clock\.epochMillis)\s*\(\s*\)/.test(body);
      const echoesAsHistoryId = /historyId\s*=\s*record\.id/.test(body);
      const echoesAsAnalysisId =
        /\.copy\(\s*analysisId\s*=/.test(body) || /analysisId\s*=/.test(body);
      const hasAnyIdBinding =
        (hasVirtualId && echoesAsAnalysisId) || echoesAsHistoryId;

      expect(
        hasAnyIdBinding,
        `${name}() must produce an id the JS can pass to cfTokenUsage.init. ` +
          `Either use the persisted SqlAnalysisRecord id (historyId = record.id) ` +
          `or generate a virtual id (clock.epochMillis) and copy it into ` +
          `the response (copy(analysisId = …)).`,
      ).toBe(true);
    });
  }
});