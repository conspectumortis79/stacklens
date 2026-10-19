window.TcRender = (function () {
  var $ = function(id){ return document.getElementById(id); };
  function t(key, fallback) { return (window.slHelpers && window.slHelpers.t) ? window.slHelpers.t(key, fallback) : (fallback || key); }

  function inlineMd(text) {
    if (!text) return '';
    var s = String(text);
    var result = '';
    var i = 0;
    var m;
    while (i < s.length) {
      var rest = s.slice(i);
      if ((m = /^\*\*(.+?)\*\*/.exec(rest))) {
        result += '<strong>' + escapeHtml(m[1]) + '</strong>';
        i += m[0].length;
      } else if ((m = /^_([^_]+)_/.exec(rest))) {
        result += '<em>' + escapeHtml(m[1]) + '</em>';
        i += m[0].length;
      } else if ((m = /^`([^`]+)`/.exec(rest))) {
        result += '<code class="tc-md-code">' + escapeHtml(m[1]) + '</code>';
        i += m[0].length;
      } else if ((m = /^\[([^\]]+)\]\(([^)]+)\)/.exec(rest))) {
        result += '<a href="' + escapeHtml(m[2]) + '" target="_blank" rel="noopener noreferrer">' + escapeHtml(m[1]) + '</a>';
        i += m[0].length;
      } else {
        result += escapeHtml(s[i]);
        i++;
      }
    }
    return result;
  }

  function renderMarkdown(text) {
    if (!text) return '';
    var lines = String(text).split('\n');
    var html = '';
    var inBullet = false, inOrdered = false;
    function closeLists() {
      if (inBullet)   { html += '</ul>'; inBullet = false; }
      if (inOrdered)  { html += '</ol>'; inOrdered = false; }
    }
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var isUl = /^[\-\*] /.test(line);
      var isOl = /^\d+\. /.test(line);
      if (!isUl && inBullet)  { html += '</ul>'; inBullet = false; }
      if (!isOl && inOrdered) { html += '</ol>'; inOrdered = false; }
      if (/^## /.test(line)) {
        closeLists();
        html += '<div class="tc-md-h2">' + inlineMd(line.slice(3).trim()) + '</div>';
      } else if (/^### /.test(line)) {
        closeLists();
        html += '<div class="tc-md-h3">' + inlineMd(line.slice(4).trim()) + '</div>';
      } else if (/^---+$/.test(line.trim())) {
        closeLists();
        html += '<hr class="tc-md-hr">';
      } else if (/^> /.test(line)) {
        closeLists();
        html += '<div class="tc-md-bq">' + inlineMd(line.slice(2)) + '</div>';
      } else if (isUl) {
        if (!inBullet) { html += '<ul class="tc-md-ul">'; inBullet = true; }
        html += '<li>' + inlineMd(line.replace(/^[\-\*] /, '')) + '</li>';
      } else if (isOl) {
        if (!inOrdered) { html += '<ol class="tc-md-ol">'; inOrdered = true; }
        html += '<li>' + inlineMd(line.replace(/^\d+\. /, '')) + '</li>';
      } else if (line.trim() === '') {
        closeLists();
      } else {
        closeLists();
        html += '<p class="tc-md-p">' + inlineMd(line) + '</p>';
      }
    }
    closeLists();
    return html;
  }

  var SECTION_CP_MAP = {
    0x1F9D1: { key: 'ticket_creation.section_story',           fb: 'User Story' },
    0x1F6E0: { key: 'ticket_creation.section_objective',       fb: 'Objective' },
    0x1F4CB: { key: 'ticket_creation.section_steps',           fb: 'Implementation Plan' },
    0x1F41B: { key: 'ticket_creation.section_bug_desc',        fb: 'Bug Description' },
    0x1F501: { key: 'ticket_creation.section_steps_reproduce', fb: 'Steps to Reproduce' },
    0x274C:  { key: 'ticket_creation.section_actual',          fb: 'Actual Behaviour' }
  };

  function renderDescription(desc) {
    if (!desc) return '<p style="color:var(--tc-muted);font-style:italic;">' + t('ticket_creation.no_description', 'Keine Beschreibung.') + '</p>';

    var lines = desc.split('\n');
    var sections = [];
    var current = null;

    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var trimmed = line.trim();

      var isAcHeader = /^(✅|##\s*(✅\s*)?(Acceptance Criteria|Akzeptanzkriterien|AK\b|AC\b|Expected Behaviour|Erwartetes Verhalten|Definition of Done))/i.test(trimmed);
      var isBgHeader = /^(🔴|🧠|##\s*((🔴|🧠)\s*)?(Background|Hintergrund|Environment|Context|Umgebung))/i.test(trimmed);

      var isGenericHeader = !isAcHeader && !isBgHeader && /^##\s+\S/.test(trimmed);

      if (isAcHeader) {
        if (current) sections.push(current);
        var acLabel;
        if (/Definition of Done/i.test(trimmed)) acLabel = t('ticket_creation.section_dod', 'Definition of Done');
        else if (/Expected Behaviour|Erwartetes Verhalten/i.test(trimmed)) acLabel = t('ticket_creation.section_expected', 'Expected Behaviour');
        else acLabel = t('ticket_creation.section_ac', 'Acceptance Criteria');
        current = { type: 'ac', icon: '✅', label: acLabel, lines: [] };
      } else if (isBgHeader) {
        if (current) sections.push(current);
        current = { type: 'bg', icon: '🧠', label: t('ticket_creation.section_bg', 'Background'), lines: [] };
      } else if (isGenericHeader) {
        if (current) sections.push(current);
        var hdrRaw = trimmed.replace(/^##\s+/, '');

        var cp = hdrRaw.codePointAt(0) || 0;
        var hasEmoji = cp > 0x2600;

        var secIcon = hasEmoji ? (hdrRaw.match(/^\S+/) || [String.fromCodePoint(cp)])[0] : '📌';
        var knownSec = hasEmoji ? SECTION_CP_MAP[cp] : null;
        var secLabel = knownSec
          ? t(knownSec.key, knownSec.fb)
          : (hasEmoji ? hdrRaw.replace(/^\S+\s*/, '').trim() || hdrRaw : hdrRaw);
        current = { type: 'other', icon: secIcon, label: secLabel, lines: [] };
      } else {
        if (!current) current = { type: 'story', icon: '🧑‍💻', label: t('ticket_creation.section_story', 'User Story'), lines: [] };
        current.lines.push(line);
      }
    }
    if (current) sections.push(current);

    if (sections.length === 0) {
      return '<div class="tc-jira-section tc-jira-section--other">' +
             '<div class="tc-jira-section-body">' + renderMarkdown(desc) + '</div></div>';
    }

    var html = '';
    for (var j = 0; j < sections.length; j++) {
      var s = sections[j];
      var content = s.lines.join('\n').trim();
      if (!content && s.type !== 'ac' && s.type !== 'bg') continue;
      html += '<div class="tc-jira-section tc-jira-section--' + s.type + '">' +
              '<div class="tc-jira-section-head">' +
              '<span>' + escapeHtml(s.icon) + '</span>' +
              '<span>' + escapeHtml(s.label) + '</span>' +
              '</div>' +
              '<div class="tc-jira-section-body">' + renderMarkdown(content) + '</div>' +
              '</div>';
    }
    return html || renderMarkdown(desc);
  }


  return {
    renderDescription: renderDescription,
  };
})();
