'use strict';

  function kbOpenExport () {

    var input = document.getElementById('kbExportPasswordInput');
    if (input) {
      input.value = '';
      input.type = 'password';
    }
    var show = document.getElementById('kbExportShowPassword');
    if (show) show.checked = false;
    var modalEl = document.getElementById('kbExportPasswordModal');
    if (!modalEl) {

      var password = window.prompt(t('knowledge.export.password', 'Passwort für die Verschlüsselung:'));
      if (password) kbTriggerExport(password);
      return;
    }
    var modal = bootstrap.Modal.getOrCreateInstance(modalEl);
    modal.show();
    if (show && input && !show.dataset.kbWired) {
      show.addEventListener('change', function () {
        input.type = show.checked ? 'text' : 'password';
      });
      show.dataset.kbWired = 'true';
    }
    modalEl.addEventListener('shown.bs.modal', function onShown () {
      modalEl.removeEventListener('shown.bs.modal', onShown);
      if (input) input.focus();
    }, { once: true });
  }

  function kbConfirmExportPassword () {
    var input = document.getElementById('kbExportPasswordInput');
    var password = input ? input.value : '';
    if (!password || password.length < 1) {
      kbNotify('warning', t('knowledge.export.password_required', 'Bitte ein Passwort eingeben.'));
      return;
    }
    var modalEl = document.getElementById('kbExportPasswordModal');
    var modal = modalEl ? bootstrap.Modal.getInstance(modalEl) : null;
    if (modal) modal.hide();
    kbTriggerExport(password);
  }

  function kbTriggerExport (password) {

    fetch('/api/knowledge/export-all', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: password }),
    })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.blob();
      })
      .then(function (blob) {
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = 'stacklens-knowledge-' + new Date().toISOString().slice(0, 10) + '.slbundle';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        kbNotify('success', t('knowledge.export.done', 'Export bereit.'));
      })
      .catch(function (e) { kbNotify('danger', e.message); });
  }

  var KB_IMPORT_FILE_EXTENSIONS = ['slknowledge', 'slbundle'];

  var kbImportSelectedFile = null;

  function kbOpenImport () {
    __kiChatOpenDirBrowser(kbOpenImportOptions, {
      files: KB_IMPORT_FILE_EXTENSIONS,
      acceptHint: '.slknowledge / .slbundle',
      storageKey: 'stacklens.fb.last.knowledgeImport',
      titleKey: 'knowledge.import.title',
      titleFallback: '.slknowledge-Datei auswählen',
      fileIcon: 'bi-file-earmark-zip',
      load: kbLoadKnowledgeArchiveEntries,
    });
  }

  function kbLoadKnowledgeArchiveEntries (path) {
    return kbToolingFileEntries(path, KB_IMPORT_FILE_EXTENSIONS);
  }

  function kbOpenImportOptions (path) {
    if (!path) return;
    kbImportSelectedFile = path;
    var overlay = document.getElementById('kbImportOverlay');
    var alertArea = document.getElementById('kbImportAlert');
    if (alertArea) { alertArea.style.display = 'none'; alertArea.innerHTML = ''; }
    var nameEl = document.getElementById('kbImportSelectedFileName');
    if (nameEl) nameEl.textContent = path;
    if (overlay) overlay.style.display = 'flex';
    document.body.style.overflow = 'hidden';
    document.querySelectorAll('.modal-backdrop').forEach(function (bd) { bd.remove(); });
    document.body.classList.remove('modal-open');
    var password = document.getElementById('kbImportPassword');
    if (password) {
      password.value = '';
      password.focus();
    }
  }

  function kbCloseImportOverlay () {
    var overlay = document.getElementById('kbImportOverlay');
    if (overlay) overlay.style.display = 'none';
    document.body.style.overflow = '';
  }

  function kbConfirmImport () {
    if (!kbImportSelectedFile) return;
    var password = document.getElementById('kbImportPassword').value;
    if (!password) {
      var alert = document.getElementById('kbImportAlert');
      alert.className = 'alert alert-danger py-2 mb-0 mt-3 kb-overlay-alert';
      alert.style.display = 'block';
      alert.innerHTML = '<i class="bi bi-exclamation-circle me-1"></i>' +
        esc(t('knowledge.import.password_required', 'Bitte das Passwort eingeben.'));
      return;
    }
    var mode = 'NEW_COPY';
    var radios = document.getElementsByName('kbImportMode');
    for (var i = 0; i < radios.length; i++) {
      if (radios[i].checked) { mode = radios[i].value; break; }
    }
    var btn = document.getElementById('kbImportConfirmBtn');
    btn.disabled = true;
    var origHtml = btn.innerHTML;
    btn.innerHTML = '<i class="bi bi-arrow-clockwise spin me-2"></i>' +
      esc(t('common.loading', 'Wird geladen…'));

    var lowerPath = String(kbImportSelectedFile || '').toLowerCase();
    var isBundle = lowerPath.endsWith('.slbundle');
    var endpoint = isBundle
      ? '/api/knowledge/import-bundle-local'
      : '/api/knowledge/import-local';
    fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        path: kbImportSelectedFile,
        password: password,
        mode: mode,
      }),
    })
      .then(function (r) {
        btn.disabled = false;
        btn.innerHTML = origHtml;
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function (result) {
        if (isBundle) {
          var docs = 0;
          var chunks = 0;
          if (Array.isArray(result)) {
            for (var i = 0; i < result.length; i++) {
              docs += result[i].documentsImported || 0;
              chunks += result[i].chunksImported || 0;
            }
          }
          kbNotify('success',
            t('knowledge.import.done_bundle', 'Bundle-Import abgeschlossen.') + ' ' +
              result.length + ' Wissensdatenbanken, ' +
              docs + ' Dokumente, ' + chunks + ' Chunks');
        } else {
          kbNotify('success',
            t('knowledge.import.done', 'Import abgeschlossen.') + ' ' +
              result.documentsImported + ' Dokumente, ' +
              result.chunksImported + ' Chunks');
        }
        kbCloseImportOverlay();
        kbInvalidateBasesCache();
        kbLoadBases();
        kbLoadOverview();
        kbLoadSources();
        kbPopulateBases('#kbManualBase');
        kbPopulateBases('#kbFileBase');
        kbPopulateBases('#kbConfluenceBase');
        kbPopulateBases('#kbJiraBase');
      })
      .catch(function (e) { kbNotify('danger', e.message); });
  }
