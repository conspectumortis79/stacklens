import { describe, it, expect, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { JSDOM } from 'jsdom';
import { extractFunction } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const TPL = resolve(__dirname, '../../main/resources/templates');

function slice(path, from, to) {
  const text = readFileSync(resolve(TPL, path), 'utf8');
  const start = text.indexOf(from);
  const end = to ? text.indexOf(to, start) : text.length;
  if (start < 0 || end < 0) throw new Error(`marker missing in ${path}: ${from}`);
  return text.slice(start, end);
}

const SIDEBAR = 'fragments/layout/scripts-sidebar.html';
const PIPE = 'fragments/ticket-implementation/scripts-agent-pipeline.html';

const HOME = {
  currentPath: '/Users/me',
  parentPath: '/',
  items: [
    { name: 'Desktop', path: '/Users/me/Desktop', type: 'directory' },
    { name: 'Pictures', path: '/Users/me/Pictures', type: 'directory' },
  ],
};
const DESKTOP = {
  currentPath: '/Users/me/Desktop',
  parentPath: '/Users/me',
  items: [
    { name: 'decks', path: '/Users/me/Desktop/decks', type: 'directory' },
    { name: 'mock.png', path: '/Users/me/Desktop/mock.png', type: 'file' },
  ],
};
const MOCKUP_FILE = { base64: 'AAAB', mimeType: 'image/png' };

function boot(byPath) {
  const settle = () => new Promise((r) => setTimeout(r, 0));
  const dom = new JSDOM('<!DOCTYPE html><html data-theme="modern"><body></body></html>', {
    runScripts: 'dangerously',
    url: 'http://localhost/',
  });
  const win = dom.window;
  win.__kiChatEsc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  win.i18n = { t: (key, fallback) => fallback || key };
  win.TA_MOCKUP_FILES = [];
  win.fetch = (url) => {
    if (String(url).includes('mockup-file')) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(MOCKUP_FILE) });
    }
    const requested = decodeURIComponent(String(url).split('path=')[1] || '') || '/Users/me';
    const body = byPath(requested) || HOME;
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(body) });
  };
  win.taMockupRenderChips = () => { };
  win.taMockupUpdateFreitextHint = () => { };
  const pipeline = readFileSync(resolve(TPL, PIPE), 'utf8');
  const script = win.document.createElement('script');
  script.textContent =
    slice(SIDEBAR, 'function __kiChatLoadDirectoryEntries', '</script>') + '\n;\n' +
    slice(PIPE, 'var _MOCKUP_IMAGE_EXTS', 'function taMockupApplySelection') + '\n;\n' +
    extractFunction(pipeline, 'taMockupApplySelection');
  win.document.body.appendChild(script);
  return { win, dom, settle };
}

const flush = () => new Promise((r) => setTimeout(r, 0));

function rows(win) {
  return [...win.document.querySelectorAll('#kiDbList button')].map((b) => b.textContent.trim());
}

function rowByText(win, text) {
  return [...win.document.querySelectorAll('#kiDbList button')].find((b) => b.textContent.includes(text));
}

function firstHint(win) {
  const el = win.document.querySelector('#kiDbList > div');
  return el ? el.textContent.trim() : null;
}

const BY_FOLDER = {
  '/Users/me': HOME,
  '/Users/me/Desktop': DESKTOP,
};

describe('mockup file browser surfaces images and the accepted formats', () => {
  let dom;

  afterEach(() => {
    if (dom) {
      try { dom.close(); } catch (e) { }
      dom = null;
    }
  });

  it('names the accepted formats in the footer', async () => {
    ({ dom } = boot((p) => BY_FOLDER[p]));
    dom.window.taMockupFbOpen();
    await flush();
    const footer = dom.window.document.getElementById('kiDbFooterLabel').textContent;
    expect(footer).toContain('Auswählen / Abwählen');
    expect(footer).toContain('PNG, JPG, JPEG, SVG, HEIC, HEIF, TIFF');
  });

  it('warns about missing images above the folder list, not below it', async () => {
    ({ dom } = boot((p) => BY_FOLDER[p]));
    dom.window.taMockupFbOpen();
    await flush();
    expect(firstHint(dom.window)).toContain('keine passenden Dateien');
    expect(rows(dom.window).length).toBe(3);
  });

  it('lists the image files of a folder that has them', async () => {
    ({ dom } = boot((p) => BY_FOLDER[p]));
    dom.window.taMockupFbOpen();
    await flush();
    rowByText(dom.window, 'Desktop').click();
    await flush();
    const all = rows(dom.window);
    expect(all.some((r) => r.includes('mock.png'))).toBe(true);
    expect(all.some((r) => r.includes('decks'))).toBe(true);
    expect(firstHint(dom.window)).toBeNull();
  });

  it('remembers the last browsed folder between openings', async () => {
    ({ dom } = boot((p) => BY_FOLDER[p]));
    const win = dom.window;
    const opened = [];

    win.taMockupFbOpen();
    await flush();
    opened.push(win.document.getElementById('kiDbPath').value);

    rowByText(win, 'Desktop').click();
    await flush();
    opened.push(win.document.getElementById('kiDbPath').value);

    win.document.getElementById('kiDbCancel').click();
    await flush();
    win.taMockupFbOpen();
    await flush();
    opened.push(win.document.getElementById('kiDbPath').value);

    expect(opened).toEqual(['/Users/me', '/Users/me/Desktop', '/Users/me/Desktop']);
    expect(win.localStorage.getItem('stacklens.fb.last.mockups')).toBe('/Users/me/Desktop');
  });

  it('applies the picked paths through taMockupApplySelection', async () => {
    const started = boot((p) => BY_FOLDER[p]);
    dom = started.dom;
    const win = started.win;
    const calls = [];
    win.fetch = (url) => {
      calls.push(url);
      return Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve(String(url).includes('mockup-file') ? MOCKUP_FILE : BY_FOLDER['/Users/me/Desktop']),
      });
    };
    win.taMockupApplySelection(['/Users/me/Desktop/mock.png']);
    await started.settle();
    await started.settle();
    expect(calls.some((u) => u.includes('mockup-file?path=%2FUsers%2Fme%2FDesktop%2Fmock.png'))).toBe(true);
    expect(win.TA_MOCKUP_FILES.map((f) => f.path)).toEqual(['/Users/me/Desktop/mock.png']);
    expect(win.TA_MOCKUP_FILES[0].name).toBe('mock.png');
  });

  it('drops a previously attached mockup when its path leaves the selection', async () => {
    const started = boot((p) => BY_FOLDER[p]);
    dom = started.dom;
    const win = started.win;
    win.TA_MOCKUP_FILES = [
      { name: 'keep.png', base64: 'AA', mimeType: 'image/png', path: '/Users/me/Desktop/mock.png' },
      { name: 'hand.png', base64: 'BB', mimeType: 'image/png', path: null },
    ];
    win.taMockupApplySelection([]);
    await started.settle();
    expect(win.TA_MOCKUP_FILES.map((f) => f.name)).toEqual(['hand.png']);
  });

  it('hands the picked paths to the dialog callback', async () => {
    const started = boot((p) => BY_FOLDER[p]);
    dom = started.dom;
    const win = started.win;
    let received = null;
    win.__kiChatOpenDirBrowser = (done, options) => {
      received = [options.multi, options.files.length];
      done(['/Users/me/Desktop/mock.png']);
    };
    win.taMockupFbOpen();
    expect(received).toEqual([true, 8]);
  });
});
