import { describe, it, expect, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SCRIPTS_FRAGMENT = resolve(__dirname, '../../main/resources/templates/fragments/config/scripts-github-ai.html');

function readScriptsBlock() {
    const html = readFileSync(SCRIPTS_FRAGMENT, 'utf8');
    const re = /<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;
    const blocks = [...html.matchAll(re)].map((m) => m[1]).filter((b) => b.trim());
    if (blocks.length === 0) throw new Error('no inline script blocks in fragment');
    return blocks[blocks.length - 1];
}

function markup(keyValue, modelValue) {
    return `
        <!DOCTYPE html><html><body><form>
        <input type="radio" name="aiProvider" value="claudecode" checked>
        <input id="aiGithubToken" value=""/>
        <input id="aiOpenAiApiKey"/>
        <select id="aiOpenAiReasoningEffort"><option value="medium">m</option></select>
        <input id="aiOllamaUrl"/>
        <input id="aiOllamaNumCtx" value="32768"/>
        <input id="aiLmStudioUrl"/>
        <input id="aiOpenRouterApiKey"/>
        <input id="aiClaudeCodeApiKey" value="${keyValue}"/>
        <input id="aiCompatibleApiKey"/>
        <input id="aiCompatibleUrl"/>
        <select id="aiModel">
          <optgroup id="aiClaudeCodeModelsGroup" label="Claude Code">
            <option value="claude-sonnet-4-5">sonnet</option>
            <option value="claude-opus-4-5"${modelValue === 'claude-opus-4-5' ? ' selected="selected"' : ''}>opus</option>
          </optgroup>
        </select>
        <span id="aiTestStatus" class="small d-none"></span>
        </form></body></html>
    `;
}

let win;

function boot(keyValue = '', modelValue = 'claude-opus-4-5', response = { success: true }) {
    const dom = new JSDOM(markup(keyValue, modelValue), {
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
    w.fetch = (url, fetchOptions) => {
        calls.push({ url, body: fetchOptions && fetchOptions.body ? JSON.parse(fetchOptions.body) : null });
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(response) });
    };
    const script = w.document.createElement('script');
    script.textContent = readScriptsBlock();
    w.document.body.appendChild(script);
    script.remove();
    return { win: w, calls };
}

function tick() {
    return new Promise((r) => setTimeout(r, 0));
}

afterEach(() => {
    if (win) {
        win.close();
        win = null;
    }
});

describe('claude code connection probe payload', () => {
    it('sends the typed token together with the selected model', async () => {
        const { win: w, calls } = boot('sk-ant-oat01-typed');
        win = w;
        await tick();

        await w.testClaudeCodeConnection();

        const probe = calls.find((c) => c.url === '/api/config/ai/test-claudecode');
        expect(probe.body.claudeCodeApiKey).toBe('sk-ant-oat01-typed');
        expect(probe.body.model).toBe('claude-opus-4-5');
        expect(w.document.getElementById('aiTestStatus').textContent).toContain('config.ai.js_claudecode_ok');
    });

    it('sends only the model when the stored token is masked', async () => {
        const { win: w, calls } = boot('••••••••');
        win = w;
        await tick();

        await w.testClaudeCodeConnection();

        const probe = calls.find((c) => c.url === '/api/config/ai/test-claudecode');
        expect(probe.body.claudeCodeApiKey).toBeUndefined();
        expect(probe.body.model).toBe('claude-opus-4-5');
    });

    it('leaves the model out when the select holds no value', async () => {
        const { win: w, calls } = boot('sk-ant-api03-typed');
        win = w;
        await tick();
        w.document.getElementById('aiModel').value = '';

        await w.testClaudeCodeConnection();

        const probe = calls.find((c) => c.url === '/api/config/ai/test-claudecode');
        expect(probe.body.model).toBeUndefined();
        expect(probe.body.claudeCodeApiKey).toBe('sk-ant-api03-typed');
    });

    it('reports a rejected probe with the server message', async () => {
        const { win: w } = boot('sk-ant-oat01-typed', 'claude-opus-4-5', { success: false, error: 'Anthropic lehnt das Token ab' });
        win = w;
        await tick();

        await w.testClaudeCodeConnection();

        expect(w.document.getElementById('aiTestStatus').className).toContain('text-danger');
        expect(w.document.getElementById('aiTestStatus').textContent).toContain('Anthropic lehnt das Token ab');
    });
});
