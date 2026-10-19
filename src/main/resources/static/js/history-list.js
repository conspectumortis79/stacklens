(function () {
  'use strict';

  var STATUS_ALIASES = {
    DONE: 'COMPLETED',
    SUCCESS: 'COMPLETED',
    OK: 'COMPLETED',
    ERROR: 'FAILED',
    ABGEBROCHEN: 'CANCELLED',
    CANCELED: 'CANCELLED',
    CANCELING: 'RUNNING',
    IN_PROGRESS: 'RUNNING',
    QUEUED: 'PENDING',
    STARTED: 'RUNNING'
  };

  var STATUS_META = {
    COMPLETED: { key: 'common.status_completed', fallback: 'Abgeschlossen', icon: 'bi-check-circle' },
    RUNNING: { key: 'common.status_running', fallback: 'Läuft', icon: 'bi-arrow-repeat hx-spin' },
    PENDING: { key: 'common.status_pending', fallback: 'Ausstehend', icon: 'bi-hourglass-split' },
    FAILED: { key: 'common.status_failed', fallback: 'Fehlgeschlagen', icon: 'bi-x-circle' },
    CANCELLED: { key: 'common.status_cancelled', fallback: 'Abgebrochen', icon: 'bi-slash-circle' }
  };

  var FILTERS = [
    { id: 'ALL', key: 'common.history_filter_all', fallback: 'Alle' },
    { id: 'RUNNING', key: 'common.history_filter_running', fallback: 'Läuft' },
    { id: 'COMPLETED', key: 'common.history_filter_completed', fallback: 'Fertig' },
    { id: 'FAILED', key: 'common.history_filter_failed', fallback: 'Fehler' },
    { id: 'CANCELLED', key: 'common.history_filter_cancelled', fallback: 'Abbruch' }
  ];

  function t(key, fallback) {
    if (window.slHelpers && window.slHelpers.t) return window.slHelpers.t(key, fallback);
    try {
      if (window.i18n && typeof window.i18n.t === 'function') {
        var v = window.i18n.t(key);
        if (v && v !== key) return v;
      }
    } catch (_) { }
    return fallback != null ? fallback : key;
  }

  function esc(s) {
    if (window.slHelpers && window.slHelpers.esc) return window.slHelpers.esc(s);
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function statusOf(raw) {
    var k = String(raw == null ? '' : raw).trim().toUpperCase();
    if (STATUS_ALIASES[k]) k = STATUS_ALIASES[k];
    return STATUS_META[k] ? k : 'PENDING';
  }

  function isLive(raw) {
    var k = statusOf(raw);
    return k === 'RUNNING' || k === 'PENDING';
  }

  function liveSignature(item, fields) {
    var parts = [item.id == null ? '' : String(item.id), statusOf(item.status)];
    (fields || []).forEach(function (field) {
      parts.push(item[field] == null ? '' : String(item[field]));
    });
    return parts.join('|');
  }

  function createLiveSync(o) {
    var fields = (o && o.fields) || [];
    var reload = (o && typeof o.reload === 'function') ? o.reload : function () {};
    var seen = new Set();
    return function (item) {
      if (!item) return false;
      var key = liveSignature(item, fields);
      if (seen.has(key)) return false;
      seen.add(key);
      reload();
      return true;
    };
  }

  function pad(n) { return (n < 10 ? '0' : '') + n; }

  function parseDate(value) {
    if (!value) return null;
    if (value instanceof Date) return isNaN(value.getTime()) ? null : value;
    if (typeof value === 'number') return new Date(value);
    var iso = String(value).trim();
    if (/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?(\.\d+)?$/.test(iso)) iso = iso.replace(' ', 'T');
    var d = new Date(iso);
    return isNaN(d.getTime()) ? null : d;
  }

  function formatAbsolute(value) {
    var d = parseDate(value);
    if (!d) return '';
    return pad(d.getDate()) + '.' + pad(d.getMonth() + 1) + '. ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function formatRelative(value) {
    var d = parseDate(value);
    if (!d) return '';
    var mins = Math.round((Date.now() - d.getTime()) / 60000);
    if (mins < 1) return t('common.history_rel_now', 'gerade eben');
    if (mins < 60) return t('common.history_rel_minutes', 'vor {n} Min').replace('{n}', mins);
    var hours = Math.round(mins / 60);
    if (hours < 24) return t('common.history_rel_hours', 'vor {n} Std').replace('{n}', hours);
    var days = Math.round(hours / 24);
    if (days === 1) return t('common.history_rel_yesterday', 'gestern');
    if (days < 30) return t('common.history_rel_days', 'vor {n} Tg').replace('{n}', days);
    return t('common.history_rel_months', 'vor {n} Mon').replace('{n}', Math.round(days / 30));
  }

  function badgeHtml(raw) {
    var k = statusOf(raw);
    var meta = STATUS_META[k];
    return '<span class="hx-badge" data-status="' + k + '"><i class="bi ' + meta.icon + '"></i>' +
      esc(t(meta.key, meta.fallback)) + '</span>';
  }

  function openLabelFor(raw) {
    var k = statusOf(raw);
    if (k === 'RUNNING') return { key: 'common.history_track', fallback: 'Verfolgen', icon: 'bi-activity' };
    if (k === 'PENDING') return { key: 'common.history_track', fallback: 'Verfolgen', icon: 'bi-hourglass-split' };
    if (k === 'FAILED' || k === 'CANCELLED') return { key: 'common.history_protocol', fallback: 'Protokoll', icon: 'bi-file-earmark-text' };
    return { key: 'common.history_open', fallback: 'Öffnen', icon: 'bi-eye' };
  }

  function call(fn, item) {
    if (typeof fn !== 'function') return;
    fn(item);
  }

  function pick(map, key, item, fallback) {
    var v = typeof map[key] === 'function' ? map[key](item) : map[key];
    return v == null ? fallback : v;
  }

  function storageKey(page) { return 'stacklens.history.open.' + page; }

  function readOpen(page) {
    try {
      var v = window.localStorage.getItem(storageKey(page));
      return v === null ? true : v === '1';
    } catch (_) { return true; }
  }

  function writeOpen(page, open) {
    try { window.localStorage.setItem(storageKey(page), open ? '1' : '0'); } catch (_) { }
  }

  function shell(o) {
    var cols = o.columns || {};
    var metricCol = cols.metric ? '<th>' + esc(t(cols.metric.key, cols.metric.fallback)) + '</th>' : '';
    return '' +
      '<div class="hx-head" data-hx-toggle role="button" tabindex="0" aria-expanded="true">' +
        '<span class="hx-head-icon"><i class="bi bi-clock-history"></i></span>' +
        '<span class="hx-head-info">' +
          '<span class="hx-head-title" data-i18n="' + esc(o.titleKey || 'common.history_title') + '">' + esc(t(o.titleKey || 'common.history_title', 'Verlauf')) + '</span>' +
          '<span class="hx-head-sub" data-hx-count></span>' +
        '</span>' +
        '<button type="button" class="hx-delall" data-hx-delall hidden>' +
          '<span data-i18n="common.all_delete">' + esc(t('common.all_delete', 'Alle löschen')) + '</span>' +
        '</button>' +
        '<i class="bi bi-chevron-down hx-chevron"></i>' +
      '</div>' +
      (o.filter === false ? '' :
        '<div class="hx-filterbar" data-hx-filterbar>' +
          FILTERS.map(function (f) {
            return '<button type="button" class="hx-filter" data-hx-filter="' + f.id + '" ' +
              'data-i18n="' + f.key + '">' + esc(t(f.key, f.fallback)) + '</button>';
          }).join('') +
          (o.search === false ? '' :
            '<label class="hx-search"><i class="bi bi-search"></i>' +
            '<input type="text" data-hx-search autocomplete="off" spellcheck="false" ' +
            'data-i18n-placeholder="common.history_search_placeholder" ' +
            'placeholder="' + esc(t('common.history_search_placeholder', 'Verlauf durchsuchen')) + '" ' +
            'aria-label="' + esc(t('common.history_search_placeholder', 'Verlauf durchsuchen')) + '"></label>') +
        '</div>') +
      '<div class="hx-body" data-hx-body>' +
        '<div class="hx-empty" data-hx-empty hidden><i class="bi bi-clock-history"></i>' +
          '<span data-i18n="' + esc(o.emptyKey || 'common.history_empty') + '">' + esc(t(o.emptyKey || 'common.history_empty', 'Noch keine Einträge.')) + '</span>' +
          (o.emptyAction ? '<div style="margin-top:12px"><button type="button" class="hx-open" data-hx-empty-action>' +
            '<i class="bi ' + esc(o.emptyAction.icon || 'bi-play-fill') + '"></i>' +
            '<span data-i18n="' + esc(o.emptyAction.key) + '">' + esc(t(o.emptyAction.key, o.emptyAction.fallback)) + '</span></button></div>' : '') +
        '</div>' +
        '<table class="hx-table" data-hx-table hidden>' +
          '<thead><tr>' +
            '<th>' + esc(t(cols.title.key, cols.title.fallback)) + '</th>' +
            '<th data-i18n="common.history_col_status">' + esc(t('common.history_col_status', 'Status')) + '</th>' +
            metricCol +
            '<th data-i18n="common.history_col_created">' + esc(t('common.history_col_created', 'Erstellt')) + '</th>' +
            '<th class="hx-col-actions"></th>' +
          '</tr></thead>' +
          '<tbody data-hx-tbody></tbody>' +
        '</table>' +
      '</div>';
  }

  function rowHtml(o, item, index) {
    var map = o.map || {};
    var raw = pick(map, 'status', item, '');
    var live = isLive(raw);
    var disabled = typeof map.disabled === 'function' ? !!map.disabled(item) : false;
    var canOpen = typeof map.canOpen === 'function' ? !!map.canOpen(item) : true;
    var canCancel = typeof map.canCancel === 'function' ? !!map.canCancel(item) : true;
    var id = pick(map, 'id', item, index);
    var title = pick(map, 'title', item, '–');
    var sub = pick(map, 'subtitle', item, '');
    var note = (!live) ? pick(map, 'note', item, '') : '';
    var tags = typeof map.tags === 'function' ? (map.tags(item) || []) : [];
    var icon = pick(map, 'typeIcon', item, 'bi-clock-history');
    var current = typeof map.isCurrent === 'function' ? !!map.isCurrent(item) : false;
    var abs = formatAbsolute(pick(map, 'createdAt', item, ''));
    var rel = formatRelative(pick(map, 'createdAt', item, ''));
    var hasMetric = !!o.columns && !!o.columns.metric;
    var metric = hasMetric ? pick(map, 'metric', item, '') : '';

    var titleCell =
      '<td class="hx-cell-title">' +
        '<span class="hx-title-wrap">' +
          '<i class="bi ' + esc(icon) + ' hx-title-icon"></i>' +
          '<span style="min-width:0">' +
            '<span class="hx-title-text" title="' + esc(title) + '">' + esc(title) +
              tags.map(function (tag) {
                var label = tag && tag.label ? tag.label : tag;
                var cls = 'hx-tag' + (tag && tag.tone === 'neutral' ? ' is-neutral' : '');
                var title = tag && tag.title ? ' title="' + esc(tag.title) + '"' : '';
                if (tag && tag.href) {
                  return '<a class="' + cls + '" href="' + esc(tag.href) + '"' + title +
                    ' target="_blank" rel="noopener noreferrer">' + esc(label) + '</a>';
                }
                return '<span class="' + cls + '"' + title + '>' + esc(label) + '</span>';
              }).join('') +
            '</span>' +
            (sub ? '<span class="hx-title-sub" title="' + esc(sub) + '">' + esc(sub) + '</span>' : '') +
            (note ? '<span class="hx-title-note" title="' + esc(note) + '"><i class="bi bi-exclamation-triangle-fill"></i><span class="hx-title-note-text">' + esc(note) + '</span></span>' : '') +
          '</span>' +
        '</span>' +
      '</td>';

    var actions = '';
    var open = openLabelFor(raw);
    var dis = disabled ? ' disabled' : '';
    if (typeof o.actions.open === 'function') {
      actions += '<button type="button" class="hx-open" data-hx-act="open" data-hx-index="' + index + '"' + (disabled || !canOpen ? ' disabled' : dis) + ' ' +
        'title="' + esc(t(open.key, open.fallback)) + '"><i class="bi ' + open.icon + '"></i>' +
        esc(t(open.key, open.fallback)) + '</button>';
    }
    if (live && typeof o.actions.cancel === 'function' && canCancel) {
      actions += '<button type="button" class="hx-act is-danger" data-hx-act="cancel" data-hx-index="' + index + '"' + dis + ' ' +
        'data-i18n-title="common.history_cancel_run" title="' + esc(t('common.history_cancel_run', 'Lauf abbrechen')) + '">' +
        '<i class="bi bi-stop-circle"></i></button>';
    } else {
      if (typeof o.actions.rerun === 'function') {
        actions += '<button type="button" class="hx-act" data-hx-act="rerun" data-hx-index="' + index + '"' + dis + ' ' +
          'data-i18n-title="common.rerun" title="' + esc(t('common.rerun', 'Erneut ausführen')) + '">' +
          '<i class="bi bi-arrow-clockwise"></i></button>';
      }
      if (typeof o.actions.remove === 'function') {
        actions += '<button type="button" class="hx-act is-danger" data-hx-act="remove" data-hx-index="' + index + '"' + dis + ' ' +
          'data-i18n-title="common.delete" title="' + esc(t('common.delete', 'Löschen')) + '">' +
          '<i class="bi bi-trash"></i></button>';
      }
    }

    var clickable = typeof o.actions.open === 'function' && !disabled && canOpen;
    var html = '<tr class="hx-row' + (current ? ' is-current' : '') + (disabled ? ' is-disabled' : '') + '" data-hx-index="' + index + '"' +
      (clickable ? ' tabindex="0" data-hx-row="open"' : '') + '>' +
      titleCell +
      '<td>' + badgeHtml(raw) + '</td>' +
      (hasMetric ? '<td class="hx-cell-metric' + (metric === '' || metric == null ? ' is-empty' : '') + '">' +
        esc(metric === '' || metric == null ? '–' : metric) + '</td>' : '') +
      '<td class="hx-cell-time" title="' + esc(abs) + '">' + esc(rel || abs) + '</td>' +
      '<td class="hx-cell-actions"><span class="hx-rail">' + actions + '</span>' +
      (clickable ? '<i class="bi bi-chevron-right hx-cta"></i>' : '') + '</td>' +
      '</tr>';

    var progress = typeof map.progress === 'function' ? map.progress(item) : null;
    if (progress && live) {
      var measured = progress.pct != null && progress.pct !== '';
      var pct = measured ? Math.max(0, Math.min(100, Number(progress.pct) || 0)) : 0;
      var colspan = 4 + (hasMetric ? 1 : 0);
      html += '<tr class="hx-progress-row"><td colspan="' + colspan + '">' +
        '<div class="hx-progress">' +
          '<span class="hx-progress-text"><i class="bi bi-arrow-repeat hx-spin"></i> ' +
            esc(t('common.status_running', 'Läuft')) +
            (progress.stage ? '<span class="hx-progress-stage"> · ' + esc(progress.stage) + '</span>' : '') +
          '</span>' +
          '<span class="hx-progress-bar">' +
            (measured
              ? '<span class="hx-progress-fill" style="width:' + pct + '%"></span>'
              : '<span class="hx-progress-stripe"></span>') +
          '</span>' +
          (measured ? '<span class="hx-progress-pct">' + pct + '%</span>' : '') +
          (typeof o.actions.cancel === 'function'
            ? '<button type="button" class="hx-progress-cancel" data-hx-act="cancel" data-hx-index="' + index + '"' + dis + ' ' +
              'data-i18n="common.history_cancel_run">' + esc(t('common.history_cancel_run', 'Abbrechen')) + '</button>'
            : '') +
        '</div></td></tr>';
    }
    return html;
  }

  function mount(options) {
    var o = options || {};
    var root = typeof o.root === 'string' ? document.getElementById(o.root) : o.root;
    if (!root) return null;

    var page = o.page || 'default';
    var actions = o.actions || {};
    var state = { items: [], filter: 'ALL', query: '' };

    root.classList.add('hx-card');
    root.setAttribute('data-hx-page', page);
    root.innerHTML = shell(o);

    var head = root.querySelector('[data-hx-toggle]');
    var countEl = root.querySelector('[data-hx-count]');
    var delallEl = root.querySelector('[data-hx-delall]');
    var emptyEl = root.querySelector('[data-hx-empty]');
    var tableEl = root.querySelector('[data-hx-table]');
    var tbodyEl = root.querySelector('[data-hx-tbody]');
    var searchEl = root.querySelector('[data-hx-search]');

    function visible() {
      var q = state.query.trim().toLowerCase();
      return state.items.filter(function (item, index) {
        if (state.filter !== 'ALL' && statusOf(pick(o.map, 'status', item, '')) !== state.filter) return false;
        if (!q) return true;
        var hay = [
          pick(o.map, 'title', item, ''),
          pick(o.map, 'subtitle', item, ''),
          pick(o.map, 'note', item, '')
        ].join(' ').toLowerCase();
        return hay.indexOf(q) !== -1;
      });
    }

    function renderCount() {
      var total = state.items.length;
      var running = state.items.filter(function (item) {
        return statusOf(pick(o.map, 'status', item, '')) === 'RUNNING';
      }).length;
      var text = total === 0
        ? t('common.history_none', 'Keine Einträge')
        : total + ' ' + t(total === 1 ? 'common.history_entry_one' : 'common.history_entries', total === 1 ? 'Eintrag' : 'Einträge');
      countEl.innerHTML = esc(text) + (running
        ? '<span class="hx-running-chip"><i class="bi bi-arrow-repeat hx-spin"></i>' +
          esc(running + ' ' + t('common.history_running_short', 'läuft')) + '</span>'
        : '');
    }

    function renderRows() {
      var list = visible();
      var indexByItem = new Map();
      state.items.forEach(function (item, index) { indexByItem.set(item, index); });
      tbodyEl.innerHTML = list.map(function (item) {
        return rowHtml(o, item, indexByItem.get(item));
      }).join('');
      emptyEl.hidden = list.length !== 0;
      tableEl.hidden = list.length === 0;
      root.querySelectorAll('[data-hx-filter]').forEach(function (btn) {
        btn.classList.toggle('is-on', btn.getAttribute('data-hx-filter') === state.filter);
      });
    }

    function render() {
      renderCount();
      renderRows();
      if (delallEl) delallEl.hidden = state.items.length === 0 || typeof actions.deleteAll !== 'function';
    }

    function setCollapsed(collapsed) {
      root.classList.toggle('is-collapsed', collapsed);
      head.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
      writeOpen(page, !collapsed);
    }

    function itemAt(index) { return state.items[index]; }

    head.addEventListener('click', function (event) {
      if (event.target.closest('[data-hx-delall]')) return;
      setCollapsed(!root.classList.contains('is-collapsed'));
    });

    head.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        setCollapsed(!root.classList.contains('is-collapsed'));
      }
    });

    if (delallEl) {
      delallEl.addEventListener('click', function (event) {
        event.stopPropagation();
        call(actions.deleteAll, null);
      });
    }

    root.querySelectorAll('[data-hx-filter]').forEach(function (btn) {
      btn.addEventListener('click', function (event) {
        event.stopPropagation();
        state.filter = btn.getAttribute('data-hx-filter');
        renderRows();
      });
    });

    if (searchEl) {
      searchEl.addEventListener('input', function () {
        state.query = searchEl.value || '';
        renderRows();
      });
    }

    if (typeof (o.emptyAction && o.emptyAction.onClick) === 'function') {
      root.querySelector('[data-hx-empty-action]').addEventListener('click', function () {
        o.emptyAction.onClick();
      });
    }

    tbodyEl.addEventListener('click', function (event) {
      var actEl = event.target.closest('[data-hx-act]');
      if (actEl) {
        if (actEl.disabled) return;
        event.stopPropagation();
        var item = itemAt(Number(actEl.getAttribute('data-hx-index')));
        if (item == null) return;
        var act = actEl.getAttribute('data-hx-act');
        if (act === 'open') call(actions.open, item);
        else if (act === 'rerun') call(actions.rerun, item);
        else if (act === 'remove') call(actions.remove, item);
        else if (act === 'cancel') call(actions.cancel, item);
        return;
      }
      var row = event.target.closest('[data-hx-row="open"]');
      if (row) {
        var opened = itemAt(Number(row.getAttribute('data-hx-index')));
        if (opened != null) call(actions.open, opened);
      }
    });

    tbodyEl.addEventListener('keydown', function (event) {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      var row = event.target.closest('[data-hx-row="open"]');
      if (!row) return;
      event.preventDefault();
      var item = itemAt(Number(row.getAttribute('data-hx-index')));
      if (item != null) call(actions.open, item);
    });

    setCollapsed(!readOpen(page));
    render();

    var controller = {
      setData: function (items) {
        state.items = Array.isArray(items) ? items : [];
        render();
        return controller;
      },
      items: function () { return state.items.slice(); },
      refresh: render,
      collapse: setCollapsed,
      setHidden: function (hidden) { root.hidden = !!hidden; },
      element: root
    };
    return controller;
  }

  window.slHistory = {
    mount: mount,
    status: statusOf,
    isLive: isLive,
    createLiveSync: createLiveSync,
    badgeHtml: badgeHtml,
    formatRelative: formatRelative,
    formatAbsolute: formatAbsolute,
    parseDate: parseDate
  };
})();
