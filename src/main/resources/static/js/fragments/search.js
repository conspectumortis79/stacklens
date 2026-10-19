if (typeof window._suCleanupTimers === 'function') { window._suCleanupTimers(); }

(function() {

  var _sessionId     = null;
  var _crossRef      = false;
  var _ownPollTimer  = null;
  var _searchAbortCtrl = null;
  var _aiPollTimer   = null;
  var _aiAnalysisId  = null;

  var _activePollTmr = null;
  var _p2PollTimer   = null;
  var _viewingId     = null;
  var _viewingSessions = [];
  var _keywords      = [];
  var _results       = [];
  var _isViewingLive = false;


  var _confAllResults  = [];
  var _confPage        = 0;
  var _confPageSize    = 25;
  var _confLoadingMore = false;
  var _confHasMore     = false;

  var buildResultCard = window.SuRender.buildResultCard;
  var buildJiraGroupedView = window.SuRender.buildJiraGroupedView;
  var buildJiraTicketCard = window.SuRender.buildJiraTicketCard;
  var suJiraToggleGroup = window.SuRender.suJiraToggleGroup;
  var suJiraToggleDesc = window.SuRender.suJiraToggleDesc;
  var jiraExtractAcOrFirst = window.SuRender.jiraExtractAcOrFirst;
  var jiraFormatDate = window.SuRender.jiraFormatDate;
  var openInBrowser = window.SuRender.openInBrowser;
  var buildGitlabGroupedView = window.SuRender.buildGitlabGroupedView;
  var buildGitlabFileItem = window.SuRender.buildGitlabFileItem;
  var suSwitchTab = window.SuRender.suSwitchTab;
  var suSetAiResult = window.SuRender.suSetAiResult;
  var suRenderAiAnalysis = window.SuRender.suRenderAiAnalysis;

  function suFetch(url, options, ms) {
    var ctrl = new AbortController();
    var tid  = setTimeout(function() { ctrl.abort(); }, ms || 15000);
    var opts = {};
    if (options) { for (var k in options) { opts[k] = options[k]; } }
    opts.signal = ctrl.signal;
    return fetch(url, opts).then(
      function(r) { clearTimeout(tid); return r; },
      function(e) { clearTimeout(tid); throw e; }
    );
  }

  function suShowView(v) {

    var r = document.getElementById('suViewResults');
    if (r) r.style.display = v === 'results' ? '' : 'none';
    startActivePoll();
  }

  function suGoBack() {

    clearInterval(_p2PollTimer); _p2PollTimer = null;
    clearInterval(_aiPollTimer); _aiPollTimer = null;

    var lb = document.getElementById('suViewLiveBadge');
    if (lb) lb.style.display = 'none';

    try { sessionStorage.removeItem('su_restore_session'); } catch(e) {}
    suShowView('search');

    fetchAndRenderActiveSessions();
  }
  window.suGoBack = suGoBack;

  function startActivePoll() {
    if (_activePollTmr) return;
    fetchAndRenderActiveSessions();
    _activePollTmr = setInterval(fetchAndRenderActiveSessions, 5000);
  }
  var _activeFetchInflight = false;
  function fetchAndRenderActiveSessions() {
    if (_activeFetchInflight) return;
    _activeFetchInflight = true;
    suFetch('/api/sessions', null, 10000)
      .then(function(r) { if (!r.ok) throw new Error(); return r.json(); })
      .then(function(sessions) {
        _activeFetchInflight = false;

        window.__lastSessionsData = { sessions: sessions, ts: Date.now() };
        _viewingSessions = sessions;
        renderActiveSessions(sessions);

        if (_sessionId) {
          var own = sessions.find(function(s) { return s.id === _sessionId; });
          if (own && own.status === 'COMPLETED') {
            clearInterval(_ownPollTimer); _ownPollTimer = null;
            _sessionId = null;
            var btn = document.getElementById('suBtnSearch');
            var cancel = document.getElementById('suBtnCancel');
            if (btn) btn.disabled = false;
            if (cancel) cancel.disabled = true;
            suHideStatus();
            suOpenSession(own.id, own.keywords, own.status, _crossRef);
          } else if (own && own.status === 'FAILED') {
            clearInterval(_ownPollTimer); _ownPollTimer = null;
            _sessionId = null;
            var btn2 = document.getElementById('suBtnSearch');
            var cancel2 = document.getElementById('suBtnCancel');
            if (btn2) btn2.disabled = false;
            if (cancel2) cancel2.disabled = true;
            suShowStatusError((window.i18n ? window.i18n.t('suche.status_failed') : 'Fehlgeschlagen') + (own.error ? ': ' + own.error : ''));
          }
        }
      })
      .catch(function() { _activeFetchInflight = false; });
  }

  var _suHistory = null;

  function suHistory() {
    if (_suHistory) return _suHistory;
    _suHistory = window.slHistory.mount({
      root: 'hxCard-search',
      page: 'search',
      titleKey: 'suche.verlauf_title',
      emptyKey: 'suche.no_active_searches',
      columns: {
        title: { key: 'suche.col_keywords', fallback: 'Suchbegriffe' },
        metric: { key: 'suche.col_results', fallback: 'Ergebnisse' }
      },
      map: {
        id: function(sess) { return sess.id; },
        status: function(sess) { return sess.status; },
        createdAt: function(sess) { return sess.createdAt; },
        typeIcon: function() { return 'bi-search'; },
        title: function(sess) { return String(sess.keywords || '').replace(/\s+/g, ' ').trim(); },
        subtitle: function(sess) { return sess.error ? String(sess.error).slice(0, 90) : ''; },
        metric: function(sess) { return sess.resultCount > 0 ? String(sess.resultCount) : ''; },
        note: function(sess) { return sess.error || ''; },
        isCurrent: function(sess) { return _viewingId === sess.id; }
      },
      actions: {
        open: function(sess) { suOpenSession(sess.id, sess.keywords || '', sess.status, false); },
        rerun: function(sess) { suRerunSession(sess.id); },
        remove: function(sess) { suDeleteSession(sess.id); },
        cancel: function(sess) { suCancelSessionRun(sess.id); },
        deleteAll: function() { suDeleteAllSessions(); }
      }
    });
    return _suHistory;
  }

  function suCancelSessionRun(id) {
    fetch('/api/sessions/' + id + '/cancel', { method: 'POST' })
      .then(function() { fetchAndRenderActiveSessions(); })
      .catch(function() {});
  }

  function renderActiveSessions(sessions) {
    var list = (sessions || []).slice().sort(function(a, b) { return (b.id || 0) - (a.id || 0); });
    suHistory().setData(list);
  }

  function suOpenSession(id, keywords, status, startCrossRef) {
    _viewingId = id;

    try { sessionStorage.setItem('su_restore_session', JSON.stringify({id: id, keywords: keywords, status: status, crossRef: !!startCrossRef})); } catch(e) {}

    suUpdateViewHeader(keywords, null, status === 'RUNNING' || status === 'PENDING');

    suRenderDbErrors([]);

    suShowView('results');

    suFetch('/api/sessions/' + id + '/results', null, 20000)
      .then(function(r) { if (!r.ok) throw new Error(); return r.json(); })
      .then(function(results) {
        _results = results;
        suRenderResults(results);
        suSwitchTab('all');
        suUpdateViewHeader(keywords, results.length, status === 'RUNNING' || status === 'PENDING');

        if (status === 'RUNNING' || status === 'PENDING') {
          startP2LivePoll(id, keywords, startCrossRef);
        } else {
          clearInterval(_p2PollTimer); _p2PollTimer = null;
          var lb = document.getElementById('suViewLiveBadge');
          if (lb) lb.style.display = 'none';

          if (status === 'COMPLETED') {
            tryRestoreAiAnalysis(id, startCrossRef);
          } else {
            suSetAiPane('placeholder');
          }
        }

        suFetch('/api/search/' + id + '/status', null, 10000)
          .then(function(rs) { if (!rs.ok) return null; return rs.json(); })
          .then(function(sd) { if (sd) suRenderDbErrors(Array.isArray(sd.databaseErrors) ? sd.databaseErrors : []); })
          .catch(function() {   });
      })
      .catch(function() {

        if (status === 'RUNNING' || status === 'PENDING') {
          startP2LivePoll(id, keywords, startCrossRef);
        } else {

          var lb2 = document.getElementById('suViewLiveBadge');
          if (lb2) lb2.style.display = 'none';
          suUpdateViewHeader(keywords, 0, false);
          suSetAiPane('placeholder');
        }
      });
  }
  window.suOpenSession = suOpenSession;

  function suOpenById(id) {
    var s = _viewingSessions.find(function(x) { return x.id === id; });
    if (s) suOpenSession(s.id, s.keywords || '', s.status, false);
  }
  window.suOpenById = suOpenById;

  function startP2LivePoll(id, keywords, startCrossRefOnDone) {
    clearInterval(_p2PollTimer);
    var liveBadge = document.getElementById('suViewLiveBadge');
    if (liveBadge) liveBadge.style.display = 'inline-flex';
    suSetAiPane('placeholder');
    suRenderDbErrors([]);

    var _pollErrors = 0;
    var _inflight   = false;

    function stopPollAndReset() {
      clearInterval(_p2PollTimer); _p2PollTimer = null;
      if (liveBadge) liveBadge.style.display = 'none';
      suUpdateViewHeader(keywords, _results.length, false);
      suRenderResults(_results);
      suSetAiPane('placeholder');
    }

    _p2PollTimer = setInterval(function() {
      if (_inflight) return;
      _inflight = true;
      suFetch('/api/search/' + id + '/status', null, 10000)
        .then(function(r) { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(function(data) {
          _inflight   = false;
          _pollErrors = 0;

          suUpdateViewHeader(keywords, data.resultCount, data.status === 'RUNNING' || data.status === 'PENDING');

          suRenderDbErrors(Array.isArray(data.databaseErrors) ? data.databaseErrors : []);
          if (data.status === 'COMPLETED' || data.status === 'FAILED' || data.status === 'CANCELLED') {
            clearInterval(_p2PollTimer); _p2PollTimer = null;
            if (liveBadge) liveBadge.style.display = 'none';
            if (data.status === 'COMPLETED') {

              suFetch('/api/sessions/' + id + '/results', null, 20000)
                .then(function(r2) { return r2.json(); })
                .then(function(results) {
                  _results = results;
                  suRenderResults(results);
                  suUpdateViewHeader(keywords, results.length, false);
                  tryRestoreAiAnalysis(id, startCrossRefOnDone);
                })
                .catch(function() {

                  suUpdateViewHeader(keywords, _results.length, false);
                  suRenderResults(_results);
                  suSetAiPane('placeholder');
                });
            } else {

              suRenderResults(_results);
              suSetAiPane('placeholder');
              if (data.status === 'FAILED') {
                suShowStatusError(data.error || t('suche.status_failed', 'Die Suche ist fehlgeschlagen.'));
              }
            }
          }
        })
        .catch(function() {
          _inflight = false;

          if (++_pollErrors >= 3) { stopPollAndReset(); }
        });
    }, 3000);
  }

  function suRenderDbErrors(errors) {
    var banner = document.getElementById('suDbErrorBanner');
    var list   = document.getElementById('suDbErrorList');
    var count  = document.getElementById('suDbErrorCount');
    if (!banner || !list || !count) return;

    if (!errors || errors.length === 0) {
      banner.style.display = 'none';
      banner.classList.remove('expanded');
      list.style.display = 'none';
      list.innerHTML = '';
      return;
    }

    count.textContent = String(errors.length);

    var html = '';
    for (var i = 0; i < errors.length; i++) {
      var e = errors[i] || {};
      var name  = escHtml(String(e.configName || ''));
      var type  = escHtml(String(e.dbType || ''));
      var host  = escHtml(String(e.host || ''));
      var db    = escHtml(String(e.databaseName || ''));
      var msg   = escHtml(String(e.errorMessage || ''));
      var typeBadge = type
        ? '<span class="su-db-error-type-badge">' + type + '</span>'
        : '';
      var target = host || db
        ? '<div class="su-db-error-target">' + host + (host && db ? ' / ' : '') + db + '</div>'
        : '';
      html +=
        '<div class="su-db-error-item">' +
          '<span class="su-db-error-bullet"></span>' +
          '<div class="su-db-error-body">' +
            '<div class="su-db-error-name">' + name + ' ' + typeBadge + '</div>' +
            target +
            '<div class="su-db-error-msg">' + msg + '</div>' +
          '</div>' +
        '</div>';
    }
    list.innerHTML = html;
    banner.style.display = '';
  }

  function suToggleDbErrorBanner() {
    var banner = document.getElementById('suDbErrorBanner');
    var list   = document.getElementById('suDbErrorList');
    if (!banner || !list) return;
    var open = list.style.display !== 'none';
    if (open) {
      list.style.display = 'none';
      banner.classList.remove('expanded');
    } else {
      list.style.display = '';
      banner.classList.add('expanded');
    }
  }
  window.suToggleDbErrorBanner = suToggleDbErrorBanner;
  window.suRenderDbErrors = suRenderDbErrors;

  function suBindAiBadge(analysisId) {
    if (analysisId == null) return;
    _aiAnalysisId = analysisId;
    if (window.cfTokenUsage) {
      window.cfTokenUsage.init('ai', analysisId, {
        anchor: document.querySelector('.search-result-header, .sl-page-header')
      });
    }
  }
  window.suBindAiBadge = suBindAiBadge;
  window.suPollAiAnalysis = suPollAiAnalysis;
  window.suIsAiTerminalStatus = suIsAiTerminalStatus;

  function tryRestoreAiAnalysis(id, startIfAbsent) {
    suFetch('/api/sessions/' + id + '/analysis', null, 10000)
      .then(function(r) { if (r.status === 404) return null; if (!r.ok) throw new Error(); return r.json(); })
      .then(function(data) {
        if (!data || data.status === 'NONE') {
          if (startIfAbsent && _results.length > 0) {
            suStartAiAnalysis(id);
          } else {
            suSetAiPane('placeholder');
          }
          return;
        }
        suBindAiBadge(data.id);
        if (data.status === 'COMPLETED' && data.analysisText) {
          suSetAiPane('done');
          suSetAiResult(data.analysisText);
        } else if (data.status === 'RUNNING' || data.status === 'PENDING') {
          suSetAiPane('loading');
          clearInterval(_aiPollTimer);
          _aiPollTimer = setInterval(function() { suPollAiAnalysis(id); }, 3000);
        } else {
          suSetAiPane('placeholder');
        }
      })
      .catch(function() { suSetAiPane('placeholder'); });
  }

  function suStartAiAnalysis(id) {
    suSetAiPane('loading');
    suFetch('/api/sessions/' + id + '/analyze', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({includeMermaid: false, includeDrawio: false})
    }, 30000)
    .then(function(r) { if (!r.ok) throw new Error(); return r.json(); })
    .then(function(data) {

      suBindAiBadge(data && data.analysisId);
      clearInterval(_aiPollTimer);
      _aiPollTimer = setInterval(function() { suPollAiAnalysis(id); }, 3000);
    })
    .catch(function() { suSetAiPane('placeholder'); });
  }

  var AI_TERMINAL_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED', 'NO_TOKEN'];

  function suIsAiTerminalStatus(status) {
    return AI_TERMINAL_STATUSES.indexOf(status) !== -1;
  }

  function suPollAiAnalysis(id) {
    suFetch('/api/sessions/' + id + '/analysis', null, 10000)
      .then(function(r) { if (!r.ok) throw new Error(); return r.json(); })
      .then(function(data) {
        if (suIsAiTerminalStatus(data.status)) {
          clearInterval(_aiPollTimer); _aiPollTimer = null;
          if (data.status === 'COMPLETED' && data.analysisText) {
            suSetAiPane('done');
            suSetAiResult(data.analysisText);
          } else {
            suSetAiPane('placeholder');
          }
        }
      })
      .catch(function() { clearInterval(_aiPollTimer); _aiPollTimer = null; suSetAiPane('placeholder'); });
  }

  function suSetAiPane(state) {
    var ph = document.getElementById('suAiPlaceholder');
    var st = document.getElementById('suAiStatus');
    var sp = document.getElementById('suAiSpinner');
    var cn = document.getElementById('suAiContent');
    var tabSp = document.getElementById('suTabKiSpinner');
    var tabIco = document.getElementById('suTabKiIcon');
    if (ph) ph.style.display = (state === 'placeholder') ? '' : 'none';
    if (st) st.style.display = (state === 'loading') ? 'flex' : 'none';
    if (sp) sp.style.display = (state === 'loading') ? '' : 'none';
    if (cn) cn.style.display = (state === 'done') ? '' : 'none';
    if (tabSp) tabSp.style.display = (state === 'loading') ? '' : 'none';
    if (tabIco) tabIco.style.display = (state === 'loading') ? 'none' : '';
  }

  function suUpdateViewHeader(keywords, count, isLive) {
    var kwEl = document.getElementById('suViewHeadKw');
    var countEl = document.getElementById('suViewResultCount');
    var liveBadge = document.getElementById('suViewLiveBadge');
    var banner = document.getElementById('suRunningBanner');
    if (kwEl) kwEl.textContent = (window.i18n ? window.i18n.t('suche.results_header_kw') : 'Suche nach:') + ' ' + truncate(keywords || '', 60);
    if (countEl && count != null) {
      countEl.textContent = '· ' + count + ' ' + (count === 1 ? t('suche.results_count', 'Ergebnis') : t('suche.results_count_plural', 'Ergebnisse'));
    }
    if (liveBadge) liveBadge.style.display = isLive ? 'inline-flex' : 'none';
    if (banner) banner.style.display = isLive ? '' : 'none';
    _isViewingLive = isLive;
  }

  function suStartSearch() {
    var btnSearch = document.getElementById('suBtnSearch');
    if (btnSearch && btnSearch.disabled) return;
    var kw = _keywords.slice();
    var inputVal = (document.getElementById('suKeywordInput').value || '').trim();
    if (inputVal && kw.indexOf(inputVal) === -1) { kw.push(inputVal); _keywords.push(inputVal); renderTags(); document.getElementById('suKeywordInput').value = ''; }

    var inclGitlab     = document.getElementById('suIncludeGitlab').checked;
    var inclDb         = document.getElementById('suIncludeDb').checked;
    var inclJira       = document.getElementById('suIncludeJira').checked;
    var inclConfluence = document.getElementById('suIncludeConfluence').checked;
    var inclGithub     = document.getElementById('suIncludeGithub') ? document.getElementById('suIncludeGithub').checked : false;
    _crossRef = document.getElementById('suCrossRef').checked;

    if (kw.length === 0) { suShowStatusError(t('suche.error_no_keyword', 'Bitte mindestens einen Suchbegriff eingeben.')); return; }
    if (!inclGitlab && !inclDb && !inclJira && !inclConfluence && !inclGithub) { suShowStatusError(t('suche.error_no_source', 'Bitte mindestens eine Quelle auswählen.')); return; }

    var gitlabIds      = inclGitlab     ? getCheckedIds('suGitlabChips')     : [];
    var dbIds          = inclDb         ? getCheckedIds('suDbChips')         : [];
    var jiraIds        = inclJira       ? getCheckedIds('suJiraChips')       : [];
    var confluenceIds  = inclConfluence ? getCheckedIds('suConfluenceChips') : [];
    var githubIds      = inclGithub     ? getCheckedIds('suGithubChips')     : [];
    var jiraProjects   = inclJira ? splitComma('suJiraProjects')   : [];
    var jiraComponents = inclJira ? splitComma('suJiraComponents') : [];
    var jiraLabels     = inclJira ? splitComma('suJiraLabels')     : [];

    var btnSearch = document.getElementById('suBtnSearch');
    var btnCancel = document.getElementById('suBtnCancel');
    if (btnSearch) btnSearch.disabled = true;
    if (btnCancel) btnCancel.disabled = false;
    suShowStatus(t('suche.searching', 'Suche wird gestartet…'));

    var savedCrossRef = _crossRef;
    var kwStr = kw.join(', ');

    _searchAbortCtrl = new AbortController();
    fetch('/api/search', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      signal: _searchAbortCtrl.signal,
      body: JSON.stringify({
        keywords: kw,
        includeGitlab: inclGitlab, includeDatabase: inclDb, includeJira: inclJira,
        includeConfluence: inclConfluence, includeGrafana: false, includePipeline: false,
        includeGithub: inclGithub,
        gitlabConfigIds: gitlabIds, databaseConfigIds: dbIds, jiraConfigIds: jiraIds,
        confluenceConfigIds: confluenceIds, githubConfigIds: githubIds,
        jiraProjects: jiraProjects, jiraComponents: jiraComponents, jiraLabels: jiraLabels,
        crossReferenceEnabled: _crossRef
      })
    })
    .then(function(r) { if (!r.ok) return r.text().then(function(txt) { throw new Error(txt); }); return r.json(); })
    .then(function(data) {

      _sessionId = null;
      _searchAbortCtrl = null;
      clearInterval(_ownPollTimer); _ownPollTimer = null;
      var btnSearch2 = document.getElementById('suBtnSearch');
      var btnCancel2 = document.getElementById('suBtnCancel');
      if (btnSearch2) btnSearch2.disabled = false;
      if (btnCancel2) btnCancel2.disabled = true;
      suHideStatus();
      _keywords = []; renderTags();
      fetchAndRenderActiveSessions();

      suOpenSession(data.sessionId, kwStr, 'RUNNING', savedCrossRef);
    })
    .catch(function(err) {
      _searchAbortCtrl = null;
      if (btnSearch) btnSearch.disabled = false;
      if (btnCancel) btnCancel.disabled = true;
      if (err && err.name === 'AbortError') {
        suShowStatusError(t('suche.error_aborted', 'Suche wurde abgebrochen.'));
        return;
      }
      suShowStatusError(t('common.error', 'Fehler') + ': ' + err.message);
    });
  }
  window.suStartSearch = suStartSearch;

  function suCancelSearch() {
    if (_searchAbortCtrl) { _searchAbortCtrl.abort(); _searchAbortCtrl = null; }
    if (_sessionId) {
      fetch('/api/sessions/' + _sessionId + '/cancel', {method: 'POST'}).catch(function() {});
    }
    clearInterval(_ownPollTimer); _ownPollTimer = null;
    _sessionId = null;
    var btnSearch = document.getElementById('suBtnSearch');
    var btnCancel = document.getElementById('suBtnCancel');
    if (btnSearch) btnSearch.disabled = false;
    if (btnCancel) btnCancel.disabled = true;
    suHideStatus();
    fetchAndRenderActiveSessions();
  }
  window.suCancelSearch = suCancelSearch;

  function suDeleteSession(id) {
    fetch('/api/sessions/' + id, {method: 'DELETE'})
      .then(function() {
        if (_viewingId === id) {
          clearInterval(_p2PollTimer); _p2PollTimer = null;
          _viewingId = null;
          try { sessionStorage.removeItem('su_restore_session'); } catch(e) {}
          suShowView('search');
        }
        fetchAndRenderActiveSessions();
      })
      .catch(function() { fetchAndRenderActiveSessions(); });
  }
  window.suDeleteSession = suDeleteSession;

  async function suRerunSession(id) {
    try {
      var resp = await fetch('/api/sessions/' + id);
      if (!resp.ok) return;
      var s = await resp.json();

      _keywords = s.keywords ? s.keywords.split(',').map(function(k) { return k.trim(); }).filter(Boolean) : [];
      renderTags();
      document.getElementById('suKeywordInput').value = '';

      function parseIds(str) {
        if (!str) return [];
        return str.split(',').map(function(v) { return parseInt(v.trim(), 10); }).filter(function(n) { return !isNaN(n); });
      }

      function setChips(containerId, ids) {
        var container = document.getElementById(containerId);
        if (!container) return;
        container.querySelectorAll('input[type=checkbox]').forEach(function(input) {
          var shouldCheck = ids.length === 0 || ids.indexOf(parseInt(input.value, 10)) !== -1;
          input.checked = shouldCheck;
          var lbl = input.closest('label') || input.parentElement;
          if (lbl) lbl.classList.toggle('selected', shouldCheck);
        });
      }

      var cbGitlab = document.getElementById('suIncludeGitlab');
      var cbDb     = document.getElementById('suIncludeDb');
      var cbJira   = document.getElementById('suIncludeJira');
      var cbConf   = document.getElementById('suIncludeConfluence');
      if (cbGitlab) cbGitlab.checked = !!s.includeGitlab;
      if (cbDb)     cbDb.checked     = !!s.includeDatabase;
      if (cbJira)   cbJira.checked   = !!s.includeJira;
      if (cbConf)   cbConf.checked   = !!s.includeConfluence;
      var cbGithub = document.getElementById('suIncludeGithub');
      if (cbGithub) cbGithub.checked = !!s.includeGithub;
      suToggleSource('gitlab');
      suToggleSource('db');
      suToggleSource('jira');
      suToggleSource('confluence');
      suToggleSource('github');
      suSyncPills();

      setChips('suGitlabChips',     parseIds(s.gitlabConfigIds));
      setChips('suDbChips',         parseIds(s.databaseConfigIds));
      setChips('suJiraChips',       parseIds(s.jiraConfigIds));
      setChips('suConfluenceChips', parseIds(s.confluenceConfigIds));
      setChips('suGithubChips',     parseIds(s.githubConfigIds));

      var jpEl = document.getElementById('suJiraProjects');
      var jcEl = document.getElementById('suJiraComponents');
      var jlEl = document.getElementById('suJiraLabels');
      if (jpEl) jpEl.value = s.jiraProjects || '';
      if (jcEl) jcEl.value = s.jiraComponents || '';
      if (jlEl) jlEl.value = s.jiraLabels || '';

      var crEl = document.getElementById('suCrossRef');
      if (crEl) crEl.checked = !!s.crossReferenceEnabled;

      suStartSearch();
    } catch(e) {
      console.error('[SU] rerunSession:', e);
    }
  }
  window.suRerunSession = suRerunSession;

  function suDeleteAllSessions() {
    fetch('/api/sessions', {method: 'DELETE'})
      .then(function() {
        clearInterval(_p2PollTimer); _p2PollTimer = null;
        _viewingId = null;
        try { sessionStorage.removeItem('su_restore_session'); } catch(e) {}
        var results = document.getElementById('suViewResults');
        if (results && results.style.display !== 'none') {
          suShowView('search');
        }
        fetchAndRenderActiveSessions();
      })
      .catch(function() {});
  }
  window.suDeleteAllSessions = suDeleteAllSessions;

  function initChips(configs, containerId, emptyId) {
    var container = document.getElementById(containerId);
    var emptyEl = document.getElementById(emptyId);
    if (!container) return;
    if (!configs || configs.length === 0) {
      if (emptyEl) emptyEl.style.display = '';
      return;
    }
    var isDb = (containerId === 'suDbChips');
    configs.forEach(function(cfg) {
      var chip = document.createElement('label');
      chip.className = 'su-chip selected';
      chip.innerHTML = '<input type="checkbox" value="' + cfg.id + '" checked/>' + escHtml(cfg.name);
      chip.querySelector('input').addEventListener('change', function() {
        chip.classList.toggle('selected', this.checked);
        if (isDb) suDbSyncSelectAllBtn();
      });
      container.appendChild(chip);
    });

    if (containerId === 'suDbChips') {
      var btn = document.getElementById('suDbSelectAllBtn');
      if (btn) { btn.style.display = 'flex'; suDbSyncSelectAllBtn(); }
    }
  }

  function suDbSyncSelectAllBtn() {
    var chips = document.querySelectorAll('#suDbChips .su-chip input');
    var btn = document.getElementById('suDbSelectAllBtn');
    var lbl = document.getElementById('suDbSelectAllLabel');
    if (!btn || chips.length === 0) return;
    var allChecked = Array.from(chips).every(function(c) { return c.checked; });
    btn.classList.toggle('all-selected', allChecked);
    if (lbl) lbl.textContent = allChecked ? 'Alle abw\u00e4hlen' : 'Alle ausw\u00e4hlen';
    btn.querySelector('i').className = allChecked ? 'bi bi-x-lg' : 'bi bi-check-all';
  }
  window.suDbSyncSelectAllBtn = suDbSyncSelectAllBtn;

  function suDbToggleSelectAll() {
    var chips = document.querySelectorAll('#suDbChips .su-chip');
    var inputs = document.querySelectorAll('#suDbChips .su-chip input');
    var allChecked = Array.from(inputs).every(function(c) { return c.checked; });
    var newState = !allChecked;
    chips.forEach(function(chip) {
      var input = chip.querySelector('input');
      if (input) { input.checked = newState; chip.classList.toggle('selected', newState); }
    });
    suDbSyncSelectAllBtn();
  }
  window.suDbToggleSelectAll = suDbToggleSelectAll;

  function suToggleSource(source) {
    var mapping = {
      gitlab:    {checkbox: 'suIncludeGitlab',    chips: 'suGitlabChips',     row: 'suSrcGitlab'},
      db:        {checkbox: 'suIncludeDb',         chips: 'suDbChips',         row: 'suSrcDb'},
      jira:      {checkbox: 'suIncludeJira',       chips: 'suJiraChips',       row: 'suSrcJira'},
      confluence:{checkbox: 'suIncludeConfluence', chips: 'suConfluenceChips', row: 'suSrcConfluence'},
      github:    {checkbox: 'suIncludeGithub',     chips: 'suGithubChips',     row: 'suSrcGithub'}
    };
    var m = mapping[source];
    if (!m) return;
    var enabled = document.getElementById(m.checkbox).checked;
    var grid = document.getElementById(m.chips);
    if (grid) {
      Array.from(grid.querySelectorAll('.su-chip')).forEach(function(c) {
        c.classList.toggle('disabled-chip', !enabled);
      });
    }
    var row = document.getElementById(m.row);
    if (row) row.classList.toggle('su-src-disabled', !enabled);
  }
  window.suToggleSource = suToggleSource;

  function suAddKeyword() {
    var input = document.getElementById('suKeywordInput');
    var val = (input.value || '').trim();
    if (!val) return;
    if (_keywords.indexOf(val) === -1) { _keywords.push(val); renderTags(); }
    input.value = ''; input.focus();
  }
  window.suAddKeyword = suAddKeyword;

  function suRemoveKeyword(kw) {
    _keywords = _keywords.filter(function(k) { return k !== kw; });
    renderTags();
  }
  window.suRemoveKeyword = suRemoveKeyword;

  function renderTags() {
    var list = document.getElementById('suTagList');
    if (!list) return;
    list.innerHTML = '';
    _keywords.forEach(function(kw) {
      var tag = document.createElement('span');
      tag.className = 'su-tag';
      tag.innerHTML = escHtml(kw) +
        '<button class="su-tag-remove" onclick="suRemoveKeyword(' + JSON.stringify(kw) + ')" title="Entfernen">×</button>';
      list.appendChild(tag);
    });
  }

  function suRenderResults(results) {
    var grouped = {GITLAB: [], DATABASE: [], JIRA: [], CONFLUENCE: [], GITHUB: []};
    results.forEach(function(r) {
      var src = (r.sourceType || '').toUpperCase();
      if (src === 'DATABASE') grouped.DATABASE.push(r);
      else if (src === 'JIRA')       grouped.JIRA.push(r);
      else if (src === 'GITHUB')     grouped.GITHUB.push(r);
      else if (src === 'CONFLUENCE') grouped.CONFLUENCE.push(r);
      else                           grouped.GITLAB.push(r);
    });

    _confAllResults = grouped.CONFLUENCE;
    _confPage = 0;

    _confHasMore = _confAllResults.length > 0 && _confAllResults.length % 25 === 0;
    setBadge('suBadgeAll',        results.length);
    setBadge('suBadgeGitlab',     grouped.GITLAB.length);
    setBadge('suBadgeDb',         grouped.DATABASE.length);
    setBadge('suBadgeJira',       grouped.JIRA.length);
    setBadge('suBadgeConfluence', grouped.CONFLUENCE.length);
    setBadge('suBadgeGithub',     grouped.GITHUB.length);
    (function() {
      var el = document.getElementById('suTabAll');
      if (!el) return;
      if (results.length === 0) {
        if (_isViewingLive) {
          el.innerHTML = '<div class="su-tab-searching">' +
            '<div class="su-tab-searching-spinner"></div>' +
            '<div class="su-tab-searching-title">' + t('suche.banner_searching_title', 'Suche l\u00e4uft') + '</div>' +
            '<div class="su-tab-searching-sub">' + t('suche.banner_searching', 'Ergebnisse werden laufend aktualisiert\u2026') + '</div>' +
            '</div>';
        } else {
          el.innerHTML = '<div class="su-empty"><i class="bi bi-search"></i>' +
            t('suche.results_empty', 'Keine Ergebnisse gefunden.') + '</div>';
        }
        return;
      }
      var html = '';
      if (grouped.GITLAB.length)     html += buildGitlabGroupedView(grouped.GITLAB, 'all');
      if (grouped.DATABASE.length)   html += grouped.DATABASE.map(buildResultCard).join('');
      if (grouped.JIRA.length)       html += buildJiraGroupedView(grouped.JIRA);
      if (grouped.CONFLUENCE.length) html += grouped.CONFLUENCE.map(buildConfluenceResultCard).join('');
      if (grouped.GITHUB.length)     html += grouped.GITHUB.map(buildResultCard).join('');
      el.innerHTML = html;
    })();
    renderTabPane('suTabGITLAB',     grouped.GITLAB);
    renderTabPane('suTabDATABASE',   grouped.DATABASE);
    renderTabPane('suTabJIRA',       grouped.JIRA);
    renderTabPane('suTabCONFLUENCE', grouped.CONFLUENCE);
    renderTabPane('suTabGITHUB',     grouped.GITHUB);
  }

  function renderTabPane(id, results) {
    var el = document.getElementById(id);
    if (!el) return;
    if (id === 'suTabCONFLUENCE') { renderConfluenceTab(); return; }
    if (results.length === 0) {
      el.innerHTML = '<div class="su-empty"><i class="bi bi-search"></i>' +
        t('suche.results_empty', 'Keine Ergebnisse gefunden.') + '</div>';
      return;
    }
    if (id === 'suTabJIRA')   { el.innerHTML = buildJiraGroupedView(results); return; }
    if (id === 'suTabGITLAB') { el.innerHTML = buildGitlabGroupedView(results, 'gitlab'); return; }
    el.innerHTML = results.map(buildResultCard).join('');
  }

  function buildConfluenceResultCard(r) {
    var url     = r.repoUrl || '';
    var title   = r.filePath || r.title || '–';
    var space   = r.repoName  || '';
    var excerpt = r.codeSnippet || '';
    var cfg     = r.configName  || '';
    var kw      = r.keyword     || '';

    var titleHtml = url
      ? '<a class="su-conf-result-title-link" href="' + escHtml(url) + '" target="_blank" rel="noopener">' + escHtml(title) + '</a>'
      : escHtml(title);

    var metaParts = [];
    if (space) metaParts.push('<span><i class="bi bi-collection"></i>' + escHtml(space) + '</span>');
    if (cfg)   metaParts.push('<span><i class="bi bi-cloud"></i>' + escHtml(cfg) + '</span>');
    if (kw)    metaParts.push('<span><i class="bi bi-tag"></i>' + escHtml(kw) + '</span>');

    var openBtn = url
      ? '<a class="su-conf-open-btn" href="' + escHtml(url) + '" target="_blank" rel="noopener">' +
        '<i class="bi bi-box-arrow-up-right"></i> In Confluence öffnen</a>'
      : '';

    var urlBar = url
      ? '<div class="su-conf-url-bar"><a class="su-conf-url-text" href="' + escHtml(url) + '" target="_blank" rel="noopener">' +
        escHtml(url.length > 80 ? url.slice(0, 77) + '…' : url) + '</a></div>'
      : '';

    var snippetHtml = excerpt ? '<div class="su-snippet">' + escHtml(excerpt) + '</div>' : '';

    return '<div class="su-result su-conf-result">' +
      '<div class="su-result-header">' +
        '<div style="flex:1;min-width:0;">' +
          '<div class="su-result-title">' + titleHtml + '</div>' +
          '<div class="su-result-meta">' + metaParts.join('') + '</div>' +
          urlBar +
        '</div>' +
        '<div style="display:flex;flex-direction:column;align-items:flex-end;gap:6px;flex-shrink:0;">' +
          '<span class="su-badge su-badge-confluence"><i class="bi bi-journal-text"></i>Confluence</span>' +
          openBtn +
        '</div>' +
      '</div>' +
      snippetHtml +
    '</div>';
  }

  function renderConfluenceTab() {
    var el = document.getElementById('suTabCONFLUENCE');
    if (!el) return;
    var total = _confAllResults.length;

    if (total === 0) {
      el.innerHTML = '<div class="su-empty"><i class="bi bi-search"></i>' +
        t('suche.results_empty', 'Keine Ergebnisse gefunden.') + '</div>';
      return;
    }

    var pageStart  = _confPage * _confPageSize;
    var pageEnd    = Math.min(pageStart + _confPageSize, total);
    var pageSlice  = _confAllResults.slice(pageStart, pageEnd);
    var totalPages = Math.ceil(total / _confPageSize);

    var html = pageSlice.map(buildConfluenceResultCard).join('');

    var onLast = _confPage >= totalPages - 1;
    var weiterDisabled = (onLast && !_confHasMore) || _confLoadingMore;
    var weiterContent = (_confLoadingMore && onLast)
      ? '<div class="su-conf-load-spinner"></div> Lädt…'
      : 'Weiter <i class="bi bi-chevron-right"></i>';
    html += '<div class="su-conf-pager">' +
      '<button class="su-conf-pager-btn" onclick="suConfPrevPage()"' + (_confPage === 0 ? ' disabled' : '') + '>' +
        '<i class="bi bi-chevron-left"></i> Zurück' +
      '</button>' +
      '<span class="su-conf-pager-info">Seite ' + (_confPage + 1) + ' / ' + totalPages + ((_confHasMore && onLast) ? '+' : '') + '</span>' +
      '<button class="su-conf-pager-btn" onclick="suConfNextPage()"' + (weiterDisabled ? ' disabled' : '') + '>' +
        weiterContent +
      '</button>' +
    '</div>';

    html += '<div class="su-conf-total">Insgesamt ' + total + ' Ergebnisse geladen</div>';

    el.innerHTML = html;
  }

  function suConfPrevPage() {
    if (_confPage > 0) {
      _confPage--;
      renderConfluenceTab();
      var el = document.getElementById('suTabCONFLUENCE');
      if (el) el.scrollIntoView({behavior: 'smooth', block: 'start'});
    }
  }
  window.suConfPrevPage = suConfPrevPage;

  function suConfNextPage() {
    var totalPages = Math.ceil(_confAllResults.length / _confPageSize);
    if (_confPage < totalPages - 1) {
      _confPage++;
      renderConfluenceTab();
      var el = document.getElementById('suTabCONFLUENCE');
      if (el) el.scrollIntoView({behavior: 'smooth', block: 'start'});
    } else if (_confHasMore && !_confLoadingMore) {

      suConfLoadMore(true);
    }
  }
  window.suConfNextPage = suConfNextPage;

  function suConfLoadMore(autoAdvance) {
    if (_confLoadingMore || !_viewingId) return;
    _confLoadingMore = true;
    renderConfluenceTab();
    suFetch('/api/sessions/' + _viewingId + '/confluence/more', {method: 'POST'}, 120000)
      .then(function(r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function(data) {
        _confHasMore = data.hasMore === true;
        if (data.addedCount > 0) {
          return suFetch('/api/sessions/' + _viewingId + '/results?sourceType=CONFLUENCE', null, 20000)
            .then(function(r2) { return r2.json(); })
            .then(function(newResults) {
              _confAllResults = newResults;
              if (autoAdvance) _confPage++;
              setBadge('suBadgeConfluence', _confAllResults.length);
              _confLoadingMore = false;
              renderConfluenceTab();
              var el = document.getElementById('suTabCONFLUENCE');
              if (el && autoAdvance) el.scrollIntoView({behavior: 'smooth', block: 'start'});
            });
        } else {
          _confHasMore = false;
          _confLoadingMore = false;
          renderConfluenceTab();
        }
      })
      .catch(function() {
        _confLoadingMore = false;
        _confHasMore = false;
        renderConfluenceTab();
      });
  }
  window.suConfLoadMore = suConfLoadMore;

  function suShowStatus(msg, isError) {
    var bar = document.getElementById('suStatusBar');
    var text = document.getElementById('suStatusText');
    var spinner = document.getElementById('suStatusSpinner');
    if (!bar || !text) return;
    bar.style.display = 'flex';
    bar.classList.toggle('error', !!isError);
    text.textContent = msg;
    if (spinner) { spinner.style.display = isError ? 'none' : ''; spinner.classList.toggle('error-spinner', !!isError); }
  }
  function suShowStatusError(msg) { suShowStatus(msg, true); }
  function suHideStatus() { var bar = document.getElementById('suStatusBar'); if (bar) bar.style.display = 'none'; }
  function setBadge(id, count) { var el = document.getElementById(id); if (el) el.textContent = count; }
  function getCheckedIds(gridId) {
    var grid = document.getElementById(gridId);
    if (!grid) return [];
    return Array.from(grid.querySelectorAll('input[type=checkbox]:checked')).map(function(i) { return parseInt(i.value, 10); });
  }
  function splitComma(id) {
    var el = document.getElementById(id);
    if (!el || !el.value.trim()) return [];
    return el.value.split(',').map(function(s) { return s.trim(); }).filter(Boolean);
  }
  function truncate(s, n) { return s.length > n ? s.slice(0, n) + '…' : s; }
  function t(key, fallback) {
    return (window.slHelpers && window.slHelpers.t) ? window.slHelpers.t(key, fallback) : (fallback != null ? fallback : key);
  }

  function suTogglePill(source) {
    var map = {
      gitlab:    { checkbox: 'suIncludeGitlab',    pill: 'suPillGitlab' },
      db:        { checkbox: 'suIncludeDb',         pill: 'suPillDb' },
      jira:      { checkbox: 'suIncludeJira',       pill: 'suPillJira' },
      confluence:{ checkbox: 'suIncludeConfluence', pill: 'suPillConfluence' },
      github:    { checkbox: 'suIncludeGithub',     pill: 'suPillGithub' }
    };
    var m = map[source]; if (!m) return;
    var cb   = document.getElementById(m.checkbox);
    var pill = document.getElementById(m.pill);
    if (cb) cb.checked = !cb.checked;
    if (pill && cb) {
      pill.classList.toggle('on',  cb.checked);
      pill.classList.toggle('off', !cb.checked);
    }
    suToggleSource(source);

    if (source === 'db' && cb && cb.checked) {
      var dbChips  = document.querySelectorAll('#suDbChips .su-chip');
      var dbInputs = document.querySelectorAll('#suDbChips .su-chip input');
      var anyUnchecked = Array.from(dbInputs).some(function(i) { return !i.checked; });
      if (anyUnchecked) {
        dbChips.forEach(function(chip) {
          var inp = chip.querySelector('input');
          if (inp) { inp.checked = true; chip.classList.add('selected'); }
        });
        if (typeof suDbSyncSelectAllBtn === 'function') suDbSyncSelectAllBtn();
      }
    }
  }
  window.suTogglePill = suTogglePill;

  function suToggleAdvanced() {
    var panel = document.getElementById('suAdvancedPanel');
    var chev  = document.getElementById('suAdvChevron');
    if (!panel) return;
    var open = panel.style.display !== 'none';
    panel.style.display = open ? 'none' : 'block';
    if (chev) chev.style.transform = open ? '' : 'rotate(180deg)';
  }
  window.suToggleAdvanced = suToggleAdvanced;

  function suSyncPills() {
    var map = {
      gitlab:    { checkbox: 'suIncludeGitlab',    pill: 'suPillGitlab' },
      db:        { checkbox: 'suIncludeDb',         pill: 'suPillDb' },
      jira:      { checkbox: 'suIncludeJira',       pill: 'suPillJira' },
      confluence:{ checkbox: 'suIncludeConfluence', pill: 'suPillConfluence' },
      github:    { checkbox: 'suIncludeGithub',     pill: 'suPillGithub' }
    };
    Object.keys(map).forEach(function(source) {
      var m    = map[source];
      var cb   = document.getElementById(m.checkbox);
      var pill = document.getElementById(m.pill);
      if (!cb || !pill) return;
      pill.classList.toggle('on',  cb.checked);
      pill.classList.toggle('off', !cb.checked);
    });
  }

  function suResetButtonState() {
    var btnS = document.getElementById('suBtnSearch');
    var btnC = document.getElementById('suBtnCancel');
    if (btnS) btnS.disabled = false;
    if (btnC) btnC.disabled = true;
    suHideStatus();
  }

  function boot() {

    suResetButtonState();

    initChips(SU_GITLAB_CONFIGS,     'suGitlabChips',     'suGitlabEmpty');
    initChips(SU_DB_CONFIGS,         'suDbChips',         'suDbEmpty');
    initChips(SU_JIRA_CONFIGS,       'suJiraChips',       'suJiraEmpty');
    initChips(SU_CONFLUENCE_CONFIGS, 'suConfluenceChips', 'suConfluenceEmpty');
    initChips(SU_GITHUB_CONFIGS,     'suGithubChips',     'suGithubEmpty');

    var kwInput = document.getElementById('suKeywordInput');
    if (kwInput) {
      kwInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
          e.preventDefault();
          suStartSearch();
        }
      });
    }

    startActivePoll();

    var openId = new URLSearchParams(window.location.search).get('session');
    if (openId) {
      fetch('/api/sessions')
        .then(function(r) { return r.json(); })
        .then(function(sessions) {
          var s = sessions.find(function(x) { return String(x.id) === openId; });
          if (s) suOpenSession(s.id, s.keywords || '', s.status, false);
        })
        .catch(function() {});
    } else {

      try {
        var saved = sessionStorage.getItem('su_restore_session');
        if (saved) {
          var rs = JSON.parse(saved);
          if (rs && rs.id) {

            fetch('/api/sessions/' + rs.id)
              .then(function(r) { if (!r.ok) throw new Error(); return r.json(); })
              .then(function(s) {

                suOpenSession(s.id, s.keywords || rs.keywords || '', s.status, rs.crossRef || false);
              })
              .catch(function() {

                try { sessionStorage.removeItem('su_restore_session'); } catch(e2) {}
              });
          }
        }
      } catch(e) {}
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }

  window.addEventListener('pageshow', function(e) {
    if (e.persisted) {
      clearInterval(_p2PollTimer); _p2PollTimer = null;
      clearInterval(_aiPollTimer); _aiPollTimer = null;
      suResetButtonState();
    }
  });

  window._suCleanupTimers = function() {
    clearInterval(_activePollTmr); _activePollTmr = null;
    clearInterval(_p2PollTimer);   _p2PollTimer   = null;
    clearInterval(_aiPollTimer);   _aiPollTimer   = null;
    clearInterval(_ownPollTimer);  _ownPollTimer  = null;
  };

  if (window._suNavigateHandler) document.removeEventListener('app:navigate', window._suNavigateHandler);
  window._suNavigateHandler = window._suCleanupTimers;
  document.addEventListener('app:navigate', window._suNavigateHandler);
})();


