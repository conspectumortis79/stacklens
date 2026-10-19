'use strict';

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', kbLoadOverview);
  } else {
    kbLoadOverview();
  }

(function kbWireActionDelegation () {
    if (typeof document === 'undefined') return;
    if (window.__kbActionDelegationWired) return;
    window.__kbActionDelegationWired = true;
    document.addEventListener('click', function (ev) {
      var el = ev.target.closest && ev.target.closest('[data-kb-action]');
      if (!el) return;
      if (el.__kbActionBound) return;
      el.__kbActionBound = true;
      var spec = el.getAttribute('data-kb-action');
      if (!spec) return;
      var colon = spec.indexOf(':');
      var handler = colon >= 0 ? spec.slice(0, colon) : spec;
      var arg = colon >= 0 ? spec.slice(colon + 1) : null;
      var fn = window[handler];
      if (typeof fn !== 'function') {

        if (typeof console !== 'undefined') console.warn('kb: data-kb-action handler not found:', handler);
        return;
      }
      try {
        if (arg === null) fn.call(el);
        else fn.call(el, arg);
      } catch (e) {
        if (typeof console !== 'undefined') console.error('kb: data-kb-action threw:', e);
      }
    });
  })();

  if (window.location.pathname.indexOf('/knowledge') === 0) {
    kbShowReindexHintOnce();
  }
