(function () {
  'use strict';

  function _hT(key, fallback) {
    return (window.i18n && typeof window.i18n.t === 'function')
      ? window.i18n.t(key)
      : fallback;
  }

  function _showAlert(message, type, fallback) {
    if (typeof window.showAlert === 'function') {
      window.showAlert(message, type || 'info');
      return;
    }
    var msg = (window.i18n && window.i18n.t)
      ? window.i18n.t(message) || fallback
      : (fallback || message);
  }

  function probeConn(kind, id, btn) {
    if (!kind || !id) return Promise.resolve();
    var button = btn || null;
    var originalHtml = button ? button.innerHTML : null;
    if (button) {
      button.disabled = true;
      button.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span>';
    }
    return fetch('/api/health/connections/' + encodeURIComponent(kind) + '/' + id + '/probe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('HTTP ' + res.status);
        }
        return res.json();
      })
      .then(function (snap) {
        if (snap && snap.healthy) {
          _showAlert(_hT('health.probe_ok', 'OK'), 'success');
        } else {
          var msg = (snap && snap.lastErrorMessage) || _hT('health.probe_failed', 'failed');
          _showAlert(msg, 'warning');
        }
        try { window.location.reload(); } catch (_) {  }
        return snap;
      })
      .catch(function (err) {
        _showAlert(_hT('health.probe_failed', 'Probe failed: ') + err.message, 'danger');
        if (button) {
          button.disabled = false;
          button.innerHTML = originalHtml || '<i class="bi bi-broadcast"></i>';
        }
      });
  }

  window.probeConn = probeConn;
})();
