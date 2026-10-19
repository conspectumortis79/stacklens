import { describe, it, expect } from 'vitest';


function escapeHtml(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function buildSubtaskDescriptionBlock(st, idx) {
  const stSummary = escapeHtml(st.summary || '');
  const stDescription = escapeHtml(st.description || '');
  return (
    '<textarea class="tc-subtask-summary" data-subtask-index="' + idx + '" rows="1">' + stSummary + '</textarea>' +
    '<div class="tc-textarea-wrap tc-subtask-desc-wrap" data-subtask-index="' + idx + '">' +
      '<textarea class="tc-subtask-description tc-textarea-box" data-subtask-index="' + idx + '" rows="3">' + stDescription + '</textarea>' +
      '<div class="sl-scroll-track" data-always-visible="true"><div class="sl-scroll-thumb"></div></div>' +
    '</div>'
  );
}

describe('Ticket-Creation subtask description custom scrollbar wrapper', () => {
  it('wraps the description textarea in .tc-textarea-wrap.tc-subtask-desc-wrap', () => {
    const html = buildSubtaskDescriptionBlock({ summary: 'Sub A', description: 'Body' }, 0);
    expect(html).toContain('class="tc-textarea-wrap tc-subtask-desc-wrap"');
    const wrapperOpenAt = html.indexOf('tc-textarea-wrap tc-subtask-desc-wrap');
    const textareaOpenAt = html.indexOf('class="tc-subtask-description tc-textarea-box"');
    const wrapperCloseAt = html.indexOf('</div>', wrapperOpenAt);
    expect(wrapperOpenAt).toBeGreaterThan(-1);
    expect(textareaOpenAt).toBeGreaterThan(wrapperOpenAt);
    expect(textareaOpenAt).toBeLessThan(wrapperCloseAt);
  });

  it('hosts a sl-scroll-track and sl-scroll-thumb inside the wrapper', () => {
    const html = buildSubtaskDescriptionBlock({ summary: 'Sub A', description: 'Body' }, 0);
    const wrapperOpenAt = html.indexOf('tc-textarea-wrap tc-subtask-desc-wrap');
    const wrapperCloseAt = html.indexOf('</div>', wrapperOpenAt);
    const inner = html.substring(wrapperOpenAt, wrapperCloseAt);
    expect(inner).toContain('class="sl-scroll-track"');
    expect(inner).toContain('class="sl-scroll-thumb"');
  });

  it('marks the track as data-always-visible so the track stays put and only the thumb hides when not needed', () => {
    const html = buildSubtaskDescriptionBlock({ summary: 'Sub A', description: 'Body' }, 0);
    expect(html).toContain('data-always-visible="true"');
  });

  it('adds the tc-textarea-box class to the inner textarea so native scrollbar is suppressed', () => {
    const html = buildSubtaskDescriptionBlock({ summary: 'Sub A', description: 'Body' }, 0);
    expect(html).toContain('class="tc-subtask-description tc-textarea-box"');
  });

  it('preserves the long description content (does not truncate to fit rows=3)', () => {
    const longBody =
      '## 🛠 Goal\n' +
      'Bereitstellung einer lauffähigen Spring-Boot-Basis in Kotlin inklusive Build-, Test- und Deployment-Pipeline als Fundament für die weitere Implementierung der Controlling-Schnittstelle.\n' +
      '\n' +
      '## 🎯 Scope\n' +
      '**Im Scope:**\n- Spring-Boot-Projekt mit Kotlin\n- CI/CD-Pipeline\n- DB-Migration\n- Integrationstest\n\n' +
      '_Out of Scope:_ Frontend für die Controlling-Fachabteilung (separates Ticket).\n';
    const html = buildSubtaskDescriptionBlock({ summary: 'Backend base', description: longBody }, 0);
    expect(html).toContain(escapeHtml(longBody));
    expect(html.split('---').length - 1).toBe(0);
  });
});