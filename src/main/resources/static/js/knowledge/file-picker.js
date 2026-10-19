'use strict';

  function kbBaseName (path) {
    var parts = String(path).replace(/\\/g, '/').split('/').filter(Boolean);
    return parts.length ? parts[parts.length - 1] : String(path);
  }

  function kbBrowseEntries (result) {
    var data = result.data || {};
    if (!result.ok || data.error) {
      throw new Error(data.error || result.message || ('HTTP ' + result.status));
    }
    var items = data.items || [];
    return {
      current: data.currentPath || '',
      parent: data.parentPath || null,
      directories: items.filter(function (item) { return item.type === 'directory'; }),
      files: items.filter(function (item) { return item.type !== 'directory'; }),
    };
  }

  function kbToolingFileEntries (path, extensions) {
    return SLBrowseFiles({
      path: path,
      fileExtensions: extensions.join(','),
      silent: true,
    }).then(kbBrowseEntries);
  }

  function kbLoadKnowledgeFileEntries (path) {
    return kbToolingFileEntries(path, KB_FILE_EXTENSIONS);
  }

  function kbApplyPickedFilePath (path) {
    var pathInput = document.getElementById('kbFileCurrentPath');
    if (pathInput) pathInput.value = path;
    var nameEl = document.getElementById('kbFileSelectedName');
    if (nameEl) nameEl.textContent = kbBaseName(path);
  }

  function kbOpenFileForm () {
    var formModalEl = document.getElementById('kbAddFileModal');
    if (!formModalEl || formModalEl.classList.contains('show')) return undefined;
    return kbPopulateBases('#kbFileBase').then(function () {
      new bootstrap.Modal(formModalEl).show();
    }).catch(function (e) {
      kbNotify('danger', (e && e.message) ? e.message : String(e));
    });
  }

  function kbApplyPickedSourceFile (path) {
    if (!path) return undefined;
    kbApplyPickedFilePath(path);
    return kbOpenFileForm();
  }

  function kbOpenFilePicker () {
    __kiChatOpenDirBrowser(kbApplyPickedSourceFile, {
      files: KB_FILE_EXTENSIONS,
      acceptHint: 'PDF, DOC, DOCX, TXT, MD',
      storageKey: 'stacklens.fb.last.knowledgeFile',
      titleKey: 'knowledge.add.file_picker.title',
      titleFallback: 'Datei auswählen',
      fileIcon: 'bi-file-earmark-text',
      awaitSelection: true,
      load: kbLoadKnowledgeFileEntries,
    });
  }
