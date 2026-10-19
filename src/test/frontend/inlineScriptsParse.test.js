import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const FRAGMENTS_DIR = resolve(__dirname, '../../main/resources/templates/fragments');
const SCRIPT_RE = /<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;

function* walkHtml(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    const s = statSync(p);
    if (s.isDirectory()) yield* walkHtml(p);
    else if (name.endsWith('.html')) yield p;
  }
}

function collectBlocks(html) {
  return [...html.matchAll(SCRIPT_RE)]
    .map((m) => m[1])
    .filter((b) => b.trim().length > 0);
}

describe('Inline <script> blocks in Thymeleaf fragments parse cleanly', () => {
  const files = [...walkHtml(FRAGMENTS_DIR)];
  expect(files.length).toBeGreaterThan(20);

  const allErrors = [];
  for (const file of files) {
    const html = readFileSync(file, 'utf8');
    const blocks = collectBlocks(html);
    for (let i = 0; i < blocks.length; i++) {
      try {
        new vm.Script(blocks[i], { filename: `${file}#block${i}` });
      } catch (e) {
        allErrors.push({ file, blockIdx: i, message: e.message, stack: e.stack });
      }
    }
  }

  it('every <script> block parses without SyntaxError', () => {
    if (allErrors.length === 0) return;
    const report = allErrors
      .map((e) => {
        const lineMatch = (e.stack || '').match(/(?::(\d+)){1,2}/);
        const lineInfo = lineMatch ? ` (block line ${lineMatch[1]})` : '';
        return `  - ${e.file}${lineInfo}\n      ${e.message}`;
      })
      .join('\n');
    throw new Error(
      `${allErrors.length} inline <script> block(s) failed to parse. ` +
        `Most common cause: \\' inside a single-quoted JS literal — use '.\n` +
        report,
    );
  });

  it('config page defines the theme refresh helper used by page initialization', () => {
    const configScript = readFileSync(join(FRAGMENTS_DIR, 'config/scripts-page.html'), 'utf8');
    expect(configScript).toMatch(/function cfRefreshThemeChoice\(\)/);
    expect(configScript).toContain('cfRefreshThemeChoice();');
  });
  it('no fragment contains the literal buggy pattern', () => {
    const needle = ['\\', "'", 'f', 'u', 'n', 'c', 't', 'i', 'o', 'n', '\\', "'"].join('');
    const offenders = [];
    for (const file of files) {
      const html = readFileSync(file, 'utf8');
      if (html.includes(needle)) offenders.push(file);
    }
    expect(offenders).toEqual([]);
  });
});