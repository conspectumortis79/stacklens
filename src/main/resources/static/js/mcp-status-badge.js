(function () {
  'use strict';

  const SOURCE_LABELS = {
    jira: 'Jira',
    confluence: 'Confluence',
    gitlab: 'GitLab',
    github: 'GitHub',
    grafana: 'Grafana',
    database: 'DB',
  };

  const SOURCE_ICONS = {
    jira: 'bi-kanban',
    confluence: 'bi-journal-text',
    gitlab: 'bi-git',
    github: 'bi-github',
    grafana: 'bi-bar-chart-line',
    database: 'bi-database',
  };

  function readSources() {
    const meta = document.querySelector('meta[name="mcp-sources"]');
    if (!meta) return [];
    return meta.content
      .split(',')
      .map((s) => s.trim().toLowerCase())
      .filter((s) => s && SOURCE_LABELS[s]);
  }

  function injectStyles() {
    if (document.getElementById('mcp-status-badge-style')) return;
    const css = document.createElement('style');
    css.id = 'mcp-status-badge-style';
    css.textContent = `
      .mcp-status-row {
        display: inline-flex; flex-wrap: wrap; gap: 6px;
        margin-top: 6px; align-items: center;
      }
      .mcp-status-chip {
        display: inline-flex; align-items: center; gap: 5px;
        padding: 2px 9px; border-radius: 999px;
        font-size: 11px; font-weight: 600; line-height: 1.6;
        border: 1px solid var(--app-border, #d8d2c5);
        background: var(--app-cream-2, #f5f1e8);
        color: var(--app-text-muted, #6b6357);
        white-space: nowrap;
      }
      .mcp-status-chip .mcp-dot {
        width: 7px; height: 7px; border-radius: 50%;
        background: var(--app-text-muted, #b9b1a1); flex-shrink: 0;
      }
      .mcp-status-chip.mcp-on {
        background: rgba(74, 222, 128, .14);
        border-color: rgba(74, 222, 128, .55);
        color: #1f7a3a;
      }
      .mcp-status-chip.mcp-on .mcp-dot {
        background: #4ade80;
        box-shadow: 0 0 0 0 rgba(74,222,128,.7);
        animation: mcp-status-pulse 1.8s ease-out infinite;
      }
      [data-theme="dark"] .mcp-status-chip.mcp-on { color: #6fe39a; }
      [data-theme="dark"] .mcp-status-chip {
        background: rgba(255,255,255,.04);
        border-color: rgba(255,255,255,.1);
        color: rgba(255,255,255,.65);
      }
      [data-theme="popos"] .mcp-status-chip {
        background: rgba(255,255,255,.05);
        border-color: rgba(255,255,255,.12);
        color: rgba(232,232,240,.75);
      }
      [data-theme="popos"] .mcp-status-chip.mcp-on {
        background: rgba(72,185,199,.18);
        border-color: rgba(72,185,199,.55);
        color: #7ed5e0;
      }
      [data-theme="popos"] .mcp-status-chip.mcp-on .mcp-dot {
        background: #48b9c7;
        animation-name: mcp-status-pulse-popos;
      }
      [data-theme="legacy"] .mcp-status-chip {
        background: #f8f9fa;
        border-color: #dee2e6;
        color: #495057;
      }
      [data-theme="legacy"] .mcp-status-chip.mcp-on {
        background: rgba(26,148,120,.12);
        border-color: rgba(26,148,120,.55);
        color: #126a5a;
      }
      [data-theme="legacy"] .mcp-status-chip.mcp-on .mcp-dot {
        background: #1a9478;
        animation-name: mcp-status-pulse-legacy;
      }
      @keyframes mcp-status-pulse {
        0%   { box-shadow: 0 0 0 0 rgba(74,222,128,.55); }
        70%  { box-shadow: 0 0 0 5px rgba(74,222,128,0); }
        100% { box-shadow: 0 0 0 0 rgba(74,222,128,0); }
      }
      @keyframes mcp-status-pulse-popos {
        0%   { box-shadow: 0 0 0 0 rgba(72,185,199,.55); }
        70%  { box-shadow: 0 0 0 5px rgba(72,185,199,0); }
        100% { box-shadow: 0 0 0 0 rgba(72,185,199,0); }
      }
      @keyframes mcp-status-pulse-legacy {
        0%   { box-shadow: 0 0 0 0 rgba(26,148,120,.55); }
        70%  { box-shadow: 0 0 0 5px rgba(26,148,120,0); }
        100% { box-shadow: 0 0 0 0 rgba(26,148,120,0); }
      }
    `;
    document.head.appendChild(css);
  }

  function renderChips(host, status, sources) {
    host.innerHTML = '';
    const routes = (status && status.routes) || {};
    const serverName = status && status.mcpServerName;
    const serverRunning = !!(status && status.processRunning);
    sources.forEach((src) => {
      const on = serverRunning && routes[src] === true;
      const chip = document.createElement('span');
      chip.className = 'mcp-status-chip' + (on ? ' mcp-on' : '');
      const tooltip = on
        ? `${SOURCE_LABELS[src]}: routed via MCP${serverName ? ' \u2014 ' + serverName : ''}`
        : `${SOURCE_LABELS[src]}: native config`;
      chip.title = tooltip;
      chip.setAttribute('aria-label', tooltip);

      const srcIcon = document.createElement('i');
      srcIcon.className = 'bi ' + (SOURCE_ICONS[src] || 'bi-circle');
      srcIcon.style.fontSize = '11px';

      const dot = document.createElement('span');
      dot.className = 'mcp-dot';

      const text = document.createElement('span');
      text.textContent = `${SOURCE_LABELS[src]}:`;

      const modeIcon = document.createElement('i');
      modeIcon.className = 'bi ' + (on ? 'bi-hdd-network-fill' : 'bi-hdd-fill');
      modeIcon.style.fontSize = '11px';
      modeIcon.style.marginLeft = '2px';

      const modeText = document.createElement('span');
      modeText.textContent = on ? 'MCP' : 'nativ';

      chip.appendChild(srcIcon);
      chip.appendChild(text);
      chip.appendChild(dot);
      chip.appendChild(modeIcon);
      chip.appendChild(modeText);
      host.appendChild(chip);
    });
  }

  function ensureHost() {
    let host = document.getElementById('mcpStatusBadge');
    if (host) return host;
    const header = document.querySelector('.sl-page-header');
    if (!header) return null;
    const subtitleHost = header.querySelector('div[style*="flex:1"]') || header;
    host = document.createElement('div');
    host.id = 'mcpStatusBadge';
    host.className = 'mcp-status-row';
    subtitleHost.appendChild(host);
    return host;
  }

  async function fetchStatus() {
    try {
      const res = await fetch('/api/mcp/status', { credentials: 'same-origin' });
      if (!res.ok) return null;
      return await res.json();
    } catch (e) {
      return null;
    }
  }

  function removeExistingHost() {
    const existing = document.getElementById('mcpStatusBadge');
    if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
  }

  let _lastSignature = null;
  function _signatureOf(status, sources) {
    const routes = (status && status.routes) || {};
    const running = !!(status && status.processRunning);
    const r = {};
    for (let i = 0; i < sources.length; i++) {
      const s = sources[i];
      r[s] = !!(running && routes[s] === true);
    }
    return running + '|' + sources.join(',') + '|' + JSON.stringify(r);
  }
  function _maybeRender(host, status, sources) {
    const sig = _signatureOf(status, sources);
    if (sig === _lastSignature) return;
    _lastSignature = sig;
    renderChips(host, status, sources);
  }

  const POLL_MS = 10000;
  let _pollTimer = null;
  function _stopPolling() {
    if (_pollTimer) { clearInterval(_pollTimer); _pollTimer = null; }
  }
  function _startPolling(host, sources) {
    _stopPolling();
    _pollTimer = setInterval(async () => {
      const liveHost = document.getElementById('mcpStatusBadge');
      if (!liveHost) return;
      const status = await fetchStatus();
      if (!status) return;
      _maybeRender(liveHost, status, sources);
    }, POLL_MS);
  }

  async function init() {
    removeExistingHost();
    const sources = readSources();
    if (sources.length === 0) {
      _stopPolling();
      return;
    }
    injectStyles();
    const host = ensureHost();
    if (!host) return;
    const status = await fetchStatus();
    _maybeRender(host, status, sources);
    _startPolling(host, sources);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  document.addEventListener('app:navigate', function () {
    init();
  });
})();
