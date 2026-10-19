import { describe, it, expect, beforeEach } from 'vitest';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import {
  extractInlineScripts,
  extractFunction,
  readJs,
  makeWindow,
  runInWindow,
} from './support/fragmentLoader.js';


const __dirname = dirname(fileURLToPath(import.meta.url));
const APP_JS = resolve(__dirname, '../../main/resources/static/js/app.js');
const FIX_SCRIPTS = resolve(
  __dirname,
  '../../main/resources/templates/fragments/logging/scripts-fix.html',
);

const FIX_DOM = `
  <div id="lgFixPanel">
    <div class="lg-fix-stage" data-stage="diagnose">
      <div class="lg-fix-stage-head lg-fix-stage-pending" onclick="lgToggleFixStage(this)">
        <span class="lg-fix-stage-badge"></span>
        <i class="lg-fix-stage-icon"></i>
      </div>
      <div class="lg-fix-stage-body-wrap">
        <div class="lg-fix-stage-body"></div>
        <div class="sl-scroll-track lg-fix-stage-body-track"><div class="sl-scroll-thumb" id="lgFixStageDiagnoseThumb"></div></div>
      </div>
    </div>
    <div class="lg-fix-stage" data-stage="plan">
      <div class="lg-fix-stage-head lg-fix-stage-pending" onclick="lgToggleFixStage(this)">
        <span class="lg-fix-stage-badge"></span>
        <i class="lg-fix-stage-icon"></i>
      </div>
      <div class="lg-fix-stage-body-wrap">
        <div class="lg-fix-stage-body"></div>
        <div class="sl-scroll-track lg-fix-stage-body-track"><div class="sl-scroll-thumb" id="lgFixStagePlanThumb"></div></div>
      </div>
    </div>
    <div class="lg-fix-stage" data-stage="code">
      <div class="lg-fix-stage-head lg-fix-stage-pending" onclick="lgToggleFixStage(this)">
        <span class="lg-fix-stage-badge"></span>
        <i class="lg-fix-stage-icon"></i>
      </div>
      <div class="lg-fix-stage-body-wrap">
        <div class="lg-fix-stage-body"></div>
        <div class="sl-scroll-track lg-fix-stage-body-track"><div class="sl-scroll-thumb" id="lgFixStageCodeThumb"></div></div>
      </div>
    </div>
    <div class="lg-fix-stage" data-stage="tests">
      <div class="lg-fix-stage-head lg-fix-stage-pending" onclick="lgToggleFixStage(this)">
        <span class="lg-fix-stage-badge"></span>
        <i class="lg-fix-stage-icon"></i>
      </div>
      <div class="lg-fix-stage-body-wrap">
        <div class="lg-fix-stage-body"></div>
        <div class="sl-scroll-track lg-fix-stage-body-track"><div class="sl-scroll-thumb" id="lgFixStageTestsThumb"></div></div>
      </div>
    </div>
  </div>
`;

function bootWindow() {
  const win = makeWindow(FIX_DOM);
  win.i18n = { t: function (k) { return k; } };
  runInWindow(win, 'function lgUpdateFixButtonState(){}');
  runInWindow(win, extractFunction(readJs(APP_JS), 'SLScroll') + '\nwindow.SLScroll = SLScroll;');
  runInWindow(win, extractInlineScripts(FIX_SCRIPTS));
  return win;
}

function nextFrame(win) {
  return new Promise((r) => win.requestAnimationFrame(() => win.requestAnimationFrame(r)));
}

function installFakeLayout(win) {
  const stages = ['diagnose', 'plan', 'code', 'tests'];
  stages.forEach(function (s) {
    const body = win.document.querySelector('.lg-fix-stage[data-stage="' + s + '"] .lg-fix-stage-body');
    const track = win.document.querySelector('.lg-fix-stage[data-stage="' + s + '"] .lg-fix-stage-body-track');
    Object.defineProperty(track, 'clientHeight', { configurable: true, value: 200 });
    Object.defineProperty(body, 'clientHeight', { configurable: true, value: 300 });
    let _h = 0;
    Object.defineProperty(body, 'scrollHeight', {
      configurable: true,
      get: function () { return _h; },
      set: function (v) { _h = v; },
    });
    Object.defineProperty(body, '_setScrollHeight', {
      configurable: true,
      value: function (v) { _h = v; },
    });
  });
}

