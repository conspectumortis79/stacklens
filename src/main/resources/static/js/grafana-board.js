
import { LiveMixin } from './grafana/live.js';
import { BoardMixin } from './grafana/board.js';
import { ChartMixin } from './grafana/chart.js';
import { ModalMixin } from './grafana/modal.js';
import { DragdropMixin } from './grafana/dragdrop.js';
import { ContextmenuMixin } from './grafana/contextmenu.js';


(function injectCss() {
  if (document.querySelector('link[data-gb-css]')) return;
  const link = document.createElement('link');
  link.rel  = 'stylesheet';
  link.type = 'text/css';
  const base = document.currentScript ? document.currentScript.src.replace('/js/grafana-board.js', '') : '';
  link.href = (base || '') + '/css/grafana-board.css';
  link.setAttribute('data-gb-css', '1');
  document.head.appendChild(link);
})();

const GrafanaBoard = {
    _container:       null,
    _rawJson:         '',
    _dashObj:         null,
    _panelApiData:    [],
    _charts:          new Map(),
    _previewChart:    null,
    _editingIdx:      -1,
    _contextMenu:     null,
    _livePreviewTimer: null,
    _originalPanelJson: null,
    _boundJsonInput:  null,
    _liveTimer:       null,
    LIVE_INTERVAL_MS: 5000,

    async renderFromTextarea(cachedPanelData) {
      const ta = document.getElementById('inputDashboardJson');
      if (!ta) return;
      const jsonStr = ta.value.trim();
      if (!jsonStr) {
        if (window.showAlert) window.showAlert(window.i18n ? window.i18n.t('grafana_analysis.js_no_dashboard_json') : 'Kein Dashboard-JSON vorhanden.', 'warning');
        return;
      }

      let parsed;
      try { parsed = JSON.parse(jsonStr); }
      catch (e) {
        if (window.showAlert) window.showAlert((window.i18n ? window.i18n.t('grafana_analysis.js_invalid_json') : 'Ungültiges JSON') + ': ' + e.message, 'danger');
        return;
      }

      this._rawJson  = jsonStr;
      this._dashObj  = parsed.dashboard || parsed;

      const section   = document.getElementById('gaBoardSection');
      const container = document.getElementById('gaBoardContainer');
      if (!section || !container) {
        if (window.showAlert) window.showAlert(window.i18n ? window.i18n.t('grafana_analysis.js_board_container_missing') : '[GrafanaBoard] Board-Container #gaBoardSection nicht gefunden.', 'danger');
        return;
      }

      section.style.display = 'block';
      const boardChatRow = document.getElementById('gaBoardChatRow');
      if (boardChatRow) boardChatRow.classList.remove('no-board');
      const boardChatHeader = document.getElementById('gaBoardChatHeader');
      if (boardChatHeader) boardChatHeader.style.display = 'flex';
      this._container = container;
      (boardChatHeader || boardChatRow || section).scrollIntoView({ behavior: 'smooth', block: 'start' });

      this._stopLive();

      if (cachedPanelData && cachedPanelData.length) {
        this._panelApiData = cachedPanelData;
        this._renderBoard();
        requestAnimationFrame(() => requestAnimationFrame(() => {
          this._updateChartsWithData(this._panelApiData);
          this._startLive();
        }));
        return;
      }

      const renderablePanelCount = (this._dashObj.panels || []).filter(p => p.type !== 'row').length;
      if (this._panelApiData.length > 0 && this._panelApiData.length === renderablePanelCount) {
        this._renderBoard();
        requestAnimationFrame(() => requestAnimationFrame(() => {
          this._updateChartsWithData(this._panelApiData);
          this._startLive();
        }));
        return;
      }

      this._panelApiData = this._seedClientData();
      this._renderBoard();

      try {
        const fetchOptions = { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ dashboardJson: jsonStr }) };
        try { fetchOptions.signal = AbortSignal.timeout(20000); } catch (_) {}

        const resp = await fetch('/api/grafana-analyse/generate-dummy-data', fetchOptions);
        if (resp.ok) {
          const result = await resp.json();
          const panelData = result.panels || [];
          if (panelData.length) {
            panelData.forEach((ap, i) => {
              if (!this._panelApiData[i]) return;
              if (ap.datasets?.length) {
                ap.datasets.forEach((ads, di) => {
                  if (!this._panelApiData[i].datasets[di]) return;
                  const last = ads.data?.[ads.data.length - 1];
                  if (last != null) {
                    this._panelApiData[i].datasets[di].data.push(last);
                    if (this._panelApiData[i].datasets[di].data.length > 20)
                      this._panelApiData[i].datasets[di].data.shift();
                  }
                  if (ads.label) this._panelApiData[i].datasets[di].label = ads.label;
                });
              }
              if (ap.unit) this._panelApiData[i].unit = ap.unit;
            });
            this._updateChartsWithData(this._panelApiData);
          }
        }
      } catch (_) {
      }

      this._startLive();
    },

    async refresh() {
      const ta = document.getElementById('inputDashboardJson');
      if (ta && ta.value.trim()) this._rawJson = ta.value.trim();
      if (!this._rawJson) return;
      this._panelApiData = [];
      await this.renderFromTextarea();
    },

    _toggleExpand(btn) {
      const wrapper = btn.closest('.gb-wrapper');
      const canvas  = wrapper && wrapper.querySelector('.gb-canvas');
      if (!canvas) return;
      const fade     = wrapper.querySelector('.gb-canvas-fade');
      const expanded = canvas.dataset.gbExpanded === '1';
      if (expanded) {
        canvas.style.maxHeight        = canvas.dataset.gbClampH;
        canvas.style.overflow         = 'hidden';
        canvas.dataset.gbExpanded     = '0';
        if (fade) fade.style.display  = '';
        btn.innerHTML = '<i class="bi bi-chevron-down"></i> ' + btn.dataset.showLabel;
        wrapper.scrollIntoView({ behavior: 'smooth', block: 'start' });
      } else {
        canvas.style.maxHeight        = 'none';
        canvas.style.overflow         = 'visible';
        canvas.dataset.gbExpanded     = '1';
        if (fade) fade.style.display  = 'none';
        btn.innerHTML = '<i class="bi bi-chevron-up"></i> ' + (window.i18n ? window.i18n.t('grafana_board.show_less') : 'Weniger anzeigen');
      }
    },

    destroy() {
      this._stopLive();
      this.closeModal();
      this._charts.forEach(c => { try { c.destroy(); } catch (_) {} });
      this._charts.clear();
      this._destroyPreviewChart();
      if (this._container) this._container.innerHTML = '';
    }
};


Object.assign(
    GrafanaBoard,
    LiveMixin,
    BoardMixin,
    ChartMixin,
    ModalMixin,
    DragdropMixin,
    ContextmenuMixin
);

window.GrafanaBoard = GrafanaBoard;
export default GrafanaBoard;
