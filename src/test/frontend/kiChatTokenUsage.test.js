import { afterEach, describe, expect, it, vi } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const DRAWER = resolve(
  __dirname,
  '../../main/resources/templates/fragments/layout/ki-chat-drawer.html',
);
const CHAT_SCRIPT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/layout/scripts-ki-chat.html',
);
const SEND_SCRIPT = resolve(
  __dirname,
  '../../main/resources/templates/fragments/layout/scripts-ki-chat-send.html',
);
const THEMES_DIR = resolve(__dirname, '../../main/resources/static/css/themes');

function extractFirstScript(path) {
  const source = readFileSync(path, 'utf8');
  const match = source.match(/<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/i);
  if (!match) throw new Error(`No inline script found in ${path}`);
  return match[1];
}

function bootUsageUi(options = {}) {
  const dom = new JSDOM(
    '<!doctype html><html data-theme="modern"><body>' +
      '<div id="kiChatDrawer" class="d-none">' +
        '<div id="kiChatDragHandle"></div>' +
        '<section id="kiChatUsage">' +
          '<span id="kiChatUsageState"></span>' +
          '<strong id="kiChatUsageInput">0</strong>' +
          '<strong id="kiChatUsageOutput">0</strong>' +
          '<strong id="kiChatUsageTotal">0</strong>' +
          '<strong id="kiChatUsageDuration">0</strong>' +
        '</section>' +
        '<div id="kiChatMessagesWrap"><div id="kiChatMessages"></div></div>' +
        '<div id="kiChatSuggestions"></div>' +
        '<div><textarea id="kiChatInput"></textarea><button id="kiChatSendBtn"></button></div>' +
      '</div>' +
    '</body></html>',
    {
      runScripts: 'dangerously',
      pretendToBeVisual: true,
      url: 'http://localhost/jwt-analysis',
    },
  );
  const win = dom.window;
  win.i18n = {
    t: (key) => ({
      'ki_chat.usage.current': 'Aktuell',
      'ki_chat.usage.live': 'Live',
      'ki_chat.default_intro': 'Hallo',
    }[key] || key),
    getLang: () => 'de',
    tList: () => [],
  };
  win.__kiChatEsc = (value) => String(value);
  if (options.tokenUsageEnabled === false) {
    win.localStorage.setItem('dev_show_token_usage', 'false');
  } else if (options.tokenUsageEnabled === true) {
    win.localStorage.setItem('dev_show_token_usage', 'true');
  }
  win.fetch = vi.fn(() => Promise.resolve({
    ok: true,
    json: () => Promise.resolve({
      promptTokens: 2184,
      completionTokens: 736,
      totalTokens: 2920,
      durationMs: 14800,
    }),
  }));

  const script = win.document.createElement('script');
  script.textContent = extractFirstScript(CHAT_SCRIPT);
  win.document.body.appendChild(script);
  script.remove();

  return win;
}

