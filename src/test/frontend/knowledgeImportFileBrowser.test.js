import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  loadScriptIsolated, installBrowserStubs, resetDom, makeJsonResponse, flushMicrotasks,
} from './_load.js';
import { KNOWLEDGE_MODULE_PATHS, readKnowledgeBundle } from './support/knowledgeModules.js';
import { installSLBrowseFiles } from './support/sharedBrowserHarness.js';

installBrowserStubs();

const here = dirname(fileURLToPath(import.meta.url));
const OVERLAY = readFileSync(
  resolve(here, '../../main/resources/templates/fragments/knowledge/import-overlay.html'),
  'utf8',
);

function stubFetch(responses) {
  const queue = [...(responses || [])];
  const calls = [];
  window.fetch = (url, init = {}) => {
    calls.push({ url, init });
    const next = queue.length > 0 ? queue.shift() : makeJsonResponse([]);
    return Promise.resolve(next);
  };
  return calls;
}

function browseResponse() {
  return makeJsonResponse({
    currentPath: '/srv/knowledge',
    parentPath: '/srv',
    items: [
      { name: 'bundles', path: '/srv/knowledge/bundles', type: 'directory' },
      { name: 'kb.slbundle', path: '/srv/knowledge/kb.slbundle', type: 'file' },
    ],
  });
}

function openBrowser() {
  const open = vi.fn();
  window.__kiChatOpenDirBrowser = open;
  window.kbOpenImport();
  expect(open).toHaveBeenCalledTimes(1);
  return open.mock.calls[0];
}

describe('Wissensdatenbank import shares the one custom file browser', () => {
  beforeEach(() => {
    resetDom();
    document.body.style.overflow = '';
    document.body.innerHTML = OVERLAY;
    window.i18n = { t: (key, fallback) => fallback || key };
    installSLBrowseFiles(window);
    stubFetch([browseResponse()]);
    for (const modulePath of KNOWLEDGE_MODULE_PATHS) {
      loadScriptIsolated(window, modulePath);
    }
  });

  it('kbOpenImport hands the archive filter to __kiChatOpenDirBrowser', () => {
    const [done, options] = openBrowser();

    expect(typeof done).toBe('function');
    expect(options.files).toEqual(['slknowledge', 'slbundle']);
    expect(options.storageKey).toBe('stacklens.fb.last.knowledgeImport');
    expect(options.titleKey).toBe('knowledge.import.title');
    expect(options.acceptHint).toBe('.slknowledge / .slbundle');
    expect(options.fileIcon).toBe('bi-file-earmark-zip');
    expect(typeof options.load).toBe('function');
  });

  it('the loader feeds the shared browser shape from the tooling browse endpoint', async () => {
    const calls = stubFetch([browseResponse()]);
    const [, options] = openBrowser();

    const data = await options.load('/srv/knowledge');

    expect(calls[0].url).toContain('/api/tooling/files/browse');
    expect(calls[0].url).toContain('fileExtensions=slknowledge%2Cslbundle');
    expect(calls[0].url).toContain('path=%2Fsrv%2Fknowledge');
    expect(data.current).toBe('/srv/knowledge');
    expect(data.parent).toBe('/srv');
    expect(data.directories.map((item) => item.name)).toEqual(['bundles']);
    expect(data.files.map((item) => item.name)).toEqual(['kb.slbundle']);
  });

  it('the loader reports a failed browse so the shared browser can show it', async () => {
    stubFetch([makeJsonResponse({ error: 'Zugriff verweigert' })]);
    const [, options] = openBrowser();

    await expect(options.load('')).rejects.toThrow('Zugriff verweigert');
  });

  it('picking an archive opens the import options with the picked path', () => {
    const [done] = openBrowser();

    done('/srv/knowledge/kb.slbundle');

    expect(document.getElementById('kbImportSelectedFileName').textContent).toBe('/srv/knowledge/kb.slbundle');
    expect(document.getElementById('kbImportOverlay').style.display).toBe('flex');
    expect(document.body.style.overflow).toBe('hidden');
    expect(document.getElementById('kbImportConfirmBtn').disabled).toBe(false);
  });

  it('cancelling the browser keeps the import options closed', () => {
    const [done] = openBrowser();

    done('');

    expect(document.getElementById('kbImportOverlay').style.display).toBe('');
    expect(document.body.style.overflow).toBe('');
  });

  it('the confirmed import posts the picked archive, password and mode', async () => {
    const [done] = openBrowser();
    done('/srv/knowledge/kb.slbundle');
    document.getElementById('kbImportPassword').value = 'secret';
    const calls = stubFetch([makeJsonResponse({ documentsImported: 3, chunksImported: 12 })]);

    window.kbConfirmImport();
    await flushMicrotasks();

    const post = calls.find((call) => call.init && call.init.method === 'POST');
    expect(post.url).toBe('/api/knowledge/import-bundle-local');
    expect(JSON.parse(post.init.body)).toEqual({
      path: '/srv/knowledge/kb.slbundle',
      password: 'secret',
      mode: 'NEW_COPY',
    });
  });

  it('the overlay ships no private file-browser markup and knowledge.js no private listing', () => {
    const knowledgeJs = readKnowledgeBundle();

    expect(OVERLAY).not.toContain('kbImportBrowserContent');
    expect(OVERLAY).not.toContain('kbImportScrollTrack');
    expect(OVERLAY).not.toContain('kbImportPath');
    expect(OVERLAY).not.toContain('kb-imp-row');
    expect(knowledgeJs).not.toContain('function kbLoadImportDirectory');
    expect(knowledgeJs).not.toContain('function kbSelectImportFile');
    expect(knowledgeJs).not.toContain('function kbWireCustomScrollbar');
  });
});
