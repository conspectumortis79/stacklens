import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  loadScriptIsolated, installBrowserStubs, resetDom,
  flushMicrotasks, makeJsonResponse, makeErrorResponse,
  untrackDocumentListeners, flushUntil
} from './_load.js';
import { KNOWLEDGE_MODULE_PATHS } from './support/knowledgeModules.js';

function loadKnowledgeScripts() {
  for (const modulePath of KNOWLEDGE_MODULE_PATHS) {
    loadScriptIsolated(window, modulePath);
  }
}

installBrowserStubs();

function setFetch(responses) {
  const calls = [];
  const queue = Array.isArray(responses) ? [...responses] : [responses];
  window.fetch = (url, init = {}) => {
    const entry = { url, init };
    calls.push(entry);
    const response = queue.length > 0 ? queue.shift() : makeJsonResponse({});
    return Promise.resolve(response);
  };
  return calls;
}

function standardI18n() {
  window._i18n = window._i18n || {};
  window.t = window.t || ((key) => key);
  window.translateError = window.translateError || ((err) => (err && err.message) || String(err));
  if (window.localStorage && !window.localStorage.getItem('sl.lang')) {
    window.localStorage.setItem('sl.lang', 'de');
  }
}

function installBootstrapMock() {
  if (!window.bootstrap) window.bootstrap = {};
  const fakeModal = function (element) {
    this.element = element;
    this.show = () => {
      if (element) element.style.display = 'block';
      return this;
    };
    this.hide = () => {
      if (element) element.style.display = 'none';
      return this;
    };
  };
  window.bootstrap.Modal = window.bootstrap.Modal || fakeModal;
  window.bootstrap.Offcanvas = window.bootstrap.Offcanvas || fakeModal;
}

function setInitialFetch() {
  setFetch([
    makeJsonResponse([]),
    makeJsonResponse([]),
    makeJsonResponse({ provider: 'X', model: 'Y' })
  ]);
}

