import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractInlineScripts, makeWindow, runInWindow } from './support/fragmentLoader.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SHARED_FRAGMENT = resolve(
    __dirname,
    '../../main/resources/templates/fragments/shared/token-usage.html',
);

const MAIN_ID = 'mainScrollContent';
const USAGE = {
    provider: 'openai-compatible',
    model: 'mock-model',
    totalTokens: 4211,
    promptTokens: 4000,
    completionTokens: 211,
    durationMs: 3000,
};

const SECURITY_PAGE = '<div class="ba-page"><div class="ba-main">Security</div></div>';
const TOOLING_PAGE = '<div class="tl-page" data-cf-token-usage-anchor="">Tooling</div>';
const LASTTEST_PAGE = '<div class="lt-page"><div class="lt-result-sticky-top">Lasttest</div></div>';

function mainInner(pageHtml) {
    return (
        '<div class="sl-page-header">Header</div>' +
        '<div id="alertArea"></div>' +
        pageHtml
    );
}

function boot(pageHtml) {
    const win = makeWindow('<main id="' + MAIN_ID + '">' + mainInner(pageHtml) + '</main>');
    win.localStorage.setItem('dev_show_token_usage', 'true');
    win.escapeHtml = (value) => String(value == null ? '' : value);
    win.i18n = { t: (key) => key };
    const requests = [];
    win.__requests = requests;
    win.fetch = (url) => {
        requests.push(String(url));
        return Promise.resolve({ ok: true, json: () => Promise.resolve(USAGE) });
    };
    runInWindow(win, extractInlineScripts(SHARED_FRAGMENT));
    return win;
}

function spaNavigate(win, pageHtml) {
    const main = win.document.getElementById(MAIN_ID);
    main.innerHTML = mainInner(pageHtml);
    runInWindow(win, extractInlineScripts(SHARED_FRAGMENT));
    win.document.dispatchEvent(new win.Event('app:navigate', { detail: { url: '/tooling' } }));
    return win.document.querySelector('.sl-page-header');
}

function header(win) {
    return win.document.querySelector('.sl-page-header');
}

function bar(win) {
    return win.document.getElementById('tug-bar');
}

function bars(win) {
    return win.document.querySelectorAll('#tug-bar');
}

function usageRequests(win, kind) {
    return win.__requests.filter((url) => url.indexOf('/api/ai-usage/' + kind + '/') === 0);
}

