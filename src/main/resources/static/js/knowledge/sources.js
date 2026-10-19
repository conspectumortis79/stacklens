'use strict';

  function kbSourceRow (id) {
    return document.querySelector('tr[data-source-id="' + String(id) + '"]');
  }

  function kbBuildSourceProgressStrip (sourceId, label, stage, opts) {
    opts = opts || {};
    var showCancel = opts.showCancel !== false;
    var tr = document.createElement('tr');
    tr.className = 'kb-source-progress-row';
    tr.setAttribute('data-kb-progress-source-id', String(sourceId));
    var cols = 7;
    var cancelHtml = showCancel
      ? '<button type="button" class="kb-source-progress-cancel" onclick="kbCancelSourceProgressByBtn(this)">' +
          esc(t('common.cancel', 'Abbrechen')) + '</button>'
      : '<span class="kb-source-progress-cancel-placeholder"></span>';
    var stageHtml = stage
      ? '<span class="kb-source-progress-stage"> · ' + esc(stage) + '</span>'
      : '';
    tr.innerHTML =
      '<td colspan="' + cols + '"><div class="kb-source-progress-line">' +
      '<i class="bi bi-arrow-clockwise spin kb-source-progress-icon"></i>' +
      '<div class="kb-source-progress-text">' + esc(label) + stageHtml + '</div>' +
      '<div class="kb-progress-bar-indeterminate"><div class="kb-progress-bar-stripe"></div></div>' +
      cancelHtml +
      '</div></td>';
    return tr;
  }

  function kbShowSourceProgress (sourceId, label, stage, opts) {
    var row = kbSourceRow(sourceId);
    if (!row) return null;
    var next = row.nextElementSibling;
    if (next && next.classList && next.classList.contains('kb-source-progress-row')) return next;
    var strip = kbBuildSourceProgressStrip(sourceId, label, stage, opts);
    if (row.nextSibling) row.parentNode.insertBefore(strip, row.nextSibling);
    else row.parentNode.appendChild(strip);
    return strip;
  }

  function kbHideSourceProgress (sourceId) {
    var row = kbSourceRow(sourceId);
    if (!row || !row.nextElementSibling) return;
    var next = row.nextElementSibling;
    if (next.classList && next.classList.contains('kb-source-progress-row')) {
      next.parentNode.removeChild(next);
    }
  }

  function kbCancelSourceProgressByBtn (btn) {
    var tr = btn.closest('tr.kb-source-progress-row');
    if (!tr) return;
    kbCancelSourceProgressById(tr.getAttribute('data-kb-progress-source-id'));
  }

  function kbCancelSourceProgressById (sourceId) {
    var ctrl = kbInflightSourceFetches[String(sourceId)];
    if (!ctrl) return;
    ctrl.abort();
    delete kbInflightSourceFetches[String(sourceId)];
  }

  function kbSyncSource (id) {
    kbShowSourceProgress(
      id,
      t('knowledge.source.sync_in_progress', 'Synchronisiere Jira-Quelle'),
      t('knowledge.source.sync_stage_walk', 'lade Hierarchie'),
    );
    var ctrl = new AbortController();
    kbInflightSourceFetches[String(id)] = ctrl;
    fetch('/api/knowledge/sources/' + id + '/sync', { method: 'POST', signal: ctrl.signal })
      .then(function () {
        kbNotify('success', t('knowledge.source.sync_queued', 'Synchronisierung in der Warteschlange.'));
        kbLoadSources();
      })
      .catch(function (e) {
        if (e && e.name === 'AbortError') {
          kbNotify('info', t('knowledge.action.cancelled', 'Aktion abgebrochen.'));
          return;
        }
        kbNotify('danger', e.message);
      })
      .finally(function () {
        delete kbInflightSourceFetches[String(id)];
        kbHideSourceProgress(String(id));
      });
  }

  function kbDeleteSource (id) {

    fetch('/api/knowledge/sources/' + id, { method: 'DELETE' })
      .then(function () { kbNotify('success', t('knowledge.source.deleted', 'Quelle gelöscht.')); kbLoadSources(); })
      .catch(function (e) { kbNotify('danger', e.message); });
  }

  var kbBasesCache = null;
  var kbBasesInflight = null;

  function kbInvalidateBasesCache () { kbBasesCache = null; }

  function kbFetchBases() {
    if (kbBasesCache) return Promise.resolve(kbBasesCache);
    if (kbBasesInflight) return kbBasesInflight;
    kbBasesInflight = fetch('/api/knowledge/bases')
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (bases) {
        kbBasesCache = bases;
        kbBasesInflight = null;
        return bases;
      })
      .catch(function (e) {
        kbBasesInflight = null;
        throw e;
      });
    return kbBasesInflight;
  }

  function kbPopulateBases(selector) {
    return kbFetchBases().then(function (bases) {
      var sel = document.querySelector(selector);
      if (!sel) return;
      if (!bases.length) {

        sel.innerHTML = '<option value="" disabled selected>' +
          esc(t('knowledge.bases.none_yet', 'Keine Wissensdatenbank — bitte im Tab "Wissensdatenbanken" eine anlegen.')) +
          '</option>';
        return;
      }
      sel.innerHTML = bases.map(function (b) {
        return '<option value="' + b.id + '">' + esc(b.name) + (b.systemManaged ? ' (System)' : '') + '</option>';
      }).join('');
    });
  }

  var KB_JIRA_CONFIG_LOOKUP = {};
  var KB_CONFLUENCE_CONFIG_LOOKUP = {};
  function kbLoadConnectionLists() {
    var cfP = fetch('/api/config/confluence').then(function (r) { return r.json(); });
    var jrP = fetch('/api/config/jira').then(function (r) { return r.json(); });
    return Promise.all([cfP, jrP]).then(function (lists) {
      var cf = lists[0] || [];
      var jr = lists[1] || [];
      KB_JIRA_CONFIG_LOOKUP = {};
      jr.forEach(function (c) { KB_JIRA_CONFIG_LOOKUP[String(c.id)] = c; });
      KB_CONFLUENCE_CONFIG_LOOKUP = {};
      cf.forEach(function (c) { KB_CONFLUENCE_CONFIG_LOOKUP[String(c.id)] = c; });
      var cfSel = document.getElementById('kbConfluenceConfig');
      if (cfSel) {
        cfSel.innerHTML = cf.map(function (c) { return '<option value="' + c.id + '" data-baseurl="' + esc(c.baseUrl || '') + '">' + esc(c.name) + '</option>'; }).join('');
      }
      var jrSel = document.getElementById('kbJiraConfig');
      if (jrSel) {
        jrSel.innerHTML = jr.map(function (c) { return '<option value="' + c.id + '" data-baseurl="' + esc(c.baseUrl || '') + '">' + esc(c.name) + '</option>'; }).join('');
      }
      var jrSprintSel = document.getElementById('kbJiraSprintConfig');
      if (jrSprintSel) {
        jrSprintSel.innerHTML = jr.map(function (c) { return '<option value="' + c.id + '" data-baseurl="' + esc(c.baseUrl || '') + '">' + esc(c.name) + '</option>'; }).join('');
      }
    });
  }

  var KB_FILE_EXTENSIONS = ['pdf', 'doc', 'docx', 'txt', 'md', 'markdown'];
