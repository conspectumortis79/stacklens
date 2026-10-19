'use strict';

  function kbSwitchTab (tabId, btn) {
    document.querySelectorAll('.kb-tab-pane').forEach(function (p) {
      p.classList.toggle('d-none', p.getAttribute('data-kb-pane') !== tabId);
    });
    document.querySelectorAll('#kbTabs .nav-link').forEach(function (b) {
      b.classList.toggle('active', b === btn);
    });
    if (tabId === 'sources') kbLoadSources();
    if (tabId === 'bases') kbLoadBases();
    if (tabId === 'config') kbLoadConfig();
    if (tabId === 'overview') kbLoadOverview();
  }

  function kbOnProviderChange () {
    var provider = document.getElementById('kbConfigProvider').value;
    document.getElementById('kbConfigOllamaSection').classList.toggle('d-none', provider !== 'OLLAMA');
    document.getElementById('kbConfigOpenAiSection').classList.toggle('d-none', provider !== 'OPENAI');
    document.getElementById('kbConfigOpenRouterSection').classList.toggle('d-none', provider !== 'OPENROUTER');
    kbRefreshModelList();
  }

  function kbRefreshModelList () {
    var provider = document.getElementById('kbConfigProvider').value;
    var ollamaUrl = document.getElementById('kbConfigOllamaUrl').value;
    var openRouterKey = document.getElementById('kbConfigOpenRouterKey').value;
    var modelSelect = document.getElementById('kbConfigModel');
    var refreshBtn = document.getElementById('kbConfigModelRefresh');
    if (!modelSelect) return;
    var previousSelection = modelSelect.value;
    modelSelect.innerHTML = '<option value="">' + esc(t('knowledge.config.model_loading', 'Lade Modelle…')) + '</option>';
    if (refreshBtn) refreshBtn.disabled = true;
    var qs = new URLSearchParams({ provider: provider });
    if (provider === 'OLLAMA' && ollamaUrl) qs.set('ollamaUrl', ollamaUrl);
    if (provider === 'OPENROUTER' && openRouterKey) qs.set('openRouterApiKey', openRouterKey);
    fetch('/api/knowledge/config/models?' + qs.toString())
      .then(function (r) { return r.json(); })
      .then(function (d) {
        var models = (d && d.models) || [];
        modelSelect.innerHTML = '';
        if (models.length === 0) {
          var opt = document.createElement('option');
          opt.value = '';
          opt.textContent = t('knowledge.config.model_none', '— keine Modelle gefunden —');
          modelSelect.appendChild(opt);
        } else {
          models.forEach(function (m) {
            var opt = document.createElement('option');
            opt.value = m;
            opt.textContent = m;
            modelSelect.appendChild(opt);
          });
        }
        var savedModel = (kbSavedConfig && kbSavedConfig.model) || '';
        var candidates = [];
        if (previousSelection) candidates.push(previousSelection);
        if (savedModel && candidates.indexOf(savedModel) < 0) candidates.push(savedModel);
        var chosen = '';
        for (var i = 0; i < candidates.length; i++) {
          for (var j = 0; j < modelSelect.options.length; j++) {
            if (modelSelect.options[j].value === candidates[i]) { chosen = candidates[i]; break; }
          }
          if (chosen) break;
        }
        if (!chosen && savedModel) {
          var keep = document.createElement('option');
          keep.value = savedModel;
          keep.textContent = savedModel + ' ' + t('knowledge.config.model_kept', '(aktuell gespeichert)');
          modelSelect.insertBefore(keep, modelSelect.firstChild);
          chosen = savedModel;
        }
        if (chosen) modelSelect.value = chosen;
        kbMarkPendingChange();
      })
      .catch(function (e) {
        modelSelect.innerHTML = '<option value="">' + esc(t('knowledge.config.model_error', 'Modelle konnten nicht geladen werden') + ': ' + e.message) + '</option>';
        kbNotify('danger', e.message);
      })
      .then(function () { if (refreshBtn) refreshBtn.disabled = false; kbSyncOverviewModel(); });
  }

  var kbSavedConfig = null;

  function kbSyncOverviewModel() {
    var modelCard = document.getElementById('kbMetricModel');
    var provCard = document.getElementById('kbMetricProvider');
    var saved = kbSavedConfig;
    if (modelCard) modelCard.textContent = (saved && saved.model) || '—';
    if (provCard) provCard.textContent = (saved && saved.provider) || '—';
  }

  function kbApplySavedConfigToDropdowns(cfg) {
    if (!cfg) return;
    var saved = kbSavedConfig;
    function shouldOverwrite(currentValue, savedValue) {
      if (saved == null) return true;
      return String(currentValue == null ? '' : currentValue) === String(savedValue == null ? '' : savedValue);
    }
    var provSel = document.getElementById('kbConfigProvider');
    if (provSel && cfg.provider && shouldOverwrite(provSel.value, saved && saved.provider)) provSel.value = cfg.provider;
    var ollamaUrl = document.getElementById('kbConfigOllamaUrl');
    if (ollamaUrl && cfg.ollamaUrl && shouldOverwrite(ollamaUrl.value, saved && saved.ollamaUrl)) ollamaUrl.value = cfg.ollamaUrl;
    var orKey = document.getElementById('kbConfigOpenRouterKey');
    if (orKey && cfg.openRouterApiKey && shouldOverwrite(orKey.value, saved && saved.openRouterApiKey)) orKey.value = cfg.openRouterApiKey;
    var modelSel = document.getElementById('kbConfigModel');
    if (modelSel && cfg.model && shouldOverwrite(modelSel.value, saved && saved.model)) modelSel.value = cfg.model;
    kbMarkPendingChange();
  }

  function kbMarkPendingChange() {
    var modelSel = document.getElementById('kbConfigModel');
    var provSel = document.getElementById('kbConfigProvider');
    var urlInput = document.getElementById('kbConfigOllamaUrl');
    var orInput = document.getElementById('kbConfigOpenRouterKey');
    var saveBtn = document.getElementById('kbConfigSave');
    var badge = document.getElementById('kbConfigPendingBadge');
    if (!modelSel || !badge) return;
    var saved = kbSavedConfig;
    if (!saved) { badge.classList.add('d-none'); return; }
    var dirty = false;
    if (modelSel.value !== (saved.model || '')) dirty = true;
    if (provSel && provSel.value !== (saved.provider || '')) dirty = true;
    if (urlInput && urlInput.value !== (saved.ollamaUrl || '')) dirty = true;
    if (orInput && orInput.value !== (saved.openRouterApiKey || '')) dirty = true;
    badge.classList.toggle('d-none', !dirty);
    if (saveBtn) saveBtn.classList.toggle('btn-warning', dirty);
  }

  function kbLoadOverview() {
    Promise.all([
      fetch('/api/knowledge/bases').then(function (r) { return r.json(); }),
      fetch('/api/knowledge/sources').then(function (r) { return r.json(); }),
      fetch('/api/knowledge/config').then(function (r) { return r.json(); })
    ]).then(function (results) {
      document.getElementById('kbMetricBases').textContent = results[0].length;
      document.getElementById('kbMetricSources').textContent = results[1].length;
      var modelCard = document.getElementById('kbMetricModel');
      var provCard = document.getElementById('kbMetricProvider');
      var overviewConfig = results[2];
      if (modelCard) modelCard.textContent = (overviewConfig && overviewConfig.model) || '—';
      if (provCard) provCard.textContent = (overviewConfig && overviewConfig.provider) || '—';
      kbMarkPendingChange();
    }).catch(function (e) { kbNotify('danger', e.message); });
  }

  function kbLoadSources() {
    fetch('/api/knowledge/sources').then(function (r) { return r.json(); })
      .then(function (sources) {
        document.getElementById('kbSourceCount').textContent = sources.length;
        var html = '';
        if (!sources.length) {
          html = '<tr><td colspan="7" class="text-center text-muted py-4">' +
                 esc(t('knowledge.sources.empty', 'Noch keine Quellen vorhanden.')) + '</td></tr>';
        } else {
          sources.forEach(function (s) {

            var canSync = s.type !== 'MANUAL' && s.type !== 'FILE';
            var syncBtn = canSync
              ? '<button class="btn btn-sm btn-outline-secondary" onclick="kbSyncSource(' + s.id + ')"><i class="bi bi-arrow-clockwise"></i></button> '
              : '';
            var size = kbFormatBytes(s.totalBytes);
            var sizeCell = '<td class="kb-source-size">' +
              '<span class="kb-source-size-badge"' +
                (size.tooltip ? ' title="' + esc(size.tooltip) + '"' : '') + '>' +
              esc(size.display) +
              '</span></td>';
            var updatedText = kbFormatTimestamp(s.updatedAt);
            var updatedCell = '<td class="text-muted small kb-source-updated"' +
              (s.updatedAt ? ' title="' + esc(s.updatedAt) + '"' : '') + '>' +
              esc(updatedText || '\u2014') +
              '</td>';
            html += '<tr data-source-id="' + esc(String(s.id)) + '">' +
              '<td class="kb-source-name">' + esc(s.name) + '</td>' +
              '<td><span class="badge bg-light text-dark border">' + esc(s.type) + '</span></td>' +
              '<td class="text-muted small">#' + esc(s.knowledgeBaseId) + '</td>' +
              sizeCell +
              '<td><span class="kb-source-badge kb-source-badge-' + esc(s.status) + '">' + esc(s.status) + '</span></td>' +
              updatedCell +
              '<td class="text-end">' +
                syncBtn +
                '<button class="btn btn-sm btn-outline-danger" onclick="kbDeleteSource(' + s.id + ')"><i class="bi bi-trash"></i></button>' +
              '</td>' +
            '</tr>';
          });
        }
        document.getElementById('kbSourceRows').innerHTML = html;
      })
      .catch(function (e) { kbNotify('danger', e.message); });
  }

  function kbLoadConfig() {
    fetch('/api/knowledge/config').then(function (r) { return r.json(); })
      .then(function (cfg) {
        kbApplySavedConfigToDropdowns(cfg);
        kbSavedConfig = cfg;
        var refreshBtn = document.getElementById('kbConfigModelRefresh');
        if (refreshBtn && refreshBtn.dataset.bound !== '1') {
          refreshBtn.dataset.bound = '1';
          refreshBtn.addEventListener('click', kbRefreshModelList);
        }
        kbOnProviderChange();
        kbSyncOverviewModel();

        kbMarkPendingChange();

        ['kbConfigModel', 'kbConfigProvider', 'kbConfigOllamaUrl',
         'kbConfigOpenRouterKey'].forEach(function (id) {
          var el = document.getElementById(id);
          if (el && el.dataset.kbBound !== '1') {
            el.dataset.kbBound = '1';
            el.addEventListener('input', kbMarkPendingChange);
            el.addEventListener('change', kbMarkPendingChange);
          }
        });

        ['kbConfigOllamaUrl', 'kbConfigOpenRouterKey'].forEach(function (id) {
          var el = document.getElementById(id);
          if (el && el.dataset.kbModelsBound !== '1') {
            el.dataset.kbModelsBound = '1';
            var timer = null;
            var trigger = function () {
              if (timer) clearTimeout(timer);
              timer = setTimeout(function () {
                timer = null;
                kbRefreshModelList();
              }, 500);
            };
            el.addEventListener('input', trigger);
            el.addEventListener('change', trigger);
          }
        });
      })
      .catch(function (e) { kbNotify('danger', e.message); });
  }

  function kbTestConnection () {
    var status = document.getElementById('kbConfigStatus');
    status.innerHTML = '<div class="text-muted small"><i class="bi bi-hourglass-split me-1"></i>Teste…</div>';
    fetch('/api/knowledge/config/test', { method: 'POST' })
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (d.ok) {
          status.innerHTML = '<div class="alert alert-success d-flex align-items-center gap-2">' +
            '<i class="bi bi-check-circle-fill"></i>' +
            '<div>' + esc(t('knowledge.config.test_ok', 'Verbindung erfolgreich.')) +
              ' (Provider ' + esc(d.provider) + ', Modell ' + esc(d.model) + ', Dimension ' + esc(d.dimension) + ')</div>' +
            '</div>';
        } else {
          status.innerHTML = '<div class="alert alert-danger d-flex align-items-center gap-2">' +
            '<i class="bi bi-x-circle-fill"></i>' +
            '<div>' + esc(d.errorMessage || t('knowledge.config.test_failed', 'Verbindung fehlgeschlagen.')) + '</div>' +
            '</div>';
        }
      })
      .catch(function (e) {
        status.innerHTML = '<div class="alert alert-danger d-flex align-items-center gap-2">' +
          '<i class="bi bi-x-circle-fill"></i><div>' + esc(e.message) + '</div></div>';
      });
  }

  function kbSaveConfig () {
    var body = {
      id: 1,
      enabled: true,
      provider: document.getElementById('kbConfigProvider').value,
      model: document.getElementById('kbConfigModel').value,
      ollamaUrl: document.getElementById('kbConfigOllamaUrl').value,
      openAiApiKey: document.getElementById('kbConfigOpenAiKey').value || null,
      openRouterApiKey: document.getElementById('kbConfigOpenRouterKey').value || null,
      chunkSize: 1600,
      chunkOverlap: 200,
      topK: 8,
      minimumBm25Score: 0.0,
      maximumContextCharacters: 12000,
      autoIndexReview: true,
      autoIndexSprintReview: true,
      autoIndexTicketCreation: true,
      autoIndexTicketRefinement: true,
      autoIndexTicketSummary: true,
    };
    fetch('/api/knowledge/config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (t) { throw new Error(t); });
        return r.json();
      })
      .then(function (cfg) {

        kbSavedConfig = cfg;
        kbApplySavedConfigToDropdowns(cfg);
        kbMarkPendingChange();
        kbSyncOverviewModel();
        kbNotify('success', t('knowledge.config.saved', 'Konfiguration gespeichert.'));
      })
      .catch(function (e) { kbNotify('danger', e.message); });
  }

  var kbInflightSourceFetches = {};
