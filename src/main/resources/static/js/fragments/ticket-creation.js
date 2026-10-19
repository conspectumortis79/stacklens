if (!window._tc) { window._tc = { gen: 0 }; }
window._tc.gen++;

window._tc.historyId = window._tc.historyId || null;

(function(){

  function t(key, fb){ return (window.slHelpers && window.slHelpers.t) ? window.slHelpers.t(key, fb) : (fb || key); }

  var $ = function(id){ return document.getElementById(id); };
  var _tcPageGen = window._tc.gen;
  var state = { abortCtrl: null, files: [], ticketData: null, historyId: null,
                runningId: null, pendingInput: null, pendingCancel: false, issueType: 'Story',
                requiredFields: [], requiredFieldsLoading: false };

  window.tcSetGenerateIssueType = function(type) {
    var supported = ['Story', 'Technical Task', 'Bug'];
    if (supported.indexOf(type) < 0) type = 'Story';
    state.issueType = type;
    var sel = $('tcGenerateIssueType');
    if (sel) {
      sel.value = type;
      if (sel.value !== type) sel.value = supported[0];
    }
    var seg = $('tcGenerateIssueTypeSeg');
    if (!seg) return;
    seg.querySelectorAll('.tc-issue-type-btn').forEach(function(btn) {
      btn.classList.toggle('active', btn.getAttribute('data-value') === type);
    });
  };

  window.tcSetIssueType = function(type) {
    var supported = ['Story', 'Technical Task', 'Bug'];
    if (supported.indexOf(type) < 0) type = 'Story';
    var sel = $('tcCreateIssueType');
    if (sel) {
      sel.value = type;
      if (sel.value !== type) sel.value = supported[0];
    }
    var seg = $('tcIssueTypeSeg');
    if (!seg) return;
    seg.querySelectorAll('.tc-issue-type-btn').forEach(function(btn) {
      btn.classList.toggle('active', btn.getAttribute('data-value') === type);
    });
  };

  var TC_RUNNING_KEY = 'tc_running_state';
  var TC_RUNNING_INPUT_LIMIT = 2000;
  var TC_ADOPT_INTERVAL_MS = 500;
  var TC_ADOPT_MAX_ATTEMPTS = 20;
  var _tcRestorePollTimer = null;
  var _tcRunningPollTimer = null;
  var _tcAdoptTimer = null;
  var _tcAdoptAttempts = 0;

  var _tcUnloading = false;
  window.addEventListener('pagehide', function(){ _tcUnloading = true; });
  window.addEventListener('beforeunload', function(){ _tcUnloading = true; });

  function tcSessionSaveRunning(inputText, historyId, issueType) {
    try {
      var existing = null;
      try { existing = JSON.parse(sessionStorage.getItem(TC_RUNNING_KEY) || 'null'); } catch (_) {}
      var startedAt = (existing && typeof existing.startedAt === 'number') ? existing.startedAt : Date.now();
      sessionStorage.setItem(TC_RUNNING_KEY, JSON.stringify({
        inputText: inputText || '',
        historyId: historyId || null,
        startedAt: startedAt,
        issueType: issueType || (existing && existing.issueType) || 'Story',
      }));
    } catch (_) {}
  }
  function tcSessionLoadRunning() {
    try { return JSON.parse(sessionStorage.getItem(TC_RUNNING_KEY) || 'null'); } catch (_) { return null; }
  }
  function tcSessionClearRunning() {
    if (_tcUnloading) return;
    try { sessionStorage.removeItem(TC_RUNNING_KEY); } catch (_) {}
  }

  function renderDescription(desc) { return window.TcRender.renderDescription(desc); }
  function renderTicket(data) {
    var issueType = escapeHtml(data.issueType || 'Story');
    var summary   = escapeHtml(data.summary || '');
    var labels    = Array.isArray(data.labels) ? data.labels : [];
    var openPts   = Array.isArray(data.openPoints) ? data.openPoints : [];
    var subtasks  = Array.isArray(data.subtasks) ? data.subtasks : [];

    var typeIcon = data.issueType === 'Bug' ? 'bi-bug-fill' :
                   data.issueType === 'Technical Task' ? 'bi-check2-square' : 'bi-bookmark-star-fill';

    var metaHtml = '<div class="tc-result-meta">' +
      '<span class="tc-result-type-badge"><i class="bi ' + typeIcon + ' me-1"></i>' + issueType + '</span>';
    labels.forEach(function(l){
      metaHtml += '<span class="tc-result-label">' + escapeHtml(l) + '</span>';
    });
    metaHtml += '</div>';

    var ticketHtml = '<div class="tc-jira-ticket">' +
      '<div class="tc-jira-header">' +
      '<i class="bi ' + typeIcon + ' tc-jira-issue-icon"></i>' +
      '<span class="tc-jira-issue-type">' + issueType + '</span>' +
      '</div>' +
      '<div class="tc-jira-body">' +
      '<div class="tc-jira-summary-line">' + summary + '</div>' +
      renderDescription(data.description) +
      '</div>' +
      '</div>';

    var opHtml = '';
    if (openPts.length > 0) {
      opHtml = '<div class="tc-open-points" style="margin-top:12px;">' +
        '<div class="tc-open-points-head"><i class="bi bi-exclamation-triangle-fill"></i>' +
        '<span>' + t('ticket_creation.open_points_title', 'Offene Punkte') + '</span></div>' +
        '<ul class="tc-open-points-list">';
      openPts.forEach(function(op){ opHtml += '<li>' + escapeHtml(op) + '</li>'; });
      opHtml += '</ul></div>';
    }

    var subHtml = '';
    if (subtasks.length > 0) {
      subHtml = '<div class="tc-subtasks" style="margin-top:12px;">' +
        '<div class="tc-subtasks-head">' +
        '<i class="bi bi-diagram-3-fill"></i>' +
        '<span>' + t('ticket_creation.subtasks_title', 'Subtasks') + ' (' + subtasks.length + ')</span>' +
        '</div>' +
        '<div class="tc-subtasks-list">';
      subtasks.forEach(function(st, idx){
        var stSummary = escapeHtml(st.summary || ('Subtask ' + (idx + 1)));
        var _stTypeResolved = (window.i18n && typeof window.i18n.t === 'function')
          ? window.i18n.t('ticket_creation.subtask_issue_type')
          : '';
        var stType = escapeHtml(_stTypeResolved || 'Subtask');
        var stDescription = st.description || '';
        var stPriority = escapeHtml(st.priority || '');
        var stLabels = Array.isArray(st.labels) ? st.labels : [];
        subHtml += '<div class="tc-subtask-card" data-subtask-index="' + idx + '">' +
          '<div class="tc-subtask-head">' +
          '<i class="bi bi-diagram-3"></i>' +
          '<span class="tc-subtask-type">' + stType + '</span>' +
          '<span class="tc-subtask-priority">' + stPriority + '</span>' +
          '<button type="button" class="tc-subtask-remove" data-subtask-index="' + idx + '" title="' + t('ticket_creation.subtask_remove', 'Remove') + '">' +
          '<i class="bi bi-x-lg"></i>' +
          '</button>' +
          '</div>' +
          '<textarea class="tc-subtask-summary" data-subtask-index="' + idx + '" rows="1" placeholder="' + t('ticket_creation.subtask_summary', 'Subtask title') + '">' + stSummary + '</textarea>' +
          '<div class="tc-textarea-wrap tc-subtask-desc-wrap" data-subtask-index="' + idx + '">' +
            '<textarea class="tc-subtask-description tc-textarea-box" data-subtask-index="' + idx + '" rows="3" placeholder="' + t('ticket_creation.subtask_description', 'Subtask description') + '">' + escapeHtml(stDescription) + '</textarea>' +
            '<div class="sl-scroll-track" data-always-visible="true"><div class="sl-scroll-thumb"></div></div>' +
          '</div>' +
          '<div class="tc-subtask-labels" data-subtask-index="' + idx + '">' +
          (stLabels.length ? stLabels.map(function(l){ return '<span class="tc-subtask-label">' + escapeHtml(l) + '</span>'; }).join('') : '') +
          '</div>' +
          '</div>';
      });
      subHtml += '</div></div>';
    }

    var content = $('tcResultContent');
    if (content) content.innerHTML = metaHtml + ticketHtml + opHtml + subHtml;

    if (subtasks.length > 0) {
      var removeBtns = document.querySelectorAll('.tc-subtask-remove');
      removeBtns.forEach(function(btn){
        btn.addEventListener('click', function(){
          var idx = parseInt(btn.getAttribute('data-subtask-index'), 10);
          removeSubtask(idx);
        });
      });

      if (typeof window.SLScroll === 'function') {
        document.querySelectorAll('.tc-subtask-desc-wrap').forEach(function(wrap){
          var ta    = wrap.querySelector('.tc-subtask-description');
          var track = wrap.querySelector('.sl-scroll-track');
          var thumb = wrap.querySelector('.sl-scroll-thumb');
          if (ta && track && thumb) {

            wrap._slScroll = window.SLScroll(ta, track, thumb);
          }
        });
      }
    }

    var createSection = $('tcCreateSection');
    if (createSection) createSection.style.display = '';

    syncResultJiraConfig();
    if (data.issueType) {
      window.tcSetIssueType(data.issueType);
      window.tcSetGenerateIssueType(data.issueType);
    }

    var formKey = ($('tcProjectKey') || {}).value || '';
    var createKey = $('tcCreateProjectKey');
    if (createKey && formKey.trim()) createKey.value = formKey.trim().toUpperCase();
    tcScheduleRequiredFields();
  }

  function collectSubtaskDrafts() {
    var drafts = [];
    var cards = document.querySelectorAll('.tc-subtask-card');
    cards.forEach(function(card){
      var idx = card.getAttribute('data-subtask-index');
      var sumEl = card.querySelector('.tc-subtask-summary');
      var descEl = card.querySelector('.tc-subtask-description');
      var labelsEl = card.querySelector('.tc-subtask-labels');
      var sum = sumEl ? sumEl.value.trim() : '';
      if (!sum) return;
      var desc = descEl ? descEl.value : '';
      var labels = [];
      if (labelsEl) {
        labelsEl.querySelectorAll('.tc-subtask-label').forEach(function(lbl){
          var txt = lbl.textContent.trim();
          if (txt) labels.push(txt);
        });
      }
      drafts.push({
        summary: sum,
        description: desc || '',
        issueType: 'Subtask',
        priority: state.ticketData.priority || 'Minor',
        labels: labels,
      });
    });
    return drafts;
  }

  function removeSubtask(idx) {
    if (!state.ticketData || !Array.isArray(state.ticketData.subtasks)) return;
    if (idx < 0 || idx >= state.ticketData.subtasks.length) return;
    state.ticketData.subtasks.splice(idx, 1);
    renderTicket(state.ticketData);
  }

  var tcJiraConfigs = [];

  function loadJiraConfigs() {
    fetch('/api/config/jira', { credentials: 'same-origin' })
      .then(function(r){ return r.json(); })
      .then(function(configs){
        if (_tcPageGen !== window._tc.gen) return;
        tcJiraConfigs = Array.isArray(configs) ? configs : [];
        fillConfigSelect($('tcJiraConfig'), tcJiraConfigs);
        fillConfigSelect($('tcCreateJiraConfig'), tcJiraConfigs);
      })
      .catch(function(){});
  }

  function fillConfigSelect(sel, configs) {
    if (!sel) return;
    var prev = sel.value;
    sel.innerHTML = '';
    if (!configs || configs.length === 0) {
      var opt = document.createElement('option');
      opt.value = '';
      opt.textContent = t('ticket_creation.no_jira_config', 'Keine Jira-Konfiguration');
      sel.appendChild(opt);
      return;
    }
    configs.forEach(function(c){
      var opt = document.createElement('option');
      opt.value = String(c.id);
      opt.textContent = c.name || c.baseUrl || String(c.id);
      sel.appendChild(opt);
    });
    if (prev) sel.value = prev;
  }

  function syncResultJiraConfig() {
    var formSel   = $('tcJiraConfig');
    var resultSel = $('tcCreateJiraConfig');
    if (!formSel || !resultSel) return;

    fillConfigSelect(resultSel, tcJiraConfigs);

    resultSel.value = formSel.value;
  }

  function removeFile(name) {
    state.files = state.files.filter(function(f){ return f.name !== name; });
    renderFileChips();
  }

  function renderFileChips() {
    var chips = $('tcFileChips');
    if (!chips) return;
    chips.innerHTML = '';
    state.files.forEach(function(f){
      var chip = document.createElement('span');
      chip.className = 'tc-file-chip';
      chip.innerHTML = '<i class="bi bi-paperclip" style="font-size:.8rem;"></i>' +
        escapeHtml(f.name) +
        '<button class="tc-file-chip-del" aria-label="Entfernen" data-fname="' + escapeHtml(f.name) + '">' +
        '<i class="bi bi-x"></i></button>';
      chips.appendChild(chip);
    });
    chips.querySelectorAll('.tc-file-chip-del').forEach(function(btn){
      btn.addEventListener('click', function(){
        removeFile(this.getAttribute('data-fname'));
      });
    });
  }

  function buildAttachmentSection(files) {
    if (!files || files.length === 0) return '';
    var parts = ['## ATTACHED FILES'];
    files.forEach(function(f){
      if (f.content && f.content.indexOf('data:image/') === 0) {

        parts.push('### Image: ' + f.name + '\n' + f.content);
      } else if (f.content) {
        parts.push('### File: ' + f.name + '\n```\n' + f.content.slice(0, 8000) + '\n```');
      } else {
        parts.push('### File: ' + f.name + ' — referenced for context');
      }
    });
    return parts.join('\n\n');
  }

  var TC_FB_EXTS = 'png,jpg,jpeg,gif,webp,svg,tif,tiff,pdf,txt,md,json,yaml,yml,xml,csv,log,properties,kt,java,ts,js,py,sh,html,css,sql,toml,ini,conf,gradle';

  function tcIsBinaryFile(name) {
    return /\.(png|jpe?g|gif|webp|tiff?|pdf)$/i.test(name);
  }

  function tcResizeImage(dataUrl, maxDim, quality, callback) {
    var img = new Image();
    img.onload = function() {
      var w = img.naturalWidth, h = img.naturalHeight;
      if (!w || !h) { callback(dataUrl); return; }
      var scale = Math.min(1, maxDim / Math.max(w, h));
      var cw = Math.round(w * scale), ch = Math.round(h * scale);
      var canvas = document.createElement('canvas');
      canvas.width = cw; canvas.height = ch;
      try {
        canvas.getContext('2d').drawImage(img, 0, 0, cw, ch);
        callback(canvas.toDataURL('image/jpeg', quality));
      } catch (e) { callback(dataUrl); }
    };
    img.onerror = function() { callback(dataUrl); };
    img.src = dataUrl;
  }

  function tcAddFile(name, content, path) {
    var exists = state.files.some(function(f){ return f.name === name; });
    if (!exists) state.files.push({ name: name, content: content, path: path || null });
    renderFileChips();
  }

  function tcRemoveFilePath(path) {
    state.files = state.files.filter(function(f){ return f.path !== path; });
    renderFileChips();
  }

  function tcLoadFileContent(path, name) {
    if (tcIsBinaryFile(name)) {
      return fetch('/api/ticket-creation/read-binary-file?path=' + encodeURIComponent(path), { credentials: 'same-origin' })
        .then(function(r){ return r.json(); })
        .then(function(data){
          if (data.error && !data.text && !data.dataUrl) throw new Error(data.error);
          if (!data.dataUrl) { tcAddFile(name, data.text || null, path); return; }
          tcResizeImage(data.dataUrl, 768, 0.65, function(resized) {
            tcAddFile(name, resized, path);
          });
        });
    }
    return fetch('/api/prompts/read-file?path=' + encodeURIComponent(path), { credentials: 'same-origin' })
      .then(function(r){ return r.json(); })
      .then(function(data){ tcAddFile(name, data.content || null, path); });
  }

  window.tcOpenFileBrowser = function() {
    var selected = state.files.map(function(f){ return f.path; }).filter(Boolean);
    __kiChatOpenDirBrowser(function (paths) {
      tcSyncAttachmentSelection(paths || []);
    }, {
      files: TC_FB_EXTS.split(','),
      multi: true,
      selected: selected,
      acceptHint: 'PNG, JPG, SVG, PDF, TXT, MD, JSON, YAML, …',
      storageKey: 'stacklens.fb.last.attachments',
      titleKey: 'ticket_creation.form_files_label',
      titleFallback: 'Dateien auswählen',
      load: tcLoadAttachmentEntries,
      onToggle: function (path, name, isSelected) {
        if (!isSelected) { tcRemoveFilePath(path); return; }
        tcLoadFileContent(path, name).catch(function (e) {
          showError((window.i18n ? window.i18n.t('common.error') : 'Fehler') + ': ' + (e.message || ''));
        });
      },
    });
  }

  function tcSyncAttachmentSelection(paths) {
    state.files = state.files.filter(function(f){ return !f.path || paths.indexOf(f.path) >= 0; });
    renderFileChips();
  }

  function tcLoadAttachmentEntries(path) {
    var url = '/api/tooling/files/browse?fileExtensions=' + TC_FB_EXTS + (path ? '&path=' + encodeURIComponent(path) : '');
    return fetch(url, { credentials: 'same-origin' }).then(function (r) {
      return r.json().then(function (data) {
        if (!r.ok || data.error) throw new Error(data.error || ('HTTP ' + r.status));
        var items = data.items || [];
        return {
          current: data.currentPath || '',
          parent: data.parentPath || null,
          directories: items.filter(function (i) { return i.type === 'directory'; }),
          files: items.filter(function (i) { return i.type !== 'directory'; }),
        };
      });
    });
  }

  function updateCharCount() {
    var ta = $('tcInputText');
    var cc = $('tcCharCount');
    if (!ta || !cc) return;
    cc.textContent = ta.value.length + ' ' + t('ticket_creation.chars', 'Zeichen');
  }

  function setGenerating(on) {
    var start  = $('tcStart');
    var cancel = $('tcCancel');
    var prog   = $('tcProgressCard');
    if (start)  { start.disabled = on; }

    if (cancel) { cancel.disabled = !on; }
    if (prog)   prog.style.display = on ? '' : 'none';
  }

  function setProgressStep(msg) {
    var el = $('tcProgressStep');
    if (el) el.textContent = msg;
  }

  function showError(msg) {
    var el = $('tcError');
    if (!el) return;
    el.textContent = msg;
    el.style.display = msg ? '' : 'none';
  }

  function generate() {
    var inputText = ($('tcInputText') || {}).value || '';
    if (!inputText.trim()) {
      $('tcInputText') && $('tcInputText').focus();
      return;
    }

    tcSessionSaveRunning(inputText, null, state.issueType);

    var resultCard = $('tcResultCard');
    if (resultCard) resultCard.style.display = 'none';
    showError('');
    var createSection = $('tcCreateSection');
    if (createSection) createSection.style.display = 'none';

    if (_tcRestorePollTimer) { clearTimeout(_tcRestorePollTimer); _tcRestorePollTimer = null; }

    setGenerating(true);
    setProgressStep(t('ticket_creation.progress_generating', 'KI generiert Ticket…'));

    var attachmentSection = buildAttachmentSection(state.files);
    state.abortCtrl = new AbortController();

    state.pendingInput = inputText;
    tcTrackRunningRecord();

    fetch('/api/ticket-creation/generate', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        inputText: inputText,
        attachmentSection: attachmentSection,
        issueType: state.issueType || 'Story'
      }),
      signal: state.abortCtrl.signal
    }).then(function(r){
      if (_tcPageGen !== window._tc.gen) return;
      if (!r.ok) {
        return r.json().then(function(err){
          throw new Error(err.error || ('HTTP ' + r.status));
        });
      }
      return r.json();
    }).then(function(data){
      if (!data) return;
      if (_tcPageGen !== window._tc.gen) return;
      setGenerating(false);

      if (data.error) {
        tcFinishRun();
        tcSessionClearRunning();
        tcLoadHistory();
        var resultCard2 = $('tcResultCard');
        if (resultCard2) resultCard2.style.display = '';
        showError(data.error);
        return;
      }

      state.ticketData = data;
      state.historyId = data.historyId || null;
      window._tc.historyId = state.historyId;
      if (typeof tcInitTokenUsageBar === 'function') tcInitTokenUsageBar();

      tcSessionSaveRunning(inputText, state.historyId, state.issueType);
      tcFinishRun();
      tcLoadHistory();
      var resultCard3 = $('tcResultCard');
      if (resultCard3) resultCard3.style.display = '';
      renderTicket(data);

      tcSessionClearRunning();
    }).catch(function(err){
      if (_tcPageGen !== window._tc.gen) return;
      if (err && err.name === 'AbortError') {
        setGenerating(false);
        setTimeout(function(){ tcLoadHistory(); }, 300);
        return;
      }
      tcSessionClearRunning();
      setGenerating(false);
      tcFinishRun();
      tcLoadHistory();
      var resultCard4 = $('tcResultCard');
      if (resultCard4) resultCard4.style.display = '';
      showError(err && err.message ? err.message : t('ticket_creation.error_generic', 'Fehler beim Generieren.'));
    });
  }

  function cancelGenerate() {
    var runningId = state.runningId;
    if (runningId == null) state.pendingCancel = true;
    if (state.abortCtrl) state.abortCtrl.abort();
    tcSessionClearRunning();
    setGenerating(false);
    if (runningId != null) tcCancelRecord(runningId);
  }

  function tcCancelRecord(id) {
    if (state.runningId != null && String(state.runningId) === String(id)) tcFinishRun();
    fetch('/api/ticket-creation/history/' + id + '/cancel', {
      method: 'POST', credentials: 'same-origin'
    }).then(function(){ tcLoadHistory(); })
      .catch(function(){ tcLoadHistory(); });
  }

  var CHECKBOX_MAX_OPTIONS = 8;
  var _tcReqFieldsTimer = null;
  var _tcReqFieldsSeq = 0;

  function tcCurrentCreateIssueType() {
    return ($('tcCreateIssueType') || {}).value || (state.ticketData && state.ticketData.issueType) || 'Story';
  }

  function tcScheduleRequiredFields() {
    if (_tcReqFieldsTimer) clearTimeout(_tcReqFieldsTimer);
    _tcReqFieldsTimer = setTimeout(tcLoadRequiredFields, 350);
  }

  function tcReadRequiredValues() {
    var values = {};
    state.requiredFields.forEach(function(f){
      if (f.multiple && f.allowedValues.length <= CHECKBOX_MAX_OPTIONS) {
        var checked = document.querySelectorAll('#tcRequiredFields input[data-field-id="' + f.fieldId + '"]:checked');
        values[f.fieldId] = Array.prototype.map.call(checked, function(cb){ return cb.value; });
      } else {
        var sel = document.querySelector('#tcRequiredFields select[data-field-id="' + f.fieldId + '"]');
        if (!sel) return;
        values[f.fieldId] = f.multiple
          ? Array.prototype.filter.call(sel.options, function(o){ return o.selected && o.value; }).map(function(o){ return o.value; })
          : sel.value;
      }
    });
    return values;
  }

  function tcRenderRequiredFields(fields, previous) {
    var box = $('tcRequiredFields');
    if (!box) return;
    state.requiredFields = fields;
    if (!fields.length) { box.innerHTML = ''; return; }

    var html = '<p class="tc-required-note">' +
      escapeHtml(t('ticket_creation.required_fields_note', 'Jira verlangt für diesen Vorgangstyp zusätzlich folgende Pflichtfelder:')) +
      '</p><div class="tc-create-row">';
    fields.forEach(function(f, i){
      var id = 'tcReqField' + i;
      var prev = previous[f.fieldId];
      var label = escapeHtml(f.name) + '<span class="tc-required-mark" aria-hidden="true">*</span>';
      html += '<div class="tc-create-field">';
      if (f.multiple && f.allowedValues.length <= CHECKBOX_MAX_OPTIONS) {
        html += '<fieldset class="tc-check-group" id="' + id + '" aria-required="true">' +
          '<legend class="tc-create-label" style="padding:0 4px;">' + label + '</legend>';
        f.allowedValues.forEach(function(v){
          var isChecked = Array.isArray(prev) && prev.indexOf(v) >= 0;
          html += '<label class="tc-check-option"><input type="checkbox" data-field-id="' + escapeHtml(f.fieldId) +
            '" value="' + escapeHtml(v) + '"' + (isChecked ? ' checked' : '') + '>' + escapeHtml(v) + '</label>';
        });
        html += '</fieldset>';
      } else {
        html += '<label class="tc-create-label" for="' + id + '">' + label + '</label>' +
          '<select id="' + id + '" class="tc-input" required data-field-id="' + escapeHtml(f.fieldId) + '"' +
          (f.multiple ? ' multiple size="5"' : '') + '>';
        if (!f.multiple) {
          html += '<option value="">' + escapeHtml(t('ticket_creation.required_field_choose', 'Bitte wählen…')) + '</option>';
        }
        f.allowedValues.forEach(function(v){
          var isSel = f.multiple ? (Array.isArray(prev) && prev.indexOf(v) >= 0) : prev === v;
          html += '<option value="' + escapeHtml(v) + '"' + (isSel ? ' selected' : '') + '>' + escapeHtml(v) + '</option>';
        });
        html += '</select>';
      }
      html += '</div>';
    });
    box.innerHTML = html + '</div>';
  }

  function tcLoadRequiredFields() {
    var box = $('tcRequiredFields');
    var configSel = $('tcCreateJiraConfig') || $('tcJiraConfig');
    var configId = configSel ? Number(configSel.value) : null;
    var projectKey = ((($('tcCreateProjectKey') || $('tcProjectKey')) || {}).value || '').trim().toUpperCase();
    var issueType = tcCurrentCreateIssueType();
    var seq = ++_tcReqFieldsSeq;
    var previous = state.requiredFields.length ? tcReadRequiredValues() : {};

    if (!box || !configId || !projectKey || !state.ticketData) {
      state.requiredFieldsLoading = false;
      tcRenderRequiredFields([], {});
      return;
    }

    state.requiredFieldsLoading = true;
    state.requiredFields = [];
    box.innerHTML = '<p class="tc-required-note"><i class="bi bi-arrow-clockwise" style="animation:tc-spin 1s linear infinite;display:inline-block;"></i> ' +
      escapeHtml(t('ticket_creation.required_fields_loading', 'Prüfe Jira-Pflichtfelder…')) + '</p>';

    var url = '/api/ticket-creation/required-fields?jiraConfigId=' + encodeURIComponent(configId) +
      '&projectKey=' + encodeURIComponent(projectKey) + '&issueType=' + encodeURIComponent(issueType);
    fetch(url, { credentials: 'same-origin' })
      .then(function(r){
        if (!r.ok) return r.json().then(function(e){ throw new Error(e.error || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function(fields){
        if (seq !== _tcReqFieldsSeq) return;
        state.requiredFieldsLoading = false;
        tcRenderRequiredFields(Array.isArray(fields) ? fields : [], previous);
      })
      .catch(function(err){
        if (seq !== _tcReqFieldsSeq) return;
        state.requiredFieldsLoading = false;
        state.requiredFields = [];
        box.innerHTML = '<p class="tc-required-note tc-status-fail"><i class="bi bi-exclamation-triangle-fill me-1"></i>' +
          escapeHtml(t('ticket_creation.required_fields_error', 'Jira-Pflichtfelder konnten nicht geladen werden:')) + ' ' +
          escapeHtml(err && err.message ? err.message : '') + '</p>';
      });
  }

  function tcValidateRequiredFields(values, statusEl) {
    var missing = state.requiredFields.filter(function(f){
      var v = values[f.fieldId];
      return Array.isArray(v) ? v.length === 0 : !v;
    });
    if (!missing.length) return true;
    missing.forEach(function(f, i){
      var idx = state.requiredFields.indexOf(f);
      var el = $('tcReqField' + idx);
      if (!el) return;
      el.classList.add('tc-input--invalid');
      el.addEventListener('change', function _rm(){ el.classList.remove('tc-input--invalid'); el.removeEventListener('change', _rm); });
      if (i === 0) (el.querySelector('input') || el).focus();
    });
    if (statusEl) statusEl.innerHTML =
      '<span class="tc-status-fail"><i class="bi bi-exclamation-circle-fill me-1"></i>' +
      escapeHtml(t('ticket_creation.required_fields_missing', 'Bitte Jira-Pflichtfelder ausfüllen:')) + ' ' +
      escapeHtml(missing.map(function(f){ return f.name; }).join(', ')) + '</span>';
    return false;
  }

  function createJiraTicket() {
    if (!state.ticketData) return;

    var configSel  = $('tcCreateJiraConfig') || $('tcJiraConfig');
    var projectKey = (($('tcCreateProjectKey') || $('tcProjectKey')) || {}).value || '';
    var configId   = configSel ? Number(configSel.value) : null;
    var statusEl   = $('tcCreateStatus');

    if (!configId) {
      if (statusEl) statusEl.innerHTML =
        '<span class="tc-status-fail"><i class="bi bi-exclamation-circle-fill me-1"></i>' +
        t('ticket_creation.error_no_jira_config', 'Bitte eine Jira-Konfiguration auswählen.') + '</span>';
      if (configSel) {
        configSel.classList.add('tc-input--invalid');
        setTimeout(function(){ configSel.classList.remove('tc-input--invalid'); }, 1500);
        configSel.focus();
      }
      return;
    }
    if (!projectKey.trim()) {
      if (statusEl) statusEl.innerHTML =
        '<span class="tc-status-fail"><i class="bi bi-exclamation-circle-fill me-1"></i>' +
        t('ticket_creation.error_no_project_key', 'Bitte einen Projekt-Key eingeben.') + '</span>';
      var pkEl = $('tcCreateProjectKey') || $('tcProjectKey');
      if (pkEl) {
        pkEl.classList.add('tc-input--invalid');
        pkEl.addEventListener('input', function _rm(){ pkEl.classList.remove('tc-input--invalid'); if (statusEl) statusEl.innerHTML = ''; pkEl.removeEventListener('input', _rm); });
        pkEl.focus();
      }
      return;
    }

    if (state.requiredFieldsLoading) {
      if (statusEl) statusEl.innerHTML = '<span style="color:var(--tc-muted);font-size:.85rem;">' +
        escapeHtml(t('ticket_creation.required_fields_loading', 'Prüfe Jira-Pflichtfelder…')) + '</span>';
      return;
    }
    var customFields = tcReadRequiredValues();
    if (!tcValidateRequiredFields(customFields, statusEl)) return;

    var btn = $('tcBtnCreate');
    if (btn) { btn.disabled = true; }
    if (statusEl) statusEl.innerHTML = '<span style="color:var(--tc-muted);font-size:.85rem;">' +
      '<i class="bi bi-arrow-clockwise" style="animation:tc-spin 1s linear infinite;display:inline-block;"></i> ' +
      t('ticket_creation.creating', 'Erstelle Ticket…') + '</span>';

    var subtaskDrafts = collectSubtaskDrafts();

    fetch('/api/ticket-creation/create-jira', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        jiraConfigId: configId,
        projectKey: projectKey.trim().toUpperCase(),
        summary: state.ticketData.summary,
        description: state.ticketData.description,
        issueType: ($('tcCreateIssueType') || {}).value || state.ticketData.issueType || 'Story',
        priority: state.ticketData.priority || 'Minor',
        labels: state.ticketData.labels || [],
        historyId: state.historyId || null,
        subtasks: subtaskDrafts,
        customFields: customFields
      })
    }).then(function(r){
      if (!r.ok) {
        return r.json().then(function(e){ throw new Error(e.error || ('HTTP ' + r.status)); });
      }
      return r.json();
    }).then(function(result){
      if (btn) btn.disabled = false;
      if (statusEl) {
        var resultSubtasks = Array.isArray(result.subtasks) ? result.subtasks : [];
        var createdSubtasks = resultSubtasks.filter(function(st){ return st && st.key && !st.error; });
        var failedSubtasks = resultSubtasks.filter(function(st){ return !st || st.error || !st.key; });
        var missingSubtaskCount = Math.max(0, subtaskDrafts.length - resultSubtasks.length);
        var failedSubtaskCount = failedSubtasks.length + missingSubtaskCount;
        var hasSubtaskFailure = subtaskDrafts.length > 0 && failedSubtaskCount > 0;
        var statusClass = hasSubtaskFailure ? 'tc-status-fail' : 'tc-status-ok';
        var statusIcon = hasSubtaskFailure ? 'bi-exclamation-triangle-fill' : 'bi-check-circle-fill';
        var parentLink = result.url
          ? '<a href="' + escapeHtml(result.url) + '" target="_blank" rel="noopener noreferrer" style="color:inherit;">' + escapeHtml(result.key || result.url) + '</a>'
          : escapeHtml(result.key || '');
        var subtaskStatus = '';
        if (subtaskDrafts.length > 0) {
          subtaskStatus = ' · ' + t('ticket_creation.subtasks_created', 'Subtasks erstellt') + ': ' +
            createdSubtasks.length + '/' + subtaskDrafts.length;
        }
        if (hasSubtaskFailure) {
          var failureDetails = failedSubtasks.map(function(st){
            var summary = st && st.summary ? st.summary : t('ticket_creation.subtask_unknown', 'Unbekannter Subtask');
            var error = st && st.error ? ': ' + st.error : '';
            return summary + error;
          });
          if (missingSubtaskCount > 0) {
            failureDetails.push(missingSubtaskCount + ' ' + t('ticket_creation.subtasks_missing_response', 'ohne Jira-Antwort'));
          }
          subtaskStatus += ' · ' + t('ticket_creation.subtasks_failed', 'Nicht erstellt') + ': ' + escapeHtml(failureDetails.join('; '));
        }
        statusEl.innerHTML = '<span class="' + statusClass + '">' +
          '<i class="bi ' + statusIcon + ' me-1"></i>' +
          t('ticket_creation.created_ok', 'Ticket erstellt') + ': ' + parentLink + subtaskStatus +
          '</span>';
      }
      tcLoadHistory();
    }).catch(function(err){
      if (btn) btn.disabled = false;
      if (statusEl) {
        statusEl.innerHTML = '<span class="tc-status-fail">' +
          '<i class="bi bi-exclamation-circle-fill me-1"></i>' +
          escapeHtml(err && err.message ? err.message : t('ticket_creation.error_create', 'Fehler beim Erstellen.')) +
          '</span>';
      }
    });
  }

  function _tcApplyRestored(inputText, ticketData, historyId) {
    var inputEl = $('tcInputText');
    if (inputEl && inputText) { inputEl.value = inputText; updateCharCount(); }

    state.ticketData = ticketData;
    state.historyId  = historyId || null;
    window._tc.historyId = state.historyId;

    var resultCard = $('tcResultCard');
    if (resultCard) resultCard.style.display = '';
    showError('');
    renderTicket(ticketData);

    var createSection = $('tcCreateSection');
    if (createSection) createSection.style.display = '';

    if (ticketData.issueType) {
      window.tcSetIssueType(ticketData.issueType);
      window.tcSetGenerateIssueType(ticketData.issueType);
    }

    syncResultJiraConfig();
    tcLoadHistory();
  }

  function _tcPollForResult(running) {
    if (_tcRestorePollTimer) { clearTimeout(_tcRestorePollTimer); _tcRestorePollTimer = null; }
    var attempts = 0;
    var maxAttempts = 90;

    function tryFetch() {
      attempts++;
      var url = running.historyId
        ? '/api/ticket-creation/history/' + running.historyId
        : '/api/ticket-creation/history';

      fetch(url, { credentials: 'same-origin' })
        .then(function(r){ return r.ok ? r.json() : null; })
        .then(function(data){

          var record = null;
          if (running.historyId) {

            record = (data && data.resultJson) ? data : null;
          } else {
            if (Array.isArray(data) && data.length) {
              var inputNormalized = (running.inputText || '').trim().toLowerCase().substring(0, 100);

              var minStartedAt = (typeof running.startedAt === 'number') ? (running.startedAt - 10000) : 0;
              for (var i = 0; i < Math.min(data.length, 5); i++) {
                var candidate = data[i];
                if (!(candidate.resultJson || candidate.status === 'FAILED')) continue;
                var candidateInput = (candidate.inputText || '').trim().toLowerCase().substring(0, 100);
                if (inputNormalized !== candidateInput) continue;
                var candidateCreatedMs = candidate.createdAt ? Date.parse(candidate.createdAt) : NaN;
                if (!isNaN(candidateCreatedMs) && candidateCreatedMs < minStartedAt) continue;
                record = candidate;
                break;
              }
            }
          }

          if (record && (record.resultJson || record.status === 'FAILED')) {
            setGenerating(false);
            tcFinishRun();
            tcLoadHistory();

            if (record.status === 'FAILED') {

              tcSessionClearRunning();
              var resultCard = $('tcResultCard');
              if (resultCard) resultCard.style.display = '';
              showError(record.errorMessage || t('ticket_creation.error_generic', 'Fehler beim Generieren.'));
            } else {

              var ticketData = null;
              try { ticketData = JSON.parse(record.resultJson); } catch (_) {}
              if (ticketData) {
                tcSessionClearRunning();
                _tcApplyRestored(running.inputText, ticketData, record.id);
              } else {
                tcSessionClearRunning();
                showError(t('ticket_creation.error_generic', 'Fehler beim Generieren.'));
              }
            }
          } else if (attempts < maxAttempts) {
            _tcRestorePollTimer = setTimeout(tryFetch, 2000);
          } else {

            setGenerating(false);
            tcSessionClearRunning();
            tcFinishRun();
            tcLoadHistory();
            var resultCard2 = $('tcResultCard');
            if (resultCard2) resultCard2.style.display = '';
            showError(t('ticket_creation.error_timeout', 'Zeitüberschreitung: Die Generierung dauert zu lange. Bitte versuche es erneut.'));
          }
        })
        .catch(function(){
          if (attempts < maxAttempts) {
            _tcRestorePollTimer = setTimeout(tryFetch, 2000);
          } else {
            setGenerating(false);
            tcSessionClearRunning();
            tcFinishRun();
            tcLoadHistory();
          }
        });
    }

    tryFetch();
  }

  function tcRestoreSession() {

    if (state.ticketData || _tcRestorePollTimer) return;

    var running = tcSessionLoadRunning();
    if (running && running.inputText !== undefined) {
      var inputEl = $('tcInputText');
      if (inputEl && running.inputText) { inputEl.value = running.inputText; updateCharCount(); }

      var resultCard = $('tcResultCard');
      if (resultCard) resultCard.style.display = 'none';
      var createSection = $('tcCreateSection');
      if (createSection) createSection.style.display = 'none';
      showError('');

      setGenerating(true);
      setProgressStep(t('ticket_creation.progress_restoring', 'Ergebnis wird geladen\u2026'));
      if (running.issueType) {
        window.tcSetGenerateIssueType(running.issueType);
      }
      state.pendingInput = running.inputText;
      tcTrackRunningRecord();
      _tcPollForResult(running);
    }
  }

  function init() {
    window.tcSetGenerateIssueType(state.issueType || 'Story');

    tcRestoreSession();

    var form = $('tcForm');
    if (form) form.addEventListener('submit', function(e){ e.preventDefault(); generate(); });

    var startBtn = $('tcStart');
    if (startBtn) startBtn.addEventListener('click', generate);

    var cancelBtn = $('tcCancel');
    if (cancelBtn) cancelBtn.addEventListener('click', cancelGenerate);

    var regenBtn = $('tcBtnRegenerate');
    if (regenBtn) regenBtn.addEventListener('click', function(){
      var resultCard = $('tcResultCard');
      if (resultCard) resultCard.style.display = 'none';
      generate();
    });

    var createBtn = $('tcBtnCreate');
    if (createBtn) createBtn.addEventListener('click', createJiraTicket);

    var ta = $('tcInputText');
    if (ta) {
      ta.addEventListener('input', updateCharCount);
      updateCharCount();

      if (window.SLScroll) {
        window.SLScroll(ta, $('tcInputScrollTrack'), $('tcInputScrollThumb'));
      }
    }

    var pkInput = $('tcProjectKey');
    if (pkInput) {
      pkInput.addEventListener('input', function(){
        var pos = this.selectionStart;
        this.value = this.value.toUpperCase();
        this.setSelectionRange(pos, pos);
      });
    }
    var pkInput2 = $('tcCreateProjectKey');
    if (pkInput2) {
      pkInput2.addEventListener('input', function(){
        var pos = this.selectionStart;
        this.value = this.value.toUpperCase();
        this.setSelectionRange(pos, pos);
        tcScheduleRequiredFields();
      });
    }
    var createCfgSel = $('tcCreateJiraConfig');
    if (createCfgSel) createCfgSel.addEventListener('change', tcScheduleRequiredFields);

    var zone = $('tcUploadZone');
    if (zone) {
      zone.addEventListener('dragover', function(e){ e.preventDefault(); });
      zone.addEventListener('drop', function(e){ e.preventDefault(); });
    }

    loadJiraConfigs();

    window.i18n.onReady(tcLoadHistory);
  }

  if (window._tcNavigateHandler) document.removeEventListener('app:navigate', window._tcNavigateHandler);
  window._tcNavigateHandler = function() {
    if (!document.getElementById('hxCard-ticket-creation')) return;
    tcLoadHistory();

    if (!state.ticketData && !_tcRestorePollTimer) {
      tcRestoreSession();
    }
  };
  document.addEventListener('app:navigate', window._tcNavigateHandler);

  var _tcHistory = null;

  function tcHistory() {
    if (_tcHistory) return _tcHistory;
    _tcHistory = window.slHistory.mount({
      root: 'hxCard-ticket-creation',
      page: 'ticket-creation',
      titleKey: 'ticket_creation.history_title',
      emptyKey: 'ticket_creation.history_empty',
      columns: {
        title: { key: 'ticket_creation.col_summary', fallback: 'Zusammenfassung' }
      },
      map: {
        id: function(r) { return r.id; },
        status: function(r) { return r.status; },
        createdAt: function(r) { return r.createdAt; },
        typeIcon: function(r) { return r.jiraKey ? 'bi-ticket-perforated' : 'bi-clipboard-plus'; },
        title: function(r) { return r.summary || r.errorMessage || tcRunningPreview(r.inputText); },
        subtitle: function(r) { return r.jiraProjectKey || ''; },
        tags: function(r) {
          if (!r.jiraKey) return [];
          return [{ label: r.jiraKey, href: r.jiraUrl || '', title: r.jiraUrl || '' }];
        },
        note: function(r) { return r.errorMessage || ''; },
        isCurrent: function(r) { return state.runningId != null && state.runningId === r.id; }
      },
      actions: {
        open: function(r) { tcShowHistoryItem(r.id); },
        cancel: function(r) { tcCancelRecord(r.id); },
        remove: function(r) { tcDeleteHistoryItem(r.id); },
        deleteAll: function() { tcDeleteAllHistory(); }
      }
    });
    return _tcHistory;
  }

  function tcRunningPreview(inputText) {
    var firstLine = String(inputText || '').split('\n')[0].trim();
    if (!firstLine) return '';
    return firstLine.length > 80 ? firstLine.substring(0, 80) + '\u2026' : firstLine;
  }

  function tcMatchesRunningRecord(r) {
    if (state.pendingInput == null) return false;
    var status = String(r.status || '').toUpperCase();
    if (status !== 'RUNNING' && status !== 'PENDING') return false;
    return String(r.inputText || '') === state.pendingInput.trim().substring(0, TC_RUNNING_INPUT_LIMIT);
  }

  function tcAdoptRunningRecord(list) {
    if (state.runningId != null) return;
    var match = (list || []).filter(tcMatchesRunningRecord)[0];
    if (!match) return;
    state.runningId = match.id;
    if (state.pendingCancel) {
      state.pendingCancel = false;
      tcCancelRecord(match.id);
    }
  }

  function tcStopRunningTracking() {
    if (_tcAdoptTimer) { clearTimeout(_tcAdoptTimer); _tcAdoptTimer = null; }
  }

  function tcScheduleRunningTracking() {
    tcStopRunningTracking();
    if (state.pendingInput == null || state.runningId != null) return;
    if (_tcAdoptAttempts >= TC_ADOPT_MAX_ATTEMPTS) return;
    _tcAdoptAttempts++;
    _tcAdoptTimer = setTimeout(tcLoadHistory, TC_ADOPT_INTERVAL_MS);
  }

  function tcTrackRunningRecord() {
    tcStopRunningTracking();
    _tcAdoptAttempts = 0;
    tcLoadHistory();
  }

  function tcFinishRun() {
    state.pendingInput = null;
    state.pendingCancel = false;
    state.runningId = null;
    tcStopRunningTracking();
  }

  function tcLoadHistory() {
    fetch('/api/ticket-creation/history', { credentials: 'same-origin' })
      .then(function(r){ return r.json(); })
      .then(function(list){
        tcRenderHistory(list || []);
        var hasRunning = (list || []).some(function(r){ return r.status === 'RUNNING'; });
        if (hasRunning && !_tcRunningPollTimer) {
          _tcRunningPollTimer = setTimeout(function(){ _tcRunningPollTimer = null; tcLoadHistory(); }, 3000);
        } else if (!hasRunning && _tcRunningPollTimer) {
          clearTimeout(_tcRunningPollTimer); _tcRunningPollTimer = null;
        }
      })
      .catch(function(){ tcScheduleRunningTracking(); });
  }

  function tcRenderHistory(list) {
    tcAdoptRunningRecord(list);
    tcScheduleRunningTracking();
    var history = tcHistory();
    if (history) history.setData(list || []);
  }

  window.tcShowHistoryItem = function(id) {
    fetch('/api/ticket-creation/history/' + id, { credentials: 'same-origin' })
      .then(function(r){ return r.json(); })
      .then(function(data){

        var inputEl = document.getElementById('tcInputText');
        if (inputEl && data.inputText) { inputEl.value = data.inputText; updateCharCount(); }

        if (data.status === 'FAILED') {
          var resultCard = $('tcResultCard');
          if (resultCard) resultCard.style.display = '';
          var createSection = $('tcCreateSection');
          if (createSection) createSection.style.display = 'none';
          showError(data.errorMessage || t('ticket_creation.error_generic', 'Fehler beim Generieren.'));
          window.scrollTo({ top: 0, behavior: 'smooth' });
          return;
        }

        if (data.resultJson) {
          var ticketData = null;
          try { ticketData = JSON.parse(data.resultJson); } catch(_) {}
          if (ticketData) {
            state.ticketData = ticketData;
            state.historyId = data.id;
            window._tc.historyId = state.historyId;
            if (typeof tcInitTokenUsageBar === 'function') tcInitTokenUsageBar();

            var resultCard = $('tcResultCard');
            if (resultCard) resultCard.style.display = '';
            showError('');
            renderTicket(ticketData);

            var createSection = $('tcCreateSection');
            if (createSection) createSection.style.display = '';

            if (ticketData.issueType) {
              window.tcSetIssueType(ticketData.issueType);
              window.tcSetGenerateIssueType(ticketData.issueType);
            }

            var createKey = $('tcCreateProjectKey');
            if (createKey) {
              var storedKey = data.jiraProjectKey || ($('tcProjectKey') || {}).value || '';
              if (storedKey.trim()) createKey.value = storedKey.trim().toUpperCase();
            }
            tcScheduleRequiredFields();

            var statusEl = $('tcCreateStatus');
            if (statusEl && data.jiraKey) {
              var jiraLink = data.jiraUrl
                ? '<a href="' + escapeHtml(data.jiraUrl) + '" target="_blank" rel="noopener" style="color:var(--tc-ok);">' +
                  escapeHtml(data.jiraKey) + '</a>'
                : escapeHtml(data.jiraKey);
              statusEl.innerHTML = '<span class="tc-status-ok">' +
                '<i class="bi bi-check-circle-fill me-1"></i>' +
                (window.i18n ? window.i18n.t('ticket_creation.history_submitted') : 'In Jira erstellt') +
                ': ' + jiraLink + '</span>';
            } else if (statusEl) {
              statusEl.innerHTML = '';
            }

            syncResultJiraConfig();
          }
        }

        window.scrollTo({ top: 0, behavior: 'smooth' });
      })
      .catch(function(){});
  };

  window.tcDeleteHistoryItem = function(id) {
    fetch('/api/ticket-creation/history/' + id, { method: 'DELETE', credentials: 'same-origin' })
      .then(function(){ tcLoadHistory(); })
      .catch(function(){});
  };

  window.tcDeleteAllHistory = function() {
    fetch('/api/ticket-creation/history', { method: 'DELETE', credentials: 'same-origin' })
      .then(function(){ tcLoadHistory(); })
      .catch(function(){});
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();

