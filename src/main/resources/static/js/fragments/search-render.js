window.SuRender = (function () {
  function t(key, fallback) { return (window.slHelpers && window.slHelpers.t) ? window.slHelpers.t(key, fallback) : (fallback || key); }

  function buildResultCard(r) {
    var src = (r.sourceType || '').toUpperCase();
    var isDb = src === 'DATABASE';
    var isJira = src === 'JIRA';
    var isConfluence = src === 'CONFLUENCE';
    var isGithub = src === 'GITHUB';
    var badgeCls   = isJira ? 'su-badge-jira' : isDb ? 'su-badge-db' : isConfluence ? 'su-badge-confluence' : isGithub ? 'su-badge-github' : 'su-badge-gitlab';
    var badgeIcon  = isJira ? 'bi-ticket-detailed' : isDb ? 'bi-database' : isConfluence ? 'bi-journal-text' : isGithub ? 'bi-github' : 'bi-gitlab';
    var _t = function(k, fb) { return (window.i18n && window.i18n.t(k)) || fb; };
    var badgeLabel = _t('enums.source_type.' + src.toLowerCase(), isJira ? 'Jira' : isDb ? 'DB' : isConfluence ? 'Confluence' : isGithub ? 'GitHub' : 'GitLab');
    var titleText = r.title || r.filePath || r.tableName || r.keyword || '–';
    var url = r.repoUrl || r.url || '';
    var titleHtml = url
      ? '<a href="' + escHtml(url) + '" target="_blank" rel="noopener" class="sl-link">' + escHtml(titleText) + '</a>'
      : escHtml(titleText);
    var metaParts = [];
    if (r.keyword)     metaParts.push('<span><i class="bi bi-tag"></i>' + escHtml(r.keyword) + '</span>');
    if (r.repoName)    metaParts.push('<span><i class="bi bi-folder"></i>' + escHtml(r.repoName) + '</span>');
    if (isDb && r.schemaName)   metaParts.push('<span><i class="bi bi-database"></i>' + escHtml(r.schemaName) + '</span>');
    if (r.filePath)    metaParts.push('<span><i class="bi bi-file-earmark-code"></i>' + escHtml(r.filePath) + '</span>');
    if (r.tableName)   metaParts.push('<span><i class="bi bi-table"></i>' + escHtml(r.tableName) + '</span>');
    if (r.columnName)  metaParts.push('<span><i class="bi bi-layout-three-columns"></i>' + escHtml(r.columnName) + '</span>');
    if (r.issueKey)    metaParts.push('<span><i class="bi bi-hash"></i>' + escHtml(r.issueKey) + '</span>');
    if (r.projectKey)  metaParts.push('<span><i class="bi bi-briefcase"></i>' + escHtml(r.projectKey) + '</span>');
    var snippetHtml = r.codeSnippet ? '<div class="su-snippet">' + escHtml(r.codeSnippet) + '</div>' : '';
    return '<div class="su-result">' +
      '<div class="su-result-header">' +
        '<div><div class="su-result-title">' + titleHtml + '</div>' +
        '<div class="su-result-meta">' + metaParts.join('') + '</div></div>' +
        '<span class="su-badge ' + badgeCls + '"><i class="bi ' + badgeIcon + '"></i>' + badgeLabel + '</span>' +
      '</div>' + snippetHtml +
    '</div>';
  }

  function buildJiraGroupedView(results) {
    if (!results || !results.length) return '';
    var projects = {};
    var bugCount = 0, storyCount = 0, taskCount = 0;
    var openCount = 0, closedCount = 0;
    var closedStatuses = ['done', 'abgeschlossen', 'closed', 'resolved', 'fertig', 'erledigt'];
    results.forEach(function(r) {
      var proj = r.repoName || '?';
      if (!projects[proj]) projects[proj] = { results: [], configName: r.configName || '' };
      projects[proj].results.push(r);
      var cls = (r.className || '').toLowerCase();
      if (cls === 'bug') bugCount++;
      else if (cls === 'story') storyCount++;
      else if (cls === 'task') taskCount++;
      var st = (r.functionName || '').toLowerCase();
      if (closedStatuses.indexOf(st) >= 0) closedCount++; else openCount++;
    });
    var projKeys = Object.keys(projects);
    var _tj = function(k, fb) { return (window.i18n && window.i18n.t(k)) || fb; };
    var typeParts = [];
    if (bugCount)   typeParts.push(_tj('suche.jira_bugs', 'Bugs:') + ' <strong>' + bugCount + '</strong>');
    if (storyCount) typeParts.push(_tj('suche.jira_stories', 'Stories:') + ' <strong>' + storyCount + '</strong>');
    if (taskCount)  typeParts.push(_tj('suche.jira_tasks', 'Tasks:') + ' <strong>' + taskCount + '</strong>');
    var stateParts = [];
    if (openCount)   stateParts.push(_tj('suche.jira_open', 'Open:') + ' <strong>' + openCount + '</strong>');
    if (closedCount) stateParts.push(_tj('suche.jira_closed', 'Closed:') + ' <strong>' + closedCount + '</strong>');
    var metaBits = [_tj('suche.jira_projects_label', 'Projects:') + ' <strong>' + escHtml(projKeys.join(', ')) + '</strong>']
      .concat(typeParts).concat(stateParts)
      .join(' <span class="su-jira-sep">|</span> ');
    var _jiraTitle = (_tj('suche.jira_stats_title', '')
      .replace('{{tickets}}', results.length)
      .replace('{{projects}}', projKeys.length))
      || (results.length + ' Jira-Ticket' + (results.length !== 1 ? 's' : '') +
          ' in ' + projKeys.length + ' Projekt' + (projKeys.length !== 1 ? 'en' : '') + ' gefunden');
    var html = '<div class="su-jira-stats">' +
      '<div class="su-jira-stats-title">' + _jiraTitle + '</div>' +
      '<div class="su-jira-stats-meta">' + metaBits + '</div>' +
    '</div>';
    projKeys.forEach(function(proj) {
      var grp = projects[proj];
      var ini = proj.length >= 2 ? proj.slice(0, 2) : proj;
      html += '<div class="su-jira-group">' +
        '<div class="su-jira-group-hdr" onclick="suJiraToggleGroup(this)">' +
          '<div class="su-jira-proj-icon">' + escHtml(ini) + '</div>' +
          '<div style="flex:1;min-width:0">' +
            '<div class="su-jira-proj-name">' + escHtml(proj) + '</div>' +
            (grp.configName ? '<div class="su-jira-proj-sub">' + escHtml(grp.configName) + '</div>' : '') +
          '</div>' +
          '<span class="su-jira-grp-count">' + grp.results.length + ' ' + (grp.results.length !== 1 ? _tj('suche.jira_ticket_pl', 'Tickets') : _tj('suche.jira_ticket_sg', 'Ticket')) + '</span>' +
          '<i class="bi bi-chevron-up su-jira-grp-chev"></i>' +
        '</div>' +
        '<div class="su-jira-grp-body">' + grp.results.map(buildJiraTicketCard).join('') + '</div>' +
      '</div>';
    });
    return html;
  }

  function buildJiraTicketCard(r) {
    var cls = (r.className || '').toLowerCase();
    var tClass = cls === 'bug' ? 't-bug' : cls === 'story' ? 't-story' : cls === 'task' ? 't-task' : cls === 'epic' ? 't-epic' : '';
    var tIcon  = cls === 'bug' ? 'bi-bug-fill' : cls === 'story' ? 'bi-bookmark-fill' : cls === 'task' ? 'bi-check-square-fill' : cls === 'epic' ? 'bi-lightning-fill' : 'bi-ticket-detailed';
    var tColor = cls === 'bug' ? '#b91c1c' : cls === 'story' ? '#27ae60' : cls === 'task' ? '#2980b9' : cls === 'epic' ? '#8e44ad' : '#888';
    var snippet = r.codeSnippet || '';
    var titleM = snippet.match(/^\[(.+?)\]/);
    var title  = titleM ? titleM[1] : (r.filePath || '–');
    var sepIdx = snippet.indexOf(']\n\n');
    var descExcerpt = sepIdx >= 0 ? snippet.slice(sepIdx + 4).trim() : '';
    var status   = r.functionName || '';
    var stLow    = status.toLowerCase();
    var stClass  = 'su-jira-tbadge bst';
    if (/in.?progress|in.?bearbeitung/.test(stLow)) stClass += ' bst-progress';
    else if (/in.?review|in.?prüfung/.test(stLow))  stClass += ' bst-review';
    else if (/done|abgeschlossen|closed|resolved|fertig|erledigt/.test(stLow)) stClass += ' bst-done';
    var priority = r.databaseName || '';
    var prLow    = priority.toLowerCase();
    var prClass  = 'su-jira-tbadge bpri';
    if (/critical|kritisch/.test(prLow))  prClass += ' bpri-critical';
    else if (/high|hoch/.test(prLow))     prClass += ' bpri-high';
    else if (/medium|mittel/.test(prLow)) prClass += ' bpri-medium';
    else if (/low|niedrig/.test(prLow))   prClass += ' bpri-low';
    var assignee = r.dbHost || '';
    var dateStr  = jiraFormatDate(r.schemaName || r.createdAt || '');
    var openUrl  = escHtml(r.repoUrl || '');
    var html = '<div class="su-jira-ticket ' + tClass + '">' +
      '<div class="su-jira-tcontent">' +
        '<div class="su-jira-ttop">' +
          '<div class="su-jira-tleft">' +
            '<div class="su-jira-tkey"><i class="bi ' + tIcon + ' me-1" style="color:' + tColor + '"></i>' + escHtml(r.filePath || '') + '</div>' +
            '<div class="su-jira-ttitle">' + escHtml(title) + '</div>' +
            '<div class="su-jira-tmeta">' +
              (status   ? '<span class="' + stClass + '">' + escHtml(status)   + '</span>' : '') +
              (priority ? '<span class="' + prClass + '">' + escHtml(priority) + '</span>' : '') +
              (assignee ? '<span class="su-jira-tmeta-info"><i class="bi bi-person"></i>' + escHtml(assignee) + '</span>' : '') +
              (dateStr  ? '<span class="su-jira-tmeta-info"><i class="bi bi-calendar3"></i>' + escHtml(dateStr)  + '</span>' : '') +
            '</div>' +
          '</div>' +
          '<div class="su-jira-tright">' +
            (openUrl ? '<button class="su-jira-open-btn" data-url="' + openUrl + '" onclick="event.stopPropagation();openInBrowser(this.dataset.url)" title="' + t('suche.open_in_jira', 'In Jira öffnen') + '"><i class="bi bi-box-arrow-up-right"></i> ' + t('suche.btn_open', 'Öffnen') + '</button>' : '') +
          '</div>' +
        '</div>';
    var fullDesc = r.ddlContext || '';
    if (descExcerpt || fullDesc) {
      html += '<div class="su-jira-desc">' +
        '<div class="su-jira-desc-pre" onclick="suJiraToggleDesc(this)" title="' + (window.i18n ? window.i18n.t('suche.click_for_details') : 'Klicken für Details') + '">' + escHtml(descExcerpt || (window.i18n ? window.i18n.t('suche.view_description') : 'Beschreibung ansehen…')) + '</div>' +
        '<div class="su-jira-desc-exp" data-full="' + escHtml(fullDesc) + '" data-exc="' + escHtml(descExcerpt) + '" onclick="suJiraToggleDesc(this.previousElementSibling)"></div>' +
      '</div>';
    }
    html += '</div></div>';
    return html;
  }

  function suJiraToggleGroup(hdr) {
    var grp = hdr.parentElement;
    if (grp) grp.classList.toggle('su-collapsed');
  }
  window.suJiraToggleGroup = suJiraToggleGroup;

  function suJiraToggleDesc(pre) {
    var exp = pre.nextElementSibling;
    if (!exp) return;
    if (exp.style.display === 'block') {
      exp.style.display = 'none'; pre.style.display = '';
    } else {
      if (!exp.dataset.loaded) {
        var text = exp.getAttribute('data-full') || exp.getAttribute('data-exc') || '';
        exp.textContent = jiraExtractAcOrFirst(text);
        exp.dataset.loaded = '1';
      }
      pre.style.display = 'none'; exp.style.display = 'block';
    }
  }
  window.suJiraToggleDesc = suJiraToggleDesc;

  function jiraExtractAcOrFirst(text) {
    if (!text) return '';
    var clean = text.replace(/\s+/g, ' ').trim();
    var patterns = [
      /akzeptanzkriterien[\s:]/i,
      /acceptance[\s\-]?criteria[\s:]/i,
      /abnahmekriterien[\s:]/i,
      /\baks?\s*:/i,
      /\bacs?\s*:/i
    ];
    for (var i = 0; i < patterns.length; i++) {
      var m = clean.match(patterns[i]);
      if (m) {
        var ac = clean.slice(m.index + m[0].length).trim();
        return ac.length > 600 ? ac.slice(0, 600) + '…' : ac;
      }
    }
    var sent = clean.match(/^.+?[.!?](?:\s|$)/);
    if (sent) return sent[0].trim();
    return clean.slice(0, 300);
  }

  function jiraFormatDate(iso) {
    if (!iso) return '';
    try {
      var d = iso.slice(0, 10).split('-');
      if (d.length !== 3) return '';
      var mo = (function() { var l = document.documentElement.lang || 'de'; return Array.from({length:12},function(_,i){return new Date(2024,i,1).toLocaleString(l,{month:'short'}).replace(/\./g,'');}); })();
      return parseInt(d[2], 10) + '. ' + mo[parseInt(d[1], 10) - 1] + ' ' + d[0];
    } catch(e) { return ''; }
  }

  function openInBrowser(url) {
    if (!url) return;

    if (window.desktopApp && typeof window.desktopApp.openInBrowser === 'function') {
      window.desktopApp.openInBrowser(url);
      return;
    }

    fetch('/api/open-url', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: url })
    }).then(function(r) {
      if (!r.ok) window.open(url, '_blank', 'noopener,noreferrer');
    }).catch(function() {
      window.open(url, '_blank', 'noopener,noreferrer');
    });
  }
  window.openInBrowser = openInBrowser;

  function buildGitlabGroupedView(results, idPrefix) {
    if (!results || !results.length) return '';
    idPrefix = idPrefix || 'gl';
    var repos = {};
    var fnCount = 0, clsCount = 0, fileCount = 0;
    results.forEach(function(r) {
      var repo = r.repoName || '?';
      if (!repos[repo]) repos[repo] = { results: [], configName: r.configName || '', repoUrl: r.repoUrl || '' };
      repos[repo].results.push(r);
      var cls = (r.className || '').toLowerCase();
      var fn  = (r.functionName || '').toLowerCase();
      if (fn)  fnCount++;
      else if (cls) clsCount++;
      else fileCount++;
    });
    var repoKeys = Object.keys(repos);
    var statBits = ['<b>' + repoKeys.length + '</b> ' + (repoKeys.length === 1 ? 'Repository' : 'Repositories')];
    if (fnCount)   statBits.push('<i class="bi bi-braces" style="color:#1a7a3c"></i><b>' + fnCount  + '</b> Funktionen');
    if (clsCount)  statBits.push('<i class="bi bi-box" style="color:#a0521a"></i><b>' + clsCount + '</b> Klassen');
    if (fileCount) statBits.push('<i class="bi bi-file-earmark-code" style="color:#1665c0"></i><b>' + fileCount + '</b> Dateien');
    statBits.push('<span style="margin-left:auto;font-style:italic;font-size:11px">Klick auf Zeile → Code einblenden</span>');
    var html = '<div class="gl-stats-bar">' +
      statBits.map(function(s) { return '<div class="gl-stats-item">' + s + '</div>'; }).join('<span class="gl-stats-sep">|</span>') +
    '</div>';
    var uid = 0;
    repoKeys.forEach(function(repo) {
      var grp = repos[repo];
      var grpId = 'su-' + idPrefix + '-grp-' + (uid++);
      html += '<div class="gl-repo-group" id="' + grpId + '">' +
        '<div class="gl-repo-header" onclick="document.getElementById(\'' + grpId + '\').classList.toggle(\'gl-group-collapsed\')">'+
          '<div class="gl-repo-icon"><i class="bi bi-gitlab"></i></div>' +
          '<div class="gl-repo-info">' +
            '<div class="gl-repo-name">' + escHtml(repo) + '</div>' +
            (grp.configName ? '<div class="gl-repo-path">' + escHtml(grp.configName) + '</div>' : '') +
          '</div>' +
          '<span class="gl-repo-count">' + grp.results.length + ' Treffer</span>' +
          '<i class="bi bi-chevron-down gl-repo-chevron"></i>' +
        '</div>' +
        '<div class="gl-repo-items">' +
          grp.results.map(function(r) { return buildGitlabFileItem(r, idPrefix, uid++); }).join('') +
        '</div>' +
      '</div>';
    });
    return html;
  }

  function buildGitlabFileItem(r, idPrefix, uid) {
    var fid = 'su-' + (idPrefix || 'gl') + '-fi-' + uid;
    var fn  = r.functionName || '';
    var cls = r.className || '';
    var ftIcon, ftClass, ftTitle;
    if (fn)        { ftIcon = 'bi-braces';           ftClass = 'gl-ftype-function'; ftTitle = 'Funktion'; }
    else if (cls)  { ftIcon = 'bi-box';               ftClass = 'gl-ftype-class';    ftTitle = 'Klasse'; }
    else           { ftIcon = 'bi-file-earmark-code'; ftClass = 'gl-ftype-file';     ftTitle = 'Datei'; }
    var fpath = r.filePath || '';
    var lastSlash = fpath.lastIndexOf('/');
    var dir  = lastSlash >= 0 ? escHtml(fpath.slice(0, lastSlash + 1)) : '';
    var fname = lastSlash >= 0 ? escHtml(fpath.slice(lastSlash + 1)) : escHtml(fpath);
    var lineNum   = r.lineNumber ? 'Z. ' + r.lineNumber : '';
    var branch    = r.branch || '';
    var keyword   = r.keyword || '';
    var openUrl   = r.repoUrl || '';
    var snippet   = r.codeSnippet || '';
    var hasSnippet = !!snippet.trim();
    var html = '<div class="gl-file-item" id="' + fid + '">' +
      '<div class="gl-file-header" onclick="document.getElementById(\'' + fid + '\').classList.toggle(\'gl-file-expanded\')">'+
        '<div class="gl-file-type-icon ' + ftClass + '" title="' + ftTitle + '"><i class="bi ' + ftIcon + '"></i></div>' +
        '<div class="gl-file-path">' + dir + '<span class="gl-fname">' + fname + '</span></div>' +
        (lineNum ? '<span class="gl-file-line">' + lineNum + '</span>' : '') +
        (branch  ? '<span class="gl-file-branch"><i class="bi bi-git"></i>' + escHtml(branch) + '</span>' : '') +
        (keyword ? '<span class="gl-file-kw"><i class="bi bi-tag"></i>' + escHtml(keyword) + '</span>' : '') +
        (openUrl ? '<button class="gl-file-open-btn" data-url="' + escHtml(openUrl) + '" onclick="event.stopPropagation();openInBrowser(this.dataset.url)" title="' + t('suche.open_in_browser', 'In Browser öffnen') + '"><i class="bi bi-box-arrow-up-right"></i> ' + t('suche.btn_open', 'Öffnen') + '</button>' : '') +
      '</div>';
    if (hasSnippet) {
      html += '<div class="gl-file-snippet"><div class="gl-snippet-code"><pre>' + escHtml(snippet) + '</pre></div></div>';
    }
    html += '</div>';
    return html;
  }

  function suSwitchTab(tab) {
    document.querySelectorAll('.su-tab').forEach(function(t) { t.classList.toggle('active', t.dataset.tab === tab); });
    document.querySelectorAll('.su-tab-pane').forEach(function(p) { p.classList.remove('active'); });
    var pane = document.getElementById(tab === 'all' ? 'suTabAll' : 'suTab' + tab);
    if (pane) pane.classList.add('active');
  }
  window.suSwitchTab = suSwitchTab;

  function suSetAiResult(text) {
    var content = document.getElementById('suAiContent');
    if (!content) return;
    content.innerHTML = '';

    var copyBar = document.createElement('div');
    copyBar.className = 'su-ai-copy-bar';
    var copyBtn = document.createElement('button');
    copyBtn.type = 'button';
    copyBtn.className = 'su-ai-copy-btn';
    copyBtn.innerHTML = '<i class="bi bi-clipboard me-1"></i>' + (t('suche.ai_copy', 'In Zwischenablage kopieren'));
    copyBtn.onclick = function () {
      try {
        navigator.clipboard.writeText(text).then(function () {
          copyBtn.innerHTML = '<i class="bi bi-clipboard-check me-1"></i>' + (t('suche.ai_copied', 'Kopiert!'));
          copyBtn.classList.add('su-ai-copy-btn--ok');
          setTimeout(function () {
            copyBtn.innerHTML = '<i class="bi bi-clipboard me-1"></i>' + (t('suche.ai_copy', 'In Zwischenablage kopieren'));
            copyBtn.classList.remove('su-ai-copy-btn--ok');
          }, 2000);
        });
      } catch (_) {   }
    };
    copyBar.appendChild(copyBtn);
    content.appendChild(copyBar);

    var body = document.createElement('div');
    body.className = 'su-ai-body';
    body.innerHTML = suRenderAiAnalysis(text);
    content.appendChild(body);
  }

  function suRenderAiAnalysis(md) {
    if (!md) return '';
    var lines = md.split('\n');
    var out = '';
    var i = 0;

    function peek() { return i < lines.length ? lines[i] : null; }
    function take() { return i < lines.length ? lines[i++] : null; }
    function skipBlanks() { while (i < lines.length && lines[i].trim() === '') i++; }
    function esc(s) {
      return (window.slHelpers && window.slHelpers.esc)
        ? window.slHelpers.esc(s)
        : String(s == null ? '' : s)
            .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }
    function inlineFormat(s) {
      s = esc(s);
      s = s.replace(/\*\*\*(.+?)\*\*\*/g,'<strong><em>$1</em></strong>');
      s = s.replace(/\*\*(.+?)\*\*/g,'<strong>$1</strong>');
      s = s.replace(/\*(.+?)\*/g,'<em>$1</em>');
      s = s.replace(/`([^`]+)`/g,'<code class="su-ai-code">$1</code>');
      return s;
    }

    var SECTION_ICONS = {
      'domain': 'bi-diagram-3', 'relationship': 'bi-link-45deg', 'connect': 'bi-arrow-left-right',
      'responsibility': 'bi-person-workspace', 'system': 'bi-diagram-2',
      'uncertainty': 'bi-question-circle', 'assumption': 'bi-question-circle',
      'cross': 'bi-intersect', 'concept': 'bi-intersect', 'confirmation': 'bi-check-circle',
      'overview': 'bi-map', 'summary': 'bi-card-text', 'conclusion': 'bi-award',
      'security': 'bi-shield-check', 'performan': 'bi-speedometer2',
      'recommend': 'bi-lightbulb', 'finding': 'bi-search'
    };
    function sectionIcon(title) {
      var tl = (title || '').toLowerCase();
      for (var k in SECTION_ICONS) { if (tl.indexOf(k) >= 0) return SECTION_ICONS[k]; }
      return 'bi-card-heading';
    }

    function renderTable(tableLines) {
      var rows = tableLines.map(function(l){
        return l.replace(/^\|/,'').replace(/\|$/,'').split('|').map(function(c){ return c.trim(); });
      });
      var isSep = function(r){ return r.every(function(c){ return /^[-:]+$/.test(c); }); };
      var h = '', body = '';
      if (rows.length > 1 && isSep(rows[1])) {
        h = rows[0]; rows = rows.slice(2);
      } else if (rows.length > 0) {
        rows = rows;
      }
      var html = '<div class="su-ai-table-wrap"><table class="su-ai-table">';
      if (h.length) {
        html += '<thead><tr>';
        h.forEach(function(c){ html += '<th>' + inlineFormat(c) + '</th>'; });
        html += '</tr></thead>';
      }
      html += '<tbody>';
      rows.forEach(function(r, ri){
        if (isSep(r)) return;
        html += '<tr>';
        r.forEach(function(c){ html += '<td>' + inlineFormat(c) + '</td>'; });
        html += '</tr>';
      });
      html += '</tbody></table></div>';
      return html;
    }

    function renderList(listLines, ordered) {
      var html = ordered ? '<ol class="su-ai-list">' : '<ul class="su-ai-list">';
      listLines.forEach(function(l){
        var m = l.match(/^(\s*)(?:[-*+]|\d+\.)?\s+(.*)/);
        var text = m ? m[2] : l;
        var indent = m ? Math.floor((m[1]||'').length / 2) : 0;
        if (indent > 0) html += '<li class="su-ai-li su-ai-li--nested">' + inlineFormat(text) + '</li>';
        else html += '<li class="su-ai-li">' + inlineFormat(text) + '</li>';
      });
      html += ordered ? '</ol>' : '</ul>';
      return html;
    }

    function parseAnalysisHeader(line) {
      var m = line.match(/Analysis[:\s]+(.+)/i);
      if (!m) return null;
      return inlineFormat(m[1]);
    }

    skipBlanks();
    var firstLine = peek();
    if (firstLine && /^---/.test(firstLine.trim())) { take(); skipBlanks(); firstLine = peek(); }
    if (firstLine && /Analysis/i.test(firstLine)) {
      var headerTxt = parseAnalysisHeader(take());
      if (headerTxt) {
        out += '<div class="su-ai-header"><i class="bi bi-robot su-ai-header-icon"></i>' + headerTxt + '</div>';
      }
      skipBlanks();
    }

    while (i < lines.length) {
      skipBlanks();
      if (i >= lines.length) break;
      var line = lines[i];
      var trimmed = line.trim();
      if (trimmed === '---' || trimmed === '---\r') { i++; continue; }

      var secMatch = trimmed.match(/^(?:(\d+)\.\s+|#{1,3}\s+)(.+)/);
      if (secMatch) {
        i++;
        var secNum = secMatch[1] || '';
        var secTitle = secMatch[2] || secMatch[0];
        var icon = sectionIcon(secTitle);
        out += '<div class="su-ai-section"><div class="su-ai-section-header">';
        if (secNum) out += '<span class="su-ai-section-num">' + esc(secNum) + '</span>';
        out += '<i class="bi ' + icon + ' su-ai-section-icon"></i>';
        out += '<span class="su-ai-section-title">' + inlineFormat(secTitle) + '</span>';
        out += '</div><div class="su-ai-section-body">';

        skipBlanks();

        var sectionOut = '';
        while (i < lines.length) {
          var sl = lines[i]; var st = sl.trim();
          if (st === '---' || st === '---\r') { i++; continue; }
          if (/^(?:\d+\.\s+|#{1,3}\s+)/.test(st)) break;

          if (st.startsWith('|')) {
            var tbl = [];
            while (i < lines.length && lines[i].trim().startsWith('|')) { tbl.push(lines[i++].trim()); }
            sectionOut += renderTable(tbl);
            continue;
          }

          if (/^[-*+]\s/.test(st) || /^\d+\.\s/.test(st)) {
            var bullets = [], ordered = /^\d+\./.test(st);
            while (i < lines.length && (/^[-*+\s]\s/.test(lines[i].trim()) || /^\d+\.\s/.test(lines[i].trim()))) {
              if (lines[i].trim() === '') { i++; break; }
              bullets.push(lines[i++]);
            }
            sectionOut += renderList(bullets, ordered);
            continue;
          }

          if (/^The following concepts/.test(st)) {
            sectionOut += '<p class="su-ai-intro">' + inlineFormat(st) + '</p>';
            i++;
            continue;
          }

          if (st !== '') {
            sectionOut += '<p class="su-ai-p">' + inlineFormat(st) + '</p>';
          }
          i++;
        }
        out += sectionOut + '</div></div>';
        continue;
      }

      if (trimmed !== '') {

        if (/^\*\*/.test(trimmed) || /^[A-Z][^|]+:$/.test(trimmed)) {
          out += '<p class="su-ai-label-p">' + inlineFormat(trimmed) + '</p>';
        } else {
          out += '<p class="su-ai-p">' + inlineFormat(trimmed) + '</p>';
        }
      }
      i++;
    }

    return out || '<p class="su-ai-p">' + esc(md) + '</p>';
  }


  return {
    buildResultCard: buildResultCard,
    buildJiraGroupedView: buildJiraGroupedView,
    buildJiraTicketCard: buildJiraTicketCard,
    suJiraToggleGroup: suJiraToggleGroup,
    suJiraToggleDesc: suJiraToggleDesc,
    jiraExtractAcOrFirst: jiraExtractAcOrFirst,
    jiraFormatDate: jiraFormatDate,
    openInBrowser: openInBrowser,
    buildGitlabGroupedView: buildGitlabGroupedView,
    buildGitlabFileItem: buildGitlabFileItem,
    suSwitchTab: suSwitchTab,
    suSetAiResult: suSetAiResult,
    suRenderAiAnalysis: suRenderAiAnalysis,
  };
})();
