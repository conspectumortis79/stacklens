(function () {
  'use strict';

  var OBJECT_URL_KEY = '__stacklensFrameObjectUrl';

  function blobDocumentsSupported() {
    return typeof window.Blob === 'function' &&
      typeof URL !== 'undefined' &&
      typeof URL.createObjectURL === 'function' &&
      typeof URL.revokeObjectURL === 'function';
  }

  function releaseObjectUrl(iframe) {
    var url = iframe[OBJECT_URL_KEY];
    if (!url) return;
    iframe[OBJECT_URL_KEY] = null;
    try {
      URL.revokeObjectURL(url);
    } catch (_) { }
  }

  function trackLoadedDocument(iframe) {
    iframe.addEventListener('load', function handler() {
      iframe.removeEventListener('load', handler);
      releaseObjectUrl(iframe);
    });
  }

  function setFrameHtml(iframe, html) {
    if (!iframe) return;
    var markup = html == null ? '' : String(html);
    releaseObjectUrl(iframe);
    if (!blobDocumentsSupported()) {
      iframe.srcdoc = markup;
      return;
    }
    var url = URL.createObjectURL(new window.Blob([markup], { type: 'text/html' }));
    iframe[OBJECT_URL_KEY] = url;
    trackLoadedDocument(iframe);
    iframe.removeAttribute('srcdoc');
    iframe.src = url;
  }

  window.stacklensSetFrameHtml = setFrameHtml;
})();