describe('knowledge.js — tab switching and source-submission forms', () => {
  beforeEach(() => {
    resetDom();
    standardI18n();
    setInitialFetch();
    document.body.innerHTML =
      '<div id="kbMetrics"></div>'
      + '<ul id="kbTabs" class="nav">'
      + '<li class="nav-item"><button id="btnOverview" class="nav-link active"></button></li>'
      + '<li class="nav-item"><button id="btnSources" class="nav-link"></button></li>'
      + '<li class="nav-item"><button id="btnConfig" class="nav-link"></button></li>'
      + '<li class="nav-item"><button id="kbCreateBaseBtn" class="nav-link"></button></li>'
      + '</ul>'
      + '<div class="kb-tab-pane" data-kb-pane="overview"></div>'
      + '<div class="kb-tab-pane" data-kb-pane="sources" class="d-none"></div>'
      + '<table><tbody id="kbSourceRows"></tbody></table>'
      + '<span id="kbSourceCount"></span>'
      + '<div class="kb-tab-pane" data-kb-pane="bases" class="d-none"></div>'
      + '<form id="kbCreateBaseForm"></form>'
      + '<input id="kbCreateBaseName" />'
      + '<table><tbody id="kbBaseRows"></tbody></table>'
      + '<span id="kbBaseCount"></span>'
      + '<div class="kb-tab-pane" data-kb-pane="config" class="d-none"></div>'
      + '<div id="kbAddManualModal" class="modal"><div class="modal-header"></div></div>'
      + '<select id="kbManualBase"><option value="1">Base 1</option><option value="2">Base 2</option></select>'
      + '<input id="kbManualName" />'
      + '<textarea id="kbManualText"></textarea>'
      + '<select id="kbFileBase"><option value="5">File Base</option></select>'
      + '<input id="kbFileCurrentPath" />'
      + '<span id="kbFileSelectedName"></span>'
      + '<div id="kbAddFileModal" class="modal"><div class="modal-header"></div></div>'
      + '<select id="kbConfluenceBase"><option value="1">Base</option></select>'
      + '<select id="kbConfluenceConfig"><option value="2">Config</option></select>'
      + '<input id="kbConfluenceUrl" />'
      + '<input type="checkbox" id="kbConfluenceChildren" />'
      + '<input id="kbConfluenceDepth" value="3" />'
      + '<div id="kbAddConfluenceModal" class="modal"><div class="modal-header"></div></div>'
      + '<select id="kbJiraBase"><option value="1">Base</option></select>'
      + '<select id="kbJiraConfig"><option value="2">Config</option></select>'
      + '<input id="kbJiraKey" />'
      + '<input type="checkbox" id="kbJiraChildren" checked />'
      + '<input type="checkbox" id="kbJiraComments" />'
      + '<input id="kbJiraDepth" value="3" />'
      + '<div id="kbAddJiraModal" class="modal"><div class="modal-header"></div></div>';
    loadKnowledgeScripts();
  });

  it('shows only the requested pane and hides the others', () => {
    window.kbSwitchTab('sources', document.getElementById('btnSources'));
    const overview = document.querySelector('[data-kb-pane="overview"]');
    const sources = document.querySelector('[data-kb-pane="sources"]');
    expect(overview.classList.contains('d-none')).toBe(true);
    expect(sources.classList.contains('d-none')).toBe(false);
  });

  it('marks the clicked button as active and the others as inactive', () => {
    const btn = document.getElementById('btnConfig');
    window.kbSwitchTab('config', btn);
    expect(btn.classList.contains('active')).toBe(true);
    expect(document.getElementById('btnOverview').classList.contains('active')).toBe(false);
  });

  it('triggers the matching loader for the sources tab', async () => {
    setFetch([
      makeJsonResponse([]),
      makeJsonResponse([])
    ]);
    window.kbSwitchTab('sources', document.getElementById('btnSources'));
    await flushMicrotasks();
    expect(document.getElementById('kbSourceCount').textContent).toBe('0');
  });

  it('triggers the matching loader for the bases tab', async () => {
    setFetch([
      makeJsonResponse([]),
      makeJsonResponse([])
    ]);
    window.kbSwitchTab('bases', document.getElementById('btnBases'));
    await flushMicrotasks();
    expect(document.getElementById('kbBaseCount').textContent).toBe('0');
  });

  it('kbOpenAddSource("manual") populates the manual base dropdown and shows the manual modal', async () => {
    installBootstrapMock();
    await window.kbOpenAddSource('manual');
    await flushMicrotasks();
    expect(document.querySelectorAll('#kbManualBase option').length).toBeGreaterThan(0);
  });

  it('kbOpenAddSource("file") opens the shared file browser instead of the form modal', async () => {
    const open = vi.fn();
    window.__kiChatOpenDirBrowser = open;
    await window.kbOpenAddSource('file');
    await flushMicrotasks();
    expect(open).toHaveBeenCalledTimes(1);
    expect(document.getElementById('kbAddFileModal').classList.contains('show')).toBe(false);
  });

  it('kbOpenAddSource("confluence") populates both base and config and shows the confluence modal', async () => {
    await window.kbOpenAddSource('confluence');
    await flushMicrotasks();
  });

  it('kbOpenAddSource("jira") populates both base and config and shows the jira modal', async () => {
    await window.kbOpenAddSource('jira');
    await flushMicrotasks();
  });

  it('kbSubmitManual POSTs the manual source body and reloads sources', async () => {
    installBootstrapMock();
    document.getElementById('kbManualBase').value = '1';
    document.getElementById('kbManualName').value = 'My Source';
    document.getElementById('kbManualText').value = 'Hello world';
    const calls = setFetch([makeJsonResponse({}), makeJsonResponse([])]);
    window.kbSubmitManual();
    await flushMicrotasks();
    expect(calls[0].url).toBe('/api/knowledge/sources/manual');
    expect(calls[0].init.method).toBe('POST');
    const body = JSON.parse(calls[0].init.body);
    expect(body.knowledgeBaseId).toBe(1);
    expect(body.name).toBe('My Source');
    expect(body.content).toBe('Hello world');
  });

  it('kbSubmitFile warns when no path is set', () => {
    document.getElementById('kbFileCurrentPath').value = '';
    window.kbSubmitFile();
  });

  it('kbSubmitFile POSTs the file path and base to /api/knowledge/sources/file-local', async () => {
    installBootstrapMock();
    document.getElementById('kbFileCurrentPath').value = '/docs/a.pdf';
    document.getElementById('kbFileBase').value = '5';
    const calls = setFetch([makeJsonResponse({}), makeJsonResponse([])]);
    window.kbSubmitFile();
    await flushMicrotasks();
    expect(calls[0].url).toBe('/api/knowledge/sources/file-local');
    const body = JSON.parse(calls[0].init.body);
    expect(body.path).toBe('/docs/a.pdf');
    expect(body.knowledgeBaseId).toBe(5);
  });

  it('kbSubmitConfluence POSTs the proper body', async () => {
    document.getElementById('kbConfluenceBase').value = '1';
    document.getElementById('kbConfluenceConfig').value = '2';
    document.getElementById('kbConfluenceUrl').value = 'https://x/page';
    document.getElementById('kbConfluenceChildren').checked = true;
    document.getElementById('kbConfluenceDepth').value = '5';
    const calls = setFetch([makeJsonResponse({}), makeJsonResponse([])]);
    window.kbSubmitConfluence();
    await flushMicrotasks();
    expect(calls[0].url).toBe('/api/knowledge/sources/confluence');
    const body = JSON.parse(calls[0].init.body);
    expect(body.includeChildren).toBe(true);
    expect(body.maxDepth).toBe(5);
  });

  it('kbSubmitJira POSTs the proper body with both flag booleans', async () => {
    document.getElementById('kbJiraBase').value = '1';
    document.getElementById('kbJiraConfig').value = '2';
    document.getElementById('kbJiraKey').value = 'PROJ-1';
    document.getElementById('kbJiraChildren').checked = true;
    document.getElementById('kbJiraComments').checked = false;
    document.getElementById('kbJiraDepth').value = '3';
    const calls = setFetch([makeJsonResponse({}), makeJsonResponse([])]);
    window.kbSubmitJira();
    await flushMicrotasks();
    expect(calls[0].url).toBe('/api/knowledge/sources/jira');
    const body = JSON.parse(calls[0].init.body);
    expect(body.includeChildren).toBe(true);
    expect(body.includeComments).toBe(false);
    expect(body.maxDepth).toBe(3);
    expect(body.rootTicketKey).toBe('PROJ-1');
  });
});

