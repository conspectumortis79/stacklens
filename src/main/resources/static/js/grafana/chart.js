import { escHtml, GRAFANA_COLORS, resolveColor, getPanelFixedColor, thresholdColorForValue } from "./helpers.js";

export const ChartMixin = {

    _renderChart(canvas, panel, pData) {
      if (!canvas || typeof Chart === 'undefined') return;

      const config = this._buildChartConfig(panel, pData, false);
      if (!config) {
        const body = canvas.closest('.gb-panel-body');
        if (body) body.innerHTML = '<div class="gb-no-data">' + (window.i18n ? window.i18n.t('grafana_board.no_preview') : 'Vorschau nicht verf\u00fcgbar') + '</div>';
        return;
      }

      const id = canvas.id;
      if (this._charts.has(id)) {
        try { this._charts.get(id).destroy(); } catch (_) {}
      }
      try {
        this._charts.set(id, new Chart(canvas, config));
      } catch (e) {
        console.warn('[GrafanaBoard] Chart error for panel "' + panel.title + '":', e);
      }
    },

    _buildChartConfig(panel, pData, largeView) {
      const type = panel.type;
      const C    = { muted: '#8e9bae', grid: '#232830', blue: '#5794f2', green: '#73bf69' };
      const fs   = largeView ? 11 : 9;

      const baseOptions = {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: {
          legend: {
            labels: { color: C.muted, font: { size: fs + 1 }, boxWidth: 10, padding: 8 },
          },
        },
      };

      const scaleOpts = () => ({
        x: {
          ticks: { color: C.muted, font: { size: fs }, maxTicksLimit: largeView ? 12 : 7 },
          grid:  { color: C.grid },
        },
        y: {
          ticks: { color: C.muted, font: { size: fs } },
          grid:  { color: C.grid },
        },
      });

      if (type === 'gauge' || type === 'piechart') {
        const ds    = pData?.datasets?.[0] || {};
        const vals  = ds.data || [];
        const val   = vals.length ? vals[vals.length - 1] : 0;
        const color = getPanelFixedColor(panel) || this._resolveThresholdColor(panel, val);

        if (type === 'gauge') {
          return {
            type: 'doughnut',
            data: {
              labels: [ds.label || 'Wert', ''],
              datasets: [{
                data: [Math.min(100, Math.max(0, val)), Math.max(0, 100 - Math.min(100, Math.max(0, val)))],
                backgroundColor: [color, '#2a2d36'],
                borderWidth: 0,
              }],
            },
            options: {
              ...baseOptions,
              cutout: '72%',
              plugins: {
                legend: { display: false },
                tooltip: { callbacks: { label: ctx => ' ' + ctx.parsed.toFixed(1) + (pData?.unit || '') } },
              },
            },
          };
        } else {
          const labels   = pData?.labels || (pData?.datasets || []).map((d, i) => panel.targets?.[i]?.legendFormat || d.label || '');
          const values   = (pData?.datasets || []).map(d => (d.data?.[d.data.length - 1] ?? 0));
          const colors   = (pData?.datasets || []).map((d, i) => resolveColor(d.color) || (['#5794f2','#73bf69','#fade2a','#f2495c','#b877d9'][i % 5]));
          return {
            type: 'pie',
            data: {
              labels,
              datasets: [{ data: values, backgroundColor: colors, borderWidth: 0 }],
            },
            options: { ...baseOptions },
          };
        }
      }

      if (type === 'bargauge' || type === 'barchart' || type === 'bar') {
        var panelBarColor = getPanelFixedColor(panel);
        return {
          type: 'bar',
          data: {
            labels: pData?.labels || [],
            datasets: (pData?.datasets || []).map((ds, i) => ({
              label:           panel.targets?.[i]?.legendFormat || ds.label,
              data:            ds.data,
              backgroundColor: resolveColor(ds.color) || panelBarColor || (['#5794f2','#73bf69','#fade2a','#f2495c'][i % 4]),
              borderRadius:    2,
              borderWidth:     0,
            })),
          },
          options: { ...baseOptions, scales: scaleOpts() },
        };
      }

      if (['timeseries', 'graph', 'line', 'area', 'heatmap', 'histogram'].includes(type) ||
          !['stat', 'singlestat', 'text', 'row', 'table', 'news', 'alertlist'].includes(type)) {
        var panelLineColor = getPanelFixedColor(panel);
        const fill = panel.fieldConfig?.defaults?.custom?.fillOpacity > 0 ||
                     type === 'area';
        return {
          type: 'line',
          data: {
            labels: pData?.labels || [],
            datasets: (pData?.datasets || []).map((ds, i) => {
              const col   = resolveColor(ds.color) || panelLineColor || (['#5794f2','#73bf69','#fade2a','#ff9830','#b877d9'][i % 5]);
              const label = panel.targets?.[i]?.legendFormat || ds.label;
              return {
                label:           label,
                data:            ds.data,
                borderColor:     col,
                backgroundColor: col + '20',
                borderWidth:     1.5,
                pointRadius:     0,
                fill:            fill,
                tension:         0.35,
              };
            }),
          },
          options: { ...baseOptions, scales: scaleOpts() },
        };
      }

      return null;
    },

    _extractStatValue(pData) {
      if (!pData?.datasets?.length) return '—';
      const data = pData.datasets[0].data;
      if (!data?.length) return '—';
      const v = data[data.length - 1];
      return typeof v === 'number' ? +v.toFixed(2) : '—';
    },

    _resolveThresholdColor(panel, val) {
      var fixedColor = getPanelFixedColor(panel);
      if (fixedColor) return fixedColor;
      const thresholds = panel.fieldConfig?.defaults?.thresholds;
      if (!thresholds) return GRAFANA_COLORS.green;
      const numVal = parseFloat(val);
      if (isNaN(numVal)) return GRAFANA_COLORS.green;
      return thresholdColorForValue(numVal, thresholds);
    },

};
