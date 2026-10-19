import { describe, it, expect } from 'vitest';
import * as H from './support/pageModelHarness.js';

const BUILTIN_GLOBALS = new Set([
  'if', 'for', 'while', 'switch', 'return', 'typeof', 'instanceof', 'new', 'delete', 'void', 'do',
  'function', 'event', 'this', 'window', 'document', 'navigator', 'location', 'history', 'console',
  'alert', 'confirm', 'prompt', 'JSON', 'String', 'Number', 'Boolean', 'Math', 'Date', 'RegExp',
  'Array', 'Object', 'Set', 'Map', 'Symbol', 'Promise', 'Error', 'fetch', 'open', 'close', 'focus',
  'blur', 'select', 'click', 'submit', 'reset', 'scroll', 'scrollBy', 'scrollTo', 'print',
  'getComputedStyle', 'matchMedia', 'structuredClone', 'setTimeout', 'setInterval', 'clearTimeout',
  'clearInterval', 'requestAnimationFrame', 'parseInt', 'parseFloat', 'isNaN', 'encodeURIComponent',
  'decodeURIComponent', 'encodeURI', 'decodeURI', 'btoa', 'atob', 'require', 'import', 'try', 'catch',
  'finally', 'else', 'break', 'continue', 'throw', 'await', 'async', 'class', 'super', 'localStorage',
  'sessionStorage', 'FormData', 'Blob', 'FileReader', 'URL', 'Event', 'CustomEvent', 'Intl',
]);

function pageUnits(page) {
  const entries = H.closureForTemplate(page);
  const scriptSources = [];
  const allSources = [];
  for (const entry of entries) {
    allSources.push({ owner: entry.rel, text: entry.text });
    for (const block of H.splitScripts(entry.text)) {
      if (block.attrs.includes('src=') || !block.body.trim()) continue;
      const text = H.stripCdta(block.body);
      scriptSources.push({ owner: `${entry.rel}#L${block.line}`, text });
      allSources.push({ owner: `${entry.rel}#L${block.line}`, text });
    }
  }
  for (const url of H.reachableJsAssets(H.scriptAssetsOf(entries))) {
    const file = H.jsFileForAsset(url);
    if (!file) continue;
    const text = H.read(file);
    scriptSources.push({ owner: url, text });
    allSources.push({ owner: url, text });
  }
  return { entries, scriptSources, allSources };
}

const PAGES = H.pages();
const UNITS = new Map(PAGES.map((p) => [H.relative(H.TEMPLATES, p), pageUnits(p)]));

function globalNamesOf(sources) {
  const names = new Set();
  for (const { text } of sources) {
    const decl = H.topLevelDeclarations(text);
    for (const f of decl.funcs) names.add(f.name);
    for (const v of decl.vars) names.add(v.name);
    for (const m of text.matchAll(/\b([A-Za-z_$][\w$]*)[ \t]*:[ \t]*(?:async[ \t]+)?function\b/g)) names.add(m[1]);
    for (const m of text.matchAll(/\bimport\s*\{([^}]*)\}/g)) {
      for (const part of m[1].split(',')) names.add(part.trim().split(/\s+as\s+/).pop().trim());
    }
  }
  return names;
}

describe('page model: fragment closure resolves', () => {
  it('every page resolves to at least its own template', () => {
    for (const [name, { entries }] of UNITS) expect(entries.length, name).toBeGreaterThan(0);
  });

  it('layout fragments reach every decorated page', () => {
    for (const [name, { entries }] of UNITS) {
      if (name === 'layout.html') continue;
      if (!H.read(H.joinPath(H.TEMPLATES, name)).includes('layout:decorate')) continue;
      expect(entries.some((e) => e.rel === 'layout.html'), name).toBe(true);
    }
  });
});

