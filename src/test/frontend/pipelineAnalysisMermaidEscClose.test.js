import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractFragmentScripts, extractFunction, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PIPELINE = resolve(
  __dirname,
  '../../main/resources/templates/fragments/pipeline-analysis/scripts.html',
);

const DOM = `
  <div id="paMermaidContainer"><svg id="paInlineSvg"></svg></div>
  <div id="paMermaidFullscreen" class="pa-mermaid-fullscreen">
    <div class="pa-mermaid-fullscreen-inner">
      <div id="paMermaidFullContainer" class="pa-mermaid-fullscreen-content"></div>
    </div>
  </div>
  <div class="modal fade" id="paMrModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog"><div class="modal-content"></div></div>
  </div>
`;

function bootWindow() {
  const win = makeWindow(DOM);
  win.i18n = { t: (key) => key };
  const source = extractFragmentScripts(PIPELINE);
  runInWindow(
    win,
    'var paMermaidStore = null;' +
      'function paT(key) { return key; }' +
      'function paSetupMermaidPanZoom() { return {}; }',
  );
  runInWindow(win, source.slice(source.indexOf('var _paEscStack'), source.indexOf('function paShowModal')));
  ['paShowModal', 'paHideModal', 'paOpenMermaidFullscreen', 'paCloseMermaidFullscreen']
    .forEach((name) => runInWindow(win, extractFunction(source, name)));
  win.paMermaidStore = 'flowchart LR\n  A --> B';
  return win;
}

function pressEscape(win, key = 'Escape') {
  win.document.dispatchEvent(new win.KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
}

function isFullscreenOpen(win) {
  const el = win.document.getElementById('paMermaidFullscreen');
  return !!el && el.classList.contains('open');
}

function isMrModalOpen(win) {
  return win.document.getElementById('paMrModal').classList.contains('show');
}

describe('Pipeline-Analyse: Escape closes the fullscreen mermaid window', () => {
  let win;

  beforeEach(() => {
    win = bootWindow();
  });

  afterEach(() => {
    win.close();
  });

  it('registers the escape binding when the fullscreen window opens', () => {
    expect(isFullscreenOpen(win)).toBe(false);
    win.paOpenMermaidFullscreen();
    expect(isFullscreenOpen(win)).toBe(true);
    expect(win._paEscStack.map((entry) => entry.elementId)).toEqual(['paMermaidFullscreen']);
  });

  it('closes the fullscreen window on Escape', () => {
    win.paOpenMermaidFullscreen();
    pressEscape(win);
    expect(isFullscreenOpen(win)).toBe(false);
    expect(win.document.getElementById('paMermaidFullContainer').innerHTML).toBe('');
    expect(win._paEscStack).toHaveLength(0);
  });

  it('closes the fullscreen window on the legacy Esc key value', () => {
    win.paOpenMermaidFullscreen();
    pressEscape(win, 'Esc');
    expect(isFullscreenOpen(win)).toBe(false);
  });

  it('leaves the inline diagram untouched when no fullscreen window is open', () => {
    pressEscape(win);
    expect(isFullscreenOpen(win)).toBe(false);
    expect(win.document.getElementById('paMermaidContainer').querySelector('svg')).not.toBeNull();
    expect(isMrModalOpen(win)).toBe(false);
  });

  it('releases the keydown binding after the window was closed by the close button', () => {
    win.paOpenMermaidFullscreen();
    win.paCloseMermaidFullscreen();
    expect(win._paEscStack).toHaveLength(0);
    win.paOpenMermaidFullscreen();
    expect(win._paEscStack.map((entry) => entry.elementId)).toEqual(['paMermaidFullscreen']);
    pressEscape(win);
    expect(isFullscreenOpen(win)).toBe(false);
  });

  it('drops bindings whose element left the DOM after an SPA remount', () => {
    win.paShowModal('paMrModal');
    win.paOpenMermaidFullscreen();
    win.document.getElementById('paMermaidFullscreen').remove();
    pressEscape(win);
    expect(isMrModalOpen(win)).toBe(false);
    expect(win._paEscStack).toHaveLength(0);
  });

  it('keeps the modal escape handling and closes only the topmost layer per press', () => {
    win.paShowModal('paMrModal');
    win.paOpenMermaidFullscreen();
    pressEscape(win);
    expect(isFullscreenOpen(win)).toBe(false);
    expect(isMrModalOpen(win)).toBe(true);
    pressEscape(win);
    expect(isMrModalOpen(win)).toBe(false);
    expect(win._paEscStack).toHaveLength(0);
  });

  it('closes a modal opened after the fullscreen window first', () => {
    win.paOpenMermaidFullscreen();
    win.paShowModal('paMrModal');
    pressEscape(win);
    expect(isMrModalOpen(win)).toBe(false);
    expect(isFullscreenOpen(win)).toBe(true);
    pressEscape(win);
    expect(isFullscreenOpen(win)).toBe(false);
  });

  it('does not treat other keys as a close request', () => {
    win.paOpenMermaidFullscreen();
    win.document.dispatchEvent(new win.KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    expect(isFullscreenOpen(win)).toBe(true);
  });
});