describe('Token-Verbrauchs-Badge: kein Blinken durch Instanzen anderer Seiten', () => {
    let win;

    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
        if (win) {
            try { win.cfTokenUsage.teardownAll(); } catch (_) {}
            try { win.close(); } catch (_) {}
            win = null;
        }
    });

    it('pollt die Analyse-Seite alle vier Sekunden, solange sie die aktuelle Seite ist', async () => {
        win = boot(SECURITY_PAGE);
        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(9000);

        expect(usageRequests(win, 'security').length).toBeGreaterThanOrEqual(2);
        expect(bar(win).querySelector('.tug-pill--empty')).toBeNull();
        expect(bar(win).textContent).toContain('4\u202F211');
    });

    it('stoppt das Pollen der vorherigen Seite nach der SPA-Navigation vollständig', async () => {
        win = boot(SECURITY_PAGE);
        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(9000);
        const pollsBeforeNavigation = usageRequests(win, 'security').length;

        spaNavigate(win, TOOLING_PAGE);
        win.cfTokenUsage.init('tooling', null, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(20000);

        expect(usageRequests(win, 'security')).toHaveLength(pollsBeforeNavigation);
    });

    it('ein veralteter Seiten-Callback kann der aktuellen Seite kein fremdes Badge aufprägen', async () => {
        win = boot(SECURITY_PAGE);
        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(4000);
        const pollsBeforeNavigation = usageRequests(win, 'security').length;

        spaNavigate(win, TOOLING_PAGE);
        win.cfTokenUsage.init('tooling', null, { anchor: header(win) });
        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(10000);

        expect(bar(win).getAttribute('data-tug-kind')).toBe('tooling');
        expect(bar(win).getAttribute('data-tug-id')).toBe('0');
        expect(bar(win).querySelector('.tug-pill--empty')).not.toBeNull();
        expect(usageRequests(win, 'security')).toHaveLength(pollsBeforeNavigation);
    });

    it('eine verspätete Antwort überschreibt einen neueren Zustand nicht', async () => {
        win = boot(SECURITY_PAGE);
        let releaseFirstPoll;
        win.fetch = () => new Promise((resolveResponse) => {
            releaseFirstPoll = resolveResponse;
        });

        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        win.cfTokenUsage.init('security', null, { anchor: header(win) });
        expect(bar(win).querySelector('.tug-pill--empty')).not.toBeNull();

        releaseFirstPoll({ ok: true, json: () => Promise.resolve(USAGE) });
        await vi.advanceTimersByTimeAsync(10);

        expect(bar(win).querySelector('.tug-pill--empty')).not.toBeNull();
        expect(bar(win).getAttribute('data-tug-id')).toBe('0');
        expect(bar(win).textContent).not.toContain('4\u202F211');
    });

    it('ein losgelöster Anker wird nie bemalt, auch nicht bei laufendem Poll', async () => {
        win = boot(SECURITY_PAGE);
        const detachedHeader = header(win);
        win.cfTokenUsage.init('security', 2, { anchor: detachedHeader });
        await vi.advanceTimersByTimeAsync(10);
        expect(bars(win)).toHaveLength(1);

        win.document.getElementById(MAIN_ID).innerHTML = mainInner(SECURITY_PAGE);
        await vi.advanceTimersByTimeAsync(9000);

        expect(bars(win)).toHaveLength(0);
    });

    it('das Teardown einer alten Instanz löscht nie das Badge der aktuellen Seite', async () => {
        win = boot(SECURITY_PAGE);
        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(4000);

        spaNavigate(win, TOOLING_PAGE);
        win.cfTokenUsage.init('tooling', null, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(12000);

        expect(bars(win)).toHaveLength(1);
        expect(bar(win).getAttribute('data-tug-kind')).toBe('tooling');
    });

    it('die aktuelle Seite darf mehrere Badges derselben Analyse-Klasse neu binden', async () => {
        win = boot(SECURITY_PAGE);
        win.cfTokenUsage.init('security', null, { anchor: header(win) });
        expect(bar(win).querySelector('.tug-pill--empty')).not.toBeNull();

        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(10);

        expect(bars(win)).toHaveLength(1);
        expect(bar(win).getAttribute('data-tug-id')).toBe('2');
        expect(bar(win).querySelector('.tug-pill--empty')).toBeNull();
    });

    it('watch() reserviert die Klassen-Bindung der Seite', async () => {
        win = boot(TOOLING_PAGE);
        let currentId = null;
        win.cfTokenUsage.watch('ai', () => currentId, { anchor: header(win) });
        currentId = 9;
        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(1000);

        expect(bar(win).getAttribute('data-tug-kind')).toBe('ai');
        expect(bar(win).getAttribute('data-tug-id')).toBe('9');
    });

    it('ohne analysiereigene Daten bleibt das Badge leer und pollt nicht', async () => {
        win = boot(TOOLING_PAGE);
        win.cfTokenUsage.init('tooling', null, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(13000);

        expect(win.__requests).toHaveLength(0);
        expect(bar(win).querySelector('.tug-pill--empty')).not.toBeNull();
    });

    it('ein fehlgeschlagener Ladevorgang malt das leere Badge, nicht den alten Wert', async () => {
        win = boot(SECURITY_PAGE);
        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(10);
        expect(bar(win).textContent).toContain('4\u202F211');

        win.fetch = () => Promise.resolve({ ok: true, json: () => Promise.resolve({ totalTokens: 0, promptTokens: 0, completionTokens: 0 }) });
        await vi.advanceTimersByTimeAsync(4000);

        expect(bar(win).querySelector('.tug-pill--empty')).not.toBeNull();
        expect(bars(win)).toHaveLength(1);
    });

    it('lasttest: die Seite beansprucht ihre Art selbst, ein fremder Callback bleibt wirkungslos', async () => {
        win = boot(LASTTEST_PAGE);
        win.cfTokenUsage.init('loadtest', null, { anchor: header(win) });
        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(10);

        expect(bar(win).getAttribute('data-tug-kind')).toBe('loadtest');
        expect(usageRequests(win, 'security')).toHaveLength(0);

        win.cfTokenUsage.init('loadtest', 42, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(10);

        expect(bar(win).getAttribute('data-tug-id')).toBe('42');
        expect(bar(win).querySelector('.tug-pill--empty')).toBeNull();
    });

    it('der Toggle in den Entwickleroptionen entfernt nur das eigene Badge', async () => {
        win = boot(SECURITY_PAGE);
        win.cfTokenUsage.init('security', 2, { anchor: header(win) });
        await vi.advanceTimersByTimeAsync(10);
        expect(bars(win)).toHaveLength(1);

        win.localStorage.setItem('dev_show_token_usage', 'false');
        win.dispatchEvent(new win.Event('storage'));
        win.document.dispatchEvent(new win.CustomEvent('cf:dev-toggle-changed', { detail: { key: 'dev_show_token_usage' } }));
        await vi.advanceTimersByTimeAsync(9000);

        expect(bars(win)).toHaveLength(0);
        expect(usageRequests(win, 'security')).toHaveLength(1);
    });
});