describe('knowledge.js — reset tab', () => {
  beforeEach(() => {
    resetDom();
    standardI18n();
    document.body.innerHTML =
      '<div id="kbResetConfirmBox" style="display:none"></div>'
      + '<button id="kbResetBtn" style="display:inline-flex"></button>'
      + '<button id="kbResetConfirmBtn"></button>'
      + '<span id="kbMetricBases"></span><span id="kbMetricSources"></span>'
      + '<div id="kbMetricProvider"></div><div id="kbMetricModel"></div>'
      + '<span id="kbSourceCount"></span><table><tbody id="kbSourceRows"></tbody></table>';
    setInitialFetch();

    loadKnowledgeScripts();
  });

  it('kbInitReset shows the confirm box and hides the trigger button', () => {
    window.kbInitReset();
    expect(document.getElementById('kbResetConfirmBox').style.display).toBe('block');
    expect(document.getElementById('kbResetBtn').style.display).toBe('none');
  });

  it('kbCancelReset hides the confirm box and re-shows the trigger button', () => {
    document.getElementById('kbResetConfirmBox').style.display = 'block';
    document.getElementById('kbResetBtn').style.display = 'none';
    window.kbCancelReset();
    expect(document.getElementById('kbResetConfirmBox').style.display).toBe('none');
    expect(document.getElementById('kbResetBtn').style.display).toBe('inline-flex');
  });

  it('kbConfirmReset POSTs to /api/knowledge/reset and reloads when the response is OK', async () => {
    const calls = setFetch([
      makeJsonResponse({}),
      makeJsonResponse([]), makeJsonResponse([])
    ]);
    window.kbConfirmReset();
    await flushMicrotasks();
    expect(calls[0].url).toBe('/api/knowledge/reset');
    expect(calls[0].init.method).toBe('POST');
  });

  it('kbConfirmReset surfaces a danger toast on error and re-enables the button', async () => {
    setFetch([makeErrorResponse(500, 'boom')]);
    window.kbConfirmReset();
    await flushMicrotasks();
    expect(document.getElementById('kbResetConfirmBtn').disabled).toBe(false);
  });
});

describe('knowledge.js — reindex', () => {
  beforeEach(() => {
    resetDom();
    standardI18n();
    document.body.innerHTML = '<div id="kbReindexResult"></div>';
    setInitialFetch();

    loadKnowledgeScripts();
  });

  it('renders the running indicator and a 1-second heartbeat when the POST is in flight', async () => {
    vi.useFakeTimers();
    let release;
    globalThis.fetch = vi.fn(() => new Promise((r) => { release = r; }));
    window.kbReindexAll();
    expect(document.getElementById('kbReindexResult').innerHTML).toMatch(/Reindexierung/);
    vi.advanceTimersByTime(2000);
    const elapsed = document.querySelector('.kb-reindex-elapsed').textContent;
    expect(elapsed).toMatch(/\([0-9]+ s\)/);
    release({ ok: true, status: 200, statusText: 'x', json: () => Promise.resolve({ baseCount: 2, totalChunks: 5 }), text: () => Promise.resolve('') });
    await flushMicrotasks();
  });

  it('renders the success alert with the elapsed time when the server returns ok', async () => {
    vi.useFakeTimers();
    let release;
    globalThis.fetch = vi.fn(() => new Promise((r) => { release = r; }));
    window.kbReindexAll();
    vi.advanceTimersByTime(2000);
    release({ ok: true, status: 200, json: () => Promise.resolve({ baseCount: 4, totalChunks: 12 }), text: () => Promise.resolve('') });
    await flushMicrotasks();
    const html = document.getElementById('kbReindexResult').innerHTML;
    expect(html).toContain('alert-success');
    expect(html).toContain('4');
    expect(html).toContain('12');
  });

  it('renders the danger alert when the POST throws', async () => {
    globalThis.fetch = vi.fn(() => Promise.reject(new Error('boom')));
    window.kbReindexAll();
    await flushMicrotasks();
    expect(document.getElementById('kbReindexResult').innerHTML).toContain('alert-danger');
    expect(document.getElementById('kbReindexResult').innerHTML).toContain('boom');
  });
});

