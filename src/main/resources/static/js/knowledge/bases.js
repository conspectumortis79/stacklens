'use strict';

  function kbLoadBases () {
    var tbody = document.getElementById('kbBaseRows');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">' +
      '<i class="bi bi-hourglass-split me-2"></i>' + esc(t('common.loading', 'Lade…')) + '</td></tr>';

    Promise.all([
      kbFetchBases(),
      fetch('/api/knowledge/sources').then(function (r) { return r.json(); }),
    ]).then(function (results) {
      var bases = results[0];
      var sources = results[1];
      var sourcesByBase = {};
      sources.forEach(function (s) {
        sourcesByBase[s.knowledgeBaseId] = (sourcesByBase[s.knowledgeBaseId] || 0) + 1;
      });
      var countEl = document.getElementById('kbBaseCount');
      if (countEl) countEl.textContent = bases.length;
      if (!bases.length) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">' +
          '<i class="bi bi-info-circle me-2"></i>' +
          esc(t('knowledge.bases.empty', 'Noch keine Wissensdatenbanken vorhanden. Klicke auf "Neue Wissensdatenbank" oben, um eine anzulegen.')) +
          '</td></tr>';
        return;
      }
      var html = '';
      bases.forEach(function (b) {
        var state = [];
        if (b.systemManaged) state.push('<span class="kb-system-tag">' + esc(t('knowledge.bases.state_system', 'System')) + '</span>');
        if (b.active) state.push('<span class="badge bg-success">' + esc(t('knowledge.bases.state_active', 'Aktiv')) + '</span>');
        else state.push('<span class="badge bg-secondary">' + esc(t('knowledge.bases.state_inactive', 'Inaktiv')) + '</span>');

        var count = sourcesByBase[b.id] || 0;
        html += '<tr>' +
          '<td class="kb-base-name">' + esc(b.name) + '</td>' +
          '<td class="text-muted small">' + esc(b.description || '') + '</td>' +
          '<td><span class="badge bg-light text-dark border">' + count + '</span></td>' +
          '<td>' + state.join(' ') + '</td>' +
          '<td class="text-end">' +
            (b.systemManaged ? '' :
              '<button class="btn btn-sm btn-outline-secondary me-1" onclick="kbToggleBaseActive(' + b.id + ',' + (b.active ? 'false' : 'true') + ')" title="' + esc(t('knowledge.bases.toggle_active', 'Aktivieren / Deaktivieren')) + '">' +
                '<i class="bi bi-' + (b.active ? 'pause' : 'play') + '-fill"></i></button>') +
            (b.systemManaged ? '' :
              '<button class="btn btn-sm btn-outline-danger" onclick="kbDeleteBase(' + b.id + ',\'' + esc(b.name) + '\')" title="' + esc(t('knowledge.bases.delete', 'Löschen')) + '">' +
                '<i class="bi bi-trash"></i></button>') +
          '</td>' +
        '</tr>';
      });
      tbody.innerHTML = html;
    }).catch(function (e) { kbNotify('danger', e.message); });
  }

  function kbShowCreateBaseForm () {
    var form = document.getElementById('kbCreateBaseForm');
    if (form) form.style.display = 'block';
    var nameInput = document.getElementById('kbCreateBaseName');
    if (nameInput) setTimeout(function () { nameInput.focus(); }, 50);
  }

  function kbHideCreateBaseForm () {
    var form = document.getElementById('kbCreateBaseForm');
    if (form) form.style.display = 'none';
    var n = document.getElementById('kbCreateBaseName');
    var d = document.getElementById('kbCreateBaseDescription');
    if (n) n.value = '';
    if (d) d.value = '';
  }

  function kbSubmitCreateBase () {
    var nameEl = document.getElementById('kbCreateBaseName');
    var descEl = document.getElementById('kbCreateBaseDescription');
    var name = nameEl ? nameEl.value.trim() : '';
    if (!name) {
      kbNotify('warning', t('knowledge.bases.name_required', 'Bitte einen Namen eingeben.'));
      return;
    }
    fetch('/api/knowledge/bases', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: name,
        description: descEl ? descEl.value.trim() : '',

        includeInGlobalChat: true,
      }),
    })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function () {
        kbNotify('success', t('knowledge.bases.created', 'Wissensdatenbank angelegt.'));
        kbHideCreateBaseForm();
        kbInvalidateBasesCache();
        kbLoadBases();
        kbLoadOverview();

        kbPopulateBases('#kbManualBase');
        kbPopulateBases('#kbFileBase');
        kbPopulateBases('#kbConfluenceBase');
        kbPopulateBases('#kbJiraBase');
      })
      .catch(function (e) { kbNotify('danger', e.message); });
  }

  function kbToggleBaseActive (id, newActive) {
    fetch('/api/knowledge/bases/' + id, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ active: newActive === 'true' || newActive === true }),
    })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json();
      })
      .then(function () {
        kbNotify('success', t('knowledge.bases.toggled', 'Status aktualisiert.'));
        kbInvalidateBasesCache();
        kbLoadBases();
        kbLoadOverview();
      })
      .catch(function (e) { kbNotify('danger', e.message); });
  }

  function kbGetDeleteBaseTarget () {
    var modal = document.getElementById('kbDeleteBaseModal');
    return modal ? modal.__kbDeleteBaseTarget : null;
  }
  function kbSetDeleteBaseTarget (target) {
    var modal = document.getElementById('kbDeleteBaseModal');
    if (modal) modal.__kbDeleteBaseTarget = target;
  }

  function kbOpenDeleteBaseModal (target) {
    kbSetDeleteBaseTarget(target);
    var nameEl = document.getElementById('kbDeleteBaseName');
    if (nameEl) nameEl.textContent = target.name;
    var statsEl = document.getElementById('kbDeleteBaseStats');
    if (statsEl) {

      var hasContent = (target.sourceCount + target.documentCount + target.chunkCount) > 0;
      if (hasContent) {
        statsEl.style.display = 'block';
        var statsText = t(
          'knowledge.bases.delete_stats',
          'Diese Base enthält: {0} Quellen, {1} Dokumente, {2} Chunks.',
        )
          .replace('{0}', target.sourceCount)
          .replace('{1}', target.documentCount)
          .replace('{2}', target.chunkCount);
        statsEl.innerHTML =
          '<i class="bi bi-info-circle me-1"></i>' + esc(statsText);
      } else {
        statsEl.style.display = 'none';
      }
    }
    var btn = document.getElementById('kbDeleteBaseConfirmBtn');
    if (btn) btn.disabled = false;
    var modal = bootstrap.Modal.getOrCreateInstance(document.getElementById('kbDeleteBaseModal'));
    modal.show();
  }

  function kbUpdateDeleteButton () {
    var btn = document.getElementById('kbDeleteBaseConfirmBtn');
    if (btn) btn.disabled = false;
  }

  function kbCancelDeleteBase () {
    kbSetDeleteBaseTarget(null);
  }

  function kbConfirmDeleteBase () {
    var target = kbGetDeleteBaseTarget();
    if (!target) return;
    var btn = document.getElementById('kbDeleteBaseConfirmBtn');
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = '<i class="bi bi-arrow-clockwise spin me-2"></i>' +
        esc(t('knowledge.bases.deleting', 'Löscht…'));
    }
    var modal = bootstrap.Modal.getInstance(document.getElementById('kbDeleteBaseModal'));
    fetch('/api/knowledge/bases/' + target.id, { method: 'DELETE' })
      .then(function (r) {
        if (!r.ok) return r.text().then(function (txt) { throw new Error(txt || ('HTTP ' + r.status)); });
        return r.json().catch(function () { return {}; });
      })
      .then(function () {
        if (modal) modal.hide();
        kbSetDeleteBaseTarget(null);
        kbNotify('success', t('knowledge.bases.deleted', 'Wissensdatenbank gelöscht.'));
        kbInvalidateBasesCache();
        kbLoadBases();
        kbLoadOverview();
        kbPopulateBases('#kbManualBase');
        kbPopulateBases('#kbFileBase');
        kbPopulateBases('#kbConfluenceBase');
        kbPopulateBases('#kbJiraBase');
      })
      .catch(function (e) {

        if (btn) {

          btn.innerHTML = '<i class="bi bi-trash3-fill me-1"></i>' +
            esc(t('knowledge.bases.delete_permanently', 'Endgültig löschen'));
        }
        kbUpdateDeleteButton();
        kbNotify('danger', e.message);
      });
  }

  function kbDeleteBase (id, name) {

    fetch('/api/knowledge/sources')
      .then(function (r) { return r.json(); })
      .then(function (sources) {
        var mine = sources.filter(function (s) { return s.knowledgeBaseId === id; });

        kbOpenDeleteBaseModal({
          id: id,
          name: name,
          sourceCount: mine.length,
          documentCount: 0,
          chunkCount: 0,
        });
      })
      .catch(function () {

        kbOpenDeleteBaseModal({ id: id, name: name, sourceCount: 0, documentCount: 0, chunkCount: 0 });
      });
  }
