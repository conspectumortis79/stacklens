(function () {
  'use strict';

  function t(key, fallback) {
    try {
      if (window.i18n && typeof window.i18n.t === 'function') {
        var v = window.i18n.t(key);
        if (v && v !== key) return v;
      }
    } catch (_) { }
    return fallback != null ? fallback : key;
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function parseBody(text) {
    if (!text) return null;
    try {
      return JSON.parse(text);
    } catch (_) {
      return null;
    }
  }

  function statusLabel(response) {
    return t('common.error', 'Fehler') + ' (' + response.status + ')';
  }

  function jsonArray(response) {
    return response.text().then(function (body) {
      var data = parseBody(body);
      if (Array.isArray(data) && response.ok) return data;
      if (data && typeof data === 'object' && data.error) throw new Error(String(data.error));
      throw new Error(statusLabel(response));
    });
  }

  function fetchJsonArray(url, options) {
    return fetch(url, options).then(jsonArray);
  }

  function browseEntries(url) {
    return fetch(url).then(function (response) {
      return response.json().then(function (data) {
        if (!response.ok || data.error) throw new Error(data.error || ('HTTP ' + response.status));
        var items = data.items || [];
        return {
          current: data.currentPath || '',
          parent: data.parentPath || null,
          directories: items.filter(function (item) { return item.type === 'directory'; }),
          files: items.filter(function (item) { return item.type !== 'directory'; }),
        };
      });
    });
  }

  function directoryEntries(url) {
    return browseEntries(url).then(function (page) {
      return { current: page.current, parent: page.parent, directories: page.directories, files: [] };
    });
  }

  function browseDirectories(endpoint, path) {
    return directoryEntries(endpoint + '?directoriesOnly=true' + (path ? '&path=' + encodeURIComponent(path) : ''));
  }

  function browseDirectoriesByPath(endpoint, path) {
    return directoryEntries(endpoint + (path ? '?path=' + encodeURIComponent(path) : ''));
  }

  async function testConnection(config) {
    var cfg = config || {};
    var idInput = document.getElementById(cfg.idElement);
    var resultDiv = document.getElementById(cfg.resultElement);
    var btn = document.getElementById(cfg.buttonElement);
    var extra = cfg.successElement ? document.getElementById(cfg.successElement) : null;
    var id = idInput ? (+idInput.value || null) : null;
    if (!id) {
      resultDiv.style.display = '';
      resultDiv.innerHTML = '<div class="alert alert-warning py-2 mb-0">' + t('config.save_first', 'Bitte zuerst speichern') + '</div>';
      return;
    }
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>' + t('config.testing', 'Teste…');
    resultDiv.style.display = 'none';
    if (extra) extra.style.display = 'none';
    try {
      var res = await fetch(cfg.endpoint.replace('{id}', id), { method: 'POST' });
      var data = await res.json();
      resultDiv.style.display = '';
      if (data.success) {
        resultDiv.innerHTML = '<div class="alert alert-success py-2 mb-0"><i class="bi bi-check-circle me-1"></i>' + esc(data.message) + '</div>';
        if (extra) extra.style.display = '';
      } else {
        resultDiv.innerHTML = '<div class="alert alert-danger py-2 mb-0"><i class="bi bi-x-circle me-1"></i><span style="white-space:pre-wrap">' + esc(data.message) + '</span></div>';
      }
    } catch (e) {
      resultDiv.style.display = '';
      var icon = cfg.networkErrorIcon === false ? '' : '<i class="bi bi-x-circle me-1"></i>';
      resultDiv.innerHTML = '<div class="alert alert-danger py-2 mb-0">' + icon + t(cfg.networkErrorKey || 'common.network_error', 'Netzwerkfehler: ') + esc(e.message) + '</div>';
    } finally {
      btn.disabled = false;
      btn.innerHTML = '<i class="bi bi-plug me-1"></i>' + t('config.test_connection', 'Verbindung testen');
    }
  }

  function setPref(key, value) {
    if (window.desktopApp && window.desktopApp.setPreference) {
      try { window.desktopApp.setPreference(key, value); } catch (e) {}
    }
    try { localStorage.setItem(key, value); } catch (e) {}
  }

  function getPref(key) {
    if (window.desktopApp && window.desktopApp.getPreference) {
      try {
        var v = window.desktopApp.getPreference(key);
        if (v !== null && v !== undefined && v !== '') return v;
      } catch (e) {}
    }
    try { return localStorage.getItem(key) || ''; } catch (e) { return ''; }
  }

  window.slHelpers = {
    t: t,
    esc: esc,
    jsonArray: jsonArray,
    fetchJsonArray: fetchJsonArray,
    browseEntries: browseEntries,
    directoryEntries: directoryEntries,
    browseDirectories: browseDirectories,
    browseDirectoriesByPath: browseDirectoriesByPath,
    testConnection: testConnection,
    setPref: setPref,
    getPref: getPref,
  };
})();
