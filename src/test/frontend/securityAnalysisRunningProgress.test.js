import { describe, it, expect, afterEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const FRAGMENTS = resolve(here, '../../main/resources/templates/fragments/security-analysis');
const I18N = resolve(here, '../../main/resources/static/i18n');
const RENDER = resolve(FRAGMENTS, 'scripts-render.html');
const STYLES = resolve(FRAGMENTS, 'styles.html');
const HISTORY_LIST = resolve(here, '../../main/resources/static/js/history-list.js');

const PARTS = ['scripts.html', 'scripts-helpers.html', 'scripts-render.html', 'scripts-fix-pr.html'];

const OPEN_WINDOWS = [];

function bootWindow() {
  const win = makeWindow('<div id="baMainArea"></div>');
  OPEN_WINDOWS.push(win);
  runInWindow(win, [
    'window.SLScroll = function() { return { update: function() {} }; };',
    'window.showConfirm = function() {};',
    'window.baInitTokenUsageBar = function() {};',
    'if (!window.CSS) { window.CSS = { escape: function (v) { return String(v); } }; }',
  ].join('\n'));
  runInWindow(win, PARTS.map((file) => extractInlineScripts(resolve(FRAGMENTS, file))).join('\n;\n'));
  win.fetch = () => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([]) });
  return win;
}

function area(win) {
  return win.document.getElementById('baMainArea');
}

afterEach(() => {
  OPEN_WINDOWS.splice(0).forEach((win) => {
    if (win._baRunClockTimer) {
      win.clearInterval(win._baRunClockTimer);
      win._baRunClockTimer = null;
    }
  });
});

describe('security analysis running view: animated progress instead of bug hero', () => {
  it('drops the bug icon and the penetration-test sentence', () => {
    const win = bootWindow();
    win.baShowRunning('stacklens', 1, 3, 'Iteration 1: Reconnaissance…');
    expect(area(win).querySelector('.ba-empty')).toBe(null);
    expect(area(win).querySelector('i.bi-bug')).toBe(null);
    expect(area(win).textContent).not.toContain('Penetrationstest');
  });

  it('carries the shimmer and the pulsing tip inside the filled bar', () => {
    const win = bootWindow();
    win.baShowRunning('stacklens', 1, 3);
    const fill = win.document.getElementById('baProgressBarInner');
    expect(fill.style.width).toBe('33%');
    expect(fill.querySelector('.ba-bar-shimmer')).not.toBe(null);
    expect(fill.querySelector('.ba-bar-tip')).not.toBe(null);
    expect(win.document.querySelector('.ba-iter-progress')).not.toBe(null);
    expect(win.document.getElementById('baLiveLog')).not.toBe(null);
  });

  it('shows percent and repository name in the progress card', () => {
    const win = bootWindow();
    win.baShowRunning('stacklens', 2, 3, 'Iteration 2: Exploit…');
    expect(win.document.getElementById('baRunPct').textContent).toBe('67 %');
    expect(win.document.getElementById('baRunRepo').textContent).toBe('stacklens');
    expect(win.document.getElementById('baIterLabelInner').textContent).toContain('Schritt 2/3');
  });

  it('updates the mounted card in place on every poll', () => {
    const win = bootWindow();
    win.baShowRunning('stacklens', 1, 3, 'a');
    const firstFill = win.document.getElementById('baProgressBarInner');
    const firstPct = win.document.getElementById('baRunPct');
    win.baShowRunning('stacklens', 3, 3, 'b');
    expect(win.document.getElementById('baProgressBarInner')).toBe(firstFill);
    expect(win.document.getElementById('baRunPct')).toBe(firstPct);
    expect(firstFill.style.width).toBe('100%');
    expect(firstPct.textContent).toBe('100 %');
    expect(win.document.getElementById('baIterLabelInner').textContent).toContain('Schritt 3/3');
  });

  it('anchors the elapsed clock to the server createdAt when available', () => {
    const win = bootWindow();
    globalThis.__baWin = win;
    const started = Date.now() - 125000;
    runInWindow(win, 'window.slHistory = { parseDate: function (v) { return new Date(' + started + '); } };');
    win.baShowRunning('stacklens', 1, 3, 'a', '2026-09-02T16:23:51.646407');
    expect(win.document.getElementById('baRunElapsed').textContent).toMatch(/0[12]:\d\d/);
  });

  it('falls back to the mount moment without a server timestamp', () => {
    const win = bootWindow();
    win.baShowRunning('stacklens', 1, 3, 'a');
    expect(win.document.getElementById('baRunElapsed').textContent).toBe('seit 00:00');
    win.baShowRunning('stacklens', 2, 3, 'b', 'nonsense');
    expect(win.document.getElementById('baRunElapsed').textContent).toBe('seit 00:00');
  });

  it('keeps the shared history parser available for the elapsed clock', () => {
    expect(readFileSync(HISTORY_LIST, 'utf8')).toContain('parseDate: parseDate');
  });
});

describe('security analysis running view: progress animation css', () => {
  const css = readFileSync(STYLES, 'utf8');

  it('clips the shimmer to the filled bar and paints the gradient', () => {
    const inner = css.slice(css.indexOf('.ba-progress-bar-inner {'), css.indexOf('.ba-bar-shimmer'));
    expect(inner).toContain('position: relative');
    expect(inner).toContain('overflow: hidden');
    expect(inner).toContain('linear-gradient(90deg, var(--app-accent-purple), var(--app-accent-blue))');
  });

  it('defines both keyframe animations', () => {
    expect(css).toContain('@keyframes ba-bar-shimmer');
    expect(css).toContain('@keyframes ba-bar-tip');
    expect(css).toContain('animation: ba-bar-shimmer 1.5s linear infinite');
    expect(css).toContain('animation: ba-bar-tip 1.5s ease-in-out infinite');
  });
});

describe('i18n for the running progress card', () => {
  const de = JSON.parse(readFileSync(resolve(I18N, 'de.json'), 'utf8'));
  const en = JSON.parse(readFileSync(resolve(I18N, 'en.json'), 'utf8'));

  it('replaces the removed hero sentence with the elapsed label', () => {
    expect(de.bug_analysis.running).toBe(undefined);
    expect(en.bug_analysis.running).toBe(undefined);
    expect(de.bug_analysis.running_elapsed).toContain('{t}');
    expect(en.bug_analysis.running_elapsed).toContain('{t}');
  });
});
