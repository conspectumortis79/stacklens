'use strict';

function esc(s) {
    return window.slHelpers.esc(s);
  }

  function t(key, fallback) {
    return window.slHelpers.t(key, fallback);
  }

  function kbFormatBytes(bytes) {
    if (bytes == null) return { display: '\u2014', tooltip: '' };
    var n = Number(bytes);
    if (!isFinite(n) || n < 0) return { display: '\u2014', tooltip: '' };
    var tooltip = n.toLocaleString('en-US').replace(/,/g, '\u202F') + ' Bytes';
    if (n < 1024) {
      return { display: n + ' B', tooltip: tooltip };
    }
    var kb = n / 1024;
    if (kb < 1024) {
      var s = kb < 10 ? kb.toFixed(1) : kb.toFixed(0);
      return { display: s.replace(/\.0$/, '') + ' KB', tooltip: tooltip };
    }
    var mb = kb / 1024;
    if (mb < 1024) {
      var s = mb < 10 ? mb.toFixed(1) : mb.toFixed(0);
      return { display: s.replace(/\.0$/, '') + ' MB', tooltip: tooltip };
    }
    var gb = mb / 1024;
    var s = gb < 10 ? gb.toFixed(1) : gb.toFixed(0);
    return { display: s.replace(/\.0$/, '') + ' GB', tooltip: tooltip };
  }

  function kbFormatTimestamp(value) {
    if (value == null || value === '') return '';
    var s = String(value);
    if (s.length >= 19) s = s.substring(0, 19);
    return s.replace('T', ' ');
  }

  function kbNotify(level, message, duration) {
    var container = document.getElementById('kbToastContainer');
    if (!container) {
      container = document.createElement('div');
      container.id = 'kbToastContainer';
      container.className = 'kb-toast-container';
      document.body.appendChild(container);
    }
    var cls = 'alert-' + (level || 'info');
    var icon = level === 'success' ? 'bi-check-circle-fill'
      : level === 'danger' ? 'bi-x-circle-fill'
      : level === 'warning' ? 'bi-exclamation-triangle-fill'
      : 'bi-info-circle-fill';

    var toast = document.createElement('div');
    toast.className = 'kb-toast alert ' + cls;
    toast.setAttribute('role', 'alert');
    toast.innerHTML =
      '<i class="bi ' + icon + ' kb-toast-icon"></i>' +
      '<div class="kb-toast-body">' + esc(message) + '</div>' +
      '<button type="button" class="kb-toast-close" aria-label="Schließen">×</button>';

    var closeBtn = toast.querySelector('.kb-toast-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', function () { dismissKbToast(toast); });
    }

    container.appendChild(toast);

    requestAnimationFrame(function () { toast.classList.add('kb-toast--visible'); });

    var ms = (typeof duration === 'number' && duration >= 0) ? duration : 5000;
    if (ms > 0) {
      setTimeout(function () { dismissKbToast(toast); }, ms);
    }
  }

  function dismissKbToast(toast) {
    if (!toast || !toast.parentNode) return;
    toast.classList.remove('kb-toast--visible');

    setTimeout(function () {
      if (toast.parentNode) toast.parentNode.removeChild(toast);
    }, 300);
  }

  function kbBeginImportProgress (modalEl, label, phase, etaSeconds) {
    if (!modalEl) return null;
    var header = modalEl.querySelector('.modal-header');
    if (!header) return null;
    var interactive = modalEl.querySelectorAll('input, select, textarea, button');
    for (var i = 0; i < interactive.length; i++) {
      var el = interactive[i];
      el.setAttribute('data-kb-imp-prev-disabled', el.disabled ? '1' : '0');
      el.disabled = true;
    }
    var band = document.createElement('div');
    band.className = 'kb-import-progress-band';
    band.setAttribute('role', 'status');
    band.setAttribute('aria-live', 'polite');
    var meta = document.createElement('div');
    meta.className = 'kb-import-progress-meta';
    var labelEl = document.createElement('div');
    labelEl.className = 'kb-import-progress-label';
    labelEl.innerHTML =
      '<i class="bi bi-arrow-clockwise kb-import-progress-icon"></i>' + esc(label) +
      (phase ? '<span class="kb-import-progress-phase"> · ' + esc(phase) + '</span>' : '');
    meta.appendChild(labelEl);
    if (typeof etaSeconds === 'number' && etaSeconds > 0) {
      var etaEl = document.createElement('span');
      etaEl.className = 'kb-import-progress-eta';
      etaEl.textContent = t('knowledge.import.progress.eta_seconds', 'ca. noch {0} Sek.').replace('{0}', String(etaSeconds));
      meta.appendChild(etaEl);
    }
    band.appendChild(meta);
    var bar = document.createElement('div');
    bar.className = 'kb-import-progress-bar';
    band.appendChild(bar);
    if (header.nextSibling) header.parentNode.insertBefore(band, header.nextSibling);
    else header.parentNode.appendChild(band);
    modalEl.classList.add('kb-import-form-busy');
    return band;
  }

  function kbEndImportProgress (modalEl) {
    if (!modalEl) return;
    modalEl.querySelectorAll('.kb-import-progress-band').forEach(function (b) {
      if (b.parentNode) b.parentNode.removeChild(b);
    });
    modalEl.querySelectorAll('[data-kb-imp-prev-disabled]').forEach(function (el) {
      el.disabled = el.getAttribute('data-kb-imp-prev-disabled') === '1';
      el.removeAttribute('data-kb-imp-prev-disabled');
    });
    modalEl.classList.remove('kb-import-form-busy');
  }

  function showKbToast(message) {
    kbNotify('info', message, 12000);
  }
