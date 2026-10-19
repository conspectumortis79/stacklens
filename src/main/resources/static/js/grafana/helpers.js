
  export function escHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  export const GRAFANA_COLORS = {
    green:            '#73bf69',
    'dark-green':     '#37872d',
    'semi-dark-green':'#56a64b',
    'light-green':    '#96d98d',
    red:              '#f2495c',
    'dark-red':       '#c4162a',
    'semi-dark-red':  '#e02f44',
    yellow:           '#fade2a',
    'dark-yellow':    '#e0b400',
    orange:           '#ff9830',
    'dark-orange':    '#e36e1a',
    blue:             '#5794f2',
    'dark-blue':      '#1f60c4',
    'semi-dark-blue': '#3274d9',
    'light-blue':     '#aafafc',
    purple:           '#b877d9',
    'dark-purple':    '#8a3ffc',
    'super-light-blue': '#c0d8ff',
    text:             '#d8d9da',
    default:          '#7eb26d',
  };

  export function resolveColor(c) {
    if (!c) return GRAFANA_COLORS.blue;
    if (c.startsWith('#') || c.startsWith('rgb')) return c;
    return GRAFANA_COLORS[c] || GRAFANA_COLORS.blue;
  }

  export function getPanelFixedColor(panel) {
    var colorCfg = panel && panel.fieldConfig && panel.fieldConfig.defaults && panel.fieldConfig.defaults.color;
    if (!colorCfg) return null;
    if (colorCfg.mode === 'fixed' && colorCfg.fixedColor) return resolveColor(colorCfg.fixedColor);
    return null;
  }

  export function thresholdColorForValue(value, thresholds) {
    if (!thresholds?.steps?.length) return GRAFANA_COLORS.green;
    let color = resolveColor(thresholds.steps[0]?.color) || GRAFANA_COLORS.green;
    for (const step of thresholds.steps) {
      if (step.value != null && value >= Number(step.value)) {
        color = resolveColor(step.color);
      }
    }
    return color;
  }

  