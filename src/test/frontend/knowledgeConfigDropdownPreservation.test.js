import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { makeWindow, runInWindow, extractFunction } from './support/fragmentLoader.js';
import { readKnowledgeBundle } from './support/knowledgeModules.js';

const source = readKnowledgeBundle();

function boot() {
  const win = makeWindow(`
    <select id="kbConfigProvider">
      <option value="OLLAMA">Ollama</option>
      <option value="OPENAI">OpenAI</option>
      <option value="OPENROUTER">OpenRouter</option>
    </select>
    <input id="kbConfigOllamaUrl" value="" />
    <input id="kbConfigOpenRouterKey" value="" />
    <select id="kbConfigModel">
      <option value=""></option>
      <option value="qwen3-embedding:0.6b">qwen3-embedding:0.6b</option>
      <option value="bge-m3:567m">bge-m3:567m</option>
      <option value="text-embedding-3-small">text-embedding-3-small</option>
    </select>
    <button id="kbConfigSave"></button>
    <span id="kbConfigPendingBadge" class="d-none"></span>
    <button id="kbConfigModelRefresh"></button>
    <select id="kbConfigOllamaSection" class="d-none"></select>
    <select id="kbConfigOpenAiSection" class=""></select>
    <select id="kbConfigOpenRouterSection" class=""></select>
    <span id="kbMetricBases">0</span>
    <span id="kbMetricSources">0</span>
    <div id="kbMetricProvider"></div>
    <div id="kbMetricModel"></div>
    <span id="kbSourceCount">0</span>
    <table><tbody id="kbSourceRows"></tbody></table>
    <div id="kbBaseRows"></div>
    <span id="kbBaseCount">0</span>
  `);
  runInWindow(win, `
    window.i18n = { t: function (k, fallback) { return fallback || k; } };
    function esc(s) { return String(s == null ? '' : s); }
    function t(k, fallback) { return (window.i18n && window.i18n.t) ? window.i18n.t(k, fallback) : (fallback || k); }
    function kbNotify() {}
  `);
  win.fetch = vi.fn();
  runInWindow(win, extractFunction(source, 'kbApplySavedConfigToDropdowns'));
  runInWindow(win, extractFunction(source, 'kbMarkPendingChange'));
  runInWindow(win, extractFunction(source, 'kbSyncOverviewModel'));
  runInWindow(win, extractFunction(source, 'kbLoadOverview'));
  runInWindow(win, extractFunction(source, 'kbLoadConfig'));
  runInWindow(win, extractFunction(source, 'kbOnProviderChange'));
  runInWindow(win, extractFunction(source, 'kbRefreshModelList'));
  runInWindow(win, 'var kbSavedConfig = null;');
  return win;
}

function setConfig(win, provider, ollamaUrl, openRouterApiKey, model) {
  win.document.getElementById('kbConfigProvider').value = provider || '';
  win.document.getElementById('kbConfigOllamaUrl').value = ollamaUrl || '';
  win.document.getElementById('kbConfigOpenRouterKey').value = openRouterApiKey || '';
  win.document.getElementById('kbConfigModel').value = model || '';
}

