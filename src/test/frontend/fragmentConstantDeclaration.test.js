import { describe, it, expect, beforeEach } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractFunction, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const TEMPLATES = resolve(__dirname, '../../main/resources/templates');
const STATIC_DIR = resolve(__dirname, '../../main/resources/static');

function fragmentFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = resolve(dir, entry.name);
    if (entry.isDirectory()) out.push(...fragmentFiles(full));
    else if (entry.name.endsWith('.html')) out.push(full);
  }
  return out;
}

function inlineScripts(path) {
  const html = readFileSync(path, 'utf8');
  const blocks = [];
  for (const m of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
    const attrs = m[1];
    const body = m[2];
    const srcMatch = attrs.match(/\b(?:th:src|src)\s*=\s*"(?:@\{)?\/?(js\/[^"}]+)\}?"/i);
    if (srcMatch) {
      blocks.push(readFileSync(resolve(STATIC_DIR, srcMatch[1]), 'utf8'));
    } else if (body.trim()) {
      blocks.push(body);
    }
  }
  return blocks;
}

const EXTENSION_CONSTANT = /\b([A-Za-z_$][A-Za-z0-9_$]*EXTS)\b/g;

describe('fragment constants are declared in the fragment that uses them', () => {
  const files = fragmentFiles(TEMPLATES);
  const allDeclarations = new Set();

  files.forEach((file) => {
    const code = inlineScripts(file).join('\n;\n');
    for (const m of code.matchAll(/(?:var|let|const)\s+([A-Za-z_$][\w$]*)/g)) allDeclarations.add(m[1]);
    for (const m of code.matchAll(/window\.([A-Za-z_$][\w$]*)\s*=/g)) allDeclarations.add(m[1]);
  });

  it('every *_EXTS constant used by a fragment is declared somewhere in the app', () => {
    const offenders = [];
    files.forEach((file) => {
      const code = inlineScripts(file).join('\n;\n');
      const used = new Set();
      for (const m of code.matchAll(EXTENSION_CONSTANT)) used.add(m[1]);
      used.forEach((name) => {
        if (!allDeclarations.has(name)) offenders.push(`${file.replace(TEMPLATES, '')} → ${name}`);
      });
    });
    expect(offenders).toEqual([]);
  });

  it('the extension constants behind the file browsers are declared exactly once', () => {
    ['TC_FB_EXTS', '_MOCKUP_IMAGE_EXTS', 'IE_IMAGE_EXTS'].forEach((name) => {
      const hits = files.filter((file) => inlineScripts(file).join('\n').includes(`var ${name} =`));
      expect(hits, `${name} must be declared exactly once`).toHaveLength(1);
    });
  });
});

describe('ticket implementation mockup browser', () => {
  let win;

  beforeEach(() => {
    win = makeWindow('<div id="taMockupChips"></div><div id="taMockupFreitextHint"></div>');
    runInWindow(win, 'window.TA_MOCKUP_FILES = []; window.showAlert = function(){}; window.__opened = null;');
  });

  function loadMockupBrowser() {
    const source = readFileSync(
      resolve(TEMPLATES, 'fragments/ticket-implementation/scripts-agent-pipeline.html'),
      'utf8',
    );
    const declaration = source.match(/var _MOCKUP_IMAGE_EXTS\s*=\s*'([^']*)'/);
    if (!declaration) throw new Error('_MOCKUP_IMAGE_EXTS is not declared in scripts-agent-pipeline.html');
    win.__exts = declaration[1];
    runInWindow(win, 'var _MOCKUP_IMAGE_EXTS = window.__exts;');
    runInWindow(win, 'function taMockupRenderChips(){} function taMockupUpdateFreitextHint(){}');
    runInWindow(win, 'window.__kiChatOpenDirBrowser = function(done, options){ window.__opened = options; done([]); };');
    ['taMockupLoadEntries', 'taMockupFbOpen', 'taMockupApplySelection'].forEach((name) => {
      runInWindow(win, extractFunction(source, name));
    });
  }

  it('declares the image extension constant it relies on', () => {
    loadMockupBrowser();
    expect(win._MOCKUP_IMAGE_EXTS).toBe('png,jpg,jpeg,svg,heic,heif,tif,tiff');
  });

  it('opens the shared browser in multi mode without throwing', () => {
    loadMockupBrowser();
    expect(() => win.taMockupFbOpen()).not.toThrow();
    expect(win.__opened).toBeTruthy();
    expect(win.__opened.multi).toBe(true);
    expect(win.__opened.files).toEqual(['png', 'jpg', 'jpeg', 'svg', 'heic', 'heif', 'tif', 'tiff']);
    expect(win.__opened.titleKey).toBe('ticket_implementation.mockup_browse_title');
  });

  it('seeds the dialog with the paths already attached', () => {
    loadMockupBrowser();
    win.TA_MOCKUP_FILES = [{ name: 'a.png', path: '/tmp/a.png' }, { name: 'local.png', path: null }];
    win.taMockupFbOpen();
    expect(win.__opened.selected).toEqual(['/tmp/a.png']);
  });

  it('queries the browse endpoint with the declared extension filter', async () => {
    loadMockupBrowser();
    const seen = [];
    win.fetch = (url) => {
      seen.push(url);
      return Promise.resolve({ ok: true, json: () => Promise.resolve({ currentPath: '/tmp', parentPath: null, items: [] }) });
    };
    await win.taMockupLoadEntries('/tmp');
    expect(seen[0]).toContain('fileExtensions=png,jpg,jpeg,svg,heic,heif,tif,tiff');
    expect(seen[0]).toContain('path=%2Ftmp');
  });
});