describe('KI-Fix-Pipeline: each stage hosts the custom SLScroll scrollbar', () => {
  let win;

  beforeEach(() => {
    win = bootWindow();
    installFakeLayout(win);
  });

  it('wires SLScroll into all four stage bodies on init', () => {
    win.lgInitFixStageScrollbars();
    ['diagnose', 'plan', 'code', 'tests'].forEach(function (s) {
      const body = win.document.querySelector('.lg-fix-stage[data-stage="' + s + '"] .lg-fix-stage-body');
      expect(body.__lgFixStageScroll).toBeTruthy();
      expect(typeof body.__lgFixStageScroll.update).toBe('function');
    });
  });

  it('hides the track for a stage whose content fits (no overflow yet)', () => {
    win.lgInitFixStageScrollbars();
    const stage = win.document.querySelector('.lg-fix-stage[data-stage="diagnose"]');
    const track = stage.querySelector('.lg-fix-stage-body-track');
    const body = stage.querySelector('.lg-fix-stage-body');
    body._setScrollHeight(100);
    body.__lgFixStageScroll.update();
    expect(track.style.visibility).toBe('hidden');
  });

  it('shows the track for a stage whose content overflows the 300px viewport', () => {
    win.lgInitFixStageScrollbars();
    const stage = win.document.querySelector('.lg-fix-stage[data-stage="plan"]');
    const track = stage.querySelector('.lg-fix-stage-body-track');
    const body = stage.querySelector('.lg-fix-stage-body');
    body._setScrollHeight(2000);
    body.__lgFixStageScroll.update();
    expect(track.style.visibility).toBe('');
  });

  it('lgToggleFixStage calls update() after the wrapper becomes visible', async () => {
    win.lgInitFixStageScrollbars();
    const stage = win.document.querySelector('.lg-fix-stage[data-stage="code"]');
    const body = stage.querySelector('.lg-fix-stage-body');
    const track = stage.querySelector('.lg-fix-stage-body-track');
    const head = stage.querySelector('.lg-fix-stage-head');

    let updateCalls = 0;
    body.__lgFixStageScroll = { update: function () { updateCalls++; } };

    body._setScrollHeight(2000);
    win.lgToggleFixStage(head);
    await nextFrame(win);

    expect(updateCalls).toBeGreaterThan(0);
    expect(stage.classList.contains('lg-fix-stage-open')).toBe(true);
    expect(track).toBeTruthy();
  });

  it('lgRenderFixStage triggers update() after writing the stage output', async () => {
    win.lgInitFixStageScrollbars();
    const stage = win.document.querySelector('.lg-fix-stage[data-stage="tests"]');
    const body = stage.querySelector('.lg-fix-stage-body');
    let updateCalls = 0;
    body.__lgFixStageScroll = { update: function () { updateCalls++; } };

    win.lgRenderFixStage('tests', 'DONE', 'long output body that fills the viewport and beyond');
    await nextFrame(win);

    expect(updateCalls).toBeGreaterThan(0);
    expect(body.textContent).toBe('long output body that fills the viewport and beyond');
    expect(stage.querySelector('.lg-fix-stage-head').className).toContain('lg-fix-stage-done');
    expect(stage.classList.contains('lg-fix-stage-open')).toBe(true);
  });

  it('lgRenderFixStage is a no-op for the scrollbar when no SLScroll is wired yet', () => {
    expect(function () {
      win.lgRenderFixStage('diagnose', 'DONE', 'payload');
    }).not.toThrow();
  });

  it('lgInitFixStageScrollbars is idempotent (does not double-wire)', () => {
    win.lgInitFixStageScrollbars();
    const body = win.document.querySelector('.lg-fix-stage[data-stage="diagnose"] .lg-fix-stage-body');
    const first = body.__lgFixStageScroll;
    win.lgInitFixStageScrollbars();
    expect(body.__lgFixStageScroll).toBe(first);
  });
});