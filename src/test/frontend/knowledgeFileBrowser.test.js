import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  loadScriptIsolated, installBrowserStubs, resetDom, makeJsonResponse, flushMicrotasks,
} from './_load.js';
import { staticSource, staticJsSource, installSLBrowseFiles } from './support/sharedBrowserHarness.js';
import { KNOWLEDGE_MODULE_PATHS, readKnowledgeBundle } from './support/knowledgeModules.js';

installBrowserStubs();

function stubFetch(responses) {
  const queue = [...(responses || [])];
  const calls = [];
  window.fetch = (url, init = {}) => {
    calls.push({ url: String(url), init });
    const next = queue.length > 0 ? queue.shift() : makeJsonResponse([]);
    return Promise.resolve(next);
  };
  return calls;
}

function browseResponse() {
  return makeJsonResponse({
    currentPath: '/srv/docs',
    parentPath: '/srv',
    items: [
      { name: 'archive', path: '/srv/docs/archive', type: 'directory' },
      { name: 'runbook.pdf', path: '/srv/docs/runbook.pdf', type: 'file' },
      { name: 'notes.md', path: '/srv/docs/notes.md', type: 'file' },
      { name: 'image.png', path: '/srv/docs/image.png', type: 'file' },
    ],
  });
}

function knowledgeDom() {
  document.body.innerHTML =
    '<div id="kbAddFileModal" class="modal"><div class="modal-header"></div></div>'
    + '<select id="kbFileBase"><option value="7">File Base</option></select>'
    + '<span id="kbFileSelectedName"></span>'
    + '<input type="hidden" id="kbFileCurrentPath" />';
}

