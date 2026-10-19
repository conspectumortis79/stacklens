import { describe, it, expect, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SCRIPTS_FRAGMENT = resolve(
    __dirname,
    '../../main/resources/templates/fragments/config/scripts-github-ai.html',
);
const SETTINGS_FRAGMENT = resolve(
    __dirname,
    '../../main/resources/templates/fragments/config/panes-settings.html',
);

const DEFAULT_MODELS = ['qwen3.8-flash', 'qwen3.8-max', 'deepseek-v4-pro'];

function readScriptsBlock() {
    const html = readFileSync(SCRIPTS_FRAGMENT, 'utf8');
    const re = /<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;
    const blocks = [...html.matchAll(re)].map((m) => m[1]).filter((b) => b.trim());
    if (blocks.length === 0) throw new Error('no inline script blocks in fragment');
    return blocks[blocks.length - 1];
}

const PROVIDERS = ['copilot', 'openai', 'ollama', 'lmstudio', 'openrouter', 'claudecode', 'compatible'];

function modelOptions(savedModel, models) {
    if (savedModel) {
        return `<option value="${savedModel}" selected="selected">${savedModel}</option>`;
    }
    return models
        .map((id, index) => `<option value="${id}"${index === 0 ? ' selected="selected"' : ''}>${id}</option>`)
        .join('\n          ');
}

const DEFAULT_SAVED_URL = 'https://dashscope-intl.aliyuncs.com/compatible-mode/v1';

function markup(checked, options) {
    const savedModel = options.savedModel || '';
    const models = options.models || DEFAULT_MODELS;
    const savedUrl = options.savedUrl || DEFAULT_SAVED_URL;
    return `
        <!DOCTYPE html><html><body><form>
        ${PROVIDERS.map((p) => `<input type="radio" name="aiProvider" value="${p}" ${checked === p ? 'checked' : ''}>`).join('\n')}
        <input id="aiGithubToken" value="••••••••"/>
        <input id="aiOpenAiApiKey"/>
        <select id="aiOpenAiReasoningEffort"><option value="medium">m</option></select>
        <input id="aiOllamaUrl"/>
        <input id="aiOllamaNumCtx" value="32768"/>
        <input id="aiLmStudioUrl"/>
        <input id="aiOpenRouterApiKey"/>
        <div id="aiOpenRouterKeySection" class="d-none"></div>
        <button id="aiTestOpenRouterBtn" class="d-none"></button>
        <input id="aiClaudeCodeApiKey"/>
        <div id="aiCompatibleUrlSection" class="d-none">
          <select id="aiCompatiblePreset">
            <option value="">custom</option>
            <option value="https://dashscope-intl.aliyuncs.com/compatible-mode/v1">qwen</option>
            <option value="https://coding-intl.dashscope.aliyuncs.com/v1">qwen-coding</option>
          </select>
          <input id="aiCompatibleUrl" value="${savedUrl}"/>
        </div>
        <div id="aiCompatibleKeySection" class="d-none"><input id="aiCompatibleApiKey" value="sk-secret"/></div>
        <div id="aiCompatibleModelSection" class="d-none">
          <select id="aiCompatibleModel">
          ${modelOptions(savedModel, models)}
          </select>
          <div id="aiCompatibleModelManualSection" class="d-none">
            <input id="aiCompatibleModelManual" value=""/>
          </div>
        </div>
        <div id="aiModelSelectSection"><select id="aiModel">
          <optgroup id="aiOpenRouterModelsGroup" label="OpenRouter"><option value="openai/gpt-4.1">gpt</option></optgroup>
          <optgroup id="aiClaudeCodeModelsGroup" label="Claude Code"><option value="claude-sonnet-4-5" selected="selected">cc</option></optgroup>
        </select></div>
        <div id="aiModelGroupsPool" style="display:none"></div>
        <div id="tab-ki" class="active"></div>
        <span id="aiTestStatus" class="small d-none"></span>
        <button id="aiTestCompatibleBtn" class="d-none"></button>
        <button id="aiTestClaudeCodeBtn" class="d-none"></button>
        <div id="aiCompatibleUrlHint" class="d-none"></div>
        <div id="aiCompatibleKeyHint" class="d-none"></div>
        </form></body></html>
    `;
}

function boot(checked = 'copilot', options = {}) {
    const models = options.models === undefined ? DEFAULT_MODELS : options.models;
    const response = options.response || {};
    const dom = new JSDOM(markup(checked, { savedModel: options.savedModel, models, savedUrl: options.savedUrl }), {
        runScripts: 'dangerously',
        pretendToBeVisual: true,
        url: 'http://localhost/',
    });
    const win = dom.window;
    win.localStorage.clear();
    win.i18n = { t: (key) => key, onReady: (fn) => fn() };
    win._t = (key) => key;
    win.showAlert = () => {};
    const calls = [];
    win.fetch = (url, fetchOptions) => {
        calls.push({ url, body: fetchOptions && fetchOptions.body ? JSON.parse(fetchOptions.body) : null });
        const payload = Object.assign({ success: true, models, modelCount: models.length }, response);
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(payload) });
    };
    const script = win.document.createElement('script');
    script.textContent = readScriptsBlock();
    win.document.body.appendChild(script);
    script.remove();
    return { win, calls };
}

