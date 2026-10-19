import { escHtml } from "./helpers.js";

export const BoardMixin = {

    _renderBoard() {
      const dash      = this._dashObj;
      const panelData = this._panelApiData;
      if (!this._container) return;

      this._charts.forEach(c => { try { c.destroy(); } catch (_) {} });
      this._charts.clear();

      const wrapper = document.createElement('div');
      wrapper.className = 'gb-wrapper';

      const panels     = dash.panels || [];
      const chartCount = panels.filter(p => p.type !== 'row').length;
      const topbar     = document.createElement('div');
      topbar.className = 'gb-topbar';
      const t = (k, fb) => (window.i18n ? window.i18n.t(k) : fb) || fb;
      topbar.innerHTML =
        '<span class="gb-board-title">' + escHtml(dash.title || 'Dashboard') + '</span>' +
        '<span class="gb-board-meta">' + chartCount + ' ' + escHtml(t('grafana_board.panels_suffix','Panels')) + '</span>' +
        '<span class="gb-live-indicator" title="' + escHtml(t('grafana_board.live_title','Live-Daten – aktualisiert alle 5 s')) + '">' +
          '<span class="gb-live-dot"></span>' + escHtml(t('grafana_board.live','Live')) +
        '</span>' +
        '<div class="gb-topbar-actions">' +
          '<button class="gb-btn gb-btn-primary gb-btn-sm" onclick="GrafanaBoard._addPanel()">' +
            '<i class="bi bi-plus-lg"></i> ' + escHtml(t('grafana_board.add_panel','Panel hinzufügen')) +
          '</button>' +
          '<button class="gb-btn gb-btn-secondary gb-btn-sm" onclick="GrafanaBoard.refresh()">' +
            '<i class="bi bi-arrow-clockwise"></i> ' + escHtml(t('grafana_board.reload','Neu laden')) +
          '</button>' +
        '</div>';

      const gridEl = document.createElement('div');
      gridEl.className = 'gb-canvas';

      wrapper.appendChild(topbar);
      wrapper.appendChild(gridEl);
      this._container.innerHTML = '';
      this._container.appendChild(wrapper);

      let dataIdx = 0;

      panels.forEach((panel, panelIdx) => {
        const gp  = panel.gridPos || { x: 0, y: panelIdx * 8, w: 24, h: 8 };
        const pData = panel.type !== 'row' ? (panelData[dataIdx] || null) : null;
        if (panel.type !== 'row') dataIdx++;

        const el = this._buildPanelElement(panel, panelIdx, pData);
        el.style.gridColumn = (gp.x + 1) + ' / span ' + (gp.w || 24);
        el.style.gridRow    = (gp.y + 1) + ' / span ' + (gp.h || 8);
        gridEl.appendChild(el);
      });

      const nonRowYs = panels
        .filter(p => p.type !== 'row')
        .map(p => (p.gridPos || { y: 0 }).y);
      const yBands = [...new Set(nonRowYs)].sort((a, b) => a - b);
      if (yBands.length > 2) {
        const cutoffY = yBands[2];
        const maxH = 20 + cutoffY * 30 + Math.max(0, cutoffY - 1) * 6;
        gridEl.style.maxHeight = maxH + 'px';
        gridEl.style.overflow  = 'hidden';
        gridEl.style.position  = 'relative';
        gridEl.dataset.gbClampH = maxH + 'px';

        const fade = document.createElement('div');
        fade.className = 'gb-canvas-fade';
        gridEl.appendChild(fade);

        const totalPanels = panels.filter(p => p.type !== 'row').length;
        const showMoreBar = document.createElement('div');
        showMoreBar.className = 'gb-show-more-bar';
        showMoreBar.innerHTML =
          '<button type="button" class="gb-btn gb-btn-secondary gb-show-more-btn"' +
          ' onclick="GrafanaBoard._toggleExpand(this)"' +
          ' data-show-label="Alle ' + totalPanels + ' Panels anzeigen">' +
            '<i class="bi bi-chevron-down"></i> Alle ' + totalPanels + ' Panels anzeigen' +
          '</button>';
        wrapper.appendChild(showMoreBar);
      }

      requestAnimationFrame(() => requestAnimationFrame(() => {
        let dIdx = 0;
        panels.forEach((panel, panelIdx) => {
          if (panel.type === 'row') return;
          const canvas = document.getElementById('gb-c-' + panelIdx);
          if (canvas) {
            this._renderChart(canvas, panel, panelData[dIdx] || null);
          }
          dIdx++;
        });
      }));
    },

    _updateChartsWithData(panelData) {
      if (!this._dashObj) return;
      const panels = this._dashObj.panels || [];
      let dIdx = 0;
      panels.forEach((panel, panelIdx) => {
        if (panel.type === 'row') return;
        const pData = panelData[dIdx] || null;

        if (panel.type === 'stat' || panel.type === 'singlestat') {
          const val   = this._extractStatValue(pData);
          const color = this._resolveThresholdColor(panel, val);
          const valueEl = document.querySelector('[data-panel-idx="' + panelIdx + '"] .gb-stat-value');
          if (valueEl) { valueEl.textContent = String(val); valueEl.style.color = color; }
        } else {
          const canvas = document.getElementById('gb-c-' + panelIdx);
          if (canvas) this._renderChart(canvas, panel, pData);
        }
        dIdx++;
      });
    },

    _buildPanelElement(panel, panelIdx, pData) {
      const el = document.createElement('div');

      if (panel.type === 'row') {
        el.className = 'gb-panel gb-panel-row';
        el.innerHTML =
          '<div class="gb-panel-header">' +
            '<i class="bi bi-dash-lg" style="color:#4a5060;flex-shrink:0;font-size:10px;"></i>' +
            '<span class="gb-panel-title">' + escHtml(panel.title || '') + '</span>' +
          '</div>';
        return el;
      }

      el.className = 'gb-panel';
      el.title = panel.title || '';
      el.setAttribute('data-panel-idx', panelIdx);
      el.addEventListener('click', () => GrafanaBoard._openModal(panelIdx));
      this._initDragDrop(el, panelIdx);
      this._initContextMenu(el, panelIdx);

      const headerHtml =
        '<div class="gb-panel-header">' +
          '<span class="gb-panel-title">' + escHtml(panel.title || 'Panel') + '</span>' +
          '<span class="gb-panel-type-badge">' + escHtml(panel.type || '') + '</span>' +
          '<button class="gb-panel-edit-btn" title="JSON bearbeiten"' +
                  ' onclick="event.stopPropagation();GrafanaBoard._openModal(' + panelIdx + ')">' +
            '<i class="bi bi-pencil"></i>' +
          '</button>' +
          '<button class="gb-panel-delete-btn" title="Panel löschen"' +
                  ' onclick="event.stopPropagation();GrafanaBoard._deletePanel(' + panelIdx + ')">' +
            '<i class="bi bi-trash3"></i>' +
          '</button>' +
        '</div>';

      const type = panel.type;

      if (type === 'stat' || type === 'singlestat') {
        const val   = this._extractStatValue(pData);
        const color = this._resolveThresholdColor(panel, val);
        el.innerHTML = headerHtml +
          '<div class="gb-stat-body gb-panel-body">' +
            '<div class="gb-stat-value" style="color:' + color + '">' + escHtml(String(val)) + '</div>' +
            (pData?.unit
              ? '<div class="gb-stat-unit">' + escHtml(pData.unit) + '</div>'
              : '') +
            (pData?.datasets?.[0]?.label
              ? '<div class="gb-stat-label">' + escHtml(pData.datasets[0].label) + '</div>'
              : '') +
          '</div>';
      } else if (type === 'text') {
        el.innerHTML = headerHtml +
          '<div class="gb-text-body">' +
            (panel.options?.content || panel.content || '<em style="opacity:.4">Kein Inhalt</em>') +
          '</div>';
      } else {
        el.innerHTML = headerHtml +
          '<div class="gb-panel-body">' +
            '<div class="gb-chart-container"><canvas id="gb-c-' + panelIdx + '"></canvas></div>' +
          '</div>';
      }

      return el;
    },

};