function installBootstrapMock() {
  const shown = [];
  window.bootstrap = {
    Modal: function (element) {
      this.element = element;
      this.show = () => { shown.push(element.id); element.classList.add('show'); };
      this.hide = () => element.classList.remove('show');
    },
  };
  return shown;
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

function openBrowser() {
  const open = vi.fn();
  window.__kiChatOpenDirBrowser = open;
  window.kbOpenFilePicker();
  expect(open).toHaveBeenCalledTimes(1);
  return open.mock.calls[0];
}

describe('Wissensdatenbank Datei-Upload teilt den einen custom file browser', () => {
  beforeEach(async () => {
    resetDom();
    knowledgeDom();
    window.i18n = { t: (key, fallback) => fallback || key };
    installSLBrowseFiles(window);
    stubFetch([browseResponse()]);
    for (const modulePath of KNOWLEDGE_MODULE_PATHS) {
      loadScriptIsolated(window, modulePath);
    }
    await flushMicrotasks();
    const noise = document.getElementById('kbToastContainer');
    if (noise) noise.remove();
  });

  it('kbOpenFilePicker übergibt den Dokumentfilter an __kiChatOpenDirBrowser', () => {
    const [done, options] = openBrowser();

    expect(typeof done).toBe('function');
    expect(options.files).toEqual(['pdf', 'doc', 'docx', 'txt', 'md', 'markdown']);
    expect(options.storageKey).toBe('stacklens.fb.last.knowledgeFile');
    expect(options.titleKey).toBe('knowledge.add.file_picker.title');
    expect(options.acceptHint).toBe('PDF, DOC, DOCX, TXT, MD');
    expect(options.fileIcon).toBe('bi-file-earmark-text');
    expect(options.awaitSelection).toBe(true);
    expect(typeof options.load).toBe('function');
  });

  it('der Loader füttert den shared browser aus dem tooling browse endpoint', async () => {
    const calls = stubFetch([browseResponse()]);
    const [, options] = openBrowser();

    const data = await options.load('/srv/docs');

    expect(calls[0].url).toContain('/api/tooling/files/browse');
    expect(calls[0].url).toContain('fileExtensions=pdf%2Cdoc%2Cdocx%2Ctxt%2Cmd%2Cmarkdown');
    expect(calls[0].url).toContain('path=%2Fsrv%2Fdocs');
    expect(data.current).toBe('/srv/docs');
    expect(data.parent).toBe('/srv');
    expect(data.directories.map((item) => item.name)).toEqual(['archive']);
    expect(data.files.map((item) => item.name)).toEqual(['runbook.pdf', 'notes.md', 'image.png']);
  });

  it('der Loader meldet einen fehlgeschlagenen browse an den shared browser', async () => {
    stubFetch([makeJsonResponse({ error: 'Zugriff verweigert' })]);
    const [, options] = openBrowser();

    await expect(options.load('')).rejects.toThrow('Zugriff verweigert');
  });

  it('ein Dateitipp merkt den Pfad und öffnet das Formularmodal', async () => {
    const shown = installBootstrapMock();
    stubFetch([makeJsonResponse([{ id: 7, name: 'File Base' }])]);
    const [done] = openBrowser();

    await done('/srv/docs/runbook.pdf');

    expect(document.getElementById('kbFileCurrentPath').value).toBe('/srv/docs/runbook.pdf');
    expect(document.getElementById('kbFileSelectedName').textContent).toBe('runbook.pdf');
    expect(shown).toEqual(['kbAddFileModal']);
  });

  it('hält das Formular zurück, bis die Wissensdatenbanken geladen sind', async () => {
    const shown = installBootstrapMock();
    const pending = deferred();
    window.fetch = () => pending.promise;
    const [done] = openBrowser();

    const bridge = done('/srv/docs/runbook.pdf');

    expect(typeof bridge.then).toBe('function');
    expect(document.getElementById('kbAddFileModal').classList.contains('show')).toBe(false);
    expect(shown).toEqual([]);

    pending.resolve(makeJsonResponse([{ id: 7, name: 'File Base' }]));
    await bridge;

    expect(shown).toEqual(['kbAddFileModal']);
    expect(document.getElementById('kbAddFileModal').classList.contains('show')).toBe(true);
  });

  it('meldet eine fehlgeschlagene Basisabfrage als Toast und läuft das Promise durch', async () => {
    installBootstrapMock();
    window.fetch = () => Promise.reject(new Error('offline'));
    const [done] = openBrowser();

    await done('/srv/docs/runbook.pdf');

    const toast = document.querySelector('.kb-toast');
    expect(toast).toBeTruthy();
    expect(toast.className).toContain('alert-danger');
    expect(toast.textContent).toContain('offline');
    expect(document.getElementById('kbAddFileModal').classList.contains('show')).toBe(false);
  });

  it('gibt kein Promise zurück, wenn das Formular schon offen steht', () => {
    installBootstrapMock();
    document.getElementById('kbAddFileModal').classList.add('show');
    const [done] = openBrowser();

    expect(done('/srv/docs/notes.md')).toBeUndefined();
    expect(document.getElementById('kbFileCurrentPath').value).toBe('/srv/docs/notes.md');
  });

  it('gibt kein Promise zurück beim Abbrechen', () => {
    installBootstrapMock();
    const [done] = openBrowser();

    expect(done('')).toBeUndefined();
  });

  it('ein erneuter Dateitipp im offenen Formularmodal ersetzt nur die Datei', async () => {
    const shown = installBootstrapMock();
    document.getElementById('kbAddFileModal').classList.add('show');
    const [done] = openBrowser();

    done('/srv/docs/notes.md');
    await flushMicrotasks();

    expect(document.getElementById('kbFileCurrentPath').value).toBe('/srv/docs/notes.md');
    expect(document.getElementById('kbFileSelectedName').textContent).toBe('notes.md');
    expect(shown).toEqual([]);
  });

  it('ein Abbruch ändert weder Auswahl noch Modalzustand', async () => {
    const shown = installBootstrapMock();
    const [done] = openBrowser();

    done('');
    await flushMicrotasks();

    expect(document.getElementById('kbFileCurrentPath').value).toBe('');
    expect(document.getElementById('kbFileSelectedName').textContent).toBe('');
    expect(shown).toEqual([]);
  });

  it('kbOpenAddSource("file") öffnet nur den shared browser', async () => {
    const open = vi.fn();
    window.__kiChatOpenDirBrowser = open;

    await window.kbOpenAddSource('file');
    await flushMicrotasks();

    expect(open).toHaveBeenCalledTimes(1);
    expect(open.mock.calls[0][1].files).toEqual(['pdf', 'doc', 'docx', 'txt', 'md', 'markdown']);
  });

  it('kein privater Datei-Browser mehr im knowledge-Code und in den Templates', () => {
    const knowledgeJs = readKnowledgeBundle();
    const page = staticSource('knowledge.html');
    const modals = staticSource('fragments/knowledge/add-source-modals.html');

    expect(knowledgeJs).toContain('__kiChatOpenDirBrowser(');
    expect(knowledgeJs).toContain('SLBrowseFiles({');
    expect(knowledgeJs).not.toContain('kbFilePickerOverlay');
    expect(knowledgeJs).not.toContain('function kbLoadFilePickerDirectory');
    expect(knowledgeJs).not.toContain('function kbConfirmFilePicker');
    expect(knowledgeJs).not.toContain('function kbSelectFilePickerFile');
    expect(knowledgeJs).not.toContain('/api/tooling/files/browse?path=');
    expect(page).not.toContain('file-picker-overlay');
    expect(modals).toContain('onclick="kbOpenFilePicker()"');
    expect(modals).not.toContain('kbFilePickerOverlay');
  });
});