describe('KI chat token-usage display', () => {
  let win;

  afterEach(() => {
    if (win) {
      try { win.close(); } catch (_) {}
      win = null;
    }
  });

  it('renders the four approved compact metrics without widening the drawer', () => {
    const source = readFileSync(DRAWER, 'utf8');
    const dom = new JSDOM(source);
    const drawer = dom.window.document.getElementById('kiChatDrawer');
    const panel = dom.window.document.getElementById('kiChatUsage');

    expect(drawer).not.toBeNull();
    expect(drawer.getAttribute('style')).toContain('width:min(748px,100vw)');
    expect(panel).not.toBeNull();
    expect(panel.querySelectorAll('.ki-chat-usage-metric')).toHaveLength(4);
    expect(dom.window.document.getElementById('kiChatUsageInput')).not.toBeNull();
    expect(dom.window.document.getElementById('kiChatUsageOutput')).not.toBeNull();
    expect(dom.window.document.getElementById('kiChatUsageTotal')).not.toBeNull();
    expect(dom.window.document.getElementById('kiChatUsageDuration')).not.toBeNull();
    dom.window.close();
  });

  it.each(['modern', 'legacy', 'dark', 'popos'])(
    'contains an explicit contrast-safe %s theme palette',
    (theme) => {
      const source = readFileSync(resolve(THEMES_DIR, `theme-${theme}.css`), 'utf8');
      expect(source).toContain(`html[data-theme="${theme}"] #kiChatUsage`);
      expect(source).toContain(`html[data-theme="${theme}"] .ki-chat-usage-metric--total`);
    },
  );

  it.each(['modern', 'legacy', 'dark', 'popos'])(
    'theme-%s.css defines the full KI-Chat CSS variable palette',
    (theme) => {
      const source = readFileSync(resolve(THEMES_DIR, `theme-${theme}.css`), 'utf8');
      const requiredVars = [
        '--ki-md-h1-fg',
        '--ki-md-h2-fg',
        '--ki-md-h2-border',
        '--ki-md-h3-fg',
        '--ki-md-h4-fg',
        '--ki-md-bq-fg',
        '--ki-md-bq-bg',
        '--ki-md-bq-border',
        '--ki-md-hr-border',
        '--ki-md-ic-bg',
        '--ki-md-ic-fg',
        '--ki-md-tbl-border',
        '--ki-md-tbl-th-bg',
        '--ki-md-tbl-th-fg',
        '--ki-md-tbl-td-bg',
        '--ki-md-tbl-hover-bg',
        '--ki-source-chip-border',
        '--ki-source-chip-bg',
        '--ki-source-chip-fg',
        '--ki-source-chip-hover-bg',
        '--ki-source-chip-icon',
        '--ki-source-chip-score-bg',
        '--ki-copy-border',
        '--ki-copy-fg',
        '--ki-copy-hover-bg',
        '--ki-sources-panel-bg',
        '--ki-sources-panel-border',
      ];
      for (const v of requiredVars) {
        expect(
          source.includes(`${v}:`),
          `theme-${theme}.css does not define ${v}. ` +
            `The KI-Chat will fall back to the Modern-theme purple-on-light ` +
            `defaults, which is unreadable on the dark backgrounds of ` +
            `'dark' and 'popos'.`,
        ).toBe(true);
      }
    },
  );

  it('ki-chat-drawer.html declares every referenced kiChatPulse* keyframes', () => {
    const drawer = readFileSync(DRAWER, 'utf8');
    for (const variant of ['kiChatPulse', 'kiChatPulseLight', 'kiChatPulseDark', 'kiChatPulsePopos']) {
      expect(drawer, `Missing @keyframes ${variant} declaration`).toContain(
        `@keyframes ${variant}`,
      );
    }
  });

  it('ki-chat-drawer.html contains no selectors for undeclared midnight/noir themes', () => {
    const drawer = readFileSync(DRAWER, 'utf8');
    expect(drawer).not.toMatch(/html\[data-theme="(?:midnight|noir)"\]/);
  });

  it.each(['modern', 'legacy', 'dark', 'popos'])(
    'theme-%s.css overrides #kiChatDragHandle',
    (theme) => {
      const source = readFileSync(resolve(THEMES_DIR, `theme-${theme}.css`), 'utf8');
      expect(
        source.match(/html\[data-theme="(?:dark|popos|legacy|modern)"\] #kiChatDragHandle\s*\{[^}]*background[^}]*\}/),
        `theme-${theme}.css does not override #kiChatDragHandle background. ` +
          `The drawer's inline lila-Gradient (#7c5cbf22 → #5b8ef012) will leak through ` +
          `and the header stays beige/lila regardless of the active theme.`,
      ).not.toBeNull();
    },
  );

  it('hides the complete panel and skips usage requests when the developer option is off', async () => {
    win = bootUsageUi({ tokenUsageEnabled: false });
    const tabId = 'url:/jwt-analysis';
    win.__kiChatLoadedTabId = tabId;

    win.__kiChatRenderUsage(tabId);
    await win.__kiChatFetchUsage(tabId);

    const panel = win.document.getElementById('kiChatUsage');
    expect(panel.style.display).toBe('none');
    expect(panel.getAttribute('aria-hidden')).toBe('true');
    expect(win.fetch).not.toHaveBeenCalled();
  });

  it('reacts immediately to the configuration toggle event in the same browser tab', async () => {
    win = bootUsageUi({ tokenUsageEnabled: true });
    const tabId = 'url:/jwt-analysis';
    win.__kiChatLoadedTabId = tabId;
    win.__kiChatRenderUsage(tabId);
    const panel = win.document.getElementById('kiChatUsage');
    expect(panel.style.display).toBe('');

    win.localStorage.setItem('dev_show_token_usage', 'false');
    win.document.dispatchEvent(new win.CustomEvent('cf:dev-toggle-changed', {
      detail: { key: 'dev_show_token_usage' },
    }));
    expect(panel.style.display).toBe('none');

    win.localStorage.setItem('dev_show_token_usage', 'true');
    win.document.dispatchEvent(new win.CustomEvent('cf:dev-toggle-changed', {
      detail: { key: 'dev_show_token_usage' },
    }));
    await Promise.resolve();

    expect(panel.style.display).toBe('');
    expect(panel.getAttribute('aria-hidden')).toBe('false');
    expect(win.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/ai-usage/kichat/'),
      { credentials: 'same-origin' },
    );
  });

  it('reacts to a storage event from a configuration page in another browser tab', () => {
    win = bootUsageUi({ tokenUsageEnabled: true });
    const tabId = 'url:/jwt-analysis';
    win.__kiChatLoadedTabId = tabId;
    win.__kiChatRenderUsage(tabId);
    const panel = win.document.getElementById('kiChatUsage');
    expect(panel.style.display).toBe('');

    win.localStorage.setItem('dev_show_token_usage', 'false');
    win.dispatchEvent(new win.StorageEvent('storage', {
      key: 'dev_show_token_usage',
      newValue: 'false',
    }));

    expect(panel.style.display).toBe('none');
    expect(panel.getAttribute('aria-hidden')).toBe('true');
  });

  it('loads and formats the aggregate belonging to the current tab chat', async () => {
    win = bootUsageUi();
    const tabId = 'url:/jwt-analysis';
    const tab = win.__kiChatGetTab(tabId);
    win.__kiChatLoadedTabId = tabId;

    await win.__kiChatFetchUsage(tabId);

    expect(tab.usageId).toBeGreaterThan(0);
    expect(win.fetch).toHaveBeenCalledWith(
      `/api/ai-usage/kichat/${tab.usageId}`,
      { credentials: 'same-origin' },
    );
    expect(win.document.getElementById('kiChatUsageInput').textContent).toMatch(/2[.\s]?184/);
    expect(win.document.getElementById('kiChatUsageOutput').textContent).toBe('736');
    expect(win.document.getElementById('kiChatUsageTotal').textContent).toMatch(/2[.\s]?920/);
    expect(win.document.getElementById('kiChatUsageDuration').textContent).toBe('14,8 s');
    expect(win.document.getElementById('kiChatUsage').getAttribute('data-ki-chat-usage-id')).toBe(String(tab.usageId));
  });

  it('persists one usage id per tab and isolates different chats', () => {
    win = bootUsageUi();
    const first = win.__kiChatGetTab('url:/jwt-analysis').usageId;
    const other = win.__kiChatGetTab('url:/code-analysis').usageId;

    delete win.__kiChatTabs['url:/jwt-analysis'];
    const restored = win.__kiChatGetTab('url:/jwt-analysis').usageId;

    expect(first).toBeGreaterThan(0);
    expect(restored).toBe(first);
    expect(other).not.toBe(first);
  });

  it('clear history resets the visible aggregate and removes old usage rows', async () => {
    win = bootUsageUi();
    const tabId = 'url:/jwt-analysis';
    const tab = win.__kiChatGetTab(tabId);
    const oldUsageId = tab.usageId;
    tab.usage = { promptTokens: 10, completionTokens: 5, totalTokens: 15, durationMs: 900 };
    tab.usageLoaded = true;
    win.__kiChatLoadedTabId = tabId;
    win.__kiChatRenderUsage(tabId);

    win.kiChatClear();
    await Promise.resolve();

    expect(tab.usageId).not.toBe(oldUsageId);
    expect(tab.usage.totalTokens).toBe(0);
    expect(win.document.getElementById('kiChatUsageTotal').textContent).toBe('0');
    expect(win.fetch).toHaveBeenCalledWith(
      `/api/ki-chat/usage/${oldUsageId}`,
      { method: 'DELETE', credentials: 'same-origin' },
    );
  });

  it('sends the per-chat usage id with every streaming request', () => {
    const source = readFileSync(SEND_SCRIPT, 'utf8');
    expect(source).toMatch(/chatId:\s*t\.usageId/);
    expect(source).toContain('__kiChatStartUsagePolling(sendTabId)');
    expect(source).toContain('__kiChatFetchUsage(sendTabId)');
  });
});
