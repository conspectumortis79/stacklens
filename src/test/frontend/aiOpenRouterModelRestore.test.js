import { describe, it, expect, beforeEach, afterEach } from 'vitest';
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

function readScriptsBlock() {
    const html = readFileSync(SCRIPTS_FRAGMENT, 'utf8');
    const re = /<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;
    const blocks = [...html.matchAll(re)].map((m) => m[1]).filter((b) => b.trim());
    if (blocks.length === 0) throw new Error('no inline script blocks in fragment');
    return blocks[blocks.length - 1];
}


function bootAiConfig(opts) {
    const { savedProvider, savedModel } = opts;

    const html = `
        <!DOCTYPE html>
        <html><body>
        <form>
          <input type="radio" name="aiProvider" value="copilot"     ${savedProvider === 'copilot' ? 'checked' : ''}>
          <input type="radio" name="aiProvider" value="openai"      ${savedProvider === 'openai' ? 'checked' : ''}>
          <input type="radio" name="aiProvider" value="ollama"      ${savedProvider === 'ollama' ? 'checked' : ''}>
          <input type="radio" name="aiProvider" value="lmstudio"    ${savedProvider === 'lmstudio' ? 'checked' : ''}>
          <input type="radio" name="aiProvider" value="openrouter"  ${savedProvider === 'openrouter' ? 'checked' : ''}>
          <input type="radio" name="aiProvider" value="claudecode"  ${savedProvider === 'claudecode' ? 'checked' : ''}>

          <select id="aiModel">
            <optgroup label="GitHub Copilot" id="aiCopilotModelsGroup">
              ${savedProvider === 'copilot'
                  ? `<option value="${savedModel}" selected="selected">${savedModel}</option>`
                  : `<option value="gpt-5.4-mini" selected="selected">gpt-5.4-mini</option>`}
            </optgroup>
            <optgroup label="OpenAI GPT" id="aiOpenAiModelsGroup">
              ${savedProvider === 'openai'
                  ? `<option value="${savedModel}" selected="selected">${savedModel}</option>`
                  : `<option value="gpt-5.2">GPT-5.2</option>`}
            </optgroup>
            <optgroup label="Ollama" id="aiOllamaModelsGroup">
              ${savedProvider === 'ollama'
                  ? `<option value="${savedModel}" selected="selected">${savedModel}</option>`
                  : `<option value="qwen3-coder:30b-a3b-q4_k_m" selected="selected">qwen3-coder:30b-a3b-q4_k_m</option>`}
            </optgroup>
            <optgroup label="LM Studio" id="aiLmStudioModelsGroup">
              <option value="local-model">local-model</option>
            </optgroup>
            <optgroup label="OpenRouter" id="aiOpenRouterModelsGroup">
              ${savedProvider === 'openrouter'
                  ? `<option value="${savedModel}" selected="selected">${savedModel}</option>`
                  : `<option value="openai/gpt-4.1">openai/gpt-4.1</option>`}
            </optgroup>
            <optgroup label="Claude Code" id="aiClaudeCodeModelsGroup">
              <option value="claude-sonnet-4-5" selected="selected">claude-sonnet-4-5</option>
            </optgroup>
          </select>

          <div id="aiModelGroupsPool" style="display:none"></div>
          <div id="tab-ki" class="active"></div>
        </form>
        </body></html>
    `;

    const dom = new JSDOM(html, {
        runScripts: 'dangerously',
        pretendToBeVisual: true,
        url: 'http://localhost/',
    });
    const win = dom.window;
    win.localStorage.clear();

    win.fetch = () => Promise.resolve({
        ok: true,
        status: 200,
        json: () => Promise.resolve({ success: false, models: [], modelCount: 0 }),
    });

    const s = win.document.createElement('script');
    s.textContent = readScriptsBlock();
    win.document.body.appendChild(s);
    s.remove();
    return win;
}

describe('AI provider model restoration — multi-selected optgroup bug', () => {
    let win;

    afterEach(() => {
        if (win) {
            try { win.close(); } catch (_) {  }
        }
    });

    it('selects the LAST option with `selected` across the whole select (the precondition of the bug)', () => {
        win = bootAiConfig({ savedProvider: 'openrouter', savedModel: 'deepseek/deepseek-v4-flash' });
        const sel = win.document.getElementById('aiModel');
        expect(sel.value).toBe('claude-sonnet-4-5');
    });

    it('auto-init writes the saved model into localStorage for the active provider', async () => {
        win = bootAiConfig({ savedProvider: 'openrouter', savedModel: 'deepseek/deepseek-v4-flash' });
        await new Promise((r) => setTimeout(r, 0));
        const stored = JSON.parse(win.localStorage.getItem('stacklens_ai_model_per_provider') || '{}');
        expect(stored.openrouter).toBe('deepseek/deepseek-v4-flash');
        expect(stored.openrouter).not.toBe('claude-sonnet-4-5');
    });

    it('works for every other provider too (not only OpenRouter)', async () => {
        const cases = [
            { savedProvider: 'copilot',    savedModel: 'gpt-5.4-mini' },
            { savedProvider: 'openai',     savedModel: 'gpt-5.2' },
            { savedProvider: 'ollama',     savedModel: 'qwen3-coder:30b-a3b-q4_k_m' },
            { savedProvider: 'lmstudio',   savedModel: 'local-model' },
            { savedProvider: 'openrouter', savedModel: 'deepseek/deepseek-v4-flash' },
            { savedProvider: 'claudecode', savedModel: 'claude-sonnet-4-5' },
        ];

        for (const tc of cases) {
            win = bootAiConfig(tc);
            await new Promise((r) => setTimeout(r, 0));
            const stored = JSON.parse(win.localStorage.getItem('stacklens_ai_model_per_provider') || '{}');
            expect(stored[tc.savedProvider]).toBe(tc.savedModel);
        }
    });

    it('falls back to the first option of the active optgroup when no option is marked selected', async () => {
        const html = `
            <!DOCTYPE html><html><body>
            <input type="radio" name="aiProvider" value="openrouter" checked>
            <select id="aiModel">
              <optgroup label="Claude Code" id="aiClaudeCodeModelsGroup">
                <option value="claude-sonnet-4-5" selected="selected">claude-sonnet-4-5</option>
              </optgroup>
              <optgroup label="OpenRouter" id="aiOpenRouterModelsGroup">
                <option value="openai/gpt-4.1">OpenAI GPT-4.1</option>
                <option value="anthropic/claude-3.5-sonnet">Anthropic Claude 3.5 Sonnet</option>
              </optgroup>
            </select>
            <div id="aiModelGroupsPool" style="display:none"></div>
            <div id="tab-ki" class="active"></div>
            </body></html>
        `;
        const dom = new JSDOM(html, { runScripts: 'dangerously', pretendToBeVisual: true, url: 'http://localhost/' });
        const w = dom.window;
        w.localStorage.clear();
        w.fetch = () => Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve({ success: false, models: [], modelCount: 0 }),
        });
        const s = w.document.createElement('script');
        s.textContent = readScriptsBlock();
        w.document.body.appendChild(s);
        s.remove();
        await new Promise((r) => setTimeout(r, 0));

        const stored = JSON.parse(w.localStorage.getItem('stacklens_ai_model_per_provider') || '{}');
        expect(stored.openrouter).toBe('openai/gpt-4.1');
    });
});