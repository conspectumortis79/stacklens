
export const ContextmenuMixin = {

    _initContextMenu(el, panelIdx) {
      el.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this._showContextMenu(e.clientX, e.clientY, panelIdx);
      });
    },

    _showContextMenu(x, y, panelIdx) {
      this._hideContextMenu();

      const menu = document.createElement('div');
      menu.className = 'gb-context-menu';
      menu.id = 'gbContextMenu';
      menu.style.left = x + 'px';
      menu.style.top  = y + 'px';

      const deleteItem = document.createElement('div');
      deleteItem.className = 'gb-context-menu-item gb-context-menu-danger';
      deleteItem.innerHTML = '<i class="bi bi-trash3"></i> ' + (window.i18n ? window.i18n.t('grafana_board.delete_panel') : 'Panel l\u00f6schen');
      deleteItem.addEventListener('click', (e) => {
        e.stopPropagation();
        this._hideContextMenu();
        this._deletePanel(panelIdx);
      });
      menu.appendChild(deleteItem);

      document.body.appendChild(menu);
      this._contextMenu = menu;

      const rect = menu.getBoundingClientRect();
      if (rect.right  > window.innerWidth)  menu.style.left = (x - rect.width)  + 'px';
      if (rect.bottom > window.innerHeight) menu.style.top  = (y - rect.height) + 'px';

      const closeMenu = (e) => {
        if (!menu.contains(e.target)) {
          this._hideContextMenu();
          document.removeEventListener('click',       closeMenu,  true);
          document.removeEventListener('contextmenu', closeMenu,  true);
          document.removeEventListener('keydown',     closeOnEsc);
        }
      };
      const closeOnEsc = (e) => {
        if (e.key === 'Escape') {
          this._hideContextMenu();
          document.removeEventListener('click',       closeMenu,  true);
          document.removeEventListener('contextmenu', closeMenu,  true);
          document.removeEventListener('keydown',     closeOnEsc);
        }
      };
      setTimeout(() => {
        document.addEventListener('click',       closeMenu,  true);
        document.addEventListener('contextmenu', closeMenu,  true);
        document.addEventListener('keydown',     closeOnEsc);
      }, 0);
    },

    _hideContextMenu() {
      const existing = document.getElementById('gbContextMenu');
      if (existing) existing.remove();
      this._contextMenu = null;
    },

    _addPanel() {
      if (!this._dashObj) return;
      const panels = this._dashObj.panels || [];

      let maxY = 0;
      panels.forEach(p => {
        const gp = p.gridPos || {};
        const bottom = (gp.y || 0) + (gp.h || 8);
        if (bottom > maxY) maxY = bottom;
      });

      const newPanel = {
        id: Date.now(),
        title: 'Neues Panel',
        type: 'timeseries',
        gridPos: { x: 0, y: maxY, w: 12, h: 8 },
        targets: [{ legendFormat: 'Series A', refId: 'A' }],
        fieldConfig: { defaults: {}, overrides: [] },
        options: {},
      };

      panels.push(newPanel);
      this._dashObj.panels = panels;
      this._syncJsonFromDash();

      this._panelApiData.push({
        labels: ['1','2','3','4','5'],
        datasets: [{ label: 'Series A', data: [0, 0, 0, 0, 0] }],
      });

      const newIdx = panels.length - 1;
      const cached = this._panelApiData.slice();
      this.renderFromTextarea(cached).then(() => {
        GrafanaBoard._openModal(newIdx);
      });
    },

    _deletePanel(panelIdx) {
      if (!this._dashObj) return;
      const panels = this._dashObj.panels || [];
      if (panelIdx < 0 || panelIdx >= panels.length) return;

      const isRow = (panels[panelIdx].type === 'row');
      if (!isRow) {
        let dIdx = 0;
        for (let i = 0; i < panelIdx; i++) {
          if ((panels[i].type || '') !== 'row') dIdx++;
        }
        if (dIdx < this._panelApiData.length) {
          this._panelApiData.splice(dIdx, 1);
        }
      }

      panels.splice(panelIdx, 1);
      this._dashObj.panels = panels;

      this._compactPanelPositions(panels);

      this._syncJsonFromDash();

      const cached = this._panelApiData.length ? this._panelApiData.slice() : null;
      this.renderFromTextarea(cached);
      if (window.showAlert) window.showAlert(window.i18n ? window.i18n.t('grafana_analysis.js_panel_deleted') : 'Panel gelöscht.', 'success');
    },

    _compactPanelPositions(panels) {
      const ys = [...new Set(
        panels
          .filter(p => p.type !== 'row' && p.gridPos)
          .map(p => p.gridPos.y || 0)
      )].sort((a, b) => a - b);

      const yMap = new Map();
      let cursor = 0;
      ys.forEach(y => {
        yMap.set(y, cursor);
        const rowH = Math.max(8, ...panels
          .filter(p => p.type !== 'row' && p.gridPos && (p.gridPos.y || 0) === y)
          .map(p => p.gridPos.h || 8));
        cursor += rowH;
      });

      panels.forEach(p => {
        if (p.type === 'row' || !p.gridPos) return;
        const newY = yMap.get(p.gridPos.y || 0);
        if (newY !== undefined) p.gridPos.y = newY;
      });
    },

    _syncJsonFromDash() {
      if (!this._dashObj) return;
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
      const jsonStr = JSON.stringify(fullObj, null, 2);
      const ta = document.getElementById('inputDashboardJson');
      if (ta) {
        ta.value = jsonStr;
        ta.dispatchEvent(new Event('input'));
      }
      this._rawJson = jsonStr;
    },

    _destroyPreviewChart() {
      if (this._previewChart) {
        try { this._previewChart.destroy(); } catch (_) {}
        this._previewChart = null;
      }
    },
};
