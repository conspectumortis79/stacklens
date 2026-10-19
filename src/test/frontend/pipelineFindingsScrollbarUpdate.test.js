import { describe, it, expect, beforeEach } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import {
  extractFragmentScripts,
  extractFunction,
  readJs,
  makeWindow,
  runInWindow,
} from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const APP_JS = resolve(__dirname, '../../main/resources/static/js/app.js');
const PIPELINE = resolve(
  __dirname,
  '../../main/resources/templates/fragments/pipeline-analysis/scripts.html',
);

const RESULT_DOM = `
  <main>
    <div class="pa-result-scroll-wrap">
      <div class="pa-result-scroll-content" id="paResultScrollContent">
        <div class="pa-tab-panel active" id="paTabSummary" data-h="200">short summary</div>
        <div class="pa-tab-panel" id="paTabFindings" data-h="2000">long findings list</div>
      </div>
      <div class="sl-scroll-track" id="paResultScrollTrack"><div class="sl-scroll-thumb" id="paResultScrollThumb"></div></div>
    </div>
    <button class="pa-tab-btn active" onclick="paSwitch('paTabSummary', this)">Summary</button>
    <button class="pa-tab-btn" id="findingsTabBtn" onclick="paSwitch('paTabFindings', this)">Befunde</button>
  </main>
`;

function bootWindow() {
  const win = makeWindow(RESULT_DOM);

  runInWindow(win, 'var paCurrentTab = "paTabSummary"; var paMermaidStore = null; function paSaveState(){} function paRenderMermaid(){}');

  runInWindow(win, extractFunction(readJs(APP_JS), 'SLScroll') + '\nwindow.SLScroll = SLScroll;');

  runInWindow(win, extractFunction(extractFragmentScripts(PIPELINE), 'paSwitch'));
  return win;
}

function nextFrame(win) {
  return new Promise((r) => win.requestAnimationFrame(() => win.requestAnimationFrame(r)));
}

describe('Pipeline-Analyse: Befunde scrollbar updates on tab switch', () => {
  let win;

  beforeEach(() => {
    win = bootWindow();
    const content = win.document.getElementById('paResultScrollContent');

    Object.defineProperty(content, 'clientHeight', { configurable: true, value: 300 });
    Object.defineProperty(content, 'scrollHeight', {
      configurable: true,
      get() {
        const active = content.querySelector('.pa-tab-panel.active');
        return active ? Number(active.getAttribute('data-h')) : 0;
      },
    });
  });

  it('hides the scrollbar while the short Summary tab is active', () => {
    const content = win.document.getElementById('paResultScrollContent');
    const track = win.document.getElementById('paResultScrollTrack');
    content.__slScroll = win.SLScroll(content, track, win.document.getElementById('paResultScrollThumb'));

    expect(track.style.visibility).toBe('hidden');
  });

  it('shows the scrollbar right after switching to Befunde — WITHOUT any scroll event', async () => {
    const content = win.document.getElementById('paResultScrollContent');
    const track = win.document.getElementById('paResultScrollTrack');
    content.__slScroll = win.SLScroll(content, track, win.document.getElementById('paResultScrollThumb'));
    expect(track.style.visibility).toBe('hidden');

    win.paSwitch('paTabFindings', win.document.getElementById('findingsTabBtn'));
    await nextFrame(win);

    expect(win.document.getElementById('paTabFindings').classList.contains('active')).toBe(true);
    expect(track.style.visibility).toBe('');
  });

  it('paSwitch invokes the container SLScroll update() (direct regression guard)', async () => {
    const content = win.document.getElementById('paResultScrollContent');
    let updateCalls = 0;
    content.__slScroll = { update: () => { updateCalls++; } };

    win.paSwitch('paTabFindings', win.document.getElementById('findingsTabBtn'));
    await nextFrame(win);

    expect(updateCalls).toBeGreaterThan(0);
  });

  it('does not throw when no SLScroll is wired yet (defensive guard)', async () => {

    expect(() => win.paSwitch('paTabFindings', win.document.getElementById('findingsTabBtn'))).not.toThrow();
    await nextFrame(win);
  });
});