describe('Wissensdatenbank Config — Dropdown preservation across reloads', () => {
  let win;
  afterEach(() => { if (win) try { win.close(); } catch (_) {} });

  it('updates the dropdown from the server when nothing is pending', () => {
    win = boot();
    setConfig(win, 'OLLAMA', 'http://old:11434', '', 'qwen3-embedding:0.6b');
    win.kbSavedConfig = {
      provider: 'OLLAMA',
      ollamaUrl: 'http://old:11434',
      openRouterApiKey: '',
      model: 'qwen3-embedding:0.6b',
    };

    win.kbApplySavedConfigToDropdowns({
      provider: 'OPENAI',
      ollamaUrl: 'http://new:11434',
      openRouterApiKey: 'sk-or-xyz',
      model: 'text-embedding-3-small',
    });

    expect(win.document.getElementById('kbConfigProvider').value).toBe('OPENAI');
    expect(win.document.getElementById('kbConfigOllamaUrl').value).toBe('http://new:11434');
    expect(win.document.getElementById('kbConfigOpenRouterKey').value).toBe('sk-or-xyz');
    expect(win.document.getElementById('kbConfigModel').value).toBe('text-embedding-3-small');
  });

  it('preserves the user-selected model when the server config differs (pending change)', () => {
    win = boot();
    setConfig(win, 'OLLAMA', '', '', 'bge-m3:567m');
    win.kbSavedConfig = {
      provider: 'OLLAMA',
      ollamaUrl: '',
      openRouterApiKey: '',
      model: 'qwen3-embedding:0.6b',
    };

    win.kbApplySavedConfigToDropdowns({
      provider: 'OLLAMA',
      ollamaUrl: '',
      openRouterApiKey: '',
      model: 'qwen3-embedding:0.6b',
    });

    expect(win.document.getElementById('kbConfigModel').value).toBe('bge-m3:567m');
  });

  it('preserves the user-selected provider but updates the untouched model field', () => {
    win = boot();
    setConfig(win, 'OPENAI', '', '', 'qwen3-embedding:0.6b');
    win.kbSavedConfig = {
      provider: 'OLLAMA',
      ollamaUrl: '',
      openRouterApiKey: '',
      model: 'qwen3-embedding:0.6b',
    };

    win.kbApplySavedConfigToDropdowns({
      provider: 'OLLAMA',
      ollamaUrl: '',
      openRouterApiKey: '',
      model: 'qwen3-embedding:0.6b',
    });

    expect(win.document.getElementById('kbConfigProvider').value).toBe('OPENAI');
    expect(win.document.getElementById('kbConfigModel').value).toBe('qwen3-embedding:0.6b');
  });

  it('kbLoadOverview does NOT overwrite the user-selected model after a base delete', async () => {
    win = boot();
    win.fetch = (url) => {
      if (String(url).endsWith('/api/knowledge/bases')) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve([{ id: 53 }]) });
      }
      if (String(url).endsWith('/api/knowledge/sources')) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
      }
      if (String(url).endsWith('/api/knowledge/config')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({
            provider: 'OLLAMA',
            ollamaUrl: '',
            openRouterApiKey: '',
            model: 'qwen3-embedding:0.6b',
          }),
        });
      }
      return Promise.reject(new Error('unexpected fetch: ' + url));
    };
    setConfig(win, 'OLLAMA', '', '', 'bge-m3:567m');
    win.kbSavedConfig = {
      provider: 'OLLAMA',
      ollamaUrl: '',
      openRouterApiKey: '',
      model: 'qwen3-embedding:0.6b',
    };

    win.kbLoadOverview();
    let p = Promise.resolve();
    for (let i = 0; i < 20; i++) p = p.then(() => {});
    await p;

    expect(win.document.getElementById('kbConfigModel').value).toBe('bge-m3:567m');
    expect(win.document.getElementById('kbConfigPendingBadge').classList.contains('d-none')).toBe(false);
  });

  it('populates the dropdown on first load when kbSavedConfig is null', () => {
    win = boot();
    setConfig(win, '', '', '', '');
    win.kbSavedConfig = null;

    win.kbApplySavedConfigToDropdowns({
      provider: 'OLLAMA',
      ollamaUrl: 'http://localhost:11434',
      openRouterApiKey: '',
      model: 'qwen3-embedding:0.6b',
    });

    expect(win.document.getElementById('kbConfigProvider').value).toBe('OLLAMA');
    expect(win.document.getElementById('kbConfigModel').value).toBe('qwen3-embedding:0.6b');
  });

  it('overwrites the HTML default with the server value when no previous saved config exists', () => {
    win = boot();
    win.kbSavedConfig = null;
    win.document.getElementById('kbConfigProvider').value = 'OLLAMA';
    win.document.getElementById('kbConfigOllamaUrl').value = '';
    win.document.getElementById('kbConfigOpenRouterKey').value = '';
    win.document.getElementById('kbConfigModel').value = '';

    const cfg = {
      provider: 'OPENROUTER',
      ollamaUrl: '',
      openRouterApiKey: 'sk-or-xyz',
      model: 'text-embedding-3-small',
    };
    win.kbApplySavedConfigToDropdowns(cfg);
    win.kbSavedConfig = cfg;

    expect(win.document.getElementById('kbConfigProvider').value).toBe('OPENROUTER');
    expect(win.document.getElementById('kbConfigOpenRouterKey').value).toBe('sk-or-xyz');
    expect(win.document.getElementById('kbConfigModel').value).toBe('text-embedding-3-small');
  });

  it('kbLoadConfig overwrites the HTML default with the server value on fresh load', async () => {
    win = boot();
    win.fetch = (url) => {
      const u = String(url);
      if (u.includes('/api/knowledge/config/models')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ models: [] }),
        });
      }
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({
          provider: 'OPENROUTER',
          ollamaUrl: '',
          openRouterApiKey: 'sk-or-xyz',
          model: 'text-embedding-3-small',
        }),
      });
    };
    win.document.getElementById('kbConfigProvider').value = 'OLLAMA';
    win.kbLoadConfig();
    let p = Promise.resolve();
    for (let i = 0; i < 30; i++) p = p.then(() => {});
    await p;

    expect(win.document.getElementById('kbConfigProvider').value).toBe('OPENROUTER');
    expect(win.document.getElementById('kbConfigOpenRouterKey').value).toBe('sk-or-xyz');
    expect(win.document.getElementById('kbConfigModel').value).toBe('text-embedding-3-small');
  });

  it('kbRefreshModelList preserves a user-selected model that is still in the new list', async () => {
    win = boot();
    win.fetch = () => Promise.resolve({
      ok: true,
      json: () => Promise.resolve({
        models: ['baai/bge-m3', 'qwen/qwen3-embedding-8b', 'openai/text-embedding-3-small'],
      }),
    });
    win.kbSavedConfig = { provider: 'OPENROUTER', model: 'qwen3-embedding:0.6b' };
    const modelSel = win.document.getElementById('kbConfigModel');
    modelSel.innerHTML = '';
    [['baai/bge-m3', 'baai/bge-m3']].forEach(([v, l]) => {
      const o = win.document.createElement('option');
      o.value = v;
      o.textContent = l;
      modelSel.appendChild(o);
    });
    modelSel.value = 'baai/bge-m3';
    win.kbRefreshModelList();
    let p = Promise.resolve();
    for (let i = 0; i < 30; i++) p = p.then(() => {});
    await p;

    expect(modelSel.value).toBe('baai/bge-m3');
  });

  it('kbLoadOverview does NOT pollute the global kbSavedConfig (so the next kbLoadConfig still sees null)', async () => {
    win = boot();
    win.fetch = (url) => {
      const u = String(url);
      if (u.endsWith('/api/knowledge/config')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({
            provider: 'OPENROUTER',
            ollamaUrl: '',
            openRouterApiKey: 'sk-or-xyz',
            model: 'baai/bge-m3',
          }),
        });
      }
      if (u.endsWith('/api/knowledge/bases') || u.endsWith('/api/knowledge/sources')) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
      }
      return Promise.reject(new Error('unexpected fetch: ' + url));
    };
    win.kbLoadOverview();
    let p = Promise.resolve();
    for (let i = 0; i < 30; i++) p = p.then(() => {});
    await p;

    expect(win.kbSavedConfig).toBeNull();
  });

  it('full page-load flow: kbLoadOverview then kbLoadConfig populates the provider dropdown from the server', async () => {
    win = boot();
    win.fetch = (url) => {
      const u = String(url);
      if (u.includes('/api/knowledge/config/models')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ models: ['baai/bge-m3', 'openai/text-embedding-3-small'] }),
        });
      }
      if (u.endsWith('/api/knowledge/config')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({
            provider: 'OPENROUTER',
            ollamaUrl: '',
            openRouterApiKey: 'sk-or-xyz',
            model: 'baai/bge-m3',
          }),
        });
      }
      if (u.endsWith('/api/knowledge/bases') || u.endsWith('/api/knowledge/sources')) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve([]) });
      }
      return Promise.reject(new Error('unexpected fetch: ' + url));
    };
    win.document.getElementById('kbConfigProvider').value = 'OLLAMA';

    win.kbLoadOverview();
    let p = Promise.resolve();
    for (let i = 0; i < 30; i++) p = p.then(() => {});
    await p;

    expect(win.document.getElementById('kbConfigProvider').value).toBe('OLLAMA');
    expect(win.kbSavedConfig).toBeNull();

    win.kbLoadConfig();
    for (let i = 0; i < 30; i++) p = p.then(() => {});
    await p;

    expect(win.document.getElementById('kbConfigProvider').value).toBe('OPENROUTER');
    expect(win.document.getElementById('kbConfigOpenRouterKey').value).toBe('sk-or-xyz');
    expect(win.document.getElementById('kbConfigModel').value).toBe('baai/bge-m3');
  });
});
