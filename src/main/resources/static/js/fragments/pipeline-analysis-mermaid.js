  var _paMermaidInitialized = false;
  var _paMermaidLoadingPromise = null;
  var _PA_MERMAID_KEYWORDS = /^(graph|flowchart|sequenceDiagram|classDiagram|stateDiagram|erDiagram|journey|gantt|pie|gitGraph|requirementDiagram|mindmap|timeline|quadrantChart|xychart-beta|sankey-beta|block-beta|architecture-beta|packet-beta|C4Context|C4Container|C4Component|C4Dynamic|C4Deployment)\b/i;

  function _paMermaidLoadScript() {
    if (window.mermaid && window.mermaid.render) return Promise.resolve();
    if (_paMermaidLoadingPromise) return _paMermaidLoadingPromise;
    _paMermaidLoadingPromise = new Promise(function (resolve) {
      var s = document.createElement('script');
      s.src = '/js/mermaid.min.js';
      s.async = true;
      s.onload = function () { resolve(); };
      s.onerror = function () { resolve(); };
      document.head.appendChild(s);
    });
    return _paMermaidLoadingPromise;
  }

  function _paActiveMermaidTheme() {
    var t = (document.documentElement.getAttribute('data-theme') ||
             document.body.getAttribute('data-theme') || '').toLowerCase();
    return t === 'dark' ? 'dark' : 'default';
  }

  async function paRenderMermaid(src) {
    var container = document.getElementById('paMermaidContainer');
    if (!container) return;
    if (!src) return;
    var firstLine = src.trim().split(/\r?\n/).find(function (l) { return l.trim().length > 0; }) || '';
    if (!_PA_MERMAID_KEYWORDS.test(firstLine.trim())) {
      container.innerHTML = '<div class="pa-empty"><i class="bi bi-diagram-3"></i><span>' + paT('pipeline.mermaid_unavailable') + '</span></div>';
      return;
    }
    await _paMermaidLoadScript();
    if (!window.mermaid || !window.mermaid.render) {
      container.innerHTML =
        '<div style="padding:20px;color:var(--pa-muted);">' + paT('pipeline.mermaid_not_loaded') + '</div>';
      return;
    }
    if (!_paMermaidInitialized) {
      try {
        window.mermaid.initialize({
          startOnLoad: false,
          theme: _paActiveMermaidTheme(),
          securityLevel: 'loose',
        });
      } catch (_) {}
      _paMermaidInitialized = true;
    }
    var headStylesBefore = Array.from(document.head.querySelectorAll('style'));
    container.innerHTML = '';
    try {
      var id = 'pa-mermaid-' + Date.now() + '-' + Math.floor(Math.random() * 1000);
      var result = await window.mermaid.render(id, src);
      container.innerHTML = result.svg;
      document.head.querySelectorAll('style').forEach(function (st) {
        if (headStylesBefore.indexOf(st) === -1) st.remove();
      });
      var svg = container.querySelector('svg');
      if (svg) {
        svg.removeAttribute('width');
        svg.removeAttribute('height');
        svg.style.maxWidth = '100%';
        svg.style.maxHeight = '480px';
        svg.style.height = 'auto';
        svg.querySelectorAll('text, tspan').forEach(function (el) {
          if (!el.getAttribute('fill') || el.getAttribute('fill') === 'currentColor') {
            el.setAttribute('fill', '#1f1f1f');
          }
        });
        svg.querySelectorAll('foreignObject *').forEach(function (el) {
          el.style.setProperty('color', '#1f1f1f', 'important');
        });
        paSetupMermaidPanZoom(container);
      }
    } catch (e) {
      document.head.querySelectorAll('style').forEach(function (st) {
        if (headStylesBefore.indexOf(st) === -1) st.remove();
      });
      container.innerHTML =
        '<div class="pa-empty"><i class="bi bi-exclamation-triangle"></i><span>' +
        paT('pipeline.diagram_render_error') + ': ' + paEsc(e.message || '') +
        '</span></div>';
    }
  }

  function paSetupMermaidPanZoom(container) {
    if (!container) return null;
    var viewport = container.querySelector(':scope > .pa-mermaid-viewport');
    if (!viewport) {
      var svg = container.querySelector(':scope > svg');
      if (!svg) return null;
      viewport = document.createElement('div');
      viewport.className = 'pa-mermaid-viewport';
      viewport.appendChild(svg);
      container.appendChild(viewport);
    }

    if (!container.querySelector(':scope > .pa-mermaid-zoom-controls')) {
      var ctrl = document.createElement('div');
      ctrl.className = 'pa-mermaid-zoom-controls';
      ctrl.innerHTML =
        '<button type="button" class="pa-mermaid-zoom-btn" onclick="paMermaidZoomIn()" title="Vergrößern" aria-label="Vergrößern"><i class="bi bi-plus-lg"></i></button>' +
        '<button type="button" class="pa-mermaid-zoom-btn" onclick="paMermaidZoomOut()" title="Verkleinern" aria-label="Verkleinern"><i class="bi bi-dash-lg"></i></button>' +
        '<button type="button" class="pa-mermaid-zoom-btn" onclick="paMermaidZoomReset()" title="Zurücksetzen" aria-label="Zurücksetzen"><i class="bi bi-arrow-counterclockwise"></i></button>' +
        '<span class="pa-mermaid-zoom-level">100%</span>';
      container.appendChild(ctrl);
    }
    if (!container.querySelector(':scope > .pa-mermaid-empty-hint')) {
      var hint = document.createElement('div');
      hint.className = 'pa-mermaid-empty-hint';
      hint.innerHTML = '<i class="bi bi-arrows-move me-1"></i>Ziehen zum Verschieben · Scrollen zum Zoomen';
      container.appendChild(hint);
    }
    var state = { scale: 1, tx: 0, ty: 0, dragging: false, _sx: 0, _sy: 0, _stx: 0, _sty: 0 };
    var levelEl = container.querySelector('.pa-mermaid-zoom-level');

    function apply() {
      viewport.style.transform = 'translate(' + state.tx + 'px,' + state.ty + 'px) scale(' + state.scale + ')';
      if (levelEl) levelEl.textContent = Math.round(state.scale * 100) + '%';
    }
    function clampScale(s) { return Math.max(0.3, Math.min(5, s)); }

    function onMouseDown(e) {
      if (e.target.closest('.pa-mermaid-zoom-controls')) return;
      if (e.button !== 0 && e.pointerType !== 'touch') return;
      state.dragging = true;
      state._sx = e.clientX; state._sy = e.clientY;
      state._stx = state.tx; state._sty = state.ty;
      container.classList.add('is-dragging');
      e.preventDefault();
    }
    function onMouseMove(e) {
      if (!state.dragging) return;
      state.tx = state._stx + (e.clientX - state._sx);
      state.ty = state._sty + (e.clientY - state._sy);
      apply();
    }
    function onMouseUp() {
      if (!state.dragging) return;
      state.dragging = false;
      container.classList.remove('is-dragging');
    }
    function onWheel(e) {
      e.preventDefault();
      var factor = e.deltaY > 0 ? 0.9 : 1.1;
      var rect = container.getBoundingClientRect();
      var cx = e.clientX - rect.left - rect.width / 2;
      var cy = e.clientY - rect.top - rect.height / 2;
      var newScale = clampScale(state.scale * factor);
      state.tx = cx - (cx - state.tx) * (newScale / state.scale);
      state.ty = cy - (cy - state.ty) * (newScale / state.scale);
      state.scale = newScale;
      apply();
    }

    container.addEventListener('mousedown', onMouseDown);
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', onMouseUp);
    container.addEventListener('wheel', onWheel, { passive: false });

    container._paZoom = {
      zoomIn:  function () { state.scale = clampScale(state.scale * 1.2); apply(); },
      zoomOut: function () { state.scale = clampScale(state.scale * 0.8); apply(); },
      reset:   function () { state.scale = 1; state.tx = 0; state.ty = 0; apply(); },
      _teardown: function () {
        container.removeEventListener('mousedown', onMouseDown);
        container.removeEventListener('wheel', onWheel);
        window.removeEventListener('mousemove', onMouseMove);
        window.removeEventListener('mouseup', onMouseUp);
      }
    };

    var isInlineContainer = container.id === 'paMermaidContainer';
    var _inlineClickThreshold = 5;
    var _inlineMouseDownPos = null;
    if (isInlineContainer && !container._paClickFsWired) {
      container._paClickFsWired = true;
      container.addEventListener('mousedown', function(e) {
        if (e.target.closest('.pa-mermaid-zoom-controls')) return;
        _inlineMouseDownPos = { x: e.clientX, y: e.clientY };
      });
      container.addEventListener('click', function(e) {
        if (e.target.closest('.pa-mermaid-zoom-controls')) return;
        if (_inlineMouseDownPos) {
          var dx = e.clientX - _inlineMouseDownPos.x;
          var dy = e.clientY - _inlineMouseDownPos.y;
          if (Math.sqrt(dx * dx + dy * dy) > _inlineClickThreshold) return;
        }
        if (typeof paOpenMermaidFullscreen === 'function') paOpenMermaidFullscreen();
      });
    }
    apply();
    return container._paZoom;
  }

  function paActiveMermaidContainer() {
    var fs = document.getElementById('paMermaidFullscreen');
    if (fs && fs.classList.contains('open')) {
      return document.getElementById('paMermaidFullContainer');
    }
    return document.getElementById('paMermaidContainer');
  }

  function paMermaidZoomIn()   { var c = paActiveMermaidContainer(); if (c && c._paZoom) c._paZoom.zoomIn();  }
  function paMermaidZoomOut()  { var c = paActiveMermaidContainer(); if (c && c._paZoom) c._paZoom.zoomOut(); }
  function paMermaidZoomReset(){ var c = paActiveMermaidContainer(); if (c && c._paZoom) c._paZoom.reset();   }

  function paOpenMermaidFullscreen() {
    if (!paMermaidStore) return;
    var fs = document.getElementById('paMermaidFullscreen');
    if (fs) fs.classList.add('open');
    paBindEscape('paMermaidFullscreen', paCloseMermaidFullscreen);
    var c = document.getElementById('paMermaidFullContainer');
    if (!c) return;
    if (c._paZoom && c._paZoom._teardown) c._paZoom._teardown();
    c.innerHTML = '';
    var inlineSvg = document.getElementById('paMermaidContainer').querySelector('svg');
    if (inlineSvg) {
      var cloned = inlineSvg.cloneNode(true);
      cloned.style.maxWidth = '100%';
      cloned.style.maxHeight = '80vh';
      c.appendChild(cloned);
      c.style.cursor = 'grab';
      c.style.position = 'relative';
      c.style.overflow = 'hidden';
      c.style.width = '100%';
      c.style.height = '100%';
      c.style.display = 'flex';
      c.style.alignItems = 'center';
      c.style.justifyContent = 'center';
      paSetupMermaidPanZoom(c);
    } else {
      _paMermaidRenderInto(c, paMermaidStore);
    }
  }
  async function _paMermaidRenderInto(container, src) {
    if (!src) return;
    await _paMermaidLoadScript();
    if (!window.mermaid || !window.mermaid.render) return;
    try {
      var id = 'pa-mermaid-fs-' + Date.now();
      var result = await window.mermaid.render(id, src);
      container.insertAdjacentHTML('beforeend', result.svg);
      var svg = container.querySelector('svg');
      if (svg) {
        svg.removeAttribute('width');
        svg.removeAttribute('height');
        svg.style.maxWidth = '100%';
        svg.style.maxHeight = '80vh';
        svg.style.height = 'auto';
      }
      container.style.cursor = 'grab';
      container.style.display = 'flex';
      container.style.alignItems = 'center';
      container.style.justifyContent = 'center';
      paSetupMermaidPanZoom(container);
    } catch (_) {}
  }

  function paCloseMermaidFullscreen() {
    var fs = document.getElementById('paMermaidFullscreen');
    if (fs) fs.classList.remove('open');
    paUnbindEscape('paMermaidFullscreen');
    var c = document.getElementById('paMermaidFullContainer');
    if (c) {
      if (c._paZoom && c._paZoom._teardown) c._paZoom._teardown();
      c.innerHTML = '';
    }
  }

  function paDownloadMermaid() {
    if (!paMermaidStore) return;
    var blob = new Blob([paMermaidStore], { type: 'text/plain;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = (paCurrentRepoName || 'stack').replace(/[^\w.-]+/g, '_') + '-stack.mmd';
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { document.body.removeChild(a); URL.revokeObjectURL(url); }, 0);
  }
