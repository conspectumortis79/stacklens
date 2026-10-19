import { describe, it, expect, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SCRIPTS_FRAGMENT = resolve(__dirname, '../../main/resources/templates/fragments/config/scripts-github-ai.html');

const STATIC_MODELS = ['claude-sonnet-4-5', 'claude-opus-4-5', 'claude-haiku-4-5', 'claude-sonnet-4-0'];

function readScriptsBlock() {
    const html = readFileSync(SCRIPTS_FRAGMENT, 'utf8');
    const re = /<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;
    const blocks = [...html.matchAll(re)].map((m) => m[1]).filter((b) => b.trim());
    if (blocks.length === 0) throw new Error('no inline script blocks in fragment');
    return blocks[blocks.length - 1];
}

function markup(opts) {
    const savedModel = opts.savedModel || '';
    const models = 'models' in opts ? opts.models : STATIC_MODELS;
    const options = models
        .map((id) => `<option value="${id}"${id === savedModel ? ' selected="selected"' : ''}>${id}</option>`)
        .join('');
    const otherGroup = opts.withOtherGroup === false
        ? ''
        : '<optgroup label="OpenAI GPT" id="aiOpenAiModelsGroup"><option value="gpt-5.2">GPT-5.2</option></optgroup>';
    return `
        <!DOCTYPE html><html><body><form>
        <input type="radio" name="aiProvider" value="claudecode" checked>
        <input type="radio" name="aiProvider" value="openai">
        <input id="aiClaudeCodeApiKey" value=""/>
        <select id="aiModel">
          ${otherGroup}
          <optgroup label="Claude Code" class="ai-claudecode-models" id="aiClaudeCodeModelsGroup">${options}</optgroup>
        </select>
        <div id="aiModelGroupsPool" style="display:none"></div>
        <span id="aiTestStatus" class="small d-none"></span>
        </form></body></html>
    `;
}

let win;

function boot(opts = {}) {
    const dom = new JSDOM(markup(opts), {
        runScripts: 'dangerously',
        pretendToBeVisual: true,
        url: 'http://localhost/',
    });
    const w = dom.window;
    w.localStorage.clear();
    w.i18n = { t: (key) => key, onReady: (fn) => fn() };
    w._t = (key) => key;
    w.showAlert = () => {};
    const calls = [];
    const responders = [];
    w.fetch = (url, fetchOptions) => {
        calls.push({ url, body: fetchOptions && fetchOptions.body ? JSON.parse(fetchOptions.body) : null });
        const respond = responders.length > 0 ? responders.shift() : () => ({ success: true });
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(respond()) });
    };
    const script = w.document.createElement('script');
    script.textContent = readScriptsBlock();
    w.document.body.appendChild(script);
    script.remove();
    return { win: w, calls, responders, probes: () => calls.filter((c) => c.url === '/api/config/ai/test-claudecode') };
}

function tick() {
    return new Promise((r) => setTimeout(r, 0));
}

function groupOptions(w) {
    const group = w.document.getElementById('aiClaudeCodeModelsGroup');
    return Array.from(group.querySelectorAll('option')).map((o) => o.value);
}

function status(w) {
    return w.document.getElementById('aiTestStatus');
}

afterEach(() => {
    if (win) {
        win.close();
        win = null;
    }
});

