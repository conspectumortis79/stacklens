import { describe, it, expect, beforeEach } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';


const __dirname = dirname(fileURLToPath(import.meta.url));
const DIAGRAMS = resolve(
  __dirname,
  '../../main/resources/templates/fragments/code-analysis/scripts-diagrams.html',
);

const RAW_DEFECTIVE_XML =
  '<mxGraphModel>' +
  '<mxCell id="2" value="Controller" style="rounded=0;fillColor=dae8fc;strokeColor=6c8ebf" vertex="1">' +
  '<mxGeometry x="40" y="40" width="120" height="60"/>' +
  '</mxCell>' +
  '</mxGraphModel>';

function bootWindow() {
  const win = makeWindow('<div id="caDrawioContainer"></div>');
  runInWindow(win, 'var caDrawioXmlStore = null; var caDrawioNormalizedXml = null; var caCurrentRepoName = "demo";');
  win.__opened = [];
  win.desktopApp = { openInBrowser: (u) => win.__opened.push(u) };
  win.i18n = { t: (k) => k };
  runInWindow(win, extractInlineScripts(DIAGRAMS));
  return win;
}

function bootWindowBrowser({ fetchResponse = { ok: false, status: 500 } } = {}) {
  const win = makeWindow('<div id="caDrawioContainer"></div>');
  runInWindow(win, 'var caDrawioXmlStore = null; var caDrawioNormalizedXml = null; var caCurrentRepoName = "demo";');
  win.__opened = [];
  win.fetch = () => Promise.resolve(fetchResponse);
  win.window.open = (u) => { win.__opened.push(u); return null; };
  win.i18n = { t: (k) => k };
  runInWindow(win, extractInlineScripts(DIAGRAMS));
  return win;
}

function decodeOpenedUrl(win) {
  expect(win.__opened.length).toBe(1);
  const url = win.__opened[0];
  expect(url.startsWith('https://app.diagrams.net/#R')).toBe(true);
  return decodeURIComponent(url.slice('https://app.diagrams.net/#R'.length));
}

