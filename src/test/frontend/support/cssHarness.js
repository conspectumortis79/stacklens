import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

export const RESOURCES = resolve(process.cwd(), 'src/main/resources');
const CSS_DIR = join(RESOURCES, 'static/css');
const TPL = join(RESOURCES, 'templates');
const JS = join(RESOURCES, 'static/js');
const KOTLIN = resolve(process.cwd(), 'src/main/kotlin');

function walk(dir, ext) {
  const out = [];
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full, ext));
    else if (entry.name.endsWith(ext)) out.push(full);
  }
  return out.sort();
}

export function cssFiles() {
  return [...walk(CSS_DIR, '.css'), ...walk(join(CSS_DIR, 'themes'), '.css')];
}

function markupAndScripts() {
  const sources = [];
  for (const file of [...walk(TPL, '.html'), ...walk(JS, '.js')]) {
    if (file.endsWith('.min.js')) continue;
    sources.push({ file, text: readFileSync(file, 'utf8') });
  }
  for (const file of walk(KOTLIN, '.kt')) sources.push({ file, text: readFileSync(file, 'utf8') });
  return sources;
}

const CLASS_ATTR = /\bclass(?:Name)?\s*=\s*\\?[\"'`]((?:\\.|[^\"'`\\])*?)(?:\\?[\"'`]|\s*\+)/g;

export function usedClassTokens(sources) {
  const used = new Set();
  const prefixes = new Set();
  for (const { text } of sources) {
    for (const m of text.matchAll(CLASS_ATTR)) {
      const value = (m[1] ?? m[2] ?? m[3] ?? m[4] ?? m[5] ?? '').replace(/\\(['"`])/g, '$1');
      for (const token of value.split(/[\s,${}]+/)) {
        if (!/^[A-Za-z_][\w-]*$/.test(token) || /^(?:th:|@)/.test(token)) continue;
        if (token.endsWith('-')) prefixes.add(token);
        else used.add(token);
      }
      const partial = value.match(/^([A-Za-z_][\w-]*)-\s*$/);
      if (partial) prefixes.add(partial[1] + '-');
    }
    for (const m of text.matchAll(/class(?:Name)?\s*=\s*["'`]([^"'`\s]*)["'`]\s*\+\s*[^"'`]*["'`]([^"'`]*)["'`]/g)) {
      prefixes.add(m[1]);
    }
    for (const m of text.matchAll(/classList\.(?:add|remove|toggle|contains)\(\s*['"]([^'"]+)['"]/g)) {
      for (const token of m[1].split(/\s+/)) if (/^[A-Za-z_][\w-]*$/.test(token)) used.add(token);
    }
    for (const m of text.matchAll(/class=\\?["'`]([A-Za-z_][\w-]*)\$\{/g)) prefixes.add(m[1]);
    for (const m of text.matchAll(/className\s*=\s*['"`]?([A-Za-z_][\w-]*)-\s*\+/g)) prefixes.add(m[1] + '-');
    for (const m of text.matchAll(/(?:querySelector|querySelectorAll|closest|matches)\(\s*['"][^'"]*\.([A-Za-z_][\w-]*)/g)) used.add(m[1]);
  }
  return { used, prefixes };
}

const RULE_RE = /([^{}]+)\{[^{}]*\}/g;

export function definedClasses(cssText) {
  const stripped = cssText.replace(/\/\*[\s\S]*?\*\//g, ' ');
  const out = new Map();
  for (const m of stripped.matchAll(RULE_RE)) {
    const selectorList = m[1];
    if (/@(keyframes|media|supports|font-face)/.test(selectorList)) continue;
    for (const dm of selectorList.matchAll(/\.(-?[_a-zA-Z][\w-]*)/g)) {
      const name = dm[1];
      if (!out.has(name)) out.set(name, []);
      out.get(name).push(selectorList.trim().replace(/\s+/g, ' '));
    }
  }
  return out;
}

export function buildIndex() {
  const sources = markupAndScripts();
  const { used, prefixes } = usedClassTokens(sources);
  const defined = new Map();
  for (const file of cssFiles()) {
    const rel = relative(RESOURCES, file);
    for (const [name, selectors] of definedClasses(readFileSync(file, 'utf8'))) {
      if (!defined.has(name)) defined.set(name, []);
      for (const selector of selectors) defined.get(name).push({ file: rel, selector });
    }
  }
  return { used, prefixes, defined, sources };
}