describe('ticket creation attachment browser', () => {
  let win;

  beforeEach(() => {
    win = makeWindow('<div id="tcFileChips"></div>');
    runInWindow(win, 'window.state = { files: [] }; window.renderFileChips = function(){};');
  });

  function extractTopLevel(source, name) {
    try {
      return extractFunction(source, name);
    } catch (e) {
      const start = source.indexOf(`window.${name} = function`);
      if (start < 0) throw e;
      let i = source.indexOf('{', start);
      let depth = 0;
      for (; i < source.length; i++) {
        if (source[i] === '{') depth++;
        else if (source[i] === '}' && --depth === 0) {
          return source.slice(start, i + 1) + ';';
        }
      }
      throw new Error('unbalanced braces for ' + name);
    }
  }

  function loadAttachmentBrowser() {
    const source = readFileSync(
      resolve(STATIC_DIR, 'js/fragments/ticket-creation.js'),
      'utf8',
    );
    const declaration = source.match(/var TC_FB_EXTS\s*=\s*'([^']*)'/);
    if (!declaration) throw new Error('TC_FB_EXTS is not declared in ticket-creation fragment scripts');
    win.__exts = declaration[1];
    runInWindow(win, 'var TC_FB_EXTS = window.__exts;');
    runInWindow(win, 'window.__kiChatOpenDirBrowser = function(done, options){ window.__opened = options; window.__done = done; };');
    runInWindow(win, 'function showError(){}');
    ['tcIsBinaryFile', 'tcResizeImage', 'tcAddFile', 'tcRemoveFilePath', 'tcLoadFileContent', 'tcOpenFileBrowser', 'tcSyncAttachmentSelection', 'tcLoadAttachmentEntries'].forEach((name) => {
      runInWindow(win, extractTopLevel(source, name));
    });
  }

  it('declares the attachment extension constant it relies on', () => {
    loadAttachmentBrowser();
    expect(win.TC_FB_EXTS).toContain('gradle');
    expect(win.TC_FB_EXTS.split(',').length).toBeGreaterThan(20);
  });

  it('opens the shared browser in multi mode without throwing', () => {
    loadAttachmentBrowser();
    expect(() => win.tcOpenFileBrowser()).not.toThrow();
    expect(win.__opened.multi).toBe(true);
    expect(win.__opened.files.slice(0, 3)).toEqual(['png', 'jpg', 'jpeg']);
    expect(win.__opened.titleKey).toBe('ticket_creation.form_files_label');
  });

  it('drops attachments whose path left the selection', () => {
    loadAttachmentBrowser();
    win.state.files = [
      { name: 'keep.md', content: 'x', path: '/tmp/keep.md' },
      { name: 'gone.md', content: 'y', path: '/tmp/gone.md' },
      { name: 'local.md', content: 'z', path: null },
    ];
    win.tcSyncAttachmentSelection(['/tmp/keep.md']);
    expect(win.state.files.map((f) => f.name)).toEqual(['keep.md', 'local.md']);
  });

  it('classifies binary attachments through the real helper', () => {
    loadAttachmentBrowser();
    expect(win.tcIsBinaryFile('photo.PNG')).toBe(true);
    expect(win.tcIsBinaryFile('deck.pptx')).toBe(false);
    expect(win.tcIsBinaryFile('report.pdf')).toBe(true);
  });

  it('loads text attachment content through the real helper chain', async () => {
    loadAttachmentBrowser();
    win.fetch = () => Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ content: 'file body' }),
    });
    await win.tcLoadFileContent('/tmp/notes.md', 'notes.md');
    expect(win.state.files).toEqual([{ name: 'notes.md', content: 'file body', path: '/tmp/notes.md' }]);
  });

  it('attaches content through the onToggle contract of the dialog', async () => {
    loadAttachmentBrowser();
    win.fetch = () => Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ content: 'inline' }),
    });
    win.tcOpenFileBrowser();

    win.__opened.onToggle('/tmp/a.md', 'a.md', true);
    await new Promise((r) => setTimeout(r, 0));
    expect(win.state.files.map((f) => f.name)).toEqual(['a.md']);

    win.__done(['/tmp/a.md']);
    expect(win.state.files.map((f) => f.name)).toEqual(['a.md']);

    win.__opened.onToggle('/tmp/a.md', 'a.md', false);
    expect(win.state.files).toEqual([]);
  });
});
