window.SpMarkdown = (function () {
  function jiraEmojiToUnicode(text) {
    if (!text) return text;
    return String(text).replace(/:([a-z0-9_+-]+):/gi, function(_, name){
      var glyph = JIRA_EMOJI[name.toLowerCase()];
      return glyph || (':' + name + ':');
    });
  }

  function inlineMd(text) {
    if (!text) return '';
    var s = jiraEmojiToUnicode(String(text));
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
        result += '<code class="sp-jira-code">' + escapeHtml(m[1]) + '</code>';
        i += m[0].length;
      } else if ((m = /^\[([^\]]+)\]\((https?:\/\/[^)]+)\)/.exec(rest))) {
        result += '<a href="' + escapeHtml(m[2]) + '" target="_blank" rel="noopener">' + escapeHtml(m[1]) + '</a>';
        i += m[0].length;
      } else {
        result += escapeHtml(s[i]);
        i++;
      }
    }
    return result;
  }

  var SP_DOC_SECTIONS = {
    'goal':                        {icon:'bi-bullseye',         cls:'goal'},
    'ziel':                        {icon:'bi-bullseye',         cls:'goal'},
    'user story':                  {icon:'bi-person-workspace', cls:'story'},
    'anwenderfall':                {icon:'bi-person-workspace', cls:'story'},
    'acceptance criteria':         {icon:'bi-check2-square',    cls:'ac'},
    'akzeptanzkriterien':          {icon:'bi-check2-square',    cls:'ac'},
    'ak':                          {icon:'bi-check2-square',    cls:'ac'},
    'dependencies':                {icon:'bi-link-45deg',       cls:'deps'},
    'abhängigkeiten':              {icon:'bi-link-45deg',       cls:'deps'},
    'abhängigkeiten / vorarbeiten':{icon:'bi-link-45deg',       cls:'deps'},
    'preconditions':               {icon:'bi-exclamation-triangle', cls:'pre'},
    'vorbedingungen':              {icon:'bi-exclamation-triangle', cls:'pre'},
    'notes':                       {icon:'bi-brain',            cls:'notes'},
    'anmerkungen':                 {icon:'bi-brain',            cls:'notes'},
    'hinweise':                    {icon:'bi-brain',            cls:'notes'},
    'hints':                       {icon:'bi-brain',            cls:'notes'},
    'hintergrund':                 {icon:'bi-brain',            cls:'notes'},
    'background':                  {icon:'bi-brain',            cls:'notes'},
    'hintergrundinformationen':    {icon:'bi-brain',            cls:'notes'}
  };

  function safeImprovedText(text) {
    if (!text) return '';
    var s = String(text).trim();
    if (s.charAt(0) !== '{') return s;
    try {
      var obj = JSON.parse(s);
      if (typeof obj.improvedDescription === 'string' && obj.improvedDescription.trim()) {
        return obj.improvedDescription.trim();
      }
      function flatAdf(n) {
        if (!n || typeof n !== 'object') return '';
        if (n.type === 'text' && typeof n.text === 'string') return n.text;
        return (n.content || []).map(flatAdf).join(' ');
      }
      if (obj.improvedDescriptionAdf && obj.improvedDescriptionAdf.type === 'doc') {
        return flatAdf(obj.improvedDescriptionAdf).replace(/\s+/g, ' ').trim();
      }
    } catch (_) {}
    return s;
  }

  function wrapAdfHtmlWithSections(html) {
    if (!html) return html;
    html = html.replace(/<\/?(p|h2|h3|li|strong|em|td|th)\b[^>]*>/gi, function(match){
      return match
        .replace(/(\u{1F4CB}|\u{1F9E0}|\u{1F4A1}|\u{1F4B0}|\u{1F466}|\u{1F467}|\u{1F468}|\u{1F469}|\u{1F9D1}|\u2705)\s*\1/gu, '$1')
        .replace(/:([a-z0-9_+-]+):/gi, function(_, name){
          var g = JIRA_EMOJI[name.toLowerCase()];
          return g ? g : (':' + name + ':');
        });
    });
    var TAG_NAMES = ['p','h2','h3','hr','ul','ol'];
    var tokens = [];
    for (var i = 0; i < html.length;) {
      if (html.charCodeAt(i) !== 60) { i++; continue; }

      if (html.charCodeAt(i+1) === 47) {
        var nameStart = i + 2;
        var nameEnd = html.indexOf('>', nameStart);
        if (nameEnd < 0) break;
        var tname = html.substring(nameStart, nameEnd).toLowerCase().trim();
        if (TAG_NAMES.indexOf(tname) >= 0) {
          tokens.push({ raw: html.substring(i, nameEnd+1), kind: tname, idx: i, isClose: true });
        }
        i = nameEnd + 1;
        continue;
      }

      var tagStart = i + 1;
      var j = tagStart;
      while (j < html.length && html.charCodeAt(j) !== 62 && !/\s/.test(html.charAt(j))) j++;
      var tagName = html.substring(tagStart, j).toLowerCase();
      if (TAG_NAMES.indexOf(tagName) < 0) { i = tagStart; continue; }

      while (j < html.length && html.charCodeAt(j) !== 62) j++;
      if (j < html.length) j++;
      tokens.push({ raw: html.substring(i, j), kind: tagName, idx: i, isClose: false });
      i = j;
    }

    var nodes = [];
    for (var ti = 0; ti < tokens.length; ti++) {
      var tk = tokens[ti];
      if (tk.isClose) continue;
      if (tk.kind !== 'p' && tk.kind !== 'h2' && tk.kind !== 'h3') continue;
      var startIdx = tk.idx;
      var depth = 1;
      var j = ti + 1;
      while (j < tokens.length && depth > 0) {
        var tk2 = tokens[j];
        if (tk2.kind === tk.kind) {
          if (tk2.isClose) depth--; else depth++;
        }
        j++;
      }
      var endIdxExclusive = (j > 0 && tokens[j-1]) ? (tokens[j-1].idx + tokens[j-1].raw.length) : (startIdx + tk.raw.length);
      while (j < tokens.length && (tokens[j].kind === 'ul' || tokens[j].kind === 'ol')) {
        var listDepth = 0;
        var jj = j;
        while (jj < tokens.length) {
          var lk = tokens[jj].kind;
          var lIsClose = tokens[jj].isClose;
          if (!lIsClose && (lk === 'ul' || lk === 'ol')) listDepth++;
          else if (lIsClose && (lk === 'ul' || lk === 'ol')) {
            listDepth--;
            if (listDepth === 0) { jj++; break; }
          }
          jj++;
        }
        if (jj >= tokens.length) break;
        endIdxExclusive = tokens[jj-1].idx + tokens[jj-1].raw.length;
        j = jj;
      }
      var blockHtml = html.substring(startIdx, endIdxExclusive);
      var blockText = blockHtml.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
      nodes.push({ type: tk.kind, html: blockHtml, text: blockText });
    }

    var EMOJI_TO_SECTION = {
      '\u{1F464}': 'user story',  '\u{1F468}': 'user story',
      '\u{1F469}': 'user story',  '\u{1F9D1}': 'user story',
      '\u{1F9D1}\u200D\u{1F4BB}': 'user story',
      '\u2705': 'acceptance criteria', '\u2611\uFE0F': 'acceptance criteria',
      '\u{1F7E2}': 'acceptance criteria',
      '\u{1F534}': 'background', '\u{1F9E0}': 'background',
      '\u{1F4A1}': 'background',
      '\u{1F3AF}': 'goal',
      '\u{1F517}': 'dependencies',
      '\u26A0\uFE0F': 'preconditions',
    };
    var TEXT_PREFIX_TO_SECTION = [
      { prefix: 'als ',         section: 'anwenderfall' },
      { prefix: 'as a ',        section: 'user story' },
      { prefix: 'as an ',       section: 'user story' },
      { prefix: 'i want ',      section: 'user story' },
      { prefix: 'ich möchte ',  section: 'anwenderfall' },
      { prefix: 'möchte ich ',  section: 'anwenderfall' },
      { prefix: 'möchten wir ', section: 'anwenderfall' },
      { prefix: 'wir möchten ', section: 'anwenderfall' },
      { prefix: 'damit ',       section: 'anwenderfall' },
      { prefix: 'so that ',     section: 'user story' },
      { prefix: 'um ',          section: 'anwenderfall' },
    ];
    var US_CONTINUATION_PREFIX = [
      'möchte ', 'möchten ', 'will ', 'wollen ',
      'i want ', 'i need ', 'so that ', 'damit ', 'um ', 'in order to ',
    ];
    function detectSection(text) {
      var lower = text.toLowerCase();
      for (var emoji in EMOJI_TO_SECTION) {
        if (EMOJI_TO_SECTION.hasOwnProperty(emoji) && text.charAt(0) === emoji) {
          return EMOJI_TO_SECTION[emoji];
        }
      }
      for (var pi = 0; pi < TEXT_PREFIX_TO_SECTION.length; pi++) {
        if (lower.indexOf(TEXT_PREFIX_TO_SECTION[pi].prefix) === 0) {
          return TEXT_PREFIX_TO_SECTION[pi].section;
        }
      }
      var sectionKeys = Object.keys(SP_DOC_SECTIONS);
      for (var ki = 0; ki < sectionKeys.length; ki++) {
        var k = sectionKeys[ki];
        if (lower === k || lower.indexOf(k + ' ') === 0 || lower.indexOf(k + ':') === 0) {
          return k;
        }
      }
      return null;
    }
    var out = [];
    var openSecKey = null;
    function closeSection() {
      if (openSecKey !== null) {
        out.push('</div></div>');
        openSecKey = null;
      }
    }
    function openSection(key) {
      closeSection();
      var meta = SP_DOC_SECTIONS[key];
      if (!meta) return;
      openSecKey = key;
      out.push('<div class="sp-doc-section sp-doc-section--' + meta.cls + '">');
      out.push('<div class="sp-doc-section-head"><i class="sp-doc-icon bi ' + meta.icon + '"></i>');
      out.push('<span class="sp-doc-title">' + escapeHtml(key) + '</span></div>');
      out.push('<div class="sp-doc-section-body">');
    }
    for (var n = 0; n < nodes.length; n++) {
      var node = nodes[n];
      var matchedKey = detectSection(node.text);
      if (matchedKey === null && openSecKey === 'user story') {
        var l = node.text.toLowerCase();
        for (var ci = 0; ci < US_CONTINUATION_PREFIX.length; ci++) {
          if (l.indexOf(US_CONTINUATION_PREFIX[ci]) === 0) { matchedKey = 'user story'; break; }
        }
      }
      if (matchedKey === 'anwenderfall' && openSecKey === 'user story') matchedKey = 'user story';
      if (matchedKey === 'user story' && openSecKey === 'anwenderfall') matchedKey = 'anwenderfall';
      if (matchedKey && matchedKey !== openSecKey) openSection(matchedKey);
      out.push(node.html);
    }
    closeSection();
    return out.join('');
  }

    function renderMarkdown(text) {
    if (!text) return '';
    var lines = String(text).split('\n');
    var html = '';
    var inBullet = false;
    var inOrdered = false;
    var inSection = false;
    function closeLists() {
      if (inBullet)  { html += '</ul>'; inBullet = false; }
      if (inOrdered) { html += '</ol>'; inOrdered = false; }
    }
    function closeSection() {
      closeLists();
      if (inSection) { html += '</div></div>'; inSection = false; }
    }
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var isUl = /^[\-\*] /.test(line);
      var isOl = /^\d+\. /.test(line);
      if (!isUl && inBullet)  { html += '</ul>'; inBullet = false; }
      if (!isOl && inOrdered) { html += '</ol>'; inOrdered = false; }
      if (/^## /.test(line) || /^# /.test(line)) {
        closeSection();
        var hText = line.replace(/^#{1,2} /, '').trim();
        var meta = SP_DOC_SECTIONS[hText.toLowerCase()];
        if (meta) {
          inSection = true;
          html += '<div class="sp-doc-section sp-doc-section--' + meta.cls + '">' +
                  '<div class="sp-doc-section-head"><i class="sp-doc-icon bi ' + meta.icon + '"></i>' +
                  '<span class="sp-doc-title">' + escapeHtml(hText) + '</span></div>' +
                  '<div class="sp-doc-section-body">';
        } else {
          html += '<div class="sp-jira-h2">' + inlineMd(hText) + '</div>';
        }
      } else if (/^### /.test(line)) {
        closeLists();
        html += '<div class="sp-jira-h3">' + inlineMd(line.slice(4).trim()) + '</div>';
      } else if (/^---+$/.test(line.trim())) {
        closeLists();
        html += '<hr class="sp-jira-hr">';
      } else if (/^> /.test(line)) {
        closeLists();
        html += '<div class="sp-jira-bq">' + inlineMd(line.slice(2)) + '</div>';
      } else if (isUl) {
        if (!inBullet) { html += '<ul class="sp-jira-ul">'; inBullet = true; }
        html += '<li>' + inlineMd(line.replace(/^[\-\*] /, '').replace(/^\[[ xX]\] /, '')) + '</li>';
      } else if (isOl) {
        if (!inOrdered) { html += '<ol class="sp-jira-ol">'; inOrdered = true; }
        html += '<li>' + inlineMd(line.replace(/^\d+\. /, '')) + '</li>';
      } else if (line.trim() === '') {
        closeLists();
      } else {
        closeLists();
        html += '<p class="sp-jira-p">' + inlineMd(line) + '</p>';
      }
    }
    closeSection();
    return html;
  }

  function splitMarkdownSections(text) {
    if (!text) return [];
    var lines = String(text).split('\n');
    var sections = [];
    var current = null;
    function push() {
      if (!current) return;
      var allBlank = current.lines.every(function(l){ return l.trim() === ''; });
      if (current.heading || !allBlank) sections.push(current);
    }
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var m = /^#{1,2} (.+?)\s*$/.exec(line);
      if (m) {
        push();
        current = { heading: m[1].trim(), key: m[1].trim().toLowerCase(), lines: [line] };
      } else {
        if (!current) current = { heading: '', key: '', lines: [] };
        current.lines.push(line);
      }
    }
    push();
    return sections.map(function(s){
      return { heading: s.heading, key: s.key, text: s.lines.join('\n') };
    });
  }

  function pairMarkdownSections(origSecs, impSecs) {
    var used = [];
    for (var i = 0; i < impSecs.length; i++) used.push(false);
    var pairs = [];
    origSecs.forEach(function(o){
      var idx = -1;
      for (var i = 0; i < impSecs.length; i++) {
        if (!used[i] && impSecs[i].key === o.key) { idx = i; break; }
      }
      if (idx >= 0) { used[idx] = true; pairs.push({ orig: o, imp: impSecs[idx] }); }
      else pairs.push({ orig: o, imp: null });
    });
    impSecs.forEach(function(im, i){
      if (!used[i]) pairs.push({ orig: null, imp: im });
    });
    return pairs;
  }

  function _diffTokenize(s) {
    return String(s).match(/[A-Za-z\u00C0-\u017F0-9_]+|\s+|[^\sA-Za-z\u00C0-\u017F0-9_]/g) || [];
  }
  function _diffOps(a, b) {
    var n = a.length, m = b.length;
    if (n * m > 400000) return null;
    var dp = [];
    for (var i0 = 0; i0 <= n; i0++) dp.push(new Uint16Array(m + 1));
    for (var i = n - 1; i >= 0; i--) {
      for (var j = m - 1; j >= 0; j--) {
        dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
    var ops = []; var x = 0, y = 0;
    while (x < n && y < m) {
      if (a[x] === b[y]) { ops.push({ op: 'eq', text: a[x] }); x++; y++; }
      else if (dp[x + 1][y] >= dp[x][y + 1]) { ops.push({ op: 'del', text: a[x] }); x++; }
      else { ops.push({ op: 'ins', text: b[y] }); y++; }
    }
    while (x < n) ops.push({ op: 'del', text: a[x++] });
    while (y < m) ops.push({ op: 'ins', text: b[y++] });
    return ops;
  }
  function _wrapDiffRun(s) {

    return s.split('\n').map(function (part) {
      return /\S/.test(part) ? '\u0001D\u0001' + part + '\u0001/D\u0001' : part;
    }).join('\n');
  }
  function diffPairTexts(origText, impText) {
    var ta = _diffTokenize(origText || '');
    var tb = _diffTokenize(impText || '');
    var ops = _diffOps(ta, tb);
    if (!ops) return [origText || '', impText || ''];
    var oOut = '', iOut = '', delBuf = '', insBuf = '';
    function flush() {
      if (delBuf) oOut += _wrapDiffRun(delBuf);
      if (insBuf) iOut += _wrapDiffRun(insBuf);
      delBuf = ''; insBuf = '';
    }
    for (var k = 0; k < ops.length; k++) {
      var op = ops[k];
      if (op.op === 'eq') { flush(); oOut += op.text; iOut += op.text; }
      else if (op.op === 'del') delBuf += op.text;
      else insBuf += op.text;
    }
    flush();
    return [oOut, iOut];
  }
  function applyDiffMarkers(html) {
    if (!html || html.indexOf('\u0001D\u0001') < 0) return html || '';
    return html.replace(/\u0001D\u0001([\s\S]*?)\u0001\/D\u0001/g,
      '<span class="sp-diff">$1</span>');
  }

  return {
    safeImprovedText: safeImprovedText,
    wrapAdfHtmlWithSections: wrapAdfHtmlWithSections,
    splitMarkdownSections: splitMarkdownSections,
    pairMarkdownSections: pairMarkdownSections,
    diffPairTexts: diffPairTexts,
    applyDiffMarkers: applyDiffMarkers,
    renderMarkdown: renderMarkdown,
  };
})();