function tick() {
    return new Promise((r) => setTimeout(r, 0));
}

function isHidden(win, id) {
    const el = win.document.getElementById(id);
    return el ? el.classList.contains('d-none') : null;
}

function selectOptions(win) {
    const select = win.document.getElementById('aiCompatibleModel');
    return Array.from(select.options).map((o) => o.value);
}

function storedModels(win) {
    return JSON.parse(win.localStorage.getItem('stacklens_ai_model_per_provider') || '{}');
}

function selectProvider(win, provider) {
    const radio = win.document.querySelector(`input[name="aiProvider"][value="${provider}"]`);
    PROVIDERS.forEach((p) => {
        win.document.querySelector(`input[name="aiProvider"][value="${p}"]`).checked = p === provider;
    });
    radio.checked = true;
    win.onAiProviderChange();
}

describe('OpenAI-compatible provider configuration', () => {
    let win;

    afterEach(() => {
        if (win) {
            try { win.close(); } catch (e) { }
            win = null;
        }
    });

    it('renders a model dropdown fed by the endpoint instead of a free text datalist', () => {
        const html = readFileSync(SETTINGS_FRAGMENT, 'utf8');
        expect(html).toContain('value="compatible"');
        expect(html).toContain('id="aiCompatibleUrlSection"');
        expect(html).toContain('id="aiCompatibleKeySection"');
        expect(html).toContain('id="aiCompatibleModelSection"');
        expect(html).toContain('<select class="form-select" id="aiCompatibleModel"');
        expect(html).toContain('id="aiCompatibleModelManualSection"');
        expect(html).toContain('id="aiTestCompatibleBtn"');
        expect(html).toContain('id="aiInfoBannerCompatible"');
        expect(html).toContain('testCompatibleConnection()');
        expect(html).not.toContain('<datalist');
        expect(html).not.toContain('aiCompatibleModelList');
    });

    it('marks the saved model as the selected option server side', () => {
        const html = readFileSync(SETTINGS_FRAGMENT, 'utf8');
        expect(html).toContain("th:with=\"savedCompatibleModel=\${(aiConfig != null and aiConfig.aiProvider == 'compatible'");
        expect(html).toContain('th:value="${savedCompatibleModel}"');
        expect(html).toContain('selected="selected"');
    });

    it('reveals only the compatible controls once the provider is selected', async () => {
        ({ win } = boot('compatible'));
        await tick();
        win.onAiProviderChange();

        expect(isHidden(win, 'aiCompatibleUrlSection')).toBe(false);
        expect(isHidden(win, 'aiCompatibleKeySection')).toBe(false);
        expect(isHidden(win, 'aiCompatibleModelSection')).toBe(false);
        expect(isHidden(win, 'aiTestCompatibleBtn')).toBe(false);
        expect(isHidden(win, 'aiOpenRouterKeySection')).toBe(true);
        expect(isHidden(win, 'aiModelSelectSection')).toBe(true);
    });

    it('hides the compatible controls again for every other provider', async () => {
        ({ win } = boot('compatible'));
        win.onAiProviderChange();
        selectProvider(win, 'openrouter');

        expect(isHidden(win, 'aiCompatibleUrlSection')).toBe(true);
        expect(isHidden(win, 'aiCompatibleModelSection')).toBe(true);
        expect(isHidden(win, 'aiModelSelectSection')).toBe(false);
        await tick();
    });

    it('loads the models of the account into the dropdown as soon as the provider is picked', async () => {
        const { win: w, calls } = boot('compatible');
        win = w;
        await tick();

        expect(calls.map((c) => c.url)).toContain('/api/config/ai/test-compatible');
        expect(selectOptions(w)).toEqual(DEFAULT_MODELS);
        expect(w.document.getElementById('aiCompatibleModel').value).toBe('qwen3.8-flash');
    });

    it('sends base url, key and the picked model on save', async () => {
        const { win: w, calls } = boot('compatible');
        win = w;
        await tick();
        w.document.getElementById('aiCompatibleModel').value = 'deepseek-v4-pro';
        w._onCompatibleModelChange();
        w.document.getElementById('aiCompatibleUrl').value = 'https://coding-intl.dashscope.aliyuncs.com/v1';
        w.document.getElementById('aiCompatibleApiKey').value = 'sk-sp-mine';

        await w.saveAiConfig();

        expect(calls.filter((c) => c.url === '/api/config/ai')).toHaveLength(1);
        const saved = calls.find((c) => c.url === '/api/config/ai').body;
        expect(saved.aiProvider).toBe('compatible');
        expect(saved.model).toBe('deepseek-v4-pro');
        expect(saved.compatibleBaseUrl).toBe('https://coding-intl.dashscope.aliyuncs.com/v1');
        expect(saved.compatibleApiKey).toBe('sk-sp-mine');
    });

    it('never sends the masked key back to the backend', async () => {
        const { win: w, calls } = boot('compatible');
        win = w;
        await tick();
        w.document.getElementById('aiCompatibleApiKey').value = '••••••••';
        w.document.getElementById('aiCompatibleModel').value = 'qwen3.8-max';
        w._onCompatibleModelChange();

        await w.saveAiConfig();

        const saved = calls.find((c) => c.url === '/api/config/ai').body;
        expect(saved.compatibleApiKey).toBeNull();
        expect(saved.model).toBe('qwen3.8-max');
    });

    it('takes the model from the shared select for other providers', async () => {
        const { win: w, calls } = boot('openrouter', { models: ['openai/gpt-4.1', 'anthropic/claude-3.5-sonnet'] });
        win = w;
        await tick();
        w.document.getElementById('aiModel').value = 'anthropic/claude-3.5-sonnet';

        await w.saveAiConfig();

        const saved = calls.find((c) => c.url === '/api/config/ai').body;
        expect(saved.aiProvider).toBe('openrouter');
        expect(saved.model).toBe('anthropic/claude-3.5-sonnet');
    });

    it('keeps the picked model selected when the model list is reloaded', async () => {
        const { win: w } = boot('compatible');
        win = w;
        await tick();
        const select = w.document.getElementById('aiCompatibleModel');
        select.value = 'qwen3.8-max';
        w._onCompatibleModelChange();

        await w._loadCompatibleModels(true);

        expect(select.value).toBe('qwen3.8-max');
        expect(selectOptions(w)).toEqual(DEFAULT_MODELS);
    });

    it('keeps the saved model selected after the endpoint list is fetched again', async () => {
        const { win: w } = boot('compatible', { savedModel: 'deepseek-v4-pro' });
        win = w;
        await tick();

        expect(w.document.getElementById('aiCompatibleModel').value).toBe('deepseek-v4-pro');
        expect(storedModels(w).compatible).toBe('deepseek-v4-pro');
    });

    it('keeps a saved model that the endpoint no longer lists instead of switching to another one', async () => {
        const { win: w } = boot('compatible', { savedModel: 'legacy-model', models: ['qwen3.8-flash', 'qwen3.8-max'] });
        win = w;
        await tick();

        expect(w.document.getElementById('aiCompatibleModel').value).toBe('legacy-model');
        expect(selectOptions(w)).toEqual(['legacy-model', 'qwen3.8-flash', 'qwen3.8-max']);
    });

    it('keeps the chosen model across provider switches', async () => {
        const { win: w } = boot('compatible');
        win = w;
        await tick();
        w.document.getElementById('aiCompatibleModel').value = 'qwen3.8-max';
        w._onCompatibleModelChange();

        selectProvider(w, 'openrouter');
        selectProvider(w, 'compatible');
        await tick();

        expect(w.document.getElementById('aiCompatibleModel').value).toBe('qwen3.8-max');
        expect(storedModels(w).compatible).toBe('qwen3.8-max');
    });

    it('does not request the endpoint again while the same configuration is already loaded', async () => {
        const { win: w, calls } = boot('compatible');
        win = w;
        await tick();
        const requestsBefore = calls.filter((c) => c.url === '/api/config/ai/test-compatible').length;

        selectProvider(w, 'openrouter');
        selectProvider(w, 'compatible');
        await tick();

        expect(calls.filter((c) => c.url === '/api/config/ai/test-compatible')).toHaveLength(requestsBefore);
    });

    it('reloads the dropdown and drops the stale pick when another endpoint is chosen', async () => {
        const { win: w } = boot('compatible');
        win = w;
        await tick();
        w.document.getElementById('aiCompatibleModel').value = 'qwen3.8-max';
        w._onCompatibleModelChange();
        w.fetch = (url, fetchOptions) => Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve({ success: true, models: ['glm-5.2', 'kimi-k2'], modelCount: 2 }),
        });

        const preset = w.document.getElementById('aiCompatiblePreset');
        preset.value = 'https://coding-intl.dashscope.aliyuncs.com/v1';
        w.applyCompatiblePreset();
        await tick();

        expect(w.document.getElementById('aiCompatibleUrl').value).toBe('https://coding-intl.dashscope.aliyuncs.com/v1');
        expect(selectOptions(w)).toEqual(['glm-5.2', 'kimi-k2']);
        expect(w.document.getElementById('aiCompatibleModel').value).toBe('glm-5.2');
        expect(storedModels(w).compatible).toBe('glm-5.2');
    });

    it('falls back to a manual model id when the endpoint publishes no list', async () => {
        const { win: w } = boot('compatible', { savedModel: 'qwen3.8-max', models: [] });
        win = w;
        await tick();

        expect(isHidden(w, 'aiCompatibleModel')).toBe(true);
        expect(isHidden(w, 'aiCompatibleModelManualSection')).toBe(false);
        expect(w.document.getElementById('aiCompatibleModelManual').value).toBe('qwen3.8-max');
    });

    it('saves the manually entered model id when no list is published', async () => {
        const { win: w, calls } = boot('compatible', { models: [] });
        win = w;
        await tick();
        w.document.getElementById('aiCompatibleModelManual').value = 'my-local-model';
        w._onCompatibleModelManualInput();

        await w.saveAiConfig();

        const saved = calls.find((c) => c.url === '/api/config/ai').body;
        expect(saved.model).toBe('my-local-model');
        expect(storedModels(w).compatible).toBe('my-local-model');
    });

    it('shows the preset that matches the saved base url after the page reloads', async () => {
        ({ win } = boot('compatible', { savedUrl: 'https://coding-intl.dashscope.aliyuncs.com/v1' }));
        await tick();

        expect(win.document.getElementById('aiCompatiblePreset').value).toBe('https://coding-intl.dashscope.aliyuncs.com/v1');
    });

    it('keeps the preset on custom when the saved base url matches no preset', async () => {
        ({ win } = boot('compatible', { savedUrl: 'http://localhost:8000/v1' }));
        await tick();

        expect(win.document.getElementById('aiCompatiblePreset').value).toBe('');
        expect(win.document.getElementById('aiCompatibleUrl').value).toBe('http://localhost:8000/v1');
    });

    it('mirrors a manually typed base url back into the preset once it matches a preset', async () => {
        ({ win } = boot('compatible', { savedUrl: 'http://localhost:8000/v1' }));
        await tick();
        const url = win.document.getElementById('aiCompatibleUrl');
        url.value = '  https://coding-intl.dashscope.aliyuncs.com/v1  ';
        win._onCompatibleUrlInput();
        expect(win.document.getElementById('aiCompatiblePreset').value).toBe('');

        win._onCompatibleUrlChange();
        await tick();

        expect(win.document.getElementById('aiCompatiblePreset').value).toBe('https://coding-intl.dashscope.aliyuncs.com/v1');
    });

    it('applies a preset to the base url field and clears it again on manual edits', async () => {
        ({ win } = boot('compatible'));
        const preset = win.document.getElementById('aiCompatiblePreset');
        const url = win.document.getElementById('aiCompatibleUrl');

        preset.value = 'https://coding-intl.dashscope.aliyuncs.com/v1';
        win.applyCompatiblePreset();
        expect(url.value).toBe('https://coding-intl.dashscope.aliyuncs.com/v1');

        url.value = 'http://localhost:8000/v1';
        win._onCompatibleUrlInput();
        expect(preset.value).toBe('');
        await tick();
    });

    it('refuses to test a connection without a base url', async () => {
        const { win: w, calls } = boot('compatible');
        win = w;
        await tick();
        const requestsBefore = calls.filter((c) => c.url === '/api/config/ai/test-compatible').length;
        w.document.getElementById('aiCompatibleUrl').value = '   ';

        await w.testCompatibleConnection();

        expect(calls.filter((c) => c.url === '/api/config/ai/test-compatible')).toHaveLength(requestsBefore);
        expect(win.document.getElementById('aiTestStatus').textContent).toContain('config.ai.js_compatible_invalid_url');
    });

    it('posts the base url and the picked model to the connection test endpoint', async () => {
        const { win: w, calls } = boot('compatible');
        win = w;
        await tick();
        w.document.getElementById('aiCompatibleUrl').value = 'https://api.groq.com/openai/v1';
        w.document.getElementById('aiCompatibleModel').value = 'qwen3.8-max';
        w._onCompatibleModelChange();
        w.document.getElementById('aiCompatibleApiKey').value = 'sk-groq';

        await w.testCompatibleConnection();

        const test = calls.filter((c) => c.url === '/api/config/ai/test-compatible').pop();
        expect(test.body.compatibleBaseUrl).toBe('https://api.groq.com/openai/v1');
        expect(test.body.model).toBe('qwen3.8-max');
        expect(test.body.compatibleApiKey).toBe('sk-groq');
    });

    it('shows the rate-limit verdict as informational rather than a key error', async () => {
        const { win: w } = boot('compatible', { response: { success: false, error: 'quota', severity: 'info', models: [], modelCount: 0 } });
        win = w;
        await tick();

        await w.testCompatibleConnection();

        const status = win.document.getElementById('aiTestStatus');
        expect(status.className).toContain('text-muted');
        expect(status.textContent).toContain('quota');
    });

    it('warns when the endpoint accepts the key but publishes no model list', async () => {
        const { win: w } = boot('compatible', { models: [] });
        win = w;
        await tick();

        await w._loadCompatibleModels(true);

        const status = win.document.getElementById('aiTestStatus');
        expect(status.className).toContain('text-warning');
        expect(status.textContent).toContain('config.ai.js_compatible_no_models');
    });
});