describe('knowledge.js — hint toast and SPA delegation', () => {
  beforeEach(() => { resetDom(); standardI18n(); setInitialFetch();
 loadKnowledgeScripts(); });

  it('kbShowReindexHintOnce shows the toast once and is a no-op the second time', () => {
    vi.useFakeTimers();
    localStorage.removeItem('stacklens.rag.knn-reindex-hint-shown');
    history.pushState({}, '', '/knowledge');
    delete window.__kbActionDelegationWired;
    window.kbShowReindexHintOnce();
    vi.advanceTimersByTime(1600);
    expect(document.querySelector('#kbToastContainer .kb-toast')).not.toBeNull();
    document.body.innerHTML = '';
    window.kbShowReindexHintOnce();
    vi.advanceTimersByTime(1600);
    expect(document.querySelector('#kbToastContainer .kb-toast')).toBeNull();
  });

  it('kbShowReindexHintOnce is a no-op when the localStorage flag is already set', () => {
    vi.useFakeTimers();
    localStorage.setItem('stacklens.rag.knn-reindex-hint-shown', '1');
    window.kbShowReindexHintOnce();
    vi.advanceTimersByTime(2000);
    expect(document.querySelector('#kbToastContainer .kb-toast')).toBeNull();
  });

  it('showKbToast wraps kbNotify with the longer 12s hint duration', () => {
    document.body.innerHTML = '';
    window.showKbToast('hint');
    expect(document.querySelector('#kbToastContainer .kb-toast')).not.toBeNull();
  });

  it('data-kb-action="kbSwitchTab" wires automatic click delegation', () => {
    document.body.innerHTML =
      '<div class="kb-tab-pane" data-kb-pane="x"></div>'
      + '<div id="kbTabs"><button id="trigger" data-kb-action="kbSwitchTab" class="nav-link"></button></div>';
    document.getElementById('trigger').click();
  });

  it('data-kb-action with a colon separator passes the suffix as the argument', () => {
    let received;
    window.__testHandler = (arg) => { received = arg; };
    document.body.innerHTML = '<button id="trigger" data-kb-action="__testHandler:hello"></button>';
    document.getElementById('trigger').click();
    expect(received).toBe('hello');
  });

  it('data-kb-action is a no-op when the handler does not exist (console warning)', () => {
    const errSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    document.body.innerHTML = '<button id="trigger" data-kb-action="__doesNotExist"></button>';
    document.getElementById('trigger').click();
    expect(errSpy).toHaveBeenCalled();
    errSpy.mockRestore();
  });

  it('data-kb-action guards a thrown handler so a single bug does not break sibling elements', () => {
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    window.__throwHandler = () => { throw new Error('boom'); };
    document.body.innerHTML = '<button id="trigger" data-kb-action="__throwHandler"></button>';
    document.getElementById('trigger').click();
    expect(errSpy).toHaveBeenCalled();
    errSpy.mockRestore();
  });

  it('does not double-wire the same element across script re-evaluations', () => {
    delete window.__kbActionDelegationWired;
    setInitialFetch();

    loadKnowledgeScripts();
    let called = 0;
    window.__incHandler = () => { called++; };
    document.body.innerHTML = '<button id="trigger" data-kb-action="__incHandler"></button>';
    document.getElementById('trigger').click();
    document.getElementById('trigger').click();
    expect(called).toBe(1);
  });

  it('boots kbLoadOverview synchronously when document.readyState !== "loading"', () => {
    setFetch([
      makeJsonResponse([{ id: 1 }]), makeJsonResponse([]), makeJsonResponse({})
    ]);
    document.body.innerHTML =
      '<div id="kbMetricBases"></div><div id="kbMetricSources"></div>'
      + '<div id="kbMetricProvider"></div><div id="kbMetricModel"></div>';
    setInitialFetch();

    loadKnowledgeScripts();
    expect(document.getElementById('kbMetricBases').textContent).toBe('');
  });
});
