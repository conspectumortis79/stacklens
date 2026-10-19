import { describe, it, expect } from 'vitest';
import { readSource } from './_load.js';
import { pathToFileURL } from 'node:url';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const staticDir = resolve(here, '..', '..', '..', 'src', 'main', 'resources', 'static');

async function importGrafanaHelpers() {
  const url = pathToFileURL(resolve(staticDir, 'js/grafana/helpers.js')).href;
  return await import(url);
}

describe('grafana/helpers.js', () => {
  describe('escHtml()', () => {
    it('returns empty string for null and undefined', async () => {
      const { escHtml } = await importGrafanaHelpers();
      expect(escHtml(null)).toBe('');
      expect(escHtml(undefined)).toBe('');
    });

    it('escapes the five HTML-sensitive characters', async () => {
      const { escHtml } = await importGrafanaHelpers();
      expect(escHtml(`<>&"'`)).toBe('&lt;&gt;&amp;&quot;&#39;');
    });

    it('escapes & first to prevent double-escaping', async () => {
      const { escHtml } = await importGrafanaHelpers();
      expect(escHtml('a&b')).toBe('a&amp;b');
    });

    it('coerces numbers and booleans to a string', async () => {
      const { escHtml } = await importGrafanaHelpers();
      expect(escHtml(42)).toBe('42');
      expect(escHtml(false)).toBe('false');
    });
  });

  describe('GRAFANA_COLORS', () => {
    it('exposes the expected named colors', async () => {
      const { GRAFANA_COLORS } = await importGrafanaHelpers();
      const expected = ['green', 'red', 'yellow', 'orange', 'blue', 'purple'];
      for (const k of expected) {
        expect(GRAFANA_COLORS[k]).toMatch(/^#[0-9a-f]{6}$/);
      }
    });

    it('contains a "default" entry used as a fallback for unknown names', async () => {
      const { GRAFANA_COLORS } = await importGrafanaHelpers();
      expect(GRAFANA_COLORS.default).toMatch(/^#[0-9a-f]{6}$/);
    });

    it('contains a "text" entry mapped from Grafana "text" theme color', async () => {
      const { GRAFANA_COLORS } = await importGrafanaHelpers();
      expect(GRAFANA_COLORS.text).toBe('#d8d9da');
    });
  });

  describe('resolveColor()', () => {
    it('returns GRAFANA_COLORS.blue as the default for falsy input', async () => {
      const { resolveColor } = await importGrafanaHelpers();
      expect(resolveColor(null)).toMatch(/^#[0-9a-f]{6}$/);
      expect(resolveColor(undefined)).toMatch(/^#[0-9a-f]{6}$/);
      expect(resolveColor('')).toMatch(/^#[0-9a-f]{6}$/);
    });

    it('returns GRAFANA_COLORS.blue for unknown color names', async () => {
      const { resolveColor, GRAFANA_COLORS } = await importGrafanaHelpers();
      expect(resolveColor('no-such-color')).toBe(GRAFANA_COLORS.blue);
    });

    it('passes through hex colors verbatim', async () => {
      const { resolveColor } = await importGrafanaHelpers();
      expect(resolveColor('#abcdef')).toBe('#abcdef');
      expect(resolveColor('#ABCDEF')).toBe('#ABCDEF');
      expect(resolveColor('#123')).toBe('#123');
    });

    it('passes through rgb(...) and rgba(...) strings verbatim', async () => {
      const { resolveColor } = await importGrafanaHelpers();
      expect(resolveColor('rgb(255,0,0)')).toBe('rgb(255,0,0)');
      expect(resolveColor('rgba(0,0,0,.5)')).toBe('rgba(0,0,0,.5)');
    });

    it('resolves named colors to their hex equivalents', async () => {
      const { resolveColor, GRAFANA_COLORS } = await importGrafanaHelpers();
      expect(resolveColor('red')).toBe(GRAFANA_COLORS.red);
      expect(resolveColor('green')).toBe(GRAFANA_COLORS.green);
      expect(resolveColor('orange')).toBe(GRAFANA_COLORS.orange);
      expect(resolveColor('dark-yellow')).toBe(GRAFANA_COLORS['dark-yellow']);
      expect(resolveColor('semi-dark-green')).toBe(GRAFANA_COLORS['semi-dark-green']);
    });
  });

  describe('getPanelFixedColor()', () => {
    it('returns null when the panel has no fieldConfig', async () => {
      const { getPanelFixedColor } = await importGrafanaHelpers();
      expect(getPanelFixedColor({})).toBeNull();
      expect(getPanelFixedColor(null)).toBeNull();
      expect(getPanelFixedColor(undefined)).toBeNull();
    });

    it('returns null when the panel has no color configuration', async () => {
      const { getPanelFixedColor } = await importGrafanaHelpers();
      expect(getPanelFixedColor({ fieldConfig: {} })).toBeNull();
      expect(getPanelFixedColor({ fieldConfig: { defaults: {} } })).toBeNull();
    });

    it('returns null when the color mode is not "fixed"', async () => {
      const { getPanelFixedColor } = await importGrafanaHelpers();
      const panel = {
        fieldConfig: {
          defaults: {
            color: { mode: 'thresholds', fixedColor: 'red' }
          }
        }
      };
      expect(getPanelFixedColor(panel)).toBeNull();
    });

    it('returns the resolved hex color when the mode is "fixed"', async () => {
      const { getPanelFixedColor, GRAFANA_COLORS } = await importGrafanaHelpers();
      const panel = {
        fieldConfig: {
          defaults: {
            color: { mode: 'fixed', fixedColor: 'green' }
          }
        }
      };
      expect(getPanelFixedColor(panel)).toBe(GRAFANA_COLORS.green);
    });

    it('returns the raw hex color when fixedColor is a hex string', async () => {
      const { getPanelFixedColor } = await importGrafanaHelpers();
      const panel = {
        fieldConfig: {
          defaults: {
            color: { mode: 'fixed', fixedColor: '#ff00ff' }
          }
        }
      };
      expect(getPanelFixedColor(panel)).toBe('#ff00ff');
    });
  });

  describe('thresholdColorForValue()', () => {
    it('returns green when the thresholds object has no steps', async () => {
      const { thresholdColorForValue } = await importGrafanaHelpers();
      expect(thresholdColorForValue(50, {})).toBe('#73bf69');
      expect(thresholdColorForValue(50, null)).toBe('#73bf69');
      expect(thresholdColorForValue(50, undefined)).toBe('#73bf69');
      expect(thresholdColorForValue(50, { steps: [] })).toBe('#73bf69');
    });

    it('uses the first step\'s color when value is below every threshold', async () => {
      const { thresholdColorForValue, GRAFANA_COLORS } = await importGrafanaHelpers();
      const thresholds = { steps: [{ color: 'red', value: null }, { color: 'green', value: 80 }] };
      expect(thresholdColorForValue(10, thresholds)).toBe(GRAFANA_COLORS.red);
    });

    it('uses the last matching step\'s color when value equals the threshold', async () => {
      const { thresholdColorForValue, GRAFANA_COLORS } = await importGrafanaHelpers();
      const thresholds = { steps: [
        { color: 'green', value: null },
        { color: 'yellow', value: 50 },
        { color: 'red', value: 90 }
      ] };
      expect(thresholdColorForValue(50, thresholds)).toBe(GRAFANA_COLORS.yellow);
      expect(thresholdColorForValue(90, thresholds)).toBe(GRAFANA_COLORS.red);
      expect(thresholdColorForValue(89, thresholds)).toBe(GRAFANA_COLORS.yellow);
    });

    it('handles null first-step value by skipping it', async () => {
      const { thresholdColorForValue, GRAFANA_COLORS } = await importGrafanaHelpers();
      const thresholds = { steps: [{ color: 'green', value: null }, { color: 'red', value: 100 }] };
      expect(thresholdColorForValue(150, thresholds)).toBe(GRAFANA_COLORS.red);
      expect(thresholdColorForValue(50, thresholds)).toBe(GRAFANA_COLORS.green);
    });

    it('coerces numeric string thresholds to numbers', async () => {
      const { thresholdColorForValue, GRAFANA_COLORS } = await importGrafanaHelpers();
      const thresholds = { steps: [{ color: 'green', value: null }, { color: 'red', value: '75' }] };
      expect(thresholdColorForValue(75, thresholds)).toBe(GRAFANA_COLORS.red);
    });

    it('returns green when steps is missing entirely (graceful fallthrough)', async () => {
      const { thresholdColorForValue } = await importGrafanaHelpers();
      expect(thresholdColorForValue(0, { steps: undefined })).toBe('#73bf69');
    });
  });
});
