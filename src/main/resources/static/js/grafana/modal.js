import { escHtml } from "./helpers.js";

export const ModalMixin = {

    _ensureModal() {
      if (document.getElementById('gbPanelModal')) return;

      const tpl = `
<div id="gbPanelModal" class="gb-modal-backdrop"
     onclick="if(event.target===this)GrafanaBoard.closeModal()">
  <div class="gb-modal">

    <div class="gb-modal-header">
      <span class="gb-modal-title">
        <i class="bi bi-grid-3x3-gap" style="color:#7c5cbf;"></i>
        <span id="gbModalTitle">Panel</span>
        <span id="gbModalTypeBadge" class="gb-modal-title-panel-type"></span>
      </span>
      <button class="gb-modal-close" onclick="GrafanaBoard.closeModal()" title="Schließen">&#x2715;</button>
    </div>

    <!-- Vorschau – immer sichtbar -->
    <div class="gb-modal-preview-wrap" id="gbModalPreviewWrap">
      <canvas id="gbModalPreviewCanvas"></canvas>
      <div id="gbModalStatPreview" class="gb-modal-stat-preview" style="display:none;"></div>
    </div>

    <!-- JSON-Editor – direkt unter der Vorschau, immer sichtbar -->
    <div class="gb-modal-editor-section">
      <div class="gb-modal-editor-label">
        <i class="bi bi-code-square"></i>
        <span>JSON-Editor</span>
        <span class="gb-modal-editor-hint">Änderungen werden sofort übernommen &nbsp;·&nbsp; Strg+S zum Speichern</span>
      </div>
      <div class="gb-editor-scroll-wrap">
        <textarea id="gbPanelJsonEditor" class="gb-json-editor" spellcheck="false"></textarea>
        <div class="sl-scroll-track" id="gbEditorScrollTrack"><div class="sl-scroll-thumb" id="gbEditorScrollThumb"></div></div>
      </div>
      <div id="gbJsonError" class="gb-json-error"></div>
    </div>

    <div class="gb-modal-footer">
      <button class="gb-modal-btn-format" onclick="GrafanaBoard._formatJson()">
        <i class="bi bi-indent"></i> Formatieren
      </button>
      <button class="gb-modal-btn-cancel" onclick="GrafanaBoard.closeModal()">
        <i class="bi bi-x-lg"></i> Abbrechen
      </button>
      <button class="gb-modal-btn-save" onclick="GrafanaBoard._savePanel()">
        <i class="bi bi-floppy"></i> Speichern
      </button>
    </div>

  </div>
</div>`;

      const div = document.createElement('div');
      div.innerHTML = tpl.trim();
      const modalEl = div.firstElementChild;
      document.body.appendChild(modalEl);

      const ta = modalEl.querySelector('#gbPanelJsonEditor');
      if (ta) {
        ta.addEventListener('input', () => { GrafanaBoard._handleJsonEditorInput(); });
        ta.addEventListener('keydown', (e) => {
          if (e.key === 's' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            GrafanaBoard._savePanel();
          }
        });
      }
      const editorTrack = modalEl.querySelector('#gbEditorScrollTrack');
      const editorThumb = modalEl.querySelector('#gbEditorScrollThumb');
      if (ta && editorTrack && editorThumb && window.SLScroll) {
        this._editorScroll = window.SLScroll(ta, editorTrack, editorThumb);
      }
    },

    _openModal(panelIdx) {
      if (!document.getElementById('gaBoardContainer')) return;

      this._ensureModal();
      const dash  = this._dashObj;
      if (!dash) return;
      const panel = (dash.panels || [])[panelIdx];
      if (!panel || panel.type === 'row') return;

      this._editingIdx = panelIdx;

      this._originalPanelJson = JSON.stringify(panel);

      document.getElementById('gbModalTitle').textContent    = panel.title || 'Panel';
      document.getElementById('gbModalTypeBadge').textContent = panel.type || '';

      const editor = document.getElementById('gbPanelJsonEditor');
      if (editor) editor.value = JSON.stringify(panel, null, 2);
      document.getElementById('gbJsonError').style.display = 'none';
      if (this._editorScroll) this._editorScroll.update();

      const ta = document.getElementById('gbPanelJsonEditor');
      if (ta) {
        if (this._boundJsonInput) {
          ta.removeEventListener('input', this._boundJsonInput);
        }
        this._boundJsonInput = () => this._handleJsonEditorInput();
        ta.addEventListener('input', this._boundJsonInput);
      }

      document.getElementById('gbPanelModal').classList.add('is-open');
      const modalEl = document.getElementById('gbPanelModal');
      this._savedScrollEls = [];
      document.querySelectorAll('*').forEach(el => {
        if (el === modalEl || (modalEl && modalEl.contains(el))) return;
        const oy = window.getComputedStyle(el).overflowY;
        if (oy === 'auto' || oy === 'scroll') {
          this._savedScrollEls.push({ el, oy });
          el.style.overflowY = 'hidden';
        }
      });
      document.documentElement.style.overflow = 'hidden';
      document.body.style.overflow = 'hidden';
      requestAnimationFrame(() => requestAnimationFrame(() => {
        this._renderModalPreview();
      }));
    },


    _renderModalPreview(panelOverride) {
      if (this._editingIdx < 0 || !this._dashObj) return;

      const panel = panelOverride || (this._dashObj.panels || [])[this._editingIdx];
      if (!panel) return;

      let dIdx = 0;
      (this._dashObj.panels || []).forEach((p, i) => {
        if (p.type !== 'row' && i < this._editingIdx) dIdx++;
      });
      const pData = this._panelApiData[dIdx] || null;

      const titleEl = document.getElementById('gbModalTitle');
      const badgeEl = document.getElementById('gbModalTypeBadge');
      if (titleEl) titleEl.textContent = panel.title || 'Panel';
      if (badgeEl) badgeEl.textContent = panel.type  || '';

      const wrap = document.getElementById('gbModalPreviewWrap');

      let canvas      = document.getElementById('gbModalPreviewCanvas');
      let statPreview = document.getElementById('gbModalStatPreview');
      if (!canvas || !statPreview) {
        if (wrap) {
          wrap.innerHTML =
            '<canvas id="gbModalPreviewCanvas"></canvas>' +
            '<div id="gbModalStatPreview" class="gb-modal-stat-preview" style="display:none;"></div>';
          canvas      = document.getElementById('gbModalPreviewCanvas');
          statPreview = document.getElementById('gbModalStatPreview');
        }
      }

      const noPreviewMsg = wrap?.querySelector('.gb-no-preview-msg');
      if (noPreviewMsg) noPreviewMsg.style.display = 'none';

      if (panel.type === 'stat' || panel.type === 'singlestat') {
        this._destroyPreviewChart();
        if (canvas)      canvas.style.display = 'none';
        if (statPreview) {
          statPreview.style.display = 'flex';
          const val   = this._extractStatValue(pData);
          const color = this._resolveThresholdColor(panel, val);
          statPreview.innerHTML =
            '<div class="gb-stat-value" style="color:' + color + '">' + escHtml(String(val)) + '</div>' +
            (pData?.unit ? '<div class="gb-stat-unit">' + escHtml(pData.unit) + '</div>' : '') +
            (pData?.datasets?.[0]?.label
              ? '<div class="gb-stat-label">' + escHtml(pData.datasets[0].label) + '</div>'
              : '');
        }
        return;
      }

      if (canvas) canvas.style.display = 'block';
      if (statPreview) statPreview.style.display = 'none';
      if (!canvas || typeof Chart === 'undefined') return;

      const config = this._buildChartConfig(panel, pData, true);
      if (!config) {
        this._destroyPreviewChart();
        if (canvas) canvas.style.display = 'none';
        if (wrap) {
          let msg = wrap.querySelector('.gb-no-preview-msg');
          if (!msg) {
            msg = document.createElement('div');
            msg.className = 'gb-no-data gb-no-preview-msg';
            msg.style.cssText = 'height:100%;background:#1f2128;border-radius:10px;';
            msg.textContent = window.i18n ? window.i18n.t('grafana_analysis.js_no_preview') : 'Keine Vorschau für diesen Panel-Typ';
            wrap.appendChild(msg);
          }
          msg.style.display = '';
        }
        return;
      }

      if (this._previewChart) {
        const sameType = this._previewChart.config?.type === config.type;
        if (sameType) {
          try {
            this._previewChart.data.labels   = config.data.labels;
            this._previewChart.data.datasets = config.data.datasets;
            this._previewChart.options       = config.options;
            this._previewChart.update('none');
            return;
          } catch (_) {  }
        }
      }

      this._destroyPreviewChart();

      if (!canvas.offsetWidth || !canvas.offsetHeight) {
        requestAnimationFrame(() => this._renderModalPreview(panelOverride));
        return;
      }

      try {
        this._previewChart = new Chart(canvas, config);
      } catch (e) {
        console.warn('[GrafanaBoard] Modal chart error:', e);
      }
    },

    _formatJson() {
      const editor = document.getElementById('gbPanelJsonEditor');
      const errEl  = document.getElementById('gbJsonError');
      if (!editor) return;
      try {
        editor.value = JSON.stringify(JSON.parse(editor.value), null, 2);
        errEl.style.display = 'none';
        if (this._editorScroll) this._editorScroll.update();
      } catch (e) {
        errEl.textContent    = (window.i18n ? window.i18n.t('grafana_analysis.js_invalid_json') : 'Ungültiges JSON') + ': ' + e.message;
        errEl.style.display  = 'block';
      }
    },

    _savePanel() {
      const editor = document.getElementById('gbPanelJsonEditor');
      const errEl  = document.getElementById('gbJsonError');
      if (!editor) return;

      let newPanel;
      try   { newPanel = JSON.parse(editor.value); }
      catch (e) {
        errEl.textContent   = (window.i18n ? window.i18n.t('grafana_analysis.js_invalid_json') : 'Ungültiges JSON') + ': ' + e.message;
        errEl.style.display = 'block';
        return;
      }
      errEl.style.display = 'none';

      if (!this._dashObj || this._editingIdx < 0) return;

      this._dashObj.panels[this._editingIdx] = newPanel;

      let fullObj;
      try {
        const orig = JSON.parse(this._rawJson);
        if (orig.dashboard) {
          orig.dashboard = this._dashObj;
          fullObj = orig;
        } else {
          fullObj = this._dashObj;
        }
      } catch (_) {
        fullObj = this._dashObj;
      }
      const fullJsonStr = JSON.stringify(fullObj, null, 2);

      const ta = document.getElementById('inputDashboardJson');
      if (ta) {
        ta.value = fullJsonStr;
        ta.dispatchEvent(new Event('input'));
      }
      this._rawJson = fullJsonStr;

      this._originalPanelJson = null;

      this.closeModal();
      if (window.showAlert) window.showAlert(window.i18n ? window.i18n.t('grafana_analysis.js_panel_saved') : 'Panel gespeichert.', 'success');

      this.renderFromTextarea();
    },


    _handleJsonEditorInput() {
      if (this._livePreviewTimer) clearTimeout(this._livePreviewTimer);
      this._livePreviewTimer = setTimeout(() => {
        this._livePreviewTimer = null;
        const editor = document.getElementById('gbPanelJsonEditor');
        const errEl  = document.getElementById('gbJsonError');
        if (!editor) return;

        let parsed;
        try {
          parsed = JSON.parse(editor.value);
          if (errEl) { errEl.textContent = ''; errEl.style.display = 'none'; }
        } catch (e) {
          if (errEl) {
            errEl.textContent   = (window.i18n ? window.i18n.t('grafana_board.json_error_prefix') : 'JSON-Fehler: ') + e.message;
            errEl.style.display = 'block';
          }
          return;
        }

        if (this._dashObj && this._editingIdx >= 0) {
          this._dashObj.panels[this._editingIdx] = parsed;
        }

        try { this._renderModalPreview(parsed); } catch (_) {}

        if (this._dashObj && this._editingIdx >= 0) {
          try { this._updateSinglePanelInBoard(this._editingIdx, parsed); } catch (_) {}
        }
      }, 150);
    },

    _updateSinglePanelInBoard(panelIdx, panel) {
      if (!this._dashObj || !this._container) return;

      let dIdx = 0;
      (this._dashObj.panels || []).forEach((p, i) => {
        if (p.type !== 'row' && i < panelIdx) dIdx++;
      });
      const pData = this._panelApiData[dIdx] || null;

      const panelEl = this._container.querySelector('[data-panel-idx="' + panelIdx + '"]');
      if (!panelEl) return;

      const titleEl = panelEl.querySelector('.gb-panel-title');
      if (titleEl) titleEl.textContent = panel.title || 'Panel';
      panelEl.title = panel.title || '';

      const badgeEl = panelEl.querySelector('.gb-panel-type-badge');
      if (badgeEl) badgeEl.textContent = panel.type || '';

      if (panel.type === 'stat' || panel.type === 'singlestat') {
        const val   = this._extractStatValue(pData);
        const color = this._resolveThresholdColor(panel, val);
        const valEl = panelEl.querySelector('.gb-stat-value');
        if (valEl) { valEl.textContent = String(val); valEl.style.color = color; }
      } else if (panel.type !== 'text' && panel.type !== 'row') {
        const canvas = document.getElementById('gb-c-' + panelIdx);
        if (canvas) this._renderChart(canvas, panel, pData);
      }
    },

};
