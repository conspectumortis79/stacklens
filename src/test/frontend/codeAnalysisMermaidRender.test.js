import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';


const __dirname = dirname(fileURLToPath(import.meta.url));
const DIAGRAMS = resolve(
  __dirname,
  '../../main/resources/templates/fragments/code-analysis/scripts-diagrams.html',
);

const SIMPLE_MERMAID = 'flowchart LR\n  A --> B\n  B --> C';

function bootWindow() {
  const win = makeWindow(
    '<div id="caMermaidContainer" class="d-none"></div>' +
    '<div id="caMermaidEmpty"></div>' +
    '<div id="caMermaidActions" class="d-none"></div>' +
    '<div id="caMermaidFullContainer"></div>' +
    '<div id="caDrawioContainer"></div>'
  );
  runInWindow(win,
    'var caMermaidStore = null;' +
    'var caDrawioXmlStore = null;' +
    'var caDrawioNormalizedXml = null;' +
    'var caCurrentRepoName = "demo";' +
    'var _caMermaidInitialized = true;' +
    'var _caMermaidSvgCache = null;' +
    'var _caMermaidDragThreshold = 5;' +
    'var _mermaidMouseDownPos = null;'
  );
  win.i18n = { t: (k) => k };

  win.showModalById = function (id) {
    const el = win.document.getElementById(id);
    if (el) el.style.display = 'block';
  };
  win.hideModalById = function (id) {
    const el = win.document.getElementById(id);
    if (el) el.style.display = 'none';
  };

  win.__setCTMLog = [];
  win.__pzCalls = [];

  function makeFakePanZoom(svg) {
    const state = { zoom: 1, x: 0, y: 0 };
    const fitZoom = 2.0;
    const ctms = [];
    let pending = false;

    function recordAndSchedule(t) {
      ctms.push({ ...t });
      win.__setCTMLog.push({ zoom: t.a, x: t.e, y: t.f });
      if (!pending) {
        pending = true;
        Promise.resolve().then(() => { pending = false; });
      }
    }

    const initialCtm = { a: fitZoom, d: fitZoom, e: 0, f: 0 };
    state.zoom = fitZoom;
    ctms.push({ ...initialCtm });
    win.__setCTMLog.push({ zoom: initialCtm.a, x: initialCtm.e, y: initialCtm.f, phase: 'init-sync' });

    const instance = {
      fit() {
        win.__pzCalls.push('fit');
        recordAndSchedule({ a: fitZoom, d: fitZoom, e: 0, f: 0 });
      },
      center() {
        win.__pzCalls.push('center');
        recordAndSchedule({ a: state.zoom, d: state.zoom, e: 0, f: 0 });
      },
      zoomBy(t) {
        win.__pzCalls.push('zoomBy(' + t + ')');
        state.zoom = state.zoom * t;
        recordAndSchedule({ a: state.zoom, d: state.zoom, e: 0, f: 0 });
      },
      destroy() {},
      resize() {},
    };
    return instance;
  }

  win.svgPanZoom = function fakeSvgPanZoom(svg) {
    return makeFakePanZoom(svg);
  };
  win.mermaid = {
    initialize: function () {},
    render: function () {
      return Promise.resolve({
        svg: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 400">' +
             '<rect width="800" height="400" fill="#eef"/></svg>',
      });
    },
  };
  runInWindow(win, extractInlineScripts(DIAGRAMS));
  return win;
}

function nextFrame(win) {
  return new Promise((resolve) => setTimeout(resolve, 100));
}

describe('Code-Analyse: Mermaid inline render must not flicker', () => {
  let win;
  beforeEach(() => {
    win = bootWindow();
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('caRenderMermaid is defined', () => {
    expect(typeof win.caRenderMermaid).toBe('function');
    expect(typeof win.openCaMermaidFullscreen).toBe('function');
  });

  it('inline render does NOT call pz.zoomBy (the flicker-causing call)', async () => {
    win.caMermaidStore = SIMPLE_MERMAID;
    await win.caRenderMermaid(SIMPLE_MERMAID);
    await nextFrame(win);

    expect(win.__pzCalls).not.toContain('fit');
    expect(win.__pzCalls).not.toContain('center');
    expect(win.__pzCalls.filter((c) => c.startsWith('zoomBy'))).toEqual([]);
  });

  it('inline render applies exactly ONE zoom: the FIT zoom (no second-frame jump)', async () => {
    win.caMermaidStore = SIMPLE_MERMAID;
    await win.caRenderMermaid(SIMPLE_MERMAID);
    await nextFrame(win);

    const zooms = win.__setCTMLog.map((e) => e.zoom);
    expect(zooms.length).toBeGreaterThan(0);
    const unique = Array.from(new Set(zooms));
    expect(unique).toEqual([2.0]);
  });

  it('fullscreen modal never called zoomBy either (sanity baseline)', async () => {
    win.caMermaidStore = SIMPLE_MERMAID;
    win.__pzCalls.length = 0;
    win.openCaMermaidFullscreen();
    expect(win.__pzCalls.filter((c) => c.startsWith('zoomBy'))).toEqual([]);
  });
});
