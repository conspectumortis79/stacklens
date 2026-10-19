'use strict';

  function kbInitReset () {
    var box = document.getElementById('kbResetConfirmBox');
    var btn = document.getElementById('kbResetBtn');
    if (box) box.style.display = 'block';
    if (btn) btn.style.display = 'none';
  }

  function kbCancelReset () {
    var box = document.getElementById('kbResetConfirmBox');
    var btn = document.getElementById('kbResetBtn');
    if (box) box.style.display = 'none';
    if (btn) btn.style.display = 'inline-flex';
  }

  function kbConfirmReset () {
    var btn = document.getElementById('kbResetConfirmBtn');
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = '<i class="bi bi-arrow-clockwise spin me-2"></i>' +
        esc(t('knowledge.danger.running', 'Löschung läuft…'));
    }
    fetch('/api/knowledge/reset', { method: 'POST' })
      .then(function (r) {
        if (btn) { btn.disabled = false; }
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function () {
        kbNotify('success', t('knowledge.danger.done', 'Wissensdatenbank gelöscht.'));
        kbCancelReset();
        kbInvalidateBasesCache();
        kbLoadOverview();
        kbLoadBases();
        kbLoadSources();
      })
      .catch(function (e) { kbNotify('danger', e.message); });
  }

  function kbReindexAll () {
    var result = document.getElementById('kbReindexResult');
    if (result) {

      result.innerHTML = '<div class="text-muted small" data-kb-reindex-started="' + Date.now() + '"><i class="bi bi-arrow-clockwise spin me-1"></i>' +
        esc(t('knowledge.reindex.running', 'Reindexierung läuft…')) + ' <span class="kb-reindex-elapsed"></span></div>';
      var startedAt = Date.now();
      var elapsedEl = result.querySelector('.kb-reindex-elapsed');
      var elapsedTimer = setInterval(function () {
        if (!elapsedEl || !result.isConnected) {
          clearInterval(elapsedTimer);
          return;
        }
        var seconds = Math.floor((Date.now() - startedAt) / 1000);
        elapsedEl.textContent = '(' + seconds + ' s)';
      }, 1000);

      result.__kbReindexTimer = elapsedTimer;
    }
    fetch('/api/knowledge/reindex-all', { method: 'POST' })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function (data) {
        var msg = t('knowledge.reindex.done', 'Reindexierung abgeschlossen: {0} Wissensdatenbanken, {1} Chunks.')
          .replace('{0}', data.baseCount)
          .replace('{1}', data.totalChunks);
        kbInvalidateBasesCache();
        if (result) {
          if (result.__kbReindexTimer) clearInterval(result.__kbReindexTimer);
          var seconds = Math.floor((Date.now() - (parseInt(result.getAttribute('data-kb-reindex-started'), 10) || Date.now())) / 1000);
          result.innerHTML = '<div class="alert alert-success d-flex align-items-center gap-2">' +
            '<i class="bi bi-check-circle-fill"></i><div>' + esc(msg) + ' <span class="text-muted small">(' + seconds + ' s)</span></div></div>';
        }
      })
      .catch(function (e) {
        if (result) {
          if (result.__kbReindexTimer) clearInterval(result.__kbReindexTimer);
          result.innerHTML = '<div class="alert alert-danger d-flex align-items-center gap-2">' +
            '<i class="bi bi-x-circle-fill"></i><div>' + esc(t('knowledge.reindex.failed', 'Reindexierung fehlgeschlagen: {0}').replace('{0}', e.message)) + '</div></div>';
        }
      });
  }

  function kbShowReindexHintOnce() {
    try {
      if (localStorage.getItem('stacklens.rag.knn-reindex-hint-shown') === '1') return;
    } catch (_) {
      return;
    }
    setTimeout(function () {
      showKbToast(
        t(
          'knowledge.reindex.hint_after_update',
          'Lucene unterstützt jetzt semantische Suche (KNN). Führe im Tab "Wartung & Gefahrenzone" einmalig "Alle Wissensdatenbanken reindexieren" aus. Reine BM25-Suche funktioniert auch ohne Reindex.',
        ),
      );
      try { localStorage.setItem('stacklens.rag.knn-reindex-hint-shown', '1'); } catch (_) {  }
    }, 1500);
  }
