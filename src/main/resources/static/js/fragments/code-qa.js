(function () {
  if (!window._mr) window._mr = {};
  window._mr.analysisId = null;
  var _mrAnalysisId    = null;
  var _mrPollTimer     = null;
  var _mrEventSource   = null;
  var _mrItEventSource = null;
  var _mrStreamAccum   = '';
  var _mrResultScroll  = null;
  var _mrThinkingScroll = null;
  var _mrItScroll       = null;
  var _mrCodeSuggestWanted = false;

  function q(id) { return document.getElementById(id); }

  function mrT(key, fallback) {
    return (window.slHelpers && window.slHelpers.t) ? window.slHelpers.t(key, fallback) : fallback;
  }

  function mrItResetStatusPanel() { return window.MrItPanel.mrItResetStatusPanel.apply(null, arguments); }
  function mrItStopElapsed() { return window.MrItPanel.mrItStopElapsed.apply(null, arguments); }
  function mrItStatusFromStream() { return window.MrItPanel.mrItStatusFromStream.apply(null, arguments); }

  function mrUpdateBtn() {
    var url = (q('mrInputUrl') ? q('mrInputUrl').value : '').trim();
    var btn = q('mrBtnStart');
    if (btn) btn.disabled = !url;
  }

  function mrInitUi() {
    var urlInput = q('mrInputUrl');
    if (urlInput) urlInput.addEventListener('input', mrUpdateBtn);
    mrUpdateBtn();

    document.querySelectorAll('.mr-tab').forEach(function (tab) {
      tab.addEventListener('click', function () {
        var target = tab.dataset.tab;
        document.querySelectorAll('.mr-tab').forEach(function (t) { t.classList.remove('active'); });
        document.querySelectorAll('.mr-tab-pane').forEach(function (p) { p.classList.remove('active'); });
        tab.classList.add('active');
        var pane = document.getElementById('mrPane' + target);
        if (pane) pane.classList.add('active');
      });
    });
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mrInitUi);
  } else {
    mrInitUi();
  }

  window.startMrAnalysis = async function () {
    var mrUrl = (q('mrInputUrl') ? q('mrInputUrl').value : '').trim();
    if (!mrUrl) {
      showAlert(window.i18n ? window.i18n.t('mr_analysis.alert_enter_url') : 'Bitte MR-URL eingeben', 'warning');
      return;
    }
    var jiraTicketKey = q('mrInputJiraKey') ? q('mrInputJiraKey').value.trim() : '';
    var jiraConfigId  = q('mrSelectJiraConfig') ? q('mrSelectJiraConfig').value : '';
    var gitConfigSel  = q('mrSelectGitConfig');
    var gitConfigId   = '';
    var gitPlatform   = '';
    if (gitConfigSel && gitConfigSel.value) {
      gitConfigId  = gitConfigSel.value;
      var selOpt   = gitConfigSel.options[gitConfigSel.selectedIndex];
      gitPlatform  = selOpt ? (selOpt.dataset.platform || '') : '';
    }
    _mrCodeSuggestWanted = !!(q('mrChkCodeSuggestions') && q('mrChkCodeSuggestions').checked);
    window._mr.integrationTestWanted = !!(q('mrChkIntegrationTest') && q('mrChkIntegrationTest').checked);
    var additionalInstructions = q('mrInputAdditionalInstructions') ? q('mrInputAdditionalInstructions').value.trim() : '';

    var btn = q('mrBtnStart');
    if (btn) btn.disabled = true;

    mrShowProgress();

    try {
      var body = { mrUrl: mrUrl };
      if (jiraTicketKey)           body.jiraTicketKey            = jiraTicketKey;
      if (jiraConfigId)            body.jiraConfigId             = jiraConfigId;
      if (gitConfigId)             body.gitConfigId              = gitConfigId;
      if (gitPlatform)             body.gitPlatform              = gitPlatform;
      if (additionalInstructions)  body.additionalInstructions   = additionalInstructions;
      if (window._mr.integrationTestWanted) body.integrationTestEnabled = 'true';

      var resp = await fetch('/api/mr-analysis', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      });

      if (!resp.ok) {
        var errText = await resp.text();
        var errMsg = errText;
        try { var parsed = JSON.parse(errText); errMsg = parsed.error || errText; } catch (_) {}
        showAlert((window.i18n ? window.i18n.t('mr_analysis.start_error') : 'Fehler: ') + errMsg, 'danger');
        mrResetView();
        return;
      }

      var data = await resp.json();
      _mrAnalysisId = data.id;
      window._mr.analysisId = _mrAnalysisId;
      if (typeof mrInitTokenUsageBar === 'function') mrInitTokenUsageBar();
      window._mr.runningId = data.id;
      mrStartStream(data.id);
      mrStartPolling();
    } catch (e) {
      showAlert((window.i18n ? window.i18n.t('common.network_error') : 'Netzwerkfehler: ') + e.message, 'danger');
      mrResetView();
    }
  };

  function mrStartPolling() {
    if (_mrPollTimer) clearInterval(_mrPollTimer);
    _mrPollTimer = setInterval(mrPoll, 2000);
  }

  function mrStopPolling() {
    if (_mrPollTimer) { clearInterval(_mrPollTimer); _mrPollTimer = null; }
  }

  function mrStartStream(id) {
    mrStopStream();
    _mrStreamAccum = '';
    _mrEventSource = new EventSource('/api/mr-analysis/' + id + '/stream');

    _mrEventSource.addEventListener('chunk', function (e) {
      _mrStreamAccum += e.data;
      var el = q('mrStreamOutput');
      if (el) {
        el.textContent = _mrStreamAccum;
        el.scrollTop = el.scrollHeight;
      }
      var wrap = q('mrStreamWrap');
      if (wrap && wrap.style.display === 'none') wrap.style.display = '';
    });

    _mrEventSource.addEventListener('done', function () {
      mrStopStream();
      mrStopPolling();
      window._mr.runningId = null;
      window._mr.thinkingText = _mrStreamAccum || null;
      if (_mrStreamAccum) {
        var _pw = q('mrProgressWrap');
        if (_pw) _pw.style.display = 'none';
        var _cancelBtn = q('mrBtnCancel');
        if (_cancelBtn) _cancelBtn.style.display = 'none';
        var _rc = q('mrResultCard');
        if (_rc) {
          _rc.style.display = 'block';
          _rc.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
        document.querySelectorAll('.mr-tab').forEach(function (t) { t.classList.remove('active'); });
        document.querySelectorAll('.mr-tab-pane').forEach(function (p) { p.classList.remove('active'); });
        var _rt = document.querySelector('.mr-tab[data-tab="Result"]');
        if (_rt) _rt.classList.add('active');
        var _rp = q('mrPaneResult');
        if (_rp) _rp.classList.add('active');
        mrRenderMarkdown(_mrStreamAccum, '');
      }

      setTimeout(function () { mrLoadResult(_mrAnalysisId); }, 800);
    });

    _mrEventSource.addEventListener('analysis-error', function (e) {
      mrStopStream();
      mrStopPolling();
      window._mr.runningId = null;
      mrShowError(e.data || (window.i18n ? window.i18n.t('mr_analysis.unknown_error') : 'Fehler'));
      mrLoadHistory();
    });

    _mrEventSource.onerror = function () {
      mrStopStream();
      mrStopPolling();
      window._mr.runningId = null;
      fetch('/api/mr-analysis/' + id)
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (data) {
          if (!data || data.status === 'CANCELLED') {
            mrLoadHistory();
          } else if (data.status === 'COMPLETED') {
            mrLoadResult(id);
          } else {
            mrShowError(data.errorMessage || (window.i18n
              ? window.i18n.t('mr_analysis.unknown_error') : 'Fehler'));
            mrLoadHistory();
          }
        })
        .catch(function () { mrLoadHistory(); });
    };
  }

  function mrStopStream() {
    if (_mrEventSource) { _mrEventSource.close(); _mrEventSource = null; }
  }

  function mrStartIntegrationTestStream(id) {
    mrStopIntegrationTestStream();
    var card = q('mrIntegrationTestCard');
    if (card) {
      card.style.display = 'block';
      card.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    mrItResetStatusPanel();
    _mrItEventSource = new EventSource('/api/mr-analysis/' + id + '/integration-test/stream');

    _mrItEventSource.addEventListener('chunk', function (e) {
      var console_ = q('mrItConsole');
      if (console_) {
        console_.textContent += e.data;
        console_.scrollTop = console_.scrollHeight;
      }
      mrItStatusFromStream(e.data);
      if (_mrItScroll) _mrItScroll.update();
    });

    _mrItEventSource.addEventListener('test-done', function (e) {
      mrStopIntegrationTestStream();
      var status = e.data || 'COMPLETED';
      mrItStopElapsed();
      mrUpdateIntegrationTestStatus(status);

      if (_mrAnalysisId) {
        fetch('/api/mr-analysis/' + _mrAnalysisId)
          .then(function (r) { return r.json(); })
          .then(function (data) { mrRenderIntegrationTestResult(data); })
          .catch(function () {});
      }
    });

    _mrItEventSource.onerror = function () {
      mrStopIntegrationTestStream();
      mrItStopElapsed();

      if (_mrAnalysisId) {
        fetch('/api/mr-analysis/' + _mrAnalysisId)
          .then(function (r) { return r.json(); })
          .then(function (data) {
            var itStatus = data.integrationTestStatus || 'FAILED';
            mrUpdateIntegrationTestStatus(itStatus);
            mrRenderIntegrationTestResult(data);
          })
          .catch(function () { mrUpdateIntegrationTestStatus('FAILED'); });
      } else {
        mrUpdateIntegrationTestStatus('FAILED');
      }
    };
  }

  function mrStopIntegrationTestStream() {
    if (_mrItEventSource) { _mrItEventSource.close(); _mrItEventSource = null; }
  }

  function mrUpdateIntegrationTestStatus(status) {
    var badge = q('mrItStatusBadge');
    var text  = q('mrItStatusText');
    if (badge) {
      badge.className = 'mr-status-badge ' + status.toLowerCase();
      badge.textContent = { COMPLETED: '\u2705 Abgeschlossen', FAILED: '\u274c Fehlgeschlagen', RUNNING: 'L\u00e4uft\u2026' }[status] || status;
    }
    if (text) text.textContent = '';
  }

  function mrRenderIntegrationTestResult(data) {
    var resultMd = data.integrationTestResult || '';
    if (!resultMd) return;
    var wrap = q('mrItResultWrap');
    var body = q('mrItResultBody');
    if (!wrap || !body) return;
    var markedLib = window.marked;
    if (markedLib && typeof markedLib.parse === 'function') {
      try { body.innerHTML = markedLib.parse(resultMd); } catch (_) { body.textContent = resultMd; }
    } else {
      body.innerHTML = '<pre style="white-space:pre-wrap">' + resultMd.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') + '</pre>';
    }
    wrap.style.display = 'block';
  }

  async function mrPoll() {
    if (!_mrAnalysisId) { mrStopPolling(); return; }
    try {
      var resp = await fetch('/api/mr-analysis/' + _mrAnalysisId + '/status');
      if (!resp.ok) { mrStopPolling(); return; }
      var data = await resp.json();
      mrUpdateProgress(data);
      mrLoadHistory();
      if (data.status === 'COMPLETED') {
        mrStopPolling();
        window._mr.runningId = null;
        mrLoadResult(_mrAnalysisId);
      } else if (data.status === 'FAILED') {
        mrStopPolling();
        window._mr.runningId = null;
        mrShowError(data.errorMessage || (window.i18n ? window.i18n.t('mr_analysis.unknown_error') : 'Unbekannter Fehler'));
        mrLoadHistory();
      }
    } catch (e) {

    }
  }

  function mrUpdateProgress(data) {
    var step = q('mrCurrentStep');
    if (step) step.textContent = data.currentStep || '';
    var log = q('mrLogOutput');
    if (log && data.logOutput) {
      log.textContent = data.logOutput;
      log.scrollTop = log.scrollHeight;
    }
  }

  async function mrLoadResult(id) {
    try {
      var resp = await fetch('/api/mr-analysis/' + id);
      if (!resp.ok) return;
      var data = await resp.json();
      mrRenderResult(data);
    } catch (e) {
      mrShowError(e.message);
    }
  }

  function mrRenderResult(data) {
    var progressWrap = q('mrProgressWrap');
    if (progressWrap) progressWrap.style.display = 'none';
    var resultCard = q('mrResultCard');
    if (resultCard) {
      resultCard.style.display = 'block';
      resultCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    var cancelBtn = q('mrBtnCancel');
    if (cancelBtn) cancelBtn.style.display = 'none';
    var startBtn = q('mrBtnStart');
    if (startBtn) { mrUpdateBtn(); }

    document.querySelectorAll('.mr-tab').forEach(function (t) { t.classList.remove('active'); });
    document.querySelectorAll('.mr-tab-pane').forEach(function (p) { p.classList.remove('active'); });
    var resultTab = document.querySelector('.mr-tab[data-tab="Result"]');
    if (resultTab) resultTab.classList.add('active');
    var resultPane = q('mrPaneResult');
    if (resultPane) resultPane.classList.add('active');

    if (window._mr.thinkingText) mrRenderThinking(window._mr.thinkingText);

    var markdown = data.resultMarkdown || '';
    window._mrDiffHeadSha = data.diffHeadSha || '';
    mrRenderMarkdown(markdown, data.mrUrl, data.diffHeadSha || '');
    mrRenderCodeSuggestions(data);
    mrLoadHistory();

    if (data.integrationTestEnabled &&
        (data.integrationTestStatus === 'RUNNING' || data.integrationTestStatus === 'PENDING')) {
      mrStartIntegrationTestStream(data.id);
    } else if (data.integrationTestEnabled &&
               (data.integrationTestStatus === 'COMPLETED' || data.integrationTestStatus === 'FAILED')) {

      var card = q('mrIntegrationTestCard');
      if (card) card.style.display = 'block';
      mrUpdateIntegrationTestStatus(data.integrationTestStatus);
      mrRenderIntegrationTestResult(data);
    }
  }

  function mrRenderMarkdown(markdown, mrUrl, diffHeadSha) {
    var container = q('mrMarkdownBody');
    if (!container) return;
    var isGithub = mrUrl && mrUrl.indexOf('github.com') > -1;
    var diffsUrl = '';
    if (mrUrl) {
      var _base = mrUrl.replace(/\/$/, '');
      var _suffix = isGithub ? '/files' : '/diffs';
      _base = _base.replace(new RegExp('\\' + _suffix + '$'), '');
      diffsUrl = _base + _suffix;
    }
    var markedLib = window.marked;

    markdown = markdown.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

    function _esc(s) {
      return String(s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
    function _inline(text) {
      if (markedLib && typeof markedLib.parseInline === 'function') {
        try { return markedLib.parseInline(text); } catch (_) {   }
      }
      return _esc(text)
        .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
        .replace(/`([^`\n]+)`/g, '<code>$1</code>');
    }
    function _itemDiv(text) {
      var b, bg;
      if (text.indexOf('\u2705') !== -1)      { b = '#86efac'; bg = 'rgba(22,163,74,.05)'; }
      else if (text.indexOf('\u274c') !== -1) { b = '#fca5a5'; bg = 'rgba(185,28,28,.04)'; }
      else if (text.indexOf('\u26a0') !== -1) { b = '#fde68a'; bg = 'rgba(202,138,4,.05)'; }
      else                                    { b = 'var(--mr-line,#ddd7cb)'; bg = 'var(--mr-paper,#fff)'; }
      return '<div class="mr-ri" style="border:1.5px solid ' + b + ';border-radius:8px;' +
        'padding:9px 14px;margin:6px 0;background:' + bg + ';display:block;' +
        'box-shadow:0 1px 4px rgba(0,0,0,.07);overflow-wrap:break-word;word-break:break-word;">' +
        _inline(text) + '</div>';
    }

    var _lines = markdown.split('\n');
    var _html = [];
    var _i = 0;
    var _pBuf = [];
    function _flushP() {
      if (_pBuf.length === 0) return;
      var t = _pBuf.join(' ').trim();
      if (t) _html.push('<p>' + _inline(t) + '</p>');
      _pBuf = [];
    }
    while (_i < _lines.length) {
      var _l = _lines[_i];

      var _fm = _l.match(/^[ \t]*```\s*([\w-]*)\s*$/);
      if (_fm) {
        _flushP();
        var _code = [];
        var _lang = _fm[1] || '';
        _i++;
        while (_i < _lines.length && !/^[ \t]*```\s*$/.test(_lines[_i])) {
          _code.push(_lines[_i]); _i++;
        }
        _i++;
        var _codeText = _code.join('\n');

        var _first = (_code[0] || '').trim();
        var _isViolation = /^[\u274c\u26a0]/.test(_first);
        if (_isViolation) {
          var _vb = _first.indexOf('\u274c') !== -1 ? '#fca5a5' : '#fde68a';
          var _vbg = _first.indexOf('\u274c') !== -1 ? 'rgba(185,28,28,.04)' : 'rgba(202,138,4,.05)';
          _html.push('<pre style="border:1.5px solid ' + _vb + ';background:' + _vbg +
            ';border-radius:10px;padding:14px 18px;margin:8px 0;white-space:pre-wrap;' +
            'word-break:break-word;font-size:13px;line-height:1.6;">' + _esc(_codeText) + '</pre>');
        } else {
          _html.push('<pre' + (_lang ? ' data-lang="' + _esc(_lang) + '"' : '') + '>' +
            '<code>' + _esc(_codeText) + '</code></pre>');
        }
        continue;
      }

      if (/^[ \t]*---+[ \t]*$/.test(_l)) { _flushP(); _html.push('<hr>'); _i++; continue; }

      var _hm = _l.match(/^(#{1,6})\s+(.*)$/);
      if (_hm) {
        _flushP();
        var _lvl = _hm[1].length;
        _html.push('<h' + _lvl + '>' + _inline(_hm[2].trim()) + '</h' + _lvl + '>');
        _i++; continue;
      }

      var _stripped = _l.replace(/^[ \t]*[-*+][ \t]+/, '').trim();
      if (/^[\u2705\u274c\u26a0]/.test(_stripped)) {
        _flushP();
        _html.push(_itemDiv(_stripped));
        _i++; continue;
      }

      var _li = _l.match(/^[ \t]*[-*+][ \t]+(.*)$/);
      if (_li) {
        _flushP();
        var _items = [];
        while (_i < _lines.length) {
          var _li2 = _lines[_i].match(/^[ \t]*[-*+][ \t]+(.*)$/);
          if (!_li2) break;
          var _ls = _li2[1].trim();

          if (/^[\u2705\u274c\u26a0]/.test(_ls)) break;
          _items.push('<li>' + _inline(_ls) + '</li>');
          _i++;
        }
        if (_items.length) _html.push('<ul>' + _items.join('') + '</ul>');
        continue;
      }

      if (_l.trim() === '') { _flushP(); _i++; continue; }

      _pBuf.push(_l);
      _i++;
    }
    _flushP();
    var html = _html.join('\n');

    html = html.replace(/\bOriginal:/g,
      '<span class="mr-label-original">Original:</span>');
    html = html.replace(/\bSuggestion:/g,
      '<span class="mr-label-suggestion">Suggestion:</span>');

    if (diffsUrl) {
      container.innerHTML = html;
      if (window.MrLinkBuilder) {
        window.MrLinkBuilder.linkifyResultContainer(container, diffsUrl);
      }
    } else {
      container.innerHTML = html;
    }

    container.querySelectorAll('pre').forEach(function (pre) {
      var text = (pre.textContent || '').trim();
      if (text.indexOf('❌') === 0) {
        pre.style.cssText = 'border:1.5px solid #fca5a5;background:rgba(185,28,28,.04);border-radius:10px;padding:14px 18px;margin-bottom:10px;white-space:pre-wrap;word-break:break-word;font-size:13px;';
      } else if (text.indexOf('✅') === 0) {
        pre.style.cssText = 'border:1.5px solid #86efac;background:rgba(22,163,74,.04);border-radius:10px;padding:14px 18px;white-space:pre-wrap;font-size:13px;';
      }
    });
    mrPostProcessLinks(container, mrUrl);
    if (_mrResultScroll) _mrResultScroll.update();
  }

  function mrRenderThinking(text) {
    var out = q('mrThinkingOutput');
    if (out) out.textContent = text;
    var tabs = q('mrThinkingTabs');
    if (tabs) tabs.style.display = 'flex';
    if (_mrThinkingScroll) _mrThinkingScroll.update();
  }

  window.mrDismissThinking = function () {
    var tabs = q('mrThinkingTabs');
    if (tabs) tabs.style.display = 'none';

    document.querySelectorAll('.mr-tab').forEach(function (t) { t.classList.remove('active'); });
    document.querySelectorAll('.mr-tab-pane').forEach(function (p) { p.classList.remove('active'); });
    var resultTab = document.querySelector('.mr-tab[data-tab="Result"]');
    if (resultTab) resultTab.classList.add('active');
    var resultPane = q('mrPaneResult');
    if (resultPane) resultPane.classList.add('active');
  };

  function mrPostProcessLinks(container, mrUrl) {
    if (!container || !mrUrl) return;
    var isGithub = mrUrl.indexOf('github.com') > -1;
    var _base2 = mrUrl.replace(/\/$/, '');
    var _suffix2 = isGithub ? '/files' : '/diffs';
    _base2 = _base2.replace(new RegExp('\\' + _suffix2 + '$'), '');
    var diffsUrl = _base2 + _suffix2;
    container.querySelectorAll('code').forEach(function (code) {
      var text = code.textContent.trim();

      if (/^[a-zA-Z0-9_.][a-zA-Z0-9_.\/-]*\/[a-zA-Z0-9_.-]+\.[a-zA-Z]{1,5}$/.test(text) &&
          !/[(){};$@"'<>\s]/.test(text)) {
        var link = document.createElement('a');
        link.className = 'mr-file-link';
        link.href = diffsUrl;
        link.textContent = text;
        link.title = (window.i18n ? window.i18n.t('mr_analysis.cs_open_diff_title') : 'Open in MR diff: ') + text;
        link.addEventListener('click', function (e) {
          e.preventDefault();
          mrOpenUrl(diffsUrl);
        });
        code.replaceWith(link);
      }
    });
  }

  function mrBuildBlobUrl(mrUrl, filePath, lineNumber, isGithub) {
    if (!mrUrl || !filePath) return '';

    var cleanMrUrl = mrUrl.replace(/\/(diffs|files)\/?$/, '');
    var m = cleanMrUrl.match(/^(https?:\/\/[^\/]+)\/(.+?)\/-\/(merge_requests|pull)\/\d+/);
    if (!m) {
      return cleanMrUrl;
    }
    var origin = m[1];
    var projectPath = m[2];

    var cleanPath = String(filePath).replace(/`/g, '').replace(/^\.\//, '');
    var encodedPath = cleanPath.split('/').map(function (seg) {
      return encodeURIComponent(seg);
    }).join('/');
    if (isGithub) {
      var gh = cleanMrUrl.match(/^(https?:\/\/[^\/]+)\/(.+?)\/-\/(pull)\/(\d+)/);
      if (!gh) return cleanMrUrl;
      return gh[1] + '/' + gh[2] + '/pull/' + gh[4] + '/files#L' + (lineNumber || '');
    }
    if (!window._mrDiffHeadSha) return cleanMrUrl;
    return origin + '/' + projectPath + '/-/blob/' + window._mrDiffHeadSha + '/' + encodedPath + (lineNumber ? '#L' + lineNumber : '');
  }

  window.mrBuildBlobUrl = mrBuildBlobUrl;

  function mrExtractViolations(markdown) {
    if (window.MrViolations && typeof window.MrViolations.parse === 'function') {
      return window.MrViolations.parse(markdown);
    }
    return [];
  }

  function mrRenderCodeSuggestions(data) {
    var panel = q('mrCodeSuggestionsPanel');
    if (!panel) return;
    var checkbox = q('mrChkCodeSuggestions');
    panel.style.display = 'none';
    panel.innerHTML = '';
    if (!_mrCodeSuggestWanted && !(checkbox && checkbox.checked)) return;
    var violations = mrExtractViolations(data.resultMarkdown || '');
    if (violations.length === 0) return;
    var mrUrl = data.mrUrl || '';
    var isGithub = mrUrl.indexOf('github.com') > -1;
    var diffsUrl = '';
    if (mrUrl) {
      var _base3 = mrUrl.replace(/\/$/, '');
      var _suffix3 = isGithub ? '/files' : '/diffs';
      _base3 = _base3.replace(new RegExp('\\' + _suffix3 + '$'), '');
      diffsUrl = _base3 + _suffix3;
    }
    window._mr.violations = violations;
    _mrAnalysisId = data.id;
    window._mr.analysisId = _mrAnalysisId;
    if (typeof mrInitTokenUsageBar === 'function') mrInitTokenUsageBar();
    var title = window.i18n ? window.i18n.t('mr_analysis.code_suggestions_title') : 'Code improvements';
    var html = '<div class="mr-sug-panel-title">' +
      '<i class="bi bi-code-square me-2"></i>' + escHtml(title) +
      ' <span class="mr-sug-count">(' + violations.length + ')</span>' +
      '</div>';
    violations.forEach(function (v, idx) {
      var blobFileUrl = (typeof mrBuildBlobUrl === 'function')
        ? mrBuildBlobUrl(diffsUrl, v.file || '', 0, isGithub)
        : '';
      var blobLineUrl = (typeof mrBuildBlobUrl === 'function' && v.lineNumber > 0)
        ? mrBuildBlobUrl(diffsUrl, v.file || '', v.lineNumber, isGithub)
        : '';
      var lineAnchor = '';
      var fileBlockAnchor = '';
      if (diffsUrl && v.file) {
        if (isGithub) {
          fileBlockAnchor = '';
        } else {
          var fileKey = (v.file || '').replace(/`/g, '').replace(/^\.\//, '').replace(/[/.]/g, '-');
          fileBlockAnchor = '#file-' + fileKey;
        }
      }
      if (diffsUrl && v.lineNumber > 0) {
        if (isGithub) {
          if (v.lineRange && v.lineRange.end && v.lineRange.end !== v.lineRange.start) {
            lineAnchor = '#L' + v.lineNumber + '-L' + v.lineRange.end;
          } else {
            lineAnchor = '#L' + v.lineNumber;
          }
        } else {
          if (v.lineRange && v.lineRange.end && v.lineRange.end !== v.lineRange.start) {
            lineAnchor = '#L' + fileKey + '_' + v.lineNumber + '-' + v.lineRange.end;
          } else {
            lineAnchor = '#L' + fileKey + '_' + v.lineNumber;
          }
        }
      }

      var fileLinkUrl = blobFileUrl || (diffsUrl ? diffsUrl + fileBlockAnchor : '');
      var fileRef = fileLinkUrl
        ? '<a class="mr-suggestion-file-link" href="' + escHtml(fileLinkUrl) + '" data-mr-open-url="' + escHtml(fileLinkUrl) + '">' +
          '<i class="bi bi-file-code me-1"></i>' + escHtml(v.file) + '</a>'
        : '<code class="mr-sug-file-plain">' + escHtml(v.file) + '</code>';
      var lineBadge = '';
      if (v.lineNumber > 0) {
        var lineLabel = (window.i18n ? window.i18n.t('mr_analysis.cs_line') : 'Line') + '\u00a0';
        var lineText = String(v.lineNumber);
        if (v.lineRange && v.lineRange.end && v.lineRange.end !== v.lineRange.start) {
          lineText += '\u2013' + v.lineRange.end;
        }
        if (fileLinkUrl) {
          var lineTitle = v.lineUncertain
            ? 'Open line in MR (approximate)'
            : 'Open line ' + lineText + ' in MR diff';
          var lineLinkUrl = blobLineUrl || (diffsUrl ? diffsUrl + lineAnchor : '');
          lineBadge = '<a class="mr-sug-line-badge mr-sug-line-link" href="' + escHtml(lineLinkUrl) + '" data-mr-open-url="' + escHtml(lineLinkUrl) + '" title="' + escHtml(lineTitle) + '">' + lineLabel + lineText + '</a>';
          if (v.lineUncertain) lineBadge += ' <span class="mr-sug-line-uncertain-mark" title="Line number is approximate">\u26a0\ufe0f</span>';
        } else if (v.lineUncertain) {
          lineBadge = '<span class="mr-sug-line-badge mr-sug-line-uncertain" title="Line number is approximate; the suggestion cannot be posted as a precise note.">' + lineLabel + lineText + ' \u26a0\ufe0f</span>';
        } else {
          lineBadge = '<span class="mr-sug-line-badge">' + lineLabel + lineText + '</span>';
        }
      }
      html += '<div class="mr-suggestion-card">';
      html += '<div class="mr-sug-rule">\u274c ' + escHtml(v.rule) + '</div>';
      html += '<div class="mr-sug-meta">\uD83D\uDCC4 ' + fileRef + lineBadge + '</div>';
      if (v.original) {
        html += '<div class="mr-sug-block">' +
          '<div class="mr-sug-label mr-sug-label-red">Original</div>' +
          '<pre class="mr-sug-pre mr-sug-pre-red">' + escHtml(v.original) + '</pre>' +
          '</div>';
      }
      if (v.suggestedCode !== undefined) {
        html += '<div class="mr-sug-block">' +
          '<div class="mr-sug-label mr-sug-label-green">\uD83D\uDCA1 ' + (window.i18n ? window.i18n.t('mr_analysis.cs_suggestion_label').replace(/^\S+\s/, '') : 'Suggestion') + '</div>' +
          '<pre class="mr-sug-pre mr-sug-pre-green">' +
          (v.suggestedCode ? escHtml(v.suggestedCode) : '<em class="mr-sug-delete">' + (window.i18n ? window.i18n.t('mr_analysis.cs_delete_line') : '(delete line)') + '</em>') +
          '</pre></div>';
      }
      html += '<div class="mr-sug-actions">';
      html += '<span id="mrSugStatus_' + idx + '" class="mr-sug-status"></span>';
      var canPost = v.lineNumber > 0 && !v.lineUncertain;
      if (canPost) {
        html += '<button class="mr-btn-outline mr-sug-btn" onclick="mrPostSuggestion(' + idx + ')">' +
          '<i class="bi bi-send me-1"></i>' + (window.i18n ? window.i18n.t('mr_analysis.code_suggestion_post') : 'Post as suggestion') + '</button>';
      } else if (diffsUrl) {
        var openTitle = v.lineUncertain ? 'Open in MR (line number is uncertain)' : 'Open in MR';
        var openUrl = blobLineUrl || (diffsUrl + lineAnchor);
        html += '<button class="mr-btn-outline mr-sug-btn" title="' + openTitle + '" data-mr-open-url="' + escHtml(openUrl) + '">' +
          '<i class="bi bi-box-arrow-up-right me-1"></i>' + (window.i18n ? window.i18n.t('mr_analysis.code_suggestion_open') : 'Open in MR') + '</button>';
      }
      html += '</div></div>';
    });
    panel.innerHTML = html;
    panel.style.display = 'block';
  }

  window.mrPostSuggestion = function (idx) {
    var v = (window._mr.violations || [])[idx];
    var analysisId = window._mr.analysisId;
    if (!v || !analysisId) return;
    if (v.lineUncertain || !v.lineNumber) {
      var statusElGuard = document.getElementById('mrSugStatus_' + idx);
      if (statusElGuard) statusElGuard.textContent = '\u26a0 Line number is uncertain \u2014 cannot post precise note.';
      return;
    }
    var btn = document.querySelector('[onclick="mrPostSuggestion(' + idx + ')"]');
    var statusEl = document.getElementById('mrSugStatus_' + idx);
    if (btn) { btn.disabled = true; btn.style.opacity = '0.5'; }
    fetch('/api/mr-analysis/' + analysisId + '/suggestions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filePath: v.file, lineNumber: v.lineNumber, suggestedCode: v.suggestedCode || '' }),
    })
      .then(function (res) { return res.json().then(function (d) { return { ok: res.ok, data: d }; }); })
      .then(function (r) {
        if (r.ok && r.data.url) {
          if (statusEl) statusEl.innerHTML = '<a href="#" data-mr-open-url="' + escHtml(r.data.url) + '" style="color:var(--mr-green);">' +
            '<i class="bi bi-check-circle me-1"></i>' + (window.i18n ? window.i18n.t('mr_analysis.code_suggestion_posted') : 'Gepostet') + '</a>';
          if (btn) btn.style.display = 'none';
        } else {
          if (statusEl) statusEl.textContent = '⚠ ' + (r.data.error || (window.i18n ? window.i18n.t('common.error') : 'Fehler'));
          if (btn) { btn.disabled = false; btn.style.opacity = ''; }
        }
      })
      .catch(function (e) {
        if (statusEl) statusEl.textContent = '⚠ ' + e.message;
        if (btn) { btn.disabled = false; btn.style.opacity = ''; }
      });
  };

  window.mrOpenUrl = function (url) {

    var ua = (navigator && navigator.userAgent) || '';
    var isDesktop = !!(window.desktopApp || window.__desktopAppReady) || /JavaFX/i.test(ua);
    if (isDesktop) {

      fetch('/api/open-url', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url: url }) })
        .catch(function () {   });
    } else {

      window.open(url, '_blank');
    }
  };

  function mrWireOpenUrlDelegation() {
    if (window._mrOpenUrlDelegated) return;
    window._mrOpenUrlDelegated = true;
    document.addEventListener('click', function (event) {
      var trigger = event.target && event.target.closest ? event.target.closest('[data-mr-open-url]') : null;
      if (!trigger) return;
      event.preventDefault();
      if (typeof window.mrOpenUrl === 'function') window.mrOpenUrl(trigger.getAttribute('data-mr-open-url'));
    });
  }

  mrWireOpenUrlDelegation();

  function mrShowProgress() {
    var progressWrap = q('mrProgressWrap');
    if (progressWrap) progressWrap.style.display = 'block';
    var resultCard = q('mrResultCard');
    if (resultCard) resultCard.style.display = 'none';
    var errorCard = q('mrErrorCard');
    if (errorCard) errorCard.style.display = 'none';
    var logOut = q('mrLogOutput');
    if (logOut) logOut.textContent = '';
    var streamOut = q('mrStreamOutput');
    if (streamOut) streamOut.textContent = '';
    var streamWrap = q('mrStreamWrap');
    if (streamWrap) streamWrap.style.display = 'none';
    var tabs = q('mrThinkingTabs');
    if (tabs) tabs.style.display = 'none';
    var sugPanel = q('mrCodeSuggestionsPanel');
    if (sugPanel) { sugPanel.style.display = 'none'; sugPanel.innerHTML = ''; }
    var itCard = q('mrIntegrationTestCard');
    if (itCard) {
      itCard.style.display = 'none';
      var itConsole = q('mrItConsole');
      if (itConsole) itConsole.textContent = '';
      var itResultWrap = q('mrItResultWrap');
      if (itResultWrap) itResultWrap.style.display = 'none';
      var itBadge = q('mrItStatusBadge');
      if (itBadge) { itBadge.className = 'mr-status-badge running'; itBadge.textContent = 'L\u00e4uft\u2026'; }
      var itPanel = q('mrItStatusPanel');
      if (itPanel) itPanel.hidden = true;
      var itElapsed = q('mrItElapsed');
      if (itElapsed) itElapsed.textContent = '00:00';
    }
    mrStopIntegrationTestStream();
    mrItStopElapsed();
    window._mr.thinkingText = null;
    window._mr.violations = [];
    _mrStreamAccum = '';
    var step = q('mrCurrentStep');
    if (step) step.textContent = window.i18n ? window.i18n.t('mr_analysis.step_starting') : 'Wird gestartet…';
    var cancelBtn = q('mrBtnCancel');
    if (cancelBtn) cancelBtn.style.display = '';
  }

  function mrShowError(msg) {
    var progressWrap = q('mrProgressWrap');
    if (progressWrap) progressWrap.style.display = 'none';
    var errorCard = q('mrErrorCard');
    if (errorCard) {
      errorCard.style.display = 'block';
      var errMsg = errorCard.querySelector('.mr-error-msg');
      if (errMsg) errMsg.textContent = msg;
    }
    var btn = q('mrBtnStart');
    if (btn) { btn.disabled = false; }
    var cancelBtn = q('mrBtnCancel');
    if (cancelBtn) cancelBtn.style.display = 'none';
  }

  window.mrCancelAnalysis = async function () {
    var cancelBtn = q('mrBtnCancel');
    if (cancelBtn) cancelBtn.disabled = true;
    if (_mrAnalysisId) {
      try {
        await fetch('/api/mr-analysis/' + _mrAnalysisId + '/cancel', { method: 'POST' });
      } catch (e) {   }
    }
    mrStopPolling();
    mrStopStream();
    window._mr.runningId = null;
    _mrAnalysisId = null;
    window._mr.analysisId = _mrAnalysisId;
    if (cancelBtn) { cancelBtn.style.display = 'none'; cancelBtn.disabled = false; }
    var progressWrap = q('mrProgressWrap');
    if (progressWrap) progressWrap.style.display = 'none';
    var btn = q('mrBtnStart');
    if (btn) { mrUpdateBtn(); }
    mrLoadHistory();
  };

  window.mrResetView = function mrResetView() {
    mrStopPolling();
    mrStopStream();
    var progressWrap = q('mrProgressWrap');
    if (progressWrap) progressWrap.style.display = 'none';
    var cancelBtn = q('mrBtnCancel');
    if (cancelBtn) cancelBtn.style.display = 'none';
    var btn = q('mrBtnStart');
    if (btn) { mrUpdateBtn(); }
  }

  var _mrHistory = null;

  function mrHistoryUrl(a) {
    return String(a.mrUrl || '').replace(/^https?:\/\//, '');
  }

  function mrHistoryPlatform(a) {
    var url = String(a.mrUrl || '').toLowerCase();
    if (url.indexOf('/merge_requests/') !== -1 || url.indexOf('gitlab') !== -1) return 'GitLab';
    if (url.indexOf('/pull/') !== -1 || url.indexOf('github') !== -1) return 'GitHub';
    return '';
  }

  function mrHistory() {
    if (_mrHistory) return _mrHistory;
    _mrHistory = window.slHistory.mount({
      root: 'hxCard-code-qa',
      page: 'code-qa',
      titleKey: 'mr_analysis.history_title',
      emptyKey: 'mr_analysis.history_empty',
      columns: {
        title: { key: 'mr_analysis.history_col_url', fallback: 'MR-URL' }
      },
      map: {
        id: function(a) { return a.id; },
        status: function(a) { return a.status; },
        createdAt: function(a) { return a.createdAt; },
        typeIcon: function(a) { return mrHistoryPlatform(a) === 'GitHub' ? 'bi-github' : 'bi-git'; },
        title: mrHistoryUrl,
        subtitle: mrHistoryPlatform,
        tags: function(a) { return a.jiraTicketKey ? [{ label: a.jiraTicketKey }] : []; },
        note: function(a) { return a.errorMessage || ''; },
        isCurrent: function(a) { return window._mr && window._mr.analysisId === a.id; }
      },
      actions: {
        open: function(a) { mrViewHistoryResult(a.id); },
        cancel: function(a) { mrCancelHistoryItem(a.id); },
        remove: function(a) { mrDeleteHistory(a.id); },
        deleteAll: function() { mrDeleteAllHistory(); }
      }
    });
    return _mrHistory;
  }

  window.mrLoadHistory = async function () {
    try {
      var resp = await fetch('/api/mr-analysis');
      if (!resp.ok) return;
      var items = await resp.json();
      mrHistory().setData(Array.isArray(items) ? items : []);
    } catch (e) { }
  };

  window.mrViewHistoryResult = async function (id) {
    try {
      var resp = await fetch('/api/mr-analysis/' + id);
      if (!resp.ok) return;
      var data = await resp.json();
      _mrAnalysisId = data.id;
      window._mr.analysisId = _mrAnalysisId;
      if (typeof mrInitTokenUsageBar === 'function') mrInitTokenUsageBar();
      mrRenderResult(data);
      if (_mrHistory) _mrHistory.refresh();
      var resultCard = q('mrResultCard');
      if (resultCard) resultCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (e) {
      showAlert(e.message, 'danger');
    }
  };

  window.mrDeleteHistory = async function (id) {
    try {
      await fetch('/api/mr-analysis/' + id, { method: 'DELETE' });
      mrLoadHistory();
    } catch (e) { }
  };

  window.mrCancelHistoryItem = async function (id) {
    try {
      await fetch('/api/mr-analysis/' + id + '/cancel', { method: 'POST' });
    } catch (e) { }
    mrLoadHistory();
  };

  window.mrDeleteAllHistory = async function () {
    try {
      await fetch('/api/mr-analysis/history', { method: 'DELETE' });
      mrLoadHistory();
    } catch (e) { }
  };

  function mrCheckDemoAutoFill() {
    var gitSel  = q('mrSelectGitConfig');
    var jiraSel = q('mrSelectJiraConfig');
    var gitOpt  = gitSel  && gitSel.options[gitSel.selectedIndex];
    var jiraOpt = jiraSel && jiraSel.options[jiraSel.selectedIndex];
    var gitIsDemo  = gitOpt  && gitOpt.text.toLowerCase().indexOf('demo') !== -1;
    var jiraIsDemo = jiraOpt && jiraOpt.text.toLowerCase().indexOf('demo') !== -1;

    var urlInput = q('mrInputUrl');
    if (urlInput && gitIsDemo) {
      urlInput.value = 'http://localhost:3080/demo-group/ecommerce-api/-/merge_requests/1';
      mrUpdateBtn();
    }

    var jiraKeyInput = q('mrInputJiraKey');
    if (jiraKeyInput && jiraIsDemo) {
      jiraKeyInput.value = 'ECOM-42';
    }
  }

  async function mrTryResumeRunning() {
    try {
      var resumeId = window._mr.runningId;
      if (!resumeId) {

        var listResp = await fetch('/api/mr-analysis');
        if (!listResp.ok) return;
        var items = await listResp.json();
        var running = items.find(function (i) { return i.status === 'RUNNING' || i.status === 'PENDING'; });
        if (!running) return;
        resumeId = running.id;
      }
      var resp = await fetch('/api/mr-analysis/' + resumeId + '/status');
      if (!resp.ok) { window._mr.runningId = null; return; }
      var data = await resp.json();
      if (data.status === 'RUNNING' || data.status === 'PENDING') {
        _mrAnalysisId = resumeId;
        window._mr.analysisId = _mrAnalysisId;
        if (typeof mrInitTokenUsageBar === 'function') mrInitTokenUsageBar();
        window._mr.runningId = resumeId;
        mrShowProgress();
        mrUpdateProgress(data);
        mrStartStream(resumeId);
        mrStartPolling();
      } else if (data.status === 'COMPLETED') {
        window._mr.runningId = null;
        mrLoadResult(resumeId);
      } else {
        window._mr.runningId = null;
      }
    } catch (e) {   }
  }

  function mrInitPage() {
    mrLoadHistory();
    var gitSel  = q('mrSelectGitConfig');
    var jiraSel = q('mrSelectJiraConfig');
    if (gitSel)  gitSel.addEventListener('change',  mrCheckDemoAutoFill);
    if (jiraSel) jiraSel.addEventListener('change', mrCheckDemoAutoFill);
    mrCheckDemoAutoFill();
    if (window.SLScroll) {
      _mrResultScroll   = window.SLScroll(q('mrMarkdownBody'),   q('mrResultScrollTrack'),   q('mrResultScrollThumb'));
      _mrThinkingScroll = window.SLScroll(q('mrThinkingOutput'), q('mrThinkingScrollTrack'), q('mrThinkingScrollThumb'));
      _mrItScroll       = window.SLScroll(q('mrItConsole'),       q('mrItScrollTrack'),        q('mrItScrollThumb'));
    }
    mrTryResumeRunning();
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', mrInitPage);
  } else {
    mrInitPage();
  }

  document.addEventListener('app:navigate', function onMrNavigate(e) {
    if (e.detail && e.detail.url && e.detail.url.indexOf('code-qa') !== -1) return;
    document.removeEventListener('app:navigate', onMrNavigate);
    mrStopPolling();
    mrStopStream();
    mrStopIntegrationTestStream();
  });
})();

