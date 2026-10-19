  if (!window._pa) window._pa = { gen: 0, pollTimer: null };
  window._pa.gen++;
  if (window._pa.pollTimer) { clearInterval(window._pa.pollTimer); window._pa.pollTimer = null; }
  window._pa.analysisId = window._pa.analysisId || null;

  var paCurrentRepoId       = null;
  var paCurrentRepoName     = '';
  var paCurrentAnalysisId   = null;
  var paCurrentTab          = 'paTabSummary';
  var paPollTimer           = window._pa.pollTimer;
  function _paSyncTimer(v) { paPollTimer = v; window._pa.pollTimer = v; }
  var paGitlabConfigId      = null;
  var paGitlabProjectId     = null;
  var paGitlabProjectPath   = null;
  var paGithubRepoConfigId  = null;
  var paStatusPollTimer     = null;
  var paMermaidStore        = null;
  var paAllFindings         = [];
  var paFileDescriptions    = [];
  var paOptimizations       = [];
  var paStartGen            = 0;
  var paHistoryList         = [];
  var paNavGen              = (window._paNavGen || 0) + 1;
  window._paNavGen          = paNavGen;

  function paT(key) {
    return window.slHelpers ? window.slHelpers.t(key) : key;
  }

  function paEsc(str) {
    return window.slHelpers.esc(str);
  }

  function paSaveState() {
    if (paCurrentRepoId !== null || paGitlabConfigId !== null || paGithubRepoConfigId !== null || paCurrentAnalysisId !== null) {
      window.PageStorage.writeJson('pa_state', {
        analysisId:        paCurrentAnalysisId,
        repoId:            paCurrentRepoId,
        repoName:          paCurrentRepoName,
        gitlabConfigId:    paGitlabConfigId,
        gitlabProjectId:   paGitlabProjectId,
        gitlabProjectPath: paGitlabProjectPath,
        githubRepoConfigId: paGithubRepoConfigId,
        tab:               paCurrentTab,
      });
    } else {
      window.PageStorage.remove('pa_state');
    }
  }
  window.paSaveState = paSaveState;

  function paClearState() {
    window.PageStorage.remove('pa_state');
  }

  function paRestoreState() {
    var saved = window.PageStorage.readJson('pa_state', null);
    if (!saved) return;
    paRestoreGitlabAccordion();
    paRestoreGithubAccordion();

    var aid = saved.analysisId || null;
    if (saved.githubRepoConfigId) {
      var gh = document.getElementById('paGhCard-' + saved.githubRepoConfigId);
      if (gh) paSelectGithub(saved.githubRepoConfigId, saved.repoName || gh.dataset.githubRepoName || '', aid);
    } else if (saved.gitlabConfigId && saved.gitlabProjectId) {
      paSelectGitlabAfterProjectsLoad(saved.gitlabConfigId, saved.gitlabProjectId, saved.gitlabProjectPath || '', saved.repoName || '', aid);
    } else if (saved.repoId) {
      var localCard = document.getElementById('repoCard-' + saved.repoId);
      if (localCard) paSelectLocal(saved.repoId, saved.repoName || localCard.dataset.repoName || '', aid);
    }
  }

  function paSelectGitlabAfterProjectsLoad(configId, projectId, projectPath, repoName, preferredAnalysisId) {
    var attempts = 0;
    var maxAttempts = 40;
    var gen = paNavGen;
    function tick() {
      if (gen !== paNavGen) return;
      var card = document.querySelector('.pa-gitlab-project[data-config-id="' + configId + '"][data-project-id="' + projectId + '"]');
      if (card) {
        paSelectGitlab(configId, projectId, projectPath, repoName, preferredAnalysisId);
        return;
      }
      attempts++;
      if (attempts >= maxAttempts) return;
      var acc = document.querySelector('.pa-gitlab-cfg .pa-gitlab-header[data-cfg-id="' + configId + '"]');
      var toggle = window.paGitlabToggleConfig;
      if (acc && typeof toggle === 'function') {
        var body = acc.nextElementSibling;
        var userClosed = false;
        userClosed = window.PageStorage.read('pa_gl_open_' + configId) === '0';
        if (body && body.classList.contains('d-none') && !userClosed) {
          toggle(acc, configId);
        }
      }
      setTimeout(tick, 250);
    }
    tick();
  }

  function paRestoreGitlabAccordion() {
    if (typeof paGitlabRestoreAccordion === 'function') paGitlabRestoreAccordion();
  }

  function paRestoreGithubAccordion() {
    var ghReposDiv = document.getElementById('paGithubRepoList');
    if (!ghReposDiv) return;
    var ghOpen = _paGhGetPref('pa_gh_open');
    if (!ghOpen) {
      var saved = window.PageStorage.readJson('pa_state', null);
      if (saved && saved.githubRepoConfigId) ghOpen = '1';
    }
    var isOpen = !ghReposDiv.classList.contains('d-none');
    if (ghOpen === '1' && !isOpen) {
      var ghHeader = ghReposDiv.previousElementSibling;
      if (ghHeader) paToggleGithubAccordion(ghHeader);
    } else if (ghOpen === '0' && isOpen) {
      var ghHeader2 = ghReposDiv.previousElementSibling;
      if (ghHeader2) paToggleGithubAccordion(ghHeader2);
    }
  }

  function paSwitch(panelId, btn) {
    document.querySelectorAll('.pa-tab-panel').forEach(function (p) { p.classList.remove('active'); });
    document.querySelectorAll('.pa-tab-btn').forEach(function (b) { b.classList.remove('active'); });
    var panel = document.getElementById(panelId);
    if (panel) panel.classList.add('active');
    if (btn) btn.classList.add('active');
    paCurrentTab = panelId || paCurrentTab;
    paSaveState();
    if (panelId === 'paTabMermaid' && paMermaidStore) {
      paRenderMermaid(paMermaidStore);
    }

    var _paResultContent = document.getElementById('paResultScrollContent');
    if (_paResultContent && _paResultContent.__slScroll && _paResultContent.__slScroll.update) {
      requestAnimationFrame(function () { _paResultContent.__slScroll.update(); });
    }
  }

  var _paEscStack = window._paEscStack || (window._paEscStack = []);

  var _paEscListener = window._paEscListener || (window._paEscListener = function (e) {
    if (e.key !== 'Escape' && e.key !== 'Esc') return;
    var stack = window._paEscStack;
    while (stack.length && !document.getElementById(stack[stack.length - 1].elementId)) {
      stack.pop();
    }
    var top = stack[stack.length - 1];
    if (!top) return;
    e.preventDefault();
    top.close();
  });

  function paBindEscape(elementId, close) {
    paUnbindEscape(elementId);
    _paEscStack.push({ elementId: elementId, close: close });
    if (_paEscStack.length === 1) window.addEventListener('keydown', _paEscListener, true);
  }

  function paUnbindEscape(elementId) {
    for (var i = _paEscStack.length - 1; i >= 0; i--) {
      if (_paEscStack[i].elementId === elementId) {
        _paEscStack.splice(i, 1);
        break;
      }
    }
    if (!_paEscStack.length) window.removeEventListener('keydown', _paEscListener, true);
  }

  function paShowModal(id) {
    var el = document.getElementById(id);
    if (!el) return;
    el.className = (el.className || '').replace(/\bshow\b/g, '').trim() + ' show';
    el.style.setProperty('display', 'block', 'important');
    el.removeAttribute('aria-hidden');
    el.setAttribute('aria-modal', 'true');
    document.body.className = (document.body.className || '').replace(/\bmodal-open\b/g, '').trim() + ' modal-open';
    if (!document.querySelector('.modal-backdrop')) {
      var bd = document.createElement('div');
      bd.className = 'modal-backdrop fade show';
      bd.style.setProperty('display', 'block', 'important');
      document.body.appendChild(bd);
    }
    paBindEscape(id, function () { paHideModal(id); });
  }

  function paHideModal(id) {
    var el = document.getElementById(id);
    if (!el) return;
    el.className = (el.className || '').replace(/\bshow\b/g, '').trim();
    el.style.setProperty('display', 'none', 'important');
    el.setAttribute('aria-hidden', 'true');
    el.removeAttribute('aria-modal');
    var bd = document.querySelector('.modal-backdrop');
    if (bd) bd.parentNode.removeChild(bd);
    if (!document.querySelector('.modal.show')) {
      document.body.className = (document.body.className || '').replace(/\bmodal-open\b/g, '').trim();
    }
    paUnbindEscape(id);
  }

  window.localRepoConfig = {
    scope: 'PIPELINE_ANALYSIS',
    listContainerId: 'repoList',
    cardIdPrefix: 'repoCard-',
    cardClass: 'pa-repo-card',
    refreshCard: function (repo) { if (typeof paRefreshRepoCard === 'function') paRefreshRepoCard(repo); },
    onDeleted: function (id) {
      if (id === paCurrentRepoId) {
        paCurrentRepoId = null;
        paCurrentRepoName = '';
        paCurrentAnalysisId = null;
        window._pa.analysisId = paCurrentAnalysisId;
        if (typeof paClearState === 'function') paClearState();
        if (typeof paResetContent === 'function') paResetContent();
      }
    }
  };

  function paRefreshRepoCard(repo) {
    var list = document.getElementById('repoList');
    if (!list) { location.reload(); return; }
    var existing = document.getElementById('repoCard-' + repo.id);
    if (existing) {
      existing.querySelector('.pa-repo-name').textContent = repo.name;
      existing.querySelector('.pa-repo-path').textContent = repo.path;
      existing.setAttribute('data-repo-name', repo.name);
      existing.setAttribute('data-repo-path', repo.path);
      existing.setAttribute('data-repo-active', repo.active);
      existing.classList.toggle('pa-repo-inactive', !repo.active);
    } else {
      var card = document.createElement('div');
      card.className = 'pa-repo-card' + (repo.active ? '' : ' pa-repo-inactive');
      card.id = 'repoCard-' + repo.id;
      card.setAttribute('data-repo-id', repo.id);
      card.setAttribute('data-repo-name', repo.name);
      card.setAttribute('data-repo-path', repo.path);
      card.setAttribute('data-repo-active', repo.active);
      card.innerHTML =
        '<div class="pa-repo-row1"><div class="pa-repo-name">' + paEsc(repo.name) + '</div></div>' +
        '<div class="pa-repo-path">' + paEsc(repo.path) + '</div>' +
        '<div class="pa-card-status"></div>' +
        '<div class="pa-btn-row">' +
          '<button class="pa-btn-xs pa-btn-xs-success" data-action="play"><i class="bi bi-play-fill"></i> ' + paT('pipeline.btn_start') + '</button>' +
          '<button class="pa-btn-xs pa-btn-xs-cancel"  data-action="cancel"><i class="bi bi-x-circle"></i></button>' +
          '<button class="pa-btn-xs pa-btn-xs-outline" data-action="edit"><i class="bi bi-pencil"></i></button>' +
          '<button class="pa-btn-xs pa-btn-xs-outline" data-action="delete"><i class="bi bi-trash"></i></button>' +
        '</div>';
      list.appendChild(card);
      var cards = Array.prototype.slice.call(list.querySelectorAll('.pa-repo-card'));
      cards.sort(function (a, b) {
        return (a.getAttribute('data-repo-name') || '').localeCompare(b.getAttribute('data-repo-name') || '');
      });
      cards.forEach(function (c) { list.appendChild(c); });
      card.addEventListener('click', function (e) {
        if (e.target.closest('button')) return;
        paSelectLocal(repo.id, repo.name);
      });
      card.querySelector('[data-action="play"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        paSelectLocal(repo.id, repo.name);
        paStartLocal(repo.id, repo.name);
      });
      card.querySelector('[data-action="cancel"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        paCancelForCard(card);
      });
      card.querySelector('[data-action="edit"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        editRepo(repo.id);
      });
      card.querySelector('[data-action="delete"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        deleteRepo(repo.id);
      });
    }
  }

  function paPageMounted() {
    return !!document.getElementById('paAnalysisPanel');
  }

  function paShowUserPromptCard() {
    var card = document.getElementById('paUserPromptCard');
    if (card) card.classList.remove('d-none');
  }

  function paSelectLocal(id, name, preferredAnalysisId) {
    if (!paPageMounted()) return;
    paCurrentRepoId = id;
    paCurrentRepoName = name;
    paGithubRepoConfigId = null;
    paGitlabConfigId = null;
    paGitlabProjectId = null;
    paGitlabProjectPath = null;
    document.querySelectorAll('.pa-repo-card').forEach(function (c) { c.classList.remove('selected'); });
    var card = document.getElementById('repoCard-' + id);
    if (card) card.classList.add('selected');
    paShowUserPromptCard();
    if (preferredAnalysisId) {
      paLoadAnalysisById(preferredAnalysisId);
    } else {
      paLoadLatest(id, 'local');
    }
    paSaveState();
  }

  function paSelectGithub(id, name, preferredAnalysisId) {
    if (!paPageMounted()) return;
    paGithubRepoConfigId = id;
    paCurrentRepoName = name;
    paCurrentRepoId = null;
    paGitlabConfigId = null;
    paGitlabProjectId = null;
    paGitlabProjectPath = null;
    document.querySelectorAll('.pa-repo-card').forEach(function (c) { c.classList.remove('selected'); });
    document.querySelectorAll('[id^="paGhCard-"]').forEach(function (c) { c.classList.remove('selected'); });
    var card = document.getElementById('paGhCard-' + id);
    if (card) card.classList.add('selected');
    paShowUserPromptCard();
    if (preferredAnalysisId) {
      paLoadAnalysisById(preferredAnalysisId);
    } else {
      paLoadLatest(id, 'github');
    }
    paSaveState();
  }

  function paSelectGitlab(configId, projectId, projectPath, name, preferredAnalysisId) {
    if (!paPageMounted()) return;
    paGitlabConfigId = configId;
    paGitlabProjectId = projectId;
    paGitlabProjectPath = projectPath;
    paCurrentRepoName = name || projectPath;
    paCurrentRepoId = null;
    paGithubRepoConfigId = null;
    document.querySelectorAll('.pa-repo-card').forEach(function (c) { c.classList.remove('selected'); });
    document.querySelectorAll('.pa-gitlab-project').forEach(function (c) { c.classList.remove('selected'); });
    var proj = document.querySelector('.pa-gitlab-project[data-config-id="' + configId + '"][data-project-id="' + projectId + '"]');
    if (proj) proj.classList.add('selected');
    paShowUserPromptCard();
    if (preferredAnalysisId) {
      paLoadAnalysisById(preferredAnalysisId);
    } else {
      paLoadLatest({ configId: configId, projectId: projectId }, 'gitlab');
    }
    paSaveState();
  }

  function paUserPromptValue() {
    var el = document.getElementById('paUserPromptInput');
    if (!el) return '';
    return (el.value || '').trim();
  }

  async function paStartCurrent() {
    if (paCurrentRepoId) {
      paStartLocal(+paCurrentRepoId, paCurrentRepoName);
    } else if (paGitlabConfigId && paGitlabProjectId) {
      paStartGitlab(+paGitlabConfigId, +paGitlabProjectId, paGitlabProjectPath || '', paCurrentRepoName);
    } else if (paGithubRepoConfigId) {
      paStartGithub(+paGithubRepoConfigId, paCurrentRepoName);
    } else {
      showAlert(paT('pipeline.select_hint'), 'warning');
    }
  }

  async function paStartLocal(repoConfigId, repoName) {
    var gen = ++paStartGen;
    try {
      try {
        await fetch('/api/pipeline-analysis/cancel-latest-for-repo/' + repoConfigId, { method: 'POST' });
      } catch (e) {
        console.warn('Pre-cancel (local) failed', e);
      }
      if (gen !== paStartGen) return;
      var resp = await fetch('/api/pipeline-analysis/start-local', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ repoConfigId: repoConfigId, userPrompt: paUserPromptValue() })
      });
      var data = await resp.json();
      if (!resp.ok || !data.analysisId) {
        showAlert((data && data.error) || paT('pipeline.be_unknown_error'), 'danger');
        return;
      }
      if (gen !== paStartGen) return;
      paCurrentAnalysisId = data.analysisId;
      window._pa.analysisId = paCurrentAnalysisId;
      if (typeof paInitTokenUsageBar === 'function') paInitTokenUsageBar();
      paCurrentRepoId = repoConfigId;
      paCurrentRepoName = repoName || paCurrentRepoName;
      paSaveState();
      paShowRunningState();
      paStartPolling();
      paRefreshCardStatuses();
    } catch (e) {
      showAlert(paT('common.network_error') + ' ' + e.message, 'danger');
    }
  }

  async function paStartGitlab(configId, projectId, projectPath, repoName) {
    var gen = ++paStartGen;
    try {
      try {
        await fetch('/api/pipeline-analysis/cancel-latest-for-gitlab/' + configId + '/' + projectId, { method: 'POST' });
      } catch (e) {
        console.warn('Pre-cancel (gitlab) failed', e);
      }
      if (gen !== paStartGen) return;
      var resp = await fetch('/api/pipeline-analysis/start-gitlab', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          gitlabConfigId: configId, projectId: projectId, projectPath: projectPath,
          userPrompt: paUserPromptValue()
        })
      });
      var data = await resp.json();
      if (!resp.ok || !data.analysisId) {
        showAlert((data && data.error) || paT('pipeline.be_unknown_error'), 'danger');
        return;
      }
      if (gen !== paStartGen) return;
      paCurrentAnalysisId = data.analysisId;
      window._pa.analysisId = paCurrentAnalysisId;
      if (typeof paInitTokenUsageBar === 'function') paInitTokenUsageBar();
      paGitlabConfigId = configId;
      paGitlabProjectId = projectId;
      paGitlabProjectPath = projectPath;
      paCurrentRepoName = repoName || projectPath || paCurrentRepoName;
      paSaveState();
      paShowRunningState();
      paStartPolling();
      paRefreshCardStatuses();
    } catch (e) {
      showAlert(paT('common.network_error') + ' ' + e.message, 'danger');
    }
  }

  async function paStartGithub(repoConfigId, repoName) {
    var gen = ++paStartGen;
    try {
      try {
        await fetch('/api/pipeline-analysis/cancel-latest-for-github/' + repoConfigId, { method: 'POST' });
      } catch (e) {
        console.warn('Pre-cancel (github) failed', e);
      }
      if (gen !== paStartGen) return;
      var resp = await fetch('/api/pipeline-analysis/start-github', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ githubRepoConfigId: repoConfigId, userPrompt: paUserPromptValue() })
      });
      var data = await resp.json();
      if (!resp.ok || !data.analysisId) {
        showAlert((data && data.error) || paT('pipeline.be_unknown_error'), 'danger');
        return;
      }
      if (gen !== paStartGen) return;
      paCurrentAnalysisId = data.analysisId;
      window._pa.analysisId = paCurrentAnalysisId;
      if (typeof paInitTokenUsageBar === 'function') paInitTokenUsageBar();
      paGithubRepoConfigId = repoConfigId;
      paCurrentRepoName = repoName || paCurrentRepoName;
      paSaveState();
      paShowRunningState();
      paStartPolling();
      paRefreshCardStatuses();
    } catch (e) {
      showAlert(paT('common.network_error') + ' ' + e.message, 'danger');
    }
  }

  async function paCancelCurrent() {
    if (!paCurrentAnalysisId) return;
    try {
      await fetch('/api/pipeline-analysis/analysis/' + paCurrentAnalysisId + '/cancel', { method: 'POST' });
    } catch (e) {
      console.warn('Cancel failed', e);
    }
    if (paPollTimer) { clearInterval(paPollTimer); _paSyncTimer(null); }
    paShowAbortedState();
    paRefreshCardStatuses();
    paLoadHistory();
  }

  async function paCancelLatestRunning(kind, id, subId) {
    var url = kind === 'gitlab'
      ? '/api/pipeline-analysis/cancel-latest-for-gitlab/' + id + '/' + subId
      : kind === 'github'
        ? '/api/pipeline-analysis/cancel-latest-for-github/' + id
        : '/api/pipeline-analysis/cancel-latest-for-repo/' + id;
    try {
      await fetch(url, { method: 'POST' });
    } catch (e) {
      console.warn('Cancel failed', e);
    }
    paRefreshCardStatuses();
  }

  function paCancelForCard(card) {
    if (!card) return;
    var cardId = card.id || '';
    if (cardId.indexOf('paGhCard-') === 0) {
      var ghId = +cardId.replace('paGhCard-', '');
      if (paGithubRepoConfigId === ghId) { paCancelCurrent(); return; }
      paCancelLatestRunning('github', ghId);
      return;
    }
    if (cardId.indexOf('repoCard-') === 0) {
      var repoId = +cardId.replace('repoCard-', '');
      if (paCurrentRepoId == repoId) { paCancelCurrent(); return; }
      paCancelLatestRunning('local', repoId);
      return;
    }
    if (card.classList.contains('pa-gitlab-project')) {
      var cfgId = +card.dataset.configId;
      var projId = +card.dataset.projectId;
      if (paGitlabConfigId == cfgId && paGitlabProjectId == projId) { paCancelCurrent(); return; }
      paCancelLatestRunning('gitlab', cfgId, projId);
    }
  }

  function paShowRunningState() {
    paHideAllStates();
    document.getElementById('paRunning').classList.remove('d-none');
    document.getElementById('paAnalysisTabs').classList.remove('d-none');
    var header = document.getElementById('paAnalysisHeader');
    if (header) header.innerHTML = '<div class="d-flex align-items-center gap-2"><span class="spinner-border spinner-border-sm"></span><span class="fw-bold">' + paT('pipeline.analyzing') + '</span><span class="text-muted ms-2">– ' + paEsc(paCurrentRepoName || '') + '</span></div>';
  }

  function paShowAbortedState() {
    paHideAllStates();
    document.getElementById('paAborted').classList.remove('d-none');
    document.getElementById('paAnalysisTabs').classList.add('d-none');
    document.getElementById('paAnalysisHeader').innerHTML =
      '<div class="pa-empty" style="padding:20px;flex:1;min-height:auto;justify-content:flex-start;flex-direction:row;gap:10px;">' +
      '<i class="bi bi-x-circle"></i><span class="text-muted" style="font-size:14px;">' + paT('pipeline.aborted') + '</span></div>';
  }

  function paResetContent() {
    paHideAllStates();
    document.getElementById('paEmptyState').classList.remove('d-none');
    document.getElementById('paAnalysisTabs').classList.add('d-none');
    document.getElementById('paAnalysisHeader').innerHTML =
      '<div class="pa-empty" style="padding:20px;flex:1;min-height:auto;justify-content:flex-start;flex-direction:row;gap:10px;">' +
      '<i class="bi bi-arrow-left"></i>' +
      '<span class="text-muted" style="font-size:14px;">' + paT('pipeline.no_analysis') + '</span></div>';
  }

  function paHideAllStates() {
    ['paEmptyState', 'paRunning', 'paAborted', 'paSummaryContent'].forEach(function (id) {
      var el = document.getElementById(id);
      if (el) el.classList.add('d-none');
    });
    var err = document.getElementById('paError');
    if (err) { err.classList.add('d-none'); err.textContent = ''; }
  }

  function paStartPolling() {
    if (paPollTimer) clearInterval(paPollTimer);
    _paSyncTimer(setInterval(paPollStatus, 2500));
    paPollStatus();
  }

  async function paPollStatus() {
    if (!paCurrentAnalysisId) return;
    var _pollGen = window._pa.gen;
    try {
      var resp = await fetch('/api/pipeline-analysis/analysis/' + paCurrentAnalysisId + '/status');
      if (_pollGen !== window._pa.gen) return;
      if (!resp.ok) {
        if (resp.status === 404) {
          if (paPollTimer) clearInterval(paPollTimer);
          _paSyncTimer(null);
        }
        return;
      }
      var data = await resp.json();
      if (_pollGen !== window._pa.gen) return;
      if (data.id !== paCurrentAnalysisId) return;
      if (data.status === 'COMPLETED') {
        if (paPollTimer) clearInterval(paPollTimer);
        _paSyncTimer(null);
        paRenderResult(data);
        await paRefreshCardStatuses();
      } else if (data.status === 'FAILED' || data.status === 'CANCELLED') {
        if (paPollTimer) clearInterval(paPollTimer);
        _paSyncTimer(null);
        if (data.status === 'CANCELLED') {
          paShowAbortedState();
        } else {
          paShowError(data.errorMessage || paT('pipeline.be_unknown_error'));
        }
        paRefreshCardStatuses();
      }
    } catch (e) {
      console.warn('poll failed', e);
    }
  }

  function paShowError(msg) {
    paHideAllStates();
    var err = document.getElementById('paError');
    if (err) {
      err.classList.remove('d-none');
      err.textContent = msg;
    }
    var _tabs = document.getElementById('paAnalysisTabs');
    var _head = document.getElementById('paAnalysisHeader');
    if (!_tabs || !_head) return;
    _tabs.classList.add('d-none');
    _head.innerHTML =
      '<div class="pa-empty" style="padding:20px;flex:1;min-height:auto;justify-content:flex-start;flex-direction:row;gap:10px;">' +
      '<i class="bi bi-exclamation-triangle"></i><span class="text-muted" style="font-size:14px;">' + paT('pipeline.be_unknown_error') + '</span></div>';
  }

  function paRenderResult(data) {
    if (!document.getElementById('paAnalysisTabs')) return;
    paHideAllStates();
    document.getElementById('paAnalysisTabs').classList.remove('d-none');
    document.getElementById('paAnalysisHeader').innerHTML =
      '<div class="d-flex align-items-center gap-2">' +
      '<i class="bi bi-diagram-3" style="color:var(--pa-accent);"></i>' +
      '<span class="fw-bold">' + paEsc(paCurrentRepoName || '') + '</span>' +
      '<span class="badge ms-2" style="background:#d4edda;color:#155724;">' + paT('pipeline.status_completed') + '</span>' +
      '</div>';

    var summary = data.summaryText || '';
    var summaryEl = document.getElementById('paSummaryContent');
    summaryEl.classList.remove('d-none');
    summaryEl.innerHTML = paRenderMarkdown(summary);

    paAllFindings = paParseJsonSafe(data.findingsJson) || [];
    paRenderFindings();

    paFileDescriptions = paParseJsonSafe(data.fileDescriptionsJson) || [];
    paRenderFiles();

    paOptimizations = paParseJsonSafe(data.optimizationsJson) || [];
    paRenderOptimizations();

    paMermaidStore = data.mermaid || null;
    if (paMermaidStore) {
      document.getElementById('paMermaidEmpty').classList.add('d-none');
      document.getElementById('paMermaidContainer').classList.remove('d-none');
      document.getElementById('paMermaidActions').classList.remove('d-none');
    } else {
      document.getElementById('paMermaidEmpty').classList.remove('d-none');
      document.getElementById('paMermaidContainer').classList.add('d-none');
      document.getElementById('paMermaidActions').classList.add('d-none');
    }

    var badge = document.getElementById('paFindingsBadge');
    if (badge) {
      var highCount = paAllFindings.filter(function (f) { return f.severity === 'HIGH'; }).length;
      if (highCount > 0) {
        badge.textContent = highCount;
        badge.style.display = 'inline-block';
      } else {
        badge.style.display = 'none';
      }
    }

    var allTextEl = document.getElementById('paAllFindings');
    if (allTextEl) {
      allTextEl.innerText = paAllFindings.map(function (f) {
        return '[' + (f.severity || '?') + '] ' + (f.file || '?') + ': ' + (f.description || '');
      }).join('\n');
    }

    paApplyMrBannerFromData(data);

    paRestoreTab();

    if (typeof paInitTokenUsageBar === 'function') paInitTokenUsageBar();
  }

  function paRestoreTab() {
    var saved = paCurrentTab || 'paTabSummary';
    var btn = null;
    document.querySelectorAll('.pa-tab-btn').forEach(function (b) {
      try {
        if (b.getAttribute('onclick') && b.getAttribute('onclick').indexOf("'" + saved + "'") >= 0) btn = b;
      } catch (_) {}
    });
    if (!btn) btn = document.querySelector('.pa-tab-btn');
    if (btn) paSwitch(saved, btn);
  }

  function paRenderMarkdown(text) {
    if (!text) return '';
    if (typeof window.marked !== 'undefined' && window.marked.parse) {
      try { return window.marked.parse(text); } catch (e) {}
    }
    return '<pre style="white-space:pre-wrap;font-family:inherit;">' + paEsc(text) + '</pre>';
  }

  function paParseJsonSafe(s) {
    if (!s) return null;
    try { return JSON.parse(s); } catch (_) { return null; }
  }

  function paRenderFindings() {
    var root = document.getElementById('paFindingsContent');
    if (!paAllFindings.length) {
      root.innerHTML = '<div class="pa-empty"><i class="bi bi-check2-circle"></i><span>' + paT('pipeline.no_findings') + '</span></div>';
      return;
    }
    var html = '';
    paAllFindings.forEach(function (f) {
      var sev = (f.severity || 'LOW').toUpperCase();
      html += '<div class="pa-finding-card">';
      html += '<div class="pa-finding-header">';
      html += '<span class="pa-badge pa-badge-' + paEsc(sev) + '">' + paEsc(sev) + '</span>';
      html += '<span class="pa-badge" style="background:#f0e9fb;color:#5b3da5;">' + paEsc(f.type || 'UNKNOWN') + '</span>';
      if (f.file) html += '<code style="font-size:12px;">' + paEsc(f.file) + (f.line ? ':' + paEsc(String(f.line)) : '') + '</code>';
      html += '</div>';
      if (f.description) html += '<div class="pa-finding-desc">' + paEsc(f.description) + '</div>';
      if (f.suggestion) html += '<div class="pa-finding-suggestion"><strong>' + paT('pipeline.tab_optimizations') + ':</strong> ' + paEsc(f.suggestion) + '</div>';
      if (f.snippet) html += '<div class="pa-code-block" style="margin-bottom:6px;">' + paEsc(f.snippet) + '</div>';
      if (f.suggestedCode) html += '<div class="pa-code-block" style="background:#d4edda;color:#155724;">' + paEsc(f.suggestedCode) + '</div>';
      html += '</div>';
    });
    root.innerHTML = html;
  }

  function paRenderFiles() {
    var root = document.getElementById('paFilesContent');
    if (!paFileDescriptions.length) {
      root.innerHTML = '<div class="pa-empty"><i class="bi bi-files"></i><span>' + paT('pipeline.no_descriptions') + '</span></div>';
      return;
    }
    var html = '';
    paFileDescriptions.forEach(function (f) {
      html += '<div class="pa-file-card">';
      html += '<div class="pa-file-head">';
      html += '<span class="pa-file-path">' + paEsc(f.path || '') + '</span>';
      if (f.fileKind) html += '<span class="pa-file-kind">' + paEsc(f.fileKind) + '</span>';
      html += '</div>';
      if (f.description) html += '<div class="pa-file-desc">' + paEsc(f.description) + '</div>';
      var keys = f.keys || [];
      if (keys.length) {
        html += '<table class="pa-file-keys"><thead><tr>';
        html += '<th>' + paT('pipeline.key_label') + '</th>';
        html += '<th>' + paT('pipeline.value_label') + '</th>';
        html += '<th>' + paT('pipeline.meaning_label') + '</th>';
        html += '</tr></thead><tbody>';
        keys.forEach(function (k) {
          html += '<tr>';
          html += '<td class="pa-key">' + paEsc(k.key || '') + '</td>';
          html += '<td class="pa-val">' + paEsc(k.value !== undefined && k.value !== null ? String(k.value) : '') + '</td>';
          html += '<td class="pa-mean">' + paEsc(k.meaning || '') + '</td>';
          html += '</tr>';
        });
        html += '</tbody></table>';
      }
      html += '</div>';
    });
    root.innerHTML = html;
  }

  function paRenderOptimizations() {
    var root = document.getElementById('paOptimizationsContent');
    if (!paOptimizations.length) {
      root.innerHTML = '<div class="pa-empty"><i class="bi bi-stars"></i><span>' + paT('pipeline.no_optimizations') + '</span></div>';
      return;
    }
    var html = '';
    paOptimizations.forEach(function (o) {
      html += '<div class="pa-opt-card">';
      html += '<div class="pa-opt-title">' + paEsc(o.title || '') + '</div>';
      if (o.rationale) html += '<div class="pa-opt-rationale">' + paEsc(o.rationale) + '</div>';
      if (o.patchSnippet) html += '<div class="pa-code-block">' + paEsc(o.patchSnippet) + '</div>';
      html += '</div>';
    });
    root.innerHTML = html;
  }

  async function paLoadLatest(idOrPair, source) {
    try {
      var url;
      if (source === 'local') {
        url = '/api/pipeline-analysis/local/' + idOrPair + '/latest';
      } else if (source === 'github') {
        url = '/api/pipeline-analysis/github/' + idOrPair + '/latest';
      } else if (source === 'gitlab') {
        url = '/api/pipeline-analysis/gitlab/' + idOrPair.configId + '/project/' + idOrPair.projectId + '/latest';
      }
      var resp = await fetch(url);
      if (!resp.ok) return;
      var data = await resp.json();
      if (data.status === 'NONE') {
        paHideAllStates();
        document.getElementById('paEmptyState').classList.remove('d-none');
        document.getElementById('paAnalysisTabs').classList.add('d-none');
        document.getElementById('paAnalysisHeader').innerHTML =
          '<div class="pa-empty" style="padding:20px;flex:1;min-height:auto;justify-content:flex-start;flex-direction:row;gap:10px;">' +
          '<i class="bi bi-arrow-left"></i>' +
          '<span class="text-muted" style="font-size:14px;">' + paT('pipeline.no_analysis') + '</span></div>';
        return;
      }
      paCurrentAnalysisId = data.id;
      window._pa.analysisId = paCurrentAnalysisId;
      if (data.status === 'COMPLETED') {
        paRenderResult(data);
      } else if (data.status === 'FAILED' || data.status === 'CANCELLED') {
        if (data.status === 'CANCELLED') paShowAbortedState(); else paShowError(data.errorMessage);
      } else {
        paShowRunningState();
        paStartPolling();
      }
    } catch (e) {
      console.warn('loadLatest failed', e);
    }
  }

  function paStopStatusPoll() { if (paStatusPollTimer) { clearInterval(paStatusPollTimer); paStatusPollTimer = null; } }
  function paStartStatusPollIfNeeded(list) {
    var hasRunning = (list || []).some(function (a) { return a.status === 'RUNNING' || a.status === 'PENDING'; });
    if (hasRunning && !paStatusPollTimer) {
      paStatusPollTimer = setInterval(paRefreshCardStatuses, 5000);
    } else if (!hasRunning) {
      paStopStatusPoll();
    }
  }

  async function paRefreshCardStatuses() {
    try {
      var resp = await fetch('/api/pipeline-analysis/all');
      if (!resp.ok) return;
      var list = await resp.json();
      paHistoryList = list || [];
      paApplyCardStatuses(paHistoryList);
      paStartStatusPollIfNeeded(paHistoryList);
      paLoadHistory();
    } catch (e) {}
  }

  function paApplyCardStatuses(list) {
    function key(a) {
      if (a.repoConfigId) return 'l:' + a.repoConfigId;
      if (a.gitlabConfigId && a.gitlabProjectId) return 'g:' + a.gitlabConfigId + ':' + a.gitlabProjectId;
      if (a.githubRepoConfigId) return 'h:' + a.githubRepoConfigId;
      return 'id:' + a.id;
    }
    var byKey = {};
    list.forEach(function (a) {
      var k = key(a);
      if (!byKey[k]) byKey[k] = a;
    });

    document.querySelectorAll('[id^="repoCard-"]').forEach(function (card) {
      var rid = card.id.replace('repoCard-', '');
      var a = byKey['l:' + rid];
      paUpdateCardStatus(card, a);
    });
    document.querySelectorAll('[id^="paGhCard-"]').forEach(function (card) {
      var hid = card.id.replace('paGhCard-', '');
      var a = byKey['h:' + hid];
      paUpdateCardStatus(card, a);
    });
    document.querySelectorAll('.pa-gitlab-project').forEach(function (card) {
      var cid = card.dataset.configId;
      var pid = card.dataset.projectId;
      var a = byKey['g:' + cid + ':' + pid];
      paUpdateCardStatus(card, a);
    });
  }

  function paSetCardBusy(card, busy) {
    if (!card) return;
    var btnRow = card.querySelector('.pa-btn-row');
    if (!btnRow) return;
    ['[data-action="play"]', '[data-action="gl-play"]', '[data-action="gh-play"]',
      '[data-action="edit"]', '[data-action="delete"]'].forEach(function (sel) {
      var btn = btnRow.querySelector(sel);
      if (!btn) return;
      btn.disabled = !!busy;
      btn.style.opacity = busy ? '0.5' : '';
      btn.style.cursor = busy ? 'not-allowed' : '';
    });
    ['[data-action="cancel"]', '[data-action="gl-cancel"]', '[data-action="gh-cancel"]'].forEach(function (sel) {
      var btn = btnRow.querySelector(sel);
      if (!btn) return;
      btn.disabled = !busy;
      btn.style.opacity = busy ? '' : '0.4';
      btn.style.cursor = busy ? '' : 'not-allowed';
    });
  }

  function paAnalysisIsRunning(analysis) {
    return !!analysis && (analysis.status === 'RUNNING' || analysis.status === 'PENDING');
  }

  function paUpdateCardStatus(card, analysis) {
    var slot = card.querySelector('.pa-card-status');
    if (slot) {
      slot.innerHTML = '';
      if (analysis) {
        var pill = document.createElement('span');
        pill.className = 'pa-card-status-pill';
        var st = analysis.status || '';
        if (st === 'RUNNING' || st === 'PENDING') {
          pill.classList.add('pa-card-status--running');
          pill.innerHTML = '<span class="spinner-border spinner-border-sm" style="width:9px;height:9px;"></span> ' + paT('pipeline.running_status');
        } else if (st === 'COMPLETED') {
          pill.classList.add('pa-card-status--completed');
          pill.innerHTML = '<i class="bi bi-check-circle-fill"></i> ' + paT('pipeline.status_completed');
        } else if (st === 'CANCELLED') {
          pill.classList.add('pa-card-status--aborted');
          pill.innerHTML = '<i class="bi bi-x-circle-fill"></i> ' + paT('pipeline.status_aborted');
        } else if (st === 'FAILED') {
          pill.classList.add('pa-card-status--aborted');
          pill.innerHTML = '<i class="bi bi-exclamation-triangle-fill"></i> ' + paT('pipeline.status_error');
        }
        slot.appendChild(pill);
      }
    }
    paSetCardBusy(card, paAnalysisIsRunning(analysis));
  }

  var _paHistory = null;

  function paRepoNameForAnalysis(a) {
    if (a.gitlabProjectPath) return a.gitlabProjectPath;
    if (a.repoConfigId) {
      var card = document.getElementById('repoCard-' + a.repoConfigId);
      return card ? (card.dataset.repoName || ('Lokal #' + a.repoConfigId)) : ('Lokal #' + a.repoConfigId);
    }
    if (a.githubRepoConfigId) {
      var gh = document.getElementById('paGhCard-' + a.githubRepoConfigId);
      return gh ? (gh.dataset.githubRepoName || ('GitHub #' + a.githubRepoConfigId)) : ('GitHub #' + a.githubRepoConfigId);
    }
    return '#' + a.id;
  }

  function paHistoryRef(a) {
    if (a.gitlabProjectPath) return 'GitLab';
    if (a.githubRepoConfigId) return 'GitHub';
    if (a.repoConfigId) return 'Lokal';
    return '';
  }

  function paHistoryFindings(a) {
    if (window.slHistory.status(a.status) !== 'COMPLETED') return '';
    if (!a.findingsJson) return '0';
    try {
      var parsed = JSON.parse(a.findingsJson);
      return Array.isArray(parsed) ? String(parsed.length) : '';
    } catch (_) { return ''; }
  }

  function paHistory() {
    if (_paHistory) return _paHistory;
    _paHistory = window.slHistory.mount({
      root: 'hxCard-pipeline-analysis',
      page: 'pipeline-analysis',
      titleKey: 'pipeline.history_title',
      emptyKey: 'pipeline.history_none_entries',
      columns: {
        title: { key: 'pipeline.col_repo', fallback: 'Repository' },
        metric: { key: 'common.history_col_findings', fallback: 'Befunde' }
      },
      map: {
        id: function(a) { return a.id; },
        status: function(a) { return a.status; },
        createdAt: function(a) { return a.createdAt; },
        typeIcon: function(a) {
          if (a.gitlabProjectPath) return 'bi-gitlab';
          if (a.githubRepoConfigId) return 'bi-github';
          return 'bi-repo';
        },
        title: paRepoNameForAnalysis,
        subtitle: paHistoryRef,
        metric: paHistoryFindings,
        note: function(a) { return a.errorMessage || ''; },
        isCurrent: function(a) { return paCurrentAnalysisId === a.id; }
      },
      actions: {
        open: function(a) { paLoadAnalysisById(a.id); },
        cancel: function(a) { paCancelHistoryItem(a.id); },
        remove: function(a) { paDeleteAnalysisById(a.id); },
        deleteAll: function() { paDeleteAllHistory(); }
      }
    });
    return _paHistory;
  }

  function paLoadHistory() {
    var list = (paHistoryList || []).slice().sort(function (a, b) { return (b.id || 0) - (a.id || 0); });
    paHistory().setData(list);
  }

  async function paCancelHistoryItem(id) {
    try {
      await fetch('/api/pipeline-analysis/analysis/' + id + '/cancel', { method: 'POST' });
    } catch (e) {
      console.warn('Cancel failed', e);
    }
    if (paCurrentAnalysisId === id) {
      if (paPollTimer) { clearInterval(paPollTimer); _paSyncTimer(null); }
      paShowAbortedState();
    }
    await paRefreshCardStatuses();
    paLoadHistory();
  }

  async function paLoadAnalysisById(id) {
    if (paCurrentAnalysisId === id) return;
    if (paPollTimer) { clearInterval(paPollTimer); _paSyncTimer(null); }
    try {
      var resp = await fetch('/api/pipeline-analysis/analysis/' + id + '/status');
      if (!resp.ok) {
        if (resp.status === 404) {
          await paRefreshCardStatuses();
        }
        return;
      }
      var data = await resp.json();
      paCurrentAnalysisId = id;
      window._pa.analysisId = paCurrentAnalysisId;
      paCurrentRepoId       = data.repoConfigId       || null;
      paGitlabConfigId      = data.gitlabConfigId     || null;
      paGitlabProjectId     = data.gitlabProjectId    || null;
      paGitlabProjectPath   = data.gitlabProjectPath  || null;
      paGithubRepoConfigId  = data.githubRepoConfigId || null;
      if (data.gitlabProjectPath) paCurrentRepoName = data.gitlabProjectPath;
      else if (data.repoConfigId) {
        var lc = document.getElementById('repoCard-' + data.repoConfigId);
        if (lc && lc.dataset.repoName) paCurrentRepoName = lc.dataset.repoName;
      } else if (data.githubRepoConfigId) {
        var gc = document.getElementById('paGhCard-' + data.githubRepoConfigId);
        if (gc && gc.dataset.githubRepoName) paCurrentRepoName = gc.dataset.githubRepoName;
      }
      document.querySelectorAll('.pa-repo-card').forEach(function (c) {
        var match = false;
        if (paCurrentRepoId && c.dataset.repoId == paCurrentRepoId && !c.dataset.cfgId) match = true;
        if (paGitlabConfigId && c.dataset.cfgId == paGitlabConfigId && c.dataset.projectId == paGitlabProjectId) match = true;
        if (paGithubRepoConfigId && c.dataset.githubRepoId == paGithubRepoConfigId) match = true;
        c.classList.toggle('selected', match);
      });
      paShowUserPromptCard();

      if (data.status === 'COMPLETED') {
        paRenderResult(data);
      } else if (data.status === 'FAILED' || data.status === 'CANCELLED') {
        if (data.status === 'CANCELLED') paShowAbortedState(); else paShowError(data.errorMessage);
      } else {
        paShowRunningState();
        paStartPolling();
      }
      paSaveState();
      paLoadHistory();
    } catch (e) {
      console.warn('loadAnalysisById failed', e);
    }
  }

  async function paDeleteAnalysisById(id) {
    try {
      await fetch('/api/pipeline-analysis/analysis/' + id, { method: 'DELETE' });
      if (paCurrentAnalysisId === id) {
        paCurrentAnalysisId = null;
        window._pa.analysisId = paCurrentAnalysisId;
        paClearState();
        paResetContent();
      }
      await paRefreshCardStatuses();
      paLoadHistory();
    } catch (e) {
      console.warn('delete failed', e);
    }
  }

  async function paDeleteAllHistory() {
    try {
      var list = paHistoryList || [];
      var deletable = list.filter(function (a) {
        return a.status !== 'RUNNING' && a.status !== 'PENDING';
      });
      var chain = Promise.resolve();
      deletable.forEach(function (item) {
        chain = chain.then(function () {
          return fetch('/api/pipeline-analysis/analysis/' + item.id, { method: 'DELETE' }).catch(function () {});
        });
      });
      await chain;
      if (deletable.some(function (a) { return a.id === paCurrentAnalysisId; })) {
        paCurrentAnalysisId = null;
        window._pa.analysisId = paCurrentAnalysisId;
        paClearState();
        paResetContent();
      }
      await paRefreshCardStatuses();
      paLoadHistory();
    } catch (e) {
      console.warn('deleteAll failed', e);
    }
  }

  function paToggleGithubAccordion(head) {
    var body = head.nextElementSibling;
    if (!body) return;
    var willOpen = body.classList.contains('d-none');
    if (willOpen) {
      body.classList.remove('d-none');
    } else {
      body.classList.add('d-none');
    }
    var chev = head.querySelector('.pa-gh-chevron');
    if (chev) {
      chev.classList.remove('bi-chevron-up');
      chev.classList.remove('bi-chevron-down');
      chev.classList.add(willOpen ? 'bi-chevron-up' : 'bi-chevron-down');
    }
    _paGhSetPref('pa_gh_open', willOpen ? '1' : '0');
  }

  function _paGhSetPref(key, value) {
  window.slHelpers.setPref(key, value);
}
  function _paGhGetPref(key) {
  return window.slHelpers.getPref(key);
}

  function paInit() {
    if (!paPageMounted()) {
      paNavGen = (window._paNavGen || 0) + 1;
      window._paNavGen = paNavGen;
      return;
    }
    paNavGen = (window._paNavGen || 0) + 1;
    window._paNavGen = paNavGen;

    document.querySelectorAll('[id^="repoCard-"]').forEach(function (card) {
      var id = +card.id.replace('repoCard-', '');
      card.addEventListener('click', function (e) {
        if (e.target.closest('button')) return;
        paSelectLocal(id, card.dataset.repoName || '');
      });
      card.querySelector('[data-action="play"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        paSelectLocal(id, card.dataset.repoName || '');
        paStartLocal(id, card.dataset.repoName || '');
      });
      card.querySelector('[data-action="cancel"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        paCancelForCard(card);
      });
      card.querySelector('[data-action="edit"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        editRepo(id);
      });
      card.querySelector('[data-action="delete"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        deleteRepo(id);
      });
    });
    document.querySelectorAll('[id^="paGhCard-"]').forEach(function (card) {
      var id = +card.id.replace('paGhCard-', '');
      card.querySelector('[data-action="gh-play"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        paSelectGithub(id, card.dataset.githubRepoName || '');
        paStartGithub(id, card.dataset.githubRepoName || '');
      });
      card.querySelector('[data-action="gh-cancel"]')?.addEventListener('click', function (e) {
        e.stopPropagation();
        paCancelForCard(card);
      });
    });

    paRestoreGithubAccordion();

    if (window.SLScroll) {
      var _paRepoContent = document.getElementById('paRepoScrollContent');
      var _paRepoTrack   = document.getElementById('paRepoScrollTrack');
      var _paRepoThumb   = document.getElementById('paRepoScrollThumb');
      if (_paRepoContent && _paRepoTrack && _paRepoThumb) {
        _paRepoContent.__slScroll = window.SLScroll(_paRepoContent, _paRepoTrack, _paRepoThumb);
      }
      var _paResultContent = document.getElementById('paResultScrollContent');
      var _paResultTrack   = document.getElementById('paResultScrollTrack');
      var _paResultThumb   = document.getElementById('paResultScrollThumb');
      if (_paResultContent && _paResultTrack && _paResultThumb) {
        _paResultContent.__slScroll = window.SLScroll(_paResultContent, _paResultTrack, _paResultThumb);
      }
    }

    paRefreshCardStatuses();
    setTimeout(function () {
      if (paNavGen === window._paNavGen) paRestoreState();
    }, 100);
  }

  function paResetMrBanner() {
    paHideEl('paMrResult');
    paHideEl('paMrError');
    var banner = document.getElementById('paMrBanner');
    if (banner) banner.classList.remove('d-none');
    var btns = document.getElementById('paMrBannerBtns');
    if (btns) {
      btns.innerHTML =
        '<button class="btn btn-sm pa-btn-mr-create" id="paMrCreateBtn" onclick="paOpenMrDialog()">' +
          '<i class="bi bi-git me-1"></i>' +
          (window.i18n ? window.i18n.t('pipeline.mr_banner_btn') : 'MR/PR erstellen') +
        '</button>';
    }
  }

  function paHideEl(id) {
    var el = document.getElementById(id);
    if (el && !el.classList.contains('d-none')) el.classList.add('d-none');
  }
  function paShowEl(id) {
    var el = document.getElementById(id);
    if (el) el.classList.remove('d-none');
  }

  function paMrAnalysisSource() {
    if (paGitlabConfigId && paGitlabProjectId) return 'gitlab';
    if (paGithubRepoConfigId) return 'github';
    if (paCurrentRepoId) return 'local';
    return 'none';
  }

  function paResolveMrAnalysisId() {
    if (paCurrentAnalysisId) return Promise.resolve(paCurrentAnalysisId);
    var url = null;
    if (paCurrentRepoId) url = '/api/pipeline-analysis/local/' + paCurrentRepoId + '/latest';
    else if (paGitlabConfigId && paGitlabProjectId) url = '/api/pipeline-analysis/gitlab/' + paGitlabConfigId + '/project/' + paGitlabProjectId + '/latest';
    else if (paGithubRepoConfigId) url = '/api/pipeline-analysis/github/' + paGithubRepoConfigId + '/latest';
    if (!url) return Promise.resolve(null);
    return fetch(url).then(function (r) { return r.json(); }).then(function (data) {
      if (data && data.status && data.status !== 'NONE' && data.id) {
        paCurrentAnalysisId = data.id;
        window._pa.analysisId = paCurrentAnalysisId;
        return data.id;
      }
      return null;
    }).catch(function () { return null; });
  }

  function paOpenMrDialog() {
    paResolveMrAnalysisId().then(function (analysisId) {
      if (!analysisId) {
        paShowMrError(window.i18n ? window.i18n.t('pipeline.no_analysis') : 'Keine Analyse vorhanden.');
        return;
      }
      if (paMrAnalysisSource() !== 'gitlab') {
        paDoCreateMr(analysisId, null, null);
        return;
      }
      var sel = document.getElementById('paMrGitlabConfigSelect');
      if (sel) sel.value = paGitlabConfigId || '';
      var projRow = document.getElementById('paMrProjectRow');
      if (projRow) projRow.style.display = paGitlabConfigId ? '' : 'none';
      paHideEl('paMrModalError');
      if (paGitlabConfigId) paMrLoadProjects();
      if (typeof showModalById === 'function') showModalById('paMrModal');
      else paShowModal('paMrModal');
    });
  }

  function paMrLoadProjects() {
    var sel = document.getElementById('paMrGitlabConfigSelect');
    if (!sel) return;
    var configId = sel.value;
    var projRow = document.getElementById('paMrProjectRow');
    var projSel = document.getElementById('paMrProjectSelect');
    if (!configId) {
      if (projRow) projRow.style.display = 'none';
      return;
    }
    if (!projSel) return;
    projSel.innerHTML = '<option>' + (window.i18n ? window.i18n.t('common.loading') : 'Wird geladen…') + '</option>';
    if (projRow) projRow.style.display = '';
    fetch('/api/local-repos/gitlab/' + configId + '/projects')
      .then(function (r) { return r.json(); })
      .then(function (projects) {
        projSel.innerHTML = '<option value="">' + (window.i18n ? window.i18n.t('code_analysis.select_project_ph') : '— Projekt wählen —') + '</option>';
        (projects || []).forEach(function (p) {
          var opt = document.createElement('option');
          opt.value = p.id;
          opt.textContent = p.path;
          projSel.appendChild(opt);
        });
        if (paGitlabProjectId) {
          for (var i = 0; i < projSel.options.length; i++) {
            if (+projSel.options[i].value === paGitlabProjectId) {
              projSel.selectedIndex = i;
              break;
            }
          }
        }
      })
      .catch(function () {
        projSel.innerHTML = '<option value="">' + (window.i18n ? window.i18n.t('common.error') : 'Fehler') + '</option>';
      });
  }

  function paSubmitMr() {
    var sel = document.getElementById('paMrGitlabConfigSelect');
    var projSel = document.getElementById('paMrProjectSelect');
    var configId = sel ? parseInt(sel.value, 10) : 0;
    var projectId = projSel ? parseInt(projSel.value, 10) : 0;
    if (!configId || !projectId) {
      var errEl = document.getElementById('paMrModalError');
      if (errEl) {
        errEl.textContent = window.i18n ? window.i18n.t('pipeline.mr_select_hint') : 'Bitte GitLab-Konfiguration und Projekt auswählen.';
        errEl.classList.remove('d-none');
      }
      return;
    }
    if (typeof hideModalById === 'function') hideModalById('paMrModal');
    else paHideModal('paMrModal');
    paDoCreateMr(paCurrentAnalysisId, configId, projectId);
  }

  function paDoCreateMr(analysisId, configId, projectId) {
    if (!analysisId) {
      paShowMrError(window.i18n ? window.i18n.t('pipeline.no_analysis') : 'Keine Analyse vorhanden.');
      return;
    }
    paHideEl('paMrResult');
    paHideEl('paMrError');
    var btns = document.getElementById('paMrBannerBtns');
    if (btns) {
      btns.innerHTML =
        '<span style="font-size:12px;color:var(--pa-muted);">' +
          '<span class="spinner-border spinner-border-sm me-1"></span>' +
          (window.i18n ? window.i18n.t('pipeline.mr_creating') : 'Erstelle MR/PR…') +
        '</span>';
    }
    var body = {};
    if (configId) body.gitlabConfigId = configId;
    if (projectId) body.gitlabProjectId = projectId;
    fetch('/api/pipeline-analysis/analysis/' + analysisId + '/create-mr-or-pr', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        if (data && data.error) {
          paShowMrError(data.error);
          paResetMrBanner();
          return;
        }
        if (!data || !data.url) {
          paShowMrError(window.i18n ? window.i18n.t('common.be_unknown_error') : 'Unbekannter Fehler');
          paResetMrBanner();
          return;
        }
        paRenderMrResult(analysisId, data);
      })
      .catch(function (e) {
        paShowMrError((window.i18n ? window.i18n.t('common.error') : 'Fehler') + ': ' + (e.message || e));
        paResetMrBanner();
      });
  }

  function paOpenExternalUrl(url) {
    if (!url) return;
    var ua = (navigator && navigator.userAgent) || '';
    var isDesktop =
      !!(window.desktopApp || window.__desktopAppReady) || /JavaFX/i.test(ua);
    var absUrl = url;
    if (!/^https?:\/\//i.test(url)) {
      absUrl =
        window.location.origin +
        (url.charAt(0) === '/' ? url : '/' + url);
    }
    if (isDesktop) {
      if (
        window.desktopApp &&
        typeof window.desktopApp.openInBrowser === 'function'
      ) {
        try {
          window.desktopApp.openInBrowser(absUrl);
          return;
        } catch (_) {
        }
      }
      fetch('/api/open-url', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: absUrl }),
      }).catch(function () {
        window.open(absUrl, '_blank', 'noopener,noreferrer');
      });
    } else {
      window.open(absUrl, '_blank', 'noopener,noreferrer');
    }
  }
  window.paOpenExternalUrl = paOpenExternalUrl;

  function paRenderMrResult(analysisId, data) {
    var label = data.provider || '';
    var providerLabel = '';
    if (label === 'GITLAB') providerLabel = window.i18n ? window.i18n.t('pipeline.mr_provider_gitlab') : 'GitLab Merge Request';
    else if (label === 'GITHUB') providerLabel = window.i18n ? window.i18n.t('pipeline.mr_provider_github') : 'GitHub Pull Request';
    else providerLabel = window.i18n ? window.i18n.t('pipeline.mr_provider_simulated') : 'Simulierter MR/PR';

    var url = data.url;
    var isAbsolute = /^https?:\/\//i.test(url);
    var link = document.getElementById('paMrLink');
    if (link) {
      link.href = url;
      link.textContent = isAbsolute ? url : (providerLabel + ' #' + analysisId);
      link.setAttribute('target', '_blank');
      link.setAttribute('rel', 'noopener noreferrer');
      link.setAttribute(
        'onclick',
        'paOpenExternalUrl(this.href); return false;'
      );
    }
    var labelEl = document.getElementById('paMrProviderLabel');
    if (labelEl) labelEl.textContent = providerLabel;
    paShowEl('paMrResult');
    var bannerText = document.getElementById('paMrBannerText');
    if (bannerText) bannerText.textContent = window.i18n ? window.i18n.t('pipeline.mr_already_created') : 'MR/PR bereits erstellt';
    var bannerBtns = document.getElementById('paMrBannerBtns');
    if (bannerBtns) {
      bannerBtns.innerHTML =
        '<a href="' + paEsc(url) + '" target="_blank" rel="noopener noreferrer" onclick="paOpenExternalUrl(this.href); return false;" class="btn btn-sm pa-btn-mr-create">' +
          '<i class="bi bi-box-arrow-up-right me-1"></i>' +
          (window.i18n ? window.i18n.t('pipeline.mr_open_btn') : 'MR/PR öffnen') +
        '</a>';
    }
    paAllAnalysesCacheId = analysisId;
  }

  function paShowMrError(msg) {
    var errEl = document.getElementById('paMrError');
    if (!errEl) return;
    errEl.textContent = msg;
    paShowEl('paMrError');
  }

  function paApplyMrBannerFromData(data) {
    if (!data) return;
    var findings = [];
    try { findings = data.findingsJson ? JSON.parse(data.findingsJson) : []; } catch (_) { findings = []; }
    if (!findings.length) {
      paHideEl('paMrBanner');
      paHideEl('paMrResult');
      paHideEl('paMrError');
      return;
    }
    paShowEl('paMrBanner');
    if (data.mrUrl) {
      paRenderMrResult(data.id, { url: data.mrUrl, provider: data.mrProvider || 'SIMULATED', kind: data.mrProvider === 'GITHUB' ? 'pull_request' : 'merge_request' });
    } else {
      paResetMrBanner();
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', paInit);
  }
  if (window._paNavigateHandler) document.removeEventListener('app:navigate', window._paNavigateHandler);
  window._paNavigateHandler = paInit;
  document.addEventListener('app:navigate', window._paNavigateHandler);
