export const LiveMixin = {

    _seedClientData() {
      if (!this._dashObj) return [];
      const panels = this._dashObj.panels || [];
      const result = [];
      const COUNT  = 20;
      const now    = new Date();

      const makeLabels = () => {
        const lbls = [];
        for (let i = COUNT - 1; i >= 0; i--) {
          const d = new Date(now.getTime() - i * 5000);
          lbls.push(
            d.getHours().toString().padStart(2, '0') + ':' +
            d.getMinutes().toString().padStart(2, '0') + ':' +
            d.getSeconds().toString().padStart(2, '0')
          );
        }
        return lbls;
      };

      const makeWalk = (startVal, min, max) => {
        const data = [];
        let v = startVal;
        const range = (max != null && max > min) ? (max - min) : Math.max(Math.abs(v) * 2, 100);
        for (let i = 0; i < COUNT; i++) {
          v += (Math.random() - 0.5) * 2 * range * 0.08;
          if (v < min) v = min + Math.random() * range * 0.1;
          if (max != null && v > max) v = max - Math.random() * range * 0.1;
          data.push(Math.round(v * 100) / 100);
        }
        return data;
      };

      panels.forEach(panel => {
        if (panel.type === 'row') return;
        const type    = panel.type;
        const min     = panel.fieldConfig?.defaults?.min ?? 0;
        const max     = panel.fieldConfig?.defaults?.max ?? null;
        const targets = panel.targets || [];
        const hi      = max ?? 100;
        const startV  = () => min + Math.random() * (hi - min);

        if (type === 'stat' || type === 'singlestat') {
          const v = startV();
          result.push({ datasets: [{ label: targets[0]?.legendFormat || panel.title || 'Wert', data: [v] }] });
          return;
        }

        if (type === 'gauge') {
          const v = startV();
          result.push({ datasets: [{ label: panel.title || 'Wert', data: [v] }] });
          return;
        }

        if (type === 'piechart') {
          const numSlices = Math.max(2, Math.min(6, targets.length || 3));
          const datasets  = [];
          for (let i = 0; i < numSlices; i++) {
            const v = 50 + Math.random() * 200;
            datasets.push({ label: targets[i]?.legendFormat || ('Slice ' + (i + 1)), data: [v] });
          }
          result.push({ labels: datasets.map(d => d.label), datasets });
          return;
        }

        const numSeries = Math.max(1, targets.length || 1);
        const labels    = makeLabels();
        const datasets  = [];
        for (let i = 0; i < numSeries; i++) {
          datasets.push({
            label: targets[i]?.legendFormat || (numSeries > 1 ? ('Series ' + (i + 1)) : (panel.title || 'Wert')),
            data:  makeWalk(startV(), min, max),
          });
        }
        result.push({ labels, datasets });
      });

      return result;
    },

    _startLive() {
      this._stopLive();
      this._liveTimer = setInterval(() => this._tickLive(), this.LIVE_INTERVAL_MS);
    },

    _stopLive() {
      if (this._liveTimer !== null) {
        clearInterval(this._liveTimer);
        this._liveTimer = null;
      }
    },

    _tickLive() {
      if (!this._dashObj) return;
      const panels = this._dashObj.panels || [];
      const now   = new Date();
      const newLabel = now.getHours().toString().padStart(2,'0') + ':'
                     + now.getMinutes().toString().padStart(2,'0') + ':'
                     + now.getSeconds().toString().padStart(2,'0');

      let dIdx = 0;
      panels.forEach((panel, panelIdx) => {
        if (panel.type === 'row') return;
        const pData = this._panelApiData[dIdx] || null;
        dIdx++;

        if (!pData) return;

        const type = panel.type;

        if (type === 'stat' || type === 'singlestat') {
          if (pData.datasets?.length) {
            const ds = pData.datasets[0];
            const last = ds.data[ds.data.length - 1] ?? 50;
            const min  = panel.fieldConfig?.defaults?.min ?? 0;
            const max  = panel.fieldConfig?.defaults?.max ?? 100;
            const step = (max - min) * 0.06;
            const next = Math.max(min, Math.min(max, last + (Math.random() - 0.5) * 2 * step));
            const rounded = Math.round(next * 100) / 100;
            ds.data.push(rounded);
            if (ds.data.length > 20) ds.data.shift();
            const val   = this._extractStatValue(pData);
            const color = this._resolveThresholdColor(panel, val);
            const el    = document.querySelector('[data-panel-idx="' + panelIdx + '"] .gb-stat-value');
            if (el) { el.textContent = String(val); el.style.color = color; }
          }
          return;
        }

        if (type === 'gauge' || type === 'piechart') {
          const id    = 'gb-c-' + panelIdx;
          const chart = this._charts.get(id);
          if (!chart) return;
          if (type === 'gauge') {
            const ds   = pData.datasets?.[0];
            if (!ds) return;
            const last  = ds.data[ds.data.length - 1] ?? 50;
            const min   = panel.fieldConfig?.defaults?.min ?? 0;
            const max   = panel.fieldConfig?.defaults?.max ?? 100;
            const step  = (max - min) * 0.05;
            const next  = Math.max(min, Math.min(max, last + (Math.random() - 0.5) * 2 * step));
            const val   = Math.round(next * 10) / 10;
            ds.data.push(val);
            if (ds.data.length > 20) ds.data.shift();
            const color = this._resolveThresholdColor(panel, val);
            chart.data.datasets[0].data = [
              Math.min(100, Math.max(0, val)),
              Math.max(0, 100 - Math.min(100, Math.max(0, val)))
            ];
            chart.data.datasets[0].backgroundColor[0] = color;
          } else {
            (pData.datasets || []).forEach((ds, i) => {
              const last = ds.data[ds.data.length - 1] ?? 100;
              const next = Math.max(1, last + (Math.random() - 0.5) * 20);
              ds.data.push(next);
              if (ds.data.length > 20) ds.data.shift();
              if (chart.data.datasets[0]?.data) {
                chart.data.datasets[0].data[i] = next;
              }
            });
          }
          chart.update('none');
          return;
        }

        const id    = 'gb-c-' + panelIdx;
        const chart = this._charts.get(id);
        if (!chart || !pData.labels) return;

        pData.labels.push(newLabel);
        if (pData.labels.length > 20) pData.labels.shift();
        chart.data.labels = pData.labels.slice();

        const min = panel.fieldConfig?.defaults?.min ?? 0;
        const max = panel.fieldConfig?.defaults?.max ?? null;

        (pData.datasets || []).forEach((ds, dsIdx) => {
          const last = ds.data[ds.data.length - 1] ?? 50;
          const range = max !== null ? (max - min) : Math.max(last * 2, 100);
          const step  = range * 0.08;
          let next = last + (Math.random() - 0.5) * 2 * step;
          next = Math.max(min, max !== null ? Math.min(max, next) : next);
          next = Math.round(next * 100) / 100;
          ds.data.push(next);
          if (ds.data.length > 20) ds.data.shift();
          if (chart.data.datasets[dsIdx]) {
            chart.data.datasets[dsIdx].data = ds.data.slice();
          }
        });

        chart.update('none');
      });

      if (this._editingIdx >= 0) {
        this._renderModalPreview();
      }
    },

    closeModal() {
      if (this._livePreviewTimer) {
        clearTimeout(this._livePreviewTimer);
        this._livePreviewTimer = null;
      }

      if (this._originalPanelJson !== null && this._editingIdx >= 0 && this._dashObj) {
        try {
          const orig = JSON.parse(this._originalPanelJson);
          this._dashObj.panels[this._editingIdx] = orig;
          this._updateSinglePanelInBoard(this._editingIdx, orig);
        } catch (_) {}
        this._originalPanelJson = null;
      }

      const modal = document.getElementById('gbPanelModal');
      if (modal) modal.classList.remove('is-open');
      if (this._savedScrollEls) {
        this._savedScrollEls.forEach(({ el, oy }) => { el.style.overflowY = oy; });
        this._savedScrollEls = null;
      }
      document.documentElement.style.overflow = '';
      document.body.style.overflow = '';
      this._destroyPreviewChart();
      this._editingIdx = -1;
    },

};