describe('Code-Analyse: "In draw.io öffnen" sends normalized, loadable XML', () => {
  let win;
  beforeEach(() => {
    win = bootWindow();
  });

  it('exposes the shared normalizer and the open function', () => {
    expect(typeof win.caNormalizeDrawioXml).toBe('function');
    expect(typeof win.caOpenInDrawio).toBe('function');
  });

  it('caNormalizeDrawioXml repairs the raw LLM XML (root, cells 0/1, color #)', () => {
    const out = win.caNormalizeDrawioXml(RAW_DEFECTIVE_XML);
    expect(out).toContain('<root>');
    expect(out).toMatch(/<mxCell\b[^>]*\bid="0"/);
    expect(out).toMatch(/<mxCell\b[^>]*\bid="1"/);
    expect(out).toContain('fillColor=#dae8fc');
    expect(out).toContain('strokeColor=#6c8ebf');
    expect(out).toContain('as="geometry"');
  });

  it('does NOT open draw.io when there is no diagram', () => {
    win.caDrawioXmlStore = null;
    win.caOpenInDrawio();
    expect(win.__opened.length).toBe(0);
  });

  it('sends normalized XML wrapped in <mxfile> — not the raw broken XML', () => {
    win.caDrawioXmlStore = RAW_DEFECTIVE_XML;
    win.caDrawioNormalizedXml = null;
    win.caOpenInDrawio();

    const sent = decodeOpenedUrl(win);
    expect(sent).not.toBe(RAW_DEFECTIVE_XML);
    expect(sent).toMatch(/^<mxfile[\s>]/);
    expect(sent).toContain('<root>');
    expect(sent).toMatch(/<mxCell\b[^>]*\bid="0"/);
    expect(sent).toMatch(/<mxCell\b[^>]*\bid="1"/);
    expect(sent).toContain('fillColor=#dae8fc');
    expect(sent).not.toContain('fillColor=dae8fc');
  });

  it('reuses the cached normalized XML when the Draw.io tab was already opened', () => {
    win.caDrawioXmlStore = RAW_DEFECTIVE_XML;
    win.caDrawioNormalizedXml = win.caNormalizeDrawioXml(RAW_DEFECTIVE_XML);
    win.caOpenInDrawio();

    const sent = decodeOpenedUrl(win);
    expect(sent).toMatch(/^<mxfile[\s>]/);
    expect(sent).toContain('<root>');
    expect(sent).toContain('fillColor=#dae8fc');
  });

  it('does not double-wrap XML that already is an <mxfile> document', () => {
    const alreadyDoc =
      '<mxfile><diagram name="d"><mxGraphModel><root>' +
      '<mxCell id="0"/><mxCell id="1" parent="0"/>' +
      '</root></mxGraphModel></diagram></mxfile>';
    win.caDrawioXmlStore = alreadyDoc;
    win.caDrawioNormalizedXml = alreadyDoc;
    win.caOpenInDrawio();

    const sent = decodeOpenedUrl(win);
    expect((sent.match(/<mxfile/g) || []).length).toBe(1);
  });

  it('browser fallback: when /api/open-url returns 500, opens the URL via window.open', async () => {
    const browserWin = bootWindowBrowser({ fetchResponse: { ok: false, status: 500 } });
    browserWin.caDrawioXmlStore = RAW_DEFECTIVE_XML;
    browserWin.caDrawioNormalizedXml = null;
    browserWin.caOpenInDrawio();
    await Promise.resolve();
    await Promise.resolve();
    expect(browserWin.__opened.length).toBe(1);
    const url = browserWin.__opened[0];
    expect(url.startsWith('https://app.diagrams.net/#R')).toBe(true);
    const payload = decodeURIComponent(url.slice('https://app.diagrams.net/#R'.length));
    expect(payload).toMatch(/^<mxfile[\s>]/);
  });

  it('browser fallback: when /api/open-url returns 200, does NOT open a second tab', async () => {
    const browserWin = bootWindowBrowser({ fetchResponse: { ok: true, status: 200 } });
    browserWin.caDrawioXmlStore = RAW_DEFECTIVE_XML;
    browserWin.caOpenInDrawio();
    await Promise.resolve();
    await Promise.resolve();
    expect(browserWin.__opened.length).toBe(0);
  });

  it('browser fallback: when fetch rejects (network error), still opens via window.open', async () => {
    const browserWin = bootWindowBrowser();
    browserWin.fetch = () => Promise.reject(new Error('network down'));
    browserWin.caDrawioXmlStore = RAW_DEFECTIVE_XML;
    browserWin.caOpenInDrawio();
    await Promise.resolve();
    await Promise.resolve();
    expect(browserWin.__opened.length).toBe(1);
  });

  it('does not load the 3.7 MB viewer when no Draw.io XML exists', async () => {
    await win.caRenderDrawio(null);

    expect(win.GraphViewer).toBeUndefined();
    expect(win.document.querySelector('script[data-stacklens-graph-viewer="true"]')).toBeNull();
  });

  it('lazy-loads GraphViewer once for concurrent callers', async () => {
    const firstLoad = win.caEnsureGraphViewer();
    const secondLoad = win.caEnsureGraphViewer();
    const scripts = win.document.querySelectorAll('script[data-stacklens-graph-viewer="true"]');

    expect(scripts.length).toBe(1);
    win.GraphViewer = function GraphViewer() {};
    scripts[0].onload();

    await expect(firstLoad).resolves.toBe(true);
    await expect(secondLoad).resolves.toBe(true);
    await expect(win.caEnsureGraphViewer()).resolves.toBe(true);
    expect(win.document.querySelectorAll('script[data-stacklens-graph-viewer="true"]').length).toBe(1);
  });

  it('renders after the lazy GraphViewer script has loaded', async () => {
    let renderedContainer = null;
    const renderPromise = win.caRenderDrawio(RAW_DEFECTIVE_XML);
    const script = win.document.querySelector('script[data-stacklens-graph-viewer="true"]');

    expect(script).not.toBeNull();
    function GraphViewer() {}
    GraphViewer.createViewerForElement = (container) => { renderedContainer = container; };
    GraphViewer.processElements = () => {};
    win.GraphViewer = GraphViewer;
    script.onload();
    await renderPromise;

    expect(renderedContainer).not.toBeNull();
    expect(renderedContainer.classList.contains('mxgraph')).toBe(true);
    expect(win.document.querySelector('#caDrawioContainer .mxgraph')).not.toBeNull();
  });

  it('shows a concrete error when the lazy GraphViewer request fails', async () => {
    const renderPromise = win.caRenderDrawio(RAW_DEFECTIVE_XML);
    const script = win.document.querySelector('script[data-stacklens-graph-viewer="true"]');

    script.onerror();
    await renderPromise;

    expect(win.document.getElementById('caDrawioContainer').textContent).toContain('code_analysis.graphviewer_not_loaded');
    expect(win.__caGraphViewerLoadPromise).toBeNull();
  });
});
