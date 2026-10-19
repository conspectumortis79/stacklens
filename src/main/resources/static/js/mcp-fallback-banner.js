(function () {
  'use strict';

  const POLL_INTERVAL_MS = 30000;
  const COOLDOWN_SECONDS = 60;

  const PROVIDER_LABELS = {
    jira: 'Jira',
    confluence: 'Confluence',
    gitlab: 'GitLab',
    github: 'GitHub',
    grafana: 'Grafana',
    database: 'DB',
  };

  let pollTimer = null;

  function providerLabel(name) {
    if (!name) return 'unknown';
    const lower = String(name).toLowerCase();
    return PROVIDER_LABELS[lower] || name;
  }

  function openCircuits(status) {
    if (!status || !status.circuits) return [];
    return Object.values(status.circuits).filter((c) => c && c.isOpen);
  }

  function buildBannerText(circuits, mcpServerName) {
    const providers = circuits
      .map((c) => providerLabel(c.provider))
      .filter((v, i, a) => a.indexOf(v) === i)
      .sort();
    const list = providers.join(', ');
    const serverPart = mcpServerName ? ` (${mcpServerName})` : '';
    const anyProbe = circuits.some((c) => c.probing);
    const tail = anyProbe
      ? ` Retrying in the background — the native adapter will keep handling requests until the MCP server recovers.`
      : ` Native fallback is active. Next probe in up to ${COOLDOWN_SECONDS}s.`;
    return `MCP server${serverPart} unreachable for: ${list}.${tail} Open Connections → MCP to start or reconfigure it.`;
  }

  function show(bannerEl, textEl, text) {
    if (textEl.textContent !== text) {
      textEl.textContent = text;
    }
    if (bannerEl.classList.contains('d-none')) {
      bannerEl.classList.remove('d-none');
    }
  }

  function hide(bannerEl) {
    if (!bannerEl.classList.contains('d-none')) {
      bannerEl.classList.add('d-none');
    }
  }

  async function refresh(bannerEl, textEl) {
    let status = null;
    try {
      const res = await fetch('/api/mcp/status', { credentials: 'same-origin' });
      if (!res.ok) return;
      status = await res.json();
    } catch (e) {
      return;
    }
    const open = openCircuits(status);
    if (open.length === 0) {
      hide(bannerEl);
      return;
    }
    show(bannerEl, textEl, buildBannerText(open, status.mcpServerName));
  }

  function startPolling(bannerEl, textEl) {
    if (pollTimer !== null) return;
    const tick = () => {
      if (window._appVisible === false) return;
      refresh(bannerEl, textEl);
    };
    pollTimer = setInterval(tick, POLL_INTERVAL_MS);
  }

  function stopPolling() {
    if (pollTimer !== null) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  async function init() {
    const bannerEl = document.getElementById('mcpFallbackBanner');
    const textEl = document.getElementById('mcpFallbackBannerText');
    if (!bannerEl || !textEl) return;
    await refresh(bannerEl, textEl);
    startPolling(bannerEl, textEl);
  }

  document.addEventListener('app:navigate', function () {
    stopPolling();
  });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();