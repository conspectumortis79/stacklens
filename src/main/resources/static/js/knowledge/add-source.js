'use strict';

  function kbOpenAddSource (kind) {
    if (kind === 'manual') {
      kbPopulateBases('#kbManualBase').then(function () {
        var m = new bootstrap.Modal(document.getElementById('kbAddManualModal'));
        m.show();
      });
      return;
    }
    if (kind === 'file') {
      kbOpenFilePicker();
      return;
    }
    if (kind === 'confluence') {
      Promise.all([kbPopulateBases('#kbConfluenceBase'), kbLoadConnectionLists()]).then(function () {
        var confSel = document.getElementById('kbConfluenceConfig');
        if (confSel && !confSel.__kbConfChangeBound) {
          confSel.addEventListener('change', kbApplyConfluenceDemoAutoFill);
          confSel.__kbConfChangeBound = true;
        }
        kbApplyConfluenceDemoAutoFill();
        var m = new bootstrap.Modal(document.getElementById('kbAddConfluenceModal'));
        m.show();
      });
      return;
    }
    if (kind === 'jira') {
      Promise.all([kbPopulateBases('#kbJiraBase'), kbLoadConnectionLists()]).then(function () {
        var m = new bootstrap.Modal(document.getElementById('kbAddJiraModal'));
        m.show();
      });
      return;
    }
    if (kind === 'jira-sprint') {
      Promise.all([kbPopulateBases('#kbJiraSprintBase'), kbLoadConnectionLists()]).then(function () {
        var sprintSel = document.getElementById('kbJiraSprintConfig');
        if (sprintSel && !sprintSel.__kbSprintChangeBound) {
          sprintSel.addEventListener('change', kbApplyJiraSprintDemoAutoFill);
          sprintSel.__kbSprintChangeBound = true;
        }
        kbApplyJiraSprintDemoAutoFill();
        var m = new bootstrap.Modal(document.getElementById('kbAddJiraSprintModal'));
        m.show();
      });
    }
  }

  var KB_DEMO_SPRINT_NAME = 'Sprint Demo KB-Import';
  function kbApplyJiraSprintDemoAutoFill () {
    var sel = document.getElementById('kbJiraSprintConfig');
    var refInput = document.getElementById('kbJiraSprintRef');
    if (!sel || !refInput) return;
    var opt = sel.options[sel.selectedIndex];
    if (!opt) return;
    var cfg = KB_JIRA_CONFIG_LOOKUP[String(opt.value)] || {};
    var baseUrl = (cfg.baseUrl || opt.getAttribute('data-baseurl') || '').toLowerCase();
    var configName = (cfg.name || opt.text || '').toLowerCase();
    var isDemo = baseUrl.indexOf('localhost') !== -1 || baseUrl.indexOf('host.docker.internal') !== -1 || configName.indexOf('demo') !== -1;
    if (!isDemo) {
      if (refInput.value.trim() === KB_DEMO_SPRINT_NAME) refInput.value = '';
      return;
    }
    if (!refInput.value.trim()) {
      refInput.value = KB_DEMO_SPRINT_NAME;
    }
  }

  var KB_DEMO_CONFLUENCE_PATH = '/wiki/spaces/DEMO/pages/60001/Confluence+Demo+KB-Import';
  function kbApplyConfluenceDemoAutoFill () {
    var sel = document.getElementById('kbConfluenceConfig');
    var urlInput = document.getElementById('kbConfluenceUrl');
    if (!sel || !urlInput) return;
    var opt = sel.options[sel.selectedIndex];
    if (!opt) return;
    var cfg = KB_CONFLUENCE_CONFIG_LOOKUP[String(opt.value)] || {};
    var baseUrl = (cfg.baseUrl || opt.getAttribute('data-baseurl') || '').toLowerCase();
    var configName = (cfg.name || opt.text || '').toLowerCase();
    var isDemo = baseUrl.indexOf('localhost') !== -1 || baseUrl.indexOf('host.docker.internal') !== -1 || configName.indexOf('demo') !== -1;
    if (!isDemo) {
      var current = urlInput.value.trim();
      if (current === buildDemoUrl(baseUrl) || current === KB_DEMO_CONFLUENCE_PATH) {
        urlInput.value = '';
      }
      return;
    }
    if (!urlInput.value.trim()) {
      urlInput.value = buildDemoUrl(baseUrl);
    }
  }

  function buildDemoUrl (baseUrl) {
    if (!baseUrl) return KB_DEMO_CONFLUENCE_PATH;
    var trimmed = baseUrl.replace(/\/+$/, '');
    return trimmed + KB_DEMO_CONFLUENCE_PATH;
  }

  function kbSubmitManual () {
    var body = {
      knowledgeBaseId: parseInt(document.getElementById('kbManualBase').value, 10),
      name: document.getElementById('kbManualName').value,
      content: document.getElementById('kbManualText').value,
    };
    var modalEl = document.getElementById('kbAddManualModal');
    kbBeginImportProgress(
      modalEl,
      t('knowledge.import.progress.manual_label', 'Manuelle Notiz wird eingefügt'),
      t('knowledge.import.progress.manual_phase_vectorize', 'vektorisiere Text'),
      6
    );
    var ctrl = new AbortController();
    kbInflightSourceFetches['__import_manual__'] = ctrl;
    fetch('/api/knowledge/sources/manual', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: ctrl.signal,
    })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function () {
        kbNotify('success', t('knowledge.add.saved', 'Quelle gespeichert und vektorisiert.'));
        bootstrap.Modal.getInstance(modalEl).hide();
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
        delete kbInflightSourceFetches['__import_manual__'];
        kbEndImportProgress(modalEl);
      });
  }

  function kbSubmitFile () {
    var pathEl = document.getElementById('kbFileCurrentPath');
    var path = pathEl ? pathEl.value : '';
    if (!path) {
      kbNotify('warning', t('knowledge.file_picker.no_selection', 'Bitte eine Datei auswählen.'));
      return;
    }
    var modalEl = document.getElementById('kbAddFileModal');
    var knowledgeBaseId = document.getElementById('kbFileBase').value;
    kbBeginImportProgress(
      modalEl,
      t('knowledge.import.progress.file_label', 'Datei wird eingefügt'),
      t('knowledge.import.progress.file_phase_read', 'lese Datei'),
      8
    );
    var ctrl = new AbortController();
    kbInflightSourceFetches['__import_file__'] = ctrl;
    fetch('/api/knowledge/sources/file-local', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ knowledgeBaseId: parseInt(knowledgeBaseId, 10), path: path }),
      signal: ctrl.signal,
    })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function () {
        kbNotify('success', t('knowledge.add.saved', 'Quelle gespeichert und vektorisiert.'));
        bootstrap.Modal.getInstance(modalEl).hide();
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
        delete kbInflightSourceFetches['__import_file__'];
        kbEndImportProgress(modalEl);
      });
  }

  function kbSubmitConfluence () {
    var body = {
      knowledgeBaseId: parseInt(document.getElementById('kbConfluenceBase').value, 10),
      confluenceConfigId: parseInt(document.getElementById('kbConfluenceConfig').value, 10),
      rootPageUrl: document.getElementById('kbConfluenceUrl').value,
      includeChildren: document.getElementById('kbConfluenceChildren').checked,
      maxDepth: parseInt(document.getElementById('kbConfluenceDepth').value, 10) || 10,
    };
    var modalEl = document.getElementById('kbAddConfluenceModal');
    kbBeginImportProgress(
      modalEl,
      t('knowledge.import.progress.confluence_label', 'Confluence wird importiert'),
      t('knowledge.import.progress.confluence_phase_walk', 'lade Hierarchie'),
      14
    );
    var ctrl = new AbortController();
    kbInflightSourceFetches['__import_confluence__'] = ctrl;
    fetch('/api/knowledge/sources/confluence', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: ctrl.signal,
    })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function () {
        kbNotify('success', t('knowledge.add.saved', 'Quelle gespeichert und vektorisiert.'));
        bootstrap.Modal.getInstance(modalEl).hide();
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
        delete kbInflightSourceFetches['__import_confluence__'];
        kbEndImportProgress(modalEl);
      });
  }

  function kbSubmitJira () {
    var body = {
      knowledgeBaseId: parseInt(document.getElementById('kbJiraBase').value, 10),
      jiraConfigId: parseInt(document.getElementById('kbJiraConfig').value, 10),
      rootTicketKey: document.getElementById('kbJiraKey').value,
      includeChildren: document.getElementById('kbJiraChildren').checked,
      includeComments: document.getElementById('kbJiraComments').checked,
      maxDepth: parseInt(document.getElementById('kbJiraDepth').value, 10) || 10,
    };
    var modalEl = document.getElementById('kbAddJiraModal');
    kbBeginImportProgress(
      modalEl,
      t('knowledge.import.progress.jira_label', 'Jira-Ticket wird importiert'),
      t('knowledge.import.progress.jira_phase_walk', 'lade Hierarchie'),
      18
    );
    var ctrl = new AbortController();
    kbInflightSourceFetches['__import_jira__'] = ctrl;
    fetch('/api/knowledge/sources/jira', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: ctrl.signal,
    })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function () {
        kbNotify('success', t('knowledge.add.saved', 'Quelle gespeichert und vektorisiert.'));
        bootstrap.Modal.getInstance(modalEl).hide();
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
        delete kbInflightSourceFetches['__import_jira__'];
        kbEndImportProgress(modalEl);
      });
  }

  function kbSubmitJiraSprint () {
    var body = {
      knowledgeBaseId: parseInt(document.getElementById('kbJiraSprintBase').value, 10),
      jiraConfigId: parseInt(document.getElementById('kbJiraSprintConfig').value, 10),
      sprintReference: document.getElementById('kbJiraSprintRef').value,
      includeChildren: document.getElementById('kbJiraSprintChildren').checked,
      includeComments: document.getElementById('kbJiraSprintComments').checked,
      maxDepth: parseInt(document.getElementById('kbJiraSprintDepth').value, 10) || 1,
    };
    var modalEl = document.getElementById('kbAddJiraSprintModal');
    kbBeginImportProgress(
      modalEl,
      t('knowledge.import.progress.jira_sprint_label', 'Sprint wird importiert'),
      t('knowledge.import.progress.jira_sprint_phase_walk', 'lade Top-Level-Tickets'),
      22
    );
    var ctrl = new AbortController();
    kbInflightSourceFetches['__import_jira_sprint__'] = ctrl;
    fetch('/api/knowledge/sources/jira-sprint', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: ctrl.signal,
    })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function () {
        kbNotify('success', t('knowledge.add.saved', 'Quelle gespeichert und vektorisiert.'));
        bootstrap.Modal.getInstance(modalEl).hide();
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
        delete kbInflightSourceFetches['__import_jira_sprint__'];
        kbEndImportProgress(modalEl);
      });
  }
