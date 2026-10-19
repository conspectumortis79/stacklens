import { describe, it, expect, afterEach } from 'vitest';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractInlineScripts, extractFunction, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const TEMPLATES = resolve(__dirname, '../../main/resources/templates');
const SHARED = resolve(TEMPLATES, 'fragments/shared/token-usage.html');
const FRAGMENT_MARKER = 'fragments/shared/token-usage :: scripts';
const SCRIPT_RE = /<script\b(?![^>]*\bsrc\s*=)[^>]*>([\s\S]*?)<\/script>/gi;

const USAGE = {
    promptTokens: 6178,
    completionTokens: 3119,
    totalTokens: 9297,
    filterTokensSaved: 0,
    model: 'qwen3.8-flash',
    provider: 'openai-compatible',
    durationMs: 122000,
};

const PAGES = [
    {
        page: 'code-analysis.html',
        kind: 'code',
        rebind: 'caInitTokenUsageBar',
        rebindSource: 'fragments/code-analysis/scripts-repo-run.html',
        seed: 'var caCurrentAnalysisId = 42;',
        resultColumn: '.ca-result-header',
    },
    {
        page: 'lasttest-analysis.html',
        kind: 'loadtest',
        rebind: 'ltInitTokenUsageBar',
        rebindSource: 'fragments/lasttest-analysis/scripts-run.html',
        seed: 'var ltCurrentAnalysisId = 42;',
        resultColumn: '.lt-result-sticky-top',
    },
    {
        page: 'code-qa.html',
        kind: 'codeqa',
        rebind: 'mrInitTokenUsageBar',
        rebindSource: null,
        seed: 'window._mr = { analysisId: 42 };',
        resultColumn: '.mr-result-header',
    },
];

function pageLevelScript(pageFile) {
    const html = readFileSync(resolve(TEMPLATES, pageFile), 'utf8');
    const tail = html.slice(html.lastIndexOf(FRAGMENT_MARKER));
    const blocks = [...tail.matchAll(SCRIPT_RE)].map((m) => m[1]).filter((b) => b.trim());
    const block = blocks.find((b) => /slTokenUsagePage\(\{/.test(b));
    if (!block) throw new Error(`test setup: ${pageFile} has no page-level slTokenUsagePage registration`);
    return block;
}

function layoutDom(config) {
    return (
        '<main id="mainScrollContent">' +
          '<div class="sl-page-header"><h1>Analyse</h1></div>' +
          '<div id="alertArea"></div>' +
          '<div class="page-body">' +
            `<div class="${config.resultColumn.replace('.', '')}">Ergebniskopf</div>` +
            '<div class="tabs">Tabs</div>' +
          '</div>' +
        '</main>'
    );
}

function boot(config) {
    const dom = new JSDOM('<!DOCTYPE html><html><body>' + layoutDom(config) + '</body></html>', {
        runScripts: 'dangerously',
        pretendToBeVisual: true,
        url: 'http://localhost/',
    });
    const win = dom.window;
    win.localStorage.setItem('dev_show_token_usage', 'true');
    win.escapeHtml = (value) => String(value == null ? '' : value);
    win.i18n = { t: (key) => key };
    win.__requests = [];
    win.fetch = (url) => {
        win.__requests.push(String(url));
        return Promise.resolve({ ok: true, json: () => Promise.resolve(USAGE) });
    };
    runInWindow(win, extractInlineScripts(SHARED));
    runInWindow(win, pageLevelScript(config.page));
    return win;
}

function rebind(win, config) {
    runInWindow(win, config.seed);
    if (config.rebindSource) {
        const source = readFileSync(resolve(TEMPLATES, config.rebindSource), 'utf8');
        runInWindow(win, extractFunction(source, config.rebind));
    }
    win[config.rebind]();
    return new Promise((done) => setTimeout(done, 0));
}

function positionOfBar(win) {
    const element = win.document.getElementById('tug-bar');
    if (!element) return null;
    return {
        parent: element.parentElement.id,
        previous: element.previousElementSibling ? element.previousElementSibling.className : null,
        insideResultColumn: !!element.closest('.page-body'),
        barCount: win.document.querySelectorAll('#tug-bar').length,
    };
}

describe('Token-Verbrauchs-Badge: einheitlich unter der Kopfzeilenkarte', () => {
    PAGES.forEach((config) => {
        describe(config.page, () => {
            let win;

            afterEach(() => {
                if (win) {
                    try { win.cfTokenUsage.teardownAll(); } catch (_) {}
                    try { win.close(); } catch (_) {}
                    win = null;
                }
            });

            it('Seiten-Init und Rebind-Funktion nutzen denselben Anker im Quelltext', () => {
                const html = readFileSync(resolve(TEMPLATES, config.page), 'utf8');
                const pageAnchor = /anchor:\s*document\.querySelector\(\s*'([^']+)'\s*\)/
                    .exec(html.slice(html.lastIndexOf(FRAGMENT_MARKER)));
                const sources = config.rebindSource
                    ? [readFileSync(resolve(TEMPLATES, config.rebindSource), 'utf8')]
                    : [html];
                const rebindAnchors = sources
                    .map((source) => /anchor:\s*document\.querySelector\(\s*'([^']+)'\s*\)/.exec(source))
                    .filter(Boolean)
                    .map((match) => match[1]);

                expect(pageAnchor[1]).toBe('.sl-page-header');
                expect(rebindAnchors).toContain('.sl-page-header');
                rebindAnchors.forEach((anchor) => expect(anchor).toBe(pageAnchor[1]));
            });

            it('der leere Badge sitzt direkt unter der Kopfzeilenkarte', () => {
                win = boot(config);

                expect(positionOfBar(win)).toEqual({
                    parent: 'mainScrollContent',
                    previous: 'sl-page-header',
                    insideResultColumn: false,
                    barCount: 1,
                });
            });

            it('der gefüllte Badge bleibt nach dem Rebind exakt dort', async () => {
                win = boot(config);
                await rebind(win, config);

                const bar = win.document.getElementById('tug-bar');
                expect(bar.getAttribute('data-tug-kind')).toBe(config.kind);
                expect(bar.getAttribute('data-tug-id')).toBe('42');
                expect(bar.textContent).toContain('9\u202F297');
                expect(positionOfBar(win)).toEqual({
                    parent: 'mainScrollContent',
                    previous: 'sl-page-header',
                    insideResultColumn: false,
                    barCount: 1,
                });
                expect(win.document.querySelector(config.resultColumn).querySelector('#tug-bar')).toBeNull();
                expect(win.__requests).toEqual([`/api/ai-usage/${config.kind}/42`]);
            });
        });
    });
});
