(function () {
  'use strict';

  var _t = {};
  var _cache = {};
  var _lang = 'en';
  var _translationsLoaded = false;
  var _readyCallbacks = [];

  function t(key) {
    if (!key || typeof key !== 'string') return '';
    var parts = key.split('.');
    var node = _t;
    for (var i = 0; i < parts.length; i++) {
      if (node == null || typeof node !== 'object') return key;
      node = node[parts[i]];
    }
    var raw = typeof node === 'string' ? node : key;
    if (arguments.length > 1) {
      for (var a = 1; a < arguments.length; a++) {
        var v = arguments[a];
        raw = raw.replace(new RegExp('\\{' + (a - 1) + '\\}', 'g'), String(v));
      }
    }
    return raw;
  }

  function tList(key) {
    if (!key || typeof key !== 'string') return [];
    var parts = key.split('.');
    var node = _t;
    for (var i = 0; i < parts.length; i++) {
      if (node == null || typeof node !== 'object') return [];
      node = node[parts[i]];
    }
    return Array.isArray(node) ? node : [];
  }

  function _replaceTextNodes(el, text) {
    var replaced = false;
    for (var i = el.childNodes.length - 1; i >= 0; i--) {
      var node = el.childNodes[i];
      if (node.nodeType === 3) {
        if (!replaced && node.textContent.trim() !== '') {
          node.textContent = text;
          replaced = true;
        } else if (replaced && node.textContent.trim() !== '') {
          el.removeChild(node);
        }
      }
    }
    if (!replaced) {
      el.appendChild(document.createTextNode(text));
    }
  }

  function applyTranslations(root) {
    root = root || document;

    var nodes = root.querySelectorAll('[data-i18n],[data-i18n-placeholder],[data-i18n-title],[data-i18n-aria-label],[data-i18n-tooltip]');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.hasAttribute('data-i18n')) {
        var key = el.getAttribute('data-i18n');
        var val = t(key);
        if (val === key) continue;
        if (el.querySelector('i, svg, img')) {
          _replaceTextNodes(el, val);
        } else if (el.textContent !== val) {
          el.textContent = val;
          void el.offsetHeight;
        }
      } else if (el.hasAttribute('data-i18n-placeholder')) {
        var phKey = el.getAttribute('data-i18n-placeholder');
        var phVal = t(phKey);
        if (phVal !== phKey) el.placeholder = phVal;
      } else if (el.hasAttribute('data-i18n-title')) {
        var tiKey = el.getAttribute('data-i18n-title');
        var tiVal = t(tiKey);
        if (tiVal !== tiKey) el.title = tiVal;
      } else if (el.hasAttribute('data-i18n-aria-label')) {
        var alKey = el.getAttribute('data-i18n-aria-label');
        var alVal = t(alKey);
        if (alVal !== alKey) el.setAttribute('aria-label', alVal);
      } else if (el.hasAttribute('data-i18n-tooltip')) {
        var ttKey = el.getAttribute('data-i18n-tooltip');
        var ttVal = t(ttKey);
        if (ttVal !== ttKey) el.setAttribute('data-tooltip', ttVal);
      }
    }

    document.documentElement.lang = _lang;
    _updateLangButtons();
  }

  function _updateLangButtons() {
    document.querySelectorAll('[data-lang-btn]').forEach(function (btn) {
      var isActive = btn.getAttribute('data-lang-btn') === _lang;
      btn.className = btn.className
        .replace(/\bbtn-primary\b/, '')
        .replace(/\bbtn-outline-secondary\b/, '')
        .trim();
      btn.className += isActive ? ' btn-primary' : ' btn-outline-secondary';
    });
  }

  function loadLanguage(lang) {
    var cached = _cache[lang];
    if (cached) {
      _t = cached;
      _lang = lang;
      applyTranslations();
      if (!_translationsLoaded) {
        _translationsLoaded = true;
        var cbs = _readyCallbacks.splice(0);
        for (var i = 0; i < cbs.length; i++) { cbs[i](); }
      }
      return Promise.resolve(cached);
    }
    return fetch('/i18n2/' + lang + '.json')
      .then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then(function (data) {
        _cache[lang] = data;
        _t = data;
        _lang = lang;
        applyTranslations();
        if (!_translationsLoaded) {
          _translationsLoaded = true;
          var cbs = _readyCallbacks.splice(0);
          for (var i = 0; i < cbs.length; i++) { cbs[i](); }
        }
      });
  }

  function setLanguage(lang) {
    if (lang !== 'en' && lang !== 'de') return;
    fetch('/api/config/language', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ language: lang })
    })
      .then(function () { return loadLanguage(lang); })
      .catch(function (e) { console.error('[i18n] setLanguage error', e); });
  }

  function init() {
    fetch('/api/config/language')
      .then(function (r) { return r.json(); })
      .then(function (d) { return loadLanguage(d.language || 'en'); })
      .catch(function () { return loadLanguage('en'); });
  }

  window.i18n = {
    t: t,
    tList: tList,
    setLanguage: setLanguage,
    apply: applyTranslations,
    getLang: function () { return _lang; },
    onReady: function (fn) {
      if (_translationsLoaded) { fn(); } else { _readyCallbacks.push(fn); }
    }
  };

  document.addEventListener('DOMContentLoaded', init);

  document.addEventListener('app:navigate', function () {
    applyTranslations();
  });
})();
