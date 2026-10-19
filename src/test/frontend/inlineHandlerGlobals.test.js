import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const ROOT = resolve(process.cwd(), 'src/main/resources');
const TPL = resolve(ROOT, 'templates');
const JS = resolve(ROOT, 'static/js');

function walk(dir, ext) {
  const out = [];
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const full = resolve(dir, e.name);
    if (e.isDirectory()) out.push(...walk(full, ext));
    else if (e.name.endsWith(ext)) out.push(full);
  }
  return out;
}

const files = [...walk(TPL, '.html'), ...walk(JS, '.js')];
const sources = files.map((file) => [file, readFileSync(file, 'utf8')]);

function scriptBlocks(text) {
  return [...text.matchAll(/<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi)]
    .map((m) => m[1])
    .filter((b) => b.trim());
}

function stripCdata(block) {
  return block.replace(/\/\*<!\[CDATA\[\*\//g, '').replace(/\/\*\]\]>\*\//g, '');
}

function isIifeWrapped(block) {
  const code = stripCdata(block);
  return /^\(function/m.test(code) || /^\}\)\(\);?/m.test(code);
}

const BUILTIN = new Set([
  'if', 'return', 'event', 'this', 'document', 'window', 'alert', 'confirm', 'String', 'Number', 'Boolean',
  'void', 'typeof', 'parseInt', 'parseFloat', 'isNaN', 'JSON', 'Math', 'Date', 'Set', 'Map', 'Array', 'Object',
  'setTimeout', 'setInterval', 'clearTimeout', 'clearInterval', 'encodeURIComponent', 'decodeURIComponent',
  'stopPropagation', 'preventDefault', 'closest', 'getAttribute', 'setAttribute', 'classList', 'toggle', 'add',
  'remove', 'contains', 'then', 'catch', 'function', 'getElementById', 'querySelector', 'querySelectorAll',
  'addEventListener', 'removeEventListener', 'createElement', 'appendChild', 'removeChild', 'focus', 'blur',
  'select', 'click', 'submit', 'reset', 'open', 'reload', 'assign', 'scrollIntoView', 'getBoundingClientRect',
  'value', 'style', 'split', 'join', 'push', 'filter', 'map', 'forEach', 'find', 'some', 'every', 'replace',
  'test', 'trim', 'slice', 'indexOf', 'includes', 'startsWith', 'endsWith', 'toFixed', 'padStart', 'match',
  'toUpperCase', 'toLowerCase', 'stringify', 'parse', 'log', 'keys', 'values', 'sort', 'reverse', 'concat',
  'abs', 'max', 'min', 'round', 'floor', 'ceil', 'random', 'now', 'getTime', 'toISOString', 'togglePw',
]);

function anyDeclaration() {
  const names = new Set();
  for (const [, text] of sources) {
    for (const m of text.matchAll(/function[ \t]+([A-Za-z_$][\w$]*)[ \t]*\(/g)) names.add(m[1]);
    for (const m of text.matchAll(/(?:var|let|const)[ \t]+([A-Za-z_$][\w$]*)[ \t]*[=:]/g)) names.add(m[1]);
    for (const m of text.matchAll(/window\.([A-Za-z_$][\w$]*)[ \t]*[=:]/g)) names.add(m[1]);
  }
  return names;
}

function globalDeclarations() {
  const names = new Set();
  for (const [, text] of sources) {
    for (const m of text.matchAll(/^(?:async[ \t]+)?function[ \t]+([A-Za-z_$][\w$]*)/gm)) names.add(m[1]);
    for (const m of text.matchAll(/window\.([A-Za-z_$][\w$]*)[ \t]*[=:]/g)) names.add(m[1]);
    for (const m of text.matchAll(/window\[(['"])([A-Za-z_$][\w$]*)\1\][ \t]*=/g)) names.add(m[2]);
    for (const block of scriptBlocks(text)) {
      if (isIifeWrapped(block)) continue;
      for (const m of stripCdata(block).matchAll(/^[ \t]*function[ \t]+([A-Za-z_$][\w$]*)[ \t]*\(/gm)) names.add(m[1]);
    }
  }
  return names;
}

function handlerNames() {
  const found = new Map();
  for (const [file, text] of sources) {
    const code = text.replace(/\/\/[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '');
    for (const m of code.matchAll(/\bon(?:click|change|input|keydown|submit|dblclick|focus|blur|load|error)\s*=\s*"([^"]*)"/g)) {
      for (const c of m[1].matchAll(/(^|[^.\w$])([A-Za-z_$][\w$]*)\s*\(/g)) {
        const name = c[2];
        if (BUILTIN.has(name)) continue;
        if (!found.has(name)) found.set(name, file.replace(ROOT, ''));
      }
    }
  }
  return found;
}

const PRE_EXISTING_UNDEFINED = ['copyDockerOutput'];

const FILE_BROWSER_ENTRY_POINTS = [
  'tcOpenFileBrowser', 'taMockupFbOpen', 'ieOpenBrowser', 'ieOpenSavePathBrowser', 'fbOpenBrowser',
  'ltFbOpenBrowserForNewRepo', 'ltFbOpenBrowserForEdit', 'baFbOpenBrowserForNewRepo', 'baFbOpenBrowserForEdit',
  'taFbOpen', 'spRepoFbOpen', 'lgFbOpen', 'raPptxOpenBrowser', 'exportTool', 'toggleImportArea',
  'openGaDashSaveOverlay', 'caOpenSaveOverlay', 'ltOpenSaveOverlay', 'raExport',
  '__promptEditorOpenExportBrowser', '__promptEditorOpenImportBrowser', '__kiChatOpenBrowser',
  'openFileBrowserForNewRepo', 'browseDirectory',
];

describe('inline event handlers reference declared functions', () => {
  const declared = anyDeclaration();
  const handlers = handlerNames();

  it('every on* handler names a function declared somewhere in the shipped sources', () => {
    const missing = [];
    for (const [name, file] of handlers) {
      if (declared.has(name) || PRE_EXISTING_UNDEFINED.includes(name)) continue;
      missing.push(`${name} (${file})`);
    }
    expect(missing).toEqual([]);
  });

  it('documents the pre-existing undefined handler as a known gap', () => {
    PRE_EXISTING_UNDEFINED.forEach((name) => {
      expect(declared.has(name), `${name} is now declared — drop it from the baseline`).toBe(false);
    });
  });
});

describe('file browser entry points are reachable from markup', () => {
  const globals = globalDeclarations();

  FILE_BROWSER_ENTRY_POINTS.forEach((name) => {
    it(`${name} is a global function`, () => {
      expect(globals.has(name), `${name} must be declared at file level or exported via window.`).toBe(true);
    });
  });
});
