window.SpaPageScripts = (function () {
  'use strict';

  var SCRIPT_TAG_RE = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  var SRC_ATTR_RE = /\bsrc\s*=\s*["']([^"']+)["']/i;
  var PAGE_SCRIPT_ATTR_RE = /\bdata-page-script\b/i;

  function collect(html) {
    var tags = [];
    if (!html) return tags;
    SCRIPT_TAG_RE.lastIndex = 0;
    var match = SCRIPT_TAG_RE.exec(html);
    while (match !== null) {
      var attrs = match[1] || '';
      var src = SRC_ATTR_RE.exec(attrs);
      if (src) {
        tags.push({ src: src[1], code: null, pageScript: PAGE_SCRIPT_ATTR_RE.test(attrs) });
      } else if ((match[2] || '').trim()) {
        tags.push({ src: null, code: match[2], pageScript: false });
      }
      match = SCRIPT_TAG_RE.exec(html);
    }
    return tags;
  }

  function detach(el) {
    if (el && el.parentNode) el.parentNode.removeChild(el);
  }

  function loadScript(src) {
    return new Promise(function (resolve) {
      var el = document.createElement('script');
      el.src = src;
      el.async = false;
      el.addEventListener('load', function () { detach(el); resolve(); });
      el.addEventListener('error', function () {
        if (typeof console !== 'undefined' && console.warn) {
          console.warn('SPA: Seitenskript nicht erreichbar: ' + src);
        }
        detach(el);
        resolve();
      });
      document.body.appendChild(el);
    });
  }

  function runScript(code) {
    var el = document.createElement('script');
    el.textContent = code;
    document.body.appendChild(el);
    detach(el);
  }

  function runAfterMain(html, deps) {
    var options = deps || {};
    var api = window.SpaPageScripts;
    var load = options.loadScript || api.loadScript;
    var run = options.runScript || api.runScript;
    var cancelled = options.isCancelled || function () { return false; };
    return collect(html).reduce(function (chain, tag) {
      return chain.then(function () {
        if (cancelled()) return undefined;
        if (tag.src) return tag.pageScript ? load(tag.src) : undefined;
        run(tag.code);
        return undefined;
      });
    }, Promise.resolve());
  }

  return {
    collect: collect,
    loadScript: loadScript,
    runScript: runScript,
    runAfterMain: runAfterMain
  };
})();