describe('page model: one page declares each global function once', () => {
  it('no page declares the same top-level function twice', () => {
    const collisions = [];
    for (const [page, { scriptSources }] of UNITS) {
      const seen = new Map();
      for (const { owner, text } of scriptSources) {
        for (const f of H.topLevelDeclarations(text).funcs) {
          if (!seen.has(f.name)) seen.set(f.name, []);
          seen.get(f.name).push(`${owner}:${f.line}`);
        }
      }
      for (const [name, sites] of seen) if (sites.length > 1) collisions.push(`${page} -> ${name} @ ${sites.join(', ')}`);
    }
    expect(collisions).toEqual([]);
  });

  it('detects a synthetic duplicate declaration in a fixture', () => {
    const fixture = 'function handler() { return 1; }\nfunction handler() { return 2; }\n';
    expect(H.topLevelDeclarations(fixture).funcs.length).toBe(2);
  });

  it('does not treat nested or IIFE-local functions as globals', () => {
    const fixture = '(function(){ function inner(){} function use(){ inner(); } })();\nfunction outer(){}\n';
    expect(H.topLevelDeclarations(fixture).funcs.map((f) => f.name)).toEqual(['outer']);
  });
});

describe('page model: inline handlers resolve against the page', () => {
  it('every on* attribute calls a name declared on the same page', () => {
    const unresolved = [];
    for (const [page, { scriptSources, allSources }] of UNITS) {
      const declared = globalNamesOf(scriptSources);
      for (const { owner, text } of allSources) {
        for (const call of H.inlineHandlerCalls(text)) {
          if (BUILTIN_GLOBALS.has(call.name) || declared.has(call.name)) continue;
          unresolved.push(`${page} -> ${call.name} (${owner}:${call.line})`);
        }
      }
    }
    expect([...new Set(unresolved)].sort()).toEqual([]);
  });

  it('detects a synthetic unknown handler in a fixture', () => {
    const fixture = '<button onclick="definitelyNotDefined(1)">x</button>';
    const calls = H.inlineHandlerCalls(fixture).map((c) => c.name);
    expect(calls).toContain('definitelyNotDefined');
    expect(globalNamesOf([{ text: fixture }]).has('definitelyNotDefined')).toBe(false);
  });
});

describe('page model: element ids referenced from script exist in the shipped markup', () => {
  const declaredIds = new Set();
  const dynamicPrefixes = new Set();
  const allSources = [];
  for (const file of [...H.templateFiles(), ...H.ownScriptFiles()]) {
    const text = H.read(file);
    allSources.push(text);
    for (const block of H.splitScripts(text)) allSources.push(block.body);
  }
  for (const text of allSources) {
    for (const id of H.idLiterals(text)) declaredIds.add(id);
    for (const prefix of H.idConcatPrefixes(text)) dynamicPrefixes.add(prefix);
  }

  it('each getElementById target is produced by markup, script, or a verified id prefix', () => {
    const orphans = [];
    for (const file of [...H.templateFiles(), ...H.ownScriptFiles()]) {
      const text = H.read(file);
      const bodies = [text, ...H.splitScripts(text).map((b) => b.body)];
      for (const body of bodies) {
        for (const [id, line] of H.referencedIds(body)) {
          if (declaredIds.has(id)) continue;
          if ([...dynamicPrefixes].some((prefix) => id.startsWith(prefix))) continue;
          orphans.push(`${id} (${H.relative(H.RESOURCES, file)}:${line})`);
        }
      }
    }
    expect([...new Set(orphans)].sort()).toEqual([]);
  });

  it('detects a synthetic orphan id and honours dynamic id prefixes', () => {
    const fixture = "var x = document.getElementById('neverRendered');";
    expect([...H.referencedIds(fixture).keys()]).toEqual(['neverRendered']);
    expect(declaredIds.has('neverRendered')).toBe(false);
    expect([...dynamicPrefixes].some((prefix) => 'hxCard-logging'.startsWith(prefix))).toBe(true);
  });
});
