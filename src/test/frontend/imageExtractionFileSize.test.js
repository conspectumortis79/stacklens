import { describe, it, expect, beforeEach } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractFunction, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCRIPTS = resolve(
  __dirname,
  '../../main/resources/templates/fragments/image-extraction/scripts.html',
);

function makeStubs(win) {
  win.__browserSelected = null;
  const elements = {
    ieFileChip: { style: {}, textContent: '' },
    ieFileChipName: { textContent: '' },
    ieFileChipMeta: { textContent: '' },
    ieResultCard: { style: {} },
    ieErrorCard: { style: {} },
    ieErrorMessage: { textContent: '' },
    ieLoading: { classList: { toggle: () => {}, add: () => {}, remove: () => {} } },
    ieLoadingText: { textContent: '' },
    ieDone: { classList: { add: () => {}, remove: () => {} } },
    ieBtnExtract: { disabled: false },
    ieBtnCancel: { style: {} },
    ieDropzone: { classList: { add: () => {}, remove: () => {} } },
    ieBrowserList: { innerHTML: '' },
    ieBrowserPath: { textContent: '' },
    ieBrowserAlert: { textContent: '', style: {} },
    ieBrowserOverlay: { style: {} },
    ieBrowserPreview: { style: {} },
    ieBrowserPreviewName: { textContent: '' },
    ieBrowserConfirmBtn: { disabled: true },
    ieSavePath: { value: '' },
    ieSavePathOverlay: { style: {} },
    ieSavePathBrowserList: { innerHTML: '' },
    ieSavePathBrowserPath: { textContent: '' },
    ieSavePathAlert: { textContent: '', style: {} },
    ieSavePathBrowserScrollTrack: null,
    ieSavePathBrowserScrollThumb: null,
    ieBrowserScrollTrack: null,
    ieBrowserScrollThumb: null,
    ieSavePath: { value: '' },
    ieFileInput: { value: '', files: null },
    ieHistoryCard: null,
    ieHistoryBody: document.createElement('div'),
    ieHistoryTable: { style: { display: 'none' } },
    ieHistoryTbody: document.createElement('tbody'),
    ieHistoryEmpty: { style: { display: 'none' } },
  };
  win.document.getElementById = function (id) {
    return elements[id] || { textContent: '', innerHTML: '', style: {}, classList: { toggle: () => {}, add: () => {}, remove: () => {} }, value: '', files: null };
  };
}

function bootWindow() {
  const win = makeWindow(
    '<div id="ieFileChip" style="display:none">' +
      '<div id="ieFileChipName"></div>' +
      '<div id="ieFileChipMeta"></div>' +
    '</div>' +
    '<div id="ieBrowserPreview" style="display:none">' +
      '<span id="ieBrowserPreviewName"></span>' +
    '</div>' +
    '<div id="ieBrowserList"></div>' +
    '<div id="ieBrowserAlert"></div>' +
    '<div id="ieBrowserOverlay"></div>' +
    '<div id="ieBrowserPath"></div>',
  );
  runInWindow(win, 'window._ie = {};');
  makeStubs(win);
  return win;
}

function loadOnly(win, names) {
  const source = require('node:fs').readFileSync(SCRIPTS, 'utf8');
  let combined = '';
  for (const n of names) combined += '\n' + extractFunction(source, n) + '\n';
  runInWindow(win, combined);
}

describe('Image-Extraction: chip shows real file size for server-file selections', () => {
  let win;
  beforeEach(() => {
    win = bootWindow();
  });

  it('the browser loader caches the size of every listed file', async () => {
    loadOnly(win, ['ieBrowseEntries', 'ieLoadImageEntries']);
    win.IE_IMAGE_EXTS = 'tif,tiff,png,jpg,jpeg';
    win._ieFileSizeByPath = {};
    win.fetch = () => Promise.resolve({
      ok: true,
      json: () => Promise.resolve({
        currentPath: '/tmp',
        parentPath: '/',
        items: [
          { name: 'sub', path: '/tmp/sub', type: 'directory' },
          { name: 'big.png', path: '/tmp/big.png', type: 'file', size: 5_242_880 },
          { name: 'no-size.png', path: '/tmp/no-size.png', type: 'file' },
        ],
      }),
    });
    const entries = await win.ieLoadImageEntries('');
    expect(entries.directories.map((d) => d.name)).toEqual(['sub']);
    expect(entries.files.map((f) => f.name)).toEqual(['big.png', 'no-size.png']);
    expect(win._ieFileSizeByPath['/tmp/big.png']).toBe(5_242_880);
    expect(win._ieFileSizeByPath['/tmp/no-size.png']).toBeUndefined();
  });

  it('the browser loader rejects a failed or erroring response', async () => {
    loadOnly(win, ['ieBrowseEntries', 'ieLoadImageEntries']);
    win.IE_IMAGE_EXTS = 'tif,tiff,png,jpg,jpeg';
    win._ieFileSizeByPath = {};
    win.fetch = () => Promise.resolve({ ok: false, json: () => Promise.resolve({}) });
    await expect(win.ieLoadImageEntries('/tmp')).rejects.toThrow();
  });

  it('ieApplyServerFile uses the cached size (regression: chip showed 0 B)', () => {
    loadOnly(win, ['ieHumanSize', 'detectMimeFromName', 'ieRenderChip', 'ieApplyServerFile']);
    win._ieFileSizeByPath = { '/tmp/photo.jpg': 12_345 };
    win.ieApplyServerFile('/tmp/photo.jpg');
    expect(win._ie.file).toBeNull();
    expect(win._ie.serverPath).toBe('/tmp/photo.jpg');
    expect(win._ie.serverName).toBe('photo.jpg');
    expect(win._ie.fileSize).toBe(12_345);
  });

  it('ieApplyServerFile falls back to 0 when the size is unknown', () => {
    loadOnly(win, ['ieHumanSize', 'detectMimeFromName', 'ieRenderChip', 'ieApplyServerFile']);
    win._ieFileSizeByPath = {};
    win.ieApplyServerFile('/tmp/unknown.png');
    expect(win._ie.fileSize).toBe(0);
    win.ieApplyServerFile('/tmp/unknown.png');
    expect(win._ie.fileSize).toBe(0);
  });

  it('ieRenderChip renders the correct size in human-readable form', () => {
    loadOnly(win, ['ieHumanSize', 'detectMimeFromName', 'ieRenderChip']);
    win._ie.serverPath = '/tmp/big.png';
    win._ie.serverName = 'big.png';
    win._ie.fileSize = 5_242_880;
    win.ieRenderChip();
    expect(win.document.getElementById('ieFileChipMeta').textContent).toBe('image/png · 5.0 MB');
  });

  it('ieRenderChip shows 0 B when no file is selected', () => {
    loadOnly(win, ['ieHumanSize', 'ieRenderChip']);
    win.ieRenderChip();
    expect(win.document.getElementById('ieFileChipMeta').textContent).toBe('');
  });

  it('ieHumanSize formats B / KB / MB / GB correctly', () => {
    loadOnly(win, ['ieHumanSize']);
    expect(win.ieHumanSize(0)).toBe('0 B');
    expect(win.ieHumanSize(null)).toBe('0 B');
    expect(win.ieHumanSize(512)).toBe('512 B');
    expect(win.ieHumanSize(1024)).toBe('1.0 KB');
    expect(win.ieHumanSize(1536)).toBe('1.5 KB');
    expect(win.ieHumanSize(5_242_880)).toBe('5.0 MB');
    expect(win.ieHumanSize(1_073_741_824)).toBe('1.0 GB');
  });
});
