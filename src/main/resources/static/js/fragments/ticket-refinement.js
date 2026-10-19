if (!window._sp) { window._sp = { gen: 0, runningId: null, lastOpenId: null }; }
window._sp.gen++;

(function(){
  function t(key, fb){ return (window.slHelpers && window.slHelpers.t) ? window.slHelpers.t(key, fb) : (fb || key); }

  var $ = function(id){ return document.getElementById(id); };
  var _spPageGen = window._sp.gen;
  var state = {
    activeId: null,
    pollTimer: null,
    lastRenderedKey: null,
  };

  var safeImprovedText = window.SpMarkdown.safeImprovedText;
  var wrapAdfHtmlWithSections = window.SpMarkdown.wrapAdfHtmlWithSections;
  var splitMarkdownSections = window.SpMarkdown.splitMarkdownSections;
  var pairMarkdownSections = window.SpMarkdown.pairMarkdownSections;
  var diffPairTexts = window.SpMarkdown.diffPairTexts;
  var applyDiffMarkers = window.SpMarkdown.applyDiffMarkers;
  var renderMarkdown = window.SpMarkdown.renderMarkdown;

  var SP_LS_LAYOUT = 'sp.prep.layout';
  var SP_LS_FS     = 'sp.prep.fs';
  function getLayoutPref() {
    var v = window.PageStorage.read(SP_LS_LAYOUT); if (v === 'side' || v === 'stack') return v;
    return 'side';
  }
  function getFsPref() {
    var v = window.PageStorage.read(SP_LS_FS); if (v === 'S' || v === 'M' || v === 'L') return v;
    return 'M';
  }
  function setLayoutPref(v) { window.PageStorage.write(SP_LS_LAYOUT, v); }
  function setFsPref(v)     { window.PageStorage.write(SP_LS_FS, v); }
  function applyResultsPrefs() {
    var box = $('spResults');
    if (!box) return;
    var layout = getLayoutPref();
    var fs = getFsPref();
    box.classList.remove('sp-layout-side','sp-layout-stack');
    box.classList.add(layout === 'stack' ? 'sp-layout-stack' : 'sp-layout-side');
    box.classList.remove('sp-fs-s','sp-fs-m','sp-fs-l');
    box.classList.add('sp-fs-' + fs.toLowerCase());
    syncToolbarsState(box);
  }
  function syncToolbarsState(scope) {
    var layout = getLayoutPref();
    var fs = getFsPref();
    [].slice.call(scope.querySelectorAll('[data-sp-toggle="layout"] button')).forEach(function(b){
      b.classList.toggle('on', b.getAttribute('data-sp-layout') === layout);
    });
    [].slice.call(scope.querySelectorAll('[data-sp-toggle="fs"] button')).forEach(function(b){
      b.classList.toggle('on', b.getAttribute('data-sp-fs') === fs);
    });
  }

  var JIRA_EMOJI = {
    'thumbsup': '\uD83D\uDC4D', 'thumbs_up': '\uD83D\uDC4D', '+1': '\uD83D\uDC4D',
    'thumbsdown': '\uD83D\uDC4E', '-1': '\uD83D\uDC4E',
    'wave': '\uD83D\uDC4B', 'hand': '\uD83D\uDC4B', 'raised_hand': '\uD83D\uDC4B',
    'smile': '\uD83D\uDE04', 'smiley': '\uD83D\uDE03',
    'frowning_face': '\uD83D\uDE26', 'sad': '\uD83D\uDE1E',
    'check': '✅', 'check_mark': '✅', 'white_check_mark': '✅',
    'x': '❌', 'cross_mark': '❌', 'negative_squared_cross_mark': '❌',
    'warning': '⚠️', 'exclamation': '❗',
    'star': '⭐', 'yellow_star': '⭐',
    'fire': '🔥', 'rocket': '🚀', 'bulb': '💡', 'light_bulb': '💡',
    'bulb_on': '💡',
    'eyes': '👀', 'see_no_evil': '🙈',
    'thinking': '🤔', 'thought_balloon': '💭',
    'sparkles': '✨', 'tada': '🎉', 'rocket_tada': '🚀🎉',
    'bug': '🐛', 'beetle': '🐛',
    'wrench': '🔧', 'hammer': '🔨', 'hammer_and_wrench': '🛠',
    'gear': '⚙️', 'construction': '🚧',
    'lock': '🔒', 'unlock': '🔓', 'key': '🔑',
    'mag': '🔍', 'mag_right': '🔎', 'search': '🔍',
    'bulb_idea': '💡', 'idea': '💡',
    'calendar': '📅', 'date': '📅', 'clock': '🕐',
    'hourglass': '⏳', 'timer': '⏱',
    'books': '📚', 'book': '📖', 'bookmark': '🔖',
    'memo': '📝', 'pencil': '✏️', 'pencil2': '✏️',
    'memo_alt': '📝', 'notebook': '📓', 'ledger': '📒',
    'file_folder': '📁', 'open_file_folder': '📂', 'closed_lock_with_key': '🔐',
    'clipboard': '📋', 'card_index': '📇', 'card_file_box': '🗃',
    'speech_balloon': '💬', 'thought_balloon_alt': '💭',
    'zap': '⚡', 'boom': '💥', 'collision': '💥',
    'tada_emoji': '🎉',
    'white_circle': '⚪', 'black_circle': '⚫', 'red_circle': '🔴',
    'large_blue_circle': '🔵', 'large_orange_diamond': '🔶',
    'small_red_triangle': '🔺', 'small_red_triangle_down': '🔻',
    'iphone': '📱', 'computer': '💻', 'keyboard': '⌨',
    'package': '📦', 'gift': '🎁', 'trophy': '🏆', 'medal': '🏅',
    'dart': '🎯', 'target': '🎯',
    'muscle': '💪', 'flexed_biceps': '💪',
    'brain': '🧠', 'gear_alt': '⚙️',
    'robot': '🤖', 'robot_face': '🤖',
    'link': '🔗', 'paperclip': '📎', 'paperclips': '🖇',
    'lock_with_ink_pen': '🔏', 'closed_lock_with_key_alt': '🔐',
    'information_source': 'ℹ️', 'grey_exclamation': '❕',
    'bangbang': '‼️', 'interrobang': '⁉️',
    'ok_hand': '👌', 'thumbsup_alt': '👍',
    'pray': '🙏', 'point_up': '☝️', 'point_right': '👉', 'point_left': '👈',
    'point_down': '👇',
    'crown': '👑', 'gem': '💎', 'ring': '💍',
    'heart': '❤️', 'broken_heart': '💔', 'two_hearts': '💕',
    '100': '💯',
  };


  function loadJiraConfigs(){
    var _gen = _spPageGen;
    return fetch('/api/config/jira', { credentials:'same-origin' })
      .then(function(r){ return r.ok ? r.json() : []; })
      .catch(function(){ return []; })
      .then(function(list){
        if (window._sp.gen !== _gen) return;
        var sel = $('spJiraConfig');
        if (!sel) return;
        sel.innerHTML = '';
        (list || []).filter(function(c){ return c.active; }).forEach(function(c){
          var o = document.createElement('option');
          o.value = c.id;
          o.textContent = c.name + ' — ' + c.baseUrl;
          o.setAttribute('data-baseurl', c.baseUrl || '');
          o.setAttribute('data-name', c.name || '');
          sel.appendChild(o);
        });
        applyDemoAutoFill();
      });
  }

  function loadDbConfigs(){
    return fetch('/api/config/database', { credentials:'same-origin' })
      .then(function(r){ return r.ok ? r.json() : []; })
      .catch(function(){ return []; })
      .then(function(list){
        var container = $('spDatabaseMultiSelect');
        if (!container) return;
        container.innerHTML = '';
        var active = (list || []).filter(function(c){ return c.active !== false; });
        var hint = $('spNoDbHint');
        if (!active.length) {
          if (hint) hint.style.display = '';
        } else {
          if (hint) hint.style.display = 'none';
          active.forEach(function(db){
            var chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'sp-db-chip';
            chip.setAttribute('data-db-id', db.id);
            chip.setAttribute('data-db-name', db.name + ' (' + (db.host || '') + ')');
            chip.innerHTML = '<i class="bi bi-database" style="font-size:11px;margin-right:4px;"></i>' + escapeHtml(db.name + ' (' + (db.host || '') + ')');
            chip.style.cssText = 'display:inline-flex;align-items:center;border:1px solid var(--sp-line);border-radius:999px;padding:3px 10px;font-size:11px;cursor:pointer;background:var(--sp-bg);color:var(--sp-text);transition:background .15s,color .15s;';
            chip.addEventListener('click', function(){
              var sel = chip.classList.toggle('selected');
              chip.style.background = sel ? 'var(--sp-accent)' : '';
              chip.style.color = sel ? '#fff' : '';
              chip.style.borderColor = sel ? 'var(--sp-accent)' : '';
            });
            container.appendChild(chip);
          });
        }
      });
  }

  function loadRepoConfigs(){
    var _gen = _spPageGen;
    return fetch('/api/local-repos', { credentials:'same-origin' })
      .then(function(r){ return r.ok ? r.json() : []; }).catch(function(){ return []; })
      .then(function(list){
        if (window._sp.gen !== _gen) return;
        var hint = $('spNoRepoHint');
        var active = (list || []).filter(function(r){ return r.active !== false && r.scope === 'TICKET_REFINEMENT'; });
        if (!active.length) {
          if (hint) hint.style.display = '';
          return;
        }
        if (hint) hint.style.display = 'none';
        active.forEach(function(repo){ spAddLocalRepo(repo); });
      });
  }

  function spAddLocalRepo(repo){
    var hint = $('spNoRepoHint');
    if (hint) hint.style.display = 'none';
    var card = document.createElement('div');
    card.className = 'sp-local-repo-card';
    card.setAttribute('data-repo-id', String(repo.id));
    card.innerHTML =
      '<span class="sp-local-repo-checkbox"><i class="bi bi-circle sp-local-repo-cb-icon"></i></span>' +
      '<i class="bi bi-folder2 sp-local-repo-icon"></i>' +
      '<div style="min-width:0;flex:1;">' +
        '<div style="display:flex;align-items:center;gap:4px;min-width:0;">' +
          '<div class="sp-local-repo-name">' + escapeHtml(repo.name) + '</div>' +
          '<button type="button" class="sp-local-repo-del" title="' +
            escapeHtml(t('ticket_refinement.repo_remove_tooltip', 'Repository entfernen')) +
            '">' + escapeHtml(t('ticket_refinement.repo_remove_label', 'Entfernen')) + '</button>' +
        '</div>' +
        '<div class="sp-local-repo-path">' + escapeHtml(repo.path || '') + '</div>' +
      '</div>';
    card.querySelector('.sp-local-repo-del').addEventListener('click', function(e){
      spDeleteLocalRepo(e, this, repo.id);
    });
    card.addEventListener('click', function(e){
      if (!e.target.closest('.sp-local-repo-del')) spToggleLocalRepo(card);
    });
    $('spRepoList').appendChild(card);
  }

  function spToggleLocalRepo(card){
    var isSelected = card.classList.toggle('selected');
    var cbIcon = card.querySelector('.sp-local-repo-cb-icon');
    var folderIcon = card.querySelector('.sp-local-repo-icon');
    if (cbIcon) cbIcon.className = 'bi ' + (isSelected ? 'bi-check-circle-fill' : 'bi-circle') + ' sp-local-repo-cb-icon';
    if (folderIcon) folderIcon.className = 'bi ' + (isSelected ? 'bi-folder2-open' : 'bi-folder2') + ' sp-local-repo-icon';
  }

  function spDeleteLocalRepo(e, btn, id){
    e.stopPropagation();
    fetch('/api/local-repos/' + id, { method:'DELETE' }).then(function(){
      var card = btn.closest('.sp-local-repo-card');
      if (card) card.remove();
      if (!document.querySelectorAll('#spRepoList .sp-local-repo-card').length){
        var hint = $('spNoRepoHint');
        if (hint) hint.style.display = '';
      }
    });
  }

  function collectRepoSelections(){
    var out = [];
    [].slice.call(document.querySelectorAll('#spRepoList .sp-local-repo-card.selected')).forEach(function(card){
      out.push({ type:'local', id:Number(card.getAttribute('data-repo-id')) });
    });
    return out;
  }

  function spRepoFbOpen(){
    __kiChatOpenDirBrowser(function(path){
      if (!path) return;
      var name = path.replace(/\\/g, '/').split('/').filter(Boolean).pop() || path;
      fetch('/api/local-repos', {
        method: 'POST',
        headers: { 'Content-Type':'application/json' },
        body: JSON.stringify({ id:null, name:name, path:path, active:true, scope:'TICKET_REFINEMENT' })
      }).then(function(r){ return r.json(); }).then(function(data){
        if (data.error){ showAlert(data.error, 'danger'); return; }
        spAddLocalRepo(data);
      });
    });
  }

  window.spRepoFbOpen          = spRepoFbOpen;

  var DEMO_PARENT_KEY = 'SPRINT-100';
  function applyDemoAutoFill(){
    var sel = $('spJiraConfig');
    if (!sel) return;
    var opt = sel.options[sel.selectedIndex];
    if (!opt) return;
    var baseUrl    = opt.getAttribute('data-baseurl') || '';
    var configName = (opt.getAttribute('data-name') || '').toLowerCase();
    var isDemo = baseUrl.indexOf('localhost') !== -1 || configName.indexOf('demo') !== -1;
    var input = $('spParentKey');
    if (!input || !isDemo) return;
    var current = input.value.trim();
    if (!current || /^[A-Z]+-\d+$/.test(current)) {
      input.value = DEMO_PARENT_KEY;
    }
  }

  function spDeleteAllHistory(){
    fetch('/api/ticket-refinement', { method:'DELETE' }).then(function(){
      state.activeId = null;
      window._sp.runningId = null;
      $('spResultsCard').style.display = 'none';
      var pc = $('spProgressCard'); if (pc) pc.style.display = 'none';
      loadHistory();
    });
  }

  var _spHistory = null;
  var _spHistoryActiveId = null;

  function spHistory() {
    if (_spHistory) return _spHistory;
    _spHistory = window.slHistory.mount({
      root: 'hxCard-ticket-refinement',
      page: 'ticket-refinement',
      titleKey: 'ticket_refinement.history_heading',
      emptyKey: 'ticket_refinement.no_results',
      columns: {
        title: { key: 'ticket_refinement.col_parent', fallback: 'Eltern-Ticket' }
      },
      map: {
        id: function(item) { return item.id; },
        status: function(item) { return item.status; },
        createdAt: function(item) { return item.createdAt; },
        typeIcon: function() { return 'bi-diagram-2'; },
        title: function(item) { return item.parentTicketKey || ''; },
        isCurrent: function(item) { return Number(_spHistoryActiveId) === Number(item.id); }
      },
      actions: {
        open: function(item) { openPreparation(item.id); },
        cancel: function(item) { spCancelHistoryItem(item.id); },
        remove: function(item) { spDeleteHistoryItem(item.id); },
        deleteAll: function() { spDeleteAllHistory(); }
      }
    });
    return _spHistory;
  }

  function spCancelHistoryItem(id) {
    fetch('/api/ticket-refinement/' + id + '/cancel', { method:'POST', credentials:'same-origin' })
      .then(function(){ loadHistory(); })
      .catch(function(){ loadHistory(); });
  }

  function spDeleteHistoryItem(id) {
    fetch('/api/ticket-refinement/' + id, { method:'DELETE' }).then(function(){
      if (Number(state.activeId) === Number(id)) {
        state.activeId = null;
        window._sp.runningId = null;
        $('spResultsCard').style.display = 'none';
        var pc = $('spProgressCard'); if (pc) pc.style.display = 'none';
      }
      loadHistory();
    });
  }

  function loadHistory(selectId){
    if (selectId != null) _spHistoryActiveId = selectId;
    var _lhGen = _spPageGen;
    return fetch('/api/ticket-refinement', { credentials:'same-origin' })
      .then(function(r){ return r.ok ? r.json() : []; })
      .then(function(list){
        if (window._sp.gen !== _lhGen) return [];
        spHistory().setData(Array.isArray(list) ? list : []);
        return list;
      });
  }

  function openPreparation(id){
    state.activeId = id;
    window._sp.lastOpenId = id;
    if (typeof spInitTokenUsageBar === 'function') spInitTokenUsageBar();
    state.lastRenderedKey = null;
    if (state.pollTimer) { clearInterval(state.pollTimer); state.pollTimer = null; }

    var pc = $('spProgressCard');
    if (pc) pc.style.display = '';

    var cb = $('spCancel');
    if (cb) cb.disabled = false;

    var sb = $('spStart');
    if (sb) sb.disabled = true;
    fetchAndRender(id);
    state.pollTimer = setInterval(function(){

      if (window._sp.gen !== _spPageGen) {
        clearInterval(state.pollTimer); state.pollTimer = null; return;
      }
      fetchAndRender(id, true);
    }, 2500);
    _spHistoryActiveId = id;
    if (_spHistory) _spHistory.refresh();
  }

  function fetchAndRender(id, polling){
    if (id === null || id === undefined || id === '' || id === 'null') return;
    fetch('/api/ticket-refinement/' + id, { credentials:'same-origin' })
      .then(function(r){ return r.ok ? r.json() : null; })
      .then(function(data){
        if (!data) return;

        if (window._sp.gen !== _spPageGen) return;
        if (Number(state.activeId) !== Number(id)) return;
        renderDetail(data);
        if (data.status === 'COMPLETED' || data.status === 'FAILED' || data.status === 'CANCELLED') {
          if (state.pollTimer) { clearInterval(state.pollTimer); state.pollTimer = null; }
          window._sp.runningId = null;
          loadHistory(id);
        }
      });
  }

  function renderRepoAnalyses(analyses){
    if (!analyses || !analyses.length) return '';
    var heading = escapeHtml(t('ticket_refinement.label_repo_analysis', 'Repository-Analyse'));
    var html = '<div class="sp-section-label sp-section-label--repo">' + heading + '</div>';
    html += '<div class="sp-repo-analyses">';
    analyses.forEach(function(a){
      var stateLabelKey = ({
        OK: 'ticket_refinement.repo_state_ok',
        SKIPPED: 'ticket_refinement.repo_state_skipped',
        FAILED: 'ticket_refinement.repo_state_failed'
      })[a.state] || 'ticket_refinement.repo_state_ok';
      var stateFb = ({ OK:'Relevant', SKIPPED:'Übersprungen', FAILED:'Fehler' })[a.state] || a.state;
      html += '<div class="sp-repo-analysis sp-repo-analysis--' + escapeHtml((a.state || 'OK').toLowerCase()) + '">';
      html += '  <div class="sp-repo-analysis-head">';
      html += '    <span class="sp-repo-analysis-name">' + escapeHtml(a.repoName || '') +
              ' <small class="sp-repo-analysis-type">(' + escapeHtml(a.repoType || '') + ')</small></span>';
      html += '    <span class="sp-state ' + escapeHtml(a.state || 'OK') + '">' + escapeHtml(t(stateLabelKey, stateFb)) + '</span>';
      if (a.state === 'OK' && a.complexity) {
        var cxLabel = ({ S: t('ticket_refinement.repo_complexity_s','S – Klein'), M: t('ticket_refinement.repo_complexity_m','M – Mittel'), X: t('ticket_refinement.repo_complexity_x','X – Komplex') })[a.complexity] || a.complexity;
        html += '    <span class="sp-repo-complexity sp-repo-complexity--' + escapeHtml(a.complexity.toLowerCase()) + '" title="' + escapeHtml(t('ticket_refinement.repo_complexity_label','Komplexität')) + '">' + escapeHtml(cxLabel) + '</span>';
      }
      html += '  </div>';
      if (a.state === 'FAILED') {
        html += '  <div class="sp-block">' + escapeHtml(a.errorMessage || a.summary || '') + '</div>';
      } else if (a.relevant === false) {
        var skip = a.summary && a.summary.trim()
          ? a.summary
          : t('ticket_refinement.repo_skipped_no_match', 'Keine Verbindung zwischen Ticket und Repository erkannt.');
        html += '  <div class="sp-block sp-block--muted">' + escapeHtml(skip) + '</div>';
      } else {
        if (a.summary && a.summary.trim()) {
          html += '  <div class="sp-block">' + escapeHtml(a.summary) + '</div>';
        }
        if ((a.suggestedChanges || []).length) {
          html += '  <div class="sp-section-sub">' + escapeHtml(t('ticket_refinement.label_repo_changes','Erforderliche Änderungen')) + '</div>';
          html += '  <ul class="sp-points">' + a.suggestedChanges.map(function(c){ return '<li>' + escapeHtml(c) + '</li>'; }).join('') + '</ul>';
        }
        if ((a.dependencies || []).length) {
          html += '  <div class="sp-section-sub">' + escapeHtml(t('ticket_refinement.label_repo_dependencies','Abhängigkeiten / Vorarbeiten')) + '</div>';
          html += '  <ul class="sp-points">' + a.dependencies.map(function(d){ return '<li>' + escapeHtml(d) + '</li>'; }).join('') + '</ul>';
        }
        if ((a.hiddenDependencies || []).length) {
          html += '  <div class="sp-section-sub">' + escapeHtml(t('ticket_refinement.label_repo_hidden_deps','Versteckte Abhängigkeiten')) + '</div>';
          html += '  <ul class="sp-points">' + a.hiddenDependencies.map(function(d){ return '<li>' + escapeHtml(d) + '</li>'; }).join('') + '</ul>';
        }
        if (!(a.suggestedChanges || []).length && !(a.dependencies || []).length &&
            !(a.hiddenDependencies || []).length && !(a.summary || '').trim()) {
          html += '  <div class="sp-block sp-block--muted">' + escapeHtml(t('ticket_refinement.repo_no_changes_needed','Keine konkreten Änderungen identifiziert.')) + '</div>';
        }
      }
      html += '</div>';
    });
    html += '</div>';
    return html;
  }

  function renderSummary(reviews) {
    if (!reviews || reviews.length === 0) return '';
    var total    = reviews.length;
    var okCount  = reviews.filter(function(r){ return r.state === 'OK'; }).length;
    var warnCount= reviews.filter(function(r){ return r.state === 'NEEDS_REVIEW'; }).length;
    var failCount= reviews.filter(function(r){ return r.state === 'FAILED'; }).length;

    var html = '<div class="sp-summary">';
    html += '<div class="sp-summary-head">';
    html += '<span class="sp-summary-title">📋 ' + escapeHtml(t('ticket_refinement.summary_heading', 'Zusammenfassung')) + '</span>';
    html += '<div class="sp-summary-stats">';
    html += '<span class="sp-summary-stat">' + total + ' ' + escapeHtml(t('ticket_refinement.summary_tickets_processed', 'Tickets')) + '</span>';
    if (okCount)   html += '<span class="sp-summary-stat sp-summary-stat--ok">'   + okCount   + ' ' + escapeHtml(t('ticket_refinement.ticket_state_ok',         'OK'))            + '</span>';
    if (warnCount) html += '<span class="sp-summary-stat sp-summary-stat--warn">' + warnCount + ' ' + escapeHtml(t('ticket_refinement.ticket_state_needs_review','Offene Punkte')) + '</span>';
    if (failCount) html += '<span class="sp-summary-stat sp-summary-stat--fail">' + failCount + ' ' + escapeHtml(t('ticket_refinement.ticket_state_failed',      'Fehler'))        + '</span>';
    html += '</div></div>';

    html += '<table class="sp-summary-table">';
    html += '<thead><tr>';
    html += '<th>' + escapeHtml(t('ticket_refinement.summary_col_ticket',      'Ticket'))        + '</th>';
    html += '<th>' + escapeHtml(t('ticket_refinement.summary_col_status',      'Status'))        + '</th>';
    html += '<th>' + escapeHtml(t('ticket_refinement.summary_col_open_points', 'Offene Punkte')) + '</th>';
    html += '<th>' + escapeHtml(t('ticket_refinement.summary_col_comment',     'Kommentar'))     + '</th>';
    html += '</tr></thead><tbody>';

    reviews.forEach(function(r) {
      var stateLabelKey = ({
        OK:           'ticket_refinement.ticket_state_ok',
        NEEDS_REVIEW: 'ticket_refinement.ticket_state_needs_review',
        FAILED:       'ticket_refinement.ticket_state_failed'
      })[r.state] || 'ticket_refinement.ticket_state_ok';
      var stateFb = ({ OK:'OK', NEEDS_REVIEW:'Offene Punkte', FAILED:'Fehler' })[r.state] || r.state;

      var keyHtml = r.ticketUrl
        ? '<a href="' + escapeHtml(r.ticketUrl) + '" target="_blank" rel="noopener">' + escapeHtml(r.ticketKey) + '</a>'
        : escapeHtml(r.ticketKey);

      var origSummary = r.originalSummary || '';
      var displaySummary = origSummary.length > 70 ? origSummary.slice(0, 67) + '…' : origSummary;
      var titleCell = keyHtml;
      if (displaySummary) {
        titleCell += '<span class="sp-summary-ticket-summary"> — ' + escapeHtml(displaySummary) + '</span>';
      }
      var impTitle = (r.improvedSummary || '').trim();
      if (impTitle && impTitle !== origSummary.trim()) {
        titleCell += '<div class="sp-summary-improved-title" title="' +
          escapeHtml(t('ticket_refinement.summary_title_improved', 'Verbesserter Titel')) +
          '">→ ' + escapeHtml(impTitle) + '</div>';
      }

      var opCount = (r.openPoints || []).length;
      var opCell = opCount > 0
        ? '<span class="sp-summary-count sp-summary-count--warn">' + opCount + '</span>'
        : '<span class="sp-summary-none">–</span>';

      var commentCell = r.commentPosted
        ? '<span class="sp-summary-posted">✓</span>'
        : '<span class="sp-summary-none">–</span>';

      html += '<tr>';
      html += '<td class="sp-summary-cell sp-summary-cell--ticket">' + titleCell + '</td>';
      html += '<td class="sp-summary-cell"><span class="sp-state ' + escapeHtml(r.state) + '">' + escapeHtml(t(stateLabelKey, stateFb)) + '</span></td>';
      html += '<td class="sp-summary-cell sp-summary-cell--center">' + opCell + '</td>';
      html += '<td class="sp-summary-cell sp-summary-cell--center">' + commentCell + '</td>';
      html += '</tr>';
    });

    html += '</tbody></table></div>';
    return html;
  }

  function renderDetail(data){
    var status = $('spStatus');
    if (!status) return;
    window._sp.lastDetail = data;
    var statusText = '';
    if (data.status === 'RUNNING' || data.status === 'PENDING') {
      statusText = (data.currentStep || t('ticket_refinement.step_fetch_parent','Lädt…'));
    } else if (data.status === 'COMPLETED') {
      statusText = t('ticket_refinement.step_done', 'Fertig.');
    } else if (data.status === 'FAILED') {
      statusText = '⚠ ' + (data.errorMessage || t('common.error','Fehler'));
    } else if (data.status === 'CANCELLED') {
      statusText = '⛔ ' + t('ticket_refinement.step_cancelled', 'Abgebrochen.');
    }

    var dataKey = data.status + '|' + (data.currentStep || '') + '|' +
                  (data.reviewsJson || '') + '|' + (data.errorMessage || '');
    var statusOnly = state.lastRenderedKey !== null &&
                     dataKey === state.lastRenderedKey;
    if (statusOnly) {

      status.textContent = statusText;
      status.className = 'sp-status' + (data.status === 'FAILED' ? ' error' : '') + (data.status === 'CANCELLED' ? ' cancelled' : '');

      var ps = $('spProgressStep');
      if (ps) ps.textContent = (data.status === 'RUNNING' || data.status === 'PENDING') ? statusText : '';

      var cb = $('spCancel');
      if (cb) cb.disabled = !(data.status === 'RUNNING' || data.status === 'PENDING');

      var sb = $('spStart');
      if (sb) sb.disabled = (data.status === 'RUNNING' || data.status === 'PENDING');
      return;
    }
    state.lastRenderedKey = dataKey;

    status.textContent = statusText;
    status.className = 'sp-status' + (data.status === 'FAILED' ? ' error' : '') + (data.status === 'CANCELLED' ? ' cancelled' : '');

    var progressCard = $('spProgressCard');
    var progressStep = $('spProgressStep');
    if (progressCard) {
      progressCard.style.display = (data.status === 'RUNNING' || data.status === 'PENDING') ? '' : 'none';
    }
    if (progressStep) {
      progressStep.textContent = (data.status === 'RUNNING' || data.status === 'PENDING')
        ? (data.currentStep || t('ticket_refinement.step_fetch_parent', 'Lädt…'))
        : '';
    }

    var resultsCard = $('spResultsCard');
    if (resultsCard) {
      resultsCard.style.display = (data.status === 'COMPLETED' || data.status === 'FAILED') ? '' : 'none';
    }

    var cancelBtn = $('spCancel');
    if (cancelBtn) {
      cancelBtn.disabled = !(data.status === 'RUNNING' || data.status === 'PENDING');
    }

    var startBtn = $('spStart');
    if (startBtn) {
      startBtn.disabled = (data.status === 'RUNNING' || data.status === 'PENDING');
    }

    var reviews = [];
    try { reviews = data.reviewsJson ? JSON.parse(data.reviewsJson) : []; } catch(_){ reviews = []; }
    var box = $('spResults');
    if (!reviews.length) {
      box.innerHTML = data.status === 'COMPLETED'
        ? '<p class="sp-empty">' + escapeHtml(t('ticket_refinement.no_results','Noch keine Ergebnisse vorhanden.')) + '</p>'
        : '';
      return;
    }
    box.innerHTML = reviews.map(function(r){
      var url = r.ticketUrl ? '<a href="' + escapeHtml(r.ticketUrl) + '" target="_blank" rel="noopener">' + escapeHtml(r.ticketKey) + '</a>'
                            : escapeHtml(r.ticketKey);
      var stateLabelKey = ({
        OK: 'ticket_refinement.ticket_state_ok',
        NEEDS_REVIEW: 'ticket_refinement.ticket_state_needs_review',
        FAILED: 'ticket_refinement.ticket_state_failed'
      })[r.state] || 'ticket_refinement.ticket_state_ok';
      var stateFb = ({ OK:'OK', NEEDS_REVIEW:'Offene Punkte', FAILED:'Fehler' })[r.state] || r.state;
      var ticketHtml =
        '<div class="sp-ticket" data-key="' + escapeHtml(r.ticketKey) + '">' +
        '  <div class="sp-ticket-head">' +
        '    <span class="sp-ticket-key">' + url + ' — ' + escapeHtml(r.originalSummary || '') + '</span>' +
        '    <span class="sp-state ' + escapeHtml(r.state) + '">' + escapeHtml(t(stateLabelKey, stateFb)) + '</span>' +
        '  </div>';
      if (r.state === 'FAILED') {
        ticketHtml += '<div class="sp-block">' + escapeHtml(r.errorMessage || '') + '</div>';
      } else {
        var origRaw  = r.originalDescription || '';
        var impRaw   = safeImprovedText(r.improvedDescription || '');
        var origHtml = wrapAdfHtmlWithSections(r.originalDescriptionHtml || renderMarkdown(origRaw));
        var impHtml  = wrapAdfHtmlWithSections(r.improvedDescriptionHtml || renderMarkdown(impRaw));
        var canPair = !r.originalDescriptionHtml && !r.improvedDescriptionHtml;
        var hasOrigAdfHtml = !!r.originalDescriptionHtml;
        var hasImpAdfHtml  = !!r.improvedDescriptionHtml;
        var origHtmlDiff = origHtml;
        var impHtmlDiff  = impHtml;
        if (!hasOrigAdfHtml && !hasImpAdfHtml && origRaw && impRaw) {
          var dFull = diffPairTexts(origRaw, impRaw);
          origHtmlDiff = applyDiffMarkers(renderMarkdown(dFull[0]));
          impHtmlDiff  = applyDiffMarkers(renderMarkdown(dFull[1]));
        }

        if (hasOrigAdfHtml && !hasImpAdfHtml) {
          impHtmlDiff = applyDiffMarkers(renderMarkdown(impRaw));
        }
        if (!hasOrigAdfHtml && hasImpAdfHtml) {
          origHtmlDiff = applyDiffMarkers(renderMarkdown(origRaw));
        }

        var pairsHtml = '';
        if (canPair) {
          var origSecs = splitMarkdownSections(origRaw);
          var impSecs  = splitMarkdownSections(impRaw);
          if (origSecs.length || impSecs.length) {
            var pairs = pairMarkdownSections(origSecs, impSecs);
            pairsHtml = pairs.map(function(p){
              var lh = '', rh = '';
              if (p.orig && p.imp) {
                var d = diffPairTexts(p.orig.text, p.imp.text);
                lh = applyDiffMarkers(renderMarkdown(d[0]));
                rh = applyDiffMarkers(renderMarkdown(d[1]));
              } else {
                lh = p.orig ? renderMarkdown(p.orig.text) : '';
                rh = p.imp  ? renderMarkdown(p.imp.text)  : '';
              }
              var lcls = 'sp-pair-cell sp-pair-cell--orig sp-block--adf' + (p.orig ? '' : ' sp-pair-cell--empty');
              var rcls = 'sp-pair-cell sp-pair-cell--imp sp-block--adf'  + (p.imp  ? '' : ' sp-pair-cell--empty');
              return '<div class="' + lcls + '">' + lh + '</div>' +
                     '<div class="' + rcls + '">' + rh + '</div>';
            }).join('');
          }
        }

        var origHeadHtml =
          '<div class="sp-col-head sp-col-head--orig">' +
          '  <span>📄 ' + escapeHtml(t('ticket_refinement.label_original_description','Originale Beschreibung')) + '</span>' +
          '  <span class="sp-col-pill">' + escapeHtml(t('ticket_refinement.col_pill_jira','aus Jira')) + '</span>' +
          '</div>';
        var impHeadHtml =
          '<div class="sp-col-head sp-col-head--imp">' +
          '  <span>✨ ' + escapeHtml(t('ticket_refinement.label_improved_description','Verbesserte Beschreibung')) + '</span>' +
          '  <span class="sp-col-pill">' + escapeHtml(t('ticket_refinement.col_pill_ai','KI')) + '</span>' +
          '</div>';

        var pairedContainer;
        if (pairsHtml) {
          pairedContainer =
            '<div class="sp-compare sp-compare--paired">' +
              origHeadHtml + impHeadHtml + pairsHtml +
            '</div>';
        } else {

          pairedContainer =
            '<div class="sp-compare sp-compare--paired sp-compare--mono">' +
            '  <div class="sp-compare-col">' + origHeadHtml +
            '    <div class="sp-col-body sp-block--adf">' + origHtmlDiff + '</div>' +
            '  </div>' +
            '  <div class="sp-compare-col">' + impHeadHtml +
            '    <div class="sp-col-body sp-block--adf">' + impHtmlDiff + '</div>' +
            '  </div>' +
            '</div>';
        }

        var stackedContainer =
          '<div class="sp-compare sp-compare--stacked">' +
          '  <div class="sp-compare-col">' + origHeadHtml +
          '    <div class="sp-col-body sp-block--adf">' + origHtmlDiff + '</div>' +
          '  </div>' +
          '  <div class="sp-compare-col">' + impHeadHtml +
          '    <div class="sp-col-body sp-block--adf">' + impHtmlDiff + '</div>' +
          '  </div>' +
          '</div>';

        ticketHtml +=
          '<div class="sp-toolbar" role="toolbar" aria-label="' + escapeHtml(t('ticket_refinement.toolbar_label','Darstellung')) + '">' +
          '  <div class="sp-toolbar-group">' +
          '    <span class="sp-toolbar-label">' + escapeHtml(t('ticket_refinement.toolbar_layout','Layout:')) + '</span>' +
          '    <div class="sp-toggle" data-sp-toggle="layout">' +
          '      <button type="button" data-sp-layout="side">⇆ ' + escapeHtml(t('ticket_refinement.layout_side','Nebeneinander')) + '</button>' +
          '      <button type="button" data-sp-layout="stack">≡ ' + escapeHtml(t('ticket_refinement.layout_stack','Untereinander')) + '</button>' +
          '    </div>' +
          '  </div>' +
          '  <div class="sp-toolbar-group">' +
          '    <span class="sp-toolbar-label">' + escapeHtml(t('ticket_refinement.toolbar_fs','Schriftgröße:')) + '</span>' +
          '    <div class="sp-toggle" data-sp-toggle="fs">' +
          '      <button type="button" data-sp-fs="S">S</button>' +
          '      <button type="button" data-sp-fs="M">M</button>' +
          '      <button type="button" data-sp-fs="L">L</button>' +
          '    </div>' +
          '  </div>' +
          '</div>' +
          pairedContainer + stackedContainer;
        if ((r.openPoints || []).length) {
          ticketHtml +=
            '<div class="sp-open-points-panel">' +
            '<div class="sp-open-points-head">⚠ ' + escapeHtml(t('ticket_refinement.label_open_points','Offene Punkte')) + '</div>' +
            '<ul class="sp-jira-ul">' +
              r.openPoints.map(function(p){ return '<li>' + escapeHtml(p) + '</li>'; }).join('') +
            '</ul></div>';
        }
        ticketHtml += renderRepoAnalyses(r.repoAnalyses || []);
        ticketHtml += renderSuggestedSubtasks(r, data);
        ticketHtml += '<div class="sp-ticket-actions">';
        if (r.state === 'OK' || r.state === 'NEEDS_REVIEW') {
          ticketHtml += '<button class="sp-btn sp-btn-secondary" data-act="post" data-key="' + escapeHtml(r.ticketKey) + '">' +
                        escapeHtml(t('ticket_refinement.btn_post_comment','Als Kommentar posten')) + '</button>';
        }
        var subCreated = lookupCreatedSubtasks(data, r.ticketKey);
        if (subCreated.length > 0) {
          ticketHtml += '<span class="sp-comment-posted">✓ ' +
            escapeHtml(t('ticket_refinement.subtasks_created_badge','Subtasks erstellt')) +
            ' (' + subCreated.length + ')</span>';
        }
        ticketHtml += '</div>';
      }
      ticketHtml += '</div>';
      return ticketHtml;
    }).join('') + (data.status === 'COMPLETED' ? renderSummary(reviews) : '');

    [].slice.call(box.querySelectorAll('button[data-act="post"]')).forEach(function(btn){
      btn.addEventListener('click', function(){
        var key = btn.dataset.key;
        btn.disabled = true;
        fetch('/api/ticket-refinement/' + state.activeId + '/comment/' + encodeURIComponent(key), { method:'POST' })
          .then(function(r){ return r.ok ? r.json() : r.json().then(function(j){ throw new Error(j.error || 'error'); }); })
          .then(function(updated){ renderDetail(updated); })
          .catch(function(e){ showAlert(e.message || 'Error', 'danger'); btn.disabled = false; });
      });
    });

    applyResultsPrefs();
    if (!box._spToolbarBound) {
      box._spToolbarBound = true;
      box.addEventListener('click', function(ev){
        var t = ev.target.closest('button[data-sp-layout],button[data-sp-fs]');
        if (!t) return;
        if (t.hasAttribute('data-sp-layout')) {
          setLayoutPref(t.getAttribute('data-sp-layout'));
        } else if (t.hasAttribute('data-sp-fs')) {
          setFsPref(t.getAttribute('data-sp-fs'));
        }
        applyResultsPrefs();
      });
    }

    bindSubtaskControls(box);
  }

  function renderSuggestedSubtasks(r, data) {
    var subs = Array.isArray(r.suggestedSubtasks) ? r.suggestedSubtasks : [];
    if (subs.length === 0) return '';
    var created = lookupCreatedSubtasks(data, r.ticketKey);
    var createdSet = {};
    created.forEach(function(s){ createdSet[s] = true; });

    var html =
      '<div class="sp-subtasks">' +
      '  <div class="sp-subtasks-head">' +
      '    <i class="bi bi-diagram-3-fill"></i>' +
      '    <span>' + escapeHtml(t('ticket_refinement.subtasks_title','Subtasks')) + ' (' + subs.length + ')</span>' +
      '  </div>' +
      '  <div class="sp-subtasks-list">';
    subs.forEach(function(st, idx){
      var sum = st.summary || '';
      var desc = st.description || '';

      var _stTypeResolved = (window.i18n && typeof window.i18n.t === 'function')
        ? window.i18n.t('ticket_refinement.subtask_issue_type')
        : '';
      var type = _stTypeResolved || 'Subtask';
      var prio = st.priority || 'Minor';
      var labels = Array.isArray(st.labels) ? st.labels : [];
      var existingKey = st.existingKey || '';
      var isCreated = !!createdSet[sum];
      var cardCls = 'sp-subtask-card' + (existingKey ? ' sp-subtask-card--existing' : '') +
                    (isCreated ? ' sp-subtask-card--created' : '');
      html +=
        '<div class="' + cardCls + '" data-subtask-index="' + idx + '"' +
        (existingKey ? ' data-existing-key="' + escapeHtml(existingKey) + '"' : '') + '>' +
        '  <div class="sp-subtask-head">' +
        '    <i class="bi bi-diagram-3"></i>' +
        '    <span class="sp-subtask-type">' + escapeHtml(type) + '</span>' +
        (existingKey
          ? '<span class="sp-subtask-existing-badge" title="' + escapeHtml(t('ticket_refinement.subtask_refine_hint','Re-formuliert bestehende Subtask')) + '">' +
            escapeHtml(t('ticket_refinement.subtask_refine_short','Refine')) + ' ' + escapeHtml(existingKey) + '</span>'
          : '') +
        (isCreated
          ? '<span class="sp-subtask-created-badge" title="' + escapeHtml(t('ticket_refinement.subtask_created_hint','In Jira erstellt')) + '">' +
            escapeHtml(t('ticket_refinement.subtask_created_short','Created')) + '</span>'
          : '') +
        '    <span class="sp-subtask-priority">' + escapeHtml(prio) + '</span>' +
        '    <button type="button" class="sp-subtask-remove" data-subtask-index="' + idx + '" title="' +
            escapeHtml(t('ticket_refinement.subtask_remove','Entfernen')) + '">' +
        '      <i class="bi bi-x-lg"></i>' +
        '    </button>' +
        '  </div>' +
        '  <textarea class="sp-subtask-summary" data-subtask-index="' + idx + '" rows="1" placeholder="' +
            escapeHtml(t('ticket_refinement.subtask_summary','Subtask-Titel')) + '">' + escapeHtml(sum) + '</textarea>' +
        '  <textarea class="sp-subtask-description" data-subtask-index="' + idx + '" rows="4" placeholder="' +
            escapeHtml(t('ticket_refinement.subtask_description','Subtask-Beschreibung')) + '">' + escapeHtml(desc) + '</textarea>' +
        '  <div class="sp-subtask-labels" data-subtask-index="' + idx + '">' +
        (labels.length ? labels.map(function(l){ return '<span class="sp-subtask-label">' + escapeHtml(l) + '</span>'; }).join('') : '') +
        '  </div>' +
        '</div>';
    });
    html +=
      '  </div>' +
      '  <div class="sp-subtask-actions" style="padding: 0 12px 12px 12px; display:flex; gap:8px; flex-wrap:wrap;">' +
      '    <button type="button" class="sp-btn sp-btn-primary sp-btn-sm" data-act="create-subtasks" data-key="' + escapeHtml(r.ticketKey) + '" data-prep-id="' + escapeHtml(String(data && data.id != null ? data.id : '')) + '"' +
      (r.state === 'FAILED' ? ' disabled' : '') + '>' +
      '      <i class="bi bi-plus-circle"></i> ' +
      escapeHtml(t('ticket_refinement.btn_create_subtasks','Subtasks in Jira erstellen')) +
      '    </button>' +
      '    <span class="sp-subtask-status" data-subtask-status="' + escapeHtml(r.ticketKey) + '"></span>' +
      '  </div>' +
      '</div>';
    return html;
  }

  function lookupCreatedSubtasks(data, ticketKey) {
    var raw = data && data.subtasksCreatedJson;
    if (!raw) return [];
    try {
      var map = (typeof raw === 'string') ? JSON.parse(raw) : raw;
      var list = map && map[ticketKey];
      return Array.isArray(list) ? list : [];
    } catch (_) { return []; }
  }

  function collectSubtaskDrafts(ticketCard) {
    var drafts = [];
    if (!ticketCard) return drafts;
    var cards = ticketCard.querySelectorAll('.sp-subtask-card');
    Array.prototype.forEach.call(cards, function(card){
      var idx = card.getAttribute('data-subtask-index');
      var sumEl = card.querySelector('.sp-subtask-summary');
      var descEl = card.querySelector('.sp-subtask-description');
      var labelsEl = card.querySelector('.sp-subtask-labels');
      var sum = sumEl ? sumEl.value.trim() : '';
      if (!sum) return;
      var desc = descEl ? descEl.value : '';
      var labels = [];
      if (labelsEl) {
        Array.prototype.forEach.call(labelsEl.querySelectorAll('.sp-subtask-label'), function(lbl){
          var txt = lbl.textContent.trim();
          if (txt) labels.push(txt);
        });
      }
      var type = card.querySelector('.sp-subtask-type');
      var prio = card.querySelector('.sp-subtask-priority');
      drafts.push({
        summary: sum,
        description: desc || '',
        issueType: type ? type.textContent.trim() || 'Subtask' : 'Subtask',
        priority: prio ? prio.textContent.trim() || 'Minor' : 'Minor',
        labels: labels,
      });
    });
    return drafts;
  }

  function removeSuggestedSubtask(data, ticketKey, index, ticketCard) {
    if (!data || !Number.isInteger(index) || index < 0) return false;
    var reviews;
    try {
      reviews = typeof data.reviewsJson === 'string' ? JSON.parse(data.reviewsJson) : data.reviewsJson;
    } catch (_) {
      return false;
    }
    if (!Array.isArray(reviews)) return false;
    var review = reviews.find(function(r){ return r.ticketKey === ticketKey; });
    if (!review || !Array.isArray(review.suggestedSubtasks) || index >= review.suggestedSubtasks.length) return false;

    if (ticketCard) {
      Array.prototype.forEach.call(ticketCard.querySelectorAll('.sp-subtask-card'), function(subtaskCard){
        var renderedIndex = parseInt(subtaskCard.getAttribute('data-subtask-index'), 10);
        var suggestion = review.suggestedSubtasks[renderedIndex];
        if (!suggestion) return;
        var summary = subtaskCard.querySelector('.sp-subtask-summary');
        var description = subtaskCard.querySelector('.sp-subtask-description');
        if (summary) suggestion.summary = summary.value;
        if (description) suggestion.description = description.value;
      });
    }

    review.suggestedSubtasks.splice(index, 1);
    data.reviewsJson = JSON.stringify(reviews);
    return true;
  }

  function bindSubtaskControls(box) {

    Array.prototype.forEach.call(box.querySelectorAll('.sp-subtask-remove'), function(btn){
      btn.addEventListener('click', function(ev){
        ev.preventDefault();
        ev.stopPropagation();
        var idx = parseInt(btn.getAttribute('data-subtask-index'), 10);
        var card = btn.closest('.sp-ticket');
        if (!card) return;
        var key = card.getAttribute('data-key');
        var detail = window._sp.lastDetail;
        if (!removeSuggestedSubtask(detail, key, idx, card)) return;
        renderDetail(detail);
      });
    });

    Array.prototype.forEach.call(box.querySelectorAll('[data-act="create-subtasks"]'), function(btn){
      btn.addEventListener('click', function(ev){
        ev.preventDefault();
        ev.stopPropagation();
        var key = btn.getAttribute('data-key');
        var card = btn.closest('.sp-ticket');
        var statusEl = card ? card.querySelector('[data-subtask-status="' + key + '"]') : null;
        var drafts = collectSubtaskDrafts(card);
        if (drafts.length === 0) {
          if (statusEl) statusEl.innerHTML =
            '<span class="sp-subtask-status--fail">' +
            escapeHtml(t('ticket_refinement.subtasks_empty','Keine Subtasks zum Erstellen.')) +
            '</span>';
          return;
        }
        btn.disabled = true;
        var prepId = btn.getAttribute('data-prep-id');
        if (!prepId) {
          prepId = window._sp.runningId;
        }
        if (!prepId) {
          if (statusEl) statusEl.innerHTML =
            '<span class="sp-subtask-status--fail">' +
            escapeHtml(t('ticket_refinement.subtasks_no_prep_id', 'Preparation-ID fehlt, bitte erneut laden.')) +
            '</span>';
          btn.disabled = false;
          return;
        }
        if (statusEl) statusEl.innerHTML =
          '<span style="opacity:.75;"><i class="bi bi-arrow-clockwise" style="animation:tc-spin 1s linear infinite;display:inline-block;"></i> ' +
          escapeHtml(t('ticket_refinement.creating_subtasks','Erstelle Subtasks…')) + '</span>';
        fetch('/api/ticket-refinement/' + prepId + '/subtasks/' + encodeURIComponent(key), {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ subtasks: drafts }),
        }).then(function(r){
          return r.json().then(function(j){ return { ok: r.ok, status: r.status, body: j }; });
        }).then(function(res){
          if (!res.ok) {
            throw new Error((res.body && res.body.error) || ('HTTP ' + res.status));
          }
          var okCount = (res.body.subtasks || []).filter(function(s){ return !s.error; }).length;
          var failCount = (res.body.subtasks || []).length - okCount;
          if (statusEl) {
            statusEl.innerHTML =
              '<span class="sp-subtask-status--ok">' +
              escapeHtml(t('ticket_refinement.subtasks_created_ok', 'Subtasks erstellt')) +
              ' (' + okCount + ')' +
              (failCount > 0 ? ' &middot; <span class="sp-subtask-status--fail">' + failCount + ' fehlgeschlagen</span>' : '') +
              '</span>';
          }
          if (typeof fetchAndRender === 'function') {
            fetchAndRender(prepId);
          }
        }).catch(function(e){
          if (statusEl) statusEl.innerHTML =
            '<span class="sp-subtask-status--fail">' + escapeHtml(e.message || 'Error') + '</span>';
        }).finally(function(){
          btn.disabled = false;
        });
      });
    });
  }

  function startPreparation(){
    var key = ($('spParentKey').value || '').trim();
    if (!key) { $('spParentKey').focus(); return; }
    var configId = $('spJiraConfig').value || null;
    var template = ($('spTemplate').value || '').trim();
    var repoSelections = collectRepoSelections();
    var singleOnlyEl = $('spSingleOnly');
    var singleOnly = singleOnlyEl ? !!singleOnlyEl.checked : true;
    var dbIds = [];
    var dbChips = document.querySelectorAll('#spDatabaseMultiSelect .sp-db-chip.selected');
    Array.prototype.forEach.call(dbChips, function(chip) { dbIds.push(Number(chip.getAttribute('data-db-id'))); });
    var btn = $('spStart');
    btn.disabled = true;
    fetch('/api/ticket-refinement', {
      method:'POST',
      headers:{ 'Content-Type':'application/json' },
      body: JSON.stringify({
        parentTicketKey: key,
        jiraConfigId: configId ? Number(configId) : null,
        templateOverride: template || null,
        repoSelections: repoSelections,
        singleTicketOnly: singleOnly,
        databaseConfigIds: dbIds
      })
    })
      .then(function(r){
        return r.ok ? r.json() : r.json().then(function(j){ throw new Error(j.error || 'error'); });
      })
      .then(function(j){
        $('spParentKey').value = '';
        window._sp.runningId = j.id;
        if (typeof spInitTokenUsageBar === 'function') spInitTokenUsageBar();
        loadHistory(j.id).then(function(){ openPreparation(j.id); });
      })
      .catch(function(e){ showAlert(e.message || 'Error', 'danger'); })
      .finally(function(){ btn.disabled = false; });
  }

  function cancelPreparation(){
    var id = state.activeId;
    if (!id) return;
    var cancelBtn = $('spCancel');
    if (cancelBtn) cancelBtn.disabled = true;
    fetch('/api/ticket-refinement/' + id + '/cancel', { method:'POST', credentials:'same-origin' })
      .then(function(r){ return r.ok ? r : null; })
      .catch(function(){})
      .finally(function(){
        if (cancelBtn) cancelBtn.disabled = false;
        fetchAndRender(id);
      });
  }

  function showDefault(){
    fetch('/api/ticket-refinement/template/default', { credentials:'same-origin' })
      .then(function(r){ return r.ok ? r.json() : { template: '' }; })
      .then(function(j){
        $('spTemplate').value = j.template || '';
      });
  }

  function tryResumeRunning(list){

    var running = null;
    if (list && list.length) {
      for (var i = 0; i < list.length; i++) {
        var s = (list[i].status || '').toUpperCase();
        if (s === 'RUNNING' || s === 'PENDING') { running = list[i]; break; }
      }
    }
    if (running) {
      window._sp.runningId = running.id;
      if (typeof spInitTokenUsageBar === 'function') spInitTokenUsageBar();
      openPreparation(running.id);
      return;
    }

    var storedId = window._sp.lastOpenId || window._sp.runningId;
    if (!storedId) return;
    fetch('/api/ticket-refinement/' + storedId, { credentials:'same-origin' })
      .then(function(r){ return r.ok ? r.json() : null; })
      .catch(function(){ return null; })
      .then(function(data){

        if (window._sp.gen !== _spPageGen) return;
        if (!data) { window._sp.runningId = null; return; }
        var s = (data.status || '').toUpperCase();
        if (s === 'COMPLETED' || s === 'FAILED') {

          window._sp.runningId = null;
          state.activeId = storedId;
          if (typeof spInitTokenUsageBar === 'function') spInitTokenUsageBar();
          renderDetail(data);
          _spHistoryActiveId = storedId;
          if (_spHistory) _spHistory.refresh();

          loadHistory(storedId);
        } else if (s === 'RUNNING' || s === 'PENDING') {

          window._sp.runningId = storedId;
          openPreparation(storedId);
        } else {

          window._sp.runningId = null;
        }
      });
  }

  function bindListeners(){
    var form = $('spForm');
    if (!form) return false;
    if (form.dataset.spBound === '1') return true;
    form.dataset.spBound = '1';
    form.addEventListener('submit', function(e){ e.preventDefault(); startPreparation(); });
    $('spStart').addEventListener('click', function(){ startPreparation(); });
    var cancelBtn = $('spCancel');
    if (cancelBtn) cancelBtn.addEventListener('click', function(){ cancelPreparation(); });
    $('spShowDefault').addEventListener('click', showDefault);
    $('spJiraConfig').addEventListener('change', applyDemoAutoFill);
    var delAll = $('spBtnDelAll');
    if (delAll) delAll.addEventListener('click', spDeleteAllHistory);
    initContextMenu();
    return true;
  }

  function loadAll(){
    _spPageGen = window._sp.gen;
    loadJiraConfigs();
    loadDbConfigs();
    loadRepoConfigs();
    loadHistory().then(function(list){ tryResumeRunning(list || []); });
  }

  function init(){
    if (!bindListeners()) return;
    loadAll();
  }

  function initContextMenu(){
    var menu = $('spCtxMenu');
    if (!menu) return;
    var copyBtn      = $('spCtxCopy');
    var selBlockBtn  = $('spCtxSelectBlock');
    var selAllBtn    = $('spCtxSelectAll');

    var _ctxTarget = null;

    function hideMenu(){
      menu.style.display = 'none';
      _ctxTarget = null;
    }

    function showMenu(x, y){

      menu.style.visibility = 'hidden';
      menu.style.display = 'block';
      var mw = menu.offsetWidth;
      var mh = menu.offsetHeight;
      var vw = window.innerWidth;
      var vh = window.innerHeight;
      if (x + mw + 6 > vw) x = vw - mw - 6;
      if (y + mh + 6 > vh) y = vh - mh - 6;
      menu.style.left  = Math.max(4, x) + 'px';
      menu.style.top   = Math.max(4, y) + 'px';
      menu.style.visibility = '';

      var sel = window.getSelection ? window.getSelection() : null;
      var hasSelection = sel && sel.toString().trim().length > 0;
      if (copyBtn) copyBtn.classList.toggle('sp-ctx-disabled', !hasSelection);
    }

    var resultsEl = $('spResults');
    if (resultsEl){
      resultsEl.addEventListener('contextmenu', function(e){

        if (e.target.closest('button, a, input, textarea, select')) return;
        e.preventDefault();
        _ctxTarget = e.target;
        showMenu(e.clientX, e.clientY);
      });
    }

    if (copyBtn){
      copyBtn.addEventListener('click', function(){
        var txt = window.getSelection ? window.getSelection().toString() : '';
        if (!txt) { hideMenu(); return; }
        if (navigator.clipboard && navigator.clipboard.writeText){
          navigator.clipboard.writeText(txt).catch(function(){ document.execCommand('copy'); });
        } else {
          document.execCommand('copy');
        }
        hideMenu();
      });
    }

    if (selBlockBtn){
      selBlockBtn.addEventListener('click', function(){
        hideMenu();
        var block = _ctxTarget && _ctxTarget.closest('.sp-block, .sp-points, .sp-ticket');
        if (!block) block = $('spResults');
        if (!block) return;
        var range = document.createRange();
        range.selectNodeContents(block);
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
      });
    }

    if (selAllBtn){
      selAllBtn.addEventListener('click', function(){
        hideMenu();
        var results = $('spResults');
        if (!results) return;
        var range = document.createRange();
        range.selectNodeContents(results);
        var sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
      });
    }

    document.addEventListener('click', function(e){
      if (!menu.contains(e.target)) hideMenu();
    });
    document.addEventListener('keydown', function(e){
      if (e.key === 'Escape') hideMenu();
    });

    menu.addEventListener('click', function(e){ e.stopPropagation(); });
  }
  if (window._spNavHandler) document.removeEventListener('app:navigate', window._spNavHandler);
  window._spNavHandler = function() {
    if (!document.getElementById('spForm')) {
      window._sp.gen++;
      return;
    }
    init();
  };
  document.addEventListener('app:navigate', window._spNavHandler);

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