describe('claude code model discovery', () => {
    it('replaces the static list with the models reported by the probe', async () => {
        const { win: w, responders, probes } = boot({ savedModel: 'claude-sonnet-4-5' });
        win = w;
        responders.push(() => ({
            success: true,
            accountHint: 'sk-ant-…3456',
            modelCount: 3,
            models: ['claude-opus-4-1', 'claude-sonnet-4-5', 'claude-haiku-4-5'],
        }));

        await w._loadClaudeCodeModels();
        await tick();

        expect(probes()).toHaveLength(1);
        expect(groupOptions(w)).toEqual(['claude-opus-4-1', 'claude-sonnet-4-5', 'claude-haiku-4-5']);
        expect(w.document.getElementById('aiModel').value).toBe('claude-sonnet-4-5');
        expect(status(w).className).toContain('text-success');
        expect(status(w).textContent).toContain('config.ai.js_claudecode_models_loaded');
    });

    it('keeps a stored model that the account does not list and selects it', async () => {
        const { win: w, responders } = boot({ savedModel: 'claude-sonnet-4-5' });
        win = w;
        responders.push(() => ({ success: true, modelCount: 1, models: ['claude-opus-4-1'] }));

        await w._loadClaudeCodeModels();
        await tick();

        expect(groupOptions(w)).toEqual(['claude-sonnet-4-5', 'claude-opus-4-1']);
        expect(w.document.getElementById('aiModel').value).toBe('claude-sonnet-4-5');
    });

    it('selects the first discovered model when no model is stored', () => {
        const { win: w } = boot({ models: [], withOtherGroup: false });
        win = w;

        w._populateClaudeCodeModels(['claude-opus-4-1', 'claude-sonnet-4-5']);

        expect(w.document.getElementById('aiModel').value).toBe('claude-opus-4-1');
        expect(JSON.parse(w.localStorage.getItem('stacklens_ai_model_per_provider')).claudecode).toBe('claude-opus-4-1');
    });

    it('ignores empty and non string entries in a reported model list', () => {
        const { win: w } = boot({ savedModel: 'claude-sonnet-4-5' });
        win = w;

        w._populateClaudeCodeModels(['', null, 42, 'claude-opus-4-1']);

        expect(groupOptions(w)).toEqual(['claude-sonnet-4-5', 'claude-opus-4-1']);
    });

    it('leaves the dropdown untouched for an empty or missing model list', () => {
        const { win: w } = boot({ savedModel: 'claude-sonnet-4-5' });
        win = w;

        w._populateClaudeCodeModels([]);
        w._populateClaudeCodeModels(null);

        expect(groupOptions(w)).toEqual(STATIC_MODELS);
    });

    it('probes without a model so a stale stored model cannot block discovery', async () => {
        const { win: w, responders, probes } = boot({ savedModel: 'claude-sonnet-4-5' });
        win = w;
        responders.push(() => ({ success: true, modelCount: 1, models: ['claude-opus-4-1'] }));

        await w._loadClaudeCodeModels();
        await tick();

        expect(probes()[0].body).toEqual({});
    });

    it('sends a freshly typed token but never the mask placeholder', async () => {
        const { win: w, responders, probes } = boot();
        win = w;
        responders.push(() => ({ success: true, modelCount: 1, models: ['claude-opus-4-1'] }));
        w.document.getElementById('aiClaudeCodeApiKey').value = 'sk-ant-oat01-typed';
        await w._loadClaudeCodeModels();
        await tick();
        expect(probes()[0].body).toEqual({ claudeCodeApiKey: 'sk-ant-oat01-typed' });

        responders.push(() => ({ success: true, modelCount: 1, models: ['claude-opus-4-1'] }));
        w.document.getElementById('aiClaudeCodeApiKey').value = '••••••••';
        await w._loadClaudeCodeModels(true);
        await tick();
        expect(probes()[1].body).toEqual({});
    });

    it('keeps the static list as fallback when the account publishes no models', async () => {
        const { win: w, responders, probes } = boot();
        win = w;
        responders.push(() => ({ success: true, modelCount: 0, models: [] }));

        await w._loadClaudeCodeModels();
        await tick();

        expect(probes()).toHaveLength(1);
        expect(groupOptions(w)).toEqual(STATIC_MODELS);
        expect(status(w).className).toContain('text-warning');
        expect(status(w).textContent).toContain('config.ai.js_claudecode_no_models');
    });

    it('keeps the static list and reports the server error when the probe fails', async () => {
        const { win: w, responders } = boot();
        win = w;
        responders.push(() => ({ success: false, error: 'Anthropic lehnt das Token ab', severity: 'error' }));

        await w._loadClaudeCodeModels();
        await tick();

        expect(groupOptions(w)).toEqual(STATIC_MODELS);
        expect(status(w).className).toContain('text-danger');
        expect(status(w).textContent).toContain('Anthropic lehnt das Token ab');
    });

    it('probes once per page and re-probes only when forced', async () => {
        const { win: w, responders, probes } = boot();
        win = w;
        responders.push(() => ({ success: true, modelCount: 0, models: [] }));

        await w._loadClaudeCodeModels();
        await tick();
        await w._loadClaudeCodeModels();
        await tick();
        expect(probes()).toHaveLength(1);

        responders.push(() => ({ success: true, modelCount: 1, models: ['claude-opus-4-1'] }));
        await w._loadClaudeCodeModels(true);
        await tick();
        expect(probes()).toHaveLength(2);
        expect(groupOptions(w)).toEqual(['claude-sonnet-4-5', 'claude-opus-4-1']);
    });

    it('ignores a response that arrives after the provider was switched away', async () => {
        const { win: w } = boot();
        win = w;
        let resolveFetch;
        w.fetch = () => new Promise((r) => { resolveFetch = r; });

        const pending = w._loadClaudeCodeModels();
        w.document.querySelector('input[name="aiProvider"][value="claudecode"]').checked = false;
        w.document.querySelector('input[name="aiProvider"][value="openai"]').checked = true;
        resolveFetch({
            ok: true,
            status: 200,
            json: () => Promise.resolve({ success: true, modelCount: 1, models: ['claude-opus-4-1'] }),
        });
        await pending;
        await tick();

        expect(groupOptions(w)).toEqual(STATIC_MODELS);
        expect(status(w).className).not.toContain('text-success');
    });

    it('ignores the loader while another provider is active', async () => {
        const { win: w, probes } = boot();
        win = w;
        w.document.querySelector('input[name="aiProvider"][value="claudecode"]').checked = false;
        w.document.querySelector('input[name="aiProvider"][value="openai"]').checked = true;

        await w._loadClaudeCodeModels();
        await tick();

        expect(probes()).toHaveLength(0);
    });

    it('fills the dropdown from the manual test button too', async () => {
        const { win: w, responders, probes } = boot({ savedModel: 'claude-opus-4-5' });
        win = w;
        responders.push(() => ({ success: true, modelCount: 2, models: ['claude-opus-4-5', 'claude-haiku-4-5'] }));

        await w.testClaudeCodeConnection();
        await tick();

        expect(probes()[0].body.model).toBe('claude-opus-4-5');
        expect(groupOptions(w)).toEqual(['claude-opus-4-5', 'claude-haiku-4-5']);
        expect(w.document.getElementById('aiModel').value).toBe('claude-opus-4-5');
    });
});
