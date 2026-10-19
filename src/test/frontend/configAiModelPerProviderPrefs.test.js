import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const here = dirname(fileURLToPath(import.meta.url));
const FRAGMENT = resolve(here, '../../main/resources/templates/fragments/config/scripts-github-ai.html');
const CODE = extractInlineScripts(FRAGMENT);

const PREFS_URL = '/api/ui-prefs/ai_model_per_provider';

const DOM =
  '<input type="radio" name="aiProvider" value="copilot" checked>' +
  '<input type="radio" name="aiProvider" value="openai">' +
  '<select id="aiModel"><optgroup id="aiCopilotModelsGroup"><option value="model-from-db" selected>model-from-db</option></optgroup></select>' +
  '<select id="aiCompatibleModel"><option value="">laden</option></select>' +
  '<div id="aiCompatibleModelManualSection" class="d-none"></div>' +
  '<input id="aiCompatibleModelManual">' +
  '<input id="aiCompatibleUrl" value="https://gateway.example.com/compatible-mode/v1">' +
  '<input id="aiCompatibleApiKey" value="sk-secret">' +
  '<select id="aiCompatiblePreset"><option value=""></option></select>' +
  '<div id="tab-ki" class="active"></div>';

function wait(ms) {
  return new Promise((done) => setTimeout(done, ms));
}

function boot(serverPrefs, options = {}) {
  const win = makeWindow(DOM);
  const calls = [];
  win.i18n = { t: (key, fallback) => fallback || key };
  win._t = (key) => key;
  win.showAlert = () => {};
  win.showConfirm = () => {};
  win.fetch = (url, init) => {
    const method = (init && init.method) || 'GET';
    calls.push({ url: String(url), method, body: init && init.body });
    if (method === 'GET') {
      if (options.pending) return new Promise(() => {});
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(serverPrefs) });
    }
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ ok: true }) });
  };
  runInWindow(win, CODE);
  return { win, calls };
}

const SERVER_MAP = { copilot: 'stale-server-model', openai: 'gpt-5-mini', compatible: 'qwen3.8-flash' };

describe('KI-Modelle je Provider werden serverseitig als UI-Präferenz geführt', () => {
  it('liest die Präferenz beim Initialisieren vom Server', async () => {
    const { win, calls } = boot({ value: JSON.stringify(SERVER_MAP) });
    await wait(20);
    expect(calls.some((c) => c.url === '/api/ui-prefs/ai_model_per_provider' && c.method === 'GET')).toBe(true);
    win.close();
  });

  it('übernimmt die gemerkten Modelle der anderen Provider', async () => {
    const { win } = boot({ value: JSON.stringify(SERVER_MAP) });
    await wait(20);
    expect(win._aiModelPerProvider.openai).toBe('gpt-5-mini');
    expect(win._compatibleModelSelection).toBe('qwen3.8-flash');
    win.close();
  });

  it('überschreibt das aktiv gewählte Modell nicht, das aus der Datenbank gerendert ist', async () => {
    const { win } = boot({ value: JSON.stringify(SERVER_MAP) });
    await wait(20);
    expect(win._aiModelPerProvider.copilot).toBe('model-from-db');
    win.close();
  });

  it('schreibt die Modell-Merkliste in die UI-Präferenz des Servers', async () => {
    const { win, calls } = boot({ value: '{}' });
    await wait(20);
    win._rememberModelForProvider('ollama', 'llama-3.3-70b');
    await wait(400);
    const posts = calls.filter((c) => c.method === 'POST');
    expect(posts).toHaveLength(1);
    expect(posts[0].url).toBe(PREFS_URL);
    expect(JSON.parse(JSON.parse(posts[0].body).value).ollama).toBe('llama-3.3-70b');
    win.close();
  });

  it('schreibt nur einmal, wenn sich die Liste nicht verändert hat', async () => {
    const { win, calls } = boot({ value: '{}' });
    await wait(20);
    win._rememberModelForProvider('ollama', 'llama-3.3-70b');
    win._saveAiModelStorage();
    win._saveAiModelStorage();
    await wait(400);
    expect(calls.filter((c) => c.method === 'POST')).toHaveLength(1);
    win.close();
  });

  it('wartet das Modell-Laden die Server-Präferenz ab', async () => {
    const { win } = boot({ value: JSON.stringify(SERVER_MAP) }, { pending: true });
    await wait(20);
    let loaded = 0;
    win._loadAiModelsOfCurrentProvider = () => { loaded += 1; };
    win._loadModelsForCurrentProvider();
    await wait(20);
    expect(loaded).toBe(0);
    win._aiModelPrefsReady = Promise.resolve();
    win._loadModelsForCurrentProvider();
    await wait(20);
    expect(loaded).toBe(1);
    win.close();
  });

  it('verwirft kaputte Serverwerte und behält die lokale Liste', async () => {
    const { win } = boot({ value: 'kein-json' });
    await wait(20);
    expect(win._aiModelPerProvider.openai).toBeUndefined();
    win.close();
  });

  it('führt Lesen und Schreiben über denselben Präferenz-Schlüssel', () => {
    const source = readFileSync(FRAGMENT, 'utf8');
    expect(source).toContain("var _AI_MODEL_PREFS_KEY = 'ai_model_per_provider';");
    expect(source.match(/\/api\/ui-prefs\/' \+ _AI_MODEL_PREFS_KEY/g)).toHaveLength(2);
  });
});
