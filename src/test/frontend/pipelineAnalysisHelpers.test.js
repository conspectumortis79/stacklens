import { describe, it, expect } from 'vitest';


function paParseJsonSafe(s) {
  if (!s) return null;
  try { return JSON.parse(s); } catch (_) { return null; }
}

function paEsc(str) {
  if (str === undefined || str === null) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function paSeverityBadgeClass(sev) {
  return 'pa-badge pa-badge-' + String(sev || 'LOW').toUpperCase();
}

describe('pipeline-analysis helpers', () => {

  describe('paParseJsonSafe', () => {
    it('returns null for null / empty / undefined input', () => {
      expect(paParseJsonSafe(null)).toBeNull();
      expect(paParseJsonSafe(undefined)).toBeNull();
      expect(paParseJsonSafe('')).toBeNull();
    });

    it('parses a JSON array', () => {
      const arr = paParseJsonSafe('[{"id":"f1"},{"id":"f2"}]');
      expect(Array.isArray(arr)).toBe(true);
      expect(arr.length).toBe(2);
      expect(arr[0].id).toBe('f1');
    });

    it('returns null on invalid JSON', () => {
      expect(paParseJsonSafe('not json')).toBeNull();
    });
  });

  describe('paEsc', () => {
    it('escapes HTML special chars', () => {
      expect(paEsc('<script>alert("x")</script>')).toBe(
        '&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;',
      );
    });

    it('returns empty string for null/undefined', () => {
      expect(paEsc(null)).toBe('');
      expect(paEsc(undefined)).toBe('');
    });
  });

  describe('paSeverityBadgeClass', () => {
    it('uppercases the severity and prefixes with pa-badge-', () => {
      expect(paSeverityBadgeClass('high')).toBe('pa-badge pa-badge-HIGH');
      expect(paSeverityBadgeClass('medium')).toBe('pa-badge pa-badge-MEDIUM');
      expect(paSeverityBadgeClass('low')).toBe('pa-badge pa-badge-LOW');
    });

    it('falls back to LOW when severity is missing', () => {
      expect(paSeverityBadgeClass(undefined)).toBe('pa-badge pa-badge-LOW');
      expect(paSeverityBadgeClass(null)).toBe('pa-badge pa-badge-LOW');
    });
  });

  describe('Key/value meaning structure (UI rendering)', () => {
    it('a file description with keys renders to a 3-column table row per key', () => {
      const desc = {
        path: 'docker-compose.yml',
        fileKind: 'docker-compose',
        description: 'top-level stack',
        keys: [
          { key: 'version', value: '3.9', meaning: 'compose file format version' },
          { key: 'services.app.image', value: 'myapp:1.4.2', meaning: 'container image tag' },
        ],
      };
      let html = '<div class="pa-file-card">';
      html += '<div class="pa-file-head">';
      html += '<span class="pa-file-path">' + paEsc(desc.path) + '</span>';
      html += '<span class="pa-file-kind">' + paEsc(desc.fileKind) + '</span>';
      html += '</div>';
      html += '<table class="pa-file-keys"><tbody>';
      desc.keys.forEach((k) => {
        html += '<tr>';
        html += '<td class="pa-key">' + paEsc(k.key) + '</td>';
        html += '<td class="pa-val">' + paEsc(k.value) + '</td>';
        html += '<td class="pa-mean">' + paEsc(k.meaning) + '</td>';
        html += '</tr>';
      });
      html += '</tbody></table></div>';
      expect(html).toContain('<span class="pa-file-path">docker-compose.yml</span>');
      expect(html).toContain('<span class="pa-file-kind">docker-compose</span>');
      expect(html).toContain('<td class="pa-key">services.app.image</td>');
      expect(html).toContain('<td class="pa-val">myapp:1.4.2</td>');
      expect(html).toContain('<td class="pa-mean">container image tag</td>');
    });
  });
});
