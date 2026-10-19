import { beforeEach, describe, expect, it } from 'vitest';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ANALYSIS_UI = resolve(
  __dirname,
  '../../main/resources/templates/fragments/review-analysis/scripts-analysis-ui.html',
);

function bootWindow() {
  const win = makeWindow();
  runInWindow(win, extractInlineScripts(ANALYSIS_UI));
  return win;
}

describe('Review Analysis dashboard sizing guard', () => {
  let win;

  beforeEach(() => {
    win = bootWindow();
  });

  it('injects a canvas size guard into generated dashboard HTML', () => {
    const html = '<!DOCTYPE html><html><head><title>Sprint</title></head><body><canvas id="chart"></canvas></body></html>';

    const prepared = win.raPrepareDashboardHtml(html);

    expect(prepared).toContain('id="stacklens-dashboard-chart-safety"');
    expect(prepared).toContain('max-height:320px!important');
    expect(prepared).toContain('max-width:100%!important');
    expect(prepared.indexOf('stacklens-dashboard-chart-safety')).toBeLessThan(prepared.indexOf('</head>'));
  });

  it('does not inject the chart guard twice when a result is rendered again', () => {
    const html = '<html><head></head><body><canvas></canvas></body></html>';

    const preparedTwice = win.raPrepareDashboardHtml(win.raPrepareDashboardHtml(html));

    expect((preparedTwice.match(/stacklens-dashboard-chart-safety/g) || [])).toHaveLength(1);
  });

  it('supports dashboard fragments without a head and preserves empty input', () => {
    const fragment = '<section><canvas></canvas></section>';

    expect(win.raPrepareDashboardHtml(fragment)).toMatch(/^<style id="stacklens-dashboard-chart-safety">/);
    expect(win.raPrepareDashboardHtml('')).toBe('');
    expect(win.raPrepareDashboardHtml(null)).toBeNull();
  });

  it('bounds iframe height so a generated dashboard cannot make the host page endless', () => {
    expect(win.raBoundDashboardHeight(100)).toBe(700);
    expect(win.raBoundDashboardHeight(900.2)).toBe(901);
    expect(win.raBoundDashboardHeight(50_000)).toBe(1200);
  });

  it('uses the minimum iframe height for missing or invalid measurements', () => {
    expect(win.raBoundDashboardHeight(null)).toBe(700);
    expect(win.raBoundDashboardHeight(undefined)).toBe(700);
    expect(win.raBoundDashboardHeight('not-a-number')).toBe(700);
  });
});
